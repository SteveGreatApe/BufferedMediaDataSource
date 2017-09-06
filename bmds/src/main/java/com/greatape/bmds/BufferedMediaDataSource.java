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

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

/**
 * @author Steve Townsend
 */
@RequiresApi(api = Build.VERSION_CODES.M)
public class BufferedMediaDataSource extends MediaDataSource {
    private StreamCreator mStreamCreator;
    private MediaCache mMediaCache;
    private final LinkedList<BufferedMediaStream> mLinkedList;

    static class BufferConfig {
        int maxUsedBuffers;
        int bufferSize;
        int cacheAheadCount;

        BufferConfig() {
            maxUsedBuffers = 64;
            bufferSize = 128 * 1024;
            cacheAheadCount = 8;
        }
    }

    public interface StreamCreator {
        // TODO: Added new scheme to work from DataInput/SmbRandomAccessFile
        InputStream openStream() throws IOException;
        long length() throws IOException;
    }

    public BufferedMediaDataSource(StreamCreator streamCreator) throws IOException {
        this(streamCreator, new BufferConfig());
    }

    public BufferedMediaDataSource(StreamCreator streamCreator, BufferConfig bufferConfig) throws IOException {
        mStreamCreator = streamCreator;
        mLinkedList = new LinkedList<>();
        mMediaCache = new MediaCache(this, bufferConfig);
    }

    InputStream openStream() throws IOException {
        return mStreamCreator.openStream();
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        return mMediaCache.readAt(position, buffer, offset, size);
    }

    @Override
    public long getSize() throws IOException {
        return mStreamCreator.length();
    }

    @Override
    public void close() throws IOException {
        mMediaCache.close();
        while(!mLinkedList.isEmpty()) {
            mLinkedList.removeFirst().close();
        }
    }

    BufferedMediaStream streamForIndex(int bufferIndex) throws IOException {
        int prevIndex = -1;
        int lastId = -1;
        for(int index = 0; index < mLinkedList.size(); index++) {
            BufferedMediaStream bufferedStream = mLinkedList.get(index);
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
        BufferedMediaStream newStream = new BufferedMediaStream(this, lastId + 1);
        mLinkedList.add(newStream);
        newStream.log("Created new BufferedMediaStream");
        return newStream;
    }

    void removeBufferedStream(BufferedMediaStream removeMe) {
        for (int index = 0; index < mLinkedList.size(); index++) {
            BufferedMediaStream bufferedStream = mLinkedList.get(index);
            if (bufferedStream == removeMe) {
                removeBufferedStream(index);
            }
        }

    }

    private void removeBufferedStream(int index) {
        BufferedMediaStream removeMe = mLinkedList.remove(index);
        try {
            removeMe.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ReadStats getReadStats() {
        return mMediaCache.getReadStats();
    }
}
