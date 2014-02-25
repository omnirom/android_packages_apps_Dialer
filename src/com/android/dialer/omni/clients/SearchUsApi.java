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

import android.util.Log;

import com.android.dialer.omni.IReverseLookupApi;
import com.android.dialer.omni.Place;
import com.android.dialer.omni.PlaceUtil;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;

import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SearchUsApi implements IReverseLookupApi {

    private final static String TAG = SearchUsApi.class.getSimpleName();
    private final static String QUERY_URL = "http://www.whitepages.com/search/ReversePhone?full_phone=";

    // testNumber = "12069735100"

    @Override
    public String getApiProviderName() {
        return "whitepages.com";
    }

    @Override
    public Place getNamedPlaceByNumber(PhoneNumber phoneNumber) {
        String normalizedNumber = PhoneNumberUtil.getInstance().format(phoneNumber,
                PhoneNumberFormat.E164);
        String encodedNumber = URLEncoder.encode(normalizedNumber);
        Place place = null;

        try {
            String html = new String(PlaceUtil.getRequest(QUERY_URL + encodedNumber));

            Pattern regex = Pattern.compile(
                    "<a\\s.*?data-gaaction=\"associated_0\".*?>(.*?)</a>",
                    Pattern.DOTALL);
            Matcher matcher = regex.matcher(html);

            if (matcher.find()) {
                place = new Place();

                String associated = matcher.group(1);

                regex = Pattern.compile("(.*?)<", Pattern.DOTALL);
                matcher = regex.matcher(associated);
                matcher.find();
                place.name = matcher.group(1).trim();

                regex = Pattern.compile(">\\s*Business\\s*<", Pattern.DOTALL);
                matcher = regex.matcher(associated);
                place.isBusiness = matcher.find();

                regex = Pattern.compile(
                        "<span\\s.*?class=\".*?address-primary.*?\".*?>(.*?)</span>",
                        Pattern.DOTALL);
                matcher = regex.matcher(html);
                if (matcher.find()) {
                    place.street = matcher.group(1).trim();
                }

                regex = Pattern.compile(
                        "<span\\s.*?class=\".*?address-location.*?\".*?>(.*?)\\s+(\\S*?)</span>",
                        Pattern.DOTALL);
                matcher = regex.matcher(html);
                if (matcher.find()) {
                    place.city = matcher.group(1).trim();
                    place.postalCode = matcher.group(2).trim();
                }
            } else {
                Log.w(TAG, "Regex matched nothing!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to do reverse lookup", e);
        }

        return place;
    }

}
