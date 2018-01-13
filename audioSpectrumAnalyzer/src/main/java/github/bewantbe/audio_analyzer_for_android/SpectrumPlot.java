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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.Log;

import static java.lang.Math.abs;
import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.lang.Math.round;

/**
 * The spectrum plot part of AnalyzerGraphic
 */

class SpectrumPlot {
    private static final String TAG = "SpectrumPlot:";
    boolean showLines;
    private Paint linePaint, linePaintLight, linePeakPaint;
    private Paint cursorPaint;
    private Paint gridPaint;
    private Paint labelPaint;
    private Paint calibNamePaint;
    private Paint calibLinePaint;
    private int canvasHeight=0, canvasWidth=0;

    private GridLabel fqGridLabel;
    private GridLabel dbGridLabel;
    private float DPRatio;
    private double gridDensity = 1/85.0;  // every 85 pixel one grid line, on average

    double cursorFreq, cursorDB;  // cursor location
    ScreenPhysicalMapping axisX;  // For frequency axis
    ScreenPhysicalMapping axisY;  // For dB axis

    SpectrumPlot(Context _context) {
        DPRatio = _context.getResources().getDisplayMetrics().density;

        linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#0D2C6D"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1);

        linePaintLight = new Paint(linePaint);
        linePaintLight.setColor(Color.parseColor("#3AB3E2"));

        linePeakPaint = new Paint(linePaint);
        linePeakPaint.setColor(0xFF00A0A0);

        gridPaint = new Paint();
        gridPaint.setColor(Color.DKGRAY);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(0.6f * DPRatio);

        cursorPaint = new Paint(gridPaint);
        cursorPaint.setColor(Color.parseColor("#00CD00"));

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.GRAY);
        labelPaint.setTextSize(14.0f * DPRatio);
        labelPaint.setTypeface(Typeface.MONOSPACE);  // or Typeface.SANS_SERIF

        calibNamePaint = new Paint(labelPaint);
        calibNamePaint.setColor(Color.YELLOW & 0x66ffffff);
        calibLinePaint = new Paint(linePaint);
        calibLinePaint.setColor(Color.YELLOW);

        cursorFreq = cursorDB = 0f;

        fqGridLabel = new GridLabel(GridLabel.Type.FREQ, canvasWidth * gridDensity / DPRatio);
        dbGridLabel = new GridLabel(GridLabel.Type.DB,   canvasHeight * gridDensity / DPRatio);

