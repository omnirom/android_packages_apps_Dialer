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

package com.android.dialer.service;

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.database.DialerDatabaseHelper.CachedContactsColumns;
import com.android.dialer.database.DialerDatabaseHelper.CachedNumberQuery;
import com.android.dialer.database.DialerDatabaseHelper.SmartDialDbColumns;
import com.android.dialer.lookup.LookupSettings;
import com.android.dialer.lookup.PhoneNumberCacheContract;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

import java.io.IOException;
import java.io.OutputStream;

import libcore.io.IoUtils;

public class CachedNumberLookupServiceImpl implements CachedNumberLookupService {
    public static final int SOURCE_DIRECTORY = 1;
    public static final int SOURCE_EXTENDED = 2;
    public static final int SOURCE_BUSINESS = 3;
    public static final int SOURCE_PERSON = 4;

    public static class CachedContactInfoImpl implements CachedContactInfo {
        private final ContactInfo mInfo;

        public String lookupKey;

        public long sourceId;
        public String sourceName;
        public int sourceType;

        public CachedContactInfoImpl(ContactInfo info) {
            if (info == null) {
                info = ContactInfo.EMPTY;
            }
            mInfo = info;
        }

        @Override
        public ContactInfo getContactInfo() {
            return mInfo;
        }

        @Override
        public void setDirectorySource(String name, long directoryId) {
            setSource(SOURCE_DIRECTORY, name, directoryId);
        }

        @Override
        public void setExtendedSource(String name, long directoryId) {
            setSource(SOURCE_EXTENDED, name, directoryId);
        }

        @Override
        public void setLookupKey(String key) {
            lookupKey = key;
        }

        protected void setSource(int type, String name, long id) {
            sourceType = type;
            sourceName = name;
            sourceId = id;
            mInfo.sourceType = type;
        }

        public static boolean isBusiness(int type) {
            return type == SOURCE_BUSINESS || type == SOURCE_EXTENDED;
        }

        public static boolean isLookupSource(int type) {
            return type == SOURCE_BUSINESS || type == SOURCE_PERSON;
        }

        public int getSourceType() {
            return sourceType;
        }

        public void setLookupSource(boolean isBusiness) {
            int type;
            if (isBusiness) {
                type = SOURCE_BUSINESS;
            } else {
                type = SOURCE_PERSON;
            }
            setSource(type, "Caller ID", 0x7fffffff);
        }
    }

    @Override
    public CachedContactInfoImpl buildCachedContactInfo(ContactInfo info) {
        return new CachedContactInfoImpl(info);
    }

    /**
     * Perform a lookup using the cached number lookup service to return contact
     * information stored in the cache that corresponds to the given number.
     *
     * @param context Valid context
     * @param number Phone number to lookup the cache for
     * @return A {@link CachedContactInfo} containing the contact information
     * if the phone number is found in the cache, {@link ContactInfo#EMPTY} if
     * the phone number was not found in the cache, and null if there was an
     * error when querying the cache.
     */
    @Override
    public CachedContactInfoImpl lookupCachedContactFromNumber(
            Context context, String number) {
        Cursor cursor = context.getContentResolver().query(
                PhoneNumberCacheContract.getContactLookupUri(number),
                CachedNumberQuery.PROJECTION, null, null, null);

        if (cursor == null) {
            return null;
        }

        try {
            if (!cursor.moveToFirst()) {
                return buildCachedContactInfo(ContactInfo.EMPTY);
            }

            int sourceType = cursor.getInt(CachedNumberQuery.CACHE_SOURCE_TYPE);

            // If reverse lookup is disabled, remove the cache entries
            if (CachedContactInfoImpl.isLookupSource(sourceType)
                    && !LookupSettings.isReverseLookupEnabled(context)) {
                purgePeopleApiCacheEntries(context);
                return buildCachedContactInfo(ContactInfo.EMPTY);
            }

            // Build ContactInfo with cached information
            ContactInfo info = new ContactInfo();

            info.lookupUri = getContactUri(cursor);
            info.name = cursor.getString(CachedNumberQuery.CACHE_DISPLAY_NAME);
            info.type = cursor.getInt(CachedNumberQuery.CACHE_PHONE_TYPE);
            info.label = cursor.getString(CachedNumberQuery.CACHE_PHONE_LABEL);

            if (info.type == 0 && info.label == null) {
                info.label = ContactInfo.GEOCODE_AS_LABEL;
            }

            info.number = cursor.getString(CachedNumberQuery.CACHE_NUMBER);
            info.normalizedNumber = number;
            info.formattedNumber = null;

            info.photoId = 0;
            info.photoUri = getPhotoUri(cursor, number);

            CachedContactInfoImpl cachedContactInfo =
                    buildCachedContactInfo(info);

            cachedContactInfo.setSource(sourceType,
                    cursor.getString(CachedNumberQuery.CACHE_SOURCE_NAME),
                    cursor.getLong(CachedNumberQuery.CACHE_SOURCE_ID));

            return cachedContactInfo;
        } finally {
            cursor.close();
        }
    }

