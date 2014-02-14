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

package com.android.dialer.lookup;

import com.android.dialer.lookup.ForwardLookup.ForwardLookupDetails;
import com.android.dialer.R;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.Settings;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.LinkedList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DialerProvider extends ContentProvider {
    private static final String TAG = DialerProvider.class.getSimpleName();

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_SHOW_DISTANCE = false;
    private static final boolean ALLOW_CONTACT_EXPORT = true;

    public static final Uri AUTHORITY_URI =
            Uri.parse("content://com.android.dialer.provider");
    public static final Uri FORWARD_LOOKUP_URI =
            Uri.withAppendedPath(AUTHORITY_URI, "forwardLookup");

    private static final Looper mLooper = new Handler().getLooper();
    private static final UriMatcher sURIMatcher = new UriMatcher(-1);
    private final LinkedList<FutureTask> mActiveTasks =
            new LinkedList<FutureTask>();

    static {
        sURIMatcher.addURI("com.android.dialer.provider", "forwardLookup/*", 0);
    }

    private class FutureCallable<T> implements Callable<T> {
        private final Callable<T> mCallable;
        private volatile FutureTask<T> mFuture;

        public FutureCallable(Callable<T> callable) {
            mFuture = null;
            mCallable = callable;
        }

        public T call() throws Exception {
            Log.v(TAG, "Future called for " + Thread.currentThread().getName());

            T result = mCallable.call();
            if (mFuture == null) {
                return result;
            }

            synchronized (mActiveTasks) {
                mActiveTasks.remove(mFuture);
            }

            mFuture = null;
            return result;
        }

        public void setFuture(FutureTask<T> future) {
            mFuture = future;
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, final String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        if (DEBUG) Log.v(TAG, "query: " + uri);

        int match = sURIMatcher.match(uri);

        switch (match) {
        case 0:
            Context context = getContext();
            if (!isLocationEnabled()) {
                Log.v(TAG, "Location settings is disabled, ignoring query.");
                return null;
            }

            final Location lastLocation = getLastLocation();
            if (lastLocation == null) {
                Log.v(TAG, "No location available, ignoring query.");
                return null;
            }

            final String filter = Uri.encode(uri.getLastPathSegment());
            String limit = uri.getQueryParameter("limit");

            int maxResults = -1;

            try {
                if (limit != null) {
                    maxResults = Integer.parseInt(limit);
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "query: invalid limit parameter: '" + limit + "'");
            }

            final int finalMaxResults = maxResults;

            return execute(new Callable<Cursor>() {
                @Override
                public Cursor call() {
                    return handleFilter(projection, filter, finalMaxResults,
                            lastLocation);
                }
            }, "FilterThread", 10000, TimeUnit.MILLISECONDS);
        }

        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        int match = sURIMatcher.match(uri);

        switch (match) {
        case 0:
            return Contacts.CONTENT_ITEM_TYPE;

        default:
            return null;
        }
    }

    /**
     * Check if the location services is on.
     *
     * @return Whether location services are enabled
     */
    private boolean isLocationEnabled() {
        try {
            int mode = Settings.Secure.getInt(
                getContext().getContentResolver(),
                Settings.Secure.LOCATION_MODE);

            return mode != Settings.Secure.LOCATION_MODE_OFF;
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Failed to get location mode", e);
            return false;
        }
    }

    /**
     * Get location from last location query.
     *
     * @return The last location
     */
    private Location getLastLocation() {
        LocationManager locationManager = (LocationManager)
                getContext().getSystemService(Context.LOCATION_SERVICE);

        // ACCURACY_COARSE maybe?
        locationManager.requestSingleUpdate(new Criteria(),
                new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (DEBUG) Log.v(TAG, "onLocationChanged: " + location);
            }

            @Override
            public void onProviderDisabled(String provider) {
                Log.v(TAG, "onProviderDisabled: " + provider);
            }

            @Override
            public void onProviderEnabled(String provider) {
                Log.v(TAG, "onProviderEnabled: " + provider);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.v(TAG, "onStatusChanged: "
                        + provider + ", " + status + ", " + extras);
            }
        }, DialerProvider.mLooper);

        return locationManager.getLastLocation();
    }

    /**
     * Process filter/query and perform the lookup.
     *
     * @param projection Columns to include in query
     * @param filter String to lookup
     * @param maxResults Maximum number of results
     * @param lastLocation Coordinates of last location query
     * @return Cursor for the results
     */
    private Cursor handleFilter(String[] projection, String filter,
            int maxResults, Location lastLocation) {
        if (DEBUG) Log.v(TAG, "handleFilter(" + filter + ")");

        if (filter != null) {
            try {
                filter = URLDecoder.decode(filter, "UTF-8");
            } catch (UnsupportedEncodingException e) {
            }

            ForwardLookup fl = ForwardLookup.getInstance(getContext());
            ForwardLookupDetails[] results =
                    fl.lookup(getContext(), filter, lastLocation);

            if (results == null || results.length == 0) {
                if (DEBUG) Log.v(TAG, "handleFilter(" + filter + "): No results");
                return null;
            }

            Cursor cur = null;
            try {
                cur = buildResultCursor(projection, results, maxResults);

                if (DEBUG) Log.v(TAG, "handleFilter(" + filter + "): "
                        + cur.getCount() + " matches");
            } catch (JSONException e) {
                Log.e(TAG, "JSON failure", e);
            }

            return cur;
        }

        return null;
    }

    /**
     * Query results.
     *
     * @param projection Columns to include in query
     * @param results Results for the forward lookup
     * @param maxResults Maximum number of rows/results to add to cursor
     * @return Cursor for forward lookup query results
     */
    private Cursor buildResultCursor(String[] projection,
            ForwardLookupDetails[] results, int maxResults)
            throws JSONException {
        int indexDisplayName = -1;
        int indexPhoneLabel = -1;
        int indexPhoneNumber = -1;
        int indexPhoneType = -1;
        int indexHasPhoneNumber = -1;
        int indexId = -1;
        int indexContactId = -1;
        int indexPhotoUri = -1;
        int indexPhotoThumbUri = -1;
        int indexLookupKey = -1;

        for (int i = 0; i < projection.length; i++) {
            String column = projection[i]; // v4

            if (column.equals(Contacts.DISPLAY_NAME)) {
                indexDisplayName = i;
            } else if (column.equals(Phone.LABEL)) {
                indexPhoneLabel = i;
            } else if (column.equals(Contacts.HAS_PHONE_NUMBER)) {
                indexHasPhoneNumber = i;
            } else if (column.equals(Contacts._ID)) {
                indexId = i;
            } else if (column.equals(Phone.CONTACT_ID)) {
                indexContactId = i;
            } else if (column.equals(Phone.NUMBER)) {
                indexPhoneNumber = i;
            } else if (column.equals(Phone.TYPE)) {
                indexPhoneType = i;
            } else if (column.equals(Contacts.PHOTO_URI)) {
                indexPhotoUri = i;
            } else if (column.equals(Contacts.PHOTO_THUMBNAIL_URI)) {
                indexPhotoThumbUri = i;
            } else if (column.equals(Contacts.LOOKUP_KEY)) {
                indexLookupKey = i;
            }
        }

        int exportSupport;
        if (ALLOW_CONTACT_EXPORT) {
            exportSupport = Directory.EXPORT_SUPPORT_ANY_ACCOUNT;
        } else {
            exportSupport = Directory.EXPORT_SUPPORT_NONE;
        }

        MatrixCursor cursor = new MatrixCursor(projection);

        int id = 1;

        for (int i = 0; i < results.length; i++) {
            String displayName = results[i].getDisplayName();
            String phoneNumber = results[i].getPhoneNumber();
            String address = results[i].getAddress();
            String profileUrl = results[i].getWebsite();
            String photoUri = results[i].getPhotoUri();
            String distance = results[i].getDistance();

            if (DEBUG_SHOW_DISTANCE) {
                if (distance != null) {
                    displayName = displayName + " [" + distance + " miles]";
                }
            }

            if (!phoneNumber.isEmpty()) {
                Object[] row = new Object[projection.length];

                if (indexDisplayName >= 0) {
                    row[indexDisplayName] = displayName;
                }

                if (indexPhoneLabel >= 0) {
                    row[indexPhoneLabel] = address;
                }

                if (indexHasPhoneNumber >= 0) {
                    row[indexHasPhoneNumber] = true;
                }

                if (indexContactId >= 0) {
                    row[indexContactId] = id;
                }

                if (indexPhoneNumber >= 0) {
                    row[indexPhoneNumber] = phoneNumber;
                }

                if (indexPhoneType >= 0) {
                    row[indexPhoneType] = Phone.TYPE_MAIN;
                }

                String photoThumbUri;

                // Use default place icon if no photo exists
                if (photoUri == null) {
                    photoUri = new Uri.Builder()
                            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                            .authority("com.android.dialer")
                            .appendPath(String.valueOf(
                                    R.drawable.ic_places_picture_180_holo_light))
                            .toString();

                    photoThumbUri = new Uri.Builder()
                            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                            .authority("com.android.dialer")
                            .appendPath(String.valueOf(
                                    R.drawable.ic_places_picture_holo_light))
                            .toString();
                } else {
                    photoThumbUri = photoUri;
                }

                if (indexPhotoUri >= 0) {
                    row[indexPhotoUri] = photoUri;
                }

                if (indexPhotoThumbUri >= 0) {
                    row[indexPhotoThumbUri] = photoThumbUri;
                }

                if (indexLookupKey >= 0) {
                    JSONObject contactRows = new JSONObject()
                    .put(StructuredName.CONTENT_ITEM_TYPE,
                            new JSONObject().put(
                                    StructuredName.DISPLAY_NAME, displayName))
                    .put(Phone.CONTENT_ITEM_TYPE,
                            newJsonArray(new JSONObject()
                                    .put(Phone.NUMBER, phoneNumber)
                                    .put(Phone.TYPE, Phone.TYPE_MAIN)))
                    .put(StructuredPostal.CONTENT_ITEM_TYPE,
                            newJsonArray(new JSONObject()
                                    .put(StructuredPostal.FORMATTED_ADDRESS,
                                            displayName + ", " + address)
                                    .put(StructuredPostal.TYPE,
                                            StructuredPostal.TYPE_WORK)));

                    if (profileUrl != null) {
                        contactRows.put(Website.CONTENT_ITEM_TYPE,
                                newJsonArray(new JSONObject()
                                        .put(Website.URL, profileUrl)
                                        .put(Website.TYPE, Website.TYPE_PROFILE)));
                    }

                    row[indexLookupKey] = new JSONObject()
                            .put(Contacts.DISPLAY_NAME, displayName)
                            .put(Contacts.DISPLAY_NAME_SOURCE,
                                    DisplayNameSources.ORGANIZATION)
                            .put(Directory.EXPORT_SUPPORT, exportSupport)
                            .put(Contacts.PHOTO_URI, photoUri)
                            .put(Contacts.CONTENT_ITEM_TYPE, contactRows)
                            .toString();
                }

                if (indexId >= 0) {
                    row[indexId] = id;
                }

                cursor.addRow(row);

                if (maxResults != -1 && cursor.getCount() >= maxResults) {
                    break;
                }

                id++;
            }
        }

        return cursor;
    }

    /**
     * Create new JSONArray of JSONObjects.
     *
     * @param objs JSONObjects
     * @return JSONArray of JSONObject
     */
    private static JSONArray newJsonArray(JSONObject... objs) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < objs.length; i++) {
            array.put(objs[i]);
        }
        return array;
    }

    /**
     * Execute thread that is killed after a specified amount of time.
     *
     * @param callable The thread
     * @param name Name of the thread
     * @param timeout Maximum time the thread can run
     * @param timeUnit Units of 'timeout'
     * @return Instance of the thread
     */
    private <T> T execute(Callable<T> callable, String name, long timeout,
            TimeUnit timeUnit) {
        FutureCallable<T> futureCallable = new FutureCallable<T>(callable);
        FutureTask<T> future = new FutureTask<T>(futureCallable);
        futureCallable.setFuture(future);

        synchronized (mActiveTasks) {
            mActiveTasks.addLast(future);
            Log.v(TAG, "Currently running tasks: " + mActiveTasks.size());

            while (mActiveTasks.size() > 8) {
                Log.w(TAG, "Too many tasks, canceling one");
                mActiveTasks.removeFirst().cancel(true);
            }
        }

        Log.v(TAG, "Starting task " + name);

        new Thread(future, name).start();

        try {
            Log.v(TAG, "Getting future " + name);
            return future.get(timeout, timeUnit);
        } catch (InterruptedException e) {
            Log.w(TAG, "Task was interrupted: " + name);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Log.w(TAG, "Task threw an exception: " + name, e);
        } catch (TimeoutException e) {
            Log.w(TAG, "Task timed out: " + name);
            future.cancel(true);
        } catch (CancellationException e) {
            Log.w(TAG, "Task was cancelled: " + name);
        }

        return null;
    }
}
