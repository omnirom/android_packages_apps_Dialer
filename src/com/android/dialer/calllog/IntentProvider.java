/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.calllog;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.telecom.PhoneAccountHandle;

import com.android.contacts.common.CallUtil;
import com.android.dialer.CallDetailActivity;

/**
 * Used to create an intent to attach to an action in the call log.
 * <p>
 * The intent is constructed lazily with the given information.
 */
public abstract class IntentProvider {

    private static final String TAG = IntentProvider.class.getSimpleName();

    public abstract Intent getIntent(Context context);

    public static IntentProvider getReturnCallIntentProvider(final String number) {
        return getReturnCallIntentProvider(number, null);
    }

    public static IntentProvider getReturnCallIntentProvider(final String number,
            final PhoneAccountHandle accountHandle) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                return CallUtil.getCallIntent(number, accountHandle);
            }
        };
    }

    public static IntentProvider getReturnVideoCallIntentProvider(final String number) {
        return getReturnVideoCallIntentProvider(number, null);
    }

    public static IntentProvider getReturnVideoCallIntentProvider(final String number,
            final PhoneAccountHandle accountHandle) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                return CallUtil.getVideoCallIntent(number, accountHandle);
            }
        };
    }

    public static IntentProvider getPlayVoicemailIntentProvider(final long rowId,
            final String voicemailUri) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                Intent intent = new Intent(context, CallDetailActivity.class);
                intent.setData(ContentUris.withAppendedId(
                        Calls.CONTENT_URI_WITH_VOICEMAIL, rowId));
                if (voicemailUri != null) {
                    intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI,
                            Uri.parse(voicemailUri));
                }
                intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_START_PLAYBACK, true);
                return intent;
            }
        };
    }

    /**
     * Retrieves the call details intent provider for an entry in the call log.
     *
     * @param id The call ID of the first call in the call group.
     * @param extraIds The call ID of the other calls grouped together with the call.
     * @param voicemailUri If call log entry is for a voicemail, the voicemail URI.
     * @return The call details intent provider.
     */
    public static IntentProvider getCallDetailIntentProvider(
            final long id, final long[] extraIds, final String voicemailUri) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                Intent intent = new Intent(context, CallDetailActivity.class);
                // Check if the first item is a voicemail.
                if (voicemailUri != null) {
                    intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI,
                            Uri.parse(voicemailUri));
                }
                intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_START_PLAYBACK, false);

                if (extraIds != null && extraIds.length > 0) {
                    intent.putExtra(CallDetailActivity.EXTRA_CALL_LOG_IDS, extraIds);
                } else {
                    // If there is a single item, use the direct URI for it.
                    intent.setData(ContentUris.withAppendedId(
                            Calls.CONTENT_URI_WITH_VOICEMAIL, id));
                }
                return intent;
            }
        };
    }
}
