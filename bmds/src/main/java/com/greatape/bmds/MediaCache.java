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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

/**
 * @author Steve Townsend
 */
@RequiresApi(api = Build.VERSION_CODES.M)
class MediaCache {
    private static final String TAG = "MediaCache";
    private TreeMap<Integer, byte[]> mBufferStore = new TreeMap<>();
    private int mMaxBlockIndex;
    private ArrayList<Integer> mBlockRepeatedCachedAhead;
    private final LoadRunnerClient mLoadRunner;
    private final BufferedMediaDataSource mBufferedMediaDataSource;
    private int mBufferSize;
    private int mMaxUsedBuffers;
    private final ReadStats mReadStats;
    private int mCacheAheadCount;

    MediaCache(BufferedMediaDataSource bufferedMediaDataSource, BufferedMediaDataSource.BufferConfig bufferConfig) {
        mBufferSize = bufferConfig.bufferSize;
        mCacheAheadCount = bufferConfig.cacheAheadCount;
        mBufferedMediaDataSource = bufferedMediaDataSource;
        mReadStats = new ReadStats();
        mLoadRunner = LoadRunner.addNewClient(this, bufferedMediaDataSource.typeName());
        mMaxUsedBuffers = bufferConfig.maxUsedBuffers;
        mBlockRepeatedCachedAhead = new ArrayList<>();
        mMaxBlockIndex = -1;
    }

    void close() {
        mLoadRunner.close();
    }

    private byte[] getBuffer(int blockIndex) throws IOException {
        byte[] cacheBuffer;
        LoadRunnerClient.LoadItem loadItem = null;
        synchronized (this) {
            cacheBuffer = mBufferStore.get(blockIndex);
            if (cacheBuffer == null) {
                BmdsLog.d(TAG, "Wait IN", blockIndex);
                loadItem = mLoadRunner.requestLoad(blockIndex, true);
//            } else { // Too verbose for normal usage.
//                BmdsLog.d(TAG, "Using Cached Buffer", blockIndex);
            }
        }
        if (cacheBuffer == null) {
            cacheBuffer = loadItem.waitForBuffer();
            BmdsLog.d(TAG, "Wait OUT", blockIndex);
        }
        return cacheBuffer;
    }

    int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        int blockIndex = blockIndex(position);
        mReadStats.blockUsed(blockIndex);
        int blockRepeatIndex = mBlockRepeatedCachedAhead.indexOf(blockIndex);
        if (blockRepeatIndex >= 0) {
            mBlockRepeatedCachedAhead.remove(blockRepeatIndex);
        }
        byte[] cacheBuffer = getBuffer(blockIndex);
        // Cache ahead next buffer.
        checkForCacheAhead(blockIndex);
        int cacheOffset = (int) (position % mBufferSize);
        int availableCache = cacheBuffer.length - cacheOffset;
        int copyLen = Math.min(availableCache, size);
        System.arraycopy(cacheBuffer, cacheOffset, buffer, offset, copyLen);
        if (cacheBuffer.length == mBufferSize && copyLen < size) {
            copyLen += readAt(position + copyLen, buffer, offset + copyLen, size - copyLen);
        }
        return copyLen;
    }

    private void checkForCacheAhead(int blockIndex) {
        synchronized (this) {
            for (int ahead = 1; ahead <= mCacheAheadCount; ahead++) {
                int cacheAheadIndex = blockIndex + ahead;
                if (!mBufferStore.keySet().contains(cacheAheadIndex) &&
                        !mLoadRunner.hasRequestForBlock(cacheAheadIndex) &&
                        !mBlockRepeatedCachedAhead.contains(cacheAheadIndex)) {
                    // We only want to cache ahead a block once if it doesn't get used after caching.
                    mBlockRepeatedCachedAhead.add(cacheAheadIndex);
                    mLoadRunner.requestLoad(cacheAheadIndex, false);
                }
            }
        }
    }

    byte[] readIntoCache(int blockIndex) throws IOException {
        if (mMaxBlockIndex >= 0 && blockIndex > mMaxBlockIndex) {
            return null;
        }
        BufferedSourceBase bufferedStream = mBufferedMediaDataSource.streamForIndex(blockIndex);
        long currentPos = bufferedStream.getPosition();
        long targetPos = blockIndex  * mBufferSize;
        if (targetPos != currentPos) {
            bufferedStream.skip(targetPos);
        }
        byte[] cacheBuffer = new byte[mBufferSize];
        int len = bufferedStream.read(cacheBuffer);
        if (len < mBufferSize) {
            cacheBuffer = Arrays.copyOf(cacheBuffer, len);
            mMaxBlockIndex = blockIndex;
        }
        synchronized (this) {
            mBufferStore.put(blockIndex, cacheBuffer);
        }
        mReadStats.blockLoaded(blockIndex);
        if (mBufferStore.size() > mMaxUsedBuffers) {
            int toPurge = mReadStats.selectBlockToPurge(mBufferStore.keySet(), blockIndex);
            mBufferStore.remove(toPurge);
            BmdsLog.d(TAG, "Purged", toPurge);
        }
        bufferedStream.log("Loaded buffer: ", blockIndex);
        return cacheBuffer;
    }

    int blockIndex(long position) {
        return (int) (position / mBufferSize);
    }

    ReadStats getReadStats() {
        return mReadStats;
    }
}
