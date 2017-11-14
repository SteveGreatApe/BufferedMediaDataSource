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

import android.media.MediaDataSource;
import android.os.Build;
import android.support.annotation.RequiresApi;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

/**
 * @author Steve Townsend
 */
@RequiresApi(api = Build.VERSION_CODES.M)
public class BufferedMediaDataSource extends MediaDataSource {
    private StreamCreator mStreamCreator;
    private DataInputCreator mDataInputCreator;
    private MediaCache mMediaCache;
    private LinkedList<BufferedSourceBase> mLinkedList;
    private BufferedSourceBase mSingleSource;
    private Long mSize;

    public static class BufferConfig {
        public int maxUsedBuffers;
        public int bufferSize;
        public int cacheAheadCount;

        public BufferConfig() {
            maxUsedBuffers = 64;
            bufferSize = 128 * 1024;
            cacheAheadCount = 8;
        }
    }

    // Use this to create an InputStream derived implementation such as FileInputStream or SmbFileInputStream
    // Multiple streams will be created to deal with non-sequential access
    public interface StreamCreator {
        InputStream openStream() throws IOException;
        long length() throws IOException;
    }

    // Use this to create an DataInput derived implementation such as RandomAccessFile or SmbRandomAccessFile
    // Only one DataInput class will be used for all access
    // Note: Ideally the readData(), seek() and closeDataInput() functions would not have been
    // required, but due to poor specification and implementations of these functions we need our
    // own versions where implementation specific versions can be implemented.
    // For example DataInput.skipBytes() only skips forward, different functions per implementation
    // need to be called to seek backwards as well.
    // The SmbRandomAccessFile implementation of readFully adds on double of the read offset, so
    // will read the wrong data for all but the first read.
    // There is no common close() function specified in DataInput.
    public interface DataInputCreator {
        DataInput openDataInput() throws IOException;
        void closeDataInput(DataInput dataInput) throws IOException;
        default void readData(DataInput dataInput, byte[] buffer, int readLen) throws IOException {dataInput.readFully(buffer, 0, readLen);}
        void seek(DataInput dataInput, long seekPos) throws IOException;
        long length() throws IOException;
    }

    private BufferedMediaDataSource(BufferConfig bufferConfig) throws IOException {
        mMediaCache = new MediaCache(this, bufferConfig);
    }

    public BufferedMediaDataSource(StreamCreator streamCreator, BufferConfig bufferConfig) throws IOException {
        this(bufferConfig);
        mLinkedList = new LinkedList<>();
        mStreamCreator = streamCreator;
    }

    public BufferedMediaDataSource(StreamCreator streamCreator) throws IOException {
        this(streamCreator, new BufferConfig());
    }

    public BufferedMediaDataSource(DataInputCreator dataInputCreator, BufferConfig bufferConfig) throws IOException {
        this(bufferConfig);
        mDataInputCreator = dataInputCreator;
    }

    public BufferedMediaDataSource(DataInputCreator dataInputCreator) throws IOException {
        this(dataInputCreator, new BufferConfig());
    }

    void readData(DataInput dataInput, byte[] buffer, int readLen) throws IOException {
        mDataInputCreator.readData(dataInput, buffer, readLen);
    }

    void skipBytes(DataInput dataInput, long seekPos) throws IOException {
        mDataInputCreator.seek(dataInput, seekPos);
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        return mMediaCache.readAt(position, buffer, offset, size);
    }

    @Override
    public long getSize() throws IOException {
        if (mSize == null) {
            if (mStreamCreator != null) {
                mSize = mStreamCreator.length();
            } else {
                mSize = mDataInputCreator.length();
            }
        }
        return mSize;
    }

    @Override
    public void close() throws IOException {
        mMediaCache.close();
        if (mLinkedList != null) {
            while (!mLinkedList.isEmpty()) {
                mLinkedList.removeFirst().close();
            }
        } else if (mSingleSource != null) {
            mSingleSource.close();
            mSingleSource = null;
        }
    }

    void closeDataInput(DataInput dataInput) throws IOException {
        mDataInputCreator.closeDataInput(dataInput);
    }

    BufferedSourceBase streamForIndex(int bufferIndex) throws IOException {
        if (mLinkedList == null) {
            if (mSingleSource == null) {
                mSingleSource = createStreamSource(1);
            }
            return mSingleSource;
        }
        int prevIndex = -1;
        int lastId = -1;
        for(int index = 0; index < mLinkedList.size(); index++) {
            BufferedSourceBase bufferedStream = mLinkedList.get(index);
            int nextIndex = mMediaCache.blockIndex(bufferedStream.getPosition());
            if (nextIndex == prevIndex) {
                bufferedStream.log("Closing matching stream, both loading: ", nextIndex);
                removeBufferedStream(index - 1);
                index--;
            }
            if (nextIndex <= bufferIndex) {
                return bufferedStream;
            }
            prevIndex = nextIndex;
            lastId = bufferedStream.id();
        }
        BufferedSourceBase newStream = createStreamSource(lastId + 1);
        mLinkedList.add(newStream);
        newStream.log("Created new BufferedMediaStream");
        return newStream;
    }

    private BufferedSourceBase createStreamSource(int id) throws IOException {
        BufferedSourceBase newStream;
        if (mDataInputCreator != null) {
            newStream = new BufferedDataSource(this, mDataInputCreator.openDataInput(), id);
        } else {
            newStream = new BufferedMediaStream(this, mStreamCreator.openStream(), id);
        }
        return newStream;
    }

    void removeBufferedStream(BufferedSourceBase removeMe) {
        if (mLinkedList != null) {
            for (int index = 0; index < mLinkedList.size(); index++) {
                BufferedSourceBase bufferedStream = mLinkedList.get(index);
                if (bufferedStream == removeMe) {
                    removeBufferedStream(index);
                }
            }
        }
    }

    private void removeBufferedStream(int index) {
        BufferedSourceBase removeMe = mLinkedList.remove(index);
        try {
            removeMe.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    ReadStats getReadStats() {
        return mMediaCache.getReadStats();
    }
}