        axisX = new ScreenPhysicalMapping(0, 0, 0, ScreenPhysicalMapping.Type.LINEAR);
        axisY = new ScreenPhysicalMapping(0, 0, 0, ScreenPhysicalMapping.Type.LINEAR);
    }

    void setCanvas(int _canvasWidth, int _canvasHeight, double[] axisBounds) {
//        Log.i("SpectrumPlot", "setCanvas: W="+_canvasWidth+"  H="+_canvasHeight);
        canvasWidth  = _canvasWidth;
        canvasHeight = _canvasHeight;
        fqGridLabel.setDensity(canvasWidth * gridDensity / DPRatio);
        dbGridLabel.setDensity(canvasHeight * gridDensity / DPRatio);
        axisX.setNCanvasPixel(canvasWidth);
        axisY.setNCanvasPixel(canvasHeight);
        if (axisBounds != null) {
            axisX.setBounds(axisBounds[0], axisBounds[2]);
            axisY.setBounds(axisBounds[1], axisBounds[3]);
        }
    }

    void setZooms(double xZoom, double xShift, double yZoom, double yShift) {
        axisX.setZoomShift(xZoom, xShift);
        axisY.setZoomShift(yZoom, yShift);
    }

    // Linear or Logarithmic frequency axis
    void setFreqAxisMode(ScreenPhysicalMapping.Type mapType, double freq_lower_bound_for_log, GridLabel.Type gridType) {
        axisX.setMappingType(mapType, freq_lower_bound_for_log);
        fqGridLabel.setGridType(gridType);
        Log.i(TAG, "setFreqAxisMode(): set to mode " + mapType + " axisX.vL=" + axisX.vLowerBound + "  freq_lower_bound_for_log = " + freq_lower_bound_for_log);
    }

    private void drawGridLines(Canvas c) {
        for(int i = 0; i < fqGridLabel.values.length; i++) {
            float xPos = (float)axisX.pixelFromV(fqGridLabel.values[i]);
            c.drawLine(xPos, 0, xPos, canvasHeight, gridPaint);
        }
        for(int i = 0; i < dbGridLabel.values.length; i++) {
            float yPos = (float)axisY.pixelFromV(dbGridLabel.values[i]);
            c.drawLine(0, yPos, canvasWidth, yPos, gridPaint);
        }
    }

    private double clampDB(double value) {
        if (value < AnalyzerGraphic.minDB || Double.isNaN(value)) {
            value = AnalyzerGraphic.minDB;
        }
        return value;
    }

    private Matrix matrix = new Matrix();
    private float[] tmpLineXY = new float[0];  // cache line data for drawing
    private double[] db_cache = null;
    int[] index_range_cache = new int[2];
    // index_range_cache[0] == beginFreqPt
    // index_range_cache[1] == endFreqPt
    AnalyzerUtil.PeakHoldAndFall peakHold = new AnalyzerUtil.PeakHoldAndFall();
    long timeLastCall;

    // Find the index range that the plot will appear inside view.
    // index_range[1] must never be reached.
    private void indexRangeFinder(double[] x_values, double x_step, int[] index_range) {
        if (index_range == null || index_range.length != 2) {
            // You are joking
            return ;
        }
        double viewMinX = axisX.vMinInView();
        double viewMaxX = axisX.vMaxInView();
        if (x_values == null) {
            index_range[0] = (int)floor(viewMinX / x_step);    // pointer to tmpLineXY
            index_range[1] = (int)ceil (viewMaxX / x_step)+1;
        } else {
            if (x_values.length == 0) {
                // mada joking
                return ;
            }
            // Assume x_values is in ascending order
            index_range[0] = AnalyzerUtil.binarySearchElem(x_values, viewMinX, true);
            index_range[1] = AnalyzerUtil.binarySearchElem(x_values, viewMaxX, false) + 1;
        }
        // Avoid log(0)
        if (((x_values == null && index_range[0] == 0)
                || (x_values != null && x_values[index_range[0]] <= 0))
                && axisX.mapType == ScreenPhysicalMapping.Type.LOG) {
            index_range[0]++;
        }
        // Avoid out-of-range access
        if (index_range[1] > db_cache.length) {
            // just in case canvasMaxFreq / freqDelta > nFreqPointsTotal
            index_range[1] = db_cache.length;
        }
    }

    private void plotBarThin(Canvas c, double[] y_value, double[] x_values, int i_begin, int i_end, double x_inc, Paint linePainter) {
        if (tmpLineXY.length < 4*(y_value.length)) {
            Log.d(TAG, "plotBar(): new tmpLineXY");
            tmpLineXY = new float[4*(y_value.length)];
        }

        final double minYCanvas = axisY.pixelNoZoomFromV(AnalyzerGraphic.minDB);
        c.save();
        matrix.reset();
        matrix.setTranslate(0, (float)(-axisY.getShift() * canvasHeight));
        matrix.postScale(1, (float)axisY.getZoom());
        c.concat(matrix);
        //      float barWidthInPixel = 0.5f * freqDelta / (canvasMaxFreq - canvasMinFreq) * canvasWidth;
        //      if (barWidthInPixel > 2) {
        //        linePaint.setStrokeWidth(barWidthInPixel);
        //      } else {
        //        linePaint.setStrokeWidth(0);
        //      }
        // plot directly to the canvas
        for (int i = i_begin; i < i_end; i++) {
            float x = (float)axisX.pixelFromV(x_values == null ? i * x_inc : x_values[i]);
            float y = (float)axisY.pixelNoZoomFromV(clampDB(y_value[i]));
            tmpLineXY[4 * i] = x;
            tmpLineXY[4 * i + 1] = (float)minYCanvas;
            tmpLineXY[4 * i + 2] = x;
            tmpLineXY[4 * i + 3] = y;
        }
        c.drawLines(tmpLineXY, 4*i_begin, 4*(i_end-i_begin), linePainter);
        c.restore();
    }

    private void plotBarThick(Canvas c, double[] y_value, double[] x_values, int i_begin, int i_end, double x_inc, Paint linePainter) {
        if (tmpLineXY.length < 4*(y_value.length)) {
            Log.d(TAG, "plotBar(): new tmpLineXY");
            tmpLineXY = new float[4*(y_value.length)];
        }

        final double minYCanvas = axisY.pixelNoZoomFromV(AnalyzerGraphic.minDB);
        int pixelStep = 2;  // each bar occupy this virtual pixel
        c.save();
        matrix.reset();
        double extraPixelAlignOffset = 0.0f;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//          // There is an shift for Android 4.4, while no shift for Android 2.3
//          // I guess that is relate to GL ES acceleration
//          if (c.isHardwareAccelerated()) {
//            extraPixelAlignOffset = 0.5f;
//          }
//        }
        matrix.setTranslate((float)(-axisX.getShift() * (y_value.length - 1) * pixelStep - extraPixelAlignOffset),
                (float)(-axisY.getShift() * canvasHeight));
        matrix.postScale((float)(canvasWidth / ((axisX.vMaxInView() - axisX.vMinInView()) / x_inc * pixelStep)), (float)axisY.getZoom());
        c.concat(matrix);
        // fill interval same as canvas pixel width.
        for (int i = i_begin; i < i_end; i++) {
            float x = x_values == null ? i * pixelStep : (float)(x_values[i] / x_inc * pixelStep);
            float y = (float)axisY.pixelNoZoomFromV(clampDB(y_value[i]));
            tmpLineXY[4*i  ] = x;
            tmpLineXY[4*i+1] = (float)minYCanvas;
            tmpLineXY[4*i+2] = x;
            tmpLineXY[4*i+3] = y;
        }
        c.drawLines(tmpLineXY, 4*i_begin, 4*(i_end-i_begin), linePainter);
        c.restore();
    }

    private void plotBar(Canvas c, double[] y_value, double[] x_values, int i_begin, int i_end, double x_inc, Paint linePainter) {
        // If bars are very close to each other, draw bars as lines
        // Otherwise, zoom in so that lines look like bars.
        if (i_end - i_begin >= axisX.nCanvasPixel / 2
                || axisX.mapType != ScreenPhysicalMapping.Type.LINEAR) {
            plotBarThin(c, y_value, x_values, i_begin, i_end, x_inc, linePainter);
        } else {
            // for zoomed linear scale
            plotBarThick(c, y_value, x_values, i_begin, i_end, x_inc, linePainter);
        }
    }

    private void plotLine(Canvas c, double[] y_value, double[] x_values, int i_begin, int i_end, double x_inc, Paint linePainter) {
        if (tmpLineXY.length < 4*(y_value.length)) {
            Log.d(TAG, "plotLine(): new tmpLineXY");
            tmpLineXY = new float[4*(y_value.length)];
        }
        c.save();
        matrix.reset();
        matrix.setTranslate(0, (float)(-axisY.getShift()*canvasHeight));
        matrix.postScale(1, (float)axisY.getZoom());
        c.concat(matrix);
        if (x_values == null) {
            float o_x = (float)axisX.pixelFromV(i_begin * x_inc);
            float o_y = (float)axisY.pixelNoZoomFromV(clampDB(y_value[i_begin]));
            for (int i = i_begin+1; i < i_end; i++) {
                float x = (float)axisX.pixelFromV(i * x_inc);
                float y = (float)axisY.pixelNoZoomFromV(clampDB(y_value[i]));
                tmpLineXY[4*i  ] = o_x;
                tmpLineXY[4*i+1] = o_y;
                tmpLineXY[4*i+2] = x;
                tmpLineXY[4*i+3] = y;
                o_x = x;
                o_y = y;
            }
        } else {
            float o_x = (float)axisX.pixelFromV(x_values[i_begin]);
            float o_y = (float)axisY.pixelNoZoomFromV(clampDB(y_value[i_begin]));
            for (int i = i_begin+1; i < i_end; i++) {
                float x = (float)axisX.pixelFromV(x_values[i]);
                float y = (float)axisY.pixelNoZoomFromV(clampDB(y_value[i]));
                tmpLineXY[4*i  ] = o_x;
                tmpLineXY[4*i+1] = o_y;
                tmpLineXY[4*i+2] = x;
                tmpLineXY[4*i+3] = y;
                o_x = x;
                o_y = y;
            }
        }
        c.drawLines(tmpLineXY, 4*(i_begin+1), 4*(i_end-i_begin-1), linePainter);
        c.restore();
    }

    private void plotLineBar(Canvas c, double[] db_cache, double[] x_values, boolean drawBar, Paint linePaint, Paint barPaint) {
        if (db_cache == null || db_cache.length == 0) return;

        // There are db.length frequency points, including DC component
        int nFreqPointsTotal = db_cache.length - 1;
        double freqDelta = axisX.vUpperBound / nFreqPointsTotal;

        indexRangeFinder(x_values, freqDelta, index_range_cache);

        if (drawBar) {
            plotBar(c, db_cache, x_values, index_range_cache[0], index_range_cache[1], freqDelta, barPaint);
        }
        plotLine(c, db_cache, x_values, index_range_cache[0], index_range_cache[1], freqDelta, linePaint);
    }

    // Plot the spectrum into the Canvas c
    private void drawSpectrumOnCanvas(Canvas c, final double[] _db) {
        if (canvasHeight < 1 || _db == null || _db.length == 0) {
            return;
        }
        AnalyzerGraphic.setIsBusy(true);

        synchronized (_db) {  // TODO: need lock on savedDBSpectrum, but how?
            if (db_cache == null || db_cache.length != _db.length) {
                Log.d(TAG, "drawSpectrumOnCanvas(): new db_cache");
                db_cache = new double[_db.length];
            }
            System.arraycopy(_db, 0, db_cache, 0, _db.length);
        }

        long timeNow = SystemClock.uptimeMillis();
        peakHold.addCurrentValue(db_cache, (timeNow - timeLastCall)/1000.0);
        timeLastCall = timeNow;

        // Spectrum peak hold
        plotLineBar(c, peakHold.v_peak, null, false, linePeakPaint, null);

        // Spectrum line and bar
        plotLineBar(c, db_cache, null, !showLines, linePaintLight, linePaint);

        if (name_calib != null) {
            c.save();
            c.translate(30*DPRatio, (float)axisY.pixelFromV(0));
            c.drawText(name_calib, 0, 0, calibNamePaint);
            c.restore();
        }
        plotLineBar(c, y_calib, x_calib, false, calibLinePaint, null);

        AnalyzerGraphic.setIsBusy(false);
    }

    double[] y_calib = null;
    double[] x_calib = null;
    String name_calib = null;

    void addCalibCurve(double[] y, double[] x, String name) {
        y_calib = y;
        x_calib = x;
        name_calib = name;
    }

    // x, y is in pixel unit
    void setCursor(double x, double y) {
        cursorFreq = axisX.vFromPixel(x);  // frequency
        cursorDB   = axisY.vFromPixel(y);  // decibel
    }

    double getCursorFreq() {
        return  canvasWidth == 0 ? 0 : cursorFreq;
    }

    double getCursorDB() {
        return canvasHeight == 0 ?   0 : cursorDB;
    }

    void hideCursor() {
        cursorFreq = 0;
        cursorDB = 0;
    }

    private void drawCursor(Canvas c) {
        if (cursorFreq == 0) {
            return;
        }
        float cX, cY;
        cX = (float)axisX.pixelFromV(cursorFreq);
        cY = (float)axisY.pixelFromV(cursorDB);
        c.drawLine(cX, 0, cX, canvasHeight, cursorPaint);
        c.drawLine(0, cY, canvasWidth, cY, cursorPaint);
    }

    // Plot spectrum with axis and ticks on the whole canvas c
    void drawSpectrumPlot(Canvas c, double[] savedDBSpectrum) {
        fqGridLabel.updateGridLabels(axisX.vMinInView(), axisX.vMaxInView());
        dbGridLabel.updateGridLabels(axisY.vMinInView(), axisY.vMaxInView());
        drawGridLines(c);
        drawSpectrumOnCanvas(c, savedDBSpectrum);
        drawCursor(c);
        AxisTickLabels.draw(c, axisX, fqGridLabel,
                0f, 0f, 0, 1,
                labelPaint, gridPaint, gridPaint);
        AxisTickLabels.draw(c, axisY, dbGridLabel,
                0f, 0f, 1, 1,
                labelPaint, gridPaint, gridPaint);
    }
}
