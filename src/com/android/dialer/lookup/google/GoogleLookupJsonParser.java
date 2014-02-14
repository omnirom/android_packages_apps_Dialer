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

import com.android.dialer.lookup.ReverseLookup.ContactBuilder;
import com.android.incallui.service.PhoneNumberServiceImpl;

import com.google.common.collect.ImmutableMap;

import android.text.TextUtils;
import android.util.Log;

import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.DisplayNameSources;

import java.util.ArrayList;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* The JSON schema (not yet complete)
{
    "type": "object",
    "$schema": "http://json-schema.org/draft-03/schema",
    "id": "http://jsonschema.net",
    "required": true,
    "properties": {
        "items": {
            "type": "array",
            "id": "http://jsonschema.net/items",
            "required": false,
            "items": {
                "type": "object",
                "id": "http://jsonschema.net/items/0",
                "required": false,
                "properties": {
                    "addresses": {
                        "type": "array",
                        "id": "http://jsonschema.net/items/0/addresses",
                        "required": false,
                        "items": {
                            "type": "object",
                            "id": "http://jsonschema.net/items/0/addresses/0",
                            "required": false,
                            "properties": {
                                "value": {
                                    "type": "string",
                                    "id": "http://jsonschema.net/items/0/addresses/0/value",
                                    "required": false
                                }
                            }
                        }
                    },
                    "metadata": {
                        "type": "object",
                        "id": "http://jsonschema.net/items/0/metadata",
                        "required": false,
                        "properties": {
                            "objectType": {
                                "type": "string",
                                "id": "http://jsonschema.net/items/0/metadata/objectType",
                                "required": false
                            },
                            "plusPageType": {
                                "type": "string",
                                "id": "http://jsonschema.net/items/0/metadata/plusPageType",
                                "required": false
                            }
                        }
                    },
                    "names": {
                        "type": "array",
                        "id": "http://jsonschema.net/items/0/names",
                        "required": false,
                        "items": {
                            "type": "object",
                            "id": "http://jsonschema.net/items/0/names/0",
                            "required": false,
                            "properties": {
                                "displayName": {
                                    "type": "string",
                                    "id": "http://jsonschema.net/items/0/names/0/displayName",
                                    "required": false
                                },
                                "familyName": {
                                    "type": "string",
                                    "id": "http://jsonschema.net/items/0/names/0/familyName",
                                    "required": false
                                },
                                "givenName": {
                                    "type": "string",
                                    "id": "http://jsonschema.net/items/0/names/0/givenName",
                                    "required": false
                                },
                                "metadata": {
                                    "type": "object",
                                    "id": "http://jsonschema.net/items/0/names/0/metadata",
                                    "required": false,
                                    "properties": {
                                        "container": {
                                            "type": "string",
                                            "id": "http://jsonschema.net/items/0/names/0/metadata/container",
                                            "required": false
                                        }
                                    }
                                }
                            }
                        }
                    },
                    "phoneNumbers": {
                        "type": "array",
                        "id": "http://jsonschema.net/items/0/phoneNumbers",
                        "required": false,
                        "items": {
                            "type": "object",
                            "id": "http://jsonschema.net/items/0/phoneNumbers/0",
                            "required": false,
                            "properties": {
                                "formattedType": {
                                    "type": "string",
                                    "id": "http://jsonschema.net/items/0/phoneNumbers/0/formattedType",
                                    "required": false
                                },
                                "type": {
                                    "type": "string",
                                    "id": "http://jsonschema.net/items/0/phoneNumbers/0/type",
                                    "required": false
                                },
                                "value": {
                                    "type": "string",
                                    "id": "http://jsonschema.net/items/0/phoneNumbers/0/value",
                                    "required": false
                                }
                            }
                        }
                    },
                    "urls": {
                        "type": "array",
                        "id": "http://jsonschema.net/items/0/urls",
                        "required": false,
                        "items": {
                            "type": "object",
                            "id": "http://jsonschema.net/items/0/urls/0",
                            "required": false,
                            "properties": {
                                "value": {
                                    "type": "string",
                                    "id": "http://jsonschema.net/items/0/urls/0/value",
                                    "required": false
                                }
                            }
                        }
                    }
                }
            }
        },
        "kind": {
            "type": "string",
            "id": "http://jsonschema.net/kind",
            "required": false
        }
    }
}
 */

