package github.bewantbe.audio_analyzer_for_android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;

import java.util.Arrays;

/**
 * The spectrogram plot part of AnalyzerGraphic
 */

class SpectrogramPlot {
    static final String TAG = "SpectrogramPlot:";
    static final String[] axisLabels = {"Hz", "dB", "Sec"};
    boolean showFreqAlongX = false;

    private static final int[] cma = ColorMapArray.hot;
    int[] spectrogramColors = new int[0];  // int:ARGB, nFreqPoints columns, nTimePoints rows
    int[] spectrogramColorsShifting;       // temporarily of spectrogramColors for shifting mode
    private int showModeSpectrogram = 1;           // 0: moving (shifting) spectrogram, 1: overwriting in loop
    private double timeWatch = 4.0;
    private volatile int timeMultiplier = 1;  // should be accorded with nFFTAverage in AnalyzerActivity
    private boolean bShowTimeAxis = true;
    int nFreqPoints;
    int nTimePoints;
    int spectrogramColorsPt;          // pointer to the row to be filled (row major)
    private Matrix matrixSpectrogram = new Matrix();
    private Paint smoothBmpPaint;
    private Paint backgroundPaint;
    private Paint cursorPaint;
    private Paint gridPaint, rulerBrightPaint;
    private Paint labelPaint;

    private GridLabel fqGridLabel;
    private GridLabel tmGridLabel;
    private float DPRatio;
    private float gridDensity = 1/85f;  // every 85 pixel one grid line, on average
    private float cursorFreq;
    private int canvasHeight=0, canvasWidth=0;
    ScreenPhysicalMapping axisFreq;
    ScreenPhysicalMapping axisTime;
    double dBLowerBound = -120;

