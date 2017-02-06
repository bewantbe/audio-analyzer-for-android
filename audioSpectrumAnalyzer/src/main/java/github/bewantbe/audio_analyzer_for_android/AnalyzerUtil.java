package github.bewantbe.audio_analyzer_for_android;

import android.util.Log;

/**
 * Created by xyy on 2/6/17.
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
}
