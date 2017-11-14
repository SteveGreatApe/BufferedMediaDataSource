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
package com.greatape.bmds.utils;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * @author Steve Townsend
 */
public class Utils {
    public static String formatDuration(long millis) {
        long ms = millis % 1000;
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        long hours = minutes / 60;
        minutes %= 60;
        StringBuilder stringBuilder = new StringBuilder();
        if (hours > 0) {
            stringBuilder.append(hours);
            stringBuilder.append('h');
        }
        if (minutes > 0) {
            stringBuilder.append(minutes);
            stringBuilder.append('m');
        }
        stringBuilder.append(String.format(Locale.ENGLISH, "%02d.%03d", seconds, ms));
        return stringBuilder.toString();
    }

    public static String formatFileSize(long size) {
        final double KiloByte = 1024.0;

        double k = size / KiloByte;
        if (k >= 1) {
            DecimalFormat dec = new DecimalFormat("0.00");
            double m = k / KiloByte;
            if (m >= 1) {
                double g = m / KiloByte;
                if (g >= 1) {
                    double t = g / KiloByte;
                    if (t >= 1) {
                        return dec.format(t).concat(" TB");
                    }
                    return dec.format(g).concat(" GB");
                }
                return dec.format(m).concat(" MB");
            }
            return dec.format(k).concat(" KB");
        }
        return Long.toString(size).concat(" Bytes");
    }
}
