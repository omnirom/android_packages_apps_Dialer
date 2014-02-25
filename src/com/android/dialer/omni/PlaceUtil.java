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

import android.app.Activity;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DisplayNameSources;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.util.Constants;
import com.android.dialer.R;
import com.android.dialer.omni.clients.GoogleApi;
import com.android.dialer.omni.clients.OsmApi;
import com.android.dialer.omni.clients.SearchChApi;
import com.android.dialer.omni.clients.SearchDeApi;
import com.android.dialer.omni.clients.SearchFrApi;
import com.android.dialer.omni.clients.SearchUsApi;
import com.android.dialerbind.ObjectFactory;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.ccil.cowan.tagsoup.Parser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PlaceUtil {
    private final static String TAG = PlaceUtil.class.getSimpleName();
    private final static boolean DEBUG = false;

    public enum ReverseLookupType {
        GLOBAL, LOCAL
    }

    public final static int MAX_RESULTS = 25;
    public final static String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2";

    private final static CachedPlacesService sCachedPlacesService =
            ObjectFactory.newCachedPlacesService();

    private static Location sLocation;

    public static Place getNamedPlaceByNumber(Context context, PhoneNumber phoneNumber,
                ReverseLookupType type) {
        IReverseLookupApi api = null;
        Place place = null;

        if (type == ReverseLookupType.GLOBAL) {
            api = getGlobalReverseApi();
        } else if (type == ReverseLookupType.LOCAL) {
            api = getLocalReverseApi(phoneNumber);
        }

        if (api != null) {
            if (DEBUG) {
                Log.d(TAG, "Running phone number lookup for " + phoneNumber
                        + " on " + api.getApiProviderName());
            }

            showToastLookup(context, api, phoneNumber);
            place = api.getNamedPlaceByNumber(phoneNumber);
            if (!Place.isEmpty(place)) {
                place.source = api.getApiProviderName();
                place.normalizedNumber = PhoneNumberUtil.getInstance().format(phoneNumber,
                        PhoneNumberUtil.PhoneNumberFormat.E164);

                cachePlace(context, place);
            } else {
                cacheNumber(context, phoneNumber);
            }
        }

        updatePlace(context, place);

        if (DEBUG) {
            Log.d(TAG, "Return place=" + place);
        }
        return place;
    }

    public static Place getNamedPlaceByNumber(Context context, PhoneNumber phoneNumber) {
        Place place = getNamedPlaceByNumber(context, phoneNumber, ReverseLookupType.GLOBAL);
        if (Place.isEmpty(place)) {
            place = getNamedPlaceByNumber(context, phoneNumber, ReverseLookupType.LOCAL);
        }
        if (Place.isEmpty(place)) {
            showToastLookupFailed(context);
        } else {
            showToastLookupFinished(context);
        }
        return place;
    }

    /**
     * Fetches and returns a list of named Places around the current location.
     * This method is NOT asynchronous. Run it in a thread.
     *
     * @param context
     * @param name The name to search
     * @param distance Max distance in meters
     * @return the list of matching places, or null if location is unavailable
     */
    public static List<Place> getNamedPlacesAround(Context context, String name, long distance) {
        if (DEBUG) {
            Log.d(TAG, "Running lookup for placed around named like \"" + name + "\"");
        }

        final LocationManager locationManager = (LocationManager)
                context.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = locationManager.getProviders(true);
        Location location = null;

        for (String provider : providers) {
            location = locationManager.getLastKnownLocation(provider);
            if (location != null) break;
        }

        if (location == null) {
            location = sLocation;
        }

        if (location == null) {
            Log.w(TAG, "Nearby search canceled as location data is unavailable.");
            return null;
        }

        sLocation = location;

        // add some noise to the exact location, roughly +- 1 km, round to 4 digits
        double latitude = location.getLatitude() + ((Math.random() - 0.5) * 0.02);
        latitude = Math.round(latitude * 10000.0) / 10000.0;
        double longitude = location.getLongitude() + ((Math.random() - 0.5) * 0.02);
        longitude = Math.round(longitude * 10000.0) / 10000.0;

        // TODO: filter duplicates
        List<Place> validPlaces = new ArrayList<Place>();

        for (IPlacesAroundApi api : getPlacesAroundApi()) {
            if (DEBUG) {
                Log.d(TAG, "Exact position is " +
                        location.getLatitude() + "," + location.getLongitude() +
                        ", sending " + latitude + "," + longitude +
                        " to " + api.getApiProviderName());
            }

            List<Place> places = api.getNamedPlacesAround(context, name.trim(), latitude, longitude, distance);
            for (int i = 0; i < places.size() && i < MAX_RESULTS; i++) {
                Place place = places.get(i);
                String countryCode = GeoUtil.getCurrentCountryIso(context);
                String numberE164 = PhoneNumberUtils.formatNumberToE164(place.phoneNumber, countryCode);
                if (numberE164 != null) {
                    place.normalizedNumber = numberE164;
                    place.source = api.getApiProviderName();

                    // looking for cached place with image
                    Place cachedPlace = sCachedPlacesService.lookupCachedPlaceFromNumber(
                            context, numberE164);
                    if (!Place.isEmpty(cachedPlace)
                            && cachedPlace.imageUri != null
                            && sCachedPlacesService.isCacheUri(cachedPlace.imageUri.toString())) {
                        place.imageUri = cachedPlace.imageUri;
                    }

                    cachePlace(context, place);
                    validPlaces.add(place);
                } else {
                    Log.w(TAG, "Place has no valid number: " + place);
                }
            }
        }

        updatePlaces(context, validPlaces);

        if (DEBUG) {
            Log.d(TAG, "Returning " + validPlaces.size() + " place(s)");
        }
        return validPlaces;
    }

    private static void cachePlace(Context context, Place place) {
        try {
            sCachedPlacesService.addPlace(context, place);
        } catch (Exception ex) {
            Log.w(TAG, "Failed to cache place " + place);
        }
    }

    private static void cacheNumber(Context context, PhoneNumber phoneNumber) {
        String normalizedNumber = PhoneNumberUtil.getInstance().format(phoneNumber,
                PhoneNumberUtil.PhoneNumberFormat.E164);
        cacheNumber(context, normalizedNumber);
    }

    private static void cacheNumber(Context context, String normalizedNumber) {
        try {
            sCachedPlacesService.touchNumber(context, normalizedNumber);
        } catch (Exception ex) {
            Log.w(TAG, "Failed to cache number " + normalizedNumber);
        }
    }

    public static Location getLastLocation() {
        return sLocation;
    }

    private static void showToastLookup(Context context, IReverseLookupApi api,
            PhoneNumber phoneNumber) {
        if (context != null) {
            String normalizedNumber = PhoneNumberUtil.getInstance()
                    .format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
            String text = context.getString(R.string.toast_reverse_lookup,
                    normalizedNumber, api.getApiProviderName());
            showToast(context, text, Toast.LENGTH_SHORT);
        }
    }

    private static void showToastLookupFinished(Context context) {
        if (context != null) {
            String text = context.getString(R.string.toast_reverse_lookup_finished);
            showToast(context, text, Toast.LENGTH_SHORT);
        }
    }

    private static void showToastLookupFailed(Context context) {
        if (context != null) {
            String text = context.getString(R.string.toast_reverse_lookup_failed);
            showToast(context, text, Toast.LENGTH_SHORT);
        }
    }

    public static void showToast(final Context context, final String text, final int duration) {
        if (context != null) {
            if (context instanceof Activity) {
                final Activity activity = (Activity) context;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, text, duration).show();
                    }
                });
            } else {
                if (DEBUG) {
                    Log.d(TAG, "don't show toast, context is " + context.getClass());
                }
            }
        }
    }

    public static void fetchImage(Context context, Place place) {
        if (!Place.isEmpty(place)
                && place.imageUri != null
                && !sCachedPlacesService.isCacheUri(place.imageUri.toString())) {
            try {
                byte[] imageData = PlaceUtil.getBinaryData(place.imageUri.toString());
                if (imageData != null && imageData.length > 0) {
                    sCachedPlacesService.addImage(context, place.normalizedNumber, imageData);
                    place.imageUri = CachedPlacesProvider.getImageLookupUri(place.normalizedNumber);
                }
            } catch (IOException ignoreException) {
            }
        }
    }

    public static IPlacesAroundApi[] getPlacesAroundApi() {
        // TODO: When we have other providers (like Google Places API), add an UI to select it.
        return new IPlacesAroundApi[] { new OsmApi(), new GoogleApi() };
    }

    public static IReverseLookupApi getGlobalReverseApi() {
        // TODO: When we have other providers (like Google Places API), add an UI to select it.
        return new OsmApi();
    }

    public static IReverseLookupApi getLocalReverseApi(PhoneNumber phoneNumber) {
        if (phoneNumber != null) {
            int countryCode = phoneNumber.getCountryCode();
            if (countryCode == 1) {
                return new SearchUsApi();
            } else if (countryCode == 30) {
                // return new SearchGrApi();
            } else if (countryCode == 31) {
                // return new SearchNlApi();
            } else if (countryCode == 32) {
                // return new SearchBeApi();
            } else if (countryCode == 33) {
                return new SearchFrApi();
            } else if (countryCode == 41) {
                return new SearchChApi();
            } else if (countryCode == 49) {
                return new SearchDeApi();
            }
        }

        return null;
    }

    /**
     * Creates a JSON-encoded lookup uri for a unknown number without an associated contact
     *
     * @param place - Unknown place
     * @return JSON-encoded URI that can be used to perform a lookup when clicking
     * on the quick contact card.
     */
    public static Uri createTemporaryContactUri(Context context, Place place) {
        if (DEBUG) {
            Log.d(TAG, "createTemporaryContactUri: place=" + place);
        }
        try {
            final JSONObject contactRows = new JSONObject()
                    .put(Phone.CONTENT_ITEM_TYPE, new JSONObject()
                            .put(Phone.NUMBER, place.phoneNumber)
                            .put(Phone.TYPE, place.phoneType))
                    .put(StructuredName.CONTENT_ITEM_TYPE, new JSONObject()
                            .put(StructuredName.DISPLAY_NAME, place.name));

            if (!TextUtils.isEmpty(place.email)) {
                contactRows.put(Email.CONTENT_ITEM_TYPE, new JSONObject()
                        .put(Email.ADDRESS, place.email));
            }

            if (!TextUtils.isEmpty(place.website)) {
                contactRows.put(Website.CONTENT_ITEM_TYPE, new JSONObject()
                        .put(Website.URL, place.website));
            }

            String formattedAddress = place.getFormattedAddress();

            if (!TextUtils.isEmpty(formattedAddress)) {
                contactRows.put(StructuredPostal.CONTENT_ITEM_TYPE, new JSONObject()
                        .put(StructuredPostal.FORMATTED_ADDRESS, formattedAddress)
                        .put(StructuredPostal.STREET, place.street)
                        .put(StructuredPostal.POSTCODE, place.postalCode)
                        .put(StructuredPostal.CITY, place.city));
            }
            if (place.imageUri != null
                    && sCachedPlacesService.isCacheUri(place.imageUri.toString())) {
                try {
                    InputStream inputStream = context.getContentResolver()
                            .openInputStream(place.imageUri);
                    byte[] imageData = PlaceUtil.getBinaryData(inputStream);
                    String imageString = new String(imageData);
                    contactRows.put(Photo.CONTENT_ITEM_TYPE, new JSONObject()
                            .put(Photo.PHOTO, imageString));
                } catch (IOException ignoreException) {
                }
            }

            final String jsonString = new JSONObject()
                    .put(Contacts.DISPLAY_NAME, place.name)
                    .put(Contacts.DISPLAY_NAME_SOURCE, DisplayNameSources.PHONE)
                    .put(Contacts.CONTENT_ITEM_TYPE, contactRows)
                    .toString();

            Uri uri = Contacts.CONTENT_LOOKUP_URI.buildUpon()
                    .appendPath(Constants.LOOKUP_URI_ENCODED)
                    .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                            String.valueOf(Long.MAX_VALUE))
                    .encodedFragment(jsonString)
                    .build();
            if (DEBUG) {
                Log.d(TAG, "createTemporaryContactUri: uri=" + uri);
            }
            return uri;
        } catch (JSONException e) {
            Log.w(TAG, "Failed to build temporary contact uri");
            return null;
        }
    }

    public static boolean updatePlace(Place place, Address address) {
        return updatePlacePosition(place, address)
                || updatePlaceAddress(place, address);
    }

    public static boolean updatePlacePosition(Place place, Address address) {
        boolean updated = false;
        if (address.hasLatitude() && address.hasLongitude()) {
            place.latitude = address.getLatitude();
            place.longitude = address.getLongitude();
            updated = true;
        }

        if (DEBUG && updated) {
            Log.d(TAG, "updatePlacePosition: updated " + place);
        }

        return updated;
    }

    public static boolean updatePlaceAddress(Place place, Address address) {
        boolean updated = false;
        if (!TextUtils.isEmpty(address.getThoroughfare())) {
            place.street = address.getThoroughfare();
            if (!TextUtils.isEmpty(address.getSubThoroughfare())) {
                place.street += " " + address.getSubThoroughfare();
            }
            updated = true;
        }
        if (!TextUtils.isEmpty(address.getPostalCode())) {
            place.postalCode = address.getPostalCode();
            updated = true;
        }
        if (!TextUtils.isEmpty(address.getLocality())) {
            place.city = address.getLocality();
            if (!TextUtils.isEmpty(address.getSubLocality())) {
                place.city += " " + address.getSubLocality();
            }
            updated = true;
        }

        if (DEBUG && updated) {
            Log.d(TAG, "updatePlaceAddress: updated " + place);
        }

        return updated;
    }

    public static void updatePlaces(Context context, List<Place> places) {
        if (Geocoder.isPresent()) {
            Geocoder geocoder = new Geocoder(context);
            for (Place place : places) {
                updatePlace(geocoder, place);
            }
        } else {
            Log.i(TAG, "No Geocoder available, unable to update locations");
        }
    }

    public static void updatePlace(Context context, Place place) {
        if (Geocoder.isPresent()) {
            Geocoder geocoder = new Geocoder(context);
            updatePlace(geocoder, place);
        } else {
            Log.i(TAG, "No Geocoder available, unable to update locations");
        }
    }

    private static void updatePlace(Geocoder geocoder, Place place) {
        try {
            String address = place.getFormattedAddress();
            if ((place.latitude == 0 || place.longitude == 0)
                    && !TextUtils.isEmpty(address)) {
                // place has no position
                List<Address> addresses = geocoder.getFromLocationName(address, 1);
                if (addresses != null && addresses.size() > 0) {
                    updatePlacePosition(place, addresses.get(0));
                }
            } else if (TextUtils.isEmpty(place.address)
                    && (TextUtils.isEmpty(place.street)
                    || TextUtils.isEmpty(place.postalCode)
                    || TextUtils.isEmpty(place.city))
                    && place.latitude != 0 && place.longitude != 0) {
                // place has incomplete address
                List<Address> addresses = geocoder.getFromLocation(
                        place.latitude, place.longitude, 1);
                if (addresses != null && addresses.size() > 0) {
                    updatePlaceAddress(place, addresses.get(0));
                }
            }
        } catch (Exception ignoreException) {
        }
    }

    /**
     * Executes a post request and return a JSON object
     * @param url The API URL
     * @param postData The data to post in POST field
     * @return the JSON object
     * @throws IOException
     * @throws JSONException
     */
    public static JSONObject postJSONObjectRequest(String url, String postData)
            throws IOException, JSONException {
        String response = postRequest(url, postData);
        JSONObject json = new JSONObject(response);
        return json;
    }

    /**
     * Executes a post request and return a JSON array
     * @param url The API URL
     * @param postData The data to post in POST field
     * @return the JSON array
     * @throws IOException
     * @throws JSONException
     */
    public static JSONArray postJSONArrayRequest(String url, String postData)
            throws IOException, JSONException {
        String response = postRequest(url, postData);
        JSONArray json = new JSONArray(response);
        return json;
    }

    /**
     * Executes a GET request and return a JSON object
     * @param url The API URL
     * @return the JSON object
     * @throws IOException
     * @throws JSONException
     */
    public static JSONObject getJSONObjectRequest(String url) throws IOException, JSONException {
        String response = getRequest(url);
        JSONObject json = new JSONObject(response);
        return json;
    }

    /**
     * Executes a GET request and return a JSON array
     * @param url The API URL
     * @return the JSON array
     * @throws IOException
     * @throws JSONException
     */
    public static JSONArray getJSONArrayRequest(String url) throws IOException, JSONException {
        String response = getRequest(url);
        JSONArray json = new JSONArray(response);
        return json;
    }

    /**
     * Executes a get request and return the content
     * @param url the URL
     * @return the contents of the web page
     * @throws IOException
     */
    public static String getRequest(String url) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) obj.openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);

        if (DEBUG) Log.d(TAG, "Getting from: " + url);

        return httpRequest(connection);
    }

    public static byte[] getBinaryData(String url) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) obj.openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);

        if (DEBUG) Log.d(TAG, "Getting binary data from: " + url);

        int responseCode = connection.getResponseCode();

        byte[] data = null;
        try {
            InputStream inputStream = connection.getInputStream();
            data = getBinaryData(inputStream);
        } catch (IOException ex) {
            throw new IOException(
                    "Failed to read stream, response code is " + responseCode, ex);
        }

        return data;
    }

    public static byte[] getBinaryData(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    /**
     * Executes a post request and return the content
     * @param url The API URL
     * @param postData The data to post in POST field
     * @return the contents of the web page
     * @throws IOException
     */
    public static String postRequest(String url, String postData)
            throws IOException {
        URL obj = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) obj.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);

        if (DEBUG) Log.d(TAG, "Posting: " + postData + " to " + url);

        // Send post request
        connection.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
        wr.writeBytes(postData);
        wr.flush();
        wr.close();

        return httpRequest(connection);
    }

    private static String httpRequest(HttpURLConnection connnection)
            throws IOException {
        int responseCode = connnection.getResponseCode();

        BufferedReader in;
        try {
            in = new BufferedReader(new InputStreamReader(connnection.getInputStream()));
        } catch (IOException ex) {
            String message = "Failed to read stream, response code is " + responseCode;
            throw new IOException(message, ex);
        }
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();

    }

    /**
     * Executes a get request and parses the result with the provided {@link ContentHandler}
     * @param url the API URL
     * @param contentHandler the ContentHandler to with which the result gets parsed
     * @throws IOException
     * @throws SAXException
     */
    public static void getRequest(String url, ContentHandler contentHandler)
            throws IOException, SAXException {
        String response = getRequest(url);
        Parser parser = new Parser();
        parser.setContentHandler(contentHandler);
        InputStream inputStream = new ByteArrayInputStream(response.getBytes());
        parser.parse(new InputSource(inputStream));
    }

}
