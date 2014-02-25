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

import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;
import android.util.Log;

import com.android.dialer.omni.IReverseLookupApi;
import com.android.dialer.omni.Place;
import com.android.dialer.omni.PlaceUtil;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.net.URLEncoder;
import java.util.Arrays;


public class SearchChApi implements IReverseLookupApi {

    private final static String TAG = SearchChApi.class.getSimpleName();
    private final static String QUERY_URL = "http://tel.search.ch/?tel=";
    // private final static String XPATH_NAME = ".//*[@id='telresultslist']/tbody/tr[1]/td/div[2]/table/tbody/tr/td[2]/div/table/tbody/tr[1]/td[1]/h5/a";

    @Override
    public String getApiProviderName() {
        return "search.ch";
    }

    @Override
    public Place getNamedPlaceByNumber(PhoneNumber phoneNumber) {
        String normalizedNumber = PhoneNumberUtil.getInstance().format(phoneNumber,
                PhoneNumberFormat.E164);
        String encodedNumber = URLEncoder.encode(normalizedNumber);
        HtmlDocumentHandler htmlDocumentHandler = new HtmlDocumentHandler();
        Place place = null;

        try {
            PlaceUtil.getRequest(QUERY_URL + encodedNumber, htmlDocumentHandler);
            if (!TextUtils.isEmpty(htmlDocumentHandler.name)) {
                place = new Place();
                place.name = htmlDocumentHandler.name;
                place.phoneNumber = normalizedNumber;
                place.street = htmlDocumentHandler.address;
                place.postalCode = htmlDocumentHandler.postalCode;
                String city = htmlDocumentHandler.city;
                if (!TextUtils.isEmpty(htmlDocumentHandler.region)) {
                    city += "/" + htmlDocumentHandler.region;
                }
                place.city = city;
                place.phoneType = htmlDocumentHandler.isBusiness
                        ? Phone.TYPE_WORK
                        : Phone.TYPE_HOME;

            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to do reverse lookup", e);
        }

        return place;
    }

    private class HtmlDocumentHandler implements ContentHandler {
        private String mLastElement;
        private Attributes mLastElementAttributes;

        public String name;
        public String address;
        public String postalCode;
        public String city;
        public String region;
        public boolean isBusiness;

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) throws SAXException {
            mLastElement = localName;
            mLastElementAttributes = attributes;
        }

        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            if (length > 0) {
                if (name == null
                        && elementIs("a")
                        && attributeContainsValue("class", "fn")) {
                    name = getValue(ch, start, length);
                    isBusiness = attributeContainsValue("class", "org");
                } else if (address == null
                        && elementIs("span")
                        && attributeContainsValue("class", "street-address")) {
                    address = getValue(ch, start, length);
                } else if (postalCode == null
                        && elementIs("span")
                        && attributeContainsValue("class", "postal-code")) {
                    postalCode = getValue(ch, start, length);
                } else if (city == null
                        && elementIs("span")
                        && attributeContainsValue("class", "locality")) {
                    city = getValue(ch, start, length);
                } else if (region == null
                        && elementIs("span")
                        && attributeContainsValue("class", "region")) {
                    region = getValue(ch, start, length);
                }
            }
        }

        private boolean elementIs(String element) {
            return element.equalsIgnoreCase(mLastElement);
        }

        private boolean attributeContainsValue(String attribute, String value) {
            return mLastElementAttributes.getValue(attribute) != null
                    && Arrays.asList(
                            mLastElementAttributes.getValue(attribute).toLowerCase()
                                    .split(" ")).contains(value);
        }

        private String getValue(char[] ch, int start, int length) {
            String value = new String(ch, start, length);
            value = value.trim();
            if (TextUtils.isEmpty(value)) {
                return null;
            }
            return value;
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
