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

package com.android.dialer.lookup.whitepages;

import com.android.dialer.lookup.ReverseLookup;
import com.android.incallui.service.PhoneNumberServiceImpl.PhoneNumberInfoImpl;

import android.content.Context;
import android.util.Pair;

import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;

import java.io.IOException;

public class WhitePagesReverseLookup extends ReverseLookup {
    private static final String TAG =
            WhitePagesReverseLookup.class.getSimpleName();

    public WhitePagesReverseLookup(Context context) {
    }

    /**
     * Lookup image
     *
     * @param context The application context
     * @param url The image URL
     * @param data Extra data (a authentication token, perhaps)
     */
    public byte[] lookupImage(Context context, String url, Object data) {
        return null;
    }

    /**
     * Perform phone number lookup.
     *
     * @param context The application context
     * @param normalizedNumber The normalized phone number
     * @param formattedNumber The formatted phone number
     * @param isIncoming Whether the call is incoming or outgoing
     * @return The phone number info object
     */
    public Pair<PhoneNumberInfoImpl, Object> lookupNumber(
            Context context, String normalizedNumber, String formattedNumber,
            boolean isIncoming) {
        WhitePagesApi wpa = new WhitePagesApi(context, normalizedNumber);
        WhitePagesApi.ContactInfo info = null;

        try {
            info = wpa.getContactInfo();
        } catch (IOException e) {
            return null;
        }

        if (info.name == null) {
            return null;
        }

        ContactBuilder builder = new ContactBuilder(
                normalizedNumber, formattedNumber);

        ContactBuilder.Name n = new ContactBuilder.Name();
        n.displayName = info.name;
        builder.setName(n);

        ContactBuilder.PhoneNumber pn = new ContactBuilder.PhoneNumber();
        pn.number = info.formattedNumber;
        pn.type = Phone.TYPE_MAIN;
        builder.addPhoneNumber(pn);

        if (info.address != null) {
            ContactBuilder.Address a = new ContactBuilder.Address();
            a.formattedAddress = info.address;
            a.type = StructuredPostal.TYPE_HOME;
            builder.addAddress(a);
        }

        ContactBuilder.WebsiteUrl w = new ContactBuilder.WebsiteUrl();
        w.url = info.website;
        w.type = Website.TYPE_PROFILE;
        builder.addWebsite(w);

        return Pair.create(builder.build(), null);
    }
}
