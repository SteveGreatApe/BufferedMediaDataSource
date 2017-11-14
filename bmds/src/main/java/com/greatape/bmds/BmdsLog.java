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

import android.util.Log;

/**
 * @author Steve Townsend
 */
public class BmdsLog {
    private static final String TAG = "BMDS:";

    private static boolean mDebug;
    private static boolean mPerformance;

    public static void enableDebug(boolean enabled) {
        mDebug = enabled;
    }

    // TODO: Add performance logging to help analyse buffering performance and requirements
    public static void enablePerformance(boolean enabled) {
        mPerformance = enabled;
    }

    private static void a(String tag, String message) {
        Log.d(TAG + tag, message);
    }

    static void d(String tag, String message) {
        if (mDebug) {
            a(tag, message);
        }
    }

    static void d(String tag, String message, int blockIndex) {
        if (mDebug) {
            a(tag, message + blockIndexToString(blockIndex));
        }
    }

    private static String blockIndexToString(int blockIndex) {
        return " {" + blockIndex + "}";
    }

    public static void p(String tag, String message) {
        if (mPerformance) {
            a(tag, message);
        }
    }

    static void w(String tag, String message) {
        Log.w(TAG + tag, message);
    }

    static void w(String tag, String message, int blockIndex) {
    }

    static void e(String tag, String message) {
        Log.e(TAG + tag, message);
    }

    static void e(String tag, String message, int blockIndex) {
    }
}
