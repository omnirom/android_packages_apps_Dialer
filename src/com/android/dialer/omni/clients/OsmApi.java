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
import android.text.TextUtils;
import android.util.Log;

import com.android.dialer.omni.IPlacesAroundApi;
import com.android.dialer.omni.IReverseLookupApi;
import com.android.dialer.omni.Place;
import com.android.dialer.omni.PlaceUtil;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * Class to interact with OpenStreetMaps API (Overpass API)
 */
public class OsmApi implements IPlacesAroundApi, IReverseLookupApi {
    private final static String TAG = OsmApi.class.getSimpleName();

    // -----
    // Constants

    // Amenity (example: fast_food, restaurant, ...)
    private final static String TAG_AMENITY = "amenity";

    // Name of the place
    private final static String TAG_NAME = "name";

    // Phone number of the place
    private final static String TAG_PHONE = "phone";
    private final static String TAG_CONTACT_PHONE = "contact:phone";

    // Type of cuisine (in case of restaurant, e.g. "pizza")
    private final static String TAG_CUISINE = "cuisine";

    // Source from which the place was put on OSM
    private final static String TAG_SOURCE = "source";

    // email address of the place
    private final static String TAG_EMAIL = "email";
    private final static String TAG_CONTACT_EMAIL = "contact:email";

    // Website of the place
    private final static String TAG_WEBSITE = "website";
    private final static String TAG_CONTACT_WEBSITE = "contact:website";

    // Street of the place
    private final static String TAG_STREET = "addr:street";

    // House number of the place
    private final static String TAG_HOUSE_NUMBER = "addr:housenumber";

    // postal code of the place
    private final static String TAG_POSTAL_CODE = "addr:postcode";

    // City where the place is
    private final static String TAG_CITY = "addr:city";

    // Opening hours
    private final static String TAG_OPENING_HOURS = "opening_hours";

    // Is the place accesible to wheelchairs?
    private final static String TAG_WHEELCHAIR = "wheelchair";

    // Provider URL (e.g. http://overpass-api.de/api/interpreter
    // or http://overpass.osm.rambler.ru/cgi/interpreter)
    private final static String mProviderUrl = "http://overpass-api.de/api/interpreter";

    @Override
    public String getApiProviderName() {
        return "OpenStreetMap";
    }

    /**
     * Fetches and returns a list of named Places around the provided latitude and
     * longitude parameters. The bounding box is calculated from lat-distance, lon-distance
     * to lat+distance, lon+distance.
     * This method is NOT asynchronous. Run it in a thread.
     *
     * @param context the context
     * @param name Name to search
     * @param lat Latitude of the point to search around
     * @param lon Longitude of the point to search around
     * @param distance Max distance in meters
     * @return the list of matching places
     */
    @Override
    public List<Place> getNamedPlacesAround(Context context, String name, double lat, double lon, long distance) {
        List<Place> places;

        if (DEBUG) Log.d(TAG, "Getting places named like \"" + name + "\"");

        // The OSM API doesn't support case-insensitive searches, but does support RegEx. So
        // we hack around a bit.
        String finalName = "";
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            finalName = finalName + "[" + Character.toUpperCase(c) + Character.toLowerCase(c) + "]";
        }

        String nameQuery = "[\"name\"~\"" + finalName + "\"]";
        String locationQuery = "(around:" + distance + "," + lat + "," + lon + ")";

        // query all elements (nodes, ways, areas) with the given name which have either
        // a tag "phone" or a tag "contact:phone"
        String request = "[out:json];(" +
                "node" + nameQuery + "[\"" + TAG_PHONE + "\"]" + locationQuery + ";" +
                "node" + nameQuery + "[\"" + TAG_CONTACT_PHONE + "\"]" + locationQuery + ";" +
                "way" + nameQuery + "[\"" + TAG_PHONE + "\"]" + locationQuery + ";" +
                "way" + nameQuery + "[\"" + TAG_CONTACT_PHONE + "\"]" + locationQuery + ";" +
                // "area" + nameQuery + "[\"" + TAG_PHONE + "\"]" + locationQuery + ";" +
                // "area" + nameQuery + "[\"" + TAG_CONTACT_PHONE + "\"]" + locationQuery + ";" +
                ");(._;>;);out body;";

