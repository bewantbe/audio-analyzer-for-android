/* Copyright 2011 Google Inc.
 *
 *Licensed under the Apache License, Version 2.0 (the "License");
 *you may not use this file except in compliance with the License.
 *You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing, software
 *distributed under the License is distributed on an "AS IS" BASIS,
 *WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *See the License for the specific language governing permissions and
 *limitations under the License.
 *
 * @author Stephen Uhler
 *
 * 2014 Eddy Xiao <bewantbe@gmail.com>
 * GUI extensively modified.
 * Add some naive auto refresh rate control logic.
 */

package github.bewantbe.audio_analyzer_for_android;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.Arrays;

/**
 * Custom view to draw the FFT graph
 */

public class AnalyzerGraphic extends View {
  private final String TAG = "AnalyzerGraphic:";
  private float xZoom, yZoom;     // horizontal and vertical scaling
  private float xShift, yShift;   // horizontal and vertical translation, in unit 1 unit
<<<<<<< HEAD
  private float minDB = -144f;    // hard lower bound for dB
  private RectF axisBounds;
=======
>>>>>>> refs/remotes/bewantbe/master
  private double[] savedDBSpectrum = new double[0];
  static float minDB = -144f;    // hard lower bound for dB
  static float maxDB = 12f;      // hard upper bound for dB

  private int showMode = 0;                      // 0: Spectrum, 1:Spectrogram

  private int canvasWidth, canvasHeight;   // size of my canvas
<<<<<<< HEAD
  private Paint linePaint, linePaintLight, backgroundPaint;
  private Paint cursorPaint;
  private Paint gridPaint, rulerBrightPaint;
  private Paint labelPaint;
=======
>>>>>>> refs/remotes/bewantbe/master
  private int[] myLocation = {0, 0}; // window location on screen
  private Matrix matrix0 = new Matrix();
  private volatile static boolean isBusy = false;
  private float freq_lower_bound_for_log = 0f;

<<<<<<< HEAD
  private float gridDensity;
  private double[][] gridPoints2   = new double[2][0];
  private double[][] gridPoints2dB = new double[2][0];
  private double[][] gridPoints2T  = new double[2][0];
  private StringBuilder[] gridPoints2Str   = new StringBuilder[0];
  private StringBuilder[] gridPoints2StrDB = new StringBuilder[0];
  private StringBuilder[] gridPoints2StrT  = new StringBuilder[0];
  private char[][] gridPoints2st   = new char[0][];
  private char[][] gridPoints2stDB = new char[0][];

  public boolean isBusy() {
    return isBusy;
  }
=======
  SpectrumPlot    spectrumPlot;
  SpectrogramPlot spectrogramPlot;

  Context context;
>>>>>>> refs/remotes/bewantbe/master

  public AnalyzerGraphic(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setup(context);
  }

  public AnalyzerGraphic(Context context, AttributeSet attrs) {
    super(context, attrs);
    setup(context);
  }

  public AnalyzerGraphic(Context context) {
    super(context);
    setup(context);
  }

<<<<<<< HEAD
  public void setReady(Ready ready) {
    this.readyCallback = ready;
  }

