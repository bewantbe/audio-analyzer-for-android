package github.bewantbe.audio_analyzer_for_android;

import android.util.Log;

/**
 * Generate grid label (marker)
 */

class GridLabel {
    final static String TAG = "GridLabel";

//    private double[][] gridPoints2   = new double[2][0];
//    private double[][] gridPoints2dB = new double[2][0];
//    private double[][] gridPoints2T  = new double[2][0];
//    private StringBuilder[] gridPoints2Str   = new StringBuilder[0];
//    private StringBuilder[] gridPoints2StrDB = new StringBuilder[0];
//    private StringBuilder[] gridPoints2StrT  = new StringBuilder[0];
//    private char[][] gridPoints2st   = new char[0][];
//    private char[][] gridPoints2stDB = new char[0][];
//    private char[][] gridPoints2stT  = new char[0][];

    // Never null!
    double[] values = new double[0];  // TODO: use a better name?
    double[] ticks  = new double[0];  // TODO: use a better name?

    enum GridScaleType {  // java's enum type is inconvenient
        FREQ(0), DB(1), TIME(2);

        private final int value;
        private GridScaleType(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    private GridScaleType gridType;
    private float gridDensity;

    GridLabel(GridScaleType _gridType, float _gridDensity) {
        gridType = _gridType;
        gridDensity = _gridDensity;
    }

    // return position of grid lines, there are roughly gridDensity lines for the bigger grid
    private static void genLinearGridPoints(double[][] gridPointsArray, double startValue, double endValue,
                                     double gridDensity, int scale_mode) {
        if (startValue == endValue || Double.isInfinite(startValue+endValue) || Double.isNaN(startValue+endValue)) {
            Log.e(TAG, "genLinearGridPoints(): startValue == endValue or value invalid");
            return;
        }
        if (startValue > endValue) {
            double t = endValue;
            endValue = startValue;
            startValue = t;
        }
        if (scale_mode == 0 || scale_mode == 2) {
            if (gridDensity < 3.2) {
                // 3.2 >= 2 * 5/sqrt(2*5), so that there are at least 2 bigger grid.
                // The constant here is because: if gridIntervalGuess = sqrt(2*5), then gridIntervalBig = 5
                // i.e. grid size become larger by factor 5/sqrt(2*5).
                // By setting gridDensity = 3.2, we can make sure minimum gridDensity > 2
                gridDensity = 3.2;
            }
        } else {
            if (gridDensity < 3.5) {  // similar discussion as above
                gridDensity = 3.5;      // 3.5 >= 2 * 3/sqrt(1*3)
            }
        }
        double intervalValue = endValue - startValue;
        double gridIntervalGuess = intervalValue / gridDensity;
        double gridIntervalBig;
        double gridIntervalSmall;

        // Determine a suitable grid interval from guess
        if (scale_mode == 0 || scale_mode == 2 || intervalValue <= 1) {  // Linear scale (Hz, Time)
            double exponent = Math.pow(10, Math.floor(Math.log10(gridIntervalGuess)));
            double fraction = gridIntervalGuess / exponent;
            // grid interval is 1, 2, 5, 10, ...
            if (fraction < Math.sqrt(1*2)) {
                gridIntervalBig   = 1;
                gridIntervalSmall = 0.2;
            } else if (fraction < Math.sqrt(2*5)) {
                gridIntervalBig   = 2;
                gridIntervalSmall = 1.0;
            } else if (fraction < Math.sqrt(5*10)) {
                gridIntervalBig   = 5;
                gridIntervalSmall = 1;
            } else {
                gridIntervalBig   = 10;
                gridIntervalSmall = 2;
            }
            gridIntervalBig   *= exponent;
            gridIntervalSmall *= exponent;
        } else {  // dB scale
            if (gridIntervalGuess > Math.sqrt(36*12)) {
                gridIntervalBig   = 36;
                gridIntervalSmall = 12;
            } else if (gridIntervalGuess > Math.sqrt(12*6)) {
                gridIntervalBig   = 12;
                gridIntervalSmall = 2;
            } else if (gridIntervalGuess > Math.sqrt(6*3)) {
                gridIntervalBig   = 6;
                gridIntervalSmall = 1;
            } else if (gridIntervalGuess > Math.sqrt(3*1)) {
                gridIntervalBig   = 3;
                gridIntervalSmall = 1;
            } else {
                gridIntervalBig   = 1;
                gridIntervalSmall = 1.0/6;
            }
        }

        if (gridPointsArray == null || gridPointsArray.length != 2) {
            Log.e(TAG, " genLinearGridPoints(): empty array!!");
            return;
        }

        // Reallocate if number of grid lines are different
        // Then fill in the gird line coordinates. Assuming the grid lines starting from 0
        double gridStartValueBig   = Math.ceil(startValue / gridIntervalBig)   * gridIntervalBig;
        int nGrid = (int)Math.floor((endValue - gridStartValueBig) / gridIntervalBig) + 1;
        if (nGrid != gridPointsArray[0].length) {
            gridPointsArray[0] = new double[nGrid];
        }
        double[] bigGridPoints = gridPointsArray[0];
        for (int i = 0; i < nGrid; i++) {
            bigGridPoints[i] = gridStartValueBig + i*gridIntervalBig;
        }

        double gridStartValueSmall = Math.ceil(startValue / gridIntervalSmall) * gridIntervalSmall;
        nGrid = (int)Math.floor((endValue - gridStartValueSmall) / gridIntervalSmall) + 1;
        if (nGrid != gridPointsArray[1].length) {    // reallocate space when need
            gridPointsArray[1] = new double[nGrid];
        }
        double[] smallGridPoints = gridPointsArray[1];
        for (int i = 0; i < nGrid; i++) {
            smallGridPoints[i] = gridStartValueSmall + i*gridIntervalSmall;
        }

    }

    StringBuilder[] strings = new StringBuilder[0];
    char[][] chars = new char[0][];

    private double[] oldGridBoundary = new double[2];
    private double[][] gridPointsArray = new double[2][];

    // It's so ugly to write these StringBuffer stuff -- in order to reduce garbage
    // Also, since there is no "pass by reference", modify array is also ugly...
    void updateGridLabels(double startValue, double endValue) {
        int scale_mode_id = gridType.getValue();
        gridPointsArray[0] = values;
        gridPointsArray[1] = ticks;
        genLinearGridPoints(gridPointsArray, startValue, endValue, gridDensity, scale_mode_id);
        boolean needUpdate = false;
        if (values.length != strings.length) {
            strings = new StringBuilder[values.length];
            for (int i = 0; i < values.length; i++) {
                strings[i] = new StringBuilder();
            }
            chars = new char[values.length][];
            for (int i = 0; i < values.length; i++) {
                chars[i] = new char[16];    // hand coded max char length...
            }
            needUpdate = true;
        }
        if (values.length > 0 && (needUpdate || values[0] != oldGridBoundary[0]
                || values[values.length-1] != oldGridBoundary[1])) {
            oldGridBoundary[0] = values[0];
            oldGridBoundary[1] = values[values.length-1];
            for (int i = 0; i < strings.length; i++) {
                strings[i].setLength(0);
                if (values[1] - values[0] >= 1) {
                    SBNumFormat.fillInNumFixedFrac(strings[i], values[i], 7, 0);
                } else if (values[1] - values[0] >= 0.1) {
                    SBNumFormat.fillInNumFixedFrac(strings[i], values[i], 7, 1);
                } else {
                    SBNumFormat.fillInNumFixedFrac(strings[i], values[i], 7, 2);
                }
                strings[i].getChars(0, strings[i].length(), chars[i], 0);
            }
        }
    }

}
