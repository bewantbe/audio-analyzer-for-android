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
 * @author bewantbe@gmail.com
 */

package com.google.corp.productivity.specialprojects.android.samples.fft;

import java.text.DecimalFormat;
import java.util.Arrays;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * Custom view to draw the FFT graph
 */

public class AnalyzeView extends View {
  private final String TAG = "AnalyzeView::";
  static float DPRatio;
  private float cursorFreq, cursorDB; // cursor location
  private float xZoom, yZoom;     // horizontal and vertical scaling
  private float xShift, yShift;   // horizontal and vertical translation, in unit 1 unit
  private float minDB = -144f;    // hard lower bound for dB
  private float maxDB = 12f;      // hard upper bound for dB
  private RectF axisBounds;
  private Ready readyCallback = null;      // callback to caller when rendering is complete
  
  private int canvasWidth, canvasHeight;   // size of my canvas
  private Paint linePaint, backgroundPaint;
  private Paint cursorPaint;
  private Paint gridPaint, rulerBrightPaint;
  private Paint labelPaint;
  private Path path;
  private int[] myLocation = {0, 0}; // window location on screen
  private Matrix matrix = new Matrix();
  private Matrix matrix0 = new Matrix();
  private volatile static boolean isBusy = false;
  
  private float gridDensity;
  private double[][] gridPoints2   = new double[2][0];
  private double[][] gridPoints2dB = new double[2][0];
  private double[][] gridPoints2T  = new double[2][0];
  private StringBuilder[] gridPoints2Str   = new StringBuilder[0];
  private StringBuilder[] gridPoints2StrDB = new StringBuilder[0];
  private StringBuilder[] gridPoints2StrT  = new StringBuilder[0];
  private char[][] gridPoints2st   = new char[0][];
  private char[][] gridPoints2stDB = new char[0][];
  private char[][] gridPoints2stT  = new char[0][];
  
  public boolean isBusy() {
    return isBusy;
  }
  
