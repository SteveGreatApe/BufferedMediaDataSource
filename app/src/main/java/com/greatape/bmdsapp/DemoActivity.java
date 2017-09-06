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
import android.graphics.Point;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.greatape.bmds.BmdsLog;
import com.greatape.bmds.BufferedMediaDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

/**
 * @author Steve Townsend
 */
public class DemoActivity extends Activity implements NetworkDirTask.Listener, SurfaceHolder.Callback {
    private static final String TAG = "DemoActivity";

    // TODO: Configure these to point to a Workgroup and Folder containing videos on your network.
    private static final String WORKGROUP = "WORKGROUP";
    private static final String NETWORK_PATH = "smb://EUROAPE/Share2/";

    private SurfaceHolder mSurfaceHolder;
    private MediaPlayer mMediaPlayer;
    private SmbFile[] mNetworkVideos;
    private SurfaceView mSurfaceView;
    private boolean mRestartOnResume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        mSurfaceView = (SurfaceView)findViewById(R.id.video_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.setSizeFromLayout();
        mSurfaceHolder.addCallback(this);
        TextView spinnerError = (TextView)findViewById(R.id.spinner_error);
        spinnerError.setText(R.string.get_video_text);
        String title = getResources().getString(R.string.title_format, NETWORK_PATH);
        TextView titleTextView = (TextView)findViewById(R.id.network_title);
        titleTextView.setText(title);
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
        mNetworkVideos = files;
        boolean hasVideos = mNetworkVideos != null && mNetworkVideos.length > 0;
        Spinner spinner = (Spinner)findViewById(R.id.file_list);
        TextView spinnerError = (TextView)findViewById(R.id.spinner_error);
        if (hasVideos) {
            List<String> fileNames = new ArrayList<>();
            for(SmbFile videoFile : mNetworkVideos) {
                fileNames.add(videoFile.getName());
            }
            ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fileNames);
            dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(dataAdapter);
        } else {
            String error = null;
            if (exception != null) {
                error = exception.getLocalizedMessage();
            }
            if (error == null) {
                error = getResources().getString(R.string.default_no_file_error);
            }
            spinnerError.setText(error);
        }
        spinnerError.setVisibility(hasVideos ? View.INVISIBLE : View.VISIBLE);
        spinner.setVisibility(hasVideos ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.play_video).setEnabled(hasVideos);
    }

    public void onGetVideoList(View view) {
        View focus = this.getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
        EditText userNameEdit = (EditText)findViewById(R.id.username);
        String username = userNameEdit.getText().toString();
        EditText passwordEdit = (EditText)findViewById(R.id.password);
        String password = passwordEdit.getText().toString();
        NetworkDirTask networkDirTask = new NetworkDirTask(this);
        networkDirTask.execute(WORKGROUP, NETWORK_PATH, username, password);
    }

    public void onPlayVideo(View view) {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        Spinner fileListSpinner = (Spinner)findViewById(R.id.file_list);
        int selectedIndex = fileListSpinner.getSelectedItemPosition();
        if (mNetworkVideos != null && selectedIndex < mNetworkVideos.length) {
            SmbFile smbFile = mNetworkVideos[selectedIndex];
            try {
                playNetworkVideo(smbFile);
                findViewById(R.id.stop_video).setEnabled(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void onStopVideo(View view) {
        if (mMediaPlayer != null) {
            findViewById(R.id.stop_video).setEnabled(false);
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private BufferedMediaDataSource createBufferedMediaDataSource(SmbFile smbFile) {
        BmdsLog.enableDebug(true);
        BufferedMediaDataSource bmds = null;
        try {
            bmds = new BufferedMediaDataSource(new BufferedMediaDataSource.StreamCreator() {
                @Override
                public InputStream openStream() throws IOException {
                    return new SmbFileInputStream(smbFile);
                }

                @Override
                public long length() throws IOException {
                    return smbFile.length();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bmds;
    }

    private void playNetworkVideo(SmbFile smbFile) throws IOException {
        BufferedMediaDataSource bmds = createBufferedMediaDataSource(smbFile);
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setDataSource(bmds);
        mMediaPlayer.setDisplay(mSurfaceHolder);
        mMediaPlayer.setOnPreparedListener(mp -> {
            Log.d(TAG, "Prepared");
            ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
            int videoWidth = mp.getVideoWidth();
            int videoHeight = mp.getVideoHeight();
            Point screenSize = new Point();
            getWindowManager().getDefaultDisplay().getSize(screenSize);
            lp.width = screenSize.x;
            lp.height = (int) (((float)videoHeight / (float)videoWidth) * (float)lp.width);
            mSurfaceView.setLayoutParams(lp);
            mMediaPlayer.start();
        });
        mMediaPlayer.prepareAsync();
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
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setDisplay(null);
        }
    }
}
