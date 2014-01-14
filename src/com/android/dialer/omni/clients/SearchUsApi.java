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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

import com.android.dialer.omni.Place;
import com.android.dialer.omni.PlaceUtil;
import com.android.dialer.omni.IReverseLookupApi;


public class SearchUsApi implements IReverseLookupApi {

    private final static String TAG = "SearchUsApi";
    private final static String QUERY_URL = "http://www.whitepages.com/search/ReversePhone?full_phone=";

    private static final int[] SUPPORTED_COUNTRIES = { 1 };

    @Override
    public String getApiProviderName() {
        return "whitepages.com";
    }

    @Override
    public int[] getSupportedCountryCodes() {
        return SUPPORTED_COUNTRIES;
    }

    @Override
    public Place getNamedPlaceByNumber(String phoneNumber) {
        String encodedNumber = URLEncoder.encode(phoneNumber);
        Place place = null;

        try {
            String html = new String(PlaceUtil.getRequest(QUERY_URL + encodedNumber));

            Pattern regex = Pattern.compile("<a.*?data-gaaction=\"associated_0\".*?>(.*?)<.*?</a>",
                    Pattern.DOTALL);
            Matcher matcher = regex.matcher(html);

            if (matcher.find()) {
                String name = matcher.group(1).trim();

                place = new Place();
                place.setName(name);
                place.setPhoneNumber(phoneNumber);
            } else {
                Log.w(TAG, "Regex matched nothing!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to do reverse lookup", e);
        }

        return place;
    }

}
