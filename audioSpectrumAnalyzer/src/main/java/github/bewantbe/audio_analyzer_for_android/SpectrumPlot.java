package github.bewantbe.audio_analyzer_for_android;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * The spectrum plot part of AnalyzerGraphic
 */

class SpectrumPlot {
    boolean showLines;
    double dBLowerBound = -120;
    private Paint linePaint, linePaintLight;
    private Paint cursorPaint;
    private Paint gridPaint;
    private Paint labelPaint;
    private Matrix matrix = new Matrix();

    private GridLabel fqGridLabel;
    private GridLabel dbGridLabel;

    private float minDB = -144f;    // hard lower bound for dB
    private float maxDB = 12f;      // hard upper bound for dB
    private float DPRatio;
    private float cursorFreq, cursorDB;  // cursor location

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
        Resources res = _context.getResources();
        minDB = Float.parseFloat(res.getString(R.string.max_DB_range));

        float gridDensity = 1/85f;  // every 85 pixel one grid line, on average
        fqGridLabel = new GridLabel(GridLabel.GridScaleType.FREQ, canvasWidth * gridDensity / DPRatio);
        dbGridLabel = new GridLabel(GridLabel.GridScaleType.DB,   canvasHeight * gridDensity / DPRatio);
    }

    // The coordinate frame of this function is identical to its view (id=plot).
    private void drawGridLabels(Canvas c) {
        float textHeigh  = labelPaint.getFontMetrics(null);
        float widthHz    = labelPaint.measureText("Hz");
        float widthDigit = labelPaint.measureText("0");
        float xPos, yPos;
        yPos = textHeigh;
        for(int i = 0; i < fqGridLabel.strings.length; i++) {
            xPos = canvasViewX4axis((float)fqGridLabel.values[i]);
            if (xPos + widthDigit*fqGridLabel.strings[i].length() + 1.5f*widthHz> canvasWidth) {
                continue;
            }
            c.drawText(fqGridLabel.chars[i], 0, fqGridLabel.strings[i].length(), xPos, yPos, labelPaint);
        }
        c.drawLine(0, 0, canvasWidth, 0, labelPaint);

        c.drawText("Hz", canvasWidth - 1.3f*widthHz, yPos, labelPaint);
        xPos = 0.4f*widthHz;
        for(int i = 0; i < dbGridLabel.strings.length; i++) {
            yPos = canvasViewY4axis((float)dbGridLabel.values[i]);
            if (yPos + 1.3f*widthHz > canvasHeight) continue;
            c.drawText(dbGridLabel.chars[i], 0, dbGridLabel.strings[i].length(), xPos, yPos, labelPaint);
        }
        c.drawLine(0, 0, 0, canvasHeight, labelPaint);
        c.drawText("dB", xPos, canvasHeight - 0.4f*widthHz, labelPaint);
    }

    private void drawGridLines(Canvas c) {
        for(int i = 0; i < fqGridLabel.values.length; i++) {
            float xPos = canvasViewX4axis((float) fqGridLabel.values[i]);
            c.drawLine(xPos, 0, xPos, canvasHeight, gridPaint);
        }
        for(int i = 0; i < dbGridLabel.values.length; i++) {
            float yPos = canvasViewY4axis((float)dbGridLabel.values[i]);
            c.drawLine(0, yPos, canvasWidth, yPos, gridPaint);
        }
    }

    private void drawGridTicks(Canvas c) {
        for(int i = 0; i < fqGridLabel.ticks.length; i++) {
            float xPos = canvasViewX4axis((float) fqGridLabel.ticks[i]);
            c.drawLine(xPos, 0, xPos, 0.02f * canvasHeight, gridPaint);
        }
        for(int i = 0; i < dbGridLabel.ticks.length; i++) {
            float yPos = canvasViewY4axis((float)dbGridLabel.ticks[i]);
            c.drawLine(0, yPos, 0.02f * canvasWidth, yPos, gridPaint);
        }
    }

    private float clampDB(float value) {
        if (value < minDB || Double.isNaN(value)) {
            value = minDB;
        }
        return value;
    }

    float[] tmpLineXY = new float[0];

