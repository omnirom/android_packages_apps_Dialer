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

import com.android.dialer.R;
import com.android.dialer.lookup.google.GoogleReverseLookup;
import com.android.dialer.lookup.opencnam.OpenCnamReverseLookup;
import com.android.dialer.lookup.whitepages.WhitePagesReverseLookup;
import com.android.dialer.lookup.yellowpages.YellowPagesReverseLookup;
import com.android.dialer.lookup.zabasearch.ZabaSearchReverseLookup;
import com.android.incallui.service.PhoneNumberServiceImpl.PhoneNumberInfoImpl;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Contacts;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class ReverseLookup {
    private static final String TAG = ReverseLookup.class.getSimpleName();

    private static ReverseLookup INSTANCE = null;

    public static ReverseLookup getInstance(Context context) {
        String provider = LookupSettings.getReverseLookupProvider(context);

        if (INSTANCE == null || !isInstance(provider)) {
            Log.d(TAG, "Chosen reverse lookup provider: " + provider);

            if (provider.equals(LookupSettings.RLP_GOOGLE)) {
                INSTANCE = new GoogleReverseLookup(context);
            } else if (provider.equals(LookupSettings.RLP_OPENCNAM)) {
                INSTANCE = new OpenCnamReverseLookup(context);
            } else if (provider.equals(LookupSettings.RLP_WHITEPAGES)
                    || provider.equals(LookupSettings.RLP_WHITEPAGES_CA)) {
                INSTANCE = new WhitePagesReverseLookup(context);
            } else if (provider.equals(LookupSettings.RLP_YELLOWPAGES)) {
                INSTANCE = new YellowPagesReverseLookup(context);
            } else if (provider.equals(LookupSettings.RLP_ZABASEARCH)) {
                INSTANCE = new ZabaSearchReverseLookup(context);
            }
        }

        return INSTANCE;
    }

    private static boolean isInstance(String provider) {
        if (provider.equals(LookupSettings.RLP_GOOGLE)
                && INSTANCE instanceof GoogleReverseLookup) {
            return true;
        } else if (provider.equals(LookupSettings.RLP_OPENCNAM)
                && INSTANCE instanceof OpenCnamReverseLookup) {
            return true;
        } else if ((provider.equals(LookupSettings.RLP_WHITEPAGES)
                || provider.equals(LookupSettings.RLP_WHITEPAGES_CA))
                && INSTANCE instanceof WhitePagesReverseLookup) {
            return true;
        } else if (provider.equals(LookupSettings.RLP_YELLOWPAGES)
                && INSTANCE instanceof YellowPagesReverseLookup) {
            return true;
        } else if (provider.equals(LookupSettings.RLP_ZABASEARCH)
                && INSTANCE instanceof ZabaSearchReverseLookup) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Lookup image
     *
     * @param context The application context
     * @param url The image URL
     * @param data Extra data (a authentication token, perhaps)
     */
    public abstract byte[] lookupImage(Context context, String url, Object data);

    /**
     * Perform phone number lookup.
     *
     * @param context The application context
     * @param normalizedNumber The normalized phone number
     * @param formattedNumber The formatted phone number
     * @param isIncoming Whether the call is incoming or outgoing
     * @return The phone number info object
     */
    public abstract Pair<PhoneNumberInfoImpl, Object> lookupNumber(
            Context context, String normalizedNumber, String formattedNumber,
            boolean isIncoming);

    public static class ContactBuilder {
        private static final String TAG =
                ContactBuilder.class.getSimpleName();

        private static final boolean DEBUG = false;

        /** Default photo for businesses if no other image is found */
        public static final String PHOTO_URI_BUSINESS =
                new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority("com.android.dialer")
                .appendPath(String.valueOf(
                        R.drawable.ic_places_picture_180_holo_light))
                .build()
                .toString();

        private ArrayList<Address> mAddresses = new ArrayList<Address>();
        private ArrayList<PhoneNumber> mPhoneNumbers
                = new ArrayList<PhoneNumber>();
        private ArrayList<WebsiteUrl> mWebsites
                = new ArrayList<WebsiteUrl>();

        private Name mName;

        private String mNormalizedNumber;
        private String mFormattedNumber;
        private int mDisplayNameSource;
        private String mPhotoUri;

        private boolean mIsBusiness;

        public ContactBuilder(String normalizedNumber, String formattedNumber) {
            mNormalizedNumber = normalizedNumber;
            mFormattedNumber = formattedNumber;
        }

        public void addAddress(Address address) {
            if (DEBUG) Log.d(TAG, "Adding address: " + address);
            if (address != null) {
                mAddresses.add(address);
            }
        }

        public Address[] getAddresses() {
            return mAddresses.toArray(new Address[mAddresses.size()]);
        }

        public void addPhoneNumber(PhoneNumber phoneNumber) {
            if (DEBUG) Log.d(TAG, "Adding phone number: " + phoneNumber);
            if (phoneNumber != null) {
                mPhoneNumbers.add(phoneNumber);
            }
        }

        public PhoneNumber[] getPhoneNumbers() {
            return mPhoneNumbers.toArray(
                    new PhoneNumber[mPhoneNumbers.size()]);
        }

        public void addWebsite(WebsiteUrl website) {
            if (DEBUG) Log.d(TAG, "Adding website: " + website);
            if (website != null) {
                mWebsites.add(website);
            }
        }

        public Website[] getWebsites() {
            return mWebsites.toArray(new Website[mWebsites.size()]);
        }

        public void setName(Name name) {
            if (DEBUG) Log.d(TAG, "Setting name: " + name);
            if (name != null) {
                mName = name;
            }
        }

        public Name getName() {
            return mName;
        }

        public void setDisplayNameSource(int source) {
            if (DEBUG) Log.d(TAG, "Setting display name source: " + source);
            mDisplayNameSource = source;
        }

        public int getDisplayNameSource() {
            return mDisplayNameSource;
        }

        public void setPhotoUri(String photoUri) {
            if (DEBUG) Log.d(TAG, "Setting photo URI: " + photoUri);
            mPhotoUri = photoUri;
        }

        public String getPhotoUri() {
            return mPhotoUri;
        }

        public void setIsBusiness(boolean isBusiness) {
            if (DEBUG) Log.d(TAG, "Setting isBusiness to: " + isBusiness);
            mIsBusiness = isBusiness;
        }

        public boolean isBusiness() {
            return mIsBusiness;
        }

        public PhoneNumberInfoImpl build() {
            if (mName == null) {
                throw new IllegalStateException("Name has not been set");
            }

            // Use the incoming call's phone number if no other phone number
            // is specified. The reverse lookup source could present the phone
            // number differently (eg. without the area code).
            if (mPhoneNumbers.size() == 0) {
                PhoneNumber pn = new PhoneNumber();
                // Use the formatted number where possible
                pn.number = mFormattedNumber != null
                        ? mFormattedNumber : mNormalizedNumber;
                pn.type = Phone.TYPE_MAIN;
                addPhoneNumber(pn);
            }

            try {
                JSONObject contact = new JSONObject();

                // Insert the name
                contact.put(StructuredName.CONTENT_ITEM_TYPE,
                        mName.getJsonObject());

                // Insert phone numbers
                JSONArray phoneNumbers = new JSONArray();
                for (int i = 0; i < mPhoneNumbers.size(); i++) {
                    phoneNumbers.put(mPhoneNumbers.get(i).getJsonObject());
                }
                contact.put(Phone.CONTENT_ITEM_TYPE, phoneNumbers);

                // Insert addresses if there are any
                if (mAddresses.size() > 0) {
                    JSONArray addresses = new JSONArray();
                    for (int i = 0; i < mAddresses.size(); i++) {
                        addresses.put(mAddresses.get(i).getJsonObject());
                    }
                    contact.put(StructuredPostal.CONTENT_ITEM_TYPE, addresses);
                }

                // Insert websites if there are any
                if (mWebsites.size() > 0) {
                    JSONArray websites = new JSONArray();
                    for (int i = 0; i < mWebsites.size(); i++) {
                        websites.put(mWebsites.get(i).getJsonObject());
                    }
                    contact.put(Website.CONTENT_ITEM_TYPE, websites);
                }

                return new PhoneNumberInfoImpl(
                        mName.displayName,
                        mNormalizedNumber,
                        mPhoneNumbers.get(0).number,
                        mPhoneNumbers.get(0).type,
                        mPhoneNumbers.get(0).label,
                        mPhotoUri,
                        new JSONObject()
                        .put(Contacts.DISPLAY_NAME, mName.displayName)
                        .put(Contacts.DISPLAY_NAME_SOURCE, mDisplayNameSource)
                        .putOpt(Contacts.PHOTO_URI, mPhotoUri)
                        .put(Contacts.CONTENT_ITEM_TYPE, contact)
                        .toString(),
                        mIsBusiness);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to build contact", e);
                return null;
            }
        }

        // android.provider.ContactsContract.CommonDataKinds.StructuredPostal
        public static class Address {
            public String formattedAddress;
            public int type;
            public String label;
            public String street;
            public String poBox;
            public String neighborhood;
            public String city;
            public String region;
            public String postCode;
            public String country;

            public JSONObject getJsonObject() throws JSONException {
                JSONObject json = new JSONObject();
                json.putOpt(StructuredPostal.FORMATTED_ADDRESS,
                        formattedAddress);
                json.put(StructuredPostal.TYPE, type);
                json.put(StructuredPostal.LABEL, label);
                json.put(StructuredPostal.STREET, street);
                json.put(StructuredPostal.POBOX, poBox);
                json.put(StructuredPostal.NEIGHBORHOOD, neighborhood);
                json.put(StructuredPostal.CITY, city);
                json.put(StructuredPostal.REGION, region);
                json.put(StructuredPostal.POSTCODE, postCode);
                json.put(StructuredPostal.COUNTRY, country);
                return json;
            }

            public String toString() {
                return "formattedAddress: " + formattedAddress + "; " +
                        "type: " + type + "; " +
                        "label: " + label + "; " +
                        "street: " + street + "; " +
                        "poBox: " + poBox + "; " +
                        "neighborhood: " + neighborhood + "; " +
                        "city: " + city + "; " +
                        "region: " + region + "; " +
                        "postCode: " + postCode + "; " +
                        "country: " + country;
            }
        }

        // android.provider.ContactsContract.CommonDataKinds.StructuredName
        public static class Name {
            public String displayName;
            public String givenName;
            public String familyName;
            public String prefix;
            public String middleName;
            public String suffix;
            public String phoneticGivenName;
            public String phoneticMiddleName;
            public String phoneticFamilyName;

            public JSONObject getJsonObject() throws JSONException {
                JSONObject json = new JSONObject();
                json.putOpt(StructuredName.DISPLAY_NAME, displayName);
                json.putOpt(StructuredName.GIVEN_NAME, givenName);
                json.putOpt(StructuredName.FAMILY_NAME, familyName);
                json.putOpt(StructuredName.PREFIX, prefix);
                json.putOpt(StructuredName.MIDDLE_NAME, middleName);
                json.putOpt(StructuredName.SUFFIX, suffix);
                json.putOpt(StructuredName.PHONETIC_GIVEN_NAME,
                        phoneticGivenName);
                json.putOpt(StructuredName.PHONETIC_MIDDLE_NAME,
                        phoneticMiddleName);
                json.putOpt(StructuredName.PHONETIC_FAMILY_NAME,
                        phoneticFamilyName);
                return json;
            }

            public String toString() {
                return "displayName: " + displayName + "; " +
                        "givenName: " + givenName + "; " +
                        "familyName: " + familyName + "; " +
                        "prefix: " + prefix + "; " +
                        "middleName: " + middleName + "; " +
                        "suffix: " + suffix + "; " +
                        "phoneticGivenName: " + phoneticGivenName + "; " +
                        "phoneticMiddleName: " + phoneticMiddleName + "; " +
                        "phoneticFamilyName: " + phoneticFamilyName;
            }
        }

        // android.provider.ContactsContract.CommonDataKinds.Phone
        public static class PhoneNumber {
            public String number;
            public int type;
            public String label;

            public JSONObject getJsonObject() throws JSONException {
                JSONObject json = new JSONObject();
                json.put(Phone.NUMBER, number);
                json.put(Phone.TYPE, type);
                json.putOpt(Phone.LABEL, label);
                return json;
            }

            public String toString() {
                return "number: " + number + "; " +
                        "type: " + type + "; " +
                        "label: " + label;
            }
        }

        // android.provider.ContactsContract.CommonDataKinds.Website
        public static class WebsiteUrl {
            public String url;
            public int type;
            public String label;

            public JSONObject getJsonObject() throws JSONException {
                JSONObject json = new JSONObject();
                json.put(Website.URL, url);
                json.put(Website.TYPE, type);
                json.putOpt(Website.LABEL, label);
                return json;
            }

            public String toString() {
                return "url: " + url + "; " +
                        "type: " + type + "; " +
                        "label: " + label;
            }
        }
    }
}