    SpectrogramPlot(Context _context) {
        DPRatio = _context.getResources().getDisplayMetrics().density;

        gridPaint = new Paint();
        gridPaint.setColor(Color.DKGRAY);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(0.6f * DPRatio);

        cursorPaint = new Paint(gridPaint);
        cursorPaint.setColor(Color.parseColor("#00CD00"));

        rulerBrightPaint = new Paint();
        rulerBrightPaint.setColor(Color.rgb(99, 99, 99));  // 99: between Color.DKGRAY and Color.GRAY
        rulerBrightPaint.setStyle(Paint.Style.STROKE);
        rulerBrightPaint.setStrokeWidth(1);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.GRAY);
        labelPaint.setTextSize(14.0f * DPRatio);
        labelPaint.setTypeface(Typeface.MONOSPACE);  // or Typeface.SANS_SERIF

        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.BLACK);

        cursorFreq = 0f;

        fqGridLabel = new GridLabel(GridLabel.Type.FREQ, canvasWidth  * gridDensity / DPRatio);
        tmGridLabel = new GridLabel(GridLabel.Type.TIME, canvasHeight * gridDensity / DPRatio);

        axisFreq = new ScreenPhysicalMapping(0, 0, 0, ScreenPhysicalMapping.Type.LINEAR);
        axisTime = new ScreenPhysicalMapping(0, 0, 0, ScreenPhysicalMapping.Type.LINEAR);

        Log.i(TAG, "SpectrogramPlot() initialized");
    }

    void setCanvas(int _canvasWidth, int _canvasHeight, RectF axisBounds) {
        Log.i(TAG, "setCanvas()");
        canvasWidth  = _canvasWidth;
        canvasHeight = _canvasHeight;
        if (showFreqAlongX) {
            axisFreq.setNCanvasPixel(canvasWidth - labelBeginX);
            axisTime.setNCanvasPixel(labelBeginY);
        } else {
            axisTime.setNCanvasPixel(canvasWidth - labelBeginX);
            axisFreq.setNCanvasPixel(labelBeginY);
        }
        if (axisBounds != null) {
            if (showFreqAlongX) {
                axisFreq.setBounds(axisBounds.left, axisBounds.right);
                axisTime.setBounds(axisBounds.top,  axisBounds.bottom);
            } else {
                axisTime.setBounds(axisBounds.left, axisBounds.right);
                axisFreq.setBounds(axisBounds.top,  axisBounds.bottom);
            }
            if (showModeSpectrogram == 0) {
                float b1 = axisTime.vLowerBound;
                float b2 = axisTime.vHigherBound;
                axisTime.setBounds(b2, b1);
            }
        }
        fqGridLabel = new GridLabel(GridLabel.Type.FREQ, canvasWidth  * gridDensity / DPRatio);
        tmGridLabel = new GridLabel(GridLabel.Type.TIME, canvasHeight * gridDensity / DPRatio);
    }

    void setZooms(float xZoom, float xShift, float yZoom, float yShift) {
        Log.i(TAG, "setZooms()");
        if (showFreqAlongX) {
            axisFreq.setZoomShift(xZoom, xShift);
            axisTime.setZoomShift(yZoom, yShift);
        } else {
            axisFreq.setZoomShift(yZoom, yShift);
            axisTime.setZoomShift(xZoom, xShift);
        }
    }

    void setupSpectrogram(int sampleRate, int fftLen, double timeDurationE, int nAve) {
        timeWatch = timeDurationE;
        timeMultiplier = nAve;
        double timeInc = fftLen / 2.0 / sampleRate;  // time of each slice. /2.0 due to overlap window
        synchronized (this) {
            boolean bNeedClean = nFreqPoints != fftLen / 2;
            nFreqPoints = fftLen / 2;                    // no direct current term
            nTimePoints = (int)Math.ceil(timeWatch / timeInc);
            if (spectrogramColors == null || spectrogramColors.length != nFreqPoints * nTimePoints) {
                spectrogramColors = new int[nFreqPoints * nTimePoints];
                spectrogramColorsShifting = new int[nFreqPoints * nTimePoints];
                bNeedClean = true;
            }
            if (spectrogramColorsPt >= nTimePoints) {
                Log.w(TAG, "setupSpectrogram(): Should not happen!!");
                bNeedClean = true;
            }
            if (bNeedClean) {
                spectrogramColorsPt = 0;
                Arrays.fill(spectrogramColors, 0);
            }
        }
        Log.i(TAG, "setupSpectrogram() done"+
                "\n  sampleRate    = " + sampleRate +
                "\n  fftLen        = " + fftLen +
                "\n  timeDurationE = " + timeDurationE);
    }

    // Draw axis, start from (labelBeginX, labelBeginY) in the canvas coordinate
    // drawOnXAxis == true : draw on X axis, otherwise Y axis
    private void drawAxis(Canvas c, float labelBeginX, float labelBeginY, float ng, boolean drawOnXAxis,
                          float axisMin, float axisMax, GridLabel.Type scale_mode) {
        int scale_mode_id = scale_mode.getValue();
        float canvasMin;
        float canvasMax;
        if (drawOnXAxis) {
            canvasMin = labelBeginX;
            canvasMax = canvasWidth;
        } else {
            canvasMin = labelBeginY;
            canvasMax = 0;
        }
        GridLabel[] gridLabelArray = {fqGridLabel, null, tmGridLabel};
        gridLabelArray[scale_mode_id].updateGridLabels(axisMin, axisMax);
        String axisLabel = axisLabels[scale_mode_id];

        double[][] gridPoints = {gridLabelArray[scale_mode_id].values, gridLabelArray[scale_mode_id].ticks};
        StringBuilder[] gridPointsStr = gridLabelArray[scale_mode_id].strings;
        char[][]        gridPointsSt  = gridLabelArray[scale_mode_id].chars;

        // plot axis mark
        float posAlongAxis;
        float textHeigh     = labelPaint.getFontMetrics(null);
        float labelLargeLen = 0.5f * textHeigh;
        float labelSmallLen = 0.6f*labelLargeLen;
        for(int i = 0; i < gridPoints[1].length; i++) {
            posAlongAxis =((float)gridPoints[1][i] - axisMin) / (axisMax-axisMin) * (canvasMax - canvasMin) + canvasMin;
            if (drawOnXAxis) {
                c.drawLine(posAlongAxis, labelBeginY, posAlongAxis, labelBeginY+labelSmallLen, gridPaint);
            } else {
                c.drawLine(labelBeginX-labelSmallLen, posAlongAxis, labelBeginX, posAlongAxis, gridPaint);
            }
        }
        for(int i = 0; i < gridPoints[0].length; i++) {
            posAlongAxis = ((float)gridPoints[0][i] - axisMin) / (axisMax-axisMin) * (canvasMax - canvasMin) + canvasMin;
            if (drawOnXAxis) {
                c.drawLine(posAlongAxis, labelBeginY, posAlongAxis, labelBeginY+labelLargeLen, rulerBrightPaint);
            } else {
                c.drawLine(labelBeginX-labelLargeLen, posAlongAxis, labelBeginX, posAlongAxis, rulerBrightPaint);
            }
        }
        if (drawOnXAxis) {
            c.drawLine(canvasMin, labelBeginY, canvasMax, labelBeginY, labelPaint);
        } else {
            c.drawLine(labelBeginX, canvasMin, labelBeginX, canvasMax, labelPaint);
        }

        // plot labels
        float widthDigit = labelPaint.measureText("0");
        float posOffAxis = labelBeginY + 0.3f*labelLargeLen + textHeigh;
        for(int i = 0; i < gridPointsStr.length; i++) {
            posAlongAxis = ((float)gridPoints[0][i] - axisMin) / (axisMax-axisMin) * (canvasMax - canvasMin) + canvasMin;
            if (drawOnXAxis) {
                if (posAlongAxis + widthDigit * gridPointsStr[i].length() > canvasWidth - (axisLabel.length() + .3f)*widthDigit) {
                    continue;
                }
                c.drawText(gridPointsSt[i], 0, gridPointsStr[i].length(), posAlongAxis, posOffAxis, labelPaint);
            } else {
                if (posAlongAxis - 0.5f*textHeigh < canvasMax + textHeigh) {
                    continue;
                }
                c.drawText(gridPointsSt[i], 0, gridPointsStr[i].length(),
                        labelBeginX - widthDigit * gridPointsStr[i].length() - 0.5f * labelLargeLen, posAlongAxis, labelPaint);
            }
        }
        if (drawOnXAxis) {
            c.drawText(axisLabel, canvasWidth - (axisLabel.length() +.3f)*widthDigit, posOffAxis, labelPaint);
        } else {
            c.drawText(axisLabel, labelBeginX - widthDigit * axisLabel.length() - 0.5f * labelLargeLen, canvasMax+textHeigh, labelPaint);
        }
    }

    // Draw time axis for spectrogram
    // Working in the original canvas frame
    private void drawTimeAxis(Canvas c, float labelBeginX, float labelBeginY, float nt, boolean drawOnXAxis) {
//            Log.i(TAG, "drawTimeAxis(): max=" + getTimeMax() + "  min=" + getTimeMin());
//        drawAxis(c, labelBeginX, labelBeginY, nt, drawOnXAxis,
//                getTimeMax(), getTimeMin(), GridLabel.Type.TIME);

        if (showFreqAlongX) {
            drawAxis(c, labelBeginX, labelBeginY, nt, drawOnXAxis,
                    getTimeMax(), getTimeMin(), GridLabel.Type.TIME);
        } else {
            drawAxis(c, labelBeginX, labelBeginY, nt, drawOnXAxis,
                    getTimeMin(), getTimeMax(), GridLabel.Type.TIME);
        }
    }

    // Draw frequency axis for spectrogram
    // Working in the original canvas frame
    // nx: number of grid lines on average
    private void drawFreqAxis(Canvas c, float labelBeginX, float labelBeginY, float nx, boolean drawOnXAxis) {
        if (showFreqAlongX) {
            drawAxis(c, labelBeginX, labelBeginY, nx, drawOnXAxis,
                    getFreqMin(), getFreqMax(), GridLabel.Type.FREQ);
        } else {
            drawAxis(c, labelBeginX, labelBeginY, nx, drawOnXAxis,
                    getFreqMax(), getFreqMin(), GridLabel.Type.FREQ);
        }
    }

    private float getTimeMin() {
        return axisTime.vMinInView();
    }

    private float getTimeMax() {
        return axisTime.vMaxInView();
    }

    float getCursorFreq() {
        return  canvasWidth == 0 ? 0 : cursorFreq;
    }

    void setCursor(float x, float y) {
        if (showFreqAlongX) {
            //cursorFreq = axisBounds.width() * (xShift + (x-labelBeginX)/(canvasWidth-labelBeginX)/xZoom);  // frequency
            cursorFreq = axisFreq.vFromPixel(x - labelBeginX);
        } else {
            //cursorFreq = axisBounds.width() * (1 - yShift - y/labelBeginY/yZoom);  // frequency
            cursorFreq = axisFreq.vFromPixel(y);
        }
        if (cursorFreq < 0) {
            cursorFreq = 0;
        }
    }

    void hideCursor() {
        cursorFreq = 0;
    }

    float labelBeginX, labelBeginY;

    void drawCursor(Canvas c) {
        float cX, cY;
        // Show only the frequency cursor
        if (showFreqAlongX) {
            // cX = (cursorFreq / axisBounds.width() - xShift) * xZoom * (canvasWidth-labelBeginX) + labelBeginX;
            cX = axisFreq.pixelFromV(cursorFreq) + labelBeginX;
            if (cursorFreq != 0) {
                c.drawLine(cX, 0, cX, labelBeginY, cursorPaint);
            }
        } else {
            //cY = (1 - yShift - cursorFreq / axisBounds.width()) * yZoom * labelBeginY;
            cY = axisFreq.pixelFromV(cursorFreq);
            if (cursorFreq != 0) {
                c.drawLine(labelBeginX, cY, canvasWidth, cY, cursorPaint);
            }
        }
    }

    float getFreqMax() {
        return axisFreq.vMaxInView();
//        if (showFreqAlongX) {
//            return axisBounds.width() * (xShift + 1 / xZoom);
//        } else {
//            return axisBounds.width() * (1 - yShift);
//        }
    }

    float getFreqMin() {
        return axisFreq.vMinInView();
//        if (showFreqAlongX) {
//            return axisBounds.width() * xShift;
//        } else {
//            return axisBounds.width() * (1 - yShift - 1/yZoom);
//        }
    }

    void setTimeMultiplier(int nAve) {
        timeMultiplier = nAve;
        axisTime.vHigherBound = (float)(timeWatch * timeMultiplier);
    }

    void setShowTimeAxis(boolean bSTA) {
        bShowTimeAxis = bSTA;
    }

    void setSpectrogramModeShifting(boolean b) {
        if ((showModeSpectrogram==0) != b) {
            // mode change, swap time bounds.
            float b1 = axisTime.vLowerBound;
            float b2 = axisTime.vHigherBound;
            axisTime.setBounds(b2, b1);
        }
        if (b) {
            showModeSpectrogram = 0;
        } else {
            showModeSpectrogram = 1;
        }
    }

    void setShowFreqAlongX(boolean b) {
//        if (showMode == 1 && showFreqAlongX != b) {
//            // match zooming
//            float t;
//            if (showFreqAlongX) {
//                t = xShift;
//                xShift = yShift;
//                yShift = 1 - t - 1/xZoom;
//            } else {
//                t = yShift;
//                yShift = xShift;
//                xShift = 1 - t - 1/yZoom;
//            }
//            t = xZoom;
//            xZoom = yZoom;
//            yZoom = t;
//        }
        if (showFreqAlongX != b) {
            float t = axisFreq.nCanvasPixel;
            axisFreq.setNCanvasPixel(axisTime.nCanvasPixel);
            axisTime.setNCanvasPixel(t);
            // TODO: exchange zoom shift
        }
        showFreqAlongX = b;
    }

    void setSmoothRender(boolean b) {
        if (b) {
            smoothBmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        } else {
            smoothBmpPaint = null;
        }
    }

    public int colorFromDB(double d) {
        if (d >= 0) {
            return cma[0];
        }
        if (d <= dBLowerBound || Double.isInfinite(d) || Double.isNaN(d)) {
            return cma[cma.length-1];
        }
        return cma[(int)(cma.length * d / dBLowerBound)];
    }

    // Will be called in another thread (SamplingLoop)
    void saveRowSpectrumAsColor(final double[] db) {
        synchronized (this) {  // essentially a lock on spectrogramColors
            int c;
            int pRef;
            double d;
            pRef = spectrogramColorsPt*nFreqPoints - 1;
            for (int i = 1; i < db.length; i++) {  // no direct current term
                d = db[i];
                if (d >= 0) {
                    c = cma[0];
                } else if (d <= dBLowerBound || Double.isInfinite(d) || Double.isNaN(d)) {
                    c = cma[cma.length-1];
                } else {
                    c = cma[(int)(cma.length * d / dBLowerBound)];
                }
                spectrogramColors[pRef + i] = c;
            }
            spectrogramColorsPt++;
            if (spectrogramColorsPt >= nTimePoints) {
                spectrogramColorsPt = 0;
            }
        }
    }

    private float getLabelBeginY() {
        float textHeigh     = labelPaint.getFontMetrics(null);
        float labelLaegeLen = 0.5f * textHeigh;
        if (!showFreqAlongX && !bShowTimeAxis) {
            return canvasHeight;
        } else {
            return canvasHeight - 0.6f*labelLaegeLen - textHeigh;
        }
    }

    // Left margin for ruler
    private float getLabelBeginX() {
        float textHeigh     = labelPaint.getFontMetrics(null);
        float labelLaegeLen = 0.5f * textHeigh;
        if (showFreqAlongX) {
            if (bShowTimeAxis) {
                int j = 3;
                for (int i = 0; i < tmGridLabel.strings.length; i++) {
                    if (j < tmGridLabel.strings[i].length()) {
                        j = tmGridLabel.strings[i].length();
                    }
                }
                return 0.6f*labelLaegeLen + j*0.5f*textHeigh;
            } else {
                return 0;
            }
        } else {
            return 0.6f*labelLaegeLen + 2.5f*textHeigh;
        }
    }

    // Plot spectrogram with axis and ticks on the whole canvas c
    void drawSpectrogramPlot(Canvas c) {
        labelBeginX = getLabelBeginX();  // this seems will make the scaling gesture inaccurate
        labelBeginY = getLabelBeginY();
        if (showFreqAlongX) {
            axisFreq.setNCanvasPixel(canvasWidth-labelBeginX);
            axisTime.setNCanvasPixel(labelBeginY);
        } else {
            axisTime.setNCanvasPixel(canvasWidth-labelBeginX);
            axisFreq.setNCanvasPixel(labelBeginY);
        }
        // show Spectrogram
        float halfFreqResolutionShift;  // move the color patch to match the center frequency
        matrixSpectrogram.reset();
        if (showFreqAlongX) {
            // when xZoom== 1: nFreqPoints -> canvasWidth; 0 -> labelBeginX
            matrixSpectrogram.postScale(axisFreq.zoom * axisFreq.nCanvasPixel / nFreqPoints,
                    axisTime.zoom * axisTime.nCanvasPixel / nTimePoints);
            halfFreqResolutionShift = axisFreq.zoom * axisFreq.nCanvasPixel / nFreqPoints / 2;
            matrixSpectrogram.postTranslate((labelBeginX - axisFreq.shift * axisFreq.zoom * axisFreq.nCanvasPixel + halfFreqResolutionShift),
                    -axisTime.shift * axisTime.zoom * axisTime.nCanvasPixel);
        } else {
            // postRotate() will make c.drawBitmap about 20% slower, don't know why
            matrixSpectrogram.postRotate(-90);
            matrixSpectrogram.postScale(axisTime.zoom * axisTime.nCanvasPixel / nTimePoints,
                    axisFreq.zoom * axisFreq.nCanvasPixel / nFreqPoints);
            // (1-yShift) is relative position of shift (after rotation)
            // yZoom*labelBeginY is canvas length in frequency direction in pixel unit
            halfFreqResolutionShift = axisFreq.zoom * axisFreq.nCanvasPixel / nFreqPoints/2;
            matrixSpectrogram.postTranslate((labelBeginX - axisTime.shift * axisTime.zoom * axisTime.nCanvasPixel),
                    (1-axisFreq.shift) * axisFreq.zoom * axisFreq.nCanvasPixel - halfFreqResolutionShift);
        }
        c.concat(matrixSpectrogram);

        // public void drawBitmap (int[] colors, int offset, int stride, float x, float y,
        //                         int width, int height, boolean hasAlpha, Paint paint)
//      long t = SystemClock.uptimeMillis();
        // drawBitmap(int[] ...) was deprecated in API level 21.
        // http://developer.android.com/reference/android/graphics/Canvas.html#drawBitmap(int[], int, int, float, float, int, int, boolean, android.graphics.Paint)
        // Consider use Bitmap
        // http://developer.android.com/reference/android/graphics/Bitmap.html#setPixels(int[], int, int, int, int, int, int)
        synchronized (this) {
            if (showModeSpectrogram == 0) {
                System.arraycopy(spectrogramColors, 0, spectrogramColorsShifting,
                        (nTimePoints-spectrogramColorsPt)*nFreqPoints, spectrogramColorsPt*nFreqPoints);
                System.arraycopy(spectrogramColors, spectrogramColorsPt*nFreqPoints, spectrogramColorsShifting,
                        0, (nTimePoints-spectrogramColorsPt)*nFreqPoints);
                c.drawBitmap(spectrogramColorsShifting, 0, nFreqPoints, 0, 0,
                        nFreqPoints, nTimePoints, false, smoothBmpPaint);
            } else {
                c.drawBitmap(spectrogramColors, 0, nFreqPoints, 0, 0,
                        nFreqPoints, nTimePoints, false, smoothBmpPaint);
            }
        }
//      Log.i(TAG, " onDraw: dt = " + (SystemClock.uptimeMillis() - t) + " ms");
        if (showModeSpectrogram == 1) {
            Paint cursorPaint0 = new Paint(cursorPaint);
            cursorPaint0.setStyle(Paint.Style.STROKE);
            cursorPaint0.setStrokeWidth(0);
            c.drawLine(0, spectrogramColorsPt, nFreqPoints, spectrogramColorsPt, cursorPaint0);
        }
        c.restore();
        drawCursor(c);
        if (showFreqAlongX) {
            c.drawRect(0, labelBeginY, canvasWidth, canvasHeight, backgroundPaint);
            drawFreqAxis(c, labelBeginX, labelBeginY, canvasWidth * gridDensity / DPRatio, showFreqAlongX);
            if (labelBeginX > 0) {
                c.drawRect(0, 0, labelBeginX, labelBeginY, backgroundPaint);
                drawTimeAxis(c, labelBeginX, labelBeginY, canvasHeight * gridDensity / DPRatio, !showFreqAlongX);
            }
        } else {
            c.drawRect(0, 0, labelBeginX, labelBeginY, backgroundPaint);
            drawFreqAxis(c, labelBeginX, labelBeginY, canvasHeight * gridDensity / DPRatio, showFreqAlongX);
            if (labelBeginY != canvasHeight) {
                c.drawRect(0, labelBeginY, canvasWidth, canvasHeight, backgroundPaint);
                drawTimeAxis(c, labelBeginX, labelBeginY, canvasWidth * gridDensity / DPRatio, !showFreqAlongX);
            }
        }
    }
}
