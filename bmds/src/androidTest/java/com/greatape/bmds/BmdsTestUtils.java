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

import com.greatape.bmds.smb.SmbBufferedMediaDataSource;

import junit.framework.Assert;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Properties;

import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbRandomAccessFile;

import static android.support.test.InstrumentationRegistry.getInstrumentation;

/**
 * @author Steve Townsend
 */
class BmdsTestUtils {

    static void grantPermission(String permission) {
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

    public static void assertBuffersEqual(String message, byte[] a, byte[] a2) {
        if (a2.length != a.length)
            Assert.fail(message);

        for (int i=0; i< a.length; i++)
            if (a[i] != a2[i])
                Assert.fail(message);

    }
}
