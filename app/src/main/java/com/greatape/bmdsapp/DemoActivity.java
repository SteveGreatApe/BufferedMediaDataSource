/* Copyright 2017 Great Ape Software Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.greatape.bmdsapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.greatape.bmdsapp.smb.NetworkDirTask;
import com.greatape.bmdsapp.smb.SmbFileList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jcifs.smb.SmbFile;

/**
 * @author Steve Townsend
 */
public class DemoActivity extends Activity implements SmbFileList.UI, VideoViewer.UI {
    private static final String TAG = "DemoActivity";

    private static final String RANDOM_ACCESS_PREF = "ra";
    private static final String REPEAT_PREF = "rp";
    private static final String LAST_FILE_PREF = "lf";

    private static final String MIME_VIDEO = "video/";
    private static final String UserNamePasswordSeparator = ":";

    private SharedPreferences mPreferences;
    private HashMap<String, String> mCredentials;
    private SmbFileList mSmbFileList;
    private VideoViewer mVideoViewer;

    private NetworkDirTask.FileListEntry mCurrentlySelected;
    private List<NetworkDirTask.FileListEntry> mFileListEntries;

    private Exception mException;
    private String mTitle;
    private String mLoadStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        mCredentials = new HashMap<>();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mVideoViewer = new VideoViewer(this);
        mVideoViewer.initSurface(findViewById(R.id.video_view_layout));
        mVideoViewer.setRepeatMode(mPreferences.getBoolean(REPEAT_PREF, false));

