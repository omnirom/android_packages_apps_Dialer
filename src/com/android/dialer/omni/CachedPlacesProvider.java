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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.PathPermission;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.GeoUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;


public class CachedPlacesProvider extends ContentProvider {
    private static final String TAG = CachedPlacesProvider.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final String AUTHORITY = "com.android.dialer.omni.cachedplaces";
    public static final Uri AUTHORITY_URI;
    public static final Uri PLACE_URI;
    public static final Uri IMAGE_URI;

    static {
        AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);
        PLACE_URI = Uri.withAppendedPath(AUTHORITY_URI, "place");
        IMAGE_URI = Uri.withAppendedPath(AUTHORITY_URI, "image");
    }

    private static final int URI_BASE = 0;
    private static final int URI_PLACE = 1;
    private static final int URI_PLACE_WITH_ID = 2;
    private static final int URI_IMAGE_WITH_ID = 3;

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sURIMatcher.addURI(AUTHORITY, "place",   URI_PLACE);
        sURIMatcher.addURI(AUTHORITY, "place/*", URI_PLACE_WITH_ID);
        sURIMatcher.addURI(AUTHORITY, "image/*", URI_IMAGE_WITH_ID);
    }

    private CachedPlacesDatabaseHelper mDatabaseHelper;
    private File mImageDir;

    public CachedPlacesProvider() {
    }

    public CachedPlacesProvider(Context context, String readPermission,
            String writePermission, PathPermission[] pathPermissions) {
        super(context, readPermission, writePermission, pathPermissions);

    }

    public static Uri getContactLookupUri(String number) {
        return PLACE_URI.buildUpon().appendPath(number).build();
    }

    public static Uri getImageLookupUri(String number) {
        return IMAGE_URI.buildUpon().appendPath(number).build();
    }

    @Override
    public boolean onCreate() {
        mDatabaseHelper = CachedPlacesDatabaseHelper.getInstance(getContext());
        mImageDir = createDirectory();
        return true;
    }

    private File createDirectory() {
        File dir = new File(getContext().getFilesDir(), "images");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Unable to create image storage directory " + dir);
        }
        return dir;
    }

    @Override
    public Cursor query(Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        Cursor cursor = null;
        if (sURIMatcher.match(uri) == URI_PLACE_WITH_ID) {
            String normalizedNumber = getNumberFromUri(uri);
            if (normalizedNumber != null)
            {
                deleteOldPlaces();
                String[] args = new String[] { normalizedNumber };
                cursor = mDatabaseHelper.getWritableDatabase().query(
                        CachedPlacesDatabaseHelper.TABLE, projection,
                        Place.NORMALIZED_NUMBER + "=?" +
                                " AND " + Place.PHONE_NUMBER + " NOT NULL" +
                                " AND " + Place.NAME + " NOT NULL",
                        args,
                        null, null, null);
            }
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (DEBUG) {
            Log.d(TAG, "insert: uri=" + uri + ", values=" + values);
        }

        int match = sURIMatcher.match(uri);
        if (match != URI_PLACE && match != URI_PLACE_WITH_ID) {
            throw new IllegalArgumentException("Unknown URI");
        }

        deleteOldPlaces();

        String normalizedNumber;
        if (match == URI_PLACE) {
            normalizedNumber = getNumberFromValues(values);
        } else {
            normalizedNumber = getNumberFromUri(uri);
        }

        if (TextUtils.isEmpty(normalizedNumber)) {
            Log.w(TAG, "Can not cache place without number");
            return uri;
        }

        values.put(Place.NORMALIZED_NUMBER, normalizedNumber);
        values.put(CachedPlacesDatabaseHelper.TIME_LAST_UPDATED,
                Long.valueOf(System.currentTimeMillis()));

        if (DEBUG) {
            Log.d(TAG, "insert: number=" + normalizedNumber);
            Log.d(TAG, "insert: uri=" + uri);
            Log.d(TAG, "insert: values=" + values);
        }

        try {
            mDatabaseHelper.getWritableDatabase().insertWithOnConflict(
                    CachedPlacesDatabaseHelper.TABLE, null, values,
                    SQLiteDatabase.CONFLICT_FAIL);
        } catch (SQLException e) {
            // number already cached, update the row
            if (DEBUG) {
                Log.e(TAG, "Updating existing cached place");
            }
            String[] args = new String[] { normalizedNumber };
            update(uri, values, Place.NORMALIZED_NUMBER + "=?", args);
        }

        return uri;
    }

    public void deleteOldPlaces() {
        // 30 days
        deleteOldPlaces(CachedPlacesDatabaseHelper.MAX_LIFETIME);
    }

    public void deleteOldPlaces(long time) {
        String[] args = new String[] { Long.toString(System.currentTimeMillis() - time) };
        String where = CachedPlacesDatabaseHelper.TIME_LAST_UPDATED + "<?";
        if (DEBUG) {
            Log.d(TAG, "deleteOldPlaces: " + where + args[0]);
        }

        String[] projection = new String[] { Place.NORMALIZED_NUMBER };

        Cursor cursor = mDatabaseHelper.getReadableDatabase().query(
                CachedPlacesDatabaseHelper.TABLE, projection, where, args, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String normalizedNumber = cursor.getString(0);
                deleteFiles(normalizedNumber);
            }
        }

        mDatabaseHelper.getWritableDatabase().delete(CachedPlacesDatabaseHelper.TABLE, where, args);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (sURIMatcher.match(uri) != URI_PLACE_WITH_ID) {
            throw new IllegalArgumentException("Unknown URI");
        }

        String normalizedNumber = getNumberFromUri(uri);
        String[] args = new String[] { normalizedNumber };
        deleteFiles(normalizedNumber);
        return mDatabaseHelper.getWritableDatabase().delete(CachedPlacesDatabaseHelper.TABLE,
                Place.NORMALIZED_NUMBER + "=?", args);
    }

    private void deleteFiles(String phoneNumber) {
        getFileForNumber(phoneNumber).delete();
    }

    private File getFileForNumber(String phoneNumber) {
        return new File(mImageDir, phoneNumber);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (DEBUG) {
            Log.d(TAG, "update: uri=" + uri + ", values=" + values);
        }

        int match = sURIMatcher.match(uri);
        if (match != URI_PLACE && match != URI_PLACE_WITH_ID) {
            throw new IllegalArgumentException("Unknown URI");
        }

        String normalizedNumber;
        if (match == URI_PLACE) {
            normalizedNumber = getNumberFromValues(values);
        } else {
            normalizedNumber = getNumberFromUri(uri);
        }

        if (TextUtils.isEmpty(normalizedNumber)) {
            Log.w(TAG, "Can not update place without number");
            return 0;
        }

        values.put(Place.NORMALIZED_NUMBER, normalizedNumber);
        values.put(CachedPlacesDatabaseHelper.TIME_LAST_UPDATED,
                Long.valueOf(System.currentTimeMillis()));

        if (DEBUG) {
            Log.d(TAG, "update: number=" + normalizedNumber);
            Log.d(TAG, "update: uri=" + uri);
            Log.d(TAG, "update: values=" + values);
        }

        return mDatabaseHelper.getWritableDatabase().update(
                CachedPlacesDatabaseHelper.TABLE, values,
                selection, selectionArgs);
    }

    private String getNumberFromUri(Uri uri)
    {
        if (uri.getPathSegments().size() != 2) {
            throw new IllegalArgumentException("Invalid URI or phone number not provided");
        } else {
            String phoneNumber = uri.getLastPathSegment();
            return getFormattedNumber(phoneNumber);
        }
    }

    private String getNumberFromValues(ContentValues values) {
        String phoneNumber = values.getAsString(Place.NORMALIZED_NUMBER);
        if (TextUtils.isEmpty(phoneNumber)) {
            phoneNumber = values.getAsString(Place.PHONE_NUMBER);
            phoneNumber = getFormattedNumber(phoneNumber);
        }
        if (TextUtils.isEmpty(phoneNumber)) {
            throw new IllegalArgumentException("Phone number not provided");
        } else {
            return phoneNumber;
        }
    }

    private String getFormattedNumber(String phoneNumber) {
        String countryCode = GeoUtil.getCurrentCountryIso(getContext());
        return PhoneNumberUtils.formatNumberToE164(phoneNumber, countryCode);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (sURIMatcher.match(uri) != URI_IMAGE_WITH_ID) {
            throw new FileNotFoundException("Unknown or unsupported URI");
        }

        String normalizedNumber = getNumberFromUri(uri);
        if ("r".equals(mode)) {
            return openImageForRead(normalizedNumber);
        } else {
            return openImageForWrite(normalizedNumber);
        }
    }

    private ParcelFileDescriptor openImageForRead(String phoneNumber)
            throws FileNotFoundException {
        File file = getFileForNumber(phoneNumber);
        if (file.exists()) {
            try {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            } catch (FileNotFoundException ignoreException) {
            }
        }

        // no image found
        setHasImage(phoneNumber, false);
        throw new FileNotFoundException("File not found for cached image: " + file);
    }

    private ParcelFileDescriptor openImageForWrite(String phoneNumber) {
        ParcelFileDescriptor fileDescriptor = null;
        File file = getFileForNumber(phoneNumber);
        try {
            if (file.exists())
            {
                file.delete();
            }

            file.createNewFile();
            setHasImage(phoneNumber, true);
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
        } catch (IOException ex) {
            Log.w(TAG, "Failed to create new file for cached image: " + file, ex);
        }
        return fileDescriptor;
    }

    public void setHasImage(String phoneNumber, boolean hasImage) {
        ContentValues values = new ContentValues();
        values.put(CachedPlacesDatabaseHelper.HAS_IMAGE, hasImage ? "1" : "0");
        String[] args = new String[] { phoneNumber };
        mDatabaseHelper.getWritableDatabase().update(CachedPlacesDatabaseHelper.TABLE, values,
                Place.NORMALIZED_NUMBER + "=?", args);
    }
}
