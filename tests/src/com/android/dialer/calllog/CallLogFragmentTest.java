/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.VoicemailContract;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.android.contacts.common.test.FragmentTestActivity;
import com.android.dialer.CallDetailActivity;
import com.android.dialer.R;

import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Random;

/**
 * Tests for the contact call list activity.
 *
 * Running all tests:
 *
 *   runtest contacts
 * or
 *   adb shell am instrument \
 *     -w com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@LargeTest
public class CallLogFragmentTest extends ActivityInstrumentationTestCase2<FragmentTestActivity> {
    private static final int RAND_DURATION = -1;
    private static final long NOW = -1L;

    /** A test value for the URI of a contact. */
    private static final Uri TEST_LOOKUP_URI = Uri.parse("content://contacts/2");
    /** A test value for the country ISO of the phone number in the call log. */
    private static final String TEST_COUNTRY_ISO = "US";
    /** A phone number to be used in tests. */
    private static final String TEST_NUMBER = "12125551000";
    /** The formatted version of {@link #TEST_NUMBER}. */
    private static final String TEST_FORMATTED_NUMBER = "1 212-555-1000";

    private static final String TEST_DEFAULT_CUSTOM_LABEL = "myLabel";

    /** The activity in which we are hosting the fragment. */
    private FragmentTestActivity mActivity;
    private CallLogFragment mFragment;
    private FrameLayout mParentView;
    /**
     * The adapter used by the fragment to build the rows in the call log. We use it with our own in
     * memory database.
     */
    private CallLogAdapter mAdapter;
    private String mVoicemail;

    // In memory array to hold the rows corresponding to the 'calls' table.
    private MatrixCursor mCursor;
    private int mIndex;  // Of the next row.

    private Random mRnd;

    // An item in the call list. All the methods performing checks use it.
    private CallLogListItemViews mItem;
    // The list of views representing the data in the DB. View are in
    // reverse order compare to the DB.
    private View[] mList;

    public CallLogFragmentTest() {
        super(FragmentTestActivity.class);
        mIndex = 1;
        mRnd = new Random();
    }

    @Override
    public void setUp() {
        mActivity = getActivity();
        // Needed by the CallLogFragment.
        mActivity.setTheme(R.style.DialtactsTheme);

        // Create the fragment and load it into the activity.
        mFragment = new CallLogFragment();
        FragmentManager fragmentManager = mActivity.getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.add(FragmentTestActivity.LAYOUT_ID, mFragment);
        transaction.commitAllowingStateLoss();
        // Wait for the fragment to be loaded.
        getInstrumentation().waitForIdleSync();

        final TelephonyManager telephonyManager =
                (TelephonyManager) mActivity.getSystemService(Context.TELEPHONY_SERVICE);
        mVoicemail = telephonyManager.getVoiceMailNumber();
        mAdapter = mFragment.getAdapter();
        // Do not process requests for details during tests. This would start a background thread,
        // which makes the tests flaky.
        mAdapter.disableRequestProcessingForTest();
        mAdapter.stopRequestProcessing();
        mParentView = new FrameLayout(mActivity);
        mCursor = new MatrixCursor(CallLogQuery._PROJECTION);
    }

    /**
     * Checks that the call icon is not visible for private and
     * unknown numbers.
     * Use 2 passes, one where new views are created and one where
     * half of the total views are updated and the other half created.
     */
    @MediumTest
    public void testCallViewIsNotVisibleForPrivateAndUnknownNumbers() {
        final int SIZE = 100;
        mList = new View[SIZE];

        // Insert the first batch of entries.
        mCursor.moveToFirst();
        insertRandomEntries(SIZE / 2);
        int startOfSecondBatch = mCursor.getPosition();

        buildViewListFromDb();
        checkCallStatus();

        // Append the rest of the entries. We keep the first set of
        // views around so they get updated and not built from
        // scratch, this exposes some bugs that are not there when the
        // call log is launched for the 1st time but show up when the
        // call log gets updated afterwards.
        mCursor.move(startOfSecondBatch);
        insertRandomEntries(SIZE / 2);

        buildViewListFromDb();
        checkCallStatus();
    }

    @MediumTest
    public void testCallAndGroupViews_GroupView() {
        mCursor.moveToFirst();
        insertPrivate(NOW, 0);
        insertPrivate(NOW, 0);
        insertPrivate(NOW, 0);
        View view = mAdapter.newGroupView(getActivity(), mParentView);
        mAdapter.bindGroupView(view, getActivity(), mCursor, 3, false);
    }

    @MediumTest
    public void testCallAndGroupViews_StandAloneView() {
        mCursor.moveToFirst();
        insertPrivate(NOW, 0);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindViewForTest(view, getActivity(), mCursor);
    }

    @MediumTest
    public void testCallAndGroupViews_ChildView() {
        mCursor.moveToFirst();
        insertPrivate(NOW, 0);
        View view = mAdapter.newChildView(getActivity(), mParentView);
        mAdapter.bindChildView(view, getActivity(), mCursor);
    }

    @MediumTest
    public void testBindView_NumberOnlyNoCache() {
        mCursor.moveToFirst();
        insert(TEST_NUMBER, Calls.PRESENTATION_ALLOWED, NOW, 0, Calls.INCOMING_TYPE);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindViewForTest(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertNameIs(views, TEST_NUMBER);
    }

    @MediumTest
    public void testBindView_NumberOnlyDbCachedFormattedNumber() {
        mCursor.moveToFirst();
        Object[] values = getValuesToInsert(TEST_NUMBER,
                Calls.PRESENTATION_ALLOWED, NOW, 0, Calls.INCOMING_TYPE);
        values[CallLogQuery.CACHED_FORMATTED_NUMBER] = TEST_FORMATTED_NUMBER;
        insertValues(values);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindViewForTest(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertNameIs(views, TEST_FORMATTED_NUMBER);
    }

    @MediumTest
    public void testBindView_WithCachedName() {
        mCursor.moveToFirst();
        // provide a default custom label instead of an empty string, which corresponds to
        // {@value com.android.dialer.calllog.ContactInfo#GEOCODE_AS_LABEL}
        insertWithCachedValues(TEST_NUMBER, NOW, 0, Calls.INCOMING_TYPE,
                "John Doe", Phone.TYPE_HOME, TEST_DEFAULT_CUSTOM_LABEL);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindViewForTest(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertNameIs(views, "John Doe");
        assertLabel(views, TEST_FORMATTED_NUMBER, getTypeLabel(Phone.TYPE_HOME));
    }

    @MediumTest
    public void testBindView_UriNumber() {
        mCursor.moveToFirst();
        insertWithCachedValues("sip:johndoe@gmail.com", NOW, 0, Calls.INCOMING_TYPE,
                "John Doe", Phone.TYPE_HOME, TEST_DEFAULT_CUSTOM_LABEL);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindViewForTest(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertNameIs(views, "John Doe");
        assertLabel(views, "sip:johndoe@gmail.com", "sip:johndoe@gmail.com");
    }

    @MediumTest
    public void testBindView_HomeLabel() {
        mCursor.moveToFirst();
        // provide a default custom label instead of an empty string, which corresponds to
        // {@value com.android.dialer.calllog.ContactInfo#GEOCODE_AS_LABEL}
        insertWithCachedValues(TEST_NUMBER, NOW, 0, Calls.INCOMING_TYPE,
                "John Doe", Phone.TYPE_HOME, TEST_DEFAULT_CUSTOM_LABEL);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindViewForTest(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertNameIs(views, "John Doe");
        assertLabel(views, TEST_FORMATTED_NUMBER, getTypeLabel(Phone.TYPE_HOME));
    }

    @MediumTest
    public void testBindView_WorkLabel() {
        mCursor.moveToFirst();
        // provide a default custom label instead of an empty string, which corresponds to
        // {@link com.android.dialer.calllog.ContactInfo#GEOCODE_AS_LABEL}
        insertWithCachedValues(TEST_NUMBER, NOW, 0, Calls.INCOMING_TYPE,
                "John Doe", Phone.TYPE_WORK, TEST_DEFAULT_CUSTOM_LABEL);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindViewForTest(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertNameIs(views, "John Doe");
        assertLabel(views, TEST_FORMATTED_NUMBER, getTypeLabel(Phone.TYPE_WORK));
    }

    @MediumTest
    public void testBindView_CustomLabel() {
        mCursor.moveToFirst();
        String numberLabel = "My label";
        insertWithCachedValues(TEST_NUMBER, NOW, 0, Calls.INCOMING_TYPE,
                "John Doe", Phone.TYPE_CUSTOM, numberLabel);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindViewForTest(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertNameIs(views, "John Doe");
        assertLabel(views, TEST_FORMATTED_NUMBER, numberLabel);
    }

    @MediumTest
    public void testBindView_WithQuickContactBadge() {
        mCursor.moveToFirst();
        insertWithCachedValues(TEST_NUMBER, NOW, 0, Calls.INCOMING_TYPE,
                "John Doe", Phone.TYPE_HOME, "");
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindViewForTest(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertTrue(views.quickContactView.isEnabled());
    }

    @MediumTest
    public void testBindView_WithoutQuickContactBadge() {
        mCursor.moveToFirst();
        insert(TEST_NUMBER, Calls.PRESENTATION_ALLOWED, NOW, 0, Calls.INCOMING_TYPE);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindViewForTest(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        assertFalse(views.quickContactView.isEnabled());
    }

    @MediumTest
    public void testBindView_CallButton() {
        mCursor.moveToFirst();
        insert(TEST_NUMBER, Calls.PRESENTATION_ALLOWED, NOW, 0, Calls.INCOMING_TYPE);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindViewForTest(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();

        // The primaryActionView tag is set in the
        // {@link com.android.dialer.calllog.CallLogAdapter#bindView} method.  If it is possible
        // to place a call to the phone number, a call intent will have been created for the
        // primaryActionView.
        IntentProvider intentProvider = (IntentProvider) views.callBackButtonView.getTag();
        Intent intent = intentProvider.getIntent(mActivity);
        // Starts a call.
        assertEquals(Intent.ACTION_CALL_PRIVILEGED, intent.getAction());
        // To the entry's number.
        assertEquals(Uri.parse("tel:" + TEST_NUMBER), intent.getData());
    }

    @MediumTest
    public void testBindView_PlayButton() {
        mCursor.moveToFirst();
        insertVoicemail(TEST_NUMBER, Calls.PRESENTATION_ALLOWED, NOW, 0);
        View view = mAdapter.newStandAloneView(getActivity(), mParentView);
        mAdapter.bindViewForTest(view, getActivity(), mCursor);

        CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        IntentProvider intentProvider = (IntentProvider) views.voicemailButtonView.getTag();
        Intent intent = intentProvider.getIntent(mActivity);
        // Starts the call detail activity.
        assertEquals(new ComponentName(mActivity, CallDetailActivity.class),
                intent.getComponent());
        // With the given entry.
        assertEquals(ContentUris.withAppendedId(Calls.CONTENT_URI_WITH_VOICEMAIL, 1),
                intent.getData());
        // With the URI of the voicemail.
        assertEquals(
                ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, 1),
                intent.getParcelableExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI));
        // And starts playback.
        assertTrue(
                intent.getBooleanExtra(CallDetailActivity.EXTRA_VOICEMAIL_START_PLAYBACK, false));
    }

    /** Returns the label associated with a given phone type. */
    private CharSequence getTypeLabel(int phoneType) {
        return Phone.getTypeLabel(getActivity().getResources(), phoneType, "");
    }

    //
    // HELPERS to check conditions on the DB/views
    //
    /**
     * Go over the views in the list and check to ensure that
     * callable numbers have an associated call intent, where numbers
     * which are not callable have a null intent.
     */
    private void checkCallStatus() {
        for (int i = 0; i < mList.length; i++) {
            if (null == mList[i]) {
                break;
            }
            mItem = (CallLogListItemViews) mList[i].getTag();
            int presentation = getPhoneNumberPresentationForListEntry(i);
            if (presentation == Calls.PRESENTATION_RESTRICTED ||
                    presentation == Calls.PRESENTATION_UNKNOWN) {
                //If number is not callable, the primary action view should have a null tag.
                assertNull(mItem.callBackButtonView.getTag());
            } else {
                //If the number is callable, the primary action view should have a non-null tag.
                assertNotNull(mItem.callBackButtonView.getTag());

                IntentProvider intentProvider = (IntentProvider)mItem.callBackButtonView.getTag();
                Intent callIntent = intentProvider.getIntent(mActivity);

                //The intent should be to make the call
                assertEquals(Intent.ACTION_CALL_PRIVILEGED, callIntent.getAction());
            }
        }
    }


    //
    // HELPERS to setup the tests.
    //

    /**
     * Get the Bitmap from the icons in the contacts package.
     */
    private Bitmap getBitmap(String resName) {
        Resources r = mActivity.getResources();
        int resid = r.getIdentifier(resName, "drawable",
                getInstrumentation().getTargetContext().getPackageName());
        BitmapDrawable d = (BitmapDrawable) r.getDrawable(resid);
        assertNotNull(d);
        return d.getBitmap();
    }

    //
    // HELPERS to build/update the call entries (views) from the DB.
    //

    /**
     * Read the DB and foreach call either update the existing view if
     * one exists already otherwise create one.
     * The list is build from a DESC view of the DB (last inserted entry is first).
     */
    private void buildViewListFromDb() {
        int i = 0;
        mCursor.moveToLast();
        while(!mCursor.isBeforeFirst()) {
            if (null == mList[i]) {
                mList[i] = mAdapter.newStandAloneView(mActivity, mParentView);
            }
            mAdapter.bindViewForTest(mList[i], mActivity, mCursor);
            mCursor.moveToPrevious();
            i++;
        }
    }

    /** Returns the number presentation associated with the given entry in {{@link #mList}. */
    private int getPhoneNumberPresentationForListEntry(int index) {
        // The entries are added backward, so count from the end of the cursor.
        mCursor.moveToPosition(mCursor.getCount() - index - 1);
        return mCursor.getInt(CallLogQuery.NUMBER_PRESENTATION);
    }

    //
    // HELPERS to insert numbers in the call log DB.
    //

    /**
     * Insert a certain number of random numbers in the DB. Makes sure
     * there is at least one private and one unknown number in the DB.
     * @param num Of entries to be inserted.
     */
    private void insertRandomEntries(int num) {
        if (num < 10) {
            throw new IllegalArgumentException("num should be >= 10");
        }
        boolean privateOrUnknownOrVm[];
        privateOrUnknownOrVm = insertRandomRange(0, num - 2);

        if (privateOrUnknownOrVm[0] && privateOrUnknownOrVm[1]) {
            insertRandomRange(num - 2, num);
        } else {
            insertPrivate(NOW, RAND_DURATION);
            insertUnknown(NOW, RAND_DURATION);
        }
    }

    /**
     * Insert a new call entry in the test DB.
     *
     * It includes the values for the cached contact associated with the number.
     *
     * @param number The phone number.
     * @param date In millisec since epoch. Use NOW to use the current time.
     * @param duration In seconds of the call. Use RAND_DURATION to pick a random one.
     * @param type Either Call.OUTGOING_TYPE or Call.INCOMING_TYPE or Call.MISSED_TYPE.
     * @param cachedName the name of the contact with this number
     * @param cachedNumberType the type of the number, from the contact with this number
     * @param cachedNumberLabel the label of the number, from the contact with this number
     */
    private void insertWithCachedValues(String number, long date, int duration, int type,
            String cachedName, int cachedNumberType, String cachedNumberLabel) {
        insert(number, Calls.PRESENTATION_ALLOWED, date, duration, type);
        ContactInfo contactInfo = new ContactInfo();
        contactInfo.lookupUri = TEST_LOOKUP_URI;
        contactInfo.name = cachedName;
        contactInfo.type = cachedNumberType;
        contactInfo.label = cachedNumberLabel;
        String formattedNumber = PhoneNumberUtils.formatNumber(number, TEST_COUNTRY_ISO);
        if (formattedNumber == null) {
            formattedNumber = number;
        }
        contactInfo.formattedNumber = formattedNumber;
        contactInfo.normalizedNumber = number;
        contactInfo.photoId = 0;
        mAdapter.injectContactInfoForTest(number, TEST_COUNTRY_ISO, contactInfo);
    }

    /**
     * Insert a new call entry in the test DB.
     * @param number The phone number.
     * @param presentation Number representing display rules for "allowed",
     *               "payphone", "restricted", or "unknown".
     * @param date In millisec since epoch. Use NOW to use the current time.
     * @param duration In seconds of the call. Use RAND_DURATION to pick a random one.
     * @param type Either Call.OUTGOING_TYPE or Call.INCOMING_TYPE or Call.MISSED_TYPE.
     */
    private void insert(String number, int presentation, long date, int duration, int type) {
        insertValues(getValuesToInsert(number, presentation, date, duration, type));
    }

    /** Inserts the given values in the cursor. */
    private void insertValues(Object[] values) {
        mCursor.addRow(values);
        ++mIndex;
    }

    /**
     * Returns the values for a new call entry.
     *
     * @param number The phone number.
     * @param presentation Number representing display rules for "allowed",
     *               "payphone", "restricted", or "unknown".
     * @param date In millisec since epoch. Use NOW to use the current time.
     * @param duration In seconds of the call. Use RAND_DURATION to pick a random one.
     * @param type Either Call.OUTGOING_TYPE or Call.INCOMING_TYPE or Call.MISSED_TYPE.
     */
    private Object[] getValuesToInsert(String number, int presentation,
            long date, int duration, int type) {
        Object[] values = CallLogQueryTestUtils.createTestValues();
        values[CallLogQuery.ID] = mIndex;
        values[CallLogQuery.NUMBER] = number;
        values[CallLogQuery.NUMBER_PRESENTATION] = presentation;
        values[CallLogQuery.DATE] = date == NOW ? new Date().getTime() : date;
        values[CallLogQuery.DURATION] = duration < 0 ? mRnd.nextInt(10 * 60) : duration;
        if (mVoicemail != null && mVoicemail.equals(number)) {
            assertEquals(Calls.OUTGOING_TYPE, type);
        }
        values[CallLogQuery.CALL_TYPE] = type;
        values[CallLogQuery.COUNTRY_ISO] = TEST_COUNTRY_ISO;
        return values;
    }

    /**
     * Insert a new voicemail entry in the test DB.
     * @param number The phone number.
     * @param presentation Number representing display rules for "allowed",
     *               "payphone", "restricted", or "unknown".
     * @param date In millisec since epoch. Use NOW to use the current time.
     * @param duration In seconds of the call. Use RAND_DURATION to pick a random one.
     */
    private void insertVoicemail(String number, int presentation, long date, int duration) {
        Object[] values = getValuesToInsert(number, presentation, date, duration, Calls.VOICEMAIL_TYPE);
        // Must have the same index as the row.
        values[CallLogQuery.VOICEMAIL_URI] =
                ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, mIndex);
        insertValues(values);
    }

    /**
     * Insert a new private call entry in the test DB.
     * @param date In millisec since epoch. Use NOW to use the current time.
     * @param duration In seconds of the call. Use RAND_DURATION to pick a random one.
     */
    private void insertPrivate(long date, int duration) {
        insert("", Calls.PRESENTATION_RESTRICTED, date, duration, Calls.INCOMING_TYPE);
    }

    /**
     * Insert a new unknown call entry in the test DB.
     * @param date In millisec since epoch. Use NOW to use the current time.
     * @param duration In seconds of the call. Use RAND_DURATION to pick a random one.
     */
    private void insertUnknown(long date, int duration) {
        insert("", Calls.PRESENTATION_UNKNOWN, date, duration, Calls.INCOMING_TYPE);
    }

    /**
     * Insert a new call to voicemail entry in the test DB.
     * @param date In millisec since epoch. Use NOW to use the current time.
     * @param duration In seconds of the call. Use RAND_DURATION to pick a random one.
     */
    private void insertCalltoVoicemail(long date, int duration) {
        // mVoicemail may be null
        if (mVoicemail != null) {
            insert(mVoicemail, Calls.PRESENTATION_ALLOWED, date, duration, Calls.OUTGOING_TYPE);
        }
    }

    /**
     * Insert a range [start, end) of random numbers in the DB. For
     * each row, there is a 1/10 probability that the number will be
     * marked as PRIVATE or UNKNOWN or VOICEMAIL. For regular numbers, a number is
     * inserted, its last 4 digits will be the number of the iteration
     * in the range.
     * @param start Of the range.
     * @param end Of the range (excluded).
     * @return An array with 2 booleans [0 = private number, 1 =
     * unknown number, 2 = voicemail] to indicate if at least one
     * private or unknown or voicemail number has been inserted. Since
     * the numbers are random some tests may want to enforce the
     * insertion of such numbers.
     */
    // TODO: Should insert numbers with contact entries too.
    private boolean[] insertRandomRange(int start, int end) {
        boolean[] privateOrUnknownOrVm = new boolean[] {false, false, false};

        for (int i = start; i < end; i++ ) {
            int type = mRnd.nextInt(10);

            if (0 == type) {
                insertPrivate(NOW, RAND_DURATION);
                privateOrUnknownOrVm[0] = true;
            } else if (1 == type) {
                insertUnknown(NOW, RAND_DURATION);
                privateOrUnknownOrVm[1] = true;
            } else if (2 == type) {
                insertCalltoVoicemail(NOW, RAND_DURATION);
                privateOrUnknownOrVm[2] = true;
            } else {
                int inout = mRnd.nextBoolean() ? Calls.OUTGOING_TYPE :  Calls.INCOMING_TYPE;
                final Formatter formatter = new Formatter();
                String number = formatter.format("1800123%04d", i).toString();
                formatter.close();
                insert(number, Calls.PRESENTATION_ALLOWED, NOW, RAND_DURATION, inout);
            }
        }
        return privateOrUnknownOrVm;
    }

    /** Asserts that the name text view is shown and contains the given text. */
    private void assertNameIs(CallLogListItemViews views, String name) {
        assertEquals(View.VISIBLE, views.phoneCallDetailsViews.nameView.getVisibility());
        assertEquals(name, views.phoneCallDetailsViews.nameView.getText());
    }

    /** Asserts that the label text view contains the given text. */
    private void assertLabel(CallLogListItemViews views, CharSequence number,
            CharSequence label) {
        if (label != null) {
            assertTrue(views.phoneCallDetailsViews.callLocationAndDate.getText().toString()
                    .contains(label));
        }
    }
}
