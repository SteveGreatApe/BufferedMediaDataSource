/* Copyright 2018 Great Ape Software Ltd
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

import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.greatape.bmds.BufferedMediaDataSource;
import com.greatape.bmdsapp.smb.SmbUtil;

import jcifs.smb.SmbFile;

/**
 * @author Steve Townsend
 */
class VideoViewer implements SurfaceHolder.Callback {
    private static final String TAG = "VideoViewer";

    private SurfaceHolder mSurfaceHolder;
    private MediaPlayer mMediaPlayer;
    private LinearLayout mHolderLayout;
    private SurfaceView mSurfaceView;
    private boolean mRestartOnResume;
    private boolean mRepeat;
    private UI mUi;

    interface UI {
        void stateChanged(boolean playing);
    }

    VideoViewer(UI ui) {
//        BmdsLog.enableDebug(true);
        mUi = ui;
    }

    void initSurface(LinearLayout holderLayout) {
        mHolderLayout = holderLayout;
        mSurfaceView = mHolderLayout.findViewById(R.id.video_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.setSizeFromLayout();
        mSurfaceHolder.addCallback(this);
    }

    boolean isPlaying() {
        return mMediaPlayer != null;
    }

    void setRepeatMode(boolean repeat) {
        mRepeat = repeat;
    }

    void stop() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    void onPause() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            mRestartOnResume = true;
        } else {
            mRestartOnResume = false;
        }
    }

    void onResume() {
        if (mMediaPlayer != null && mRestartOnResume) {
            mMediaPlayer.start();
        }
    }

    void playNetworkVideo(SmbFile smbFile, boolean useRandomAccess) {
        BufferedMediaDataSource bmds = SmbUtil.createBufferedMediaDataSource(smbFile, useRandomAccess);
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setDataSource(bmds);
        mMediaPlayer.setDisplay(mSurfaceHolder);
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                onStopVideo(true);
            }
        });
        mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                onStopVideo(false);
                return true;
            }
        });
        mMediaPlayer.setOnPreparedListener(mp -> {
            Log.d(TAG, "Prepared");
            adjustViewSize();
            mMediaPlayer.start();
        });
        mMediaPlayer.prepareAsync();
        mUi.stateChanged(true);
    }

    void onStopVideo(boolean allowRepeat) {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            if (allowRepeat && mRepeat) {
                mMediaPlayer.start();
            } else {
                mUi.stateChanged(false);
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
// Use this for heap analysis
//            System.gc();
//            HeapDump();
        }
    }

    private void adjustViewSize() {
        if (mMediaPlayer != null) {
            int viewWidth = mHolderLayout.getWidth();
            int viewHeight = mHolderLayout.getHeight();
            int videoWidth = mMediaPlayer.getVideoWidth();
            int videoHeight = mMediaPlayer.getVideoHeight();
            double videoRatio = (float) videoWidth / videoHeight;
            double displayRatio = (float)viewWidth / viewHeight;
            ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
            if (videoRatio > displayRatio) {
                lp.width = viewWidth;
                lp.height = (int)((double)lp.width / videoRatio);
            } else {
                lp.height = viewHeight;
                lp.width = (int)((double)lp.height * videoRatio);
            }
            Log.d(TAG, "Layout{" + lp.width + "," + lp.height + "} Video{" + videoWidth + "," + videoHeight + "} View{" + viewWidth + "," + viewHeight + "}");
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
        adjustViewSize();
        mSurfaceView.requestLayout();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setDisplay(null);
        }
    }
}