//    private void computeMatrix() {
//        matrix.reset();
//        matrix.setTranslate(-xShift*canvasWidth, -yShift*canvasHeight);
//        matrix.postScale(xZoom, yZoom);
//    }

    double[] db_cache = null;

    // Plot the spectrum into the Canvas c
    public void drawSpectrumOnCanvas(Canvas c, final double[] _db) {
        if (canvasHeight < 1 || _db == null || _db.length == 0) {
            return;
        }
        isBusy = true;

        synchronized (_db) {  // TODO: need lock on savedDBSpectrum, but how?
            if (db_cache == null || db_cache.length != _db.length) {
                db_cache = new double[_db.length];
            }
            System.arraycopy(_db, 0, db_cache, 0, _db.length);
        }

        float canvasMinFreq = getFreqMin();
        float canvasMaxFreq = getFreqMax();
        // There are db.length frequency points, including DC component
        float freqDelta = getFreqBound() / (db_cache.length - 1);
        //int nFreqPoints = (int)Math.floor ((canvasMaxFreq - canvasMinFreq) / freqDelta);
        int nFreqPointsTotal = db.length - 1;
        int beginFreqPt = (int)Math.ceil(canvasMinFreq / freqDelta);
        int endFreqPt = (int)Math.floor(canvasMaxFreq / freqDelta) + 1;
        //float minYCanvas = canvasY4axis(getMinY()); // canvasY4axis(minDB);
        float minYCanvas = canvasY4axis(minDB);

        // add one more boundary points
        if (beginFreqPt > 0) {
            beginFreqPt -= 1;
        }
        if (endFreqPt < db_cache.length) {
            endFreqPt += 1;
        }

        if (tmpLineXY.length != 4*(db_cache.length)) {
            tmpLineXY = new float[4*(db_cache.length)];
        }

        // spectrum bar
        if (showLines == false) {
            c.save();
            if (endFreqPt - beginFreqPt >= getCanvasWidth() / 2) {
                matrix.reset();
                matrix.setTranslate(0, -yShift * canvasHeight);
                matrix.postScale(1, yZoom);
                c.concat(matrix);
                //      float barWidthInPixel = 0.5f * freqDelta / (canvasMaxFreq - canvasMinFreq) * canvasWidth;
                //      if (barWidthInPixel > 2) {
                //        linePaint.setStrokeWidth(barWidthInPixel);
                //      } else {
                //        linePaint.setStrokeWidth(0);
                //      }
                // plot directly to the canvas
                for (int i = beginFreqPt; i < endFreqPt; i++) {
                    float x = (i * freqDelta - canvasMinFreq) / (canvasMaxFreq - canvasMinFreq) * canvasWidth;
                    float y = canvasY4axis(clampDB((float) db_cache[i]));
                    if (y != canvasHeight) {
                        tmpLineXY[4*i  ] = x;
                        tmpLineXY[4*i+1] = minYCanvas;
                        tmpLineXY[4*i+2] = x;
                        tmpLineXY[4*i+3] = y;
                    }
                }
                c.drawLines(tmpLineXY, 4*beginFreqPt, 4*(endFreqPt-beginFreqPt), linePaint);
            } else {
                int pixelStep = 2;
                matrix.reset();
                float extraPixelAlignOffset = 0.0f;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//          // There is an shift for Android 4.4, while no shift for Android 2.3
//          // I guess that is relate to GL ES acceleration
//          if (c.isHardwareAccelerated()) {
//            extraPixelAlignOffset = 0.5f;
//          }
//        }
                matrix.setTranslate(-xShift * nFreqPointsTotal * pixelStep - extraPixelAlignOffset, -yShift * canvasHeight);
                matrix.postScale(canvasWidth / ((canvasMaxFreq - canvasMinFreq) / freqDelta * pixelStep), yZoom);
                c.concat(matrix);
                // fill interval same as canvas pixel width.
                for (int i = beginFreqPt; i < endFreqPt; i++) {
                    float x = i * pixelStep;
                    float y = canvasY4axis(clampDB((float) db_cache[i]));
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
        matrix.setTranslate(0, -yShift*canvasHeight);
        matrix.postScale(1, yZoom);
        c.concat(matrix);
        float o_x = (beginFreqPt * freqDelta - canvasMinFreq) / (canvasMaxFreq - canvasMinFreq) * canvasWidth;
        float o_y = canvasY4axis(clampDB((float)db[beginFreqPt]));
        for (int i = beginFreqPt+1; i < endFreqPt; i++) {
            float x = (i * freqDelta - canvasMinFreq) / (canvasMaxFreq - canvasMinFreq) * canvasWidth;
            float y = canvasY4axis(clampDB((float)db[i]));
            tmpLineXY[4*i  ] = o_x;
            tmpLineXY[4*i+1] = o_y;
            tmpLineXY[4*i+2] = x;
            tmpLineXY[4*i+3] = y;
            o_x = x;
            o_y = y;
        }
        c.drawLines(tmpLineXY, 4*(beginFreqPt+1), 4*(endFreqPt-beginFreqPt-1), linePaintLight);
        c.restore();

        isBusy = false;
    }

    void setCursor(float x, float y) {
        cursorFreq = axisX4canvasView(x);  // frequency
        cursorDB   = axisY4canvasView(y);  // decibel
    }

    float getCursorFreq() {
        return  canvasWidth == 0 ? 0 : cursorFreq;
    }

    public float getCursorDB() {
        return canvasHeight == 0 ?   0 : cursorDB;
    }

    void hideCursor() {
        cursorFreq = 0;
        cursorDB = 0;
    }

    void drawCursor(Canvas c) {
        float cX, cY;
        cX = canvasViewX4axis(cursorFreq);
        cY = canvasViewY4axis(cursorDB);
        if (cursorFreq != 0) {
            c.drawLine(cX, 0, cX, canvasHeight, cursorPaint);
        }
        if (cursorDB != 0) {
            c.drawLine(0, cY, canvasWidth, cY, cursorPaint);
        }
    }

    float getFreqMax() {
        return axisBounds.width() * (xShift + 1 / xZoom);
    }

    float getFreqMin() {
        return axisBounds.width() * xShift;
    }

    // Plot spectrum with axis and ticks on the whole canvas c
    void drawSpectrumPlot(Canvas c, double[] savedDBSpectrum) {
        fqGridLabel.updateGridLabels(getFreqMin(), getFreqMax());
        dbGridLabel.updateGridLabels(getMinY(), getMaxY());
        drawGridLines(c);
        drawSpectrumOnCanvas(c, savedDBSpectrum);
        drawCursor(c);
        drawGridTicks(c);
        drawGridLabels(c);
    }

    void set
}
