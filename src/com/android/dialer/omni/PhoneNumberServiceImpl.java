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
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.dialer.omni.PlaceUtil.ReverseLookupType;
import com.android.dialerbind.ObjectFactory;
import com.android.i18n.phonenumbers.NumberParseException;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.android.incallui.service.PhoneNumberService;

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
    private final Handler mHandler;
    private final String mCountryIso;

    /**
     * @param context
     */
    public PhoneNumberServiceImpl(Context context) {
        mContext = context;
        mLookupExecutorService = Executors.newFixedThreadPool(3);
        mHandler = new LookupHandler(Looper.getMainLooper());
        mCountryIso = getCountryCodeIso();
    }

    private String getCountryCodeIso() {
        return ((TelephonyManager) mContext.getSystemService("phone"))
                .getSimCountryIso().toUpperCase();
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

    private void dispatchPhoneNumberFound(NumberLookupListener numberListener,
            PhoneNumberInfo phoneNumberInfo) {
        String imageUrl = phoneNumberInfo.getImageUrl();
        if (imageUrl != null) {
            if (DEBUG) {
                Log.d(TAG, "Reverse number lookup. Contact info found, loading image.");
            }
            // TODO: image loader
            if (sCachedPlacesService.isCacheUri(imageUrl)) {
                // TODO: cached image
                // mContext.getContentResolver().openInputStream(Uri.parse(imageUrl));
            } else {
                // TODO: load image from web
                // TODO: and save to cache
            }
        } else {
            if (DEBUG) {
                Log.d(TAG, "Reverse number lookup. Contact info found, no image.");
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
        public LookupRunnable(String phoneNumber,
                NumberLookupListener numberListener,
                ImageLookupListener imageListener) {

            mPhoneNumber = phoneNumber;
            mNumberListener = numberListener;
            mImageListener = imageListener;

            // TODO: override number for testing
            // mPhoneNumber = "0033123456789";
        }

        @Override
        public void run() {
            // ignore VOIP calls
            if (PhoneNumberUtils.isUriNumber(mPhoneNumber)) {
                return;
            }

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
            PhoneNumberInfo phoneNumberInfo = null;
            Place place = sCachedPlacesService.lookupCachedPlaceFromNumber(mContext, normalizedNumber);
            if (place != null && place != Place.EMPTY) {
                String imageUrl = null;

                phoneNumberInfo = new PhoneNumberInfoImpl(
                        place.getName(),
                        place.getPhoneNumber(),
                        Phone.TYPE_CUSTOM,
                        place.getSource(),
                        place.getPhoneNumber(),
                        imageUrl,
                        place.isBusiness());
            }

            // We had the number cached, display it right now
            if (phoneNumberInfo != null
                    && phoneNumberInfo.getDisplayName() != null) {
                dispatchPhoneNumberFound(mNumberListener, phoneNumberInfo);
            }

            // do the reverse lookup
            // In parallel, we do a local and a public search, and show the first
            // available result (it should be the same anyway if both return something).
            // We still let both run as one or the other might provide an image that the
            // first one might not have.
            if (phoneNumberInfo == null) {
                if (DEBUG) Log.d(TAG, "No cached info, looking up number");

                // We select two clients here:
                //  - We look for nearby shops/public places through OSM
                //  - We look for a local number through a local (geo)
                //    provider. Each has to be coded manually as there
                //    are no public/free APIs that do that.

                mLookupExecutorService.execute(
                    new LookupProcessRunnable(
                        ReverseLookupType.GLOBAL,
                        phoneNumber,
                        mNumberListener));

                mLookupExecutorService.execute(
                    new LookupProcessRunnable(
                        ReverseLookupType.LOCAL,
                        phoneNumber,
                        mNumberListener));
            }
        }
    }

    private class LookupProcessRunnable implements Runnable {
        private PhoneNumber mPhoneNumber;
        private NumberLookupListener mNumberListener;
        private ReverseLookupType mReverseLookupType;

        public LookupProcessRunnable(ReverseLookupType reverseLookupType,
                PhoneNumber phoneNumber,
                NumberLookupListener numberListener) {
            mReverseLookupType = reverseLookupType;
            mPhoneNumber = phoneNumber;
            mNumberListener = numberListener;
        }

        public void run() {
            Place place = PlaceUtil.getNamedPlaceByNumber(mContext,
                    mPhoneNumber, mReverseLookupType);
            if (!Place.isEmpty(place)) {
                String label = place.getSource();
                String city = place.getCity();
                if (!TextUtils.isEmpty(city)) {
                    label = city;
                }

                PhoneNumberInfo phoneNumberInfo = new PhoneNumberInfoImpl(
                        place.getName(),
                        place.getPhoneNumber(),
                        Phone.TYPE_CUSTOM,
                        label,
                        place.getPhoneNumber(),
                        null,
                        place.isBusiness());

                if (DEBUG) {
                    Log.d(TAG, "Reverse lookup result: " + place);
                    Log.d(TAG, "Reverse lookup result: " + phoneNumberInfo);
                }

                dispatchPhoneNumberFound(mNumberListener, phoneNumberInfo);
            }
        }
    }

    private class LookupHandler extends Handler {
        
        private boolean mHasProcessed;

        LookupHandler(Looper looper) {
            super(looper);
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
                    numberLookupListener.onPhoneNumberInfoComplete(phoneNumberInfo);
                    mHasProcessed = true;
                }
            } else if (message.what == MESSAGE_IMAGE_FOUND) {
                Pair<ImageLookupListener, Bitmap> obj = (Pair<ImageLookupListener, Bitmap>) message.obj;
                ImageLookupListener imageLookupListener = (ImageLookupListener) obj.first;
                Bitmap bitmap = (Bitmap) obj.second;
                imageLookupListener.onImageFetchComplete(bitmap);
            }
        }
    }

}
