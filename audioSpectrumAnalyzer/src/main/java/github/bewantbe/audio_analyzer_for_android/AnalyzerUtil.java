/* Copyright 2014 Eddy Xiao <bewantbe@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package github.bewantbe.audio_analyzer_for_android;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.util.Log;

import java.util.ArrayList;

/**
 * Utility functions for audio analyzer.
 */

class AnalyzerUtil {
    static String TAG = "AnalyzerUtil";
    private static final String[] LP = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    // Convert frequency to pitch
    // Fill with sFill until length is 6. If sFill=="", do not fill
    static void freq2Cent(StringBuilder a, double f, String sFill) {
        if (f<=0 || Double.isNaN(f) || Double.isInfinite(f)) {
            a.append("      ");
            return;
        }
        int len0 = a.length();
        // A4 = 440Hz
        double p = 69 + 12 * Math.log(f/440.0)/Math.log(2);  // MIDI pitch
        int pi = (int) Math.round(p);
        int po = (int) Math.floor(pi/12.0);
        int pm = pi-po*12;
        a.append(LP[pm]);
        SBNumFormat.fillInInt(a, po-1);
        if (LP[pm].length() == 1) {
            a.append(' ');
        }
        SBNumFormat.fillInNumFixedWidthSignedFirst(a, Math.round(100*(p-pi)), 2, 0);
        while (a.length()-len0 < 6 && sFill!=null && sFill.length()>0) {
            a.append(sFill);
        }
    }

    // used to detect if the data is unchanged
    private double[] cmpDB;
    void sameTest(double[] data) {
        // test
        if (cmpDB == null || cmpDB.length != data.length) {
            Log.i(TAG, "sameTest(): new");
            cmpDB = new double[data.length];
        } else {
            boolean same = true;
            for (int i=0; i<data.length; i++) {
                if (!Double.isNaN(cmpDB[i]) && !Double.isInfinite(cmpDB[i]) && cmpDB[i] != data[i]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                Log.i(TAG, "sameTest(): same data row!!");
            }
            for (int i=0; i<data.length; i++) {
                cmpDB[i] = data[i];
            }
        }
    }

    /**
     * Return a array of verified audio sampling rates.
     * @param requested: the sampling rates to be verified
     */
    static String[] validateAudioRates(String[] requested) {
        ArrayList<String> validated = new ArrayList<>();
        for (String s : requested) {
            int rate;
            String[] sv = s.split("::");
            if (sv.length == 1) {
                rate = Integer.parseInt(sv[0]);
            } else {
                rate = Integer.parseInt(sv[1]);
            }
            if (rate != 0) {
                if (AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT) != AudioRecord.ERROR_BAD_VALUE) {
                    validated.add(s);
                }
            } else {
                validated.add(s);
            }
        }
        return validated.toArray(new String[0]);
    }

    static double parseDouble(String st) {
        try {
            return Double.parseDouble(st);
        } catch (NumberFormatException e) {
            return 0.0/0.0;  // nan
        }
    }
}
