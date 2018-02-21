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

import org.junit.Test;

import java.net.MalformedURLException;

import jcifs.CIFSContext;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Steve Townsend
 */
public class SmbFileTest {
/*
These tests aren't really part of BMDS, but they are useful for understanding what does or doesn't
work in the JCIFS version under test.
JCIFS-NG is currently under development and we need to work around it's deficiencies for now, so
these tests understand exactly what those are.
 */

    @Test
    public void rootTestSmb1() {
        doRootTest(false);
    }

    @Test
    public void rootTestSmb2() {
        doRootTest(true);
    }

    @Test
    public void workgroupTestSmb1() {
        doWorkgroupTest(false);
    }

    @Test
    public void workgroupTestSmb2() {
        doWorkgroupTest(true);
    }

    @Test
    public void serverIsDirectorySmb1() {
        doIsDirectory(false, false);
    }

    @Test
    public void serverIsDirectorySmb2() {
        doIsDirectory(true, false);
    }

    @Test
    public void serverIsDirectoryCloneSmb1() {
        doIsDirectory(false, true);
    }

    @Test
    public void serverIsDirectoryCloneSmb2() {
        doIsDirectory(true, true);
    }

    @Test
    public void listEuroapeSmb1() {
        doListEuroape(false);
    }

    @Test
    public void listEuroapeSmb2() {
        doListEuroape(true);
    }

//    @Test
//    public void testServerEnumeration() throws SocketException {
//        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
//        for (NetworkInterface netint : Collections.list(nets)) {
//            Log.d("ZZZ", "Display name: " + netint.getDisplayName());
//            Log.d("ZZZ", "Name: " + netint.getName());
//            Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
//            for (InetAddress inetAddress : Collections.list(inetAddresses)) {
//                Log.d("ZZZ", "InetAddress: " + inetAddress);
//            }
//        }
//    }

    private void doRootTest(boolean smb2) {
        CIFSContext cifsContext = SmbUtil.baseContext(smb2);
        try {
            SmbFile smbFolder = new SmbFile("smb://", cifsContext);
            SmbFile[] smbFiles = smbFolder.listFiles();
            assertEquals(1, smbFiles.length);
            assertEquals("smb://WORKGROUP/", smbFiles[0].getPath());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    private void doWorkgroupTest(boolean smb2) {
        CIFSContext cifsContext = SmbUtil.baseContext(smb2);
        try {
            SmbFile smbFolder = new SmbFile("smb://WORKGROUP", cifsContext);
            SmbFile[] smbFiles = smbFolder.listFiles();
            assertEquals(3, smbFiles.length);
            assertEquals("smb://EUROAPE/", smbFiles[0].getPath());
            assertEquals("smb://MINTY/", smbFiles[1].getPath());
            assertEquals("smb://SILENTAPE/", smbFiles[2].getPath());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    private void doIsDirectory(boolean smb2, boolean makeClone) {
        CIFSContext cifsContext1 = SmbUtil.baseContext(false);
        SmbFile[] smbFiles = null;
        try {
            SmbFile smbFolder = new SmbFile("smb://WORKGROUP", cifsContext1);
            smbFiles = smbFolder.listFiles();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        assertNotNull(smbFiles);
        boolean[] results = new boolean[smbFiles.length];
        CIFSContext cifsContext2 = SmbUtil.baseContext(smb2);
        for(int index = 0; index < smbFiles.length; index++) {
            try {
                SmbFile isDir;
                if (makeClone) {
                    isDir = new SmbFile(smbFiles[index].getPath(), cifsContext2);
                } else {
                    isDir = smbFiles[index];
                }
                results[index] = isDir.isDirectory();
            } catch (SmbException e) {
                e.printStackTrace();
                results[index] = false;
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        for(int index = 0; index < results.length; index++) {
            assertTrue(results[index]);
        }
    }

    private void doListEuroape(boolean smb2) {
        CIFSContext cifsContext1 = SmbUtil.baseContext(false);
        SmbFile[] smbFiles = null;
        try {
            SmbFile smbFolder = new SmbFile("smb://WORKGROUP", cifsContext1);
            smbFiles = smbFolder.listFiles();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        assertNotNull(smbFiles);
        for (SmbFile smbFile : smbFiles) {
            if (smbFile.getName().equals("EUROAPE")) {
                try {
                    CIFSContext cifsContext2 = SmbUtil.baseContext(smb2);
                    NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(cifsContext2, "WORKGROUP", "steve", "oo-two");
                    cifsContext2 = cifsContext2.withCredentials(auth);
                    SmbFile clone = new SmbFile(smbFile.getPath(), cifsContext2);
                    SmbFile[] topFiles = clone.listFiles();
                    assertNotNull(topFiles);
                } catch (SmbException | MalformedURLException e) {
                    e.printStackTrace();
                    fail(e.getLocalizedMessage());
                }
            }
        }
    }
//    @Test
//    public void aTest1() throws MalformedURLException, SmbException {
//        CIFSContext cifsContext = SmbUtil.baseContext(false);
//        SmbFile smbFolder = new SmbFile("smb://WORKGROUP", cifsContext);
//        SmbFile[] smbFiles = smbFolder.listFiles();
//        smbFiles[0].isDirectory();
//    }
//
//    @Test
//    public void aTest2() throws MalformedURLException, SmbException {
//        CIFSContext cifsContext = SmbUtil.baseContext(false);
//        SmbFile smbFolder = new SmbFile("smb://WORKGROUP", cifsContext);
//        SmbFile[] smbFiles = smbFolder.listFiles();
//        SmbFile clone = new SmbFile(smbFiles[0].getPath(), cifsContext);
//        clone.isDirectory();
//    }
}
