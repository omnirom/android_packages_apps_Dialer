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

import com.android.incallui.service.PhoneNumberService.PhoneNumberInfo;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;


public class PhoneNumberInfoImpl implements PhoneNumberInfo {

	private String mDisplayName;
	private String mNumber;
	private int mPhoneType;
	private String mPhoneLabel;
	private String mNormalizedNumber;
	private String mImageUrl;
	private boolean mBusiness;

	/**
	 * 
	 * @param displayName
	 * @param number
	 * @param phoneType
	 * @param phoneLabel
	 * @param normalizedNumber
	 * @param imageUrl
	 * @param business
	 */
	public PhoneNumberInfoImpl(String displayName, String number,
			int phoneType, String phoneLabel, String normalizedNumber,
			String imageUrl, boolean business) {
		mDisplayName = displayName;
		mNumber = number;
		mPhoneType = phoneType;
		mPhoneLabel = phoneLabel;
		mNormalizedNumber = normalizedNumber;
		mImageUrl = imageUrl;
		mBusiness = business;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.android.incallui.service.PhoneNumberService.PhoneNumberInfo#
	 * getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return mDisplayName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.android.incallui.service.PhoneNumberService.PhoneNumberInfo#getNumber
	 * ()
	 */
	@Override
	public String getNumber() {
		return mNumber;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.android.incallui.service.PhoneNumberService.PhoneNumberInfo#getPhoneType
	 * ()
	 */
	@Override
	public int getPhoneType() {
		return mPhoneType;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.android.incallui.service.PhoneNumberService.PhoneNumberInfo#getPhoneLabel
	 * ()
	 */
	@Override
	public String getPhoneLabel() {
		return mPhoneLabel;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.android.incallui.service.PhoneNumberService.PhoneNumberInfo#
	 * getNormalizedNumber()
	 */
	@Override
	public String getNormalizedNumber() {
		return mNormalizedNumber;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.android.incallui.service.PhoneNumberService.PhoneNumberInfo#getImageUrl
	 * ()
	 */
	@Override
	public String getImageUrl() {
		return mImageUrl;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.android.incallui.service.PhoneNumberService.PhoneNumberInfo#isBusiness
	 * ()
	 */
	@Override
	public boolean isBusiness() {
		return mBusiness;
	}

	@Override
	public String toString() {
		ToStringHelper toStringHelper = Objects.toStringHelper(this);
		toStringHelper.add("mDisplayName", mDisplayName);
		toStringHelper.add("mImageUrl", mImageUrl);
		toStringHelper.add("mNormalizedNumber", mNormalizedNumber);
		return toStringHelper.toString();
	}
}
