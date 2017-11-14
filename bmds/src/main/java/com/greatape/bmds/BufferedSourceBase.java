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

import java.io.IOException;

/**
 * @author Steve Townsend
 */
abstract class BufferedSourceBase {
    private static final String TAG = "BufferedSourceBase";
    BufferedMediaDataSource mBufferedMediaDataSource;
    long mPosition;
    int mId;

    BufferedSourceBase(BufferedMediaDataSource bufferedMediaDataSource, int id) {
        mBufferedMediaDataSource = bufferedMediaDataSource;
        mId = id;
    }

    abstract void close() throws IOException;

    int id() {
        return mId;
    }

    void log(String message, int blockIndex) {
        BmdsLog.d(TAG, "[" + mId + "] " + message, blockIndex);
    }

    void log(String message) {
        BmdsLog.d(TAG, "[" + mId + "] " + message);
    }

    long getPosition() {
        return mPosition;
    }

    abstract void skip(long seekPos) throws IOException;

    abstract int read(byte[] buffer) throws IOException;
}
