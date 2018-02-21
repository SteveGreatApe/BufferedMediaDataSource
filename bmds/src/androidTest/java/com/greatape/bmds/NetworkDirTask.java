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

import android.os.AsyncTask;
import android.text.TextUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.Semaphore;

import jcifs.CIFSContext;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * @author Steve Townsend
 */
public class NetworkDirTask extends AsyncTask<String, Void, SmbFile[]> {
    private CIFSContext mCifsContext;
    private Listener mListener;
    private IOException mException;

    static SmbFile[] syncFetch(CIFSContext cifsContext, String workgroup, String networkPath, String username, String password) throws Exception {
        final SmbFile[][] smbFiles = {null};
        Semaphore semaphore = new Semaphore(0);
        final Exception[] smbException = {null};
        NetworkDirTask networkDirTask = new NetworkDirTask(cifsContext, (files, exception) -> {
            semaphore.release();
            smbFiles[0] = files;
            smbException[0] = exception;
        });
        networkDirTask.execute(workgroup, networkPath, username, password);
        semaphore.acquire();
        if (smbException[0] != null) {
            throw smbException[0];
        }
        return smbFiles[0];
    }

    interface Listener {
        void fileList(SmbFile[] files, IOException exception);
    }

    private NetworkDirTask(CIFSContext cifsContext, Listener listener) {
        mCifsContext = cifsContext;
        mListener = listener;
    }

    @Override
    protected SmbFile[] doInBackground(String... params) {
        // Extract parameters and get the video list
        String username = params[2];
        CIFSContext cifsContext = mCifsContext;
        if (!TextUtils.isEmpty(username)) {
            String workgroup = params[0];
            String password = params[3];
            NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(cifsContext, workgroup, username, password);
            cifsContext = cifsContext.withCredentials(auth);
        }
        SmbFile[] files = null;
        try {
            String path = params[1];
            SmbFile smbFolder = new SmbFile(path, cifsContext);
            files = smbFolder.listFiles();
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
