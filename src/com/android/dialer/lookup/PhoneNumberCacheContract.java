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

import android.net.Uri;

public final class PhoneNumberCacheContract {
    public static final Uri AUTHORITY_URI =
            Uri.parse("content://com.android.dialer.cacheprovider");
    public static final Uri CONTACT_URI =
            Uri.withAppendedPath(AUTHORITY_URI, "contact");
    public static final Uri PHOTO_URI =
            Uri.withAppendedPath(AUTHORITY_URI, "photo");
    public static final Uri THUMBNAIL_URI =
            Uri.withAppendedPath(AUTHORITY_URI, "thumbnail");

    private PhoneNumberCacheContract() {
    }

    public static Uri getContactLookupUri(String number) {
        return CONTACT_URI.buildUpon().appendPath(number).build();
    }

    public static Uri getPhotoLookupUri(String number) {
        return PHOTO_URI.buildUpon().appendPath(number).build();
    }

    public static Uri getThumbnailLookupUri(String number) {
        return THUMBNAIL_URI.buildUpon().appendPath(number).build();
    }
}
