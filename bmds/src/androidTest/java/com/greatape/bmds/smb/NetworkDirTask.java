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

import android.os.AsyncTask;
import android.text.TextUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.Semaphore;

import jcifs.CIFSContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * @author Steve Townsend
 */
public class NetworkDirTask extends AsyncTask<String, Void, NetworkDirTask.FileListEntry[]> {
    private NetworkDirTask.Listener mListener;
    private IOException mException;
    private String mPath;
    private SmbFile mSmbFile;
    private FileListEntry mParentEntry;

    interface Listener {
        void fileList(NetworkDirTask.FileListEntry parent, FileListEntry[] files, IOException exception);
    }

    NetworkDirTask(SmbFile smbFile, NetworkDirTask.Listener listener) {
        mSmbFile = smbFile;
        mListener = listener;
    }

    NetworkDirTask(String path, NetworkDirTask.Listener listener) {
        mPath = path;
        mListener = listener;
    }

    public static FileListEntry[] syncFetch(String networkPath) throws Exception {
        final FileListEntry[][] smbFiles = {null};
        Semaphore semaphore = new Semaphore(0);
        final Exception[] smbException = {null};
        NetworkDirTask networkDirTask = new NetworkDirTask(networkPath, new NetworkDirTask.Listener() {
            @Override
            public void fileList(FileListEntry parent, FileListEntry[] files, IOException exception) {
                smbFiles[0] = files;
                smbException[0] = exception;
                semaphore.release();
            }
        });
        networkDirTask.execute(null, null, null);
        semaphore.acquire();
        if (smbException[0] != null) {
            throw smbException[0];
        }
        return smbFiles[0];
    }

    @Override
    protected FileListEntry[] doInBackground(String... params) {
        String workgroup = params[0];
        String username = params[1];
        String password = params[2];
        FileListEntry[] ret = null;
        try {
            ret = doListFiles(workgroup, username, password);
        } catch (MalformedURLException | SmbException e) {
            mException = e;
        }
        return ret;
    }

    private FileListEntry[] doListFiles(String workgroup, String username, String password) throws SmbException, MalformedURLException {
        if (!TextUtils.isEmpty(username)) {
            NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(workgroup, username, password);
            CIFSContext cifsContext = SmbUtil.baseContext(true).withCredentials(auth);
            mSmbFile = new SmbFile(mPath != null ? mPath : mSmbFile.getPath(), cifsContext);
        } else if (mSmbFile == null) {
            mSmbFile = new SmbFile(mPath, SmbUtil.baseContext(!SmbUtil.isRootOrWorkgroup(mPath)));
        }
        mParentEntry = new FileListEntry(mSmbFile, true);
        SmbFile[] smbFiles = mSmbFile.listFiles();
        FileListEntry[] files = new FileListEntry[smbFiles.length];
        for(int index = 0; index < smbFiles.length; index++) {
            SmbFile smbFile = smbFiles[index];
            boolean isDirectory = true;
            // TODO: isDirectory() leaves with SmbAuthException on a password protected folder.
            // So use this workaround for now, hopeful the behaviour will be fixed later.
            try {
                isDirectory = smbFile.isDirectory();
            } catch(SmbAuthException ignore) {
            } catch(SmbException e) {
                // Can throw SmbException "The parameter is incorrect.")
                isDirectory = false;
            }
            files[index] = new FileListEntry(smbFile, isDirectory);
        }
        return files;
    }

    @Override
    protected void onPostExecute(FileListEntry[] files) {
        mListener.fileList(mParentEntry, files, mException);
    }

    public static class FileListEntry {
        public SmbFile file;
        public boolean isDirectory;
        public boolean isHidden;
        public int type;

        FileListEntry(SmbFile file, boolean isDirectory) {
            this.file = file;
            this.isDirectory = isDirectory;
            this.type = SmbUtil.getType(file);
            try {
                this.isHidden = (file.getAttributes() & SmbFile.ATTR_HIDDEN) != 0;
            } catch (SmbException ignore) {
            }
        }
    }
}