  public AnalyzeView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setup(attrs, context);
  }
  
  public AnalyzeView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setup(attrs, context);
  }
  
  public AnalyzeView(Context context) {
    super(context);
    setup(null, context);
  }
  
  public void setReady(Ready ready) {
    // Log.i(AnalyzeActivity.TAG, "Setting Ready");
    this.readyCallback = ready;
  }
  
  private void setup(AttributeSet attrs, Context context) {
    DPRatio = context.getResources().getDisplayMetrics().density;
    Log.v(TAG, "setup():");
    matrix0.reset();
    matrix0.setTranslate(0f, 0f);
    matrix0.postScale(1f, 1f);

    path = new Path();
    
    linePaint = new Paint();
    linePaint.setColor(Color.RED);
    linePaint.setStyle(Paint.Style.STROKE);
    linePaint.setStrokeWidth(0);
    
    cursorPaint = new Paint(linePaint);
    cursorPaint.setColor(Color.BLUE);
    
    gridPaint = new Paint(linePaint);
    gridPaint.setColor(Color.DKGRAY);
    
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
    minDB = Float.parseFloat(res.getString(R.string.max_DB_range));
  }
  
  public void setBounds(RectF bounds) {
    this.axisBounds = bounds;
  }
  
  public void setBoundsBottom(float b) {
    this.axisBounds.bottom = b;
  }
  
  public void setLowerBound(double b) {
    this.dBLowerBound = b;
  }
  
  public RectF getBounds() {
    return new RectF(axisBounds);
  }
  
  public double getLowerBound() {
    return dBLowerBound;
  }
  
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
    int nGrid = (int)Math.floor(intervalValue / gridIntervalBig) + 1;
    if (nGrid != gridPointsArray[0].length) {
      gridPointsArray[0] = new double[nGrid];
    }
    double[] bigGridPoints = gridPointsArray[0];
    double gridStartValueBig   = Math.ceil(startValue / gridIntervalBig)   * gridIntervalBig;    
    for (int i = 0; i < nGrid; i++) {
      bigGridPoints[i] = gridStartValueBig + i*gridIntervalBig;
    }
    
    nGrid = (int)Math.floor(intervalValue / gridIntervalSmall) + 1;
    if (nGrid != gridPointsArray[1].length) {    // reallocate space when need
      gridPointsArray[1] = new double[nGrid];
    }
    double[] smallGridPoints = gridPointsArray[1];
    double gridStartValueSmall = Math.ceil(startValue / gridIntervalSmall) * gridIntervalSmall;
    for (int i = 0; i < nGrid; i++) {
      smallGridPoints[i] = gridStartValueSmall + i*gridIntervalSmall;
    }
  }
  
  private DecimalFormat smallFormatter = new DecimalFormat("@@");
  private DecimalFormat largeFormatter = new DecimalFormat("#");
  private double[][] oldGridPointBoundaryArray = new double[3][2];
  
  private double[][][] gridPointsArray = {gridPoints2, gridPoints2dB, gridPoints2T};
  private StringBuilder[][] gridPointsStrArray = new StringBuilder[3][0];
  private char[][][] gridPointsStArray = new char[3][0][];
  
  public enum GridScaleType {  // java's enum type is inconvenient
    FREQ(0), DB(1), TIME(2);
    
    private final int value;
    private GridScaleType(int value) { this.value = value; }
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
        gridPoints2stT = gridPointsSt;
        break;
      }
      needUpdate = true;
    }
    if (gridPointsBig.length > 0 && (needUpdate || gridPointsBig[0] != oldGridPointBoundary[0]
        || gridPointsBig[gridPointsBig.length-1] != oldGridPointBoundary[1])) {
      oldGridPointBoundary[0] = gridPointsBig[0];
      oldGridPointBoundary[1] = gridPointsBig[gridPointsBig.length-1];
      for (int i = 0; i < gridPointsStr.length; i++) {
        gridPointsStr[i].setLength(0);
        if (Math.abs(gridPointsBig[i]) >= 10) {
          gridPointsStr[i].append(largeFormatter.format(gridPointsBig[i]));
        } else if (gridPointsBig[i] != 0) {
          gridPointsStr[i].append(smallFormatter.format(gridPointsBig[i]));
        } else {
          gridPointsStr[i].append("0");
        }
        gridPointsStr[i].getChars(0, gridPointsStr[i].length(), gridPointsSt[i], 0);
      }
    }
  }

  // Map a coordinate in frame of axis to frame of canvas c (in pixel unit)
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
  
  float axisY4canvasView(float y) {
    return axisBounds.height() * (yShift + y / canvasHeight / yZoom) + axisBounds.top;
  }
  
  private void drawGridLines(Canvas c, float nx, float ny) {
    updateGridLabels(getFreqMin(), getFreqMax(), nx, GridScaleType.FREQ);
    for(int i = 0; i < gridPoints2[0].length; i++) {
      float xPos = canvasViewX4axis((float)gridPoints2[0][i]);
      c.drawLine(xPos, 0, xPos, canvasHeight, gridPaint);
    }
    for(int i = 0; i < gridPoints2[1].length; i++) {
      float xPos = canvasViewX4axis((float)gridPoints2[1][i]);
      c.drawLine(xPos, 0, xPos, 0.02f * canvasHeight, gridPaint);
    }
    updateGridLabels(getMinY(), getMaxY(), ny, GridScaleType.DB);
    for(int i = 0; i < gridPoints2dB[0].length; i++) {
      float yPos = canvasViewY4axis((float)gridPoints2dB[0][i]);
      c.drawLine(0, yPos, canvasWidth, yPos, gridPaint);
    }
    for(int i = 0; i < gridPoints2dB[1].length; i++) {
      float yPos = canvasViewY4axis((float)gridPoints2dB[1][i]);
      c.drawLine(0, yPos, 0.02f * canvasWidth, yPos, gridPaint);
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
  
  private float getLabelBeginX() {
    float textHeigh     = labelPaint.getFontMetrics(null);
    float labelLaegeLen = 0.5f * textHeigh;
    if (showFreqAlongX) {
      if (bShowTimeAxis) {
        return 0.6f*labelLaegeLen + 1.5f*textHeigh;
      } else {
        return 0;
      }
    } else {
      return 0.6f*labelLaegeLen + 2.5f*textHeigh;
    }
  }
  
  static final String[] axisLabels = {"Hz", "dB", "Sec"};
  
  // Draw axis, start from (labelBeginX, labelBeginY) in the canvas coordinate
  private void drawAxis(Canvas c, float labelBeginX, float labelBeginY, float ng, boolean drawOnXAxis,
                        float axisMin, float axisMax, GridScaleType scale_mode) {
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
    updateGridLabels(axisMin, axisMax, ng, scale_mode);
    String axisLabel = axisLabels[scale_mode_id];
    
    double[][]      gridPoints    = gridPointsArray[scale_mode_id];
    StringBuilder[] gridPointsStr = gridPointsStrArray[scale_mode_id];
    char[][]        gridPointsSt  = gridPointsStArray[scale_mode_id];
    
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
  
  // Draw frequency axis for spectrogram
  // Working in the original canvas frame
  // nx: number of grid lines on average
  private void drawFreqAxis(Canvas c, float labelBeginX, float labelBeginY, float nx, boolean drawOnXAxis) {
    drawAxis(c, labelBeginX, labelBeginY, nx, drawOnXAxis,
             getFreqMin(), getFreqMax(), GridScaleType.FREQ);
  }
  
  // Draw time axis for spectrogram
  // Working in the original canvas frame
  private void drawTimeAxis(Canvas c, float labelBeginX, float labelBeginY, float nt, boolean drawOnXAxis) {
    if (showFreqAlongX ^ (showModeSpectrogram == 0)) {
      drawAxis(c, labelBeginX, labelBeginY, nt, drawOnXAxis,
          (float) timeWatch * timeMultiplier, 0, GridScaleType.TIME);
    } else {
      drawAxis(c, labelBeginX, labelBeginY, nt, drawOnXAxis,
          0, (float) timeWatch * timeMultiplier, GridScaleType.TIME);
    }
  }
  
  // The coordinate frame of this function is identical to its view (id=plot).
  private void drawGridLabels(Canvas c) {
    float textHeigh  = labelPaint.getFontMetrics(null);
    float widthHz    = labelPaint.measureText("Hz");
    float widthDigit = labelPaint.measureText("0");
    float xPos, yPos;
    yPos = textHeigh;
    for(int i = 0; i < gridPoints2Str.length; i++) {
      xPos = canvasViewX4axis((float)gridPoints2[0][i]);
      if (xPos + widthDigit*gridPoints2Str[i].length() + 1.5f*widthHz> canvasWidth) {
        continue;
      }
      c.drawText(gridPoints2st[i], 0, gridPoints2Str[i].length(), xPos, yPos, labelPaint);
    }
    c.drawLine(0, 0, canvasWidth, 0, labelPaint);
    
    c.drawText("Hz", canvasWidth - 1.3f*widthHz, yPos, labelPaint);
    xPos = 0.4f*widthHz;
    for(int i = 0; i < gridPoints2StrDB.length; i++) {
      yPos = canvasViewY4axis((float)gridPoints2dB[0][i]);
      if (yPos + 1.3f*widthHz > canvasHeight) continue;
      c.drawText(gridPoints2stDB[i], 0, gridPoints2StrDB[i].length(), xPos, yPos, labelPaint);
    }
    c.drawLine(0, 0, 0, canvasHeight, labelPaint);
    c.drawText("dB", xPos, canvasHeight - 0.4f*widthHz, labelPaint);
  }
  
  private float clampDB(float value) {
    if (value < minDB || Double.isNaN(value)) {
      value = minDB;
    }
    return value;
  }
  
  /**
   * Re-plot the spectrum
   */
  public void replotRawSpectrum(double[] db, int start, int end, boolean bars) {
    if (canvasHeight < 1) {
      return;
    }
    isBusy = true;
//    long t = SystemClock.uptimeMillis();
    if (showMode == 0) {
      float minYcanvas = canvasY4axis(minDB);
      path.reset();
      if (bars) {
        for (int i = start; i < end; i++) {
          float x = (float) i / (db.length-1) * canvasWidth;
          float y = canvasY4axis(clampDB((float)db[i]));
          if (y != canvasHeight) {
            //path.moveTo(x, canvasHeight);
            path.moveTo(x, minYcanvas);
            path.lineTo(x, y);
          }
        }
      } else {
        // (0,0) is the upper left of the View, in pixel unit
        path.moveTo((float) start / (db.length-1) * canvasWidth, canvasY4axis(clampDB((float)db[start])));
        for (int i = start+1; i < end; i++) {
          float x = (float) i / (db.length-1) * canvasWidth;
          float y = canvasY4axis(clampDB((float)db[i]));
          path.lineTo(x, y);
        }
      }
    } else {
      //use pushRawSpectrogram(db);
    }
//    Log.i(TAG, " replotRawSpectrum: dt = " + (SystemClock.uptimeMillis() - t) + " ms");
    isBusy = false;
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
        cursorFreq = axisX4canvasView(x);  // frequency
        cursorDB   = axisY4canvasView(y);  // decibel
        //Log.i(TAG, "cursorDB = " + cursorDB + "  y = " + y);
      } else {
        cursorDB   = 0;  // disabled
        if (showFreqAlongX) {
          cursorFreq = axisBounds.width() * (xShift + (x-labelBeginX)/(canvasWidth-labelBeginX)/xZoom);  // frequency
        } else {
          cursorFreq = axisBounds.width() * (1 - yShift - y/labelBeginY/yZoom);  // frequency
        }
        if (cursorFreq < 0) {
          cursorFreq = 0;
        }
      }
      return true;
    } else {
      return false;
    }
  }
  
  public float getCursorFreq() {
    return  canvasWidth == 0 ? 0 : cursorFreq;
  }
  
  public float getCursorDB() {
    if (showMode == 0) {
      return canvasHeight == 0 ?   0 : cursorDB;
    } else {
      return 0;
    }
  }
  
  // In the original canvas view frame
  private void drawCursor(Canvas c) {
    float cX, cY;
    if (showMode == 0) {
      cX = canvasViewX4axis(cursorFreq);
      cY = canvasViewY4axis(cursorDB);
      if (cursorFreq != 0) {
        c.drawLine(cX, 0, cX, canvasHeight, cursorPaint); 
      }
      if (cursorDB != 0) {
        c.drawLine(0, cY, canvasWidth, cY, cursorPaint); 
      }
    } else {
      // Show only the frequency cursor
      if (showFreqAlongX) {
        cX = (cursorFreq / axisBounds.width() - xShift) * xZoom * (canvasWidth-labelBeginX) + labelBeginX;
        if (cursorFreq != 0) {
          c.drawLine(cX, 0, cX, labelBeginY, cursorPaint); 
        }
      } else {
        cY = (1 - yShift - cursorFreq / axisBounds.width()) * yZoom * labelBeginY;
        if (cursorFreq != 0) {
          c.drawLine(labelBeginX, cY, canvasWidth, cY, cursorPaint); 
        }
      }
    }
  }

  // In axis frame
  public float getMaxY() {
    return canvasHeight == 0 ? 0 : axisBounds.height() * yShift;
  }
  
  public float getMinY() {
    return canvasHeight == 0 ? 0 : axisBounds.height() * (yShift + 1 / yZoom);
  }
  
  public float getFreqMax() {
    if (showMode == 0 || showFreqAlongX) {
      return axisBounds.width() * (xShift + 1 / xZoom);
    } else {
      return axisBounds.width() * (1 - yShift);
    }
  }
  
  public float getFreqMin() {
    if (showMode == 0 || showFreqAlongX) {
      return axisBounds.width() * xShift;
    } else {
      return axisBounds.width() * (1 - yShift - 1/yZoom);
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
      return canvasWidth - labelBeginX;
    }
  }
  
  public float getCanvasHeight() {
    if (showMode == 0) {
      return canvasHeight;
    } else {
      return labelBeginY;
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
      // limit to minDB ~ maxDB
      return clamp(offset, (maxDB - axisBounds.top) / axisBounds.height(),
                           (minDB - axisBounds.top) / axisBounds.height() - 1 / yZoom);
    } else {
      // strict restrict, y can be frequency or time.
      //  - 0.25f/canvasHeight so we can see "0" for sure
      return clamp(offset, 0f, 1 - (1 - 0.25f/canvasHeight) / yZoom);
    }
  }
  
  public void setScale(float s) {
    xZoom = Math.max(s, 1f); 
    xShift = clamp(xShift, 0f, 1-1/xZoom);
  }
  
  public void setXShift(float offset) {
    xShift = clampXShift(offset);
  }
  
  public void setYShift(float offset) {
    yShift = clampYShift(offset);
  }
  
  public void resetViewScale() {
    xShift = 0;
    xZoom = 1;
    yShift = 0;
    yZoom = 1;
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
    if (canvasWidth*0.13f < xDiffOld) {  // if fingers are not very close in x direction, do scale in x direction
      // limit to 200Hz one screen
      xZoom  = clamp(xZoomOld * Math.abs(x1-x2)/xDiffOld, 1f, axisBounds.width()/200f);
    }
    xShift = clampXShift(xShiftOld + (xMidOld/xZoomOld - (x1+x2)/2f/xZoom) / canvasWidth);
    if (canvasHeight*0.13f < yDiffOld) {  // if fingers are not very close in y direction, do scale in y direction
      // limit to 6dB one screen
      yZoom  = clamp(yZoomOld * Math.abs(y1-y2)/yDiffOld, 1f, -axisBounds.height()/6f);
    }
    yShift = clampYShift(yShiftOld + (yMidOld/yZoomOld - (y1+y2)/2f/yZoom) / canvasHeight);
  }
  
  private void computeMatrix() {
    matrix.reset();
    matrix.setTranslate(-xShift*canvasWidth, -yShift*canvasHeight);
    matrix.postScale(xZoom, yZoom);
  }
  
  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    isBusy = true;
    this.canvasHeight = h;
    this.canvasWidth = w;
    Log.i(TAG, "onSizeChanged(): canvas (" + oldw + "," + oldh + ") -> (" + w + "," + h + ")");
    if (h > 0 && readyCallback != null) {
      readyCallback.ready();
    }
    isBusy = false;
  }
  
  private int[] spectrogramColors;          // int:ARGB, nFreqPoints columns, nTimePoints rows
  private int[] spectrogramColorsShifting;  // temporarily of spectrogramColors for shifting mode
  private int showMode = 0;                 // 0: Spectrum, 1:Spectrogram
  private int showModeSpectrogram = 1;      // 0: moving (shifting) spectrogram, 1: overwriting in loop
  private boolean showFreqAlongX = false;
  private int nFreqPoints;
  private double timeWatch = 4.0;
  private volatile int timeMultiplier = 1;  // should be accorded with nFFTAverage in AnalyzeActivity
  private boolean bShowTimeAxis = true;
  private int nTimePoints;
  private int spectrogramColorsPt;
  private Matrix matrixSpectrogram = new Matrix();
  private static final int[] cma = ColorMapArray.hot;
  private double dBLowerBound = -120;
  private Paint smoothBmpPaint;
  float labelBeginX, labelBeginY;
  
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
      // the frequency range is the same
    } else {
      xShift = 1 - yShift - 1/yZoom;
      xZoom = yZoom;
    }
    yShift = oldYShift;
    yZoom = oldYZoom;
  }
  
  public void switch2Spectrogram(int sampleRate, int fftLen, double timeDurationE) {
    showMode = 1;
    setupSpectrogram(sampleRate, fftLen, timeDurationE);
  }
  
  public void setupSpectrogram(int sampleRate, int fftLen, double timeDurationE) {
    timeWatch = timeDurationE;
    oldXShift = xShift;
    oldXZoom  = xZoom;
    oldYShift = yShift;
    oldYZoom  = yZoom;
    if (showMode == 1 && !showFreqAlongX && canvasWidth>0) {
      // when switch from Spectrum mode to Spectrogram mode
      yZoom = xZoom;
      yShift = 1 - 1/yZoom - xShift;
    }
    double timeInc = fftLen / 2.0 / sampleRate;  // time of each slice. /2.0 due to overlap window
    synchronized (this) {
      nFreqPoints = fftLen / 2;                    // no direct current term
      nTimePoints = (int)Math.ceil(timeWatch / timeInc);
      spectrogramColorsPt = 0;                     // pointer to the row to be filled (row major)
      if (spectrogramColors == null || spectrogramColors.length != nFreqPoints * nTimePoints) {
        spectrogramColors = new int[nFreqPoints * nTimePoints];
        spectrogramColorsShifting = new int[nFreqPoints * nTimePoints];
      }
      Arrays.fill(spectrogramColors, 0);
    }
    Log.i(TAG, "setupSpectrogram() is ready"+
      "\n  sampleRate    = " + sampleRate + 
      "\n  fftLen        = " + fftLen + 
      "\n  timeDurationE = " + timeDurationE);
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
  
  public void pushRawSpectrum(double[] db) {
    isBusy = true;
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
    isBusy = false;
  }
  
//  FramesPerSecondCounter fpsCounter = new FramesPerSecondCounter("View"); 
//  long t_old;

  @Override
  protected void onDraw(Canvas c) {
//    fpsCounter.inc();
//    Log.i(TAG, " onDraw last call dt = " + (t - t_old));
//    t_old = t;
    isBusy = true;
    c.concat(matrix0);
    c.save();
    if (showMode == 0) {
      drawGridLines(c, canvasWidth * gridDensity / DPRatio, canvasHeight * gridDensity / DPRatio);
      computeMatrix();
      c.concat(matrix);
      c.drawPath(path, linePaint);
      c.restore();
      drawCursor(c);
      drawGridLabels(c);
    } else {
      labelBeginX = getLabelBeginX();  // this seems will make the scaling gesture inaccurate
      labelBeginY = getLabelBeginY();
      // show Spectrogram
      float halfFreqResolutionShift = xZoom*(canvasWidth-labelBeginX)/nFreqPoints/2;
      matrixSpectrogram.reset();
      if (showFreqAlongX) {
        // when xZoom== 1: nFreqPoints -> canvasWidth; 0 -> labelBeginX
        matrixSpectrogram.postScale(xZoom*(canvasWidth-labelBeginX)/nFreqPoints, labelBeginY/nTimePoints);
        matrixSpectrogram.postTranslate(labelBeginX - xShift*xZoom*(canvasWidth-labelBeginX) + halfFreqResolutionShift, 0f);
      } else {
        // Will make c.drawBitmap about 20% slower, don't know why
        matrixSpectrogram.postRotate(-90);
        matrixSpectrogram.postScale((canvasWidth-labelBeginX)/nTimePoints, yZoom*labelBeginY/nFreqPoints);
        // (canvasHeight-yShift)/canvasHeight is relative position of shift
        // yZoom*labelBeginY is canvas length in frequency direction in pixel unit
        matrixSpectrogram.postTranslate(labelBeginX, (1-yShift)*yZoom*labelBeginY + halfFreqResolutionShift);
      }
      c.concat(matrixSpectrogram);
      
      // public void drawBitmap (int[] colors, int offset, int stride, float x, float y,
      //                         int width, int height, boolean hasAlpha, Paint paint)
//      long t = SystemClock.uptimeMillis();
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
        c.drawLine(0, spectrogramColorsPt, nFreqPoints, spectrogramColorsPt, cursorPaint);
      }
      c.restore();
      drawCursor(c);
      if (showFreqAlongX) {
        drawFreqAxis(c, labelBeginX, labelBeginY, canvasWidth * gridDensity / DPRatio, showFreqAlongX);
        if (labelBeginX > 0) {
          c.drawRect(0, 0, labelBeginX, labelBeginY, backgroundPaint);
          drawTimeAxis(c, labelBeginX, labelBeginY, canvasHeight * gridDensity / DPRatio, !showFreqAlongX);
        }
      } else {
        drawFreqAxis(c, labelBeginX, labelBeginY, canvasHeight * gridDensity / DPRatio, showFreqAlongX);
        if (labelBeginY != canvasHeight) {
          c.drawRect(0, labelBeginY, canvasWidth, canvasHeight, backgroundPaint);
          drawTimeAxis(c, labelBeginX, labelBeginY, canvasWidth * gridDensity / DPRatio, !showFreqAlongX);
        }
      }
    }
    isBusy = false;
  }
  
  /*
   * Save the labels, cursors, and bounds
   */
  
  @Override
  protected Parcelable onSaveInstanceState() {
    Parcelable parentState = super.onSaveInstanceState();
    State state = new State(parentState);
    state.cx = cursorFreq;
    state.cy = cursorDB;
    state.xZ = xZoom;
    state.yZ = yZoom;
    state.xS = xShift;
    state.yS = yShift;
    state.bounds = axisBounds;
    Log.i("onSaveInstanceState()", "xShift = " + xShift + "  xZoom = " + xZoom + "  yShift = " + yShift + "  yZoom = " + yZoom);
    return state;
  }
  
  @Override
  public void onRestoreInstanceState(Parcelable state) {
    // TODO: save more state here!!
    if (state instanceof State) {
      State s = (State) state;
      super.onRestoreInstanceState(s.getSuperState());
      this.cursorFreq = s.cx;
      this.cursorDB = s.cy;
      this.xZoom = s.xZ;
      this.yZoom = s.yZ;
      this.xShift = s.xS;
      this.yShift = s.yS;
      this.axisBounds = s.bounds;
      Log.i("onRestoreInstanceState()", "xShift = " + xShift + "  xZoom = " + xZoom + "  yShift = " + yShift + "  yZoom = " + yZoom);
    } else {
      super.onRestoreInstanceState(state);
    }
  }
  
  public static interface Ready {
    public void ready();
  }
  
  public static class State extends BaseSavedState {
    float cx, cy; 
    float xZ, yZ;
    float xS, yS;
    RectF bounds;
    
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
      out.writeFloat(xS);
      out.writeFloat(yS);
      bounds.writeToParcel(out, flags);
//      out.write
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
      cx = in.readFloat();
      cy = in.readFloat();
      xZ = in.readFloat();
      yZ = in.readFloat();
      xS = in.readFloat();
      yS = in.readFloat();
      bounds = RectF.CREATOR.createFromParcel(in);
    }
  }
  
}
