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

package com.android.dialer.omni.clients;

import java.net.URLEncoder;
import java.util.Arrays;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.android.dialer.omni.Place;
import com.android.dialer.omni.PlaceUtil;
import com.android.dialer.omni.IReverseLookupApi;


public class SearchFrApi implements IReverseLookupApi {

    private final static String TAG = "SearchFrApi";
    private final static String QUERY_URL = "http://www.recherche-inverse.com/ajax/xml-getinfo.php?mode=json&code&w=1&num=";

    private static final int[] SUPPORTED_COUNTRIES = { 33 };
    private String mCity = null;

    @Override
    public String getApiProviderName() {
        // It looks better to show the city here, since the API provides it
        if (mCity != null) {
            return mCity;
        } else {
            return "recherche-inverse.com";
        }
    }

    @Override
    public int[] getSupportedCountryCodes() {
        return SUPPORTED_COUNTRIES;
    }

    @Override
    public Place getNamedPlaceByNumber(String phoneNumber) {
        // This API requires us to have a national-formatted number
        if (phoneNumber.startsWith("0033")) {
            phoneNumber = phoneNumber.substring(4);
            phoneNumber = "0" + phoneNumber;
        } else if (phoneNumber.startsWith("+33")) {
            phoneNumber = phoneNumber.substring(3);
            phoneNumber = "0" + phoneNumber;
        }

        // Trim out spaces as well
        phoneNumber = phoneNumber.replaceAll(" ", "");

        String encodedNumber = URLEncoder.encode(phoneNumber);
        Place place = null;

        if (DEBUG) Log.d(TAG, "Looking for: " + QUERY_URL + phoneNumber);

        try {
            JSONObject obj = PlaceUtil.getJsonRequest(QUERY_URL + phoneNumber);

            if (obj.getInt("nb") > 0) {
                JSONObject inverse = obj.getJSONObject("inverse");

                if (inverse.getString("NNAT").replaceAll(" ", "").equals(phoneNumber)) {
                    // Number looks good!
                    place = new Place();
                    place.setName(inverse.getString("NOM"));
                    place.setPhoneNumber(phoneNumber);
                    mCity = inverse.getString("LIB_SNT_LOCP");
                } else {
                    Log.e(TAG, "The API returned informations for "
                            + inverse.get("NNAT") + " instead of " + phoneNumber + "!");
                }
            } else {
                Log.d(TAG, "No directory entry for that number");
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to get data", e);
        }

        return place;
    }

}
