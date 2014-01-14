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


public interface ReverseLookupApi extends PlaceApi {
	
	/**
	 * Gets the country codes for which reverse lookup is provided.
	 * If the list is empty or null, all countries are supported.
	 * 
	 * @return the supported country codes
	 */
	int[] getSupportedCountryCodes();
	
	/**
	 * Fetches and returns a named Place with the provided phone number.
     * This method is NOT asynchronous. Run it in a thread.
	 * 
	 * @param phoneNumber the number in {@link com.android.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.E164} format
	 * @return the first matching place
	 */
	Place getNamedPlaceByNumber(String phoneNumber);
	
}
