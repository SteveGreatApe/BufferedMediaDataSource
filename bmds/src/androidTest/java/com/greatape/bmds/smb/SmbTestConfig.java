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

package com.greatape.bmds.smb;

import android.util.Log;

import junit.framework.Assert;

import java.net.MalformedURLException;

import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * @author Steve Townsend
 */
public class SmbTestConfig {
    // Set NETWORK_PATH to point to a publicly available folder on your network, then copy the test
    // files from assets/TestVideos into the folder.
    private static final String NETWORK_PATH = "smb://SILENTAPE/Public/BmdsTest/";
    private static final String TAG = "SmbTestConfig";

    private static boolean pathVerified = false;

    public static String networkPath() {
        if (!pathVerified) {
            verifyPath();
        }
        return NETWORK_PATH;
    }

    private static void verifyPath() {
        String error = null;
        try {
            SmbFile smbFile = new SmbFile(NETWORK_PATH, SmbUtil.baseContext(true));
            if (!smbFile.exists()) {
                error = "does not exist.";
            } if (!smbFile.isDirectory()) {
                error = "is not a directory.";
            } else {
                SmbFile[] files = smbFile.listFiles();
                if (files.length > 0) {
                    pathVerified = true;
                } else {
                    error = " is empty.";
                }
            }
        } catch (SmbAuthException auth) {
            error = "access as not authorised.";
        } catch (MalformedURLException | SmbException e) {
            error = "triggered: " + e.getMessage();
            e.printStackTrace();
        }
        if (error != null) {
            error = "Specified path '" + NETWORK_PATH + "' " + error;
            Log.e(TAG, "Set SmbTestConfig.NETWORK_PATH to point to a publicly available folder on your network, then copy the test files from assets/TestVideos into the folder.");
            Assert.fail(error);
        }
    }
}
