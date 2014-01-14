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
import com.android.dialer.service.CachedNumberLookupService.CachedContactInfo;
import com.android.i18n.phonenumbers.NumberParseException;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.android.incallui.service.PhoneNumberService;

public class PhoneNumberServiceImpl implements PhoneNumberService {

	private final static String TAG = "PhoneNumberServiceImpl";
	private final static boolean DEBUG = true;

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
		mLookupExecutorService = Executors.newFixedThreadPool(2);
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
				Settings.System.ENABLE_DIALER_REVERSE_LOOKUP, 1) == 1) {
			LookupRunnable lookupRunnable = new LookupRunnable(phoneNumber,
					numberListener, imageListener);
			mLookupExecutorService.execute(lookupRunnable);
		}
	}

	private class LookupRunnable implements Runnable {

		private String mPhoneNumber;
		private NumberLookupListener mNumberListener;
		private ImageLookupListener mImageListener;

		private ReverseLookupApi mReverseLookupClient;

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
			// mPhoneNumber = "00492131754630";

			// TODO: UI to select client
			// mReverseLookupClient = new SearchChApi();
			mReverseLookupClient = new OsmApi();
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

			// do the reverse lookup
			if (phoneNumberInfo == null
					&& isCountryCodeSupported(phoneNumber.getCountryCode())) {
				Place place = mReverseLookupClient
						.getNamedPlaceByNumber(normalizedNumber);
				if (place != null) {
					phoneNumberInfo = new PhoneNumberInfoImpl(place.getName(),
							normalizedNumber, Phone.TYPE_CUSTOM,
							mReverseLookupClient.getApiProviderName(),
							normalizedNumber, null, false);
					if (DEBUG)
						Log.d(TAG, "Reverse lookup result: " + phoneNumberInfo);
				}
			}

			if (phoneNumberInfo != null
					&& phoneNumberInfo.getDisplayName() != null) {
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
						mNumberListener, phoneNumberInfo);
				mHandler.obtainMessage(MESSAGE_NUMBER_FOUND, obj)
						.sendToTarget();
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
			int[] supportedCountryCodes = mReverseLookupClient
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

	private class LookupHandler extends Handler {
		LookupHandler(Looper looper) {
			super(looper);
		}

		public void handleMessage(Message message) {
			if (message.what == MESSAGE_NUMBER_FOUND) {
				Pair<NumberLookupListener, PhoneNumberInfo> obj = (Pair<NumberLookupListener, PhoneNumberInfo>) message.obj;
				NumberLookupListener numberLookupListener = (NumberLookupListener) obj.first;
				PhoneNumberInfo phoneNumberInfo = (PhoneNumberInfo) obj.second;
				numberLookupListener.onPhoneNumberInfoComplete(phoneNumberInfo);
			} else if (message.what == MESSAGE_IMAGE_FOUND) {
				Pair<ImageLookupListener, Bitmap> obj = (Pair<ImageLookupListener, Bitmap>) message.obj;
				ImageLookupListener imageLookupListener = (ImageLookupListener) obj.first;
				Bitmap bitmap = (Bitmap) obj.second;
				imageLookupListener.onImageFetchComplete(bitmap);
			}
		}
	}

}
