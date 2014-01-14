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

import android.util.Log;

import com.android.dialer.omni.Place;
import com.android.dialer.omni.PlaceUtil;
import com.android.dialer.omni.IReverseLookupApi;


public class SearchChApi implements IReverseLookupApi {

    private final static String TAG = "SearchChApi";
    private final static String QUERY_URL = "http://tel.search.ch/?tel=";
    // private final static String XPATH_NAME = ".//*[@id='telresultslist']/tbody/tr[1]/td/div[2]/table/tbody/tr/td[2]/div/table/tbody/tr[1]/td[1]/h5/a";

    private static final int[] SUPPORTED_COUNTRIES = { 41 };

    @Override
    public String getApiProviderName() {
        return "search.ch";
    }

    @Override
    public int[] getSupportedCountryCodes() {
        return SUPPORTED_COUNTRIES;
    }

    @Override
    public Place getNamedPlaceByNumber(String phoneNumber) {
        String encodedNumber = URLEncoder.encode(phoneNumber);
        HtmlDocumentHandler htmlDocumentHandler = new HtmlDocumentHandler();
        Place place = null;

        try {
            PlaceUtil.getRequest(QUERY_URL + encodedNumber, htmlDocumentHandler);
            if (htmlDocumentHandler.getName() != null) {
                place = new Place();
                // TODO: get business
                place.setName(htmlDocumentHandler.getName());
                place.setPhoneNumber(phoneNumber);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to do reverse lookup", e);
        }

        return place;
    }

    public class HtmlDocumentHandler implements ContentHandler {
        private String mName;
        private String mLastElement;
        private Attributes mLastElementAtts;

        public String getName() {
            return mName;
        }

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
                    && "a".equalsIgnoreCase(mLastElement)
                    && mLastElementAtts.getValue("class") != null
                    && Arrays.asList(
                            mLastElementAtts.getValue("class").toLowerCase()
                                    .split(" ")).contains("fn")) {
                String name = new String(ch, start, length);
                name = name.trim();
                if (!name.isEmpty()) {
                    mName = name;
                }
            }
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
