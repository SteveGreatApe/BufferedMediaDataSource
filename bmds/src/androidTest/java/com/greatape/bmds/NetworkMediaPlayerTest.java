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
    private static final String NETWORK_PATH = "smb://EUROAPE/Users/Public/Videos/";

    @Before
    public void grantInternetPermission() {
        grantPermission("INTERNET");
    }

    @Test
    public void testNetworkPlay() throws Exception {
        initSmbConfig();
        SmbFile[] smbFiles = NetworkDirTask.syncFetch("", NETWORK_PATH, "", "");
        if (smbFiles != null) {
            for(SmbFile smbFile : smbFiles) {
                String mimeType = URLConnection.guessContentTypeFromName(smbFile.getName());
                if (mimeType != null && mimeType.startsWith(MIME_VIDEO)) {
                    doSmbFileTest(smbFile, false);
                    doSmbFileTest(smbFile, true);
                }
            }
        } else {
            fail("Test Video folder not found, please edit NETWORK_PATH to point to a folder containing videos to test.");
        }
    }

    private void initSmbConfig() {
//        LogStream.setLevel(3);
        // TODO: How can we can make a generic setup that works well on all networks? It's not at
        // all good that we need this sort of tweak to make JCIFS perform properly.
        // Note: With large buffer fix we can get away without this, but it is still potentially
        // an unnecessary performance hit.
//        jcifs.Config.setProperty("jcifs.smb.client.dfs.disabled", "true");
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
            });
        }
        try {
            doTest(bufferedMediaDataSource);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
