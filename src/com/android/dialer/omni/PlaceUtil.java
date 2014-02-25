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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
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

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.android.dialer.R;
import com.android.dialer.omni.clients.OsmApi;
import com.android.dialer.omni.clients.SearchChApi;
import com.android.dialer.omni.clients.SearchFrApi;
import com.android.dialer.omni.clients.SearchUsApi;
import com.android.dialerbind.ObjectFactory;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;


public class PlaceUtil {
    private final static String TAG = PlaceUtil.class.getSimpleName();
    private final static boolean DEBUG = false;

    public enum ReverseLookupType {
        GLOBAL, LOCAL
    }

    private final static CachedPlacesService sCachedPlacesService =
            ObjectFactory.newCachedPlacesService();

    public static Place getNamedPlaceByNumber(PhoneNumber phoneNumber, ReverseLookupType type) {
        return getNamedPlaceByNumber(null, phoneNumber, type);
    }

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
            if (DEBUG) Log.d(TAG, "Running phone number lookup for " + phoneNumber
                    + " on " + api.getApiProviderName());

            showToastLookup(context, api, phoneNumber);
            place = api.getNamedPlaceByNumber(phoneNumber);
            if (!Place.isEmpty(place)) {
                place.setSource(api.getApiProviderName());

                if (context != null) {
                    sCachedPlacesService.addPlace(context, place);
                    // TODO: save image
                }
            }
        }

        return place;
    }

    public static Place getNamedPlaceByNumber(PhoneNumber phoneNumber) {
        return getNamedPlaceByNumber(null, phoneNumber);
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
     * Executes a post request and return a JSON object
     * @param url The API URL
     * @param postData The data to post in POST field
     * @return the JSON object
     * @throws IOException
     * @throws JSONException
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

    /**
     * Executes a get request and return byte array
     * @param url the API URL
     * @return the byte array containing the web page
     * @throws ClientProtocolException
     * @throws IOException
     */
    public static byte[] getRequest(String url) throws ClientProtocolException, IOException {
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
     * @throws ClientProtocolException
     * @throws IOException
     * @throws SAXException
     */
    public static void getRequest(String url, ContentHandler contentHandler)
            throws ClientProtocolException, IOException, SAXException {
        byte[] htmlResultPage = getRequest(url);
        Parser parser = new Parser();
        parser.setContentHandler(contentHandler);
        InputStream inputStream = new ByteArrayInputStream(htmlResultPage);
        parser.parse(new InputSource(inputStream));
    }

}
