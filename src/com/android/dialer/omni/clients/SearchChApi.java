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

import java.net.URLEncoder;
import java.util.Arrays;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;

import com.android.dialer.omni.Place;
import com.android.dialer.omni.PlaceUtil;
import com.android.dialer.omni.IReverseLookupApi;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;


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
            if (htmlDocumentHandler.getName() != null) {
                place = new Place();
                place.setName(htmlDocumentHandler.getName());
                place.setPhoneNumber(normalizedNumber);
                place.setStreet(htmlDocumentHandler.getAddress());
                place.setPostalCode(htmlDocumentHandler.getPostalCode());
                String city = htmlDocumentHandler.getCity();
                if (htmlDocumentHandler.getRegion() != null) {
                    city += "/" + htmlDocumentHandler.getRegion();
                }
                place.setCity(city);
                int phoneType = htmlDocumentHandler.isBusiness()
                        ? Phone.TYPE_WORK
                        : Phone.TYPE_HOME;
                place.setPhoneType(phoneType);

            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to do reverse lookup", e);
        }

        return place;
    }

    public class HtmlDocumentHandler implements ContentHandler {
        private String mName;
        private String mAddress;
        private String mPostalCode;
        private String mCity;
        private String mRegion;
        private boolean mBusiness;
        private String mLastElement;
        private Attributes mLastElementAtts;

        public String getName() {
            return mName;
        }

        public String getAddress() { return mAddress; }

        public String getPostalCode() { return mPostalCode; }

        public String getCity() { return mCity; }

        public String getRegion() { return mRegion; }

        public Boolean isBusiness() { return mBusiness; }

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes atts) throws SAXException {
            mLastElement = localName;
            mLastElementAtts = atts;
        }

        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            if (mName == null
                    && length > 0
                    && elementIs("a")
                    && attributeContainsValue("class", "fn")) {
                mName = getValue(ch, start, length);
                mBusiness = attributeContainsValue("class", "org");
            } else if (mAddress == null
                    && length > 0
                    && elementIs("span")
                    && attributeContainsValue("class", "street-address")) {
                mAddress = getValue(ch, start, length);
            } else if (mPostalCode == null
                    && length > 0
                    && elementIs("span")
                    && attributeContainsValue("class", "postal-code")) {
                mPostalCode = getValue(ch, start, length);
            } else if (mCity == null
                    && length > 0
                    && elementIs("span")
                    && attributeContainsValue("class", "locality")) {
                mCity = getValue(ch, start, length);
            } else if (mRegion == null
                    && length > 0
                    && elementIs("span")
                    && attributeContainsValue("class", "region")) {
                mRegion = getValue(ch, start, length);
            }
        }

        private boolean elementIs(String element) {
            return element.equalsIgnoreCase(mLastElement);
        }

        private boolean attributeContainsValue(String attribute, String value) {
            return mLastElementAtts.getValue(attribute) != null
                    && Arrays.asList(
                            mLastElementAtts.getValue(attribute).toLowerCase()
                                    .split(" ")).contains(value);
        }

        private String getValue(char[] ch, int start, int length) {
            String value = new String(ch, start, length);
            value = value.trim();
            if (value.isEmpty()) {
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
