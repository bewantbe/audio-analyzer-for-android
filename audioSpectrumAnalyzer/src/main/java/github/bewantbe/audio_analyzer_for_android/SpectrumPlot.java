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
import static java.lang.Math.log10;
import static java.lang.Math.round;

/**
 * The spectrum plot part of AnalyzerGraphic
 */

class SpectrumPlot {
    static final String TAG = "SpectrumPlot:";
    boolean showLines;
    private Paint linePaint, linePaintLight;
    private Paint cursorPaint;
    private Paint gridPaint;
    private Paint labelPaint;
    private int canvasHeight=0, canvasWidth=0;

    private GridLabel fqGridLabel;
    private GridLabel dbGridLabel;
    private float DPRatio;
    float gridDensity = 1/85f;  // every 85 pixel one grid line, on average

    float cursorFreq, cursorDB;  // cursor location
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

    void setCanvas(int _canvasWidth, int _canvasHeight, RectF axisBounds) {
//        Log.i("SpectrumPlot", "setCanvas: W="+_canvasWidth+"  H="+_canvasHeight);
        canvasWidth  = _canvasWidth;
        canvasHeight = _canvasHeight;
        fqGridLabel.setDensity(canvasWidth * gridDensity / DPRatio);
        dbGridLabel.setDensity(canvasHeight * gridDensity / DPRatio);
        axisX.setNCanvasPixel(canvasWidth);
        axisY.setNCanvasPixel(canvasHeight);
        if (axisBounds != null) {
            axisX.setBounds(axisBounds.left, axisBounds.right);
            axisY.setBounds(axisBounds.top,  axisBounds.bottom);
        }
    }

    void setZooms(float xZoom, float xShift, float yZoom, float yShift) {
        axisX.setZoomShift(xZoom, xShift);
        axisY.setZoomShift(yZoom, yShift);
    }

    // Linear or Logarithmic frequency axis
    void setFreqAxisMode(ScreenPhysicalMapping.Type mapType, float freq_lower_bound) {
        axisX.setMappingType(mapType, freq_lower_bound);
        if (mapType == ScreenPhysicalMapping.Type.LOG) {
            fqGridLabel.setGridType(GridLabel.Type.FREQ_LOG);
        } else {
            fqGridLabel.setGridType(GridLabel.Type.FREQ);
        }
    }

    float getFreqMin() { return axisX.vMinInView(); }
    float getFreqMax() { return axisX.vMaxInView(); }

    boolean isAlmostInteger(float x) {
        // return x % 1 == 0;
        float i = round(x);
        if (i == 0) {
            return abs(x) < 1.2e-7;  // 2^-23 = 1.1921e-07
        } else {
            return abs(x - i) / i < 1.2e-7;
        }
    }

