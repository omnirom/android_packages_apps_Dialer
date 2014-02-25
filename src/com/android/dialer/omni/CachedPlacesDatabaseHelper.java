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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class CachedPlacesDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = CachedPlacesDatabaseHelper.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "places.db";
    public static final String TABLE = "cached_number_contacts";

    public static final String HAS_IMAGE = "has_image";
    public static final String TIME_LAST_UPDATED = "time_last_updated";

    // 30 days
    public static final long MAX_LIFETIME = 30L * 24L * 60L * 60L * 1000L;

    private static CachedPlacesDatabaseHelper sSingleton = null;

    protected CachedPlacesDatabaseHelper(Context context, String databaseName) {
        super(context, databaseName, null, DATABASE_VERSION);
    }

    public static synchronized CachedPlacesDatabaseHelper getInstance(Context context) {
        if (DEBUG) {
            Log.v(TAG, "Getting Instance");
        }
        if (sSingleton == null) {
            // Use application context instead of activity context because this is a singleton,
            // and we don't want to leak the activity if the activity is not running but the
            // dialer database helper is still doing work.
            sSingleton = new CachedPlacesDatabaseHelper(context.getApplicationContext(),
                    DATABASE_NAME);
        }
        return sSingleton;
    }

    private void setupTable(SQLiteDatabase database) {
        dropTable(database);
        database.execSQL("CREATE TABLE " + TABLE + " (" +
                Place.NORMALIZED_NUMBER + " TEXT PRIMARY KEY NOT NULL, " +
                Place.LATITUDE + " REAL DEFAULT 0, " +
                Place.LONGITUDE + " REAL DEFAULT 0, " +
                Place.PHONE_NUMBER + " TEXT, " +
                Place.PHONE_TYPE + " INTEGER DEFAULT 0, " +
                Place.IS_BUSINESS + " INTEGER DEFAULT 0, " +
                Place.NAME + " TEXT, " +
                Place.ADDRESS + " TEXT, " +
                Place.STREET + " TEXT, " +
                Place.POSTAL_CODE + " TEXT, " +
                Place.CITY + " TEXT, " +
                Place.EMAIL + " TEXT, " +
                Place.WEBSITE + " TEXT, " +
                Place.SOURCE + " TEXT, " +
                HAS_IMAGE + " INTEGER DEFAULT 0, " +
                TIME_LAST_UPDATED + " LONG NOT NULL); ");
        database.execSQL("CREATE INDEX cached_place_index ON " + TABLE +
                " (" + Place.NORMALIZED_NUMBER + ");");
    }

    public void dropTable(SQLiteDatabase database) {
        database.execSQL("DROP TABLE IF EXISTS " + TABLE);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        setupTable(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion +
                " to " + newVersion + ", which will destroy all old data");
        setupTable(database);
    }

    public void deleteOldPlaces() {
        // 30 days
        deleteOldPlaces(MAX_LIFETIME);
    }

    public void deleteOldPlaces(long time) {
        String[] args = new String[] { Long.toString(System.currentTimeMillis() - time) };
        if (DEBUG) {
            Log.d(TAG, "deleteOldPlaces: " + args[0]);
        }
        getWritableDatabase().delete(TABLE, TIME_LAST_UPDATED + "<?", args);
    }

    public void deleteAll() {
        if (DEBUG) {
            Log.d(TAG, "deleteAll");
        }
        getWritableDatabase().delete(TABLE, null, null);
    }

    public void deleteSource(String source) {
        if (DEBUG) {
            Log.d(TAG, "deleteSource: " + source);
        }
        String[] args = new String[] { source };
        getWritableDatabase().delete(TABLE, Place.SOURCE + "=?", args);
    }

}
