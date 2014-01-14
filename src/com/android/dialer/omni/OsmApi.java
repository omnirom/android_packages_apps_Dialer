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

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

/**
 * Class to interact with OpenStreetMaps API (Overpass API)
 */
public class OsmApi extends AbstractPlaceApi {
    private final static String TAG = "OsmApi";

    // Provider URL (e.g. http://overpass-api.de/api/interpreter
    // or http://overpass.osm.rambler.ru/api/interpreter)
    private String mProviderUrl;


    /**
     * Default constructor
     * @param providerUrl The URL of the OSM database provider
     */
    public OsmApi(String providerUrl) {
        mProviderUrl = providerUrl;
    }

    /**
     * Fetches and returns a list of named Places around the provided latitude and
     * longitude parameters. The bounding box is calculated from lat-distance, lon-distance
     * to lat+distance, lon+distance.
     * This method is NOT asynchronous. Run it in a thread.
     *
     * @param name Name to search
     * @param lat Latitude of the point to search around
     * @param lon Longitude of the point to search around
     * @param distance Max distance (polar coordinates)
     */
    @Override
    public List<Place> getNamedPlacesAround(String name, double lat, double lon, double distance) {
        List<Place> places = new ArrayList<Place>();

        if (DEBUG) Log.d(TAG, "Getting places named " + name);

        double latStart =   lat - distance / 2.0;
        double latEnd =     lat + distance / 2.0;
        double lonStart =   lon - distance / 2.0;
        double lonEnd =     lon + distance / 2.0;

        // The OSM API doesn't support case-insentive searches, but does support RegEx. So
        // we hack around a bit.
        String finalName = "";
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            finalName = finalName + "[" + Character.toUpperCase(c) + Character.toLowerCase(c) + "]";
        }

        // Build request data
        String request = "[out:json];node[\"name\"~\"" + finalName + "\"][\"phone\"]" +
            "(" + latStart + "," + lonStart + "," + latEnd + "," + lonEnd + ");out body;";

        try {
            // Post and parse request
            JSONObject obj = postJsonRequest(mProviderUrl, "data=" + URLEncoder.encode(request));
            JSONArray elements = obj.getJSONArray("elements");

            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);

                Place place = new Place();
                place.latitude = element.getDouble("lat");
                place.longitude = element.getDouble("lon");

                JSONObject tags = element.getJSONObject("tags");
                JSONArray tagNames = tags.names();

                for (int j = 0; j < tagNames.length(); j++) {
                    String tagName = tagNames.getString(j);
                    String tagValue = tags.getString(tagName);

                    place.tags.put(tagName, tagValue);
                }

                places.add(place);
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Unable to get named places around", e);
        }

        if (DEBUG) Log.d(TAG, "Returning " + places.size() + " places");

        return places;
    }
}