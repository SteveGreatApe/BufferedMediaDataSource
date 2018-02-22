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

import com.greatape.bmds.smb.NetworkDirTask;
import com.greatape.bmds.smb.SmbTestConfig;
import com.greatape.bmds.smb.SmbUtil;
import com.greatape.bmds.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import jcifs.smb.SmbFile;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static junit.framework.Assert.fail;

/**
 * @author Steve Townsend
 */
@RunWith(AndroidJUnit4.class)
public class SpeedTest {
    private static final String TAG_SPEED_TEST = "SpeedTest";

    private LogFile mLogFile;

    @Before
    public void grantInternetPermission() {
        grantPermission("INTERNET");
        grantPermission("READ_EXTERNAL_STORAGE");
        grantPermission("WRITE_EXTERNAL_STORAGE");
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
        mLogFile.close();
    }

    @Test
    public void testNetworkReadSpeed() throws Exception {
        Results results = new Results("Network speed sequential only");
        doTestNetworkReadSpeed(true, results);
        results.writeToLog("Speed-Sequential");
    }

    @Test
    public void testNetworkReadSpeedRandom() throws Exception {
        Results results = new Results("Network speed with Random reads");
        doTestNetworkReadSpeed(false, results);
        results.writeToLog("Speed-Random");
    }

    private void doTestNetworkReadSpeed(boolean sequential, Results results) throws Exception {
//        BmdsLog.enableDebug(true);
        final int NumIterations = 20; // Use a large number to gather meaningful results
        for(int iteration = 0; iteration < NumIterations; iteration++) {
            String logName = "SpeedTest";
            if (NumIterations > 1) {
                logName += String.format("%02d", iteration);
            }
            mLogFile = new LogFile(logName, LogFile.Mode.Unique);
            NetworkDirTask.FileListEntry[] smbFiles = NetworkDirTask.syncFetch(SmbTestConfig.networkPath());
            if (smbFiles != null) {
                for (NetworkDirTask.FileListEntry listEntry : smbFiles) {
                    SmbFile smbFile = listEntry.file;
                    doSmbFileSpeedTest(smbFile, false, true, null);
                    doSmbFileSpeedTest(smbFile, true, sequential, results);
                    doSmbFileSpeedTest(smbFile, false, sequential, results);
                }
            } else {
                fail("No test videos found");
            }
        }
    }

    private void doSmbFileSpeedTest(SmbFile smbFile, boolean useRandomAccess, boolean sequential, Results results) throws IOException {
        BufferedMediaDataSource bmds = SmbUtil.createBufferedMediaDataSource(smbFile, useRandomAccess);
        long length = bmds.getSize();
        Log.d(TAG_SPEED_TEST, "Testing: " + smbFile.getName() + " " + Utils.formatFileSize(length) + " Random: " + useRandomAccess + " Sequential Only:" + sequential);
        final int NumProgressSteps = 10;
        int nextLogStep = 0;
        long nextLogPos = 0;
        long startTime = System.currentTimeMillis();
        byte[] buffer = new byte[bmds.getBufferSize()];
        Random random = null;
        if (!sequential) {
            // Use a repeatable sequence per file
            random = new Random(smbFile.hashCode());
        }
        for(long pos = 0; pos < length; ) {
            if (pos >= nextLogPos) {
                if (nextLogPos > 0) { // Don't log 0%, just setup nextLogPos
                    int progress = nextLogStep * 100 / NumProgressSteps;
                    Log.d(TAG_SPEED_TEST, "Progress: " + progress + "% " + Utils.formatDuration(System.currentTimeMillis() - startTime));
                }
                nextLogStep++;
                nextLogPos = length * nextLogStep / NumProgressSteps;
            }
            int len = (int)Math.min(buffer.length, length - pos);
            bmds.readAt(pos, buffer, 0, len);
            if (!sequential) {
                // Also do a random read from anywhere upto the sequential read point
                long randomReadPos = (long)(pos * random.nextFloat());
                int randomReadlen = (int)Math.min(buffer.length, length - randomReadPos);
                bmds.readAt(randomReadPos, buffer, 0, randomReadlen);
            }
            pos += len;
        }
        long duration = System.currentTimeMillis() - startTime;
        if (results != null) {
            ResultSet resultSet = results.getResultSet(smbFile.getName(), length, useRandomAccess);
            resultSet.add(duration);
            mLogFile.log(smbFile.getName() + ":" +  useRandomAccess + ":" + sequential + ":" + length + ":" + duration);
        }
        Log.d(TAG_SPEED_TEST, "Time: " + Utils.formatDuration(duration));
        bmds.close();
    }

    private void grantPermission(String permission) {
        // In M+ some permissions needs to show a dialog to grant the permission, run this so
        // the permission is granted before running these tests.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StringBuilder stringBuilder = new StringBuilder("pm grant ");
            stringBuilder.append(getInstrumentation().getTargetContext().getPackageName());
            stringBuilder.append(" android.permission.");
            stringBuilder.append(permission);
            getInstrumentation().getUiAutomation().executeShellCommand(stringBuilder.toString());
        }
    }

    private class ResultSet {
        String fileName;
        long fileSize;
        boolean randomAccess;
        List<Long> times;

        ResultSet(String fileName, long fileSize, boolean randomAccess) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.randomAccess = randomAccess;
            times = new ArrayList<>();
        }

        void add(long duration) {
            times.add(duration);
        }

        void writeToLog(LogFile logFile, boolean sorted) {
            List<Long> resultTimes = times;
            if (sorted) {
                Collections.sort(resultTimes);
            }
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(fileName);
            stringBuilder.append(",");
            stringBuilder.append(fileSize);
            stringBuilder.append(",");
            stringBuilder.append(randomAccess);
            for(Long time : resultTimes) {
                stringBuilder.append(",");
                stringBuilder.append(time);
            }
            logFile.log(stringBuilder.toString());
        }
    }


    private class Results {
        String title;
        List<ResultSet> resultSets;

        Results(String title) {
            this.title = title;
            resultSets = new ArrayList<>();
        }

        ResultSet getResultSet(String name, long length, boolean randomAccess) {
            for(ResultSet resultSet : resultSets) {
                if (resultSet.fileName.equals(name) && resultSet.randomAccess == randomAccess) {
                    return resultSet;
                }
            }
            ResultSet newResultSet = new ResultSet(name, length, randomAccess);
            resultSets.add(newResultSet);
            return newResultSet;
        }

        void writeToLog(String logFileName) {
            LogFile logFile = new LogFile(logFileName, LogFile.Mode.Unique);
            logFile.log(title);
            for(ResultSet resultSet : resultSets) {
                resultSet.writeToLog(logFile, false);
            }
            logFile.log("Ordered results");
            for(ResultSet resultSet : resultSets) {
                resultSet.writeToLog(logFile, true);
            }
            logFile.close();
        }
    }
}
