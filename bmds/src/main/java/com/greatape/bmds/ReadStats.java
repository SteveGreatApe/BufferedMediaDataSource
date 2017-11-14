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

import android.util.SparseArray;
import android.util.SparseIntArray;

import java.util.Set;

/**
 * @author Steve Townsend
 */
public class ReadStats {
    private final SparseIntArray mLoadCounts;
    private final SparseArray<Long> mLastLoadTimes;
    private final SparseArray<Long> mLastUsedTimes;

    ReadStats() {
        mLoadCounts = new SparseIntArray();
        mLastLoadTimes = new SparseArray<>();
        mLastUsedTimes = new SparseArray<>();
    }

    void blockLoaded(int blockIndex) {
        mLoadCounts.put(blockIndex, mLoadCounts.get(blockIndex) + 1);
        mLastLoadTimes.put(blockIndex, System.currentTimeMillis());
    }

    void blockUsed(int blockIndex) {
        mLastUsedTimes.put(blockIndex, System.currentTimeMillis());
    }

    int selectBlockToPurge(Set<Integer> integers, int currentLoad) {
        long maxScore = 0;
        int blockToPurge = -1;
        long now = System.currentTimeMillis();
        for(int blockIndex : integers) {
            if (blockIndex == currentLoad) {
                continue;
            }
            Long lastUsed = mLastUsedTimes.get(blockIndex);
            if (lastUsed == null) {
                lastUsed = mLastLoadTimes.get(blockIndex);
            }
            long score = now - lastUsed;
            int loadCount = mLoadCounts.get(blockIndex);
            score /= loadCount;
            if (score > maxScore) {
                maxScore = score;
                blockToPurge = blockIndex;
            }
        }
        return blockToPurge;
    }

    public SparseIntArray loadCounts() {
        return mLoadCounts;
    }
}
