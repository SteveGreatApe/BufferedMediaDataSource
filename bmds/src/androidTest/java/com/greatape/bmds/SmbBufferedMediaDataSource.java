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
import java.io.IOException;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbRandomAccessFile;

/**
 * @author Steve Townsend
 */
class SmbBufferedMediaDataSource extends BufferedMediaDataSource {
    SmbBufferedMediaDataSource(SmbFile smbFile, BufferConfig bufferConfig) throws IOException {
        super(new DataInputCreator() {
            @Override
            public DataInput openDataInput() throws IOException {
                return new SmbRandomAccessFile(smbFile, "r");
            }

            @Override
            public void readData(DataInput dataInput, byte[] buffer, int readLen) throws IOException {
                ((SmbRandomAccessFile)dataInput).read(buffer, 0, readLen);
            }

            @Override
            public void closeDataInput(DataInput dataInput) throws IOException {
                ((SmbRandomAccessFile)dataInput).close();
            }

            @Override
            public void seek(DataInput dataInput, long pos) throws IOException {
                ((SmbRandomAccessFile)dataInput).seek(pos);
            }

            @Override
            public long length() throws SmbException {
                return smbFile.length();
            }

            @Override
            public String typeName() {
                return "SmbFile";
            }
        }, bufferConfig);
    }
}