        try {
            places = getPlaces(request, true);
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
     * @param phoneNumber the phone number
     * @return the first matching place
     */
    @Override
    public Place getNamedPlaceByNumber(PhoneNumber phoneNumber) {
        if (DEBUG) Log.d(TAG, "Getting place for " + phoneNumber);
        Place place = null;

        String nationalNumber = Long.toString(phoneNumber.getNationalNumber());
        String countryCode = Long.toString(phoneNumber.getCountryCode());

        // build the reg exp for number look up:
        // ignore additional ;-separated numbers before and after: "^(.*;)?" and "(;.*)?$"
        // allow + or 00 in before the country code: "^(\\+)?" + countryCode
        //     or allow national number without country code but with leading "0"
        // allow spaces, - and / between digits: "[^;0-9]*"
        String ignoredChars = "[^;0-9]*";
        String regExp = "^(.*;)?" + ignoredChars;
        regExp += "((\\\\+|00)" + countryCode + "|0)" + ignoredChars;
        for (int i = 0; i < nationalNumber.length(); i++) {
            char c = nationalNumber.charAt(i);
            regExp += c + ignoredChars;
        }
        regExp += "(;.*)?$";

        // Build request data
        String request = "[out:json];(" +
                "node[\"" + TAG_PHONE + "\"~\"" + regExp + "\"];" +
                "node[\"" + TAG_CONTACT_PHONE + "\"~\"" + regExp + "\"];" +
                "way[\"" + TAG_PHONE + "\"~\"" + regExp + "\"];" +
                "way[\"" + TAG_CONTACT_PHONE + "\"~\"" + regExp + "\"];" +
                // "area[\"" + TAG_PHONE + "\"~\"" + regExp + "\"];" +
                // "area[\"" + TAG_CONTACT_PHONE + "\"~\"" + regExp + "\"];" +
                ");out body;";

        if (DEBUG) Log.d(TAG, "OSM query: " + request);

        try {
            List<Place> places = getPlaces(request, false);
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
     * @param forceLocation if true, fetch only places with location
     * @return the list of matching places
     * @throws IOException
     * @throws JSONException
     */
    private List<Place> getPlaces(String request, boolean forceLocation)
            throws IOException, JSONException {
        List<Place> places = new ArrayList<Place>();
        // Post and parse request
        if (DEBUG) {
            Log.d(TAG, "getPlaces: request=" + request);
        }
        JSONObject obj = PlaceUtil.postJSONObjectRequest(mProviderUrl,
                "data=" + URLEncoder.encode(request));
        JSONArray elements = obj.getJSONArray("elements");

        // read all nodes first, they could be referenced in ways and areas later
        HashMap<Integer, JSONObject> nodes = new HashMap<Integer, JSONObject>(elements.length());
        if (forceLocation) {
            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);
                if (element.getString("type").equalsIgnoreCase("node")) {
                    int id = element.getInt("id");
                    nodes.put(id, element);
                }
            }
        }

        for (int i = 0; i < elements.length(); i++) {
            try {
                JSONObject element = elements.getJSONObject(i);

                // ignore elements without name and phone tags
                if (element.has("tags")) {
                    JSONObject tags = element.getJSONObject("tags");
                    if (tags.has(TAG_NAME)
                            && (tags.has(TAG_PHONE) || tags.has(TAG_CONTACT_PHONE))) {
                        if (element.has("lat") && element.has("lon")) {
                            // element has coordinates, it's most probably a node
                            Place place = new Place();

                            place.latitude = element.getDouble("lat");
                            place.longitude = element.getDouble("lon");

                            parseTags(place, tags);

                            if (DEBUG) {
                                Log.d(TAG, "Add place with coordinates: " + place);
                            }
                            places.add(place);
                        } else if (!forceLocation) {
                            // element has no coordinates, but we do not care
                            Place place = new Place();

                            parseTags(place, tags);

                            if (DEBUG) {
                                Log.d(TAG, "Add place without coordinates: " + place);
                            }
                            places.add(place);
                        } else if (element.has("nodes")) {
                            // element has referenced nodes, at least one them should have coordinates
                            Place place = null;
                            JSONArray refNodes = element.getJSONArray("nodes");
                            for (int j = 0; j < refNodes.length() && Place.isEmpty(place); j++) {
                                int refId = refNodes.getInt(j);

                                if (nodes.containsKey(refId)) {
                                    JSONObject refNode = nodes.get(refId);
                                    if (refNode.has("lat") && refNode.has("lon")) {
                                        place = new Place();

                                        place.latitude = refNode.getDouble("lat");
                                        place.longitude = refNode.getDouble("lon");
                                    }
                                }
                            }

                            // only proceed if we found coordinates
                            if (!Place.isEmpty(place)) {
                                parseTags(place, tags);

                                if (DEBUG) {
                                    Log.d(TAG, "Add place with linked nodes: " + place);
                                }
                                places.add(place);
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Could not read place at index " + i, e);
            }
        }
        return places;
    }

    private static void parseTags(Place place, JSONObject tags) throws JSONException {
        JSONArray tagNames = tags.names();
        for (int j = 0; j < tagNames.length(); j++) {
            String tagName = tagNames.getString(j);
            String tagValue = tags.getString(tagName);

            putTag(place, tagName, tagValue);
        }
    }

    private static void putTag(Place place, String tagName, String tagValue) {
        if (TAG_NAME.equalsIgnoreCase(tagName)) {
            place.name = tagValue;
        } else if (TAG_PHONE.equalsIgnoreCase(tagName)
                || TAG_CONTACT_PHONE.equalsIgnoreCase(tagName)) {
            // TODO: TAG_PHONE can contain multiple numbers "number1;number2"
            place.phoneNumber = tagValue;
        } else if (TAG_EMAIL.equalsIgnoreCase(tagName)
                || TAG_CONTACT_EMAIL.equalsIgnoreCase(tagName)) {
            place.email = tagValue;
        } else if (TAG_WEBSITE.equalsIgnoreCase(tagName)
                || TAG_CONTACT_WEBSITE.equalsIgnoreCase(tagName)) {
            place.website = tagValue;
        } else if (TAG_STREET.equalsIgnoreCase(tagName)) {
            // put street and house number together in Place.street
            String oldValue = "";
            if (!TextUtils.isEmpty(place.street)) {
                oldValue = " " + place.street;
            }
            place.street = tagValue + oldValue;
        } else if (TAG_HOUSE_NUMBER.equalsIgnoreCase(tagName)) {
            // put street and house number together in Place.street
            String oldValue = "";
            if (!TextUtils.isEmpty(place.street)) {
                oldValue = place.street + " ";
            }
            place.street = oldValue + tagValue;
        } else if (TAG_POSTAL_CODE.equalsIgnoreCase(tagName)) {
            place.postalCode = tagValue;
        } else if (TAG_CITY.equalsIgnoreCase(tagName)) {
            place.city = tagValue;
        } else if (DEBUG) {
            Log.d(TAG, "Tag " + tagName + " not supported");
        }
    }

}