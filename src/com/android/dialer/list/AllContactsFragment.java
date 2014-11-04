/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.dialer.list;

import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.QuickContact;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.util.ViewUtil;
import com.android.dialer.R;
import com.android.dialer.util.DialerUtils;

/**
 * Fragments to show all contacts with phone numbers.
 */
public class AllContactsFragment extends ContactEntryListFragment<ContactEntryListAdapter> {

    public AllContactsFragment() {
        setQuickContactEnabled(false);
        setAdjustSelectionBoundsEnabled(true);
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setDarkTheme(false);
        setVisibleScrollbarEnabled(true);
    }

    @Override
    public void onViewCreated(View view, android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View emptyListView = view.findViewById(R.id.empty_list_view);
        DialerUtils.configureEmptyListView(emptyListView, R.drawable.empty_contacts,
                R.string.all_contacts_empty, getResources());
        getListView().setEmptyView(emptyListView);

        ViewUtil.addBottomPaddingToListViewForFab(getListView(), getResources());
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        final DefaultContactListAdapter adapter = new DefaultContactListAdapter(getActivity()) {
            @Override
            protected void bindView(View itemView, int partition, Cursor cursor, int position) {
                super.bindView(itemView, partition, cursor, position);
                itemView.setTag(this.getContactUri(partition, cursor));
            }
        };
        adapter.setDisplayPhotos(true);
        adapter.setFilter(ContactListFilter.createFilterWithType(
                ContactListFilter.FILTER_TYPE_DEFAULT));
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        return adapter;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.show_all_contacts_fragment, null);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final Uri uri = (Uri) view.getTag();
        if (uri != null) {
            QuickContact.showQuickContact(getActivity(), view, uri, QuickContact.MODE_LARGE, null);
        }
    }

    @Override
    protected void onItemClick(int position, long id) {
        // Do nothing. Implemented to satisfy ContactEntryListFragment.
    }
}
