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

package com.google.android.car.kitchensink.radio;

import android.content.Context;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;

import com.google.android.car.kitchensink.R;

public final class ProgramInfoAdapter extends ArrayAdapter<RadioManager.ProgramInfo> {

    private RadioTunerFragment mFragment;
    private Context mContext;
    private int mLayoutResourceId;
    private RadioManager.ProgramInfo[] mProgramInfos;
    public ProgramInfoAdapter(Context context, int layoutResourceId,
                              RadioManager.ProgramInfo[] programInfos,
                              RadioTunerFragment fragment) {
        super(context, layoutResourceId, programInfos);
        mFragment = fragment;
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mProgramInfos = programInfos;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder vh = new ViewHolder();
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(mLayoutResourceId, parent, /* attachToRoot= */ false);
            vh.programSelectorText = convertView.findViewById(R.id.text_selector);
            vh.tuneButton = convertView.findViewById(R.id.button_tune);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }
        if (mProgramInfos[position] != null) {
            if (position == 0) {
                vh.programSelectorText.setText(R.string.radio_program_station_title);
                vh.tuneButton.setVisibility(View.INVISIBLE);
            } else {
                int programType = mProgramInfos[position].getSelector().getPrimaryId().getType();
                String programSelectorText = "";
                if (programType == ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY) {
                    programSelectorText = ProgramSelectorExt.getDisplayName(
                            mProgramInfos[position].getSelector(), /* flags= */ 0);
                }
                vh.programSelectorText.setText(programSelectorText);
                vh.tuneButton.setVisibility(View.VISIBLE);

                vh.tuneButton.setOnClickListener((view) -> {
                    mFragment.handleTune(mProgramInfos[position].getSelector());
                });
            }
        }
        return convertView;
    }

    @Override
    public int getCount() {
        return mProgramInfos.length;
    }

    void updateProgramInfos(RadioManager.ProgramInfo[] programInfos) {
        mProgramInfos = programInfos;
        notifyDataSetChanged();
    }

    private static final class ViewHolder {
        public TextView programSelectorText;
        public Button tuneButton;
    }
}
