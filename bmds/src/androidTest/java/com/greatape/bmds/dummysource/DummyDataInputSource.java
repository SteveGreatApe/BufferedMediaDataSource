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

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;

/**
 * @author Steve Townsend
 */
public class DummyDataInputSource extends DummyStreamSource implements DataInput {

    public DummyDataInputSource(long length) {
        super(length);
    }

    @Override
    public void readFully(@NonNull byte[] buffer) throws IOException {
        readFully(buffer, 0, buffer.length);
    }

    @Override
    public void readFully(@NonNull byte[] buffer, int off, int len) throws IOException {
        int read = read(buffer, off, len);
        if (read < len) {
            throw new EOFException("Dummy DataInput EOF");
        }
    }

    @Override
    public int skipBytes(int n) throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean readBoolean() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public byte readByte() throws IOException {
        return (byte)read();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public short readShort() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public int readUnsignedShort() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public char readChar() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public int readInt() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long readLong() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public float readFloat() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public double readDouble() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String readLine() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @NonNull
    @Override
    public String readUTF() throws IOException {
        throw new RuntimeException("Not implemented");
    }
}
