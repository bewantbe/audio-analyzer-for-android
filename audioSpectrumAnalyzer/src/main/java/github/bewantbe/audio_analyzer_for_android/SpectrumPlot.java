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
    private Paint linePaint, linePaintLight;
    private Paint cursorPaint;
    private Paint gridPaint;
    private Paint labelPaint;
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

        double canvasMinFreq = axisX.vMinInView();
        double canvasMaxFreq = axisX.vMaxInView();
        // There are db.length frequency points, including DC component
        int nFreqPointsTotal = db_cache.length - 1;
        double freqDelta = axisX.vUpperBound / nFreqPointsTotal;
        int beginFreqPt = (int)floor(canvasMinFreq / freqDelta);    // pointer to tmpLineXY
        int endFreqPt   = (int)ceil (canvasMaxFreq / freqDelta)+1;
        final double minYCanvas = axisY.pixelNoZoomFromV(AnalyzerGraphic.minDB);

        // add one more boundary points
        if (beginFreqPt == 0 && axisX.mapType == ScreenPhysicalMapping.Type.LOG) {
            beginFreqPt++;
        }
        if (endFreqPt > db_cache.length) {
            endFreqPt = db_cache.length;  // just in case canvasMaxFreq / freqDelta > nFreqPointsTotal
        }

        if (tmpLineXY.length != 4*(db_cache.length)) {
            Log.d(TAG, "drawSpectrumOnCanvas(): new tmpLineXY");
            tmpLineXY = new float[4*(db_cache.length)];
        }

        // spectrum bar
        if (showLines == false) {
            c.save();
            // If bars are very close to each other, draw bars as lines
            // Otherwise, zoom in so that lines look like bars.
            if (endFreqPt - beginFreqPt >= axisX.nCanvasPixel / 2
                    || axisX.mapType != ScreenPhysicalMapping.Type.LINEAR) {
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
                for (int i = beginFreqPt; i < endFreqPt; i++) {
                    float x = (float)axisX.pixelFromV(i * freqDelta);
                    float y = (float)axisY.pixelNoZoomFromV(clampDB(db_cache[i]));
                    if (y != canvasHeight) { // ...forgot why
                        tmpLineXY[4 * i] = x;
                        tmpLineXY[4 * i + 1] = (float)minYCanvas;
                        tmpLineXY[4 * i + 2] = x;
                        tmpLineXY[4 * i + 3] = y;
                    }
                }
                c.drawLines(tmpLineXY, 4*beginFreqPt, 4*(endFreqPt-beginFreqPt), linePaint);
            } else {
                // for zoomed linear scale
                int pixelStep = 2;  // each bar occupy this virtual pixel
                matrix.reset();
                double extraPixelAlignOffset = 0.0f;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//          // There is an shift for Android 4.4, while no shift for Android 2.3
//          // I guess that is relate to GL ES acceleration
//          if (c.isHardwareAccelerated()) {
//            extraPixelAlignOffset = 0.5f;
//          }
//        }
                matrix.setTranslate((float)(-axisX.getShift() * nFreqPointsTotal * pixelStep - extraPixelAlignOffset),
                                    (float)(-axisY.getShift() * canvasHeight));
                matrix.postScale((float)(canvasWidth / ((canvasMaxFreq - canvasMinFreq) / freqDelta * pixelStep)), (float)axisY.getZoom());
                c.concat(matrix);
                // fill interval same as canvas pixel width.
                for (int i = beginFreqPt; i < endFreqPt; i++) {
                    float x = i * pixelStep;
                    float y = (float)axisY.pixelNoZoomFromV(clampDB(db_cache[i]));
                    if (y != canvasHeight) {
                        tmpLineXY[4*i  ] = x;
                        tmpLineXY[4*i+1] = (float)minYCanvas;
                        tmpLineXY[4*i+2] = x;
                        tmpLineXY[4*i+3] = y;
                    }
                }
                c.drawLines(tmpLineXY, 4*beginFreqPt, 4*(endFreqPt-beginFreqPt), linePaint);
            }
            c.restore();
        }

        // spectrum line
        c.save();
        matrix.reset();
        matrix.setTranslate(0, (float)(-axisY.getShift()*canvasHeight));
        matrix.postScale(1, (float)axisY.getZoom());
        c.concat(matrix);
        float o_x = (float)axisX.pixelFromV(beginFreqPt * freqDelta);
        float o_y = (float)axisY.pixelNoZoomFromV(clampDB(db_cache[beginFreqPt]));
        for (int i = beginFreqPt+1; i < endFreqPt; i++) {
            float x = (float)axisX.pixelFromV(i * freqDelta);
            float y = (float)axisY.pixelNoZoomFromV(clampDB(db_cache[i]));
            tmpLineXY[4*i  ] = o_x;
            tmpLineXY[4*i+1] = o_y;
            tmpLineXY[4*i+2] = x;
            tmpLineXY[4*i+3] = y;
            o_x = x;
            o_y = y;
        }
        c.drawLines(tmpLineXY, 4*(beginFreqPt+1), 4*(endFreqPt-beginFreqPt-1), linePaintLight);
        c.restore();

        AnalyzerGraphic.setIsBusy(false);
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
