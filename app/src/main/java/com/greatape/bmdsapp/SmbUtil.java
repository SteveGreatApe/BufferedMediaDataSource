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
package com.greatape.bmdsapp;

import com.greatape.bmds.BufferedMediaDataSource;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbRandomAccessFile;

/**
 * @author Steve Townsend
 */
class SmbUtil {

    static BufferedMediaDataSource createBufferedMediaDataSource(SmbFile smbFile, boolean useRandomAccess) {
        BufferedMediaDataSource bmds = null;
        BufferedMediaDataSource.BufferConfig bufferConfig = new BufferedMediaDataSource.BufferConfig();
        int bufferSize = getReadBufferSize(smbFile, useRandomAccess);
        if (bufferSize > 0) {
            int totalBufferSize = bufferConfig.bufferSize * bufferConfig.maxUsedBuffers;
            int totalCacheAheadSize = bufferConfig.bufferSize * bufferConfig.cacheAheadCount;
            bufferConfig.bufferSize = bufferSize;
            bufferConfig.maxUsedBuffers = totalBufferSize / bufferConfig.bufferSize;
            bufferConfig.cacheAheadCount = totalCacheAheadSize / bufferConfig.bufferSize;
        }
        try {
            if (useRandomAccess) {
                bmds = new BufferedMediaDataSource(new BufferedMediaDataSource.DataInputCreator() {
                    @Override
                    public DataInput openDataInput() throws IOException {
                        return new SmbRandomAccessFile(smbFile, "r");
                    }

                    @Override
                    public long length() throws IOException {
                        return smbFile.length();
                    }

                    @Override
                    public void seek(DataInput dataInput, long seekPos) throws IOException {
                        ((SmbRandomAccessFile)dataInput).seek(seekPos);
                    }

                    @Override
                    public void closeDataInput(DataInput dataInput) throws IOException {
                        ((SmbRandomAccessFile)dataInput).close();
                    }

                    @Override
                    public void readData(DataInput dataInput, byte[] buffer, int readLen) throws IOException {
                        ((SmbRandomAccessFile)dataInput).read(buffer, 0, readLen);
                    }
                }, bufferConfig);
            } else {
                bmds = new BufferedMediaDataSource(new BufferedMediaDataSource.StreamCreator() {
                    @Override
                    public InputStream openStream() throws IOException {
                        return new SmbFileInputStream(smbFile);
                    }

                    @Override
                    public long length() throws IOException {
                        return smbFile.length();
                    }
                }, bufferConfig);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bmds;
    }

    private static int getReadBufferSize(SmbFile smbFile, boolean useRandomAccess) {
        int[] readSizeFile = {0};
        // Network access needs to be on a different thread or it will throw an Exception
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (useRandomAccess) {
                        SmbRandomAccessFile smbRandomAccessFile = new SmbRandomAccessFile(smbFile, "r");
                        Field field = SmbRandomAccessFile.class.getDeclaredField("readSize");
                        field.setAccessible(true);
                        readSizeFile[0] = (int)field.get(smbRandomAccessFile);
                        smbRandomAccessFile.close();
                    } else {
                        SmbFileInputStream smbFileInputStream = new SmbFileInputStream(smbFile);
                        Field field = SmbFileInputStream.class.getDeclaredField("readSizeFile");
                        field.setAccessible(true);
                        readSizeFile[0] = (int)field.get(smbFileInputStream);
                        smbFileInputStream.close();
                    }
                } catch (IllegalAccessException | NoSuchFieldException | IOException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return readSizeFile[0];
    }
}
