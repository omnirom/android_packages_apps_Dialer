/*
 *  Copyright (C) 2014 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.android.dialer.omni;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.contacts.common.GeoUtil;
import com.android.dialer.omni.PlaceUtil.ReverseLookupType;
import com.android.dialerbind.ObjectFactory;
import com.android.i18n.phonenumbers.NumberParseException;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.android.incallui.service.PhoneNumberService;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class PhoneNumberServiceImpl implements PhoneNumberService {

    private final static String TAG = PhoneNumberServiceImpl.class.getSimpleName();
    private final static boolean DEBUG = false;

    private final static int MESSAGE_NUMBER_FOUND = 1;
    private final static int MESSAGE_IMAGE_FOUND = 2;

    private final static CachedPlacesService sCachedPlacesService =
            ObjectFactory.newCachedPlacesService();

    private final Context mContext;
    private final ExecutorService mLookupExecutorService;
    private final ExecutorService mImageLoaderExecutorService;
    private final LookupHandler mHandler;
    private final String mCountryIso;

    /**
     * @param context
     */
    public PhoneNumberServiceImpl(Context context) {
        mContext = context;
        mLookupExecutorService = Executors.newFixedThreadPool(3);
        mImageLoaderExecutorService = Executors.newFixedThreadPool(2);
        mHandler = new LookupHandler(Looper.getMainLooper());
        mCountryIso = GeoUtil.getCurrentCountryIso(context);
    }

    @Override
    public void getPhoneNumberInfo(String phoneNumber,
            NumberLookupListener numberListener,
            ImageLookupListener imageListener, boolean isIncoming) {
        if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.ENABLE_DIALER_REVERSE_LOOKUP, 0) == 1) {

            LookupRunnable lookupRunnable = new LookupRunnable(phoneNumber,
                    numberListener, imageListener);
            mLookupExecutorService.execute(lookupRunnable);
        }
    }

    private void dispatchPlaceFound(NumberLookupListener numberListener,
            ImageLookupListener imageListener, Place place) {

        String label = TextUtils.isEmpty(place.city) ? place.source : place.city;
        String imageUrl = place.imageUri != null ? place.imageUri.toString() : null;

        PhoneNumberInfo phoneNumberInfo = new PhoneNumberInfoImpl(
                place.name,
                place.phoneNumber,
                Phone.TYPE_CUSTOM,
                label,
                place.normalizedNumber,
                imageUrl,
                place.isBusiness);

        if (!TextUtils.isEmpty(imageUrl)) {
            if (DEBUG) {
                Log.d(TAG, "Reverse number lookup. Contact info found, loading image." +
                        phoneNumberInfo);
            }
            ImageLoaderRunnable imageLoaderRunnable = new ImageLoaderRunnable(place,
                    imageListener);
            mImageLoaderExecutorService.execute(imageLoaderRunnable);
        } else {
            if (DEBUG) {
                Log.d(TAG, "Reverse number lookup. Contact info found, no image." +
                        phoneNumberInfo);
            }
        }

        Pair<NumberLookupListener, PhoneNumberInfo> obj = Pair.create(
                numberListener, phoneNumberInfo);
        mHandler.obtainMessage(MESSAGE_NUMBER_FOUND, obj).sendToTarget();
    }

    private class LookupRunnable implements Runnable {

        private String mPhoneNumber;
        private NumberLookupListener mNumberListener;
        private ImageLookupListener mImageListener;

        /**
         * @param phoneNumber
         * @param numberListener
         * @param imageListener
         */
        LookupRunnable(String phoneNumber,
                NumberLookupListener numberListener,
                ImageLookupListener imageListener) {

            mPhoneNumber = phoneNumber;
            mNumberListener = numberListener;
            mImageListener = imageListener;
        }

        @Override
        public void run() {
            // ignore VOIP calls
            if (PhoneNumberUtils.isUriNumber(mPhoneNumber)) {
                return;
            }

            mHandler.reset();

            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            PhoneNumber phoneNumber = null;
            try {
                phoneNumber = util.parse(mPhoneNumber, mCountryIso);
            } catch (NumberParseException e) {
            }

            if (phoneNumber == null || !util.isValidNumber(phoneNumber)) {
                Log.w(TAG, "Could not parse number.  Skipping lookup.");
                return;
            }

            String normalizedNumber = util.format(phoneNumber,
                    PhoneNumberFormat.E164);
            if (DEBUG)
                Log.d(TAG, "raw number: " + mPhoneNumber + ", formatted e164: "
                        + normalizedNumber);

            // Lookup cache
            Place place = sCachedPlacesService.lookupCachedPlaceFromNumber(mContext,
                    normalizedNumber);
            if (!Place.isEmpty(place)) {
                // We had the number cached, display it right now
                dispatchPlaceFound(mNumberListener, mImageListener, place);
            } else if (!sCachedPlacesService.isCachedNumber(mContext, normalizedNumber)) {
                // do the reverse lookup, but only if it hasn't been done before
                if (DEBUG) Log.d(TAG, "No cached info, looking up number");

                // In parallel, we do a local and a public search, and show the first
                // available result (it should be the same anyway if both return something).
                // We still let both run as one or the other might provide an image that the
                // first one might not have.

                // We select two clients here:
                //  - We look for nearby shops/public places through OSM
                //  - We look for a local number through a local (geo)
                //    provider. Each has to be coded manually as there
                //    are no public/free APIs that do that.

                mLookupExecutorService.execute(
                        new LookupProcessRunnable(
                                ReverseLookupType.GLOBAL,
                                phoneNumber,
                                mNumberListener,
                                mImageListener));

                mLookupExecutorService.execute(
                        new LookupProcessRunnable(
                                ReverseLookupType.LOCAL,
                                phoneNumber,
                                mNumberListener,
                                mImageListener));
            }
        }
    }

    private class LookupProcessRunnable implements Runnable {
        private PhoneNumber mPhoneNumber;
        private NumberLookupListener mNumberListener;
        private ImageLookupListener mImageListener;
        private ReverseLookupType mReverseLookupType;

        LookupProcessRunnable(ReverseLookupType reverseLookupType,
                PhoneNumber phoneNumber,
                NumberLookupListener numberListener,
                ImageLookupListener imageLookupListener) {
            mReverseLookupType = reverseLookupType;
            mPhoneNumber = phoneNumber;
            mNumberListener = numberListener;
            mImageListener = imageLookupListener;
        }

        public void run() {
            Place place = PlaceUtil.getNamedPlaceByNumber(mContext,
                    mPhoneNumber, mReverseLookupType);
            if (!Place.isEmpty(place)) {

                if (DEBUG) {
                    Log.d(TAG, "Reverse lookup result: " + place);
                }

                dispatchPlaceFound(mNumberListener, mImageListener, place);
            }
        }
    }

    private class ImageLoaderRunnable implements Runnable {
        private Place mPlace;
        private ImageLookupListener mImageListener;


        ImageLoaderRunnable(Place place, ImageLookupListener imageListener) {
            mPlace = place;
            mImageListener = imageListener;
        }

        @Override
        public void run() {
            Uri imageUri = mPlace.imageUri;
            byte[] imageData = null;
            boolean isCached = sCachedPlacesService.isCacheUri(imageUri.toString());

            try {
                if (isCached) {
                    InputStream inputStream = mContext.getContentResolver()
                            .openInputStream(imageUri);
                    imageData = PlaceUtil.getBinaryData(inputStream);
                } else {
                    imageData = PlaceUtil.getBinaryData(imageUri.toString());
                }

                if (imageData != null && imageData.length > 0) {
                    Bitmap image = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                    Pair<ImageLookupListener, Bitmap> obj = Pair.create(
                            mImageListener, image);
                    mHandler.obtainMessage(MESSAGE_IMAGE_FOUND, obj).sendToTarget();

                    if (!isCached) {
                        sCachedPlacesService.addImage(mContext, mPlace.normalizedNumber, imageData);
                    }
                }
            } catch (IOException exception) {
                Log.e(TAG, "Failed to load image from " + imageUri, exception);
            }
        }
    }

    private class LookupHandler extends Handler {
        
        private boolean mHasProcessed;

        LookupHandler(Looper looper) {
            super(looper);
            mHasProcessed = false;
        }

        public void reset() {
            mHasProcessed = false;
        }

        public void handleMessage(Message message) {
            if (message.what == MESSAGE_NUMBER_FOUND) {
                if (mHasProcessed) {
                    Log.w(TAG, "Received a response from both APIs - keeping the first one");
                } else {
                    Pair<NumberLookupListener, PhoneNumberInfo> obj = (Pair<NumberLookupListener, PhoneNumberInfo>) message.obj;
                    NumberLookupListener numberLookupListener = (NumberLookupListener) obj.first;
                    PhoneNumberInfo phoneNumberInfo = (PhoneNumberInfo) obj.second;
                    if (DEBUG) {
                        Log.d(TAG, "Sending info to numberLookupListener: " + phoneNumberInfo);
                    }
                    numberLookupListener.onPhoneNumberInfoComplete(phoneNumberInfo);
                    mHasProcessed = true;
                }
            } else if (message.what == MESSAGE_IMAGE_FOUND) {
                Pair<ImageLookupListener, Bitmap> obj = (Pair<ImageLookupListener, Bitmap>) message.obj;
                ImageLookupListener imageLookupListener = (ImageLookupListener) obj.first;
                Bitmap bitmap = (Bitmap) obj.second;
                if (DEBUG) {
                    Log.d(TAG, "Sending image to imageLookupListener");
                }
                imageLookupListener.onImageFetchComplete(bitmap);
            }
        }
    }

}
