/* Copyright 2018 Great Ape Software Ltd
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

package com.greatape.bmdsapp.smb;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import jcifs.CIFSContext;
import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbFile;

/**
 * @author Steve Townsend
 */
public class SmbFileList implements NetworkDirTask.Listener {
    private static final String PATH_PREF = "path";
    private static final String WORKGROUP_PREF = "wg";

    private final SharedPreferences mPreferences;
    private String mWorkGroup;
    private SmbFile mParentFolder;
    private UI mUi;
    private String mFilteredMimeType;

    public interface UI {
        void getUserNamePassword(final SmbFile smbFile);
        void loading(String path);
        void updateFileList(List<NetworkDirTask.FileListEntry> fileListEntries, Exception exception);
    }

    public SmbFileList(Context context, UI ui) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mUi = ui;
        mWorkGroup = mPreferences.getString(WORKGROUP_PREF, null);
    }

    public void requestStartFileList() {
        String path = mPreferences.getString(PATH_PREF, null);
        if (path == null) {
            path = "smb://";
        }
        mUi.loading(path);
        NetworkDirTask networkDirTask = new NetworkDirTask(path, this);
        networkDirTask.execute(mWorkGroup, null, null);
    }

    public void requestFileList(SmbFile smbFile, String username, String password) {
        mUi.loading(smbFile.getPath());
        NetworkDirTask networkDirTask = new NetworkDirTask(smbFile, this);
        networkDirTask.execute(mWorkGroup, username, password);
    }

    public void setFilteredMimeType(String filteredMimeType) {
        mFilteredMimeType = filteredMimeType;
    }

    public SmbFile getParentFolder() {
        return mParentFolder;
    }

    @Override
    public void fileList(NetworkDirTask.FileListEntry parent, NetworkDirTask.FileListEntry[] files, IOException exception) {
        try {
            String parentName = parent.file.getParent();
            CIFSContext cifsContext;
            // For levels above Server we need to use a CIFSContext with smb2 disabled
            if (parent.type == SmbFile.TYPE_SERVER) {
                cifsContext = SmbUtil.baseContext(false);
                storeWorkGroup(parent.file.getName());
            } else {
                cifsContext = parent.file.getContext();
            }
            mParentFolder = new SmbFile(parentName, cifsContext);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            mParentFolder = null;
        }

        if (exception != null && exception.getClass().equals(SmbAuthException.class)) {
            mUi.getUserNamePassword(parent.file);
            return;
        }
        // Store last loaded path to return next time we run
        storePreference(PATH_PREF, parent.file.getPath());

        ArrayList<NetworkDirTask.FileListEntry> fileListEntries = new ArrayList<>();
        if (files != null) {
            for (NetworkDirTask.FileListEntry listFile : files) {
                boolean addToList;
                if (listFile.isHidden || listFile.file.getName().startsWith("print$")) {
                    addToList = false;
                } else if (listFile.type == SmbFile.TYPE_WORKGROUP || listFile.type == SmbFile.TYPE_SHARE) {
                    addToList = true;
                } else if (listFile.type == SmbFile.TYPE_SERVER) {
                    addToList = true;
                    // For Server enumeration we needed to use a context with smb2 disabled.
                    // But for the levels below the server we need to switch to a context with smb2 enabled
                    try {
                        listFile.file = new SmbFile(listFile.file.getPath(), SmbUtil.baseContext(true));
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                } else if (listFile.type == SmbFile.TYPE_FILESYSTEM) {
                    if (listFile.isDirectory || mFilteredMimeType == null) {
                        addToList = true;
                    } else {
                        String mimeType = URLConnection.guessContentTypeFromName(listFile.file.getName());
                        addToList = mimeType != null && mimeType.startsWith(mFilteredMimeType);
                    }
                } else {
                    addToList = false;
                }
                if (addToList) {
                    fileListEntries.add(listFile);
                }
            }
        }
        mUi.updateFileList(fileListEntries, exception);
    }

    private void storeWorkGroup(String workgroup) {
        workgroup = workgroup.replace("/", "");
        if (!workgroup.equals("smb:")) {
            mWorkGroup = workgroup;
            storePreference(WORKGROUP_PREF, mWorkGroup);
        }
    }

    private void storePreference(String key, String value) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }
}
