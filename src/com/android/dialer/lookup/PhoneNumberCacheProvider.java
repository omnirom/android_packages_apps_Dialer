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

import com.android.contacts.common.GeoUtil;
import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.database.DialerDatabaseHelper.CachedContactsColumns;
import com.android.dialer.database.DialerDatabaseHelper.SmartDialDbColumns;
import com.android.dialer.database.DialerDatabaseHelper.Tables;
import com.android.dialer.service.CachedNumberLookupServiceImpl;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PathPermission;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class PhoneNumberCacheProvider extends ContentProvider {
    private static final String TAG = PhoneNumberCacheProvider.class.getSimpleName();
    private static final String AUTHORITY = "com.android.dialer.cacheprovider";

    public static final int CONTACT_ID = 1000;
    public static final int CONTACT = 1001;
    public static final int PHOTO = 2000;
    public static final int THUMBNAIL = 3000;

    private static final Set<String> SUPPORTED_UPDATE_COLUMNS
            = new HashSet<String>();
    private static final UriMatcher sUriMatcher = new UriMatcher(-1);
    private final String[] mArgs1;
    private DialerDatabaseHelper mDbHelper;
    private File mPhotoPath;
    private File mThumbnailPath;

    static {
        sUriMatcher.addURI(AUTHORITY, "contact", CONTACT_ID);
        sUriMatcher.addURI(AUTHORITY, "contact/*", CONTACT);
        sUriMatcher.addURI(AUTHORITY, "photo/*", PHOTO);
        sUriMatcher.addURI(AUTHORITY, "thumbnail/*", THUMBNAIL);
        SUPPORTED_UPDATE_COLUMNS.add(CachedContactsColumns.NUMBER);
        SUPPORTED_UPDATE_COLUMNS.add(CachedContactsColumns.PHONE_TYPE);
        SUPPORTED_UPDATE_COLUMNS.add(CachedContactsColumns.PHONE_LABEL);
        SUPPORTED_UPDATE_COLUMNS.add(SmartDialDbColumns.DISPLAY_NAME_PRIMARY);
        SUPPORTED_UPDATE_COLUMNS.add(CachedContactsColumns.PHOTO_URI);
        SUPPORTED_UPDATE_COLUMNS.add(CachedContactsColumns.SOURCE_NAME);
        SUPPORTED_UPDATE_COLUMNS.add(CachedContactsColumns.SOURCE_TYPE);
        SUPPORTED_UPDATE_COLUMNS.add(CachedContactsColumns.SOURCE_ID);
        SUPPORTED_UPDATE_COLUMNS.add(CachedContactsColumns.LOOKUP_KEY);
    }

    public PhoneNumberCacheProvider() {
        mArgs1 = new String[1];
    }

    public PhoneNumberCacheProvider(Context context, String readPermission,
            String writePermission, PathPermission[] pathPermissions) {
        super(context, readPermission, writePermission, pathPermissions);
        mArgs1 = new String[1];
    }

    @Override
    public boolean onCreate() {
        mDbHelper = DialerDatabaseHelper.getInstance(getContext());
        createPhotoDirectoriesIfDoNotExist();
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        int type = sUriMatcher.match(uri);

        if (type == CONTACT) {
            String number = getNumberFromUri(uri);

            if (number != null) {
                mDbHelper.prune();
                mArgs1[0] = number;
                return mDbHelper.getWritableDatabase().query(
                        Tables.CACHED_CONTACTS, projection,
                        CachedContactsColumns.NORMALIZED_NUMBER + "=?",
                        mArgs1, null, null, null);
            }
        }

        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        int type = sUriMatcher.match(uri);

        switch (type) {
        case CONTACT_ID:
        case CONTACT:
            String number;
            if (type == CONTACT_ID) {
                number = getNumberFromValues(values);
            } else {
                number = getNumberFromUri(uri);
            }

            for (String key : values.keySet()) {
                if (!SUPPORTED_UPDATE_COLUMNS.contains(key)) {
                    values.remove(key);
                    Log.e(TAG, "Ignoring unsupported column for update: " + key);
                }
            }

            mDbHelper.prune();
            values.put(CachedContactsColumns.NORMALIZED_NUMBER, number);
            values.put(CachedContactsColumns.TIME_LAST_UPDATED,
                    System.currentTimeMillis());

            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            mArgs1[0] = number;
            Integer sourceType = values.getAsInteger(
                    CachedContactsColumns.SOURCE_TYPE);

            boolean overrideCache = false;

            if (sourceType != null && CachedNumberLookupServiceImpl
                    .CachedContactInfoImpl.isLookupSource(sourceType)) {
                overrideCache = true;
            } else {
                int prevSourceType = -1;

                try {
                    prevSourceType = (int) DatabaseUtils.longForQuery(db,
                            "SELECT " + CachedContactsColumns.SOURCE_TYPE +
                            " FROM " + Tables.CACHED_CONTACTS + " WHERE " +
                            CachedContactsColumns.NORMALIZED_NUMBER + "=?",
                            mArgs1);
                } catch (SQLiteDoneException ex) {
                }

                boolean peopleApiSource = CachedNumberLookupServiceImpl
                        .CachedContactInfoImpl.isLookupSource(
                        prevSourceType);

                if (!peopleApiSource) {
                    overrideCache = true;
                }
            }

            if (overrideCache) {
                db.insertWithOnConflict(Tables.CACHED_CONTACTS,
                        null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }

            return uri;

        default:
            throw new IllegalArgumentException("Unknown URI");
        }
    }

    @Override
    public int update(Uri uri, ContentValues values,
            String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException(
                "The cache does not support update operations."
                + " Use insert to replace an existing phone number, if needed.");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (sUriMatcher.match(uri) == CONTACT) {
            mDbHelper.prune();

            String number = getNumberFromUri(uri);
            mArgs1[0] = number;
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            deleteFiles(number);
            return db.delete(Tables.CACHED_CONTACTS,
                    CachedContactsColumns.NORMALIZED_NUMBER
                    + "=?", mArgs1);
        }
        throw new IllegalArgumentException(
                "Unknown URI or phone number not provided");
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        int type = sUriMatcher.match(uri);

        switch (type) {
        case PHOTO:
        case THUMBNAIL:
            String number = getNumberFromUri(uri);

            if (!isNumberInCache(number)) {
                throw new FileNotFoundException("Phone number does not exist in cache");
            }

            if (mode.equals("r")) {
                return openFileForRead(number, type == PHOTO);
            } else {
                return openFileForWrite(number, type == PHOTO);
            }

        default:
            throw new FileNotFoundException("Unknown or unsupported URI");
        }
    }

    /**
     * Create directories for storing cached photos and thumbnails.
     */
    private void createPhotoDirectoriesIfDoNotExist() {
        mPhotoPath = new File(getContext().getFilesDir(), "photos/raw");
        mThumbnailPath = new File(getContext().getFilesDir(), "thumbnails/raw");
        createDirectoryIfDoesNotExist(mPhotoPath);
        createDirectoryIfDoesNotExist(mThumbnailPath);
    }

    /**
     * Create a directory if it doesn't already exist.
     *
     * @param dir File object representing the directory
     */
    private void createDirectoryIfDoesNotExist(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException(
                    "Unable to create photo storage directory " + dir.getPath());
        }
    }

    /**
     * Get phone number from ContentValues and return it E-164 formatted.
     *
     * @param values The ContentValues containing the phone number
     * @return The phone number
     */
    private String getNumberFromValues(ContentValues values) {
        String number = values.getAsString("number");

        if (number == null || number.length() == 0) {
            throw new IllegalArgumentException("Phone number not provided");
        }

        return getE164Number(number);
    }

    /**
     * Get phone number of content URI.
     *
     * @param uri The URI
     * @return A string containing the E-164 formatted phone number
     */
    private String getNumberFromUri(Uri uri) {
        if (uri.getPathSegments().size() != 2) {
            throw new IllegalArgumentException(
                    "Invalid URI or phone number not provided");
        }
        return getE164Number(uri.getLastPathSegment());
    }

    /**
     * Format phone number as E-164.
     *
     * @param number The phone number to format
     * @return The E-164 formatted phone number
     */
    private String getE164Number(String number) {
        return PhoneNumberUtils.formatNumberToE164(number,
                GeoUtil.getCurrentCountryIso(getContext()));
    }

    /**
     * Check if phone number is in the cache.
     *
     * @param number The phone number to check
     * @return Whether the number is in the cache
     */
    private boolean isNumberInCache(String number) {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        mArgs1[0] = number;
        long entries = DatabaseUtils.queryNumEntries(db,
                DialerDatabaseHelper.Tables.CACHED_CONTACTS,
                DialerDatabaseHelper.CachedContactsColumns
                        .NORMALIZED_NUMBER + "=?", mArgs1);

        return entries > 0;
    }

    /**
     * Open photo or thumbnail file for a phone number for reading.
     *
     * @param number The phone number
     * @param fullPhoto Whether to open the photo or thumbnail
     * @return The ParcelFileDescriptor for the file
     */
    private ParcelFileDescriptor openFileForRead(
            String number, boolean fullPhoto) throws FileNotFoundException {
        File file;

        if (fullPhoto) {
            file = getPhotoForNumber(number);
        } else {
            file = getThumbnailForNumber(number);
        }

        if (file.exists()) {
            return ParcelFileDescriptor.open(file,
                    ParcelFileDescriptor.MODE_READ_ONLY);
        } else {
            setHavePhoto(number, fullPhoto, false);
            throw new FileNotFoundException(
                    "No photo file found for number: " + number);
        }
    }

    /**
     * Open photo or thumbnail file for a phone number for writing.
     *
     * @param number The phone number
     * @param fullPhoto Whether to open the photo or thumbnail
     * @return The ParcelFileDescriptor for the file
     */
    private ParcelFileDescriptor openFileForWrite(
            String number, boolean fullPhoto) {
        File file;

        if (fullPhoto) {
            file = getPhotoForNumber(number);
        } else {
            file = getThumbnailForNumber(number);
        }

        try {
            if (!file.exists()) {
                file.createNewFile();
                setHavePhoto(number, fullPhoto, true);
            }

            return ParcelFileDescriptor.open(file,
                    ParcelFileDescriptor.MODE_READ_WRITE);
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Failed create new file for cached photo.", e);
            return null;
        }
    }

    /**
     * Get File object for photo in the photo cache directory.
     *
     * @param number The phone number
     * @return File object for photo
     */
    private File getPhotoForNumber(String number) {
        return new File(mPhotoPath, number);
    }

    /**
     * Get File object for thumbnail in the thumbnail cache directory.
     *
     * @param number The phone number
     * @return File object for thumbnail
     */
    private File getThumbnailForNumber(String number) {
        return new File(mThumbnailPath, number);
    }

    /**
     * Write whether the photo or thumbnail for a phone number exists to the
     * database.
     *
     * @param number The phone number
     * @param fullPhoto Whether to set for the photo or thumbnail
     * @param haveFile Whether the file exists
     */
    private void setHavePhoto(String number, boolean fullPhoto, boolean haveFile) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        mArgs1[0] = number;
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE " + Tables.CACHED_CONTACTS + " SET ");

        if (fullPhoto) {
            sb.append(CachedContactsColumns.HAS_PHOTO);
        } else {
            sb.append(CachedContactsColumns.HAS_THUMBNAIL);
        }

        sb.append("=");
        sb.append(haveFile ? "1" : "0");
        sb.append(" WHERE ");
        sb.append(CachedContactsColumns.NORMALIZED_NUMBER + "=?");
        sb.append(";");

        db.execSQL(sb.toString(), mArgs1);
    }

    /**
     * Delete photo or thumbnail file for phone number.
     *
     * @param number The phone number
     * @param fullPhoto Whether to delete the photo or thumbnail
     * @return Whether the delete operation succeeded
     */
    private boolean deleteFile(String number, boolean fullPhoto) {
        File file;

        if (fullPhoto) {
            file = getPhotoForNumber(number);
        } else {
            file = getThumbnailForNumber(number);
        }

        return file.delete();
    }

    /**
     * Delete photo and thumbnail for phone number.
     *
     * @param number The phone number
     * @return Whether the delete operation succeeded
     */
    private boolean deleteFiles(String number) {
        boolean deletedPhoto = deleteFile(number, true);
        boolean deletedThumbnail = deleteFile(number, false);

        if (!deletedPhoto && !deletedThumbnail) {
            return false;
        } else {
            return true;
        }
    }
}
