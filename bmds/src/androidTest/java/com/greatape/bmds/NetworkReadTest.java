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
import com.greatape.bmds.smb.SmbUtil;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

import static junit.framework.Assert.assertTrue;

/**
 * @author Steve Townsend
 */
@RunWith(AndroidJUnit4.class)
public class NetworkReadTest {
    private static final String TAG = "NetworkReadTest";

    @Before
    public void grantInternetPermission() {
        BmdsTestUtils.grantPermission("INTERNET");
    }

    @Test
    public void testNetworkPlay() throws Exception {
        NetworkDirTask.FileListEntry[] smbFiles = NetworkDirTask.syncFetch(SmbTestConfig.networkPath());
        if (smbFiles != null) {
            List<SmbTestInstance> instances = new ArrayList<>();
            for (NetworkDirTask.FileListEntry listEntry : smbFiles) {
                SmbFile smbFile = listEntry.file;
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
                int totalRead = 0;
                do {
                    int read = bis.read(contents1, totalRead, contents1.length - totalRead);
                    assertTrue("Failure reading BufferedInputStream, read=" + read, read > 0);
                    totalRead += read;
                } while(totalRead < contents1.length);
                bis.close();
                Log.d(TAG, "Comparing buffers");
                BmdsTestUtils.assertBuffersEqual("Network play buffer compare", contents1, testInstance.buffer);
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
            bmds = SmbUtil.createBufferedMediaDataSource(smbFile, useDataInput);
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
