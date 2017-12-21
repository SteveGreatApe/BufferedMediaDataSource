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

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;

/**
 * @author Steve Townsend
 */
class BufferedDataSource extends BufferedSourceBase {
    private DataInput mDataInput;

    BufferedDataSource(BufferedMediaDataSource bufferedMediaDataSource, DataInput dataInput, int id) throws IOException {
        super(bufferedMediaDataSource, id);
        mDataInput = dataInput;
    }

    void close() throws IOException {
        if (mDataInput != null) {
            mBufferedMediaDataSource.closeDataInput(mDataInput);
            mDataInput = null;
        }
    }

    @Override
    int read(byte[] buffer) throws IOException {
        log("Reading from: " + mPosition);
        int readLen = buffer.length;
        long available = (mBufferedMediaDataSource.getSize() - mPosition);
        if (available < 0) {
            throw new IOException("Read past end of file by " + available + " byte(s)");
        }
        boolean removeBufferedStream = false;
        if (available < readLen) {
            readLen = (int)available;
            removeBufferedStream = true;
        }
        try {
            // We should just call readFully here, but unbelievably the SmbRandomAccessFile
            // readFully() is totally broken, it adds on the double the read offset to the file
            // position, so subsequent reads get the wrong data, Doh! Just a small bug aye. NOT!
//            mDataInput.readFully(buffer, 0, readLen);
            mBufferedMediaDataSource.readData(mDataInput, buffer, readLen);
        }catch (IOException ioe) {
            if (ioe instanceof EOFException || ioe.getMessage().equals("EOF")) {
                // This is horrible, SmbRandomAccessFile doesn't follow the spec and throw EOFException
                // instead it breaks the contract and throws an SmbException with the description "EOF".
                // So we need to catch this special case.
                log("Reached EOF");
                removeBufferedStream = true;
            } else {
                log("Unexpected exception reading input: " + ioe.getMessage());
                throw ioe;
            }
        }
        if (removeBufferedStream) {
            mBufferedMediaDataSource.removeBufferedStream(this);
        }
        // There doesn't seem to be any way of knowing how much was actually read?
        // Can we rely the fact we've hit EOF meaning no more data will be requested?
        mPosition += readLen;
        return readLen;
    }

    @Override
    void skip(long seekPos) throws IOException {
        mBufferedMediaDataSource.skipBytes(mDataInput, seekPos);
        mPosition = seekPos;
    }
}
