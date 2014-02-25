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

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.android.dialer.omni.IPlacesAroundApi;
import com.android.dialer.omni.Place;
import com.android.dialer.omni.PlaceUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GoogleApi implements IPlacesAroundApi {
    private final static String TAG = GoogleApi.class.getSimpleName();

    // Provider URL
    private final static String mProviderUrl = "https://www.google.com/complete/search?gs_ri=dialer";

    private static final String QUERY_FILTER = "q";
    private static final String QUERY_LANGUAGE = "hl";
    private static final String QUERY_LOCATION = "sll";
    private static final String QUERY_RADIUS = "radius";

    @Override
    public String getApiProviderName() {
        return "Google";
    }

    /**
     * Fetches and returns a list of named Places around the provided latitude and
     * longitude parameters.
     * This method is NOT asynchronous. Run it in a thread.
     *
     * @param context  the context
     * @param name     Name to search
     * @param lat      Latitude of the point to search around
     * @param lon      Longitude of the point to search around
     * @param distance Max distance in meters
     * @return the list of matching places
     */
    @Override
    public List<Place> getNamedPlacesAround(Context context, String name, double lat, double lon, long distance) {
        List<Place> places;

        if (DEBUG) {
            Log.d(TAG, "Getting places named like \"" + name + "\"");
        }

        String language = context.getResources().getConfiguration().locale.getLanguage();

        int distanceInMiles = (int) Math.max(Math.round(distance / 1609.344), 1000);
        String request = Uri.parse(mProviderUrl).buildUpon()
                .appendQueryParameter(QUERY_FILTER, name)
                .appendQueryParameter(QUERY_LANGUAGE, language)
                .appendQueryParameter(QUERY_LOCATION, String.format("%f,%f", lat, lon))
                .appendQueryParameter(QUERY_RADIUS, Integer.toString(distanceInMiles))
                .build().toString();

        try {
            places = getPlaces(context, request);

        } catch (Exception e) {
            Log.e(TAG, "Unable to get named places around", e);
            places = new ArrayList<Place>();
        }

        if (DEBUG) {
            Log.d(TAG, "Returning " + places.size() + " places");
        }

        return places;
    }

    /**
     * Fetches and returns Places by sending the provided request
     *
     * @param context the context
     * @param request the JSON request
     * @return the list of matching places
     * @throws IOException
     * @throws JSONException
     */
    private List<Place> getPlaces(Context context, String request)
            throws IOException, JSONException {
        List<Place> places = new ArrayList<Place>();
        // Get and parse request
        if (DEBUG) {
            Log.d(TAG, "getPlaces: request=" + request);
        }

        JSONArray result = PlaceUtil.getJSONArrayRequest(request);
        Log.d(TAG, "result=" + result);
        JSONArray elements = result.getJSONArray(1);

        Geocoder geocoder = new Geocoder(context);
        if (!Geocoder.isPresent()) {
            Log.w(TAG, "No Geocoder available, unable to determine locations!");
        }

        for (int i = 0; i < elements.length(); i++) {
            try {
                Place place = new Place();
                JSONArray element = elements.getJSONArray(i);

                place.name = element.getString(0);

                JSONObject properties = element.getJSONObject(3);
                place.phoneNumber = properties.getString("b");

                place.address = properties.getString("a");
                if (!TextUtils.isEmpty(place.address)) {
                    List<Address> addresses = geocoder.getFromLocationName(place.address, 1);
                    if (addresses != null && addresses.size() > 0) {
                        PlaceUtil.updatePlace(place, addresses.get(0));
                    }
                }

                place.website = properties.optString("f", null);

                if (properties.has("d")) {
                    place.imageUri = Uri.parse(properties.getString("d"));
                }

                places.add(place);
            } catch (JSONException e) {
                Log.e(TAG, "Could not read place at index " + i, e);
            }
        }
        return places;
    }
}
