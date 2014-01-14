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

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.service.CachedNumberLookupService;


public class CachedNumberLookupServiceImpl implements CachedNumberLookupService {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.android.dialer.service.CachedNumberLookupService#buildCachedContactInfo
	 * (com.android.dialer.calllog.ContactInfo)
	 */
	@Override
	public CachedContactInfo buildCachedContactInfo(ContactInfo info) {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.android.dialer.service.CachedNumberLookupService#
	 * lookupCachedContactFromNumber(android.content.Context, java.lang.String)
	 */
	@Override
	public CachedContactInfo lookupCachedContactFromNumber(Context context,
			String number) {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.android.dialer.service.CachedNumberLookupService#addContact(android
	 * .content.Context,
	 * com.android.dialer.service.CachedNumberLookupService.CachedContactInfo)
	 */
	@Override
	public void addContact(Context context, CachedContactInfo info) {
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.android.dialer.service.CachedNumberLookupService#isCacheUri(java.
	 * lang.String)
	 */
	@Override
	public boolean isCacheUri(String uri) {
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.android.dialer.service.CachedNumberLookupService#addPhoto(android
	 * .content.Context, java.lang.String, byte[])
	 */
	@Override
	public boolean addPhoto(Context context, String number, byte[] photo) {
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.android.dialer.service.CachedNumberLookupService#clearAllCacheEntries
	 * (android.content.Context)
	 */
	@Override
	public void clearAllCacheEntries(Context context) {
		// TODO Auto-generated method stub

	}

}
