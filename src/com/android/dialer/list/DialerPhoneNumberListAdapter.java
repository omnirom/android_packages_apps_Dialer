package com.android.dialer.list;

import android.content.Context;
import android.content.res.Resources;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.provider.Settings;
import android.os.Handler;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.dialer.omni.Place;
import com.android.dialer.omni.IRemoteApi;
import com.android.dialer.omni.IPlacesAroundApi;
import com.android.dialer.omni.clients.OsmApi;
import com.android.dialer.R;

/**
 * {@link PhoneNumberListAdapter} with the following added shortcuts, that are displayed as list
 * items:
 * 1) Directly calling the phone number query
 * 2) Adding the phone number query to a contact
 *
 * These shortcuts can be enabled or disabled to toggle whether or not they show up in the
 * list.
 */
public class DialerPhoneNumberListAdapter extends PhoneNumberListAdapter {
    private final static String TAG = "DialerPhoneNumberListAdapter";

    private String mFormattedQueryString;
    private String mCountryIso;

    private IPlacesAroundApi mPlacesAroundApi;
    private List<Place> mPlacesList;
    private String mPreviousQuery;
    private Handler mHandler;
    private Thread mQueryThread;
    private boolean mEnableSuggestions;
    private final Object mQueryLock = new Object();

    private Runnable mQueryKickerRunnable = new Runnable() {
        @Override
        public void run() {
            mQueryThread.start();
        }
    };

    public final static int SHORTCUT_INVALID = -1;
    public final static int SHORTCUT_DIRECT_CALL = 0;
    public final static int SHORTCUT_ADD_NUMBER_TO_CONTACTS = 1;

    public final static int SHORTCUT_COUNT = 2;

    private final boolean[] mShortcutEnabled = new boolean[SHORTCUT_COUNT];

    public DialerPhoneNumberListAdapter(Context context) {
        super(context);

        mCountryIso = GeoUtil.getCurrentCountryIso(context);

        // Enable all shortcuts by default
        for (int i = 0; i < mShortcutEnabled.length; i++) {
            mShortcutEnabled[i] = true;
        }

        mHandler = new Handler();

        // TODO: UI to select client
        mPlacesAroundApi = new OsmApi();
        mEnableSuggestions = Settings.System.getInt(context.getContentResolver(),
            Settings.System.ENABLE_DIALER_SUGGESTIONS, 0) == 1;
    }

    @Override
    public int getCount() {
        return super.getCount() + getSuggestionsCount() + getShortcutCount();
    }

    /**
     * @return The number of enabled shortcuts. Ranges from 0 to a maximum of SHORTCUT_COUNT
     */
    public int getShortcutCount() {
        int count = 0;
        for (int i = 0; i < mShortcutEnabled.length; i++) {
            if (mShortcutEnabled[i]) count++;
        }
        return count;
    }

    public int getSuggestionsCount() {
        if (mPlacesList == null) {
            return 0;
        } else {
            return mPlacesList.size();
        }
    }

    @Override
    public int getItemViewType(int position) {
        final int shortcut = getShortcutTypeFromPosition(position);
        if (shortcut >= 0) {
            // shortcutPos should always range from 1 to SHORTCUT_COUNT
            return super.getViewTypeCount() + shortcut;
        } else if (position >= super.getCount()) {
            return super.getViewTypeCount() + SHORTCUT_COUNT;
        } else {
            return super.getItemViewType(position);
        }
    }

    @Override
    public int getViewTypeCount() {
        // Number of item view types in the super implementation + 2 for the 2 new shortcuts
        // + 1 for suggestions
        return super.getViewTypeCount() + SHORTCUT_COUNT + 1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final int suggestionIndex = getSuggestionIndexFromPosition(position);
        final int shortcutType = getShortcutTypeFromPosition(position);

        synchronized (mQueryLock) {
            if (shortcutType >= 0) {
                if (convertView != null) {
                    assignShortcutToView((ContactListItemView) convertView, shortcutType);
                    return convertView;
                } else {
                    final ContactListItemView v = new ContactListItemView(getContext(), null);
                    assignShortcutToView(v, shortcutType);
                    return v;
                }
            } else if (suggestionIndex >= 0 && mPlacesList != null && mPlacesList.size() > 0
                       && suggestionIndex < mPlacesList.size()) {
                if (convertView != null) {
                    assignSuggestionToView((ContactListItemView) convertView, suggestionIndex);
                    return convertView;
                } else {
                    final ContactListItemView v = new ContactListItemView(getContext(), null);
                    assignSuggestionToView(v, suggestionIndex);
                    return v;
                }
            } else {
                return super.getView(position, convertView, parent);
            }
        }
    }

    public int getSuggestionIndexFromPosition(int position) {
        return position - super.getCount();
    }

    public String getSuggestionPhoneNumber(int position) {
        final int index = getSuggestionIndexFromPosition(position);
        final String phoneNum = mPlacesList.get(index).getPhoneNumber();
        return PhoneNumberUtils.convertAndStrip(phoneNum);
    }

