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

import java.io.EOFException;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * @author Steve Townsend
 */
@RequiresApi(api = Build.VERSION_CODES.M)
class LoadRunner implements Runnable {
    private final static String TAG = "LoadRunner";

    // Due to issues with JCIF's not handling multi-threaded access we ensure only one thread is ever
    // active at a time.
    // TODO: Make this configurable to allow other implementations to take advantage of multi-threading
    private static Thread mThread;

    private Semaphore mLoadSemaphore;
    private final List<LoadItem> mLoadQueue;
    private boolean mStopped;
    private final MediaCache mMediaCache;

    class LoadItem {
        int blockIndex;
        byte[] result;
        IOException exception;
        Semaphore semaphore;

        LoadItem(int blockIndex, boolean blocking) {
            this.blockIndex = blockIndex;
            if (blocking) {
                this.semaphore = new Semaphore(0);
            }
        }

        byte[] waitForBuffer() throws IOException {
            try {
                semaphore.acquire();
                if (exception != null) {
                    throw exception;
                }
                return result;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    LoadRunner(MediaCache mediaCache) {
        mLoadSemaphore = new Semaphore(0);
        mLoadQueue = Collections.synchronizedList(new LinkedList<LoadItem>());
        mMediaCache = mediaCache;
        Thread oldThread = mThread;
        if (oldThread != null) {
            BmdsLog.d(TAG, "Old thread still exists, wait for it to exit: " + oldThread);
            boolean accessGranted = false;
            while (!accessGranted) {
                try {
                    oldThread.join(0);
                    accessGranted = true;
                    BmdsLog.d(TAG, "Thread.join() complete");
                } catch (InterruptedException e) {
                    BmdsLog.w(TAG, "Thread.join() interrupted: " + e.toString());
                    e.printStackTrace();
                }
            }
        }
        BmdsLog.d(TAG, "Creating new LoadRunner thread");
        mThread = new Thread(this);
        mThread.start();
    }

    LoadItem requestLoad(int blockIndex, boolean priority) {
        synchronized(mLoadQueue) {
            BmdsLog.d(TAG, "Queuing load, priority=" + priority + " for", blockIndex);
            LoadItem loadItem = new LoadItem(blockIndex, priority);
            mLoadQueue.add(loadItem);
            mLoadSemaphore.release();
            return loadItem;
        }
    }

    boolean hasRequestForBlock(int blockIndex) {
        synchronized(mLoadQueue) {
            for(LoadItem loadItem : mLoadQueue) {
                if (loadItem.blockIndex == blockIndex) {
                    return true;
                }
            }
            return false;
        }
    }

    private void notifyResult(int blockIndex, byte[] buffer, IOException exception) {
        synchronized(mLoadQueue) {
            boolean isFirst = true;
            Iterator<LoadItem> iterator = mLoadQueue.iterator();
            while (iterator.hasNext()) {
                LoadItem loadItem = iterator.next();
                if (loadItem.blockIndex == blockIndex) {
                    loadItem.result = buffer;
                    loadItem.exception = exception;
                    if (loadItem.semaphore != null) {
                        loadItem.semaphore.release();
                        if (buffer == null && exception == null) {
                            BmdsLog.e(TAG, "Priority load with no buffer or exception returned");
                        }
                    }
                    iterator.remove();
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        // Should always have an available permit as release is called for every item
                        // added to mLoadQueue
                        if (!mLoadSemaphore.tryAcquire()) {
                            BmdsLog.e(TAG, "No outstanding permit for mLoadSemaphore");
                        }
                    }
                }
            }
        }
    }

    void stop() {
        BmdsLog.d(TAG, "stop() IN");
        mStopped = true;
        // We need to synchronise here to avoid stop running and freeing resources while
        // the load thread is busy running a load.
        Thread closingThread = mThread;
        synchronized (this) {
            mLoadSemaphore.release();
        }
        try {
            closingThread.join(0);
            if (closingThread.isAlive()) {
                BmdsLog.w(TAG, "Timed out, exiting without waiting for thread to stop");
            } else if (closingThread == mThread) {
                mThread = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        BmdsLog.d(TAG, "stop() OUT");
    }

    @Override
    public void run() {
        do {
            LoadItem toLoad = null;
            try {
                mLoadSemaphore.acquire();
                synchronized (this) {
                    if (mStopped) {
                        return;
                    }
                    for (int qIndex = 0; qIndex < mLoadQueue.size(); qIndex++) {
                        LoadItem qItem = mLoadQueue.get(qIndex);
                        // Prioritise the first blocking LoadItem
                        if (qItem.semaphore != null) {
                            toLoad = qItem;
                            break;
                        }
                        if (toLoad == null) {
                            toLoad = qItem;
                        }
                    }
                    BmdsLog.d(TAG, "Running load IN", toLoad.blockIndex);
                    byte[] buffer = mMediaCache.readIntoCache(toLoad.blockIndex);
                    notifyResult(toLoad.blockIndex, buffer, null);
                    BmdsLog.d(TAG, "Running load OUT", toLoad.blockIndex);
                }
            } catch (EOFException e) {
                BmdsLog.e(TAG, "EOF in wait load", toLoad.blockIndex);
            } catch (IOException e) {
                BmdsLog.e(TAG, "Exception in wait load", toLoad.blockIndex);
                e.printStackTrace();
                notifyResult(toLoad.blockIndex, null, e);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mLoadQueue.remove(toLoad);
        } while (!mStopped);
    }
}
