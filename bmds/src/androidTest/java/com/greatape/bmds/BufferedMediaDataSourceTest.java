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

import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Semaphore;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * @author Steve Townsend
 */
@RequiresApi(api = Build.VERSION_CODES.M)
public class BufferedMediaDataSourceTest {
    private final static long[] sTestLengths1 = {1, 10, 1000, 10000, 1000000, 10000000};
    private final static long[] sTestLengths2 = {1234, 123456, 12345678};
    private static final String TAG = "BufMediaDataSrcTest";

    private DummyStreamSource.Delay mPerCallDelay;
    private DummyStreamSource.Delay mLoadDelay;
    private DummyStreamSource.Delay mSkipDelay;

    enum TestMode {
        Sequential,
        Random
    }

    @Before
    public void setUp() {
        BmdsLog.enableDebug(true);
        BmdsLog.enablePerformance(true);
    }

    @Test
    public void testSmbDataSource() throws Exception {
        for(long streamLen : sTestLengths1) {
            doTest(streamLen);
        }
    }

    @Test
    public void testMultipleThreads() throws Exception {
        final int MaxThreads = 16;
        mPerCallDelay = new DummyStreamSource.Delay(1, 3);
        mLoadDelay = new DummyStreamSource.Delay(10, 20);
        mSkipDelay = new DummyStreamSource.Delay(2, 5);
        for(int numThreads = 4; numThreads < MaxThreads; numThreads *= 2) {
            for(long streamLen : sTestLengths2) {
                doMultiThreadTest(streamLen, numThreads);
            }
        }
    }

    private void doTest(long streamLen) throws IOException {
        for(int bufLen = Math.max(1, (int)(streamLen / 100)); bufLen <= streamLen; bufLen = bufLen * 2 + 1) {
            doTestSingleThread(TestMode.Sequential, streamLen, bufLen);
            doTestSingleThread(TestMode.Random, streamLen, bufLen);
        }
    }

    private void doTestSingleThread(TestMode testMode, long streamLen, int bufLen) throws IOException {
        TestThreadInstance tti = new TestThreadInstance(testMode, streamLen, bufLen);
        tti.startThread("Single");
        tti.waitToComplete();
    }

    private void doMultiThreadTest(long streamLen, int numThreads) throws IOException {
        for (int bufLen = Math.max(10, (int) (streamLen / 100)); bufLen <= streamLen; bufLen = bufLen * 3 + 1) {
            Log.d(TAG, "doMultiThreadTest: streamLen=" + streamLen + " numThreads=" + numThreads + " bufLen=" + bufLen);
            DummyStreamSource.resetStats();
            TestThreadInstance[] ttis = new TestThreadInstance[numThreads];
            for(int threadCount = 0; threadCount < numThreads; threadCount++) {
                TestMode testMode = (threadCount & 1) == 0 ? TestMode.Sequential : TestMode.Random;
                ttis[threadCount] = new TestThreadInstance(testMode, streamLen, bufLen);
                ttis[threadCount].startThread("Thread:" + threadCount);
            }
            for(int threadCount = 0; threadCount < numThreads; threadCount++) {
                TestThreadInstance tti = ttis[threadCount];
                String threadName = tti.mThread.getName();
                Log.d(TAG, "doMultiThreadTest: waitForThread: " + threadName);
                tti.waitToComplete();
                Log.d(TAG, "doMultiThreadTest: thread complete: " + threadName);
            }
            DummyStreamSource.logTotalStats();
        }
    }

    private BufferedMediaDataSource createDataSource(final long streamLen) throws IOException {
        return new BufferedMediaDataSource(new BufferedMediaDataSource.StreamCreator() {
            @Override
            public InputStream openStream() throws IOException {
                DummyStreamSource dummyStreamSource = new DummyStreamSource(streamLen);
                dummyStreamSource.setEmulatedCallDelay(mPerCallDelay);
                dummyStreamSource.setEmulatedLoadDelay(mLoadDelay);
                dummyStreamSource.setEmulatedSkipDelay(mSkipDelay);
                return dummyStreamSource;
            }

            @Override
            public long length() throws IOException {
                return streamLen;
            }
        });
    }

    private class TestThreadInstance implements Runnable {
        private TestMode mMode;
        private long mStreamLen;
        private int mBufLen;
        private Semaphore mSemaphore;
        private Thread mThread;

        TestThreadInstance(TestMode mode, long streamLen, int bufLen) {
            mMode = mode;
            mStreamLen = streamLen;
            mBufLen = bufLen;
            mSemaphore = new Semaphore(0);
        }

        void startThread(String name) {
            mThread = new Thread(this);
            mThread.setName(name);
            mThread.run();
        }

        void waitToComplete() {
            try {
                mSemaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                switch(mMode) {
                case Sequential:
                        sequential();
                        break;
                    case Random:
                        random();
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            mSemaphore.release();
            Log.d(TAG, "Test Thread Complete");
        }

        private void sequential() throws IOException {
            Log.d(TAG, "Sequential: streamLen=" + mStreamLen + " bufLen=" + mBufLen);
            BufferedMediaDataSource dataSource = createDataSource(mStreamLen);
            DummyStreamSource expectedValues = new DummyStreamSource(mStreamLen);
            byte[] buffer = new byte[mBufLen];
            for(int index = 0; index < mStreamLen; index += mBufLen) {
                long len = dataSource.readAt(index, buffer, 0, mBufLen);
                int expectedLen = (int) Math.min(mBufLen, mStreamLen - index);
                assertEquals(expectedLen, len);
                byte[] expected = new byte[expectedLen];
                int expectedRead = expectedValues.read(expected, 0, expectedLen);
                assertEquals(expectedLen, expectedRead);
                assertTrue(Arrays.equals(expected, expectedLen < mBufLen ? Arrays.copyOf(buffer, expectedLen) : buffer));
            }
            dataSource.close();
        }

        private void random() throws IOException {
            Log.d(TAG, "Random: streamLen=" + mStreamLen + " bufLen=" + mBufLen);
            final int NumRandomReads = 20;
            BufferedMediaDataSource dataSource = createDataSource(mStreamLen);
            Random random = new Random();
            for(int iteration = 0; iteration < NumRandomReads; iteration++) {
                int readLen = 1 + (mBufLen > 1 ? random.nextInt(mBufLen - 1) : 0);
                int index = mStreamLen > 1 ? random.nextInt((int)(mStreamLen - 1)) : 0;
                int expectedLen = (int) Math.min(readLen, mStreamLen - index);
                byte[] buffer = new byte[expectedLen];
                long len = dataSource.readAt(index, buffer, 0, readLen);
                assertEquals(expectedLen, len);
                byte[] expected = new byte[expectedLen];
                DummyStreamSource expectedValues = new DummyStreamSource(mStreamLen);
                assertEquals(index, expectedValues.skip(index));
                assertEquals(expectedLen, expectedValues.read(expected, 0, expectedLen));
                assertTrue(Arrays.equals(expected, buffer));
            }
            dataSource.close();
        }
    }
}