  private void setup(Context context) {
    DPRatio = context.getResources().getDisplayMetrics().density;
=======
  private void setup(AttributeSet attrs, Context _context) {
    context = _context;
>>>>>>> refs/remotes/bewantbe/master
    Log.v(TAG, "setup():");
    matrix0.reset();
    matrix0.setTranslate(0f, 0f);
    matrix0.postScale(1f, 1f);

<<<<<<< HEAD
    new Path();

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

    rulerBrightPaint = new Paint(linePaint);
    rulerBrightPaint.setColor(Color.rgb(99, 99, 99));  // 99: between Color.DKGRAY and Color.GRAY

    labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    labelPaint.setColor(Color.GRAY);
    labelPaint.setTextSize(14.0f * DPRatio);
    labelPaint.setTypeface(Typeface.MONOSPACE);  // or Typeface.SANS_SERIF

    backgroundPaint = new Paint();
    backgroundPaint.setColor(Color.BLACK);

    cursorFreq = cursorDB = 0f;
    xZoom=1f;
    xShift=0f;
    yZoom=1f;
    yShift=0f;
    canvasWidth = canvasHeight = 0;
    axisBounds = new RectF(0.0f, 0.0f, 8000.0f, -120.0f);
    gridDensity = 1/85f;  // every 85 pixel one grid line, on average
    Resources res = getResources();
=======
    Resources res = _context.getResources();
>>>>>>> refs/remotes/bewantbe/master
    minDB = Float.parseFloat(res.getString(R.string.max_DB_range));

    xZoom  = 1f;
    xShift = 0f;
    yZoom  = 1f;
    yShift = 0f;
    canvasWidth = canvasHeight = 0;

    // Demo of full initialization
    spectrumPlot    = new SpectrumPlot(context);
    spectrogramPlot = new SpectrogramPlot(context);

    spectrumPlot   .setCanvas(canvasWidth, canvasHeight, null);
    spectrogramPlot.setCanvas(canvasWidth, canvasHeight, null);

    spectrumPlot   .setZooms(xZoom, xShift, yZoom, yShift);
    spectrogramPlot.setZooms(xZoom, xShift, yZoom, yShift);

    spectrumPlot.axisY.vLowerBound = minDB;
  }

<<<<<<< HEAD
  // return position of grid lines, there are roughly gridDensity lines for the bigger grid
  private void genLinearGridPoints(double[][] gridPointsArray, double startValue, double endValue,
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
      if (fraction < Math.sqrt(2)) {
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
      } else if (gridIntervalGuess > Math.sqrt(3)) {
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
=======
  // Call this when settings changed.
  void setupPlot(int sampleRate, int fftLen, double timeDurationE, int nAve) {
    freq_lower_bound_for_log = sampleRate/fftLen;
    float freq_lower_bound_local = 0;
    if (spectrumPlot.axisX.mapTypeInt != ScreenPhysicalMapping.Type.LINEAR.getValue()) {
      freq_lower_bound_local = freq_lower_bound_for_log;
>>>>>>> refs/remotes/bewantbe/master
    }
    // Spectrum
    RectF axisBounds = new RectF(freq_lower_bound_local, 0.0f, sampleRate/2.0f, spectrumPlot.axisY.vHigherBound);
    Log.i(TAG, "setupPlot(): W=" + canvasWidth + "  H=" + canvasHeight + "  dB=" + spectrumPlot.axisY.vHigherBound);
    spectrumPlot.setCanvas(canvasWidth, canvasHeight, axisBounds);

    // Spectrogram
    spectrogramPlot.setupSpectrogram(sampleRate, fftLen, timeDurationE, nAve);
    freq_lower_bound_local = 0;
    if (spectrogramPlot.axisFreq.mapTypeInt != ScreenPhysicalMapping.Type.LINEAR.getValue()) {
      freq_lower_bound_local = freq_lower_bound_for_log;
    }
    if (spectrogramPlot.showFreqAlongX) {
      axisBounds = new RectF(freq_lower_bound_local, 0.0f, sampleRate/2.0f, (float)timeDurationE * nAve);
    } else {
      axisBounds = new RectF(0.0f, sampleRate/2.0f, (float)timeDurationE * nAve, freq_lower_bound_local);
    }
    spectrogramPlot.setCanvas(canvasWidth, canvasHeight, axisBounds);
  }

<<<<<<< HEAD
  private double[][] oldGridPointBoundaryArray = new double[3][2];

  private double[][][] gridPointsArray = {gridPoints2, gridPoints2dB, gridPoints2T};
  private StringBuilder[][] gridPointsStrArray = new StringBuilder[3][0];
  private char[][][] gridPointsStArray = new char[3][0][];

  private enum GridScaleType {  // java's enum type is inconvenient
    FREQ(0), DB(1), TIME(2);

    private final int value;
    GridScaleType(int value) { this.value = value; }
    public int getValue() { return value; }
  }

  // It's so ugly to write these StringBuffer stuff -- in order to reduce garbage
  // Also, since there is no "pass by reference", modify array is also ugly...
  void updateGridLabels(double startValue, double endValue, double gridDensity, GridScaleType scale_mode) {
    int scale_mode_id = scale_mode.getValue();
    double[][] gridPoints = gridPointsArray[scale_mode_id];
    StringBuilder[] gridPointsStr = gridPointsStrArray[scale_mode_id];
    char[][] gridPointsSt = gridPointsStArray[scale_mode_id];
    double[] oldGridPointBoundary = oldGridPointBoundaryArray[scale_mode_id];

    genLinearGridPoints(gridPoints, startValue, endValue, gridDensity, scale_mode_id);
    double[] gridPointsBig = gridPoints[0];
    boolean needUpdate = false;
    if (gridPointsBig.length != gridPointsStr.length) {
      gridPointsStrArray[scale_mode_id] = new StringBuilder[gridPointsBig.length];
      gridPointsStr = gridPointsStrArray[scale_mode_id];
      for (int i = 0; i < gridPointsBig.length; i++) {
        gridPointsStr[i] = new StringBuilder();
      }
      gridPointsStArray[scale_mode_id] = new char[gridPointsBig.length][];  // new array of two char array
      gridPointsSt = gridPointsStArray[scale_mode_id];
      for (int i = 0; i < gridPointsBig.length; i++) {
        gridPointsSt[i] = new char[16];
      }
      switch (scale_mode_id) {
      case 0:
        gridPoints2Str = gridPointsStr;
        gridPoints2st = gridPointsSt;
        break;
      case 1:
        gridPoints2StrDB = gridPointsStr;
        gridPoints2stDB = gridPointsSt;
        break;
      case 2:
        gridPoints2StrT = gridPointsStr;
        break;
      }
      needUpdate = true;
=======
  void setAxisModeLinear(boolean b) {
    ScreenPhysicalMapping.Type mapType;
    if (b) {
      mapType = ScreenPhysicalMapping.Type.LINEAR;
    } else {
      mapType = ScreenPhysicalMapping.Type.LOG;
>>>>>>> refs/remotes/bewantbe/master
    }
    spectrumPlot   .setFreqAxisMode(mapType, freq_lower_bound_for_log);
    spectrogramPlot.setFreqAxisMode(mapType, freq_lower_bound_for_log);
    if (showMode == 0) {
      xZoom  = spectrumPlot.axisX.zoom;
      xShift = spectrumPlot.axisX.shift;
    } else if (showMode == 1) {
      if (spectrogramPlot.showFreqAlongX) {
        xZoom  = spectrogramPlot.axisFreq.zoom;
        xShift = spectrogramPlot.axisFreq.shift;
      } else {
        yZoom  = spectrogramPlot.axisFreq.zoom;
        yShift = spectrogramPlot.axisFreq.shift;
      }
    }
  }

<<<<<<< HEAD
  // Map a coordinate in frame of axis to frame of canvas c (in pixel unit)
  // canvasX4axis is currently not used
  float canvasX4axis(float x) {
    return (x - axisBounds.left) / axisBounds.width() * canvasWidth;
  }

  float canvasY4axis(float y) {
    return (y - axisBounds.top) / axisBounds.height() * canvasHeight;
  }

  // Map a coordinate in frame of axis to frame of view id=plot (in pixel unit)
  float canvasViewX4axis(float x) {
    return ((x - axisBounds.left) / axisBounds.width() - xShift) * xZoom * canvasWidth;
  }

  float canvasViewY4axis(float y) {
    return ((y - axisBounds.top) / axisBounds.height() - yShift) * yZoom * canvasHeight;
  }

  float axisX4canvasView(float x) {
    return axisBounds.width() * (xShift + x / canvasWidth / xZoom) + axisBounds.left;
  }
=======
  public void setShowFreqAlongX(boolean b) {
    spectrogramPlot.setShowFreqAlongX(b);
>>>>>>> refs/remotes/bewantbe/master

    if (showMode == 0) return;

    if (spectrogramPlot.showFreqAlongX) {
      xZoom  = spectrogramPlot.axisFreq.zoom;
      xShift = spectrogramPlot.axisFreq.shift;
      yZoom  = spectrogramPlot.axisTime.zoom;
      yShift = spectrogramPlot.axisTime.shift;
    } else {
      xZoom  = spectrogramPlot.axisTime.zoom;
      xShift = spectrogramPlot.axisTime.shift;
      yZoom  = spectrogramPlot.axisFreq.zoom;
      yShift = spectrogramPlot.axisFreq.shift;
    }
  }

  // Note: Assume setupPlot() was called once.
  public void switch2Spectrum() {
    Log.v(TAG, "switch2Spectrum()");
    if (showMode == 0) {
      return;
    }
    // execute when switch from Spectrogram mode to Spectrum mode
    showMode = 0;
    xZoom  = spectrogramPlot.axisFreq.zoom;
    xShift = spectrogramPlot.axisFreq.shift;
    if (spectrogramPlot.showFreqAlongX == false) {
      xShift = 1 - 1/xZoom - xShift;
    }
    spectrumPlot.axisX.setZoomShift(xZoom, xShift);

    yZoom  = spectrumPlot.axisY.zoom;
    yShift = spectrumPlot.axisY.shift;
  }

<<<<<<< HEAD
  private float getLabelBeginX() {
    float textHeigh     = labelPaint.getFontMetrics(null);
    float labelLaegeLen = 0.5f * textHeigh;
    if (showFreqAlongX) {
      if (bShowTimeAxis) {
        int j = 3;
        for (StringBuilder aGridPoints2StrT : gridPoints2StrT) {
          if (j < aGridPoints2StrT.length()) {
            j = aGridPoints2StrT.length();
          }
        }
        return 0.6f*labelLaegeLen + j*0.5f*textHeigh;
=======
  // Note: Assume setupPlot() was called once.
  public void switch2Spectrogram() {
    if (showMode == 0 && canvasHeight > 0) { // canvasHeight==0 means the program is just start
      if (spectrogramPlot.showFreqAlongX) {
        // no need to change x scaling
        yZoom  = spectrogramPlot.axisTime.zoom;
        yShift = spectrogramPlot.axisTime.shift;
        spectrogramPlot.axisFreq.setZoomShift(xZoom, xShift);
>>>>>>> refs/remotes/bewantbe/master
      } else {
        yZoom = xZoom;
        yShift = 1 - 1/xZoom - xShift;         // axisFreq is reverted
        xZoom  = spectrogramPlot.axisTime.zoom;
        xShift = spectrogramPlot.axisTime.shift;
        spectrogramPlot.axisFreq.setZoomShift(yZoom, yShift);
      }
    }
    showMode = 1;
  }

  private void updateAxisZoomShift() {
    if (showMode == 0) {
      spectrumPlot   .setZooms(xZoom, xShift, yZoom, yShift);
    } else {
      spectrogramPlot.setZooms(xZoom, xShift, yZoom, yShift);
    }
  }

  public void setSmoothRender(boolean b) {
    spectrogramPlot.setSmoothRender(b);
  }

  public int getShowMode() {
    return showMode;
  }

  public void setTimeMultiplier(int nAve) {
    spectrogramPlot.setTimeMultiplier(nAve);
  }

  public void setShowTimeAxis(boolean bSTA) {
    spectrogramPlot.setShowTimeAxis(bSTA);
  }

  public void setSpectrogramModeShifting(boolean b) {
    spectrogramPlot.setSpectrogramModeShifting(b);
  }

  static boolean isBusy() {
    return isBusy;
  }

  static void setIsBusy(boolean b) { isBusy = b; }

  FPSCounter fpsCounter = new FPSCounter("View");
//  long t_old;

  @Override
  protected void onDraw(Canvas c) {
    fpsCounter.inc();
    isBusy = true;
    c.concat(matrix0);
    c.save();
    if (showMode == 0) {
      spectrumPlot.drawSpectrumPlot(c, savedDBSpectrum);
    } else {
      spectrogramPlot.drawSpectrogramPlot(c);
    }
    isBusy = false;
  }

<<<<<<< HEAD
    // spectrum bar
    if (!showLines) {
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
          float y = canvasY4axis(clampDB((float) db[i]));
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
          float y = canvasY4axis(clampDB((float) db[i]));
          if (y != canvasHeight) {
            tmpLineXY[4*i  ] = x;
            tmpLineXY[4*i+1] = minYCanvas;
            tmpLineXY[4*i+2] = x;
            tmpLineXY[4*i+3] = y;
          }
        }
        c.drawLines(tmpLineXY, 4*beginFreqPt, 4*(endFreqPt-beginFreqPt), linePaint);
=======
  // All FFT data will enter this view through this interface
  // Will be called in another thread (SamplingLoop)
  public void saveSpectrum(double[] db) {
    synchronized (savedDBSpectrum) {  // TODO: need lock on savedDBSpectrum, but how?
      if (savedDBSpectrum == null || savedDBSpectrum.length != db.length) {
        savedDBSpectrum = new double[db.length];
>>>>>>> refs/remotes/bewantbe/master
      }
      System.arraycopy(db, 0, savedDBSpectrum, 0, db.length);  // TODO: sync?
    }
    // TODO: Should run on another thread? Or lock on data Or CompletionService?
    if (showMode == 1) {
      spectrogramPlot.saveRowSpectrumAsColor(savedDBSpectrum);
    }
  }

  void setSpectrumDBLowerBound(float b) {
    spectrumPlot.axisY.vHigherBound = b;
  }

  void setSpectrogramDBLowerBound(float b) {
    spectrogramPlot.dBLowerBound = b;
  }

  public void setShowLines(boolean b) {
    spectrumPlot.showLines = b;
  }

  private boolean intersects(float x, float y) {
    getLocationOnScreen(myLocation);
    return x >= myLocation[0] && y >= myLocation[1] &&
       x < myLocation[0] + getWidth() && y < myLocation[1] + getHeight();
  }

  // return true if the coordinate (x,y) is inside graphView
  public boolean setCursor(float x, float y) {
    if (intersects(x, y)) {
      x = x - myLocation[0];
      y = y - myLocation[1];
      // Convert to coordinate in axis
      if (showMode == 0) {
        spectrumPlot.setCursor(x, y);
      } else {
        spectrogramPlot.setCursor(x, y);
      }
      return true;
    } else {
      return false;
    }
  }

  public float getCursorFreq() {
    if (showMode == 0) {
      return spectrumPlot.getCursorFreq();
    } else {
      return spectrogramPlot.getCursorFreq();
    }
  }

  public float getCursorDB() {
    if (showMode == 0) {
      return spectrumPlot.getCursorDB();
    } else {
      return 0;
    }
  }

  public void hideCursor() {
    spectrumPlot.hideCursor();
    spectrogramPlot.hideCursor();
  }

  public float getFreqMax() {
    if (showMode == 0) {
      return spectrumPlot.getFreqMax();
    } else {
      return spectrogramPlot.getFreqMax();
    }
  }

  public float getFreqMin() {
    if (showMode == 0) {
      return spectrumPlot.getFreqMin();
    } else {
      return spectrogramPlot.getFreqMin();
    }
  }

  public float getXZoom() {
    return xZoom;
  }

  public float getYZoom() {
    return yZoom;
  }

  public float getXShift() {
    return xShift;
  }

  public float getYShift() {
    return yShift;
  }

  public float getCanvasWidth() {
    if (showMode == 0) {
      return canvasWidth;
    } else {
      return canvasWidth - spectrogramPlot.labelBeginX;
    }
  }

  public float getCanvasHeight() {
    if (showMode == 0) {
      return canvasHeight;
    } else {
      return spectrogramPlot.labelBeginY;
    }
  }

  private float clamp(float x, float min, float max) {
    if (x > max) {
      return max;
    } else if (x < min) {
      return min;
    } else {
      return x;
    }
  }

  private float clampXShift(float offset) {
    return clamp(offset, 0f, 1 - 1 / xZoom);
  }

  private float clampYShift(float offset) {
    if (showMode == 0) {
<<<<<<< HEAD
      // limit to minDB ~ maxDB
      float maxDB = 12f;
      return clamp(offset, (maxDB - axisBounds.top) / axisBounds.height(),
                           (minDB - axisBounds.top) / axisBounds.height() - 1 / yZoom);
=======
      // limit view to minDB ~ maxDB, assume linear in dB scale
      return clamp(offset, (maxDB - spectrumPlot.axisY.vLowerBound) / spectrumPlot.axisY.diffVBounds(),
                           (minDB - spectrumPlot.axisY.vLowerBound) / spectrumPlot.axisY.diffVBounds() - 1 / yZoom);
>>>>>>> refs/remotes/bewantbe/master
    } else {
      // strict restrict, y can be frequency or time.
      //  - 0.25f/canvasHeight so we can see "0" for sure
      return clamp(offset, 0f, 1 - (1 - 0.25f/canvasHeight) / yZoom);
    }
  }

  public void setXShift(float offset) {
    xShift = clampXShift(offset);
    updateAxisZoomShift();
  }

  public void setYShift(float offset) {
    yShift = clampYShift(offset);
    updateAxisZoomShift();
  }

  public void resetViewScale() {
    xShift = 0;
    xZoom = 1;
    yShift = 0;
    yZoom = 1;
    updateAxisZoomShift();
  }

  private float xMidOld = 100;
  private float xDiffOld = 100;
  private float xZoomOld = 1;
  private float xShiftOld = 0;
  private float yMidOld = 100;
  private float yDiffOld = 100;
  private float yZoomOld = 1;
  private float yShiftOld = 0;

  // record the coordinate frame state when starting scaling
  public void setShiftScaleBegin(float x1, float y1, float x2, float y2) {
    xMidOld = (x1+x2)/2f;
    xDiffOld = Math.abs(x1-x2);
    xZoomOld  = xZoom;
    xShiftOld = xShift;
    yMidOld = (y1+y2)/2f;
    yDiffOld = Math.abs(y1-y2);
    yZoomOld  = yZoom;
    yShiftOld = yShift;
  }

  // Do the scaling according to the motion event getX() and getY() (getPointerCount()==2)
  public void setShiftScale(float x1, float y1, float x2, float y2) {
    float limitXZoom;
    float limitYZoom;
    if (showMode == 0) {
      limitXZoom =  spectrumPlot.axisX.diffVBounds()/200f;  // limit to 200 Hz a screen
      limitYZoom = -spectrumPlot.axisY.diffVBounds()/6f;    // limit to 6 dB a screen
    } else {
      int nTimePoints = spectrogramPlot.nTimePoints;
      if (spectrogramPlot.showFreqAlongX) {
        limitXZoom = spectrogramPlot.axisFreq.diffVBounds()/200f;
        limitYZoom = nTimePoints>10 ? nTimePoints / 10 : 1;
      } else {
        limitXZoom = nTimePoints>10 ? nTimePoints / 10 : 1;
        limitYZoom = spectrogramPlot.axisFreq.diffVBounds()/200f;
      }
    }
    limitXZoom = Math.abs(limitXZoom);
    limitYZoom = Math.abs(limitYZoom);
//    Log.i(TAG, "setShiftScale: limit: xZ="+limitXZoom+"  yZ="+limitYZoom);
    if (canvasWidth*0.13f < xDiffOld) {  // if fingers are not very close in x direction, do scale in x direction
      xZoom  = clamp(xZoomOld * Math.abs(x1-x2)/xDiffOld, 1f, limitXZoom);
    }
    xShift = clampXShift(xShiftOld + (xMidOld/xZoomOld - (x1+x2)/2f/xZoom) / canvasWidth);
    if (canvasHeight*0.13f < yDiffOld) {  // if fingers are not very close in y direction, do scale in y direction
      yZoom  = clamp(yZoomOld * Math.abs(y1-y2)/yDiffOld, 1f, limitYZoom);
    }
    yShift = clampYShift(yShiftOld + (yMidOld/yZoomOld - (y1+y2)/2f/yZoom) / canvasHeight);
//    Log.i(TAG, "setShiftScale: xZ="+xZoom+"  xS="+xShift+"  yZ="+yZoom+"  yS="+yShift);
    updateAxisZoomShift();
  }

<<<<<<< HEAD
=======
  private Ready readyCallback = null;      // callback to caller when rendering is complete

  public void setReady(Ready ready) {
    this.readyCallback = ready;
  }

>>>>>>> refs/remotes/bewantbe/master
  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    isBusy = true;
    this.canvasWidth = w;
    this.canvasHeight = h;
    spectrumPlot   .setCanvas(w, h, null);
    spectrogramPlot.setCanvas(w, h, null);
    Log.i(TAG, "onSizeChanged(): canvas (" + oldw + "," + oldh + ") -> (" + w + "," + h + ")");
    if (h > 0 && readyCallback != null) {
      readyCallback.ready();
    }
    isBusy = false;
  }

<<<<<<< HEAD
  private int[] spectrogramColors = new int[0];  // int:ARGB, nFreqPoints columns, nTimePoints rows
  private int[] spectrogramColorsShifting;       // temporarily of spectrogramColors for shifting mode
  private int showMode = 0;                      // 0: Spectrum, 1:Spectrogram
  private int showModeSpectrogram = 1;           // 0: moving (shifting) spectrogram, 1: overwriting in loop
  private boolean showFreqAlongX = false;
  private int nFreqPoints;
  private double timeWatch = 4.0;
  private volatile int timeMultiplier = 1;  // should be accorded with nFFTAverage in AnalyzerActivity
  private boolean bShowTimeAxis = true;
  private int nTimePoints;
  private int spectrogramColorsPt;          // pointer to the row to be filled (row major)
  private Matrix matrixSpectrogram = new Matrix();
  private static final int[] cma = ColorMapArray.hot;
  private double dBLowerBound = -120;
  private Paint smoothBmpPaint;
  private float labelBeginX, labelBeginY;

  public int getShowMode() {
    return showMode;
  }

  public void setTimeMultiplier(int nAve) {
    timeMultiplier = nAve;
  }

  public void setShowTimeAxis(boolean bSTA) {
    bShowTimeAxis = bSTA;
  }

  public void setSpectrogramModeShifting(boolean b) {
    if (b) {
      showModeSpectrogram = 0;
    } else {
      showModeSpectrogram = 1;
    }
  }

  public void setShowFreqAlongX(boolean b) {
    if (showMode == 1 && showFreqAlongX != b) {
      // match zooming
      float t;
      if (showFreqAlongX) {
        t = xShift;
        xShift = yShift;
        yShift = 1 - t - 1/xZoom;
      } else {
        t = yShift;
        yShift = xShift;
        xShift = 1 - t - 1/yZoom;
      }
      t = xZoom;
      xZoom = yZoom;
      yZoom = t;
    }
    showFreqAlongX = b;
  }

  public void setSmoothRender(boolean b) {
    if (b) {
      smoothBmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    } else {
      smoothBmpPaint = null;
    }
  }

  float oldYShift = 0;
  float oldXShift = 0;
  float oldYZoom = 1;
  float oldXZoom = 1;

  public void switch2Spectrum() {
    Log.v(TAG, "switch2Spectrum()");
    if (showMode == 0) {
      return;
    }
    // execute when switch from Spectrogram mode to Spectrum mode
    showMode = 0;
    if (showFreqAlongX) {
      //< the frequency range is the same
    } else {
      // get frequency range
      xShift = 1 - yShift - 1/yZoom;
      xZoom = yZoom;
    }
    yShift = oldYShift;
    yZoom = oldYZoom;
  }

  public void switch2Spectrogram(int sampleRate, int fftLen, double timeDurationE) {
    if (showMode == 0 && canvasHeight > 0) { // canvasHeight==0 means the program is just start
      oldXShift = xShift;
      oldXZoom  = xZoom;
      oldYShift = yShift;
      oldYZoom  = yZoom;
      if (showFreqAlongX) {
        //< no need to change x scaling
        yZoom = 1;
        yShift = 0;
      } else {
        yZoom = xZoom;
        yShift = 1 - 1/yZoom - xShift;
        xZoom = 1;
        xShift = 0;
      }
    }
    setupSpectrogram(sampleRate, fftLen, timeDurationE);
    showMode = 1;
  }

  public void setupSpectrogram(int sampleRate, int fftLen, double timeDurationE) {
    timeWatch = timeDurationE;
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
    Log.i(TAG, "setupSpectrogram() is ready"+
      "\n  sampleRate    = " + sampleRate +
      "\n  fftLen        = " + fftLen +
      "\n  timeDurationE = " + timeDurationE);
  }

  // colorFromDB is not used
  public int colorFromDB(double d) {
    if (d >= 0) {
      return cma[0];
    }
    if (d <= dBLowerBound || Double.isInfinite(d) || Double.isNaN(d)) {
      return cma[cma.length-1];
    }
    return cma[(int)(cma.length * d / dBLowerBound)];
  }

  public void saveRowSpectrumAsColor(double[] db) {
    saveSpectrum(db);
    synchronized (this) {
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

  // Plot spectrum with axis and ticks on the whole canvas c
  public void drawSpectrumPlot(Canvas c) {
    updateGridLabels(getFreqMin(), getFreqMax(),
            canvasWidth * gridDensity / DPRatio, GridScaleType.FREQ);
    updateGridLabels(getMinY(), getMaxY(),
            canvasHeight * gridDensity / DPRatio, GridScaleType.DB);
    drawGridLines(c);
    drawSpectrumOnCanvas(c, savedDBSpectrum);
    drawCursor(c);
    drawGridTicks(c);
    drawGridLabels(c);
  }

  // Plot spectrogram with axis and ticks on the whole canvas c
  public void drawSpectrogramPlot(Canvas c) {
    labelBeginX = getLabelBeginX();  // this seems will make the scaling gesture inaccurate
    labelBeginY = getLabelBeginY();
    // show Spectrogram
    float halfFreqResolutionShift;  // move the color patch to match the center frequency
    matrixSpectrogram.reset();
    if (showFreqAlongX) {
      // when xZoom== 1: nFreqPoints -> canvasWidth; 0 -> labelBeginX
      matrixSpectrogram.postScale(xZoom*(canvasWidth-labelBeginX)/nFreqPoints,
              yZoom*labelBeginY/nTimePoints);
      halfFreqResolutionShift = xZoom*(canvasWidth-labelBeginX)/nFreqPoints/2;
      matrixSpectrogram.postTranslate(labelBeginX - xShift*xZoom*(canvasWidth-labelBeginX) + halfFreqResolutionShift,
              -yShift*yZoom*labelBeginY);
    } else {
      // postRotate() will make c.drawBitmap about 20% slower, don't know why
      matrixSpectrogram.postRotate(-90);
      matrixSpectrogram.postScale(xZoom*(canvasWidth-labelBeginX)/nTimePoints,
              yZoom*labelBeginY/nFreqPoints);
      // (1-yShift) is relative position of shift (after rotation)
      // yZoom*labelBeginY is canvas length in frequency direction in pixel unit
      halfFreqResolutionShift = yZoom*labelBeginY/nFreqPoints/2;
      matrixSpectrogram.postTranslate(labelBeginX - xShift*xZoom*(canvasWidth-labelBeginX),
              (1-yShift)*yZoom*labelBeginY - halfFreqResolutionShift);
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

  FPSCounter fpsCounter = new FPSCounter("View");
//  long t_old;

  @Override
  protected void onDraw(Canvas c) {
    fpsCounter.inc();
//    Log.i(TAG, " onDraw last call dt = " + (t - t_old));
//    t_old = t;
    isBusy = true;
    c.concat(matrix0);
    c.save();
    if (showMode == 0) {
      drawSpectrumPlot(c);
    } else {
      drawSpectrogramPlot(c);
    }
    isBusy = false;
  }

=======
>>>>>>> refs/remotes/bewantbe/master
  /*
   * Save the labels, cursors, and bounds
   * TODO: need to check what need to save
   */

  @Override
  protected Parcelable onSaveInstanceState() {
    Parcelable parentState = super.onSaveInstanceState();
    State state = new State(parentState);
    state.cx = spectrumPlot.cursorFreq;
    state.cy = spectrumPlot.cursorDB;
    state.xZ = xZoom;
    state.yZ = yZoom;
    state.OyZ = spectrumPlot.axisY.zoom;
    state.xS = xShift;
    state.yS = yShift;
    state.OyS = spectrumPlot.axisY.shift;
    state.bounds = new RectF(spectrumPlot.axisX.vLowerBound,  spectrumPlot.axisY.vLowerBound,
                             spectrumPlot.axisX.vHigherBound, spectrumPlot.axisY.vHigherBound);

    state.nfq = savedDBSpectrum.length;
    state.tmpS = savedDBSpectrum;

    state.nsc = spectrogramPlot.spectrogramColors.length;
    state.nFP = spectrogramPlot.nFreqPoints;
    state.nSCP = spectrogramPlot.spectrogramColorsPt;
    state.tmpSC = spectrogramPlot.spectrogramColors;
    Log.i(TAG, "onSaveInstanceState(): xShift = " + xShift + "  xZoom = " + xZoom + "  yShift = " + yShift + "  yZoom = " + yZoom);
    return state;
  }

  // maybe we could save the whole view in main activity

  @Override
  public void onRestoreInstanceState(Parcelable state) {
    if (state instanceof State) {
      State s = (State) state;
      super.onRestoreInstanceState(s.getSuperState());
      this.spectrumPlot.cursorFreq = s.cx;
      this.spectrumPlot.cursorDB = s.cy;
      this.xZoom = s.xZ;
      this.yZoom = s.yZ;
      this.spectrumPlot.axisY.zoom = s.OyZ;
      this.xShift = s.xS;
      this.yShift = s.yS;
      this.spectrumPlot.axisY.shift = s.OyS;
      RectF sb = s.bounds;
      spectrumPlot.axisX.vLowerBound = sb.left;
      spectrumPlot.axisY.vLowerBound = sb.top;
      spectrumPlot.axisX.vHigherBound = sb.right;
      spectrumPlot.axisY.vHigherBound = sb.bottom;

      this.savedDBSpectrum = s.tmpS;

      this.spectrogramPlot.nFreqPoints = s.nFP;
      this.spectrogramPlot.spectrogramColorsPt = s.nSCP;
      this.spectrogramPlot.spectrogramColors = s.tmpSC;
      this.spectrogramPlot.spectrogramColorsShifting = new int[this.spectrogramPlot.spectrogramColors.length];

      // Will constructor of this class been called?
      // spectrumPlot == null || spectrumPlot.axisX == null is always false

      Log.i(TAG, "onRestoreInstanceState(): xShift = " + xShift + "  xZoom = " + xZoom + "  yShift = " + yShift + "  yZoom = " + yZoom);
    } else {
      super.onRestoreInstanceState(state);
    }
  }

  interface Ready {
    void ready();
  }

  private static class State extends BaseSavedState {
    float cx, cy;
    float xZ, yZ, OyZ;
    float xS, yS, OyS;
    RectF bounds;
    int nfq;
    double[] tmpS;
    int nsc;  // size of tmpSC
    int nFP;
    int nSCP;
    int[] tmpSC;

    State(Parcelable state) {
      super(state);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
      super.writeToParcel(out, flags);
      out.writeFloat(cx);
      out.writeFloat(cy);
      out.writeFloat(xZ);
      out.writeFloat(yZ);
      out.writeFloat(OyZ);
      out.writeFloat(xS);
      out.writeFloat(yS);
      out.writeFloat(OyS);
      bounds.writeToParcel(out, flags);

      out.writeInt(nfq);
      out.writeDoubleArray(tmpS);

      out.writeInt(nsc);
      out.writeInt(nFP);
      out.writeInt(nSCP);
      out.writeIntArray(tmpSC);  // TODO: consider use compress
      // https://developer.android.com/reference/java/util/zip/Deflater.html
    }

    public static final Parcelable.Creator<State> CREATOR = new Parcelable.Creator<State>() {
      @Override
      public State createFromParcel(Parcel in) {
        return new State(in);
      }

      @Override
      public State[] newArray(int size) {
        return new State[size];
      }
    };

    private State(Parcel in) {
      super(in);
      cx  = in.readFloat();
      cy  = in.readFloat();
      xZ  = in.readFloat();
      yZ  = in.readFloat();
      OyZ = in.readFloat();
      xS  = in.readFloat();
      yS  = in.readFloat();
      OyS = in.readFloat();
      bounds = RectF.CREATOR.createFromParcel(in);

      nfq = in.readInt();
      tmpS = new double[nfq];
      in.readDoubleArray(tmpS);

      nsc = in.readInt();
      nFP = in.readInt();
      nSCP = in.readInt();
      tmpSC = new int[nsc];
      in.readIntArray(tmpSC);
    }
  }
}
