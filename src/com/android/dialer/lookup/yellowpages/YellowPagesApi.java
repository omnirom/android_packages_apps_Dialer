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

package com.android.dialer.lookup.yellowpages;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

public class YellowPagesApi {
    private static final String TAG = YellowPagesApi.class.getSimpleName();

    private static final String LOOKUP_URL = "http://www.yellowpages.com/phone?phone_search_terms=";

    private String mNumber = null;
    public String mOutput = null;
    private ContactInfo mInfo = null;

    public YellowPagesApi(String number) {
        mNumber = number;
    }

    private void fetchPage() throws IOException {
        mOutput = httpGet(LOOKUP_URL + mNumber);
    }

    private String httpGet(String url) throws IOException {
        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(url);

        HttpResponse response = client.execute(get);
        int status = response.getStatusLine().getStatusCode();

        // Android's org.apache.http doesn't have the RedirectStrategy class
        if (status == HttpStatus.SC_MOVED_PERMANENTLY
                || status == HttpStatus.SC_MOVED_TEMPORARILY) {
            Header[] headers = response.getHeaders("Location");

            if (headers != null && headers.length != 0) {
                String newUrl = headers[headers.length - 1].getValue();
                return httpGet(newUrl);
            } else {
                return null;
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getEntity().writeTo(out);

        return new String(out.toByteArray());
    }

    private String getPhotoUrl(String website) throws IOException {
        String output = httpGet(website);

        Matcher m;

        Pattern regexGallery = Pattern.compile(
                "href=\"([^\"]+gallery\\?lid=[^\"]+)\"", Pattern.DOTALL);

        String galleryUrl = null;

        m = regexGallery.matcher(output);
        if (m.find()) {
            galleryUrl = "http://www.yellowpages.com" + m.group(1).trim();
        }

        if (galleryUrl == null) {
            return null;
        }

        // Get first image

        output = httpGet(galleryUrl);

        Pattern regexPhoto = Pattern.compile(
                "\"type\":\"image\",\"src\":\"([^\"]+)\"", Pattern.DOTALL);

        String photoUrl = null;

        m = regexPhoto.matcher(output);
        if (m.find()) {
            photoUrl = m.group(1).trim();
        }

        return photoUrl;
    }

    private void buildContactInfo() throws IOException {
        Matcher m;

        // Name
        Pattern regexNameAndWebsite = Pattern.compile(
                "<a href=\"([^>]+?)\"[^>]+?class=\"url[^>]+?>([^<]+)</a>",
                Pattern.DOTALL);
        String name = null;
        String website = null;

        m = regexNameAndWebsite.matcher(mOutput);
        if (m.find()) {
            website = m.group(1).trim();
            name = m.group(2).trim();
        }

        // Formatted phone number
        Pattern regexPhoneNumber = Pattern.compile(
                "business-phone.*?>\n*([^\n<]+)\n*<", Pattern.DOTALL);
        String phoneNumber = null;

        m = regexPhoneNumber.matcher(mOutput);
        if (m.find()) {
            phoneNumber = m.group(1).trim();
        }

        // Address
        Pattern regexAddressStreet = Pattern.compile(
                "street-address.*?>\n*([^\n<]+)\n*<", Pattern.DOTALL);
        Pattern regexAddressCity = Pattern.compile(
                "locality.*?>\n*([^\n<]+)\n*<", Pattern.DOTALL);
        Pattern regexAddressState = Pattern.compile(
                "region.*?>\n*([^\n<]+)\n*<", Pattern.DOTALL);
        Pattern regexAddressZip = Pattern.compile(
                "postal-code.*?>\n*([^\n<]+)\n*<", Pattern.DOTALL);

        String addressStreet = null;
        String addressCity = null;
        String addressState = null;
        String addressZip = null;

        m = regexAddressStreet.matcher(mOutput);
        if (m.find()) {
            addressStreet = m.group(1).trim();
            if (addressStreet.endsWith(",")) {
                addressStreet = addressStreet.substring(0,
                        addressStreet.length() - 1);
            }
        }

        m = regexAddressCity.matcher(mOutput);
        if (m.find()) {
            addressCity = m.group(1).trim();
        }

        m = regexAddressState.matcher(mOutput);
        if (m.find()) {
            addressState = m.group(1).trim();
        }

        m = regexAddressZip.matcher(mOutput);
        if (m.find()) {
            addressZip = m.group(1).trim();
        }

        StringBuilder sb = new StringBuilder();

        if (addressStreet != null && addressStreet.length() != 0) {
            sb.append(addressStreet);
        }
        if (addressCity != null && addressCity.length() != 0) {
            sb.append(", ");
            sb.append(addressCity);
        }
        if (addressState != null && addressState.length() != 0) {
            sb.append(", ");
            sb.append(addressState);
        }
        if (addressZip != null && addressZip.length() != 0) {
            sb.append(", ");
            sb.append(addressZip);
        }

        String address = sb.toString();
        if (address.length() == 0) {
            address = null;
        }

        String photoUrl = null;
        if (website != null) {
            photoUrl = getPhotoUrl(website);
        }

        ContactInfo info = new ContactInfo();
        info.name = name;
        info.address = address;
        info.formattedNumber = phoneNumber != null ? phoneNumber : mNumber;
        info.website = website;
        info.photoUrl = photoUrl;
        mInfo = info;
    }

    public ContactInfo getContactInfo() throws IOException {
        if (mInfo == null) {
            fetchPage();

            buildContactInfo();
        }

        return mInfo;
    }

    public static class ContactInfo {
        String name;
        String address;
        String formattedNumber;
        String website;
        String photoUrl;
    }
}
