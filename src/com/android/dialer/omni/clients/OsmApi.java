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

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.dialer.omni.Place;
import com.android.dialer.omni.PlaceApi;
import com.android.dialer.omni.PlaceUtil;
import com.android.dialer.omni.PlacesAroundApi;
import com.android.dialer.omni.ReverseLookupApi;

import android.util.Log;


/**
 * Class to interact with OpenStreetMaps API (Overpass API)
 */
public class OsmApi implements PlacesAroundApi, ReverseLookupApi {
    private final static String TAG = "OsmApi";

    // -----
    // Constants

    // Amenity (example: fast_food, restaurant, ...)
	private final static String TAG_AMENITY = "amenity";

    // Name of the place
	private final static String TAG_NAME = "name";

    // Phone number of the place
	private final static String TAG_PHONE = "phone";

    // Type of cuisine (in case of restaurant, e.g. "pizza")
	private final static String TAG_CUISINE = "cuisine";

    // Source from which the place was put on OSM
	private final static String TAG_SOURCE = "source";

    // Website of the place
	private final static String TAG_WEBSITE = "website";

    // Opening hours
	private final static String TAG_OPENING_HOURS = "opening_hours";

    // Is the place accesible to wheelchairs?
	private final static String TAG_WHEELCHAIR = "wheelchair";

	// Provider URL (e.g. http://overpass-api.de/api/interpreter
    // or http://overpass.osm.rambler.ru/api/interpreter)
    private String mProviderUrl;
    
    public OsmApi() {
    	mProviderUrl = "http://overpass-api.de/api/interpreter";
    }
    
    /**
     * Default constructor
     * @param providerUrl The URL of the OSM database provider
     */
    public OsmApi(String providerUrl) {
        mProviderUrl = providerUrl;
    }

    @Override
    public String getApiProviderName() {
    	return "OpenStreetMap";
    }
    
    @Override
    public int[] getSupportedCountryCodes() {
    	return null;
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
     * @return the list of matching places
     */
    @Override
    public List<Place> getNamedPlacesAround(String name, double lat, double lon, double distance) {
        List<Place> places;

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
        	places = getPlaces(request);
        }
        catch (Exception e) {
            Log.e(TAG, "Unable to get named places around", e);
            places = new ArrayList<Place>();
        }

        if (DEBUG) Log.d(TAG, "Returning " + places.size() + " places");

        return places;
    }
    
	/**
	 * Fetches and returns a named Place with the provided phone number.
     * This method is NOT asynchronous. Run it in a thread.
	 * 
	 * @param phoneNumber the number in {@link com.android.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.E164} format
	 * @return the first matching place
	 */
    @Override
    public Place getNamedPlaceByNumber(String phoneNumber) {
    	if (DEBUG) Log.d(TAG, "Getting place for " + phoneNumber);
    	Place place = null;
    	
    	// build the reg exp for number look up:
    	// ignore additional ;-separated numbers before and after: "^(.*;)?" and "(;.*)?$"
    	// allow + or 00 in before the country code: "[^;0-9]*(00)?"
    	// allow spaces, - and / between digits: "[^;0-9]*"
    	String numberWithoutPlus = phoneNumber.replace("+", "");
    	String regExp = "^(.*;)?[^;0-9]*(00)?";
    	for (int i = 0; i < numberWithoutPlus.length(); i++) {
            char c = numberWithoutPlus.charAt(i);
            regExp += c + "[^;0-9]*";
    	}
    	regExp += "(;.*)?$";
    	
    	// Build request data
        String request = "[out:json];node[\"phone\"~\"" + regExp + "\"];out body;";

        try {
        	List<Place> places = getPlaces(request);
        	if (places.size() > 0) {
        		place = places.get(0);
        	}
        }
        catch (Exception e) {
            Log.e(TAG, "Unable to get place with number", e);
        }

        return place;
    }
    
    /**
     * Fetches and returns Places by sending the provided request
     * @param request the JSON request
     * @return the list of matching places
     * @throws IOException
     * @throws JSONException
     */
    private List<Place> getPlaces(String request) throws IOException, JSONException {
        List<Place> places = new ArrayList<Place>();
        // Post and parse request
        JSONObject obj = PlaceUtil.postJsonRequest(mProviderUrl, "data=" + URLEncoder.encode(request));
        JSONArray elements = obj.getJSONArray("elements");

        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.getJSONObject(i);

            Place place = new Place();
            place.setLatitude(element.getDouble("lat"));
            place.setLongitude(element.getDouble("lon"));

            JSONObject tags = element.getJSONObject("tags");
            JSONArray tagNames = tags.names();

            for (int j = 0; j < tagNames.length(); j++) {
                String tagName = tagNames.getString(j);
                String tagValue = tags.getString(tagName);

                // TODO: TAG_PHONE can contain multiple numbers "number1;number2"
                putTag(place, tagName, tagValue);
            }

            places.add(place);
        }
        return places;
    }
    
    private static void putTag(Place place, String tagName, String tagValue) {
    	if (TAG_NAME.equalsIgnoreCase(tagName)) {
    		place.setName(tagValue);
    	} else if (TAG_PHONE.equalsIgnoreCase(tagName)) {
    		place.setPhoneNumber(tagValue);
    	} else if (DEBUG) {
    		Log.d(TAG, "Tag " + tagName + " not supported");
    	}
    	
    }

}