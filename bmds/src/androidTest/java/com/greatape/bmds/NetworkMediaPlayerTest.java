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

import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.greatape.bmds.smb.NetworkDirTask;
import com.greatape.bmds.smb.SmbTestConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbRandomAccessFile;

import static junit.framework.Assert.fail;

/**
 * @author Steve Townsend
 */
@RunWith(AndroidJUnit4.class)
public class NetworkMediaPlayerTest extends MediaPlayerTest {

    @Before
    public void grantInternetPermission() {
        grantPermission("INTERNET");
    }

    @Test
    public void testNetworkPlay() throws Exception {
        initSmbConfig();
        NetworkDirTask.FileListEntry[] smbFiles = NetworkDirTask.syncFetch(SmbTestConfig.networkPath());
        if (smbFiles != null) {
            for(NetworkDirTask.FileListEntry listEntry : smbFiles) {
                SmbFile smbFile = listEntry.file;
                String mimeType = URLConnection.guessContentTypeFromName(smbFile.getName());
                if (mimeType != null && mimeType.startsWith(MIME_VIDEO)) {
                    if (smbFile.getName().contains("A720")) {
                        Log.d(TAG, "Skipping test for: " + smbFile);
                        continue;
                    }
                    doSmbFileTest(smbFile, true);
                    doSmbFileTest(smbFile, false);
                }
            }
        } else {
            fail("Test Video folder not found, please edit NETWORK_PATH to point to a folder containing videos to test.");
        }
    }

    private void initSmbConfig() {
//        BmdsLog.enableDebug(true);
//        LogStream.setLevel(3);
    }

    private void doSmbFileTest(final SmbFile smbFile, boolean useDataInput) throws IOException {
        logTestingTitle(smbFile.getCanonicalPath());
        BufferedMediaDataSource bufferedMediaDataSource;
        if (useDataInput) {
            bufferedMediaDataSource = new BufferedMediaDataSource(new BufferedMediaDataSource.DataInputCreator() {
                @Override
                public DataInput openDataInput() throws IOException {
                    return new SmbRandomAccessFile(smbFile, "r");
                }

                @Override
                public void closeDataInput(DataInput dataInput) throws IOException {
                    ((SmbRandomAccessFile)dataInput).close();
                }

                @Override
                public void readData(DataInput dataInput, byte[] buffer, int readLen) throws IOException {
                    ((SmbRandomAccessFile)dataInput).read(buffer, 0, readLen);
                }

                @Override
                public void seek(DataInput dataInput, long seekPos) throws IOException {
                    ((SmbRandomAccessFile)dataInput).seek(seekPos);
                }

                @Override
                public long length() throws IOException {
                    return smbFile.length();
                }

                @Override
                public String typeName() {
                    return "SmbFile";
                }
            });
        } else {
            bufferedMediaDataSource = new BufferedMediaDataSource(new BufferedMediaDataSource.StreamCreator() {

                @Override
                public InputStream openStream() throws IOException {
                    return new SmbFileInputStream(smbFile);
                }

                @Override
                public long length() throws IOException {
                    return smbFile.length();
                }

                @Override
                public String typeName() {
                    return "SmbFile";
                }
            });
        }
        try {
            doTest(bufferedMediaDataSource);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
