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

package com.greatape.bmdsapp.smb;

import com.greatape.bmds.BufferedMediaDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Properties;

import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

/**
 * @author Steve Townsend
 */
public class SmbUtil {
    private static CIFSContext sCifsContext1 = createContext(false);
    private static CIFSContext sCifsContext2 = createContext(true);

    public static BufferedMediaDataSource createBufferedMediaDataSource(SmbFile smbFile, boolean useRandomAccess) {
        BufferedMediaDataSource.BufferConfig bufferConfig = new BufferedMediaDataSource.BufferConfig();
        int bufferSize = getReadBufferSize(smbFile);
        if (bufferSize > 0) {
            int totalBufferSize = bufferConfig.bufferSize * bufferConfig.maxUsedBuffers;
            int totalCacheAheadSize = bufferConfig.bufferSize * bufferConfig.cacheAheadCount;
            bufferConfig.bufferSize = bufferSize;
            bufferConfig.maxUsedBuffers = totalBufferSize / bufferConfig.bufferSize;
            bufferConfig.cacheAheadCount = totalCacheAheadSize / bufferConfig.bufferSize;
        }
        BufferedMediaDataSource bmds = null;
        try {
            if (useRandomAccess) {
                bmds = new SmbBufferedMediaDataSource(smbFile, bufferConfig);
            } else {
                bmds = new BufferedMediaDataSource(new BufferedMediaDataSource.StreamCreator() {
                    @Override
                    public InputStream openStream() throws IOException {
                        return new SmbFileInputStream(smbFile);
                    }

                    @Override
                    public long length() throws IOException {
                        return smbFile.length();
                    }

                    @Override
                    public String typeName() {
                        return "SmbFile";
                    }
                }, bufferConfig);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bmds;
    }

    private static int getReadBufferSize(SmbFile smbFile) {
        return smbFile.getContext().getConfig().getReceiveBufferSize();
    }

    static CIFSContext baseContext(boolean smb2) {
        return smb2 ? sCifsContext2 : sCifsContext1;
    }

    private static CIFSContext createContext(boolean smb2) {
        Properties prop = new Properties();
        prop.putAll(System.getProperties());
        // Increased buffer size gives improved performance
        prop.setProperty("jcifs.smb.client.rcv_buf_size", "262144");
        prop.setProperty("jcifs.smb.client.enableSMB2", String.valueOf(smb2));
        PropertyConfiguration propertyConfiguration = null;
        try {
            propertyConfiguration = new PropertyConfiguration(prop);
        } catch (CIFSException e) {
            e.printStackTrace();
        }
        return new BaseContext(propertyConfiguration);
    }

    static boolean isRootOrWorkgroup(String path) {
        boolean isRootOrWorkgroup = false;
        try {
            SmbFile smbFile = new SmbFile(path, baseContext(false));
            int type = getType(smbFile);
            // Note: TYPE_WORKGROUP is also returned for the root
            if (type == SmbFile.TYPE_WORKGROUP) {
                isRootOrWorkgroup = true;
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return isRootOrWorkgroup;
    }

    static int getType(SmbFile smbFile) {
        // TODO: Workaround for issue where jcifs-ng throws SmbAuthException when getting type of password protected Share
        int type;
        try {
            type = smbFile.getType();
        } catch(SmbAuthException authE) {
            type = SmbFile.TYPE_SHARE;
        } catch (SmbException e) {
            type = SmbFile.TYPE_FILESYSTEM;
        }
        return(type);
    }
}
