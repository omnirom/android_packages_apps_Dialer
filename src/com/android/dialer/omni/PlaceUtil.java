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
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
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
import com.android.dialer.omni.clients.OsmApi;
import com.android.dialer.omni.clients.SearchChApi;
import com.android.dialer.omni.clients.SearchFrApi;
import com.android.dialer.omni.clients.SearchUsApi;
import com.android.dialerbind.ObjectFactory;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.ccil.cowan.tagsoup.Parser;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
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
                place.setSource(api.getApiProviderName());

                cachePlace(context, place);
                // TODO: save image
            } else {
                cacheNumber(context, phoneNumber);
            }
        }

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

        // add some noise to the exact location, roughly +- 1 km, round to 5 digits
        double latitude = location.getLatitude() + ((Math.random() - 0.5) * 0.02);
        latitude = Math.round(latitude * 100000.0) / 100000.0;
        double longitude = location.getLongitude() + ((Math.random() - 0.5) * 0.02);
        longitude = Math.round(longitude * 100000.0) / 100000.0;

        IPlacesAroundApi api = getPlacesAroundApi();
        if (DEBUG) {
            Log.d(TAG, "Exact position is " +
                    location.getLatitude() + "," + location.getLongitude() +
                    ", sending " + latitude + "," + longitude +
                    " to " + api.getApiProviderName());
        }

        List<Place> places = api.getNamedPlacesAround(name.trim(), latitude, longitude, distance);
        List<Place> validPlaces = new ArrayList<Place>(places.size());
        for (int i = 0; i < places.size() && i < MAX_RESULTS; i++) {
            Place place = places.get(i);
            String number = place.getPhoneNumber();
            String countryCode = GeoUtil.getCurrentCountryIso(context);
            String numberE164 = PhoneNumberUtils.formatNumberToE164(number, countryCode);
            if (numberE164 != null) {
                place.setSource(api.getApiProviderName());
                cachePlace(context, place);
                validPlaces.add(place);
            } else {
                Log.w(TAG, "Place has no valid number: " + place);
            }
        }

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

    public static IPlacesAroundApi getPlacesAroundApi() {
        // TODO: When we have other providers (like Google Places API),
        // add an UI to select it.
        return new OsmApi();
    }

    public static IReverseLookupApi getGlobalReverseApi() {
        // TODO: When we have other providers (like Google Places API),
        // add an UI to select it.
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
                // return new SearchDeApi();
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
    public static Uri createTemporaryContactUri(Place place) {
        if (DEBUG) {
            Log.d(TAG, "createTemporaryContactUri: place=" + place);
        }
        try {
            final JSONObject contactRows = new JSONObject()
                    .put(Phone.CONTENT_ITEM_TYPE, new JSONObject()
                            .put(Phone.NUMBER, place.getPhoneNumber())
                            .put(Phone.TYPE, place.getPhoneType()))
                    .put(StructuredName.CONTENT_ITEM_TYPE, new JSONObject()
                            .put(StructuredName.DISPLAY_NAME, place.getName()));

            if (!TextUtils.isEmpty(place.getEmail())) {
                contactRows.put(Email.CONTENT_ITEM_TYPE, new JSONObject()
                        .put(Email.ADDRESS, place.getEmail()));
            }

            if (!TextUtils.isEmpty(place.getWebsite())) {
                contactRows.put(Website.CONTENT_ITEM_TYPE, new JSONObject()
                        .put(Website.URL, place.getWebsite()));
            }

            // format address as "%street, %postalCode %city"
            String formattedAddress = "";
            if (!TextUtils.isEmpty(place.getCity())) {
                formattedAddress = place.getCity();
            }
            if (!TextUtils.isEmpty(place.getPostalCode())) {
                if (!TextUtils.isEmpty(formattedAddress)) {
                    formattedAddress = " " + formattedAddress;
                }
                formattedAddress = place.getPostalCode() + formattedAddress;
            }
            if (!TextUtils.isEmpty(place.getStreet())) {
                if (!TextUtils.isEmpty(formattedAddress)) {
                    formattedAddress = ", " + formattedAddress;
                }
                formattedAddress = place.getStreet() + formattedAddress;
            }
            if (!TextUtils.isEmpty(formattedAddress)) {
                contactRows.put(StructuredPostal.CONTENT_ITEM_TYPE, new JSONObject()
                        .put(StructuredPostal.FORMATTED_ADDRESS, formattedAddress)
                        .put(StructuredPostal.STREET, place.getStreet())
                        .put(StructuredPostal.POSTCODE, place.getPostalCode())
                        .put(StructuredPostal.CITY, place.getCity()));
            }

            final String jsonString = new JSONObject()
                    .put(Contacts.DISPLAY_NAME, place.getName())
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

    /**
     * Executes a post request and return a JSON object
     * @param url The API URL
     * @param postData The data to post in POST field
     * @return the JSON object
     * @throws IOException
     * @throws JSONException
     */
    public static JSONObject postJsonRequest(String url, String postData)
            throws IOException, JSONException {
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

        BufferedReader in;
        try {
            in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        } catch (IOException ex) {
            throw new IOException(
                    "Failed to read stream, response code is " + responseCode, ex);
        }
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        JSONObject json = new JSONObject(response.toString());
        return json;
    }

    /**
     * Executes a GET request and return a JSON object
     * @param url The API URL
     * @return the JSON object
     * @throws IOException
     * @throws JSONException
     */
    public static JSONObject getJsonRequest(String url) throws IOException, JSONException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        con.setRequestMethod("GET");

        if (DEBUG) Log.d(TAG, "Getting JSON from: " + url);

        con.setDoOutput(true);
        int responseCode = con.getResponseCode();

        BufferedReader in;
        try {
            in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        } catch (IOException ex) {
            throw new IOException(
                    "Failed to read stream, response code is " + responseCode, ex);
        }
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        JSONObject json = new JSONObject(response.toString());
        return json;
    }

    /**
     * Executes a get request and return byte array
     * @param url the API URL
     * @return the byte array containing the web page
     * @throws IOException
     */
    public static byte[] getRequest(String url) throws IOException {
        if (DEBUG) Log.d(TAG, "download: " + url);

        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, 15000);
        HttpConnectionParams.setSoTimeout(params, 15000);
        HttpClient client = new DefaultHttpClient(params);
        HttpGet request = new HttpGet(url);
        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        byte[] htmlResultPage = null;
        if (entity != null) {
            htmlResultPage = EntityUtils.toByteArray(entity);
            entity.consumeContent();
        }

        return htmlResultPage;
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
        byte[] htmlResultPage = getRequest(url);
        Parser parser = new Parser();
        parser.setContentHandler(contentHandler);
        InputStream inputStream = new ByteArrayInputStream(htmlResultPage);
        parser.parse(new InputSource(inputStream));
    }

}
