/*
 * Copyright (C) 2014 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.lookup.google;

import com.android.dialer.lookup.ReverseLookup;
import com.android.incallui.service.PhoneNumberServiceImpl.PhoneNumberInfoImpl;
import com.android.services.telephony.common.MoreStrings;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.graphics.Point;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;
import android.view.WindowManager;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;

public class GoogleReverseLookup extends ReverseLookup {
    private static final String TAG =
            GoogleReverseLookup.class.getSimpleName();

    private static final boolean DEBUG = false;
    private static final String DEBUG_URL =
            "http://10.8.8.228:8000/plus/v2whitelisted/people/lookup";

    // Maximum number of Google accounts to use for lookups
    private static final int MAX_ACCOUNTS = 3;

    // There is currently no user agent restrictions, but that could
    // potentially change
    private static final String USER_AGENT =
            "Dalvik/1.6.0 (Linux; U; Android 4.4.2; Nexus 5 Build/KOT49H)";

    /** OAuth2 scope for obtaining token for doing a phone number lookup */
    private static final String SCOPES = "oauth2:" +
            "https://www.googleapis.com/auth/plus.me" + " " +
            "https://www.googleapis.com/auth/plus.peopleapi.readwrite";

    /** OAuth2 cope for obtaining token to download images */
    private static final String IMAGE_SCOPES = "oauth2:" +
            "https://www.googleapis.com/auth/plus.contactphotos";

    private static final String LOOKUP_URL =
            "https://www.googleapis.com/plus/v2whitelisted/people/lookup";

    private static final String PHOTO_URL =
            "https://plus.google.com/_/focus/photos/private";

    private static HashMap<String, String> mTokens
            = new HashMap<String, String>();
    private static HashMap<String, String> mImageTokens
            = new HashMap<String, String>();

    private static class AuthException extends Exception {
        public AuthException(String msg) {
            super(msg);
        }
    }

    public GoogleReverseLookup(Context context) {
    }

    /**
     * Get OAuth2 access token for performing the reverse lookup.
     *
     * @param context The application context
     * @param account The Google account
     * @return The OAuth2 access token
     */
    private static synchronized String getToken(
            Context context, Account account) {
        if (account == null) {
            return null;
        }

        String token = null;

        try {
            token = mTokens.get(account.name);
            if (token == null) {
                token = GoogleAuth.getInstance(context)
                        .getToken(account, SCOPES);
                if (token != null) {
                    mTokens.put(account.name, token);
                }
            }
        } finally {
        }

        return token;
    }

    /**
     * Get OAuth2 access token for downloading contact images.
     *
     * @param context The application context
     * @param account The Google account
     * @return The OAuth2 access token
     */
    private static synchronized String getImageToken(
            Context context, Account account) {
        if (account == null) {
            return null;
        }

        String token = null;

        try {
            token = mImageTokens.get(account.name);
            if (token == null) {
                token = GoogleAuth.getInstance(context)
                        .getToken(account, IMAGE_SCOPES);
                if (token != null) {
                    mImageTokens.put(account.name, token);
                }
            }
        } finally {}

        return token;
    }

    /**
     * Invalidate an OAuth2 access token for reverse lookup.
     *
     * @param context The application context
     * @param account The Google account
     */
    private static synchronized void invalidateToken(
            Context context, Account account) {
        GoogleAuth.getInstance(context).invalidateToken(
                mTokens.get(account.name));
        mTokens.remove(account.name);
    }

    /**
     * Invalidate an OAuth2 access token for contact image downloads.
     *
     * @param context The application context
     * @param account The Google account
     */
    private static synchronized void invalidateImageToken(
            Context context, Account account) {
        GoogleAuth.getInstance(context).invalidateToken(
                mImageTokens.get(account.name));
        mImageTokens.remove(account.name);
    }

    /**
     * Remove personally identifiable information from a URL
     *
     * @param url Original URL
     * @return New URL with personal information replaced with dummy text
     */
    private static String obfuscateUrl(String url) {
        Uri uri = Uri.parse(url);
        Uri.Builder builder = new Uri.Builder();

        builder.scheme(uri.getScheme());
        builder.encodedAuthority(uri.getEncodedAuthority());
        builder.path(uri.getPath());

        for (String name : uri.getQueryParameterNames()) {
            String value = uri.getQueryParameter(name);

            if (name.equals("access_token")) {
                builder.appendQueryParameter(name, "token");
            } else if (name.equals("id")) {
                builder.appendQueryParameter(name,
                        MoreStrings.toSafeString(value));
            } else {
                builder.appendQueryParameter(name, value);
            }
        }

        return builder.toString();
    }

    /**
     * Perform an HTTP GET request and return the result as a string.
     *
     * @param context The application context
     * @param request The URL
     * @param token The OAuth2 access token
     * @return The byte array containing the response
     */
    private static byte[] httpGetRequest(Context context, String url,
            String token) throws IOException, AuthException {
        if (url == null) {
            throw new NullPointerException("URL is null");
        }

        // Rewrite the URL if needed (domain change, etc.)
        String rewrittenUrl = UrlRules.getRules(context.getContentResolver())
                .matchRule(url).apply(url);

        if (rewrittenUrl == null) {
            Log.d(TAG, "URL is blocked. Ignoring request: " + obfuscateUrl(url));
            return null;
        }

        if (!rewrittenUrl.equals(url)) {
            Log.d(TAG, "Original URL: " + obfuscateUrl(url));
            Log.d(TAG, "Rewritten URL: " + obfuscateUrl(rewrittenUrl));
        } else {
            Log.d(TAG, "Original and rewritten URL are identical.");
        }

        Log.e(TAG, "Fetching " + obfuscateUrl(rewrittenUrl));

        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(rewrittenUrl);

        request.setHeader("User-Agent", USER_AGENT);

        // Set authentication token
        if (token != null) {
            request.setHeader("Authorization", "Bearer " + token);
        }

        HttpResponse response = client.execute(request);

        int responseCode = response.getStatusLine().getStatusCode();
        if (DEBUG) Log.d(TAG, "HTTP response code: " + responseCode);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getEntity().writeTo(out);
        byte[] responseBytes = out.toByteArray();

        if (responseCode == HttpStatus.SC_OK) {
            if (DEBUG) Log.d(TAG, "Response:\n" + new String(responseBytes));
            return responseBytes;
        } else if (responseCode == HttpStatus.SC_UNAUTHORIZED) {
            Log.e(TAG, "Invalid response:\n" + new String(responseBytes));
            throw new AuthException("Failed to authenticate");
        }

        return null;
    }

    /**
     * Lookup image
     *
     * @param context The application context
     * @param url The image URL
     * @param data Extra data (a authentication token, perhaps)
     */
    @Override
    public byte[] lookupImage(Context context, String url, Object data) {
        Account account = (Account) data;

        String token = null;
        if (url.startsWith(PHOTO_URL)) {
            token = getImageToken(context, account);
            if (token == null) {
                Log.e(TAG, "Failed to get token");
            }
        }

        Uri uri = Uri.parse(url);
        Uri.Builder builder = new Uri.Builder();

        builder.scheme(uri.getScheme());
        builder.encodedAuthority(uri.getEncodedAuthority());
        builder.path(uri.getPath());

        WindowManager wm = (WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);
        Point p = new Point();
        wm.getDefaultDisplay().getSize(p);

        // Screen width
        builder.appendQueryParameter("sz", Integer.toString(p.x / 2));

        // Try twice
        int maxTries = 2;

        for (int i = 0; i < maxTries; i++) {
            try {
                return httpGetRequest(context, builder.toString(), token);
            } catch (AuthException e) {
                Log.e(TAG, "Tried " + (i + 1) + " times, " +
                        "but failed to authenticate for image lookup");
                if (i < maxTries - 1) {
                    invalidateImageToken(context, account);
                    Log.e(TAG, "Invalidating token and trying again ...");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to download image.", e);
            }
        }

        return null;
    }

    /**
     * Perform phone number lookup.
     *
     * @param context The application context
     * @param account The Google account
     * @param normalizedNumber The normalized phone number
     * @param formattedNumber The formatted phone number
     * @param includePlaces Whether places should be queried
     * @param isIncoming Whether the call is incoming or outgoing
     * @return The phone number info object
     */
    private PhoneNumberInfoImpl lookupNumberInternal(
            Context context, Account account,
            String normalizedNumber, String formattedNumber,
            boolean includePlaces, boolean isIncoming) {
        if (normalizedNumber == null) {
            throw new NullPointerException();
        }
        if (account == null) {
            throw new NullPointerException();
        }

        // Try twice
        int maxTries = 2;

        for (int i = 0; i < maxTries; i++) {
            try {
                String token = getToken(context, account);

                if (token != null) {
                    Uri uri = Uri.parse(DEBUG ? DEBUG_URL : LOOKUP_URL);
                    Uri.Builder builder = new Uri.Builder();

                    builder.scheme(uri.getScheme());
                    builder.encodedAuthority(uri.getEncodedAuthority());
                    builder.path(uri.getPath());

                    // The caller function loops through all available Google accounts.
                    // This is set to false after the first account to avoid duplicate
                    // place entries.
                    if (includePlaces) {
                        builder.appendQueryParameter("includePlaces", "1");
                    }

                    builder.appendQueryParameter("includePeople", "1");
                    builder.appendQueryParameter("includeGal", "1");
                    builder.appendQueryParameter("type", "phone");
                    builder.appendQueryParameter("fields",
                            "kind," +
                            "items(metadata(objectType,plusPageType,attributions)," +
                            "names," +
                            "phoneNumbers(value,type,formattedType,canonicalizedForm)," +
                            "addresses(value,type,formattedType)," +
                            "images(url,metadata(container))," +
                            "urls(value))");

                    builder.appendQueryParameter("callType",
                            isIncoming ? "incoming" : "outgoing");
                    builder.appendQueryParameter("id", normalizedNumber);

                    String json = new String(httpGetRequest(
                            context, builder.toString(), token));

                    if (json == null) {
                        return null;
                    } else {
                        return GoogleLookupJsonParser.parsePeopleJson(json,
                                normalizedNumber, formattedNumber, PHOTO_URL);
                    }
                }
            } catch (AuthException e) {
                Log.e(TAG, "Tried " + (i + 1) + " times, " +
                        "but failed to authenticate for number lookup");
                if (i < maxTries - 1) {
                    invalidateToken(context, account);
                    Log.e(TAG, "Invalidating token and trying again ...");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to lookup phone number", e);
                return null;
            }
        }

        return null;
    }

    /**
     * Perform phone number lookup.
     *
     * @param context The application context
     * @param normalizedNumber The normalized phone number
     * @param formattedNumber The formatted phone number
     * @param isIncoming Whether the call is incoming or outgoing
     * @return The phone number info object
     */
    @Override
    public Pair<PhoneNumberInfoImpl, Object> lookupNumber(
            Context context, String normalizedNumber, String formattedNumber,
            boolean isIncoming) {
        Account[] accounts = AccountManager.get(context)
                .getAccountsByType("com.google");
        if (accounts.length == 0) {
            Log.d(TAG, "No Google accounts found. Skipping reverse lookup.");
            return null;
        }

        boolean includePlaces = true;

        PhoneNumberInfoImpl numberInfo = null;
        Account account = null;

        for (int i = 0; i < accounts.length && i < MAX_ACCOUNTS; i++) {
            account = accounts[i];
            numberInfo = lookupNumberInternal(context, accounts[i],
                    normalizedNumber, formattedNumber, includePlaces,
                    isIncoming);

            if (numberInfo != null && numberInfo.getDisplayName() != null) {
                break;
            }

            // Include places only once to avoid duplicates
            includePlaces = false;
        }

        return Pair.create(numberInfo, (Object) account);
    }
}
