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

import java.io.EOFException;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

import static com.greatape.bmds.LoadRunner.TAG;

/**
 * @author Steve Townsend
 */
class LoadRunnerClient {
    private final MediaCache mMediaCache;
    private final List<LoadItem> mLoadQueue;
    private LoadRunner mLoadRunner;

    LoadRunnerClient(MediaCache mediaCache, LoadRunner loadRunner) {
        mMediaCache = mediaCache;
        mLoadRunner = loadRunner;
        mLoadQueue = Collections.synchronizedList(new LinkedList<LoadItem>());
    }

    LoadItem requestLoad(int blockIndex, boolean priority) {
        synchronized(mLoadQueue) {
            BmdsLog.d(TAG, "Queuing load, priority=" + priority + " for", blockIndex);
            LoadItem loadItem = new LoadItem(blockIndex, priority);
            mLoadQueue.add(loadItem);
            mLoadRunner.releaseSemaphore();
            return loadItem;
        }
    }
    public void close() {
        mLoadRunner.remove(this);
        Semaphore waitForClose = null;
        synchronized(mLoadQueue) {
            for (LoadItem loadItem : mLoadQueue) {
                if (loadItem.isActive) {
                    loadItem.closeSemaphore = new Semaphore(0);
                    waitForClose = loadItem.closeSemaphore;
                }
            }
        }
        if (waitForClose != null) {
            try {
                waitForClose.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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
                        if (!mLoadRunner.acquireSemaphore()) {
                            BmdsLog.e(TAG, "No outstanding permit for mLoadSemaphore");
                        }
                    }
                }
            }
        }
    }

    LoadItem findLoadItem() {
        LoadItem toLoad = null;
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
        return toLoad;
    }

    class LoadItem {
        int blockIndex;
        byte[] result;
        IOException exception;
        Semaphore semaphore;
        boolean isActive;
        Semaphore closeSemaphore;

        LoadItem(int blockIndex, boolean blocking) {
            this.blockIndex = blockIndex;
            if (blocking) {
                this.semaphore = new Semaphore(0);
            }
        }

        void read() {
            try {
                BmdsLog.d(TAG, "Running load IN", blockIndex);
                byte[] buffer = mMediaCache.readIntoCache(blockIndex);
                notifyResult(blockIndex, buffer, null);
                BmdsLog.d(TAG, "Running load OUT", blockIndex);
            } catch (EOFException e) {
                BmdsLog.e(TAG, "EOF in wait load", blockIndex);
            } catch (IOException e) {
                BmdsLog.e(TAG, "Exception in wait load", blockIndex);
                e.printStackTrace();
                notifyResult(blockIndex, null, e);
            }
            synchronized(mLoadQueue) {
                mLoadQueue.remove(this);
                if (closeSemaphore != null) {
                    closeSemaphore.release();
                }
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

        void setActive() {
            isActive = true;
        }
    }
}
