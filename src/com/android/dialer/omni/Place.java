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


import android.net.Uri;
import android.text.TextUtils;

import com.google.common.base.Objects;


public class Place {

    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";
    public static final String PHONE_NUMBER = "phone_number";
    public static final String NORMALIZED_NUMBER = "normalized_number";
    public static final String PHONE_TYPE = "phone_type";
    public static final String IS_BUSINESS = "is_business";
    public static final String NAME = "name";
    public static final String ADDRESS = "address";
    public static final String STREET = "street";
    public static final String POSTAL_CODE = "postal_code";
    public static final String CITY = "city";
    public static final String EMAIL = "email";
    public static final String WEBSITE = "website";
    public static final String SOURCE = "source";

    public static Place EMPTY = new Place();

    public static boolean isEmpty(Place place) {
        return place == null || place == EMPTY;
    }

    /**
     * Latitude of the place
      */
    public double latitude;

    /**
     * Longitude of the place
     */
    public double longitude;

    /**
     * Phone number of the place
     */
    public String phoneNumber;

    /**
     * Normalized number (E164)
     */
    public String normalizedNumber;

    /**
     * Type of the phone, e.g. home or mobile
     */
    public int phoneType;

    /**
     * Is place business or private
     */
    public boolean isBusiness;

    /**
     * Name of the place
     */
    public String name;

    /**
     * Formatted address is either {@link address},
     * or, if empty, "{@link street}, {@link postalCode} {@link city}"
     * @return
     */
    public String getFormattedAddress() {
        String formattedAddress = address;
        if (TextUtils.isEmpty(formattedAddress)) {
            // format address as "%street, %postalCode %city"
            formattedAddress = "";
            if (!TextUtils.isEmpty(city)) {
                formattedAddress = city;
            }
            if (!TextUtils.isEmpty(postalCode)) {
                if (!TextUtils.isEmpty(formattedAddress)) {
                    formattedAddress = " " + formattedAddress;
                }
                formattedAddress = postalCode + formattedAddress;
            }
            if (!TextUtils.isEmpty(street)) {
                if (!TextUtils.isEmpty(formattedAddress)) {
                    formattedAddress = ", " + formattedAddress;
                }
                formattedAddress = street + formattedAddress;
            }
        }
        return formattedAddress;
    }

    /**
     * The full, unstructured postal address. <i>This field must be
     * consistent with any structured data.</i>
     */
    public String address;

    /**
     * Street of the place
     */
    public String street;

    /**
     * Postal code of the place
     */
    public String postalCode;

    /**
     * Name of the city
     */
    public String city;

    /**
     * Email address
     */
    public String email;

    /**
     * Website
     */
    public String website;

    /**
     * Uri of the image
     */
    public Uri imageUri;

    /**
     * The provider of this place
     */
    public String source;

    @Override
    public String toString() {
        Objects.ToStringHelper toStringHelper = Objects.toStringHelper(this);
        toStringHelper.add("name", name);
        toStringHelper.add("phoneNumber", phoneNumber);
        toStringHelper.add("normalizedNumber", normalizedNumber);
        toStringHelper.add("address", address);
        toStringHelper.add("street", street);
        toStringHelper.add("postalCode", postalCode);
        toStringHelper.add("city", city);
        toStringHelper.add("latitude", latitude);
        toStringHelper.add("longitude", longitude);
        toStringHelper.add("email", email);
        toStringHelper.add("website", website);
        toStringHelper.add("imageUri", imageUri);
        toStringHelper.add("source", source);
        return toStringHelper.toString();
    }
}
