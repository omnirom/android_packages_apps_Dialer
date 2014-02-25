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

package com.android.dialer.omni.clients;

import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;
import android.util.Log;

import com.android.dialer.omni.IReverseLookupApi;
import com.android.dialer.omni.Place;
import com.android.dialer.omni.PlaceUtil;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.net.URLEncoder;
import java.util.Arrays;

public class SearchDeApi implements IReverseLookupApi {

    private final static String TAG = SearchChApi.class.getSimpleName();
    private final static String QUERY_URL = "http://www.dasoertliche.de/Controller?form_name=search_inv&ph=";

    // testNumber = "+4988612562000";

    @Override
    public String getApiProviderName() {
        return "DasÖrtliche";
    }

    @Override
    public Place getNamedPlaceByNumber(PhoneNumber phoneNumber) {
        String normalizedNumber = PhoneNumberUtil.getInstance().format(phoneNumber,
                PhoneNumberUtil.PhoneNumberFormat.E164);
        String encodedNumber = URLEncoder.encode(normalizedNumber);
        HtmlDocumentHandler htmlDocumentHandler = new HtmlDocumentHandler();
        Place place = null;

        try {
            PlaceUtil.getRequest(QUERY_URL + encodedNumber, htmlDocumentHandler);
            if (!TextUtils.isEmpty(htmlDocumentHandler.name)) {
                place = new Place();
                place.name = htmlDocumentHandler.name;
                place.phoneNumber = normalizedNumber;
                place.phoneType = Phone.TYPE_WORK;
                place.street = htmlDocumentHandler.street;
                place.postalCode = htmlDocumentHandler.postalCode;
                place.city = htmlDocumentHandler.city;
                place.email = htmlDocumentHandler.email;
                place.website = htmlDocumentHandler.website;
                place.imageUri = htmlDocumentHandler.imageUri;
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to do reverse lookup", e);
        }

        return place;
    }

    private class HtmlDocumentHandler implements ContentHandler {
        private String mCurrentElement;
        private String mPreviousElement;
        private Attributes mCurrentElementAttributes;
        private Attributes mPreviousElementAttributes;
        private int mCounter;

        public String name;
        public String street;
        public String postalCode;
        public String city;
        public String email;
        public String website;
        public Uri imageUri;

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) throws SAXException {
            mPreviousElement = mCurrentElement;
            mPreviousElementAttributes = mCurrentElementAttributes;
            mCurrentElement = localName;
            mCurrentElementAttributes = attributes;
        }

        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            if (length > 0) {
                if (elementIs("div")
                        && attributeContainsValue("class", "counter")) {
                    String value = getValue(ch, start, length);
                    try {
                        mCounter = Integer.parseInt(value);
                    } catch (NumberFormatException ignoreException) {
                    }
                } else if (mCounter == 1) {
                    if (name == null
                            && elementIs("span")
                            && previousElementIs("a")
                            && previousAttributeContainsValue("class", "iname")) {
                        name = getValue(ch, start, length);
                    } else if (street == null
                            && postalCode == null
                            && elementIs("div")
                            && attributeContainsValue("class", "strasse")) {
                        String[] values = getValue(ch, start, length).split(",");
                        street = values[0].replaceAll("&nbsp;", " ").trim();
                        postalCode = values[1].replaceAll("&nbsp;", " ").trim();
                    } else if (city == null
                            && previousElementIs("div")
                            && previousAttributeContainsValue("class", "strasse")
                            && elementIs("span")) {
                        city = getValue(ch, start, length);
                    } else if (website == null
                            && previousElementIs("a")
                            && previousAttributeContainsValue("class", "toplink")
                            && elementIs("span")) {
                        website = getValue(ch, start, length);
                    } else if (email == null
                            && elementIs("a")
                            && attributeContainsValue("class", "topmail")) {
                        email = getValue(ch, start, length);
                    } else if (imageUri == null
                            && elementIs("img")
                            && previousAttributeContainsValue("class", "t_logo")) {
                        String url = mCurrentElementAttributes.getValue("src");
                        if (!TextUtils.isEmpty(url)) {
                            imageUri = Uri.parse(url);
                        }
                    }
                }
            }
        }

        private boolean elementIs(String element) {
            return element.equalsIgnoreCase(mCurrentElement);
        }

        private boolean previousElementIs(String element) {
            return element.equalsIgnoreCase(mPreviousElement);
        }

        private boolean attributeContainsValue(String attribute, String value) {
            return mCurrentElementAttributes.getValue(attribute) != null
                    && Arrays.asList(
                            mCurrentElementAttributes.getValue(attribute).toLowerCase()
                                    .split(" ")).contains(value);
        }

        private boolean previousAttributeContainsValue(String attribute, String value) {
            return mPreviousElementAttributes.getValue(attribute) != null
                    && Arrays.asList(
                    mPreviousElementAttributes.getValue(attribute).toLowerCase()
                                    .split(" ")).contains(value);
        }

        private String getValue(char[] ch, int start, int length) {
            String value = new String(ch, start, length);
            value = trimNonAlNum(value);
            if (TextUtils.isEmpty(value)) {
                return null;
            }
            return value;
        }

        private String trimNonAlNum(String value) {
            char[] chars = new char[value.length()];
            value.getChars(0, value.length(), chars, 0);
            int start = 0;
            int last = value.length() - 1;
            int end = last;
            while ((start <= end) && (chars[start] <= '0' || chars[start] >= 'z')) {
                start++;
            }
            while ((end >= start) && (chars[end] <= '0' || chars[end] >= 'z')) {
                end--;
            }
            if (start == 0 && end == last) {
                return value;
            }
            return new String(chars, start, end - start + 1);
        }

        @Override
        public void setDocumentLocator(Locator locator) {
        }

        @Override
        public void startDocument() throws SAXException {
        }

        @Override
        public void endDocument() throws SAXException {
        }

        @Override
        public void startPrefixMapping(String prefix, String uri)
                throws SAXException {
        }

        @Override
        public void endPrefixMapping(String prefix) throws SAXException {
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length)
                throws SAXException {
        }

        @Override
        public void processingInstruction(String target, String data)
                throws SAXException {
        }

        @Override
        public void skippedEntity(String name) throws SAXException {
        }
    }
}