        mSmbFileList = new SmbFileList(this, this);
        mSmbFileList.setFilteredMimeType(MIME_VIDEO);
        mSmbFileList.requestStartFileList();
        initUI();
    }

    private void initUI() {
        View stopButton = findViewById(R.id.stop_video);
        if (stopButton != null) {
            stopButton.setEnabled(mVideoViewer.isPlaying());

            TextView titleTextView = findViewById(R.id.network_title);
            titleTextView.setText(mTitle);

            CheckBox randomAccess = findViewById(R.id.random_access);
            randomAccess.setChecked(mPreferences.getBoolean(RANDOM_ACCESS_PREF, false));
            randomAccess.setOnCheckedChangeListener((buttonView, isChecked) -> mPreferences.edit().
                putBoolean(RANDOM_ACCESS_PREF, isChecked).
                apply());

            CheckBox repeat = findViewById(R.id.repeat);
            repeat.setChecked(mPreferences.getBoolean(REPEAT_PREF, false));
            repeat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mVideoViewer.setRepeatMode(isChecked);
                    mPreferences.edit().
                            putBoolean(REPEAT_PREF, isChecked).
                            apply();
                }
            });

            doUpdateFileList();
        }
    }

    @Override
    public void onPause() {
        mVideoViewer.onPause();
        super.onPause();
    }

    @Override
    public void onResume() {
        mVideoViewer.onResume();
        super.onResume();
    }

    @Override
    public void updateFileList(List<NetworkDirTask.FileListEntry> fileListEntries, Exception exception) {
        mFileListEntries = fileListEntries;
        mException = exception;
        doUpdateFileList();
    }

    private void doUpdateFileList() {
        Spinner spinner = findViewById(R.id.file_list);
        if (spinner == null) {
            return;
        }
        boolean hasContent = mFileListEntries != null && mFileListEntries.size() > 0;
        if (hasContent) {
            List<String> fileNames = new ArrayList<>();
            for(NetworkDirTask.FileListEntry listEntry : mFileListEntries){
                fileNames.add(listEntry.file.getName());
            }
            ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fileNames);
            dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(dataAdapter);
            int selectionIndex = -1;
            String lastFile = mPreferences.getString(LAST_FILE_PREF, null);
            if (lastFile != null) {
                selectionIndex = fileNames.indexOf(lastFile);
            }
            if (selectionIndex < 0) {
                selectionIndex = fileNames.size() > 1 ? 1 : 0;
            }
            spinner.setSelection(selectionIndex);
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    mCurrentlySelected = mFileListEntries.get(position);
                    if (!mCurrentlySelected.isDirectory) {
                        mPreferences.edit().
                                putString(LAST_FILE_PREF, fileNames.get(position)).
                                apply();

                        setPlayButtonLabel(R.string.play_video);
                    } else {
                        setPlayButtonLabel(R.string.open_folder);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    mPreferences.edit().
                            putString(LAST_FILE_PREF, null).
                            apply();
                }
            });
            setLoadStatus(null);
        } else {
            if (mException != null) {
                setLoadStatus(mException.getLocalizedMessage());
            } else {
                setLoadStatus(R.string.default_no_file_error);
            }
        }
        spinner.setVisibility(hasContent ? View.VISIBLE : View.INVISIBLE);
        setViewEnabled(R.id.play_video, hasContent);
        setViewEnabled(R.id.up_level, mSmbFileList.getParentFolder() != null);
    }

    private void setViewEnabled(int viewId, boolean enabled) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setEnabled(enabled);
        }
    }

    private void setPlayButtonLabel(int labelId) {
        ((Button)findViewById(R.id.play_video)).setText(labelId);
    }

    @Override
    public void loading(String path) {
        setLoadStatus(R.string.fetching_list);

        TextView titleTextView = findViewById(R.id.network_title);
        if (titleTextView != null) {
            mTitle = getResources().getString(R.string.title_format, path);
            titleTextView.setText(mTitle);
        }
    }

    private void setLoadStatus(int resId) {
        setLoadStatus(getResources().getString(resId));
    }

    private void setLoadStatus(String loadStatus) {
        mLoadStatus = loadStatus;
        resetListUiState();
    }

    private void resetListUiState() {
        TextView videoListState = findViewById(R.id.video_list_state);
        if (videoListState != null) {
            videoListState.setVisibility(mLoadStatus == null ? View.INVISIBLE : View.VISIBLE);
            videoListState.setText(mLoadStatus);
        }
    }

    public void onLevelUp(View view) {
        SmbFile parentFolder = mSmbFileList.getParentFolder();
        if (parentFolder != null) {
            mSmbFileList.requestFileList(parentFolder, null, null);
        }
    }

    public void onPlayVideo(View view) {
        mVideoViewer.stop();
        if (mCurrentlySelected != null) {
            if (mCurrentlySelected.isDirectory) {
                mSmbFileList.requestFileList(mCurrentlySelected.file, null, null);
            } else {
                mVideoViewer.playNetworkVideo(mCurrentlySelected.file, mPreferences.getBoolean(RANDOM_ACCESS_PREF, false));
            }
        }
    }

    public void stopVideo(View view) {
        mVideoViewer.onStopVideo(false);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        setContentView(R.layout.activity_demo);
        mVideoViewer.initSurface(findViewById(R.id.video_view_layout));
        initUI();
        resetListUiState();
    }

    private void HeapDump() {
        Log.d(TAG, "Dumping heap");
        try {
            File external = Environment.getExternalStorageDirectory();
            File folder = new File(external, "Documents");
            File heapDumpFile1 = new File(folder, "oom.hprof");
            Debug.dumpHprofData(heapDumpFile1.getPath());
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        Log.d(TAG, "Finished heap dump");
    }

    @Override
    public void stateChanged(boolean playing) {
        setViewEnabled(R.id.stop_video, playing);
    }

    @Override
    public void getUserNamePassword(final SmbFile smbFile) {
        setViewEnabled(R.id.up_level, mSmbFileList.getParentFolder() != null);
        setLoadStatus(R.string.requesting_credentials);

        LayoutInflater li = getLayoutInflater();
        View prompt = li.inflate(R.layout.credentials_dialog, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setView(prompt);
        final EditText user = prompt.findViewById(R.id.username);
        final EditText pass = prompt.findViewById(R.id.password);
        String userNamePassword = mCredentials.get(smbFile.getPath());
        if (userNamePassword != null) {
            String[] pair = userNamePassword.split(UserNamePasswordSeparator);
            if (pair.length == 2) {
                user.setText(pair[0]);
                pass.setText(pair[1]);
            }
        }
        alertDialogBuilder.setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        String userName = user.getText().toString();
                        String password = pass.getText().toString();
                        mCredentials.put(smbFile.getPath(), userName + UserNamePasswordSeparator + password);
                        mSmbFileList.requestFileList(smbFile, userName, password);
                    }
                });

        alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                setLoadStatus(R.string.cancelled_credentials_request);
                dialog.cancel();
            }
        });
        alertDialogBuilder.show();
    }
}
