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
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbRandomAccessFile;

import static android.support.test.InstrumentationRegistry.getInstrumentation;

/**
 * @author Steve Townsend
 */
@RunWith(AndroidJUnit4.class)
public class NetworkReadTest {
    private static final String NETWORK_PATH = "smb://EUROAPE/Users/Public/Videos/";
    private static final String TAG = "NetworkReadTest";

    @Before
    public void grantInternetPermission() {
        SmbUtil.grantPermission("INTERNET");
    }

    @Test
    public void testNetworkPlay() throws Exception {
        SmbFile[] smbFiles = NetworkDirTask.syncFetch(SmbUtil.baseContext(true),"", NETWORK_PATH, "", "");
        if (smbFiles != null) {
            List<SmbTestInstance> instances = new ArrayList<>();
            for (SmbFile smbFile : smbFiles) {
                if (!smbFile.isFile()) {
                    Log.d(TAG, "Skipping: " + smbFile.getName());
                    continue;
                }
                Log.d(TAG, "Testing: " + smbFile.getName());
                SmbTestInstance testInstance = new SmbTestInstance(smbFile, true);
                instances.add(testInstance);
                testInstance.run();
            }
            for(SmbTestInstance testInstance : instances) {
                testInstance.waitForLoad();
                byte[] contents1 = new byte[testInstance.smbFile.getContentLength()];
                SmbFileInputStream fileIn = new SmbFileInputStream(testInstance.smbFile);
                BufferedInputStream bis = new BufferedInputStream(fileIn);
                bis.read(contents1, 0, contents1.length);
                bis.close();
                Assert.assertEquals(contents1.length, testInstance.buffer.length);
                Log.d(TAG, "Comparing buffers");
                Assert.assertTrue(Arrays.equals(contents1, testInstance.buffer));
                Log.d(TAG, "Finished buffer compare");
            }
        }
    }

    private static class SmbTestInstance {
        SmbFile smbFile;
        BufferedMediaDataSource bmds;
        byte[] buffer;
        Thread thread;

        SmbTestInstance(SmbFile smbFile, boolean useDataInput) throws IOException {
            this.smbFile = smbFile;
            this.buffer = new byte[(int)smbFile.length()];
            bmds = SmbUtil.createBufferedMediaDataSource(smbFile, useDataInput, 0);
        }

        void run() {
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        long fileSize = bmds.getSize();
                        Log.d(TAG, "Reading: " + smbFile.getName() + " Length=" + fileSize);
                        int readPos = 0;
                        while(readPos < fileSize) {
                            int read = bmds.readAt(readPos, buffer, readPos, (int)(fileSize - readPos));
                            readPos += read;
                            Log.d(TAG, "..." + smbFile.getName() + " " + (readPos * 100 / fileSize) + "%");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        Assert.fail(e.getMessage());
                    }
                    Log.d(TAG, "Finished reading: " + smbFile.getName());
                }
            });
            thread.start();
        }

        void waitForLoad() throws InterruptedException, IOException {
            Log.d(TAG, "Waiting for thread: " + smbFile.getName());
            thread.join(0);
            Log.d(TAG, "Thread join complete: " + smbFile.getName());
            bmds.close();
            Log.d(TAG, "BMDS closed: " + smbFile.getName());
        }
    }
}
