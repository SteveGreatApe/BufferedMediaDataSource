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

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * @author Steve Townsend
 */
public class NetworkDirTask extends AsyncTask<String, Void, SmbFile[]> {
    private Listener mListener;
    private IOException mException;

    static SmbFile[] syncFetch(String workgroup, String networkPath, String username, String password) throws Exception {
        final SmbFile[][] smbFiles = {null};
        Semaphore semaphore = new Semaphore(0);
        final Exception[] smbException = {null};
        NetworkDirTask networkDirTask = new NetworkDirTask((files, exception) -> {
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

    NetworkDirTask(Listener listener) {
        mListener = listener;
    }

    @Override
    protected SmbFile[] doInBackground(String... params) {
        // Extract parameters and get the video list
        String workgroup = params[0];
        String path = params[1];
        String username = params[2];
        String password = params[3];
        NtlmPasswordAuthentication auth = null;
        if (!TextUtils.isEmpty(username)) {
            auth = new NtlmPasswordAuthentication(workgroup, username, password);
        }
        SmbFile[] files = null;
        try {
            SmbFile smbFolder = new SmbFile(path, auth);
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
