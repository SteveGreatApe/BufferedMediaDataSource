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

import android.text.format.Time;
import android.util.Log;

import com.greatape.utils.Utils;

import org.junit.Test;

import java.text.DecimalFormat;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Random;

import static junit.framework.Assert.assertEquals;

/**
 * @author Steve Townsend
 */
public class UtilsTest {
    private static final String TAG = "UtilsTest";

    final long KiloByte = 1024;
    final long MegaByte = 1024 * KiloByte;
    final long GigaByte = 1024 * MegaByte;
    final long TeraByte = 1024 * GigaByte;

    final String[] Suffixes = {"Bytes", "KB", "MB", "GB", "TB"};

    @Test
    public void testFormatSize() {
        // A few hand crafted tests
        doTestFormatSize(123, "123 Bytes");
        doTestFormatSize(KiloByte, "1.00 KB");
        doTestFormatSize((long)(KiloByte * 1.1f), "1.10 KB");
        doTestFormatSize(MegaByte, "1.00 MB");
        doTestFormatSize((long)(MegaByte * 9.8765f), "9.88 MB");
        doTestFormatSize(GigaByte, "1.00 GB");
        doTestFormatSize((long)(GigaByte * 123.4567890f), "123.46 GB");
        doTestFormatSize(TeraByte, "1.00 TB");
        doTestFormatSize((long)(TeraByte * 1024.68f), "1024.68 TB");

        // And a load of auto-generated test values
        int NumRandTests = 1000;
        Random random = new Random(12345);
        for(int index = 0; index < NumRandTests; index++) {
            int e = random.nextInt(62);
            double testValue = Math.pow(1 + random.nextDouble(), e);
            doTestFormatSize((long)testValue);
        }
    }

    private void doTestFormatSize(long size) {
        double dividedSize = size;
        int suffixIndex = 0;
        while (dividedSize >= KiloByte && suffixIndex < (Suffixes.length - 1)) {
            suffixIndex++;
            dividedSize /= KiloByte;
        }
        String expected;
        if (suffixIndex == 0) {
            expected = String.valueOf(size) + " " + Suffixes[suffixIndex];
        } else {
            expected = new DecimalFormat("0.00").format(dividedSize) + " " + Suffixes[suffixIndex];
        }
        Log.d(TAG, "Testing format of " + size + " == " + expected);
        doTestFormatSize(size, expected);
    }

    private void doTestFormatSize(long size, String expected) {
        assertEquals(expected, Utils.formatFileSize(size));
    }
}
