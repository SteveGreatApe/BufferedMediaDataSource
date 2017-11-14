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

import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Steve Townsend
 */
class LogFile {
    private final static long CloseTimeout = 1000;
    private static final String EOL = System.getProperty("line.separator");
    private static final String LogFolder = "log";
    private static final String LogExtension = ".log";

    private SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    private File mFile;
    private BufferedWriter mBufferedWriter;
    private boolean mAppend;
    private boolean mPostponeTimer;
    private Timer mTimer;
    private String mLogTag;

    enum Mode {
        Create,
        Append,
        Unique
    }

    LogFile(String fileName, Mode mode) {
        mAppend = (mode == Mode.Append);
        File external = Environment.getExternalStorageDirectory();
        File folder = new File(external, LogFolder);
        //noinspection ResultOfMethodCallIgnored
        folder.mkdir();
        if (mode == Mode.Unique) {
            fileName += "-" + formatDate(System.currentTimeMillis());
        }
        mFile = new File(folder, fileName + LogExtension);
    }

    void setLogTag(String logTag) {
        mLogTag = logTag;
    }

    synchronized void log(String text) {
        if (mLogTag != null) {
            Log.d(mLogTag, text);
        }
        try {
            if (mBufferedWriter == null) {
                try {
                    FileOutputStream outputStream = new FileOutputStream(mFile, mAppend);
                    mAppend = true;
                    mBufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, "utf-8"));
                    mTimer = new Timer();
                    mTimer.schedule(createCloseTask(), CloseTimeout);
                } catch (FileNotFoundException | UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            mBufferedWriter.write(text + EOL);
            mBufferedWriter.flush();
            mPostponeTimer = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    synchronized void close() {
        if (mBufferedWriter != null) {
            try {
                mBufferedWriter.flush();
                mBufferedWriter.close();
                mBufferedWriter = null;
                mTimer.cancel();
                mTimer = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private TimerTask createCloseTask() {
        return new TimerTask() {
            @Override
            public synchronized void run() {
                if (mPostponeTimer) {
                    mTimer.schedule(createCloseTask(), CloseTimeout);
                    mPostponeTimer = false;
                } else {
                    close();
                }
            }
        };
    }

    private String formatDate(long date) {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(date);
        return sDateFormat.format(calendar.getTime());
    }
}
