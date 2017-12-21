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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.greatape.bmds.BmdsLog;
import com.greatape.bmds.BufferedMediaDataSource;

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import jcifs.smb.SmbFile;
import jcifs.util.LogStream;

/**
 * @author Steve Townsend
 */
public class DemoActivity extends Activity implements NetworkDirTask.Listener, SurfaceHolder.Callback {
    private static final String TAG = "DemoActivity";
    private static final String MIME_VIDEO = "video/";

    // TODO: Configure these to point to a Workgroup and Folder containing videos on your network.
    private static final String WORKGROUP = "WORKGROUP";
    private static final String NETWORK_PATH = "smb://EUROAPE/Users/Public/Videos/";

    private static final String USERNAME_PREF = "u";
    private static final String PASSWORD_PREF = "p";
    private static final String RANDOM_ACCESS_PREF = "ra";
    private static final String REPEAT_PREF = "rp";
    private static final String STORE_LOGIN_PREF = "ra";
    private static final String LAST_FILE_PREF = "lf";

    private SurfaceHolder mSurfaceHolder;
    private MediaPlayer mMediaPlayer;
    private ArrayList<SmbFile> mNetworkVideos;
    private List<String> mFileNames;
    private IOException mFileListException;
    private SurfaceView mSurfaceView;
    private boolean mRestartOnResume;
    private int mVideoViewWidth;
    private int mVideoViewHeight;
    private SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
//        BmdsLog.enableDebug(true);
//        LogStream.setLevel(5);
//        jcifs.Config.setProperty("jcifs.smb.client.dfs.disabled", "true");
        initSurface();
        initUI();
    }

    private void initUI() {
        TextView titleTextView = findViewById(R.id.network_title);
        if (titleTextView != null) {
            String title = getResources().getString(R.string.title_format, NETWORK_PATH);
            titleTextView.setText(title);

            EditText userNameEdit = findViewById(R.id.username);
            String userName = mPreferences.getString(USERNAME_PREF, null);
            userNameEdit.setText(userName);
            EditText passwordEdit = findViewById(R.id.password);
            passwordEdit.setText(mPreferences.getString(PASSWORD_PREF, null));
            if (userName != null) {
                getVideoList();
            }

            findViewById(R.id.stop_video).setEnabled(mMediaPlayer != null);

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
                    mPreferences.edit().
                            putBoolean(REPEAT_PREF, isChecked).
                            apply();
                }
            });

            CheckBox storeLogin = findViewById(R.id.store_login);
            storeLogin.setChecked(mPreferences.getBoolean(STORE_LOGIN_PREF, false));
            storeLogin.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mPreferences.edit().
                            putBoolean(STORE_LOGIN_PREF, isChecked).
                            apply();
                }
            });
            updateFileList();
        }
    }

    private void initSurface() {
        mSurfaceView = findViewById(R.id.video_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.setSizeFromLayout();
        mSurfaceHolder.addCallback(this);
    }

    @Override
    public void onPause() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            mRestartOnResume = true;
        } else {
            mRestartOnResume = false;
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        if (mMediaPlayer != null && mRestartOnResume) {
            mMediaPlayer.start();
        }
        super.onResume();
    }

    @Override
    public void fileList(SmbFile[] files, IOException exception) {
        mFileNames = new ArrayList<>();
        mFileListException = exception;
        if (files != null) {
            CheckBox storeLogin = findViewById(R.id.store_login);
            if (storeLogin.isChecked()) {
                storeLogin();
            }
            mNetworkVideos = new ArrayList<>();
            for (SmbFile smbFile : files) {
                String mimeType = URLConnection.guessContentTypeFromName(smbFile.getName());
                if (mimeType != null && mimeType.startsWith(MIME_VIDEO)) {
                    mNetworkVideos.add(smbFile);
                    mFileNames.add(smbFile.getName());
                }
            }
        }
        updateFileList();
    }

    private void storeLogin() {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(USERNAME_PREF, getUsername());
        editor.putString(PASSWORD_PREF, getPassword());
        editor.apply();
    }

    private void updateFileList() {
        Spinner spinner = findViewById(R.id.file_list);
        if (spinner == null) {
            return;
        }
        TextView videoListState = findViewById(R.id.video_list_state);
        if (mFileNames != null) {
            boolean hasVideos = mFileNames.size() > 0;
            if (hasVideos) {
                ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mFileNames);
                dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(dataAdapter);
                String lastFile = mPreferences.getString(LAST_FILE_PREF, null);
                if (lastFile != null) {
                    int selectionIndex = mFileNames.indexOf(lastFile);
                    if (selectionIndex >= 0) {
                        spinner.setSelection(selectionIndex);
                    }
                }
                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        mPreferences.edit().
                                putString(LAST_FILE_PREF, mFileNames.get(position)).
                                apply();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        mPreferences.edit().
                                putString(LAST_FILE_PREF, null).
                                apply();
                    }
                });
            } else {
                String error = null;
                if (mFileListException != null) {
                    error = mFileListException.getLocalizedMessage();
                }
                if (error == null) {
                    error = getResources().getString(R.string.default_no_file_error);
                }
                videoListState.setText(error);
            }
            videoListState.setVisibility(hasVideos ? View.INVISIBLE : View.VISIBLE);
            spinner.setVisibility(hasVideos ? View.VISIBLE : View.INVISIBLE);
            findViewById(R.id.play_video).setEnabled(hasVideos);
        } else {
            videoListState.setText(R.string.get_video_text);
        }
    }

    public void onGetVideoList(View view) {
        getVideoList();
    }

    private void getVideoList() {
        View focus = this.getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
        NetworkDirTask networkDirTask = new NetworkDirTask(this);
        networkDirTask.execute(WORKGROUP, NETWORK_PATH, getUsername(), getPassword());
        TextView videoListState = findViewById(R.id.video_list_state);
        videoListState.setText(R.string.fetching_list);
    }

    private String getUsername() {
        EditText userNameEdit = findViewById(R.id.username);
        return userNameEdit.getText().toString();
    }

    private String getPassword() {
        EditText passwordEdit = findViewById(R.id.password);
        return passwordEdit.getText().toString();
    }

    public void onPlayVideo(View view) {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        Spinner fileListSpinner = findViewById(R.id.file_list);
        int selectedIndex = fileListSpinner.getSelectedItemPosition();
        if (mNetworkVideos != null && selectedIndex < mNetworkVideos.size()) {
            SmbFile smbFile = mNetworkVideos.get(selectedIndex);
            try {
                playNetworkVideo(smbFile, mPreferences.getBoolean(RANDOM_ACCESS_PREF, false));
                findViewById(R.id.stop_video).setEnabled(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void onStopVideo(View view) {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            if (view == null &&
                    mPreferences.getBoolean(REPEAT_PREF, false)) {
                mMediaPlayer.start();
            } else {
                findViewById(R.id.stop_video).setEnabled(false);
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
// Use this for heap analysis
//            System.gc();
//            HeapDump();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        setContentView(R.layout.activity_demo);
        initSurface();
        initUI();
    }

    private void playNetworkVideo(SmbFile smbFile, boolean useRandomAccess) throws IOException {
        BufferedMediaDataSource bmds;
        if (useRandomAccess) {
            bmds = new SmbBufferedMediaDataSource(smbFile);
        } else {
            bmds = SmbUtil.createBufferedMediaDataSource(smbFile, useRandomAccess);
        }
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setDataSource(bmds);
        mMediaPlayer.setDisplay(mSurfaceHolder);
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                onStopVideo(null);
            }
        });
        mMediaPlayer.setOnPreparedListener(mp -> {
            Log.d(TAG, "Prepared");
            adjustViewSize();
            mMediaPlayer.start();
        });
        mMediaPlayer.prepareAsync();
    }

    private void adjustViewSize() {
        if (mMediaPlayer != null) {
            int videoWidth = mMediaPlayer.getVideoWidth();
            int videoHeight = mMediaPlayer.getVideoHeight();
            float videoRatio = (float) videoWidth / videoHeight;
            float displayRatio = (float) mVideoViewWidth / mVideoViewHeight;
            ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
            if (videoRatio > displayRatio) {
                lp.width = mVideoViewWidth;
                lp.height = (int) ((float) lp.width / videoRatio);
            } else {
                lp.height = mVideoViewHeight;
                lp.width = (int) ((float) lp.height * videoRatio);
            }
            Log.d(TAG, "Layout{" + lp.width + "," + lp.height + "} Video{" + videoWidth + "," + videoHeight + "} View{" + mVideoViewWidth + "," + mVideoViewHeight + "}");
            mSurfaceView.setLayoutParams(lp);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setDisplay(mSurfaceHolder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged: " + format + "{" + width + "," + height + "}");
        mVideoViewWidth = width;
        mVideoViewHeight = height;
        adjustViewSize();
        mSurfaceView.requestLayout();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setDisplay(null);
        }
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
}