    @Override
    public void addContact(Context context,
            CachedContactInfo info) {
        if (info instanceof CachedContactInfoImpl) {
            CachedContactInfoImpl cachedInfo = (CachedContactInfoImpl) info;

            Uri uri = PhoneNumberCacheContract.CONTACT_URI;
            ContentValues contentValues = new ContentValues();
            ContactInfo contactInfo = cachedInfo.getContactInfo();

            if (contactInfo != null && contactInfo != ContactInfo.EMPTY) {
                String number;
                if (contactInfo.number != null) {
                    number = contactInfo.number;
                } else {
                    number = contactInfo.normalizedNumber;
                }

                if (!TextUtils.isEmpty(number)) {
                    contentValues.put(CachedContactsColumns.NUMBER, number);
                    contentValues.put(CachedContactsColumns.PHONE_TYPE,
                            contactInfo.type);
                    contentValues.put(CachedContactsColumns.PHONE_LABEL,
                            contactInfo.label);
                    contentValues.put(SmartDialDbColumns.DISPLAY_NAME_PRIMARY,
                            contactInfo.name);
                    String photoUri;
                    if (contactInfo.photoUri != null) {
                        photoUri = contactInfo.photoUri.toString();
                    } else {
                        photoUri = null;
                    }
                    contentValues.put(CachedContactsColumns.PHOTO_URI, photoUri);
                    contentValues.put(CachedContactsColumns.SOURCE_NAME,
                            cachedInfo.sourceName);
                    contentValues.put(CachedContactsColumns.SOURCE_TYPE,
                            cachedInfo.sourceType);
                    contentValues.put(CachedContactsColumns.SOURCE_ID,
                            cachedInfo.sourceId);
                    contentValues.put(CachedContactsColumns.LOOKUP_KEY,
                            cachedInfo.lookupKey);
                    context.getContentResolver().insert(uri, contentValues);
                }
            }
        }
    }

    @Override
    public boolean isCacheUri(String uri) {
        return uri.startsWith(
                PhoneNumberCacheContract.AUTHORITY_URI.toString());
    }

    @Override
    public boolean addPhoto(Context context, String number, byte[] photo) {
        Uri uri = PhoneNumberCacheContract.getPhotoLookupUri(number);
        OutputStream out = null;
        try {
            out = context.getContentResolver().openOutputStream(uri);
            out.write(photo);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            IoUtils.closeQuietly(out);
        }
    }

    @Override
    public void clearAllCacheEntries(Context context) {
        DialerDatabaseHelper.getInstance(context).purgeAll();
    }

    public static void purgePeopleApiCacheEntries(Context context) {
        DialerDatabaseHelper helper =
                DialerDatabaseHelper.getInstance(context);

        helper.purgeSource(SOURCE_BUSINESS);
        helper.purgeSource(SOURCE_PERSON);
    }

    private Uri getContactUri(Cursor cursor) {
        int sourceType = cursor.getInt(CachedNumberQuery.CACHE_SOURCE_TYPE);
        String sourceId = cursor.getString(CachedNumberQuery.CACHE_SOURCE_ID);
        String sourceName = cursor.getString(CachedNumberQuery.CACHE_SOURCE_NAME);
        String lookupKey = cursor.getString(CachedNumberQuery.CACHE_LOOKUP_KEY);

        if (!TextUtils.isEmpty(lookupKey) && !TextUtils.isEmpty(sourceId)) {
            if (sourceType == SOURCE_DIRECTORY) {
                return ContactsContract.Contacts.getLookupUri(0, lookupKey)
                        .buildUpon().appendQueryParameter("directory", sourceId)
                        .build();
            } else if (sourceType == SOURCE_EXTENDED
                    || sourceType == SOURCE_BUSINESS
                    || sourceType == SOURCE_PERSON) {
                Uri.Builder encodedFragment =
                        ContactsContract.Contacts.CONTENT_LOOKUP_URI
                        .buildUpon().appendPath("encoded")
                        .encodedFragment(lookupKey);

                if (!TextUtils.isEmpty(sourceName)) {
                    encodedFragment.appendQueryParameter(
                            "displayName", sourceName);
                }

                return encodedFragment.appendQueryParameter(
                        "directory", sourceId).build();
            }
        }

        return null;
    }

    /**
     * Return photo or thumbnail URI if either is cached. Otherwise, return
     * the default parameter (uri).
     *
     * @param cursor Cursor for database
     * @param uri Default URI if there is no cached URI
     * @return The photo or thumbnail URI
     */
    private Uri getPhotoUri(Cursor cursor, String number) {
        int hasPhoto = cursor.getInt(CachedNumberQuery.CACHE_HAS_PHOTO);
        int hasThumbnail = cursor.getInt(CachedNumberQuery.CACHE_HAS_THUMBNAIL);

        if (hasPhoto != 0) {
            return PhoneNumberCacheContract.getPhotoLookupUri(number);
        } else if (hasThumbnail != 0) {
            return PhoneNumberCacheContract.getThumbnailLookupUri(number);
        }

        String photoUri = cursor.getString(CachedNumberQuery.CACHE_PHOTO_URI);
        if (photoUri != null) {
            return Uri.parse(photoUri);
        } else {
            return null;
        }
    }
}
