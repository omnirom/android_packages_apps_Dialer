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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

import libcore.io.IoUtils;

public class CachedPlacesService {
    private static final String TAG = CachedPlacesService.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String[] _PROJECTION = new String[] {
            Place.LATITUDE,                                //  0
            Place.LONGITUDE,                               //  1
            Place.PHONE_NUMBER,                            //  2
            Place.PHONE_TYPE,                              //  3
            Place.IS_BUSINESS,                             //  4
            Place.NAME,                                    //  5
            Place.ADDRESS,                                 //  6
            Place.STREET,                                  //  7
            Place.POSTAL_CODE,                             //  8
            Place.CITY,                                    //  9
            Place.EMAIL,                                   // 10
            Place.WEBSITE,                                 // 11
            Place.SOURCE,                                  // 12
            Place.NORMALIZED_NUMBER,                       // 13
            CachedPlacesDatabaseHelper.HAS_IMAGE           // 14
    };

    private static final int LATITUDE = 0;
    private static final int LONGITUDE = 1;
    private static final int PHONE_NUMBER = 2;
    private static final int PHONE_TYPE = 3;
    private static final int IS_BUSINESS = 4;
    private static final int NAME = 5;
    private static final int ADDRESS = 6;
    private static final int STREET = 7;
    private static final int POSTAL_CODE = 8;
    private static final int CITY = 9;
    private static final int EMAIL = 10;
    private static final int WEBSITE = 11;
    private static final int SOURCE = 12;
    private static final int NORMALIZED_NUMBER = 13;
    private static final int HAS_IMAGE = 14;

    /**
     * Perform a lookup using the cached places lookup service to return the place
     * stored in the cache that corresponds to the given number.
     *
     * @param context Valid context
     * @param number Phone number to lookup the cache for
     * @return A {@link Place} containing the contact information if the phone
     * number is found in the cache, {@link Place#EMPTY} if the phone number was
     * not found in the cache, and null if there was an error when querying the cache.
     */
    public Place lookupCachedPlaceFromNumber(Context context, String number) {
        Place place = queryPlace(context, number);

        // fetch only places with number and name
        if (!Place.isEmpty(place)
                && TextUtils.isEmpty(place.phoneNumber)
                && TextUtils.isEmpty(place.name)) {
            place = Place.EMPTY;
        }

        if (DEBUG) {
            Log.d(TAG, "lookupCachedPlaceFromNumber: number=" + number + ", place=" + place);
        }
        return place;
    }

    public boolean isCachedNumber(Context context, String number) {
        Place place = queryPlace(context, number);
        boolean result = !Place.isEmpty(place);

        if (DEBUG) {
            Log.d(TAG, "isCachedNumber: number=" + number + ", result=" + result);
        }
        return result;
    }

    /**
     * Perform a lookup using the cached places lookup service to return the place
     * stored in the cache that corresponds to the given number.
     *
     * @param context Valid context
     * @param number Phone number to lookup the cache for
     * @return A {@link Place} containing the contact information if the phone
     * number is found in the cache, or {@link Place#EMPTY} if the phone number was
     * not found in the cache, and null if there was an error when querying the cache.
     */
    private Place queryPlace(Context context, String number) {
        Place place = null;
        try {
            Uri uri = CachedPlacesProvider.getContactLookupUri(number);
            if (DEBUG) {
                Log.d(TAG, "queryPlace: number=" + number
                        + ", uri=" + uri);
            }
            Cursor cursor = context.getContentResolver().query(
                    uri, _PROJECTION, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    String normalizedNumber = cursor.getString(NORMALIZED_NUMBER);

                    place = new Place();
                    place.latitude = cursor.getDouble(LATITUDE);
                    place.longitude = cursor.getDouble(LONGITUDE);
                    place.phoneNumber = cursor.getString(PHONE_NUMBER);
                    place.normalizedNumber = normalizedNumber;
                    place.phoneType = cursor.getInt(PHONE_TYPE);
                    place.isBusiness = cursor.getInt(IS_BUSINESS) != 0;
                    place.name = cursor.getString(NAME);
                    place.address = cursor.getString(ADDRESS);
                    place.street = cursor.getString(STREET);
                    place.postalCode = cursor.getString(POSTAL_CODE);
                    place.city = cursor.getString(CITY);
                    place.email = cursor.getString(EMAIL);
                    place.website = cursor.getString(WEBSITE);
                    place.source = cursor.getString(SOURCE);

                    if (cursor.getInt(HAS_IMAGE) == 1) {
                        place.imageUri = CachedPlacesProvider.getImageLookupUri(normalizedNumber);
                    }
                } else {
                    place = Place.EMPTY;
                }
                cursor.close();
            }
        } catch (Exception ignored) {
        }

        return place;
    }

    public void addPlace(Context context, Place place) {
        if (DEBUG) {
            Log.d(TAG, "addPlace: place=" + place);
        }
        if (!Place.isEmpty(place)) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Place.LATITUDE, place.latitude);
            contentValues.put(Place.LONGITUDE, place.longitude);
            contentValues.put(Place.PHONE_NUMBER, place.phoneNumber);
            contentValues.put(Place.NORMALIZED_NUMBER, place.normalizedNumber);
            contentValues.put(Place.PHONE_TYPE, place.phoneType);
            contentValues.put(Place.IS_BUSINESS, place.isBusiness ? 1 : 0);
            contentValues.put(Place.NAME, place.name);
            contentValues.put(Place.ADDRESS, place.address);
            contentValues.put(Place.STREET, place.street);
            contentValues.put(Place.POSTAL_CODE, place.postalCode);
            contentValues.put(Place.CITY, place.city);
            contentValues.put(Place.EMAIL, place.email);
            contentValues.put(Place.WEBSITE, place.website);
            contentValues.put(Place.SOURCE, place.source);

            context.getContentResolver().insert(CachedPlacesProvider.PLACE_URI, contentValues);
        }
    }

    public void touchNumber(Context context, String phoneNumber) {
        if (DEBUG) {
            Log.d(TAG, "touchNumber: number=" + phoneNumber);
        }

        ContentValues contentValues = new ContentValues();
        contentValues.put(Place.PHONE_NUMBER, phoneNumber);

        context.getContentResolver().insert(CachedPlacesProvider.PLACE_URI, contentValues);
    }

    public boolean addImage(Context context, String number, byte[] image) {
        boolean success = false;

        Uri uri = CachedPlacesProvider.getImageLookupUri(number);
        if (DEBUG) {
            Log.d(TAG, "addImage: number=" + number + ", uri=" + uri);
        }
        OutputStream outputStream = null;
        try {
            outputStream = context.getContentResolver().openOutputStream(uri);
            if (outputStream != null) {
                outputStream.write(image);
                success = true;
            }
        } catch (IOException ignored) {
        } finally {
            IoUtils.closeQuietly(outputStream);
        }

        return success;
    }

    /**
     * Remove all cached phone number entries from the cache, regardless of how old they
     * are.
     *
     * @param context Valid context
     */
    public void clearAllCacheEntries(Context context) {
        CachedPlacesDatabaseHelper.getInstance(context).deleteAll();
    }

    public boolean isCacheUri(String uri) {
        return uri != null
                && uri.startsWith(CachedPlacesProvider.AUTHORITY_URI.toString());
    }
}
