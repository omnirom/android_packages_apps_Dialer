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

import org.json.JSONObject;

import android.util.Log;

import com.android.dialer.omni.IReverseLookupApi;
import com.android.dialer.omni.Place;
import com.android.dialer.omni.PlaceUtil;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;


public class SearchFrApi implements IReverseLookupApi {

    private final static String TAG = SearchFrApi.class.getSimpleName();
    private final static String QUERY_URL = "http://www.recherche-inverse.com/ajax/xml-getinfo.php?mode=json&code&w=1&num=";

    @Override
    public String getApiProviderName() {
        return "recherche-inverse.com";
    }

    @Override
    public Place getNamedPlaceByNumber(PhoneNumber phoneNumber) {
        // This API requires us to have a national-formatted number
        String nationalNumber = "0" + Long.toString(phoneNumber.getNationalNumber());

        String encodedNumber = URLEncoder.encode(nationalNumber);
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
                    place.setPhoneNumber(nationalNumber);
                    place.setCity(inverse.getString("LIB_SNT_LOCP"));
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
