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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.telephony.TelephonyManager;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Request URLs without going through Google Play Services because we need to
 * imitate the package and signature for com.google.android.dialer in order to
 * get the needed data.
 */
public class GoogleAuth {
    private static GoogleAuth INSTANCE = null;

    private Context mContext = null;

    /** Authentication URL */
    private static final String AUTH_URL =
            "https://android.clients.google.com/auth";

    /** Token revoke URL */
    private static final String REVOKE_URL =
            "https://accounts.google.com/o/oauth2/revoke?token=";

    /** Google Dialer package name */
    private static final String PACKAGE = "com.google.android.dialer";

    /** Google's signing key certificate fingerprint
      * https://androidobservatory.org/cert/38918A453D07199354F8B19AF05EC6562CED5788 */
    private static final String FINGERPRINT = "38918a453d07199354f8b19af05ec6562ced5788";

    private static final String ACCOUNT_TYPE = "HOSTED_OR_GOOGLE";

    private String mDeviceCountry;
    private String mOperatorCountry;
    private String mLanguage;

    private static final String mPlayServicesVersion = "4132538";
    private static final String mSystemPartition = "1";
    private static final String mHasPermission = "1";
    private static final String mSource = "android";

    private static final String KEY_DEVICE_COUNTRY = "device_country";
    private static final String KEY_OPERATOR_COUNTRY = "operatorCountry";
    private static final String KEY_LANG = "lang";
    private static final String KEY_SDK_VERSION = "sdk_version";
    private static final String KEY_PLAY_SERVICES_VERSION = "google_play_services_version";
    private static final String KEY_ACCOUNT_TYPE = "accountType";
    private static final String KEY_SYSTEM_PARTITION = "system_partition";
    private static final String KEY_EMAIL = "Email";
    private static final String KEY_HAS_PERMISSION = "has_permission";
    private static final String KEY_SERVICE = "service";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_ANDROID_ID = "androidId";
    private static final String KEY_APP = "app";
    private static final String KEY_CLIENT_SIGNATURE = "client_sig";
    private static final String KEY_CALLER_PACKAGE = "callerPkg";
    private static final String KEY_CALLER_SIGNATURE = "callerSig";
    private static final String KEY_TOKEN = "Token";

    public GoogleAuth(Context context) {
        mContext = context.getApplicationContext();

        mDeviceCountry = context.getResources().getConfiguration().locale.getCountry();

        TelephonyManager tm = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        mOperatorCountry = tm.getNetworkCountryIso();
        //mOperatorCountry = tm.getSimCountryIso();

        mLanguage = Locale.getDefault().toString();
    }

    public static GoogleAuth getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new GoogleAuth(context);
        }
        return INSTANCE;
    }

    /**
     * Get OAuth2 access token
     *
     * @param scope OAuth2 scope
     * @return The OAuth2 access token
     */
    public String getToken(Account account, String scope) {
        String refreshToken = getRefreshToken(account);

        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(AUTH_URL);

        try {
            List<NameValuePair> pairs = new ArrayList<NameValuePair>(1);
            pairs.add(new BasicNameValuePair(KEY_DEVICE_COUNTRY, mDeviceCountry));
            pairs.add(new BasicNameValuePair(KEY_OPERATOR_COUNTRY, mOperatorCountry));
            pairs.add(new BasicNameValuePair(KEY_LANG, mLanguage));
            pairs.add(new BasicNameValuePair(KEY_SDK_VERSION,
                    Integer.toString(Build.VERSION.SDK_INT)));
            pairs.add(new BasicNameValuePair(KEY_PLAY_SERVICES_VERSION,
                    mPlayServicesVersion));
            pairs.add(new BasicNameValuePair(KEY_ACCOUNT_TYPE, ACCOUNT_TYPE));
            pairs.add(new BasicNameValuePair(KEY_SYSTEM_PARTITION, mSystemPartition));
            pairs.add(new BasicNameValuePair(KEY_EMAIL, account.name));
            pairs.add(new BasicNameValuePair(KEY_HAS_PERMISSION, mHasPermission));
            pairs.add(new BasicNameValuePair(KEY_SERVICE, scope));
            pairs.add(new BasicNameValuePair(KEY_SOURCE, mSource));
            pairs.add(new BasicNameValuePair(KEY_ANDROID_ID, getAndroidId()));
            pairs.add(new BasicNameValuePair(KEY_APP, PACKAGE));
            pairs.add(new BasicNameValuePair(KEY_CLIENT_SIGNATURE, FINGERPRINT));
            pairs.add(new BasicNameValuePair(KEY_CALLER_PACKAGE, PACKAGE));
            pairs.add(new BasicNameValuePair(KEY_CALLER_SIGNATURE, FINGERPRINT));
            pairs.add(new BasicNameValuePair(KEY_TOKEN, refreshToken));

            post.setEntity(new UrlEncodedFormEntity(pairs));

            HttpResponse response = client.execute(post);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()));

            // OAuth2 access token
            String token = null;

            String line;
            while ((line = in.readLine()) != null) {
                if (line.length() > 5 && line.startsWith("Auth=")) {
                    token = line.substring(5);
                }
            }

            return token;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Invalidate a OAuth2 access token
     *
     * @param token The token
     */
    public void invalidateToken(String token) {
        String url = REVOKE_URL + token;
        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(url);

        try {
            HttpResponse response = client.execute(request);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the OAuth2 refresh token for a Google account.
     *
     * @param account The Google account
     * @return The refresh token
     */
    private String getRefreshToken(Account account) {
        AccountManager am = AccountManager.get(mContext);
        // Google stores the token in the password field
        return am.getPassword(account);
    }

    /**
     * Get Android ID (ie. GTalk ID) from Gservices.
     *
     * @return Android ID
     */
    private String getAndroidId() {
        // Not a problem for installations without gapps since this code won't
        // be called if there are no Google accounts registered in
        // AccountManager
        String[] query = new String[] { "android_id" };
        Cursor cursor = mContext.getContentResolver().query(
                Uri.parse("content://com.google.android.gsf.gservices"),
                null, null, query, null);

        if (cursor.moveToFirst() && cursor.getColumnCount() >= 2) {
            return Long.toHexString(Long.parseLong(cursor.getString(1)));
        }

        return null;
    }
}