public final class GoogleLookupJsonParser {
    private static final String TAG =
            GoogleLookupJsonParser.class.getSimpleName();

    private static final boolean DEBUG = false;

    private GoogleLookupJsonParser() {
    }

    /** Map address fields to the respective fields in StructuredPostal */
    private static final Map<String, Integer> ADDRESS_TYPE_MAP =
            new ImmutableMap.Builder<String, Integer>()
            .put("home", StructuredPostal.TYPE_HOME)
            .put("work", StructuredPostal.TYPE_WORK)
            .put("other", StructuredPostal.TYPE_OTHER)
            .build();

    /** Unused */
    private static final Map<String, Integer> PHONE_TYPE_MAP =
            new ImmutableMap.Builder<String, Integer>()
            .put("home", Phone.TYPE_HOME)
            .put("work", Phone.TYPE_WORK)
            .put("mobile", Phone.TYPE_MOBILE)
            .put("homeFax", Phone.TYPE_FAX_HOME)
            .put("workFax", Phone.TYPE_FAX_WORK)
            .put("otherFax", Phone.TYPE_OTHER_FAX)
            .put("pager", Phone.TYPE_PAGER)
            .put("workMobile", Phone.TYPE_WORK_MOBILE)
            .put("workPager", Phone.TYPE_WORK_PAGER)
            .put("main", Phone.TYPE_MAIN)
            .put("googleVoice", Phone.TYPE_CUSTOM)
            .put("other", Phone.TYPE_OTHER)
            .build();

