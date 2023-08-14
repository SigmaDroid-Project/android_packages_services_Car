/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.android.car.kitchensink;

import static com.google.android.car.kitchensink.KitchenSinkActivity.MENU_ENTRIES;

import android.annotation.Nullable;
import android.car.drivingstate.CarUxRestrictions;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.car.ui.core.CarUi;
import com.android.car.ui.recyclerview.CarUiContentListItem;
import com.android.car.ui.recyclerview.CarUiRecyclerView;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.NavButtonMode;
import com.android.car.ui.toolbar.SearchMode;
import com.android.car.ui.toolbar.ToolbarController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// TODO: b/293660419 - Add CLI support i.e. onNewIntent and dump()
public class KitchenSink2Activity extends FragmentActivity {
    static final String TAG = KitchenSink2Activity.class.getName();
    private static final String PREFERENCES_NAME = "fragment_item_prefs";
    private static final String KEY_PINNED_ITEMS_LIST = "key_pinned_items_list";
    private static final String DELIMITER = "::"; // A unique delimiter
    @Nullable
    private Fragment mLastFragment;
    private static final int NO_INDEX = -1;
    private static final String EMPTY_STRING = "";
    private HighlightableAdapter mAdapter;
    private final FragmentItemClickHandler mItemClickHandler = new FragmentItemClickHandler();

    /**
     * List of all the original menu items.
     */
    private List<FragmentListItem> mData;

