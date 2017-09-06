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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Steve Townsend
 */
@RequiresApi(api = Build.VERSION_CODES.M)
class BufferedMediaStream {
    private static final String TAG = "Stream";

    private BufferedMediaDataSource bufferedMediaDataSource;
    private InputStream mInputStream;
    private long mPosition;
    private int mId;

    BufferedMediaStream(BufferedMediaDataSource bufferedMediaDataSource, int id) throws IOException {
        this.bufferedMediaDataSource = bufferedMediaDataSource;
        mId = id;
        mInputStream = bufferedMediaDataSource.openStream();
    }

    void close() throws IOException {
        if (mInputStream != null) {
            mInputStream.close();
            mInputStream = null;
        }
    }

    int id() {
        return mId;
    }

    void log(String message, int blockIndex) {
        BmdsLog.d(TAG, "[" + mId + "] " + message, blockIndex);
    }

    void log(String message) {
        BmdsLog.d(TAG, "[" + mId + "] " + message);
    }

    int read(byte[] buffer, int bufferSize) throws IOException {
        log("Reading from: " + mPosition);
        int read = 0;
        do {
            long len = mInputStream.read(buffer, read, bufferSize - read);
            if (len <= 0) {
                log("Reached EOF after reading " + read + " bytes, end position=" + (mPosition + read));
                bufferedMediaDataSource.removeBufferedStream(this);
                break;
            }
            read += len;
            log("Reading to go=" + (bufferSize - read));
        } while (read < bufferSize);
        mPosition += read;
        return read;
    }

    void skip(long toSkip) throws IOException {
        long skipped = 0;
        do {
            long len = mInputStream.skip(toSkip);
            if (len <= 0) {
                BmdsLog.e(TAG, "Error, unexpected EOF");
                throw new EOFException();
            }
            skipped += len;
        } while (skipped < toSkip);
        mPosition += skipped;
    }

    long getPosition() {
        return mPosition;
    }
}
