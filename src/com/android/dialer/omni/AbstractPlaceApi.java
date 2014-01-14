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


public abstract class AbstractPlaceApi {
    private final static String TAG = "AbstractPlaceApi";
    protected final static boolean DEBUG = true;

    public class Place {
        // -----
        // Constants

        // Amenity (example: fast_food, restaurant, ...)
        public final static String TAG_AMENITY = "amenity";

        // Name of the place
        public final static String TAG_NAME = "name";

        // Phone number of the place
        public final static String TAG_PHONE = "phone";

        // Type of cuisine (in case of restaurant, e.g. "pizza")
        public final static String TAG_CUISINE = "cuisine";

        // Source from which the place was put on OSM
        public final static String TAG_SOURCE = "source";

        // Website of the place
        public final static String TAG_WEBSITE = "website";

        // Opening hours
        public final static String TAG_OPENING_HOURS = "opening_hours";

        // Is the place accesible to wheelchairs?
        public final static String TAG_WHEELCHAIR = "wheelchair";

        // -----
        // Fields

        // Latitude of the place
        public double latitude;

        // Longitude of the place
        public double longitude;

        // Tags of the place (see TAG_ constants). Note that
        // not all tags might be set depending on the amenity of
        // the place and the amount of information available.
        public HashMap<String, String> tags = new HashMap<String, String>();
    }


    /**
     * Default constructor
     */
    public AbstractPlaceApi() {
    }

    /**
     * Fetches and returns a list of named Places around the provided latitude and
     * longitude parameters. The bounding box is calculated from lat-distance, lon-distance
     * to lat+distance, lon+distance.
     * This method is NOT asynchronous. Run it in a thread.
     *
     * @param name The name to search
     * @param lat Latitude of the point to search around
     * @param lon Longitude of the point to search around
     * @param distance Max distance (polar coordinates)
     */
    public abstract List<Place> getNamedPlacesAround(String name, double lat, double lon, double distance);

    /**
     * Executes a post request and return a JSON object
     * @param url The API URL
     * @param data The data to post in POST field
     */
    public static JSONObject postJsonRequest(String url, String postData) throws IOException, JSONException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        con.setRequestMethod("POST");

        if (DEBUG) Log.d(TAG, "Posting: " + postData + " to " + url);

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(postData);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        JSONObject json = new JSONObject(response.toString());
        return json;
    }
}