    /**
     * Dynamic list of menu items that needs to be given to the adapter. Useful while searching.
     */
    private List<FragmentListItem> mFilteredData;
    private boolean mIsSinglePane = false;
    private ToolbarController mGlobalToolbar, mMiniToolbar;
    private View mMenuContainer;
    private CarUiRecyclerView mRV;
    private CharSequence mLastFragmentTitle;
    private MenuItem mFavButton;
    private boolean mIsSearching;
    private int mPinnedItemsCount;
    private SharedPreferences mSharedPreferences;

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }

    private void showDefaultFragment() {
        onFragmentItemClick(0);
    }

    /**
     * Searches in dynamic list (Not the whole list)
     *
     * @param title - Searches the list whose fragment title matches
     * @return index of the item
     */
    private int getFragmentIndexFromTitle(String title) {
        for (int i = 0; i < mFilteredData.size(); i++) {
            String targetText = mFilteredData.get(i).getTitle().getPreferredText().toString();
            if (targetText.equalsIgnoreCase(title)) {
                return i;
            }
        }
        return NO_INDEX;
    }

    public void onFragmentItemClick(int fragIndex) {
        if (fragIndex < 0 || fragIndex >= mFilteredData.size()) return;
        FragmentListItem fragmentListItem = mFilteredData.get(fragIndex);
        Fragment fragment = fragmentListItem.getFragment();
        if (mLastFragment != fragment) {
            Log.v(TAG, "onFragmentItemClick(): from " + mLastFragment + " to " + fragment);
        } else {
            Log.v(TAG, "onFragmentItemClick(): showing " + fragment + " again");
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        mLastFragment = fragment;
        mLastFragmentTitle = fragmentListItem.getTitle().getPreferredText();
        mMiniToolbar.setTitle(mLastFragmentTitle);
        mAdapter.requestHighlight(mLastFragmentTitle.toString(), fragIndex);
        mFavButton.setIcon(
                fragmentListItem.isFavourite()
                        ? getDrawable(R.drawable.ic_item_unpin)
                        : getDrawable(R.drawable.ic_item_pin));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedPreferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);

        setContentView(R.layout.activity_2pane);
        mMenuContainer = findViewById(R.id.top_level_menu_container);
        mRV = findViewById(R.id.list_pane);

        setUpToolbars();

        mData = getProcessedData();
        mFilteredData = new ArrayList<>(mData);
        mAdapter = new HighlightableAdapter(this, mFilteredData, mRV);
        mRV.setAdapter(mAdapter);

        // showing Default
        showDefaultFragment();
    }

    private void setUpToolbars() {
        View toolBarView = requireViewById(R.id.top_level_menu_container);

        mGlobalToolbar = CarUi.installBaseLayoutAround(
                toolBarView,
                insets -> findViewById(R.id.top_level_menu_container).setPadding(
                        insets.getLeft(), insets.getTop(), insets.getRight(),
                        insets.getBottom()), /* hasToolbar= */ true);

        MenuItem searchButton = new MenuItem.Builder(this)
                .setToSearch()
                .setOnClickListener(menuItem -> onSearchButtonClicked())
                .setUxRestrictions(CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD)
                .setId(R.id.toolbar_menu_item_search)
                .build();

        mGlobalToolbar.setMenuItems(List.of(searchButton));
        mGlobalToolbar.registerBackListener(() -> {
            mIsSearching = false;
            mGlobalToolbar.setSearchMode(SearchMode.DISABLED);
            mGlobalToolbar.setNavButtonMode(NavButtonMode.DISABLED);
            mGlobalToolbar.unregisterOnSearchListener(this::onQueryChanged);
            EditText mSearchText = findViewById(R.id.car_ui_toolbar_search_bar);
            mSearchText.getText().clear();
            onQueryChanged(EMPTY_STRING);
            mAdapter.onSearchEnded();
            return true;
        });

        mGlobalToolbar.setTitle(getString(R.string.app_title));
        mGlobalToolbar.setNavButtonMode(NavButtonMode.DISABLED);
        mGlobalToolbar.setLogo(R.drawable.ic_launcher);
//        if (mIsSinglePane) {
//            mGlobalToolbar.setNavButtonMode(NavButtonMode.BACK);
//            findViewById(R.id.top_level_menu_container).setVisibility(View.GONE);
//            findViewById(R.id.top_level_divider).setVisibility(View.GONE);
//            return;
//        }
        mMiniToolbar = CarUi.installBaseLayoutAround(
                requireViewById(R.id.fragment_container_wrapper),
                insets -> findViewById(R.id.fragment_container_wrapper).setPadding(
                        insets.getLeft(), insets.getTop(), insets.getRight(),
                        insets.getBottom()), /* hasToolbar= */ true);

        mFavButton = new MenuItem.Builder(this)
                .setOnClickListener(i -> onFavClicked())
                .setUxRestrictions(CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD)
                .build();
        mMiniToolbar.setMenuItems(List.of(mFavButton));
        mMiniToolbar.setNavButtonMode(NavButtonMode.BACK);
    }

    private void onSearchButtonClicked() {
        mIsSearching = true;
        mGlobalToolbar.setSearchMode(SearchMode.SEARCH);
        mGlobalToolbar.setNavButtonMode(NavButtonMode.BACK);
        mGlobalToolbar.registerOnSearchListener(this::onQueryChanged);
    }

    private void onQueryChanged(String query) {
        mFilteredData.clear();
        if (query.isEmpty()) {
            mFilteredData.addAll(mData);
        } else {
            for (FragmentListItem item : mData) {
                if (item.getTitle().getPreferredText().toString().toLowerCase().contains(
                        query.toLowerCase())) {
                    mFilteredData.add(item);
                }
            }
        }
        mAdapter.afterTextChanged();
    }

    private void onFavClicked() {
        int fromIndex = getOriginalIndexFromTitle(mLastFragmentTitle, 0, false);
        int toIndex;
        String text;

        FragmentListItem fragmentListItem = mData.get(fromIndex);
        if (fragmentListItem.isFavourite()) {
            // Un-pinning: Moving the item to its lexicographic position
            toIndex = getOriginalIndexFromTitle(
                    fragmentListItem.getTitle().getPreferredText(), mPinnedItemsCount,
                    true);
            text = getString(R.string.toast_item_unpinned_message, mLastFragmentTitle);
            mPinnedItemsCount--;
        } else {
            // Pinning: Moving the item to the top most position.
            toIndex = 0;
            text = getString(R.string.toast_item_pinned_message, mLastFragmentTitle);
            mPinnedItemsCount++;
        }

        moveFragmentItem(fromIndex, toIndex);
        fragmentListItem.toggleFavourite();

        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        mFavButton.setIcon(
                fragmentListItem.isFavourite()
                        ? getDrawable(R.drawable.ic_item_unpin)
                        : getDrawable(R.drawable.ic_item_pin));

    }

    /**
     * Finds index from the original data (Not from the dynamic list)
     *
     * @param fragmentTitle   - Finds by comparing the title
     * @param startFrom       - the starting index it should search from
     * @param isLexicographic - If set to true, finds lexicographical position by comparing
     *                        strings. If false, finds the
     *                        index with exact string match or -1.
     */
    private int getOriginalIndexFromTitle(CharSequence fragmentTitle, int startFrom,
            boolean isLexicographic) {
        if (fragmentTitle.toString().equalsIgnoreCase(EMPTY_STRING)) return NO_INDEX;
        for (int i = startFrom; i < mData.size(); i++) {
            String targetText = mData.get(i).getTitle().getPreferredText().toString();
            if (isLexicographic && targetText.compareToIgnoreCase(fragmentTitle.toString()) > 0) {
                return i - 1;
            }
            if (!isLexicographic && targetText.equalsIgnoreCase(fragmentTitle.toString())) {
                return i;
            }
        }
        return isLexicographic ? mData.size() - 1 : NO_INDEX;
    }

    /**
     * Moves the fragmentItem from @param "from" to @param "to"
     * Used for both pinning and unpinning an item.
     *
     * @param from - the current index of the item
     * @param to   - the target index to move the item
     */
    private void moveFragmentItem(int from, int to) {
        if (from < 0 || from >= mData.size() || to < 0 || to >= mData.size()) return;
        mData.add(to, mData.remove(from));
        if (!mIsSearching) {
            mFilteredData.add(to, mFilteredData.remove(from));
            mAdapter.afterFavClicked(from, to);
        }
    }

    List<FragmentListItem> getProcessedData() {

        List<String> pinnedTitles = getPinnedTitlesFromPrefs();

        List<FragmentListItem> allItems = new ArrayList<>();
        ArrayList<FragmentListItem> pinnedItems = new ArrayList<>();
        mPinnedItemsCount = pinnedTitles.size();
        for (int i = 0; i < mPinnedItemsCount; i++) {
            pinnedItems.add(null);
        }

        for (Pair<String, Class> entry : MENU_ENTRIES) {
            // Retrieves the pinned position and preserves it in the same order
            int pinnedPosition = pinnedTitles.indexOf(entry.first.toLowerCase());
            if (pinnedPosition >= 0) {
                pinnedItems.set(pinnedPosition,
                        new FragmentListItem(entry.first, true, entry.second, mItemClickHandler));
            } else {
                allItems.add(
                        new FragmentListItem(entry.first, false, entry.second, mItemClickHandler));
            }
        }

        allItems.sort((o1, o2) -> {
            String s1 = o1.getTitle().getPreferredText().toString();
            String s2 = o2.getTitle().getPreferredText().toString();
            return s1.compareToIgnoreCase(s2);
        });

        allItems.addAll(0, pinnedItems);
        return allItems;
    }

    @NonNull
    List<String> getPinnedTitlesFromPrefs() {
        if (mSharedPreferences == null) return new ArrayList<>();

        String pinnedTitles = mSharedPreferences.getString(KEY_PINNED_ITEMS_LIST, "");
        if (pinnedTitles.isEmpty()) {
            return new ArrayList<>();
        } else {
            return Arrays.asList(pinnedTitles.split(DELIMITER));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePinnedItemsToPreferences();
    }

    private void savePinnedItemsToPreferences() {
        if (mSharedPreferences == null) {
            mSharedPreferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        }
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mPinnedItemsCount; i++) {
            sb.append(mData.get(i).getTitle().getPreferredText()).append(DELIMITER);
        }

        // Remove the last delimiter
        if (sb.length() > 0) {
            sb.setLength(sb.length() - DELIMITER.length());
        }

        editor.putString(KEY_PINNED_ITEMS_LIST, sb.toString().toLowerCase());
        editor.apply();
    }

    private class FragmentItemClickHandler implements CarUiContentListItem.OnClickListener {
        @Override
        public void onClick(@NonNull CarUiContentListItem carUiContentListItem) {
            int fragmentItemIndex = getFragmentIndexFromTitle(
                    carUiContentListItem.getTitle().getPreferredText().toString());
            onFragmentItemClick(fragmentItemIndex);
        }
    }

}