    // The coordinate frame of this function is identical to its view (id=plot).
    private void drawGridLabels(Canvas c) {
        float textHeigh  = labelPaint.getFontMetrics(null);
        float widthHz    = labelPaint.measureText("Hz");
        float widthDigit = labelPaint.measureText("0");
        float xPos, yPos;
        int notShowNextLabel = 0;

        // draw freq label
        yPos = textHeigh;
        for(int i = 0; i < fqGridLabel.strings.length; i++) {
            xPos = axisX.pixelFromV((float)fqGridLabel.values[i]);
            // Avoid label overlap:
            // (1) No overlap to "Hz";
            // (2) If no (1), no overlap to label 1, 10, 100, 1000, 10000, 1k, 10k;
            // (3) If no (1) and (2), no overlap to previous label.
            float thisDigitWidth = widthDigit*fqGridLabel.strings[i].length() + 0.3f*widthDigit;
            if (xPos + thisDigitWidth + 1.3f*widthHz > canvasWidth) {
                continue;
            }
            if (notShowNextLabel > 0) {
                notShowNextLabel--;
                continue;
            }
            int j = i+1;
            while (j < fqGridLabel.strings.length &&
                   xPos + thisDigitWidth > axisX.pixelFromV((float)fqGridLabel.values[j])) {
                // label i shadows label j (case (3))
                notShowNextLabel++;
                if (isAlmostInteger((float)log10(fqGridLabel.values[j]))) {
                    // do not show this label (case (2))
                    if (axisX.pixelFromV((float)fqGridLabel.values[j]) +
                            widthDigit*fqGridLabel.strings[j].length() + 0.3f*widthDigit
                            + 1.3f*widthHz <= canvasWidth) {
                        notShowNextLabel = -1;
                        break;
                    }
                }
                j++;
            }
            if (notShowNextLabel == -1) {
                notShowNextLabel = j - i - 1;  // show the label in case (2)
                continue;
            }
            c.drawText(fqGridLabel.chars[i], 0, fqGridLabel.strings[i].length(), xPos, yPos, labelPaint);
        }
        c.drawLine(0, 0, canvasWidth, 0, labelPaint);
        c.drawText("Hz", canvasWidth - 1.3f*widthHz, yPos, labelPaint);

        // draw dB label
        xPos = 0.4f*widthHz;
        for(int i = 0; i < dbGridLabel.strings.length; i++) {
            yPos = axisY.pixelFromV((float)dbGridLabel.values[i]);
            if (yPos + 1.3f*widthHz > canvasHeight) continue;
            c.drawText(dbGridLabel.chars[i], 0, dbGridLabel.strings[i].length(), xPos, yPos, labelPaint);
        }
        c.drawLine(0, 0, 0, canvasHeight, labelPaint);
        c.drawText("dB", xPos, canvasHeight - 0.4f*widthHz, labelPaint);
    }

    private void drawGridLines(Canvas c) {
        for(int i = 0; i < fqGridLabel.values.length; i++) {
            float xPos = axisX.pixelFromV((float)fqGridLabel.values[i]);
            c.drawLine(xPos, 0, xPos, canvasHeight, gridPaint);
        }
        for(int i = 0; i < dbGridLabel.values.length; i++) {
            float yPos = axisY.pixelFromV((float)dbGridLabel.values[i]);
            c.drawLine(0, yPos, canvasWidth, yPos, gridPaint);
        }
    }

    private void drawGridTicks(Canvas c) {
        for(int i = 0; i < fqGridLabel.ticks.length; i++) {
            float xPos = axisX.pixelFromV((float)fqGridLabel.ticks[i]);
            c.drawLine(xPos, 0, xPos, 0.02f * canvasHeight, gridPaint);
        }
        for(int i = 0; i < dbGridLabel.ticks.length; i++) {
            float yPos = axisY.pixelFromV((float)dbGridLabel.ticks[i]);
            c.drawLine(0, yPos, 0.02f * canvasWidth, yPos, gridPaint);
        }
    }