    /**
     * Parse JSON data from phone number lookup.
     *
     * @param json The JSON data
     * @param normalizedNumber The normalized phone number
     * @param formattedNumber The formatted phone number
     * @param photoUrl The URL to the image
     * @return The phone number info object
     */
    public static PhoneNumberServiceImpl.PhoneNumberInfoImpl
            parsePeopleJson(String json, String normalizedNumber,
            String formattedNumber, String photoUrl) {
        try {
            JSONObject peopleList = new JSONObject(json);

            String kind = peopleList.getString("kind");
            if (!"plus#peopleList".equals(kind)) {
                if (DEBUG) Log.e(TAG, "The value of 'kind' is not recognized: " + kind
                        + "JSON: " + peopleList);
                return null;
            }

            JSONObject item = getArrayItem(peopleList, "items");
            if (item != null) {
                return parseContactJson(item, normalizedNumber,
                        formattedNumber, photoUrl);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse JSON: " + json, e);
        }
        return null;
    }

    /**
     * Parse item object from the number lookup JSON data.
     *
     * @param item The JSON data representing the item
     * @param normalizedNumber The normalized phone number
     * @param formattedNumber The formatted phone number
     * @param photoUrl The URL to the image
     * @return The phone number info object
     */
    private static PhoneNumberServiceImpl.PhoneNumberInfoImpl
            parseContactJson(JSONObject item, String normalizedNumber,
            String formattedNumber, String photoUrl) throws JSONException {
        // The metadata object contains information on what
        JSONObject metadata = item.optJSONObject("metadata");

        // Whether the item represents a person
        boolean personItem = true;

        // TODO: Unknown
        String[] attributions = null;
        if (metadata != null) {
            personItem = isPersonItem(metadata);
            attributions = getArrayOfStrings(metadata, "attributions");
        }

        Integer displayNameSource = personItem
                ? DisplayNameSources.STRUCTURED_NAME
                : DisplayNameSources.ORGANIZATION;
        String number = formattedNumber != null
                ? formattedNumber : normalizedNumber;
        int type = personItem ? Phone.TYPE_MOBILE : Phone.TYPE_MAIN;

        ContactBuilder builder = new ContactBuilder(
                normalizedNumber, formattedNumber);

        // List of names of the person or place
        JSONObject names = getArrayItem(item, "names");

        if (names != null) {
            ContactBuilder.Name n = getNameObject(names);
            builder.setName(n);
        }

        // Find 'phoneNumbers' object matching the number if it exists
        JSONObject phoneObject = findPhoneObject(item, normalizedNumber);

        String label = null;
        if (phoneObject != null) {
            number = phoneObject.getString("value");

            String typeStr = phoneObject.getString("type");
            String formattedType =
                    phoneObject.optString("formattedType", null);
            Integer typeInt = PHONE_TYPE_MAP.get(type);

            type = typeInt;

            if (typeInt != null && typeInt != Phone.TYPE_CUSTOM) {
                label = null;
            } else {
                label = formattedType;
            }
        }

        ContactBuilder.PhoneNumber pn = new ContactBuilder.PhoneNumber();
        pn.number = number;
        pn.type = type;
        pn.label = label;
        builder.addPhoneNumber(pn);

        String photoUri = null;
        if (attributions == null) {
            if (!personItem) {
                ContactBuilder.Address a = getAddressObject(item);
                builder.addAddress(a);

                ContactBuilder.WebsiteUrl[] websites = getWebsiteObjects(item);
                for (int i = 0; i < websites.length; i++) {
                    builder.addWebsite(websites[i]);
                }
            }

            photoUri = getFirstImageUrl(item, photoUrl);
        }

        if (!personItem && photoUri == null) {
            photoUri = ContactBuilder.PHOTO_URI_BUSINESS;
        }

        builder.setDisplayNameSource(displayNameSource);
        builder.setPhotoUri(photoUri);
        builder.setIsBusiness(!personItem);

        return builder.build();
    }

    /**
     * Check if item represents a person.
     *
     * @param metadata The JSON 'metadata' object
     * @return Whether the item represents a person
     */
    private static boolean isPersonItem(JSONObject metadata)
            throws JSONException {
        String type = metadata.optString("objectType", null);
        return type == null || !type.equals("page");
    }

    /**
     * Get names from the 'names' JSON object of a lookup item.
     *
     * @param names The JSON object containing the names
     * @return Name object containing the names
     */
    private static ContactBuilder.Name getNameObject(JSONObject names) {
        ContactBuilder.Name n = new ContactBuilder.Name();
        n.displayName = names.optString("displayName", null);
        n.givenName = names.optString("givenName", null);
        n.familyName = names.optString("familyName", null);
        n.prefix = names.optString("honorificPrefix", null);
        n.middleName = names.optString("middleName", null);
        n.suffix = names.optString("honorificSuffix", null);
        n.phoneticGivenName = names.optString("phoneticGivenName", null);
        n.phoneticFamilyName = names.optString("phoneticFamilyName", null);
        return n;
    }

    /**
     * Get addresses from the JSON object of a lookup item.
     *
     * @param item The JSON object representing a lookup item
     * @return Address object containing the addresses
     */
    public static ContactBuilder.Address getAddressObject(
            JSONObject item) throws JSONException {
        // Get address from JSON
        JSONObject addresses = getArrayItem(item, "addresses");
        if (addresses == null) {
            return null;
        }

        String address = addresses.getString("value");

        String type = addresses.optString("type", null);
        String formattedType = addresses.optString("formattedType", null);

        ContactBuilder.Address a = new ContactBuilder.Address();
        a.formattedAddress = address;

        if (type != null) {
            Integer typeInt = ADDRESS_TYPE_MAP.get(type);
            a.type = typeInt;

            if (typeInt != null && typeInt != StructuredPostal.TYPE_CUSTOM) {
                a.label = null;
            } else {
                a.label = formattedType;
            }
        }

        return a;
    }

    /**
     * Get website URLs from the JSON object of a lookup item.
     *
     * @param item The JSON object representing a lookup item
     * @return WebsiteUrl objects containing the URLs
     */
    private static ContactBuilder.WebsiteUrl[] getWebsiteObjects(
            JSONObject item) throws JSONException {
        // Get URLs from JSON
        JSONArray urls = item.optJSONArray("urls");

        if (urls == null) {
            return null;
        }

        ArrayList<ContactBuilder.WebsiteUrl> websites =
                new ArrayList<ContactBuilder.WebsiteUrl>();

        for (int i = 0; i < urls.length(); i++) {
            ContactBuilder.WebsiteUrl w = new ContactBuilder.WebsiteUrl();
            w.url = urls.getJSONObject(i).getString("value");
            websites.add(w);
        }

        if (websites.size() > 0) {
            return websites.toArray(
                    new ContactBuilder.WebsiteUrl[websites.size()]);
        } else {
            return null;
        }
    }

    /**
     * Find JSON object containing the phone number.
     *
     * @param json The JSON object to find the phone number in
     * @param normalizedNumber The phone number to find
     * @return The JSON object containing the phone number
     */
    public static JSONObject findPhoneObject(JSONObject json,
            String normalizedNumber) throws JSONException {
        JSONArray phoneNumbers = json.optJSONArray("phoneNumbers");

        if (phoneNumbers != null) {
            for (int i = 0; i < phoneNumbers.length(); i++) {
                JSONObject phoneNumber = phoneNumbers.getJSONObject(i);

                if (phoneNumber != null && normalizedNumber.equals(
                        phoneNumber.optString("canonicalizedForm", null))) {
                    return phoneNumber;
                }
            }

            return null;
        }

        return null;
    }

    /**
     * Get the URL of the first photo that is not the photoUrl parameter.
     *
     * @param json The JSON object representing the contact
     * @param photoUrl The photo's URL
     * @return The URL of the first photo that is not photoUrl
     */
    private static String getFirstImageUrl(JSONObject json, String photoUrl)
            throws JSONException {
        JSONArray images = json.optJSONArray("images");

        if (images != null) {
            String ret = null;

            for (int i = 0; i < images.length(); i++) {
                JSONObject image = images.getJSONObject(i);
                if (image != null) {
                    JSONObject metadata = image.optJSONObject("metadata");
                    if (metadata == null
                            || !"contact".equals(metadata.optString("container"))) {
                        String url = image.optString("url", null);
                        if (!TextUtils.isEmpty(url)) {
                            if (!url.startsWith(photoUrl)) {
                                return url;
                            }

                            ret = url;
                        }
                    }
                }
            }

            return ret;
        }

        return null;
    }

    /**
     * Find JSON array from a JSON object and get first element.
     *
     * @param json The JSON object containing the JSON array
     * @param name The name of the JSON array
     * @return The first element of the JSON array
     */
    public static JSONObject getArrayItem(JSONObject json, String name)
            throws JSONException {
        JSONArray array = json.optJSONArray(name);

        if (array == null || array.length() == 0) {
            return null;
        }

        return array.getJSONObject(0);
    }

    /**
     * Find a JSON array and return its strings as a string array.
     *
     * @param json The JSON object containing the JSON array
     * @param name The name of the JSON array
     * @return The string array containing the strings
     */
    public static String[] getArrayOfStrings(JSONObject json, String name)
            throws JSONException {
        JSONArray array = json.optJSONArray(name);

        String[] ret;

        if (array == null || array.length() == 0) {
            ret = null;
        } else {
            ret = new String[array.length()];
            for (int i = 0; i < array.length(); i++) {
                ret[i] = array.getString(i);
            }
        }

        return ret;
    }
}
