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
package com.greatape.bmds.dummysource;

import android.support.annotation.NonNull;
import android.util.Log;

import com.greatape.bmds.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * @author Steve Townsend
 */
public class DummyStreamSource extends InputStream {
    private static final String TAG = "DummyStreamSource";

    private static final long Megabyte = 1024 * 1024;

    private static Random sRandom = new Random(0);
    long mLength;
    long mIndex;
    private Delay mPerMegabyte;
    private Delay mPerCall;
    private Delay mSkipPerMegabyte;

    private static long mTotalLoadBytes;
    private static long mTotalLoadDelay;
    private static long mTotalSkipBytes;
    private static long mTotalSkipDelay;
    private static long mTotalCalls;
    private static long mTotalPerCallDelay;

    public DummyStreamSource(long length) {
        mLength = length;
    }

    public void setEmulatedCallDelay(Delay perCallDelay) {
        mPerCall = perCallDelay;
    }

    public void setEmulatedLoadDelay(Delay perMbDelay) {
        mPerMegabyte = perMbDelay;
    }

    public void setEmulatedSkipDelay(Delay perSkipDelay) {
        mSkipPerMegabyte = perSkipDelay;
    }

    @Override
    public int read() throws IOException {
        emulateLoadTime(1, 0);
        if (mIndex >= mLength) {
            return -1;
        }
        return expectedValue(mIndex++);
    }

    public int read(@NonNull byte buffer[], int off, int len) throws IOException {
        int actualLen = Math.min(len, (int) (mLength - mIndex));
        emulateLoadTime(actualLen, 0);
        long end = mIndex + actualLen;
        for(; mIndex < end; mIndex++) {
            buffer[off++] = (byte)expectedValue(mIndex);
        }
        return actualLen;
    }

    public void seek(long seekPos) throws IOException {
        mIndex = Math.min(seekPos, mLength);
    }

    void emulateLoadTime(int loadLen, int skipLen) {
        long loadTime = 0;
        mTotalCalls++;
        if (mPerCall != null) {
            loadTime += mPerCall.rand();
            mTotalPerCallDelay += loadTime;
        }
        mTotalLoadBytes += loadLen;
        if (mPerMegabyte != null) {
            long loadDelay = loadLen * mPerMegabyte.rand() / Megabyte;
            mTotalLoadDelay += loadDelay;
            loadTime += loadDelay;
        }
        mTotalSkipBytes += skipLen;
        if (mSkipPerMegabyte != null && skipLen > 0) {
            long skipDelay = skipLen * mSkipPerMegabyte.rand() / Megabyte;
            mTotalSkipDelay += skipDelay;
            loadTime += skipDelay;
        }
        if (loadTime > 0) {
            try {
                Thread.sleep(loadTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    static int expectedValue(long index) {
        return ((int)index & 0xFF) ^ (((int)index >> 8) & 0xFF);
    }

    public static class Delay {
        long min;
        int maxExtra;

        public Delay(long min, long max) {
            this.min = min;
            this.maxExtra = (int)(max - min);
        }

        long rand() {
            long ret = min;
            if (maxExtra > 0) {
                ret += sRandom.nextInt(maxExtra);
            }
            return ret;
        }
    }

    public static void logTotalStats() {
        Log.d(TAG, "Totals: Calls: " + mTotalCalls + " Loaded: " + Utils.formatFileSize(mTotalLoadBytes) + " Skipped: " + Utils.formatFileSize(mTotalSkipBytes));
        Log.d(TAG, "Delays: Call: " + Utils.formatDuration(mTotalPerCallDelay) + " Loading: " + Utils.formatDuration(mTotalLoadDelay) + " Skipping: " + Utils.formatDuration(mTotalSkipDelay));
    }

    public static void resetStats() {
        mTotalLoadBytes = 0;
        mTotalLoadDelay= 0;
        mTotalSkipBytes= 0;
        mTotalSkipDelay= 0;
        mTotalCalls= 0;
        mTotalPerCallDelay = 0;
    }
}
