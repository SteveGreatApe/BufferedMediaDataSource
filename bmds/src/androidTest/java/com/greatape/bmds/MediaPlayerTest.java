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
package com.greatape.bmds;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;
import android.support.test.annotation.UiThreadTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.util.SparseIntArray;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static junit.framework.Assert.assertTrue;

/**
 * @author Steve Townsend
 */
@RunWith(AndroidJUnit4.class)
public class MediaPlayerTest {
    static final String TAG = "MediaPlayerTest";
    private static final String TAG_PERFORMANCE = TAG + ":Perf";

    static final String MIME_VIDEO = "video/";
    private static final String AssetVideoPath = "TestVideos";
    private static final String TestVideoPath = "BmdsTest";

    // The following can be used for more in depth long term and performance testing
    private static boolean PerformanceTest = false;
    private static final int PerformancePlayLength = 120 * 1000;
    private static final int PerformancePlayMaxIterations = 4;

    private Semaphore mSemaphore;
    private Semaphore mLooperThreadSemaphore;

    @Before
    public void grantRequiredPermissions() {
        grantPermission("READ_EXTERNAL_STORAGE");
    }

    void grantPermission(String permission) {
        // In M+ some permissions needs to show a dialog to grant the permission, run this so
        // the permission is granted before running these tests.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StringBuilder stringBuilder = new StringBuilder("pm grant ");
            stringBuilder.append(getInstrumentation().getTargetContext().getPackageName());
            stringBuilder.append(" android.permission.");
            stringBuilder.append(permission);
            getInstrumentation().getUiAutomation().executeShellCommand(stringBuilder.toString());
        }
    }

    @Before
    public void setUp() {
        mSemaphore = new Semaphore(0);
        mLooperThreadSemaphore = new Semaphore(0);
        //BmdsLog.enableDebug(true); // Enable this for in depth logging from BMDS
    }

    @Test
    @UiThreadTest
    public void testMediaPlayerAssets() throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AssetManager assetManager = getInstrumentation().getContext().getResources().getAssets();
            String[] files = assetManager.list(AssetVideoPath);
            for (String file : files) {
                String mimeType = URLConnection.guessContentTypeFromName(file);
                if (mimeType != null && mimeType.startsWith(MIME_VIDEO)) {
                    doAssetTest(assetManager, new File(AssetVideoPath, file).getPath());
                }
            }
        }
    }

    @Test
    @UiThreadTest
    public void testMediaPlayerExternal() throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            File external = Environment.getExternalStorageDirectory();
            if (external != null) {
                File testFolder = new File(external, TestVideoPath);
                File[] videoFiles = testFolder.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        String mimeType = URLConnection.guessContentTypeFromName(name);
                        return mimeType != null && mimeType.startsWith(MIME_VIDEO);
                    }
                });
                if (videoFiles != null) {
                    for (File file : videoFiles) {
                        doFileTest(file);
                    }
                } else {
                    Log.w(TAG, "No test files found in " + testFolder);
                }
            } else {
                Log.w(TAG, "External storage not found");
            }
        }
    }

    private void doAssetTest(final AssetManager assetManager, final String fileName) throws IOException, InterruptedException {
        logTestingTitle(fileName);
        BufferedMediaDataSource bufferedMediaDataSource = new BufferedMediaDataSource(new BufferedMediaDataSource.StreamCreator() {
            @Override
            public InputStream openStream() throws IOException {
                return assetManager.open(fileName);
            }

            @Override
            public long length() throws IOException {
                AssetFileDescriptor fd = assetManager.openFd(fileName);
                return fd.getLength();
            }
        });
        doTest(bufferedMediaDataSource);
    }

    private void doFileTest(final File file) throws IOException, InterruptedException {
        logTestingTitle(file.getPath());
        BufferedMediaDataSource bufferedMediaDataSource = new BufferedMediaDataSource(new BufferedMediaDataSource.StreamCreator() {
            @Override
            public InputStream openStream() throws IOException {
                return new FileInputStream(file);
            }

            @Override
            public long length() throws IOException {
                return file.length();
            }
        });
        doTest(bufferedMediaDataSource);
    }

    void logTestingTitle(String title) {
        String logString = "Testing: " + title;
        Log.d(TAG, logString);
        if (PerformanceTest) {
            Log.d(TAG_PERFORMANCE, logString);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void doTest(final BufferedMediaDataSource bufferedMediaDataSource) throws InterruptedException {
        final boolean[] success = new boolean[] {false};
        Thread thread = new Thread(() -> {
            Looper.prepare();
            Handler handler = new Handler();
            handler.post(() -> doBmsTest(bufferedMediaDataSource, success));
            Looper.loop();
            mLooperThreadSemaphore.release();
        });
        thread.start();
        mLooperThreadSemaphore.acquire();
        assertTrue(success[0]);
    }

    private boolean doBmsTest(final BufferedMediaDataSource bufferedMediaDataSource, final boolean[] success) {
        final MediaPlayer mediaPlayer = new MediaPlayer();
        mediaPlayer.setDataSource(bufferedMediaDataSource);
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mSemaphore.release();
                Log.d(TAG, "Prepared");
                try {
                    if (PerformanceTest) {
                        performanceTest(mediaPlayer);
                        SparseIntArray readStats = bufferedMediaDataSource.getReadStats().loadCounts();
                        logReadStats(readStats);
                    } else{
                        playMediaFile(mediaPlayer);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "Complete");
                success[0] = true;
                mediaPlayer.release();
                Looper.myLooper().quit();
            }
        });
        try {
            mediaPlayer.prepare();
            mediaPlayer.setVolume(0f, 0f);
            Log.d(TAG, "Waiting for prepare...");
        } catch (IOException e1) {
            e1.printStackTrace();
            Looper.myLooper().quit();
        }
        return success[0];
    }

    private void logReadStats(SparseIntArray readStats) {
        Log.d(TAG, "Read stats: ");
        int maxLogged = Integer.MAX_VALUE;
        int maxCount;
        do {
            ArrayList<Integer> keys = null;
            maxCount = 0;
            for (int index = 0; index < readStats.size(); index++) {
                int key = readStats.keyAt(index);
                int value = readStats.get(key);
                if (value >= maxLogged) {
                    continue;
                }
                if (value > maxCount) {
                    maxCount = value;
                    keys = null;
                }
                if (value == maxCount) {
                    if (keys == null) {
                        keys = new ArrayList<>();
                    }
                    keys.add(key);
                }
            }
            if (keys != null) {
                maxLogged = maxCount;
                StringBuilder stringBuilder = new StringBuilder("Count=" + maxCount + " ");
                for(int key : keys) {
                    stringBuilder.append(key);
                    stringBuilder.append(",");
                }
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                Log.d(TAG_PERFORMANCE, stringBuilder.toString());
            }
        } while(maxCount > 0);
    }

    private void playMediaFile(MediaPlayer mediaPlayer) throws InterruptedException {
        final int TestPlayLength = 5 * 1000;

        int duration = mediaPlayer.getDuration();
        int target = Math.min(duration, TestPlayLength);
        MediaPlayMonitor mediaPlayMonitor = new MediaPlayMonitor(mediaPlayer);
        mediaPlayer.start();
        mediaPlayMonitor.waitForTarget(target, true);
        int seekTarget = Math.max(1, duration - TestPlayLength);
        mediaPlayer.pause();
        mediaPlayer.seekTo(seekTarget);
        mediaPlayMonitor.waitForTarget(-seekTarget, false);
        mediaPlayer.start();
        mediaPlayMonitor.waitForTarget(target, true);
        mediaPlayMonitor.stop();
    }

    private void performanceTest(MediaPlayer mediaPlayer) throws InterruptedException {
        int duration = mediaPlayer.getDuration();
        MediaPlayMonitor mediaPlayMonitor = new MediaPlayMonitor(mediaPlayer);
        mediaPlayer.start();
        int performancePlayLength = Math.min(PerformancePlayLength, duration / 4);
        int iterations = Math.min(1 + duration / performancePlayLength, PerformancePlayMaxIterations);
        for(int iteration = 0; iteration < iterations; iteration++) {
            int seekTo = 0;
            if (iteration > 0) {
                seekTo = iteration * (duration - performancePlayLength) / (iterations - 1);
                mediaPlayer.pause();
                mediaPlayer.seekTo(seekTo);
                long seekDuration = mediaPlayMonitor.waitForTarget(seekTo, false);
                mediaPlayer.start();
                logPerformance(0, seekDuration);
            }
            int target = seekTo + performancePlayLength;
            long actualDuration = mediaPlayMonitor.waitForTarget(target, true);
            logPerformance(performancePlayLength, actualDuration);
        }
        mediaPlayMonitor.stop();
    }

    private void logPerformance(int expected, long actual) {
        Log.d(TAG_PERFORMANCE, "Expected: " + expected + " Actual: " + actual + " Lag: " + (actual - expected));
    }

    private class MediaPlayMonitor implements Runnable {
        // Media players won't go to the exact millisecond expected, so allow a little tolerance.
        private final static long TargetTolerance = 100;
        private final static long DefaultPollTime = 1000;
        private final static long MaxPollTime = 10 * 1000;

        private final Thread mThread;
        private final MediaPlayer mMediaPlayer;
        private boolean mStop;
        private Semaphore mSemaphore;
        private long mTargetTime;
        private boolean mLinearWait;

        private MediaPlayMonitor(MediaPlayer mediaPlayer) {
            mMediaPlayer = mediaPlayer;
            mThread = new Thread(this);
            mThread.start();
            mSemaphore = new Semaphore(0);
        }

        private void stop() {
            mStop = true;
            mThread.interrupt();
        }

        private long waitForTarget(long millis, boolean linearWait) throws InterruptedException {
            long start = System.currentTimeMillis();
            mTargetTime = millis;
            mLinearWait = linearWait;
            mSemaphore.acquire();
            return System.currentTimeMillis() - start;
        }

        @Override
        public void run() {
            while(!mStop) {
                try {
                    int currentPosition = mMediaPlayer.getCurrentPosition();
                    Log.d(TAG, "MediaPlayer Position: " + currentPosition + "ms, Target: " + mTargetTime + "ms");
                    long waitTime = DefaultPollTime;
                    if (mTargetTime > 0) {
                        // If mTargetTime > 0 assume we are playing forwards and look for anything past the target.
                        if (currentPosition >= (mTargetTime - TargetTolerance)) {
                            mTargetTime = 0;
                            mSemaphore.release();
                        } else if (mLinearWait) {
                            waitTime = Math.min(MaxPollTime, mTargetTime - currentPosition);
                        }
                    } else if (mTargetTime < 0) {
                        // If mTargetTime < 0 then assume we are seeking non-linearly and look for being close to the target.
                        long difference = Math.abs(currentPosition + mTargetTime);
                        if (difference < TargetTolerance) {
                            mTargetTime = 0;
                            mSemaphore.release();
                        }
                    }
                    Thread.sleep(waitTime);
                } catch(InterruptedException ignore){
                }
            }
        }
    }
}