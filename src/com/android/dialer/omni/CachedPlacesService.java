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
            Place.STREET,                                  //  6
            Place.POSTAL_CODE,                             //  7
            Place.CITY,                                    //  8
            Place.EMAIL,                                   //  9
            Place.SOURCE,                                  // 10
            CachedPlacesDatabaseHelper.NORMALIZED_NUMBER,  // 11
            CachedPlacesDatabaseHelper.HAS_IMAGE           // 12
    };

    private static final int LATITUDE = 0;
    private static final int LONGITUDE = 1;
    private static final int PHONE_NUMBER = 2;
    private static final int PHONE_TYPE = 3;
    private static final int IS_BUSINESS = 4;
    private static final int NAME = 5;
    private static final int STREET = 6;
    private static final int POSTAL_CODE = 7;
    private static final int CITY = 8;
    private static final int EMAIL = 9;
    private static final int SOURCE = 10;
    private static final int NORMALIZED_NUMBER = 11;
    private static final int HAS_IMAGE = 12;

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
        Place place = null;
        try {
            Uri uri = CachedPlacesProvider.getContactLookupUri(number);
            if (DEBUG) {
                Log.d(TAG, "lookupCachedPlaceFromNumber: number=" + number
                        + ", uri=" + uri);
            }
            Cursor cursor = context.getContentResolver().query(
                    uri, _PROJECTION, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    place = new Place();
                    place.setLatitude(cursor.getDouble(LATITUDE));
                    place.setLongitude(cursor.getDouble(LONGITUDE));
                    place.setPhoneNumber(cursor.getString(PHONE_NUMBER));
                    place.setPhoneType(cursor.getInt(PHONE_TYPE));
                    place.setBusiness(cursor.getInt(IS_BUSINESS) != 0);
                    place.setName(cursor.getString(NAME));
                    place.setStreet(cursor.getString(STREET));
                    place.setPostalCode(cursor.getString(POSTAL_CODE));
                    place.setCity(cursor.getString(CITY));
                    place.setEmail(cursor.getString(EMAIL));
                    place.setSource(cursor.getString(SOURCE));

                    String normalizedNumber = cursor.getString(NORMALIZED_NUMBER);
                    if (cursor.getInt(HAS_IMAGE) == 1) {
                        place.setImageUri(CachedPlacesProvider.getImageLookupUri(normalizedNumber));
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
            contentValues.put(Place.LATITUDE, place.getLatitude());
            contentValues.put(Place.LONGITUDE, place.getLongitude());
            contentValues.put(Place.PHONE_NUMBER, place.getPhoneNumber());
            contentValues.put(Place.PHONE_TYPE, place.getPhoneType());
            contentValues.put(Place.IS_BUSINESS, place.isBusiness() ? 1 : 0);
            contentValues.put(Place.NAME, place.getName());
            contentValues.put(Place.STREET, place.getStreet());
            contentValues.put(Place.POSTAL_CODE, place.getPostalCode());
            contentValues.put(Place.CITY, place.getCity());
            contentValues.put(Place.EMAIL, place.getEmail());
            contentValues.put(Place.SOURCE, place.getSource());

            context.getContentResolver().insert(CachedPlacesProvider.CONTACT_URI, contentValues);
        }
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
            outputStream.write(image);
            success = true;
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