    private float clampDB(float value) {
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
                Log.i(TAG, "drawSpectrumOnCanvas(): new db_cache");
                db_cache = new double[_db.length];
            }
            System.arraycopy(_db, 0, db_cache, 0, _db.length);
        }

        float canvasMinFreq = axisX.vMinInView();
        float canvasMaxFreq = axisX.vMaxInView();
        // There are db.length frequency points, including DC component
        float freqDelta = (axisX.vHigherBound - axisX.vLowerBound) / (db_cache.length - 1);
        int nFreqPointsTotal = db_cache.length - 1;
        int beginFreqPt = (int)ceil (canvasMinFreq / freqDelta);    // pointer to tmpLineXY
        int endFreqPt   = (int)floor(canvasMaxFreq / freqDelta) + 1;
        float minYCanvas = axisY.pixelNoZoomFromV(AnalyzerGraphic.minDB);

        // add one more boundary points
        if (beginFreqPt > 0) {
            beginFreqPt -= 1;
        }
        if (beginFreqPt == 0 && axisX.mapTypeInt == ScreenPhysicalMapping.Type.LOG.getValue()) {
            beginFreqPt++;
        }
        if (endFreqPt < db_cache.length) {
            endFreqPt += 1;
        }

        if (tmpLineXY.length != 4*(db_cache.length)) {
            Log.i(TAG, "drawSpectrumOnCanvas(): new tmpLineXY");
            tmpLineXY = new float[4*(db_cache.length)];
        }

        // spectrum bar
        if (showLines == false) {
            c.save();
            // If bars are very close to each other, draw bars as lines
            // Otherwise, zoom in so that lines look like bars.
            if (endFreqPt - beginFreqPt >= axisX.nCanvasPixel / 2
                    || axisX.mapTypeInt != ScreenPhysicalMapping.Type.LINEAR.getValue()) {
                matrix.reset();
                matrix.setTranslate(0, -axisY.shift * canvasHeight);
                matrix.postScale(1, axisY.zoom);
                c.concat(matrix);
                //      float barWidthInPixel = 0.5f * freqDelta / (canvasMaxFreq - canvasMinFreq) * canvasWidth;
                //      if (barWidthInPixel > 2) {
                //        linePaint.setStrokeWidth(barWidthInPixel);
                //      } else {
                //        linePaint.setStrokeWidth(0);
                //      }
                // plot directly to the canvas
                for (int i = beginFreqPt; i < endFreqPt; i++) {
                    float x = axisX.pixelFromV(i * freqDelta);
                    float y = axisY.pixelNoZoomFromV(clampDB((float) db_cache[i]));
                    if (y != canvasHeight) { // ...forgot why
                        tmpLineXY[4 * i] = x;
                        tmpLineXY[4 * i + 1] = minYCanvas;
                        tmpLineXY[4 * i + 2] = x;
                        tmpLineXY[4 * i + 3] = y;
                    }
                }
                c.drawLines(tmpLineXY, 4*beginFreqPt, 4*(endFreqPt-beginFreqPt), linePaint);
            } else {
                // for zoomed linear scale
                int pixelStep = 2;  // each bar occupy this virtual pixel
                matrix.reset();
                float extraPixelAlignOffset = 0.0f;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//          // There is an shift for Android 4.4, while no shift for Android 2.3
//          // I guess that is relate to GL ES acceleration
//          if (c.isHardwareAccelerated()) {
//            extraPixelAlignOffset = 0.5f;
//          }
//        }
                matrix.setTranslate(-axisX.shift * nFreqPointsTotal * pixelStep - extraPixelAlignOffset,
                                    -axisY.shift * canvasHeight);
                matrix.postScale(canvasWidth / ((canvasMaxFreq - canvasMinFreq) / freqDelta * pixelStep), axisY.zoom);
                c.concat(matrix);
                // fill interval same as canvas pixel width.
                for (int i = beginFreqPt; i < endFreqPt; i++) {
                    float x = i * pixelStep;
                    float y = axisY.pixelNoZoomFromV(clampDB((float) db_cache[i]));
                    if (y != canvasHeight) {
                        tmpLineXY[4*i  ] = x;
                        tmpLineXY[4*i+1] = minYCanvas;
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
        matrix.setTranslate(0, -axisY.shift*canvasHeight);
        matrix.postScale(1, axisY.zoom);
        c.concat(matrix);
        float o_x = axisX.pixelFromV(beginFreqPt * freqDelta);
        float o_y = axisY.pixelNoZoomFromV(clampDB((float)db_cache[beginFreqPt]));
        for (int i = beginFreqPt+1; i < endFreqPt; i++) {
            float x = axisX.pixelFromV(i * freqDelta);
            float y = axisY.pixelNoZoomFromV(clampDB((float)db_cache[i]));
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
    void setCursor(float x, float y) {
        cursorFreq = axisX.vFromPixel(x);  // frequency
        cursorDB   = axisY.vFromPixel(y);  // decibel
    }

    float getCursorFreq() {
        return  canvasWidth == 0 ? 0 : cursorFreq;
    }

    float getCursorDB() {
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
        cX = axisX.pixelFromV(cursorFreq);
        cY = axisY.pixelFromV(cursorDB);
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
        drawGridTicks(c);
        drawGridLabels(c);
    }
}