    /**
     * @param position The position of the item
     * @return The enabled shortcut type matching the given position if the item is a
     * shortcut, -1 otherwise
     */
    public int getShortcutTypeFromPosition(int position) {
        int shortcutCount = position - (super.getCount() + getSuggestionsCount());
        if (shortcutCount >= 0) {
            // Iterate through the array of shortcuts, looking only for shortcuts where
            // mShortcutEnabled[i] is true
            for (int i = 0; shortcutCount >= 0 && i < mShortcutEnabled.length; i++) {
                if (mShortcutEnabled[i]) {
                    shortcutCount--;
                    if (shortcutCount < 0) return i;
                }
            }
            throw new IllegalArgumentException("Invalid position - greater than cursor count "
                    + " but not a shortcut.");
        }
        return SHORTCUT_INVALID;
    }

    @Override
    public boolean isEmpty() {
        return getShortcutCount() == 0 && getSuggestionsCount() == 0 && super.isEmpty();
    }

    @Override
    public boolean isEnabled(int position) {
        final int shortcutType = getShortcutTypeFromPosition(position);
        final int suggestionIndex = getSuggestionIndexFromPosition(position);
        if (shortcutType >= 0) {
            return true;
        } else if (suggestionIndex > 0) {
            return true;
        } else {
            return super.isEnabled(position);
        }
    }

    private void assignShortcutToView(ContactListItemView v, int shortcutType) {
        final CharSequence text;
        final int drawableId;
        final Resources resources = getContext().getResources();
        final String number = getFormattedQueryString();
        switch (shortcutType) {
            case SHORTCUT_DIRECT_CALL:
                text = resources.getString(R.string.search_shortcut_call_number, number);
                drawableId = R.drawable.ic_phone_dk;
                break;
            case SHORTCUT_ADD_NUMBER_TO_CONTACTS:
                text = resources.getString(R.string.search_shortcut_add_to_contacts);
                drawableId = R.drawable.ic_add_person_dk;
                break;
            default:
                throw new IllegalArgumentException("Invalid shortcut type");
        }
        v.setDrawableResource(R.drawable.list_item_avatar_bg, drawableId);
        v.setDisplayName(text);
        v.setPhotoPosition(super.getPhotoPosition());
    }

    private void assignSuggestionToView(ContactListItemView v, int suggestionIndex) {
        final Resources resources = getContext().getResources();
        final Place place = mPlacesList.get(suggestionIndex);
        if (mPreviousQuery != null) {
            v.setHighlightedPrefix(mPreviousQuery.toUpperCase());
        }
        if (suggestionIndex == 0) {
            v.setSectionHeader(resources.getString(R.string.nearby_places));
        } else {
            v.setSectionHeader(null);
        }
        v.setDrawableResource(R.drawable.list_item_avatar_bg, R.drawable.ic_phone_dk);
        v.setDisplayName(place.getName());
        v.setPhoneNumber(PhoneNumberUtils.formatNumber(PhoneNumberUtils.convertAndStrip(place.getPhoneNumber()), mCountryIso));
        v.setPhotoPosition(super.getPhotoPosition());
    }

    public void setShortcutEnabled(int shortcutType, boolean visible) {
        mShortcutEnabled[shortcutType] = visible;
    }

    public String getFormattedQueryString() {
        return mFormattedQueryString;
    }

    @Override
    public void setQueryString(String queryString) {
        mFormattedQueryString = PhoneNumberUtils.formatNumber(
                PhoneNumberUtils.convertAndStrip(queryString), mCountryIso);


        if (mEnableSuggestions) {
            // Query api for nearby places with that name
            queryPlaces(queryString);
        }

        super.setQueryString(queryString);
    }

    public void queryPlaces(final String query) {
        if (mPreviousQuery != null && query.equals(mPreviousQuery)) return;
        mPreviousQuery = query;

        if (mQueryThread != null) {
            mQueryThread.interrupt();
        }

        mQueryThread = new Thread() {
            @Override
            public void run() {
                final LocationManager locationManager = (LocationManager)
                    mContext.getSystemService(Context.LOCATION_SERVICE);
                List<String> providers = locationManager.getProviders(true);
                Location l = null;

                for (int i = providers.size()-1; i>=0; i--) {
                    l = locationManager.getLastKnownLocation(providers.get(i));
                    if (l != null) break;
                }

                double lat, lon;

                try {
                    lat = l.getLatitude();
                    lon = l.getLongitude();
                } catch (NullPointerException e) {
                    Log.w(TAG, "Nearby search canceled as location data is unavailable.");
                    return;
                }
                List<Place> places = mPlacesAroundApi.getNamedPlacesAround(query, lat, lon, 0.4);

                if (isInterrupted()) {
                    Log.i(TAG, "Cancelling current nearby search, superseeded by a newer one");
                    return;
                }

                synchronized (mQueryLock) {
                    mPlacesList = places;
                }

                mHandler.post(new Runnable() {
                    public void run() {
                        notifyDataSetChanged();
                    }
                });
            }
        };

        // To not post every keystroke to the API, we send it only
        // after some time if the query string didn't change (otherwise
        // the previous request is cancelled).
        mHandler.removeCallbacks(mQueryKickerRunnable);
        mHandler.postDelayed(mQueryKickerRunnable, 800);
    }

}
