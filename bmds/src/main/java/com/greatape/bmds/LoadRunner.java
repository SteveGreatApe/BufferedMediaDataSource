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
import android.support.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

/**
 * @author Steve Townsend
 */
@RequiresApi(api = Build.VERSION_CODES.M)
class LoadRunner implements Runnable {
    final static String TAG = "LoadRunner";

    private final static long CloseThreadTimeOut = 60 * 1000;
    // Due to issues with JCIF's not handling multi-threaded access we ensure only one thread is
    // ever active at a time.
    private static Map<String, LoadRunner> sLoadRunnerInstances = new HashMap<>();
    private final static Object sSyncObject = new Object();

    private Thread mThread;
    private Semaphore mLoadSemaphore;
    private boolean mStopped;
    private final List<LoadRunnerClient> mClientList;
    private String mTypeName;
    private Timer mTimer;

    static LoadRunnerClient addNewClient(MediaCache mediaCache, String typeName) {
        LoadRunner loadRunner = sLoadRunnerInstances.get(typeName);
        synchronized (sSyncObject) {
            if (loadRunner == null) {
                loadRunner = new LoadRunner(typeName);
                sLoadRunnerInstances.put(typeName, loadRunner);
            } else {
                loadRunner.cancelCloseTimer();
            }
        }
        return loadRunner.addClient(mediaCache);
    }

    private LoadRunner(String typeName) {
        mTypeName = typeName;
        mLoadSemaphore = new Semaphore(0);
        mClientList = Collections.synchronizedList(new ArrayList<>());
        mThread = new Thread(this);
        BmdsLog.d(TAG, "Created new LoadRunner thread: " + mThread.getName());
        mThread.start();
    }

    private LoadRunnerClient addClient(MediaCache mediaCache) {
        LoadRunnerClient client;
        synchronized (sSyncObject) {
            client = new LoadRunnerClient(mediaCache, this);
            mClientList.add(client);
        }
        return client;
    }

    void releaseSemaphore() {
        mLoadSemaphore.release();
    }

    boolean acquireSemaphore() {
        return mLoadSemaphore.tryAcquire();
    }

    void remove(LoadRunnerClient loadRunnerClient) {
        // We need to synchronise here to avoid stop running and freeing resources while
        // the load thread is busy running a load.
        synchronized (sSyncObject) {
            mClientList.remove(loadRunnerClient);
            if (mClientList.isEmpty()) {
                mTimer = new Timer();
                mTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        synchronized (sSyncObject) {
                            if (!mClientList.isEmpty()) {
                                return;
                            }
                            sLoadRunnerInstances.remove(mTypeName);
                            BmdsLog.d(TAG, "remove() Stopping thread");
                            mStopped = true;
                            mLoadSemaphore.release();
                        }
                    }
                }, CloseThreadTimeOut);
            }
        }
    }

    private void cancelCloseTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    @Override
    public void run() {
        do {
            try {
                mLoadSemaphore.acquire();
                if (!mStopped) {
                    LoadRunnerClient.LoadItem loadItem = null;
                    synchronized (sSyncObject) {
                        for (LoadRunnerClient client : mClientList) {
                            loadItem = client.findLoadItem();
                            if (loadItem != null) {
                                break;
                            }
                        }
                        if (loadItem != null) {
                            loadItem.setActive(true);
                        }
                    }
                    if (loadItem != null) {
                        loadItem.read();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (!mStopped);
        BmdsLog.d(TAG, "run() QUIT");
    }
}
