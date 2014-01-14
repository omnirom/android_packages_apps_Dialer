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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.omni.clients.OsmApi;
import com.android.dialer.omni.clients.SearchChApi;
import com.android.dialer.omni.clients.SearchFrApi;
import com.android.dialer.service.CachedNumberLookupService.CachedContactInfo;
import com.android.i18n.phonenumbers.NumberParseException;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.android.incallui.service.PhoneNumberService;

public class PhoneNumberServiceImpl implements PhoneNumberService {

    private final static String TAG = "PhoneNumberServiceImpl";
    private final static boolean DEBUG = false;

    private final static int MESSAGE_NUMBER_FOUND = 1;
    private final static int MESSAGE_IMAGE_FOUND = 2;

    private final static CachedNumberLookupServiceImpl mCachedNumberLookupService = new CachedNumberLookupServiceImpl();

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
        if (phoneNumberInfo.getImageUrl() != null) {
            if (DEBUG)
                Log.d(TAG,
                        "Reverse number lookup. Contact info found, loading image.");
            // TODO: image loader
        } else {
            if (DEBUG)
                Log.d(TAG,
                        "Reverse number lookup. Contact info found, no image.");
        }

        Pair<NumberLookupListener, PhoneNumberInfo> obj = Pair.create(
                numberListener, phoneNumberInfo);
        mHandler.obtainMessage(MESSAGE_NUMBER_FOUND, obj)
                .sendToTarget();
    }

    private class LookupRunnable implements Runnable {

        private String mPhoneNumber;
        private NumberLookupListener mNumberListener;
        private ImageLookupListener mImageListener;

        private final static int LOOKUP_PUBLIC = 0;
        private final static int LOOKUP_LOCAL  = 1;

        private IReverseLookupApi[] mReverseLookupClient;

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

            // We select two clients here:
            //  - We look for nearby shops/public places through OSM
            //  - We look for a local number through a local (geo)
            //    provider. Each has to be coded manually as there
            //    are no public/free APIs that do that.
            mReverseLookupClient = new IReverseLookupApi[2];

            // TODO: When we have other providers (like Google Places API),
            // add an UI to select it.
            mReverseLookupClient[LOOKUP_PUBLIC] = new OsmApi();

            // We then compute, based on the phone number prefix, the
            // local (geo) API to use
            mReverseLookupClient[LOOKUP_LOCAL] = getLocalReverseApi(phoneNumber);
        }

        private IReverseLookupApi getLocalReverseApi(String phoneNumber) {
            // TODO: Is there a better way to do that?
            // WARN: Most specific first

            if (phoneNumber.startsWith("+")) {
                phoneNumber = phoneNumber.replaceAll("\\+", "00");
            }
            
            if (phoneNumber.startsWith("0030")) {
                // return new clients.SearchGrApi();
            } else if (phoneNumber.startsWith("0031")) {
                // return new clients.SearchNlApi();
            } else if (phoneNumber.startsWith("0032")) {
                // return new clients.SearchBeApi();
            } else if (phoneNumber.startsWith("0033")) {
                return new SearchFrApi();
            } else if (phoneNumber.startsWith("0041")) {
                return new SearchChApi();
            } else if (phoneNumber.startsWith("0049")) {
                // return new clients.SearchDeApi();
            } else if (phoneNumber.startsWith("001")) {
                // return new clients.SearchUsApi();
            }

            return null;
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
            if (mCachedNumberLookupService != null) {
                CachedContactInfo cachedContactInfo = mCachedNumberLookupService
                        .lookupCachedContactFromNumber(mContext,
                                normalizedNumber);
                if (cachedContactInfo != null) {
                    ContactInfo contactInfo = cachedContactInfo
                            .getContactInfo();
                    if (contactInfo != ContactInfo.EMPTY) {
                        String imageUrl = null;
                        if (contactInfo.photoUri != null) {
                            imageUrl = contactInfo.photoUri.toString();
                        }

                        // TODO: use function
                        boolean isBusiness = false;
                        phoneNumberInfo = new PhoneNumberInfoImpl(
                                contactInfo.name, contactInfo.number,
                                contactInfo.type, contactInfo.label,
                                contactInfo.normalizedNumber, imageUrl,
                                isBusiness);
                    }
                }
            }

            // We had the number cached, display them right now
            if (phoneNumberInfo != null
                    && phoneNumberInfo.getDisplayName() != null) {
                dispatchPhoneNumberFound(mNumberListener, phoneNumberInfo);
            }

            // do the reverse lookup
            // In parellel, we do a local and a public search, and show the first
            // available result (it should be the same anyway if both return something).
            // We still let both run as one or the other might provide an image that the
            // first one might not have.
            if (phoneNumberInfo == null
                    && isCountryCodeSupported(phoneNumber.getCountryCode())) {
                if (DEBUG) Log.d(TAG, "No cached info, looking up number");

                if (mReverseLookupClient[LOOKUP_PUBLIC] != null) {
                    if (DEBUG) Log.d(TAG, "Running PUBLIC phone number lookup");
                    mLookupExecutorService.execute(
                        new LookupProcessRunnable(
                            mReverseLookupClient[LOOKUP_PUBLIC],
                            normalizedNumber,
                            mNumberListener)
                        );
                }
                if (mReverseLookupClient[LOOKUP_LOCAL] != null) {
                    if (DEBUG) Log.d(TAG, "Running LOCAL phone number lookup");
                    mLookupExecutorService.execute(
                        new LookupProcessRunnable(
                            mReverseLookupClient[LOOKUP_LOCAL],
                            normalizedNumber,
                            mNumberListener)
                        );
                }
            }
        }

        /**
         * Checks if the provided country code is supported by the reverse
         * lookup client
         *
         * @param countryCode
         *            the country code
         * @return <code>true</code> if supportedCountryCodes is null or empty
         *         or if it contains countryCode; otherwise, <code>false</code>
         */
        private boolean isCountryCodeSupported(int countryCode) {
            int[] supportedCountryCodes = mReverseLookupClient[LOOKUP_PUBLIC]
                    .getSupportedCountryCodes();
            if (supportedCountryCodes == null
                    || supportedCountryCodes.length == 0) {
                return true;
            }
            for (int i = 0; i < supportedCountryCodes.length; ++i) {
                if (supportedCountryCodes[i] == countryCode) {
                    return true;
                }
            }
            return false;
        }
    }

    private class LookupProcessRunnable implements Runnable {
        private String mPhoneNumber;
        private NumberLookupListener mNumberListener;
        private IReverseLookupApi mApi;

        public LookupProcessRunnable(IReverseLookupApi api,
                String phoneNumber,
                NumberLookupListener numberListener) {
            mApi = api;
            mPhoneNumber = phoneNumber;
            mNumberListener = numberListener;
        }

        public void run() {
            Place place = mApi.getNamedPlaceByNumber(mPhoneNumber);
            if (place != null) {
                PhoneNumberInfo phoneNumberInfo = new PhoneNumberInfoImpl(place.getName(),
                        mPhoneNumber, Phone.TYPE_CUSTOM,
                        mApi.getApiProviderName(),
                        mPhoneNumber, null, false);

                if (DEBUG)
                    Log.d(TAG, "Reverse lookup result: " + phoneNumberInfo);

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
