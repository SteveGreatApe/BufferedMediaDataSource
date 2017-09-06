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

import android.os.AsyncTask;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLConnection;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.util.LogStream;

/**
 * @author Steve Townsend
 */
public class NetworkDirTask extends AsyncTask<String, Void, SmbFile[]> {
    private static final String MIME_VIDEO = "video/";

    private Listener mListener;
    private IOException mException;

    interface Listener {
        void fileList(SmbFile[] files, IOException exception);
    }

    NetworkDirTask(Listener listener) {
        mListener = listener;
    }

    @Override
    protected SmbFile[] doInBackground(String... params) {
//        LogStream.setLevel(3); // This is very for debugging any network issues
        // TODO: How can we can make a generic setup that works well on all networks? It's not at
        // all good that we need this sort of tweak to make JCIFS perform properly.
        jcifs.Config.setProperty("jcifs.smb.client.dfs.disabled", "true");

        // Extract parameters and get the video list
        String workgroup = params[0];
        String path = params[1];
        String username = params[2];
        String password = params[3];
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(workgroup, username, password);
        SmbFile[] files = null;
        try {
            SmbFile smbFolder = new SmbFile(path, auth);
            files = smbFolder.listFiles((smbPath, name) -> {
                String mimeType = URLConnection.guessContentTypeFromName(name);
                return mimeType != null && mimeType.startsWith(MIME_VIDEO);
            });
        } catch (MalformedURLException | SmbException e) {
            mException = e;
        }
        return files;
    }

    @Override
    protected void onPostExecute(SmbFile[] files) {
        mListener.fileList(files, mException);
    }
}
