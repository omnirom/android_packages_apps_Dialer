/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved
 * Not a Contribution.
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

package com.android.dialer.app.calllog;

import static android.Manifest.permission.READ_CALL_LOG;

import android.app.Activity;
import android.app.Fragment;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.v13.app.FragmentCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import com.android.dialer.app.Bindings;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.calllogcache.CallLogCache;
import com.android.dialer.app.contactinfo.ContactInfoCache;
import com.android.dialer.app.contactinfo.ContactInfoCache.OnContactInfoChangedListener;
import com.android.dialer.app.contactinfo.ExpirableCacheHeadlessFragment;
import com.android.dialer.app.list.ListsFragment;
import com.android.dialer.app.voicemail.VoicemailPlaybackPresenter;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialer.widget.EmptyContentView.OnEmptyViewActionButtonClickedListener;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.database.CallLogQueryHandler;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.phonenumbercache.ContactInfoHelper;
import com.android.dialer.util.PermissionsUtil;

/**
 * Displays a list of call log entries. To filter for a particular kind of call (all, missed or
 * voicemails), specify it in the constructor.
 */
public class MSimCallLogFragment extends Fragment
    implements CallLogQueryHandler.Listener,
        CallLogAdapter.CallFetcher,
        CallLogAdapter.MultiSelectRemoveView,
        OnEmptyViewActionButtonClickedListener,
        FragmentCompat.OnRequestPermissionsResultCallback,
        CallLogModalAlertManager.Listener,
        OnClickListener {
  private static final String KEY_FILTER_TYPE = "filter_type";
  private static final String KEY_LOG_LIMIT = "log_limit";
  private static final String KEY_DATE_LIMIT = "date_limit";
  private static final String KEY_IS_CALL_LOG_ACTIVITY = "is_call_log_activity";
  private static final String KEY_HAS_READ_CALL_LOG_PERMISSION = "has_read_call_log_permission";
  private static final String KEY_REFRESH_DATA_REQUIRED = "refresh_data_required";
  private static final String KEY_SELECT_ALL_MODE = "select_all_mode_checked";

  // No limit specified for the number of logs to show; use the CallLogQueryHandler's default.
  private static final int NO_LOG_LIMIT = -1;
  // No date-based filtering.
  private static final int NO_DATE_LIMIT = 0;

  private static final int READ_CALL_LOG_PERMISSION_REQUEST_CODE = 1;

  private static final int CALL = 1;

  private static final int INVALID_SIM_SLOT_INDEX = -1;

  private static final int EVENT_UPDATE_DISPLAY = 1;

  private static final long MILLIS_IN_MINUTE = 60 * 1000;
  private final Handler mHandler = new Handler();
  // See issue 6363009
  private final ContentObserver mCallLogObserver = new CustomContentObserver();
  private final ContentObserver mContactsObserver = new CustomContentObserver();
  private View mMultiSelectUnSelectAllViewContent;
  private TextView mSelectUnselectAllViewText;
  private ImageView mSelectUnselectAllIcon;
  private RecyclerView mRecyclerView;
  private LinearLayoutManager mLayoutManager;
  private CallLogAdapter mAdapter;
  private CallLogQueryHandler mCallLogQueryHandler;
  private boolean mScrollToTop;
  private EmptyContentView mEmptyListView;
  private KeyguardManager mKeyguardManager;
  private ContactInfoCache mContactInfoCache;
  private final OnContactInfoChangedListener mOnContactInfoChangedListener =
      new OnContactInfoChangedListener() {
        @Override
        public void onContactInfoChanged() {
          if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
          }
        }
      };
  private boolean mRefreshDataRequired;
  private boolean mHasReadCallLogPermission;
  // Exactly same variable is in Fragment as a package private.
  private boolean mMenuVisible = true;
  // Default to all calls.
  private int mCallTypeFilter = CallLogQueryHandler.CALL_TYPE_ALL;
  // Log limit - if no limit is specified, then the default in {@link CallLogQueryHandler}
  // will be used.
  private int mLogLimit = NO_LOG_LIMIT;
  // Date limit (in millis since epoch) - when non-zero, only calls which occurred on or after
  // the date filter are included.  If zero, no date-based filtering occurs.
  private long mDateLimit = NO_DATE_LIMIT;
  /*
   * True if this instance of the MSimCallLogFragment shown in the CallLogActivity.
   */
  private boolean mIsCallLogActivity = false;
  private boolean selectAllMode;
  private final Handler mDisplayUpdateHandler =
      new Handler() {
        @Override
        public void handleMessage(Message msg) {
          switch (msg.what) {
            case EVENT_UPDATE_DISPLAY:
              refreshData();
              rescheduleDisplayUpdate();
              break;
            default:
              throw Assert.createAssertionFailException("Invalid message: " + msg);
          }
        }
      };
  protected CallLogModalAlertManager mModalAlertManager;
  private ViewGroup mModalAlertView;

  // The Spinners to filter call log
  private Spinner mFilterSlotSpinnerView;
  private Spinner mFilterStatusSpinnerView;
  // Key for the call log sub saved in the default preference
  private static final String PREFERENCE_KEY_CALLLOG_SLOT = "call_log_slot";
  // Default to all slots
  private int mCallSlotFilter = INVALID_SIM_SLOT_INDEX;

  public MSimCallLogFragment() {
    this(CallLogQueryHandler.CALL_TYPE_ALL, NO_LOG_LIMIT);
  }

  public MSimCallLogFragment(int filterType) {
    this(filterType, NO_LOG_LIMIT);
  }

  public MSimCallLogFragment(int filterType, boolean isCallLogActivity) {
    this(filterType, NO_LOG_LIMIT);
    mIsCallLogActivity = isCallLogActivity;
  }

  public MSimCallLogFragment(int filterType, int logLimit) {
    this(filterType, logLimit, NO_DATE_LIMIT);
  }

  /**
   * Creates a call log fragment, filtering to include only calls of the desired type, occurring
   * after the specified date.
   *
   * @param filterType type of calls to include.
   * @param dateLimit limits results to calls occurring on or after the specified date.
   */
  public MSimCallLogFragment(int filterType, long dateLimit) {
    this(filterType, NO_LOG_LIMIT, dateLimit);
  }

  /**
   * Creates a call log fragment, filtering to include only calls of the desired type, occurring
   * after the specified date. Also provides a means to limit the number of results returned.
   *
   * @param filterType type of calls to include.
   * @param logLimit limits the number of results to return.
   * @param dateLimit limits results to calls occurring on or after the specified date.
   */
  public MSimCallLogFragment(int filterType, int logLimit, long dateLimit) {
    mLogLimit = logLimit;
    mDateLimit = dateLimit;
  }

  @Override
  public void onCreate(Bundle state) {
    LogUtil.d("MSimCallLogFragment.onCreate", toString());
    super.onCreate(state);
    mRefreshDataRequired = true;
    if (state != null) {
      mCallTypeFilter = state.getInt(KEY_FILTER_TYPE, getSelectedSlotId());
      mLogLimit = state.getInt(KEY_LOG_LIMIT, mLogLimit);
      mDateLimit = state.getLong(KEY_DATE_LIMIT, mDateLimit);
      mIsCallLogActivity = state.getBoolean(KEY_IS_CALL_LOG_ACTIVITY, mIsCallLogActivity);
      mHasReadCallLogPermission = state.getBoolean(KEY_HAS_READ_CALL_LOG_PERMISSION, false);
      mRefreshDataRequired = state.getBoolean(KEY_REFRESH_DATA_REQUIRED, mRefreshDataRequired);
    }

    final Activity activity = getActivity();
    final ContentResolver resolver = activity.getContentResolver();
    mCallLogQueryHandler = new CallLogQueryHandler(activity, resolver, this, mLogLimit);
    mKeyguardManager = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);

    if (PermissionsUtil.hasCallLogReadPermissions(getContext())) {
      resolver.registerContentObserver(CallLog.CONTENT_URI, true, mCallLogObserver);
    } else {
      LogUtil.w("CallLogFragment.onCreate", "call log permission not available");
    }
    if (PermissionsUtil.hasContactsReadPermissions(getContext())) {
      resolver.registerContentObserver(
          ContactsContract.Contacts.CONTENT_URI, true, mContactsObserver);
    } else {
      LogUtil.w("CallLogFragment.onCreate", "contacts permission not available.");
    }
    setHasOptionsMenu(true);
  }

  /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
  @Override
  public boolean onCallsFetched(Cursor cursor) {
    if (getActivity() == null || getActivity().isFinishing()) {
      // Return false; we did not take ownership of the cursor
      return false;
    }
    mAdapter.invalidatePositions();
    mAdapter.setLoading(false);
    mAdapter.changeCursor(cursor);
    // This will update the state of the "Clear call log" menu item.
    getActivity().invalidateOptionsMenu();

    if (cursor != null && cursor.getCount() > 0) {
      mRecyclerView.setPaddingRelative(
          mRecyclerView.getPaddingStart(),
          0,
          mRecyclerView.getPaddingEnd(),
          mRecyclerView.getPaddingBottom());
      mEmptyListView.setVisibility(View.GONE);
    } else {
      mRecyclerView.setPaddingRelative(
          mRecyclerView.getPaddingStart(), 0, mRecyclerView.getPaddingEnd(), 0);
      mEmptyListView.setVisibility(View.VISIBLE);
    }
    if (mScrollToTop) {
      // The smooth-scroll animation happens over a fixed time period.
      // As a result, if it scrolls through a large portion of the list,
      // each frame will jump so far from the previous one that the user
      // will not experience the illusion of downward motion.  Instead,
      // if we're not already near the top of the list, we instantly jump
      // near the top, and animate from there.
      if (mLayoutManager.findFirstVisibleItemPosition() > 5) {
        // TODO: Jump to near the top, then begin smooth scroll.
        mRecyclerView.smoothScrollToPosition(0);
      }
      // Workaround for framework issue: the smooth-scroll doesn't
      // occur if setSelection() is called immediately before.
      mHandler.post(
          new Runnable() {
            @Override
            public void run() {
              if (getActivity() == null || getActivity().isFinishing()) {
                return;
              }
              mRecyclerView.smoothScrollToPosition(0);
            }
          });

      mScrollToTop = false;
    }
    return true;
  }

  @Override
  public void onVoicemailStatusFetched(Cursor statusCursor) {}

  @Override
  public void onVoicemailUnreadCountFetched(Cursor cursor) {}

  @Override
  public void onMissedCallsUnreadCountFetched(Cursor cursor) {}

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
    View view = inflater.inflate(R.layout.msim_call_log_fragment, container, false);
    setupView(view);
    return view;
  }

  protected void setupView(View view) {
    mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
    mRecyclerView.setHasFixedSize(true);
    mLayoutManager = new LinearLayoutManager(getActivity());
    mRecyclerView.setLayoutManager(mLayoutManager);
    mEmptyListView = (EmptyContentView) view.findViewById(R.id.empty_list_view);
    mEmptyListView.setImage(R.drawable.empty_call_log);
    mEmptyListView.setActionClickedListener(this);
    mModalAlertView = (ViewGroup) view.findViewById(R.id.modal_message_container);
    mModalAlertManager =
        new CallLogModalAlertManager(LayoutInflater.from(getContext()), mModalAlertView, this);
    mMultiSelectUnSelectAllViewContent =
        view.findViewById(R.id.multi_select_select_all_view_content);
    mSelectUnselectAllViewText = (TextView) view.findViewById(R.id.select_all_view_text);
    mSelectUnselectAllIcon = (ImageView) view.findViewById(R.id.select_all_view_icon);
    mMultiSelectUnSelectAllViewContent.setOnClickListener(null);
    mSelectUnselectAllIcon.setOnClickListener(this);
    mSelectUnselectAllViewText.setOnClickListener(this);
    mFilterSlotSpinnerView = (Spinner) view.findViewById(R.id.filter_sub_spinner);
    mFilterStatusSpinnerView = (Spinner) view.findViewById(R.id.filter_status_spinner);
  }

  protected void setupData() {
    updateFilterSpinnerViews();
    int activityType =
        mIsCallLogActivity
            ? CallLogAdapter.ACTIVITY_TYPE_CALL_LOG
            : CallLogAdapter.ACTIVITY_TYPE_DIALTACTS;
    String currentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());

    mContactInfoCache =
        new ContactInfoCache(
            ExpirableCacheHeadlessFragment.attach((AppCompatActivity) getActivity())
                .getRetainedCache(),
            new ContactInfoHelper(getActivity(), currentCountryIso),
            mOnContactInfoChangedListener);
    mAdapter =
        Bindings.getLegacy(getActivity())
            .newCallLogAdapter(
                getActivity(),
                mRecyclerView,
                this,
                this,
                activityType == CallLogAdapter.ACTIVITY_TYPE_DIALTACTS
                    ? (CallLogAdapter.OnActionModeStateChangedListener) getActivity()
                    : null,
                new CallLogCache(getActivity()),
                mContactInfoCache,
                getVoicemailPlaybackPresenter(),
                new FilteredNumberAsyncQueryHandler(getActivity()),
                activityType);
    mRecyclerView.setAdapter(mAdapter);
    fetchCalls();
  }

  @Nullable
  protected VoicemailPlaybackPresenter getVoicemailPlaybackPresenter() {
    return null;
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    setupData();
    updateSelectAllState(savedInstanceState);
    mAdapter.onRestoreInstanceState(savedInstanceState);
  }

  private void updateSelectAllState(Bundle savedInstanceState) {
    if (savedInstanceState != null) {
      if (savedInstanceState.getBoolean(KEY_SELECT_ALL_MODE, false)) {
        updateSelectAllIcon();
      }
    }
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    updateEmptyMessage(mCallTypeFilter);
  }

  @Override
  public void onResume() {
    LogUtil.d("MSimCallLogFragment.onResume", toString());
    super.onResume();
    final boolean hasReadCallLogPermission =
        PermissionsUtil.hasPermission(getActivity(), READ_CALL_LOG);
    if (!mHasReadCallLogPermission && hasReadCallLogPermission) {
      // We didn't have the permission before, and now we do. Force a refresh of the call log.
      // Note that this code path always happens on a fresh start, but mRefreshDataRequired
      // is already true in that case anyway.
      mRefreshDataRequired = true;
      updateEmptyMessage(mCallTypeFilter);
    }

    mHasReadCallLogPermission = hasReadCallLogPermission;

    /*
     * Always clear the filtered numbers cache since users could have blocked/unblocked numbers
     * from the settings page
     */
    mAdapter.clearFilteredNumbersCache();
    refreshData();
    mAdapter.onResume();

    rescheduleDisplayUpdate();
  }

  @Override
  public void onPause() {
    LogUtil.d("MSimCallLogFragment.onPause", toString());
    cancelDisplayUpdate();
    mAdapter.onPause();
    super.onPause();
  }

  @Override
  public void onStop() {
    super.onStop();
    mAdapter.onStop();
    mContactInfoCache.stop();
  }

  @Override
  public void onDestroy() {
    LogUtil.d("MSimCallLogFragment.onDestroy", toString());
    mAdapter.changeCursor(null);

    getActivity().getContentResolver().unregisterContentObserver(mCallLogObserver);
    getActivity().getContentResolver().unregisterContentObserver(mContactsObserver);
    super.onDestroy();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(KEY_FILTER_TYPE, mCallTypeFilter);
    outState.putInt(KEY_LOG_LIMIT, mLogLimit);
    outState.putLong(KEY_DATE_LIMIT, mDateLimit);
    outState.putBoolean(KEY_IS_CALL_LOG_ACTIVITY, mIsCallLogActivity);
    outState.putBoolean(KEY_HAS_READ_CALL_LOG_PERMISSION, mHasReadCallLogPermission);
    outState.putBoolean(KEY_REFRESH_DATA_REQUIRED, mRefreshDataRequired);
    outState.putBoolean(KEY_SELECT_ALL_MODE, selectAllMode);
    mAdapter.onSaveInstanceState(outState);
  }

  @Override
  public void fetchCalls() {
    if (mFilterSlotSpinnerView.isEnabled()) {
      if (mCallSlotFilter != INVALID_SIM_SLOT_INDEX) {
        SubscriptionInfo subInfo = SubscriptionManager.from(getActivity())
            .getActiveSubscriptionInfoForSimSlotIndex(mCallSlotFilter);
        if (subInfo != null) {
          mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mDateLimit,
              subInfo.getIccId());
        }
      } else {
        mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mDateLimit);
      }
    } else {
      mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mDateLimit);
    }
    if (!mIsCallLogActivity) {
      ((ListsFragment) getParentFragment()).updateTabUnreadCounts();
    }
    updateEmptyMessage(mCallTypeFilter);
  }

  private void updateEmptyMessage(int filterType) {
    final Context context = getActivity();
    if (context == null) {
      return;
    }

    if (!PermissionsUtil.hasPermission(context, READ_CALL_LOG)) {
      mEmptyListView.setDescription(R.string.permission_no_calllog);
      mEmptyListView.setActionLabel(R.string.permission_single_turn_on);
      return;
    }

    final int messageId;
    switch (filterType) {
      case Calls.INCOMING_TYPE:
        messageId = R.string.recentIncoming_empty;
        break;
      case Calls.OUTGOING_TYPE:
        messageId = R.string.recentOutgoing_empty;
        break;
      case Calls.MISSED_TYPE:
        messageId = R.string.call_log_missed_empty;
        break;
      case Calls.VOICEMAIL_TYPE:
        messageId = R.string.call_log_voicemail_empty;
        break;
      case CallLogQueryHandler.CALL_TYPE_ALL:
        messageId = R.string.call_log_all_empty;
        break;
      default:
        throw new IllegalArgumentException(
            "Unexpected filter type in MSimCallLogFragment: " + filterType);
    }
    mEmptyListView.setDescription(messageId);
    if (mIsCallLogActivity) {
      mEmptyListView.setActionLabel(EmptyContentView.NO_LABEL);
    } else if (filterType == CallLogQueryHandler.CALL_TYPE_ALL) {
      mEmptyListView.setActionLabel(R.string.call_log_all_empty_action);
    }
  }

  public CallLogAdapter getAdapter() {
    return mAdapter;
  }

  @Override
  public void setMenuVisibility(boolean menuVisible) {
    super.setMenuVisibility(menuVisible);
    if (mMenuVisible != menuVisible) {
      mMenuVisible = menuVisible;
      if (menuVisible && isResumed()) {
        refreshData();
      }
    }
  }

  /** Requests updates to the data to be shown. */
  private void refreshData() {
    // Prevent unnecessary refresh.
    if (mRefreshDataRequired) {
      // Mark all entries in the contact info cache as out of date, so they will be looked up
      // again once being shown.
      mContactInfoCache.invalidate();
      mAdapter.setLoading(true);

      fetchCalls();
      mCallLogQueryHandler.fetchVoicemailStatus();
      mCallLogQueryHandler.fetchMissedCallsUnreadCount();
      mRefreshDataRequired = false;
    } else {
      // Refresh the display of the existing data to update the timestamp text descriptions.
      mAdapter.notifyDataSetChanged();
    }
  }

  @Override
  public void onEmptyViewActionButtonClicked() {
    final Activity activity = getActivity();
    if (activity == null) {
      return;
    }

    if (!PermissionsUtil.hasPermission(activity, READ_CALL_LOG)) {
      FragmentCompat.requestPermissions(
          this, new String[] {READ_CALL_LOG}, READ_CALL_LOG_PERMISSION_REQUEST_CODE);
    } else if (!mIsCallLogActivity) {
      // Show dialpad if we are not in the call log activity.
      ((HostInterface) activity).showDialpad();
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == READ_CALL_LOG_PERMISSION_REQUEST_CODE) {
      if (grantResults.length >= 1 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
        // Force a refresh of the data since we were missing the permission before this.
        mRefreshDataRequired = true;
      }
    }
  }

  /** Schedules an update to the relative call times (X mins ago). */
  private void rescheduleDisplayUpdate() {
    if (!mDisplayUpdateHandler.hasMessages(EVENT_UPDATE_DISPLAY)) {
      long time = System.currentTimeMillis();
      // This value allows us to change the display relatively close to when the time changes
      // from one minute to the next.
      long millisUtilNextMinute = MILLIS_IN_MINUTE - (time % MILLIS_IN_MINUTE);
      mDisplayUpdateHandler.sendEmptyMessageDelayed(EVENT_UPDATE_DISPLAY, millisUtilNextMinute);
    }
  }

  /** Cancels any pending update requests to update the relative call times (X mins ago). */
  private void cancelDisplayUpdate() {
    mDisplayUpdateHandler.removeMessages(EVENT_UPDATE_DISPLAY);
  }

  @CallSuper
  public void onVisible() {
    LogUtil.enterBlock("CallLogFragment.onPageSelected");
    if (getActivity() != null) {
      ((HostInterface) getActivity())
          .enableFloatingButton(mModalAlertManager == null || mModalAlertManager.isEmpty());
    }
  }

  @CallSuper
  public void onNotVisible() {
    LogUtil.enterBlock("CallLogFragment.onPageUnselected");
  }

  @Override
  public void onShowModalAlert(boolean show) {
    LogUtil.d(
        "MSimCallLogFragment.onShowModalAlert",
        "show: %b, fragment: %s, isVisible: %b",
        show,
        this,
        getUserVisibleHint());
    getAdapter().notifyDataSetChanged();
    HostInterface hostInterface = (HostInterface) getActivity();
    if (show) {
      mRecyclerView.setVisibility(View.GONE);
      mModalAlertView.setVisibility(View.VISIBLE);
      if (hostInterface != null && getUserVisibleHint()) {
        hostInterface.enableFloatingButton(false);
      }
    } else {
      mRecyclerView.setVisibility(View.VISIBLE);
      mModalAlertView.setVisibility(View.GONE);
      if (hostInterface != null && getUserVisibleHint()) {
        hostInterface.enableFloatingButton(true);
      }
    }
  }

  @Override
  public void showMultiSelectRemoveView(boolean show) {
    mMultiSelectUnSelectAllViewContent.setVisibility(show ? View.VISIBLE : View.GONE);
    mMultiSelectUnSelectAllViewContent.setAlpha(show ? 0 : 1);
    mMultiSelectUnSelectAllViewContent.animate().alpha(show ? 1 : 0).start();
    ((ListsFragment) getParentFragment()).showMultiSelectRemoveView(show);
  }

  @Override
  public void setSelectAllModeToFalse() {
    selectAllMode = false;
    mSelectUnselectAllIcon.setImageDrawable(
        getContext().getDrawable(R.drawable.ic_empty_check_mark_white_24dp));
  }

  @Override
  public void tapSelectAll() {
    LogUtil.i("CallLogFragment.tapSelectAll", "imitating select all");
    selectAllMode = true;
    updateSelectAllIcon();
  }

  @Override
  public void onClick(View v) {
    selectAllMode = !selectAllMode;
    if (selectAllMode) {
      Logger.get(v.getContext()).logImpression(DialerImpression.Type.MULTISELECT_SELECT_ALL);
    } else {
      Logger.get(v.getContext()).logImpression(DialerImpression.Type.MULTISELECT_UNSELECT_ALL);
    }
    updateSelectAllIcon();
  }

  private void updateSelectAllIcon() {
    if (selectAllMode) {
      mSelectUnselectAllIcon.setImageDrawable(
          getContext().getDrawable(R.drawable.ic_check_mark_blue_24dp));
      getAdapter().onAllSelected();
    } else {
      mSelectUnselectAllIcon.setImageDrawable(
          getContext().getDrawable(R.drawable.ic_empty_check_mark_white_24dp));
      getAdapter().onAllDeselected();
    }
  }

  public interface HostInterface {

    void showDialpad();

    void enableFloatingButton(boolean enabled);
  }

  protected class CustomContentObserver extends ContentObserver {

    public CustomContentObserver() {
      super(mHandler);
    }

    @Override
    public void onChange(boolean selfChange) {
      mRefreshDataRequired = true;
    }
  }

  private OnItemSelectedListener mSlotSelectedListener = new OnItemSelectedListener() {
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
      LogUtil.d("Slot selected, position: " + position, toString());
      int slot = position - 1;
      if (slot != mCallSlotFilter) {
        mCallSlotFilter = slot;
        setSelectedSlotId(slot);
        fetchCalls();
      }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
      // Do nothing.
    }
  };

  private OnItemSelectedListener mStatusSelectedListener = new OnItemSelectedListener() {
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
      LogUtil.d("Status selected, position: " + position, toString());
      int type = ((SpinnerContent)parent.getItemAtPosition(position)).value;
      if (type != mCallTypeFilter) {
        mCallTypeFilter = type;
        fetchCalls();
      }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
      // Do nothing.
    }
  };

  /**
   * Initialize the filter views content.
   */
  private void updateFilterSpinnerViews() {
    if (mFilterSlotSpinnerView == null
        || mFilterStatusSpinnerView == null) {
      LogUtil.d("The filter spinner view is null!", toString());
      return;
    }

    // Update the sub filter's content.
    final SubscriptionManager subscriptionManager = SubscriptionManager.from(getActivity());
    if (subscriptionManager.getActiveSubscriptionInfoCount() < 2) {
      mFilterSlotSpinnerView.setVisibility(View.GONE);
    }else{
      ArrayAdapter<SpinnerContent> filterSlotAdapter = new ArrayAdapter<SpinnerContent>(
          getActivity(), R.layout.msim_call_log_spinner_item,
          SpinnerContent.setupSlotFilterContent(getActivity()));
      if (filterSlotAdapter.getCount() <= 1) {
        mFilterSlotSpinnerView.setVisibility(View.GONE);
      } else{
        mCallSlotFilter = getSelectedSlotId();
        mFilterSlotSpinnerView.setAdapter(filterSlotAdapter);
        mFilterSlotSpinnerView.setOnItemSelectedListener(mSlotSelectedListener);
        SpinnerContent.setSpinnerContentValue(mFilterSlotSpinnerView, mCallSlotFilter);
      }
    }
    // Update the status filter's content.
    ArrayAdapter<SpinnerContent> filterStatusAdapter = new ArrayAdapter<SpinnerContent>(
        getActivity(), R.layout.msim_call_log_spinner_item,
        SpinnerContent.setupStatusFilterContent(getActivity(), false));
    mFilterStatusSpinnerView.setAdapter(filterStatusAdapter);
    mFilterStatusSpinnerView.setOnItemSelectedListener(mStatusSelectedListener);
    SpinnerContent.setSpinnerContentValue(mFilterStatusSpinnerView, mCallTypeFilter);
  }

  /**
   * @return the saved selected subscription.
   */
  private int getSelectedSlotId() {
    // Get the saved selected sub, and the default value is display all.
    return PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt(
        PREFERENCE_KEY_CALLLOG_SLOT, INVALID_SIM_SLOT_INDEX);
  }

  /**
   * Save the selected subscription to preference.
   */
  private void setSelectedSlotId(int slotId) {
    // Save the selected sub to the default preference.
    PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
        .putInt(PREFERENCE_KEY_CALLLOG_SLOT, slotId).commit();
  }

  public void setFilterType(int callType) {
    mCallTypeFilter = callType;
  }

}
