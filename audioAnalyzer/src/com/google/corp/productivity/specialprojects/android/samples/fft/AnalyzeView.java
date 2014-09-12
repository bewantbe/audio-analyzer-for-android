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
import android.content.SharedPreferences;
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
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * Custom view to draw the FFT graph
 */

public class AnalyzeView extends View {
  private final String TAG = "AnalyzeView::";
  private float cursorX, cursorY; // cursor location
  private float xZoom;     // horizontal scaling
  private float xShift;    // horizontal translation
  private float yZoom;     // vertical scaling
  private float yShift;    // vertical translation
  private float minDB = -144f;  // hard lower bound limit
  private RectF axisBounds;
  private Ready readyCallback = null;      // callback to caller when rendering is complete
  
  private int canvasWidth, canvasHeight;   // size of my canvas
  private Paint linePaint, backgroundPaint;
  private Paint cursorPaint;
  private Paint gridPaint;
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
  SharedPreferences sharedPref;  // read preference automatically
  
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
    Log.v(TAG, "setup():");
    path = new Path();
    
    linePaint = new Paint();
    linePaint.setColor(Color.RED);
    linePaint.setStyle(Paint.Style.STROKE);
    linePaint.setStrokeWidth(0);
    
    cursorPaint = new Paint(linePaint);
    cursorPaint.setColor(Color.BLUE);
    
    gridPaint = new Paint(linePaint);
    gridPaint.setColor(Color.DKGRAY);

    labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    labelPaint.setColor(Color.GRAY);
    labelPaint.setTextSize(14.0f);
    labelPaint.setTypeface(Typeface.MONOSPACE);  // or Typeface.SANS_SERIF
    
    backgroundPaint = new Paint();
    backgroundPaint.setColor(Color.BLACK);

    cursorX = cursorY = 0f;
    xZoom=1f;
    xShift=0f;
    yZoom=1f;
    yShift=0f;
    canvasWidth = canvasHeight = 0;
    axisBounds = new RectF(0.0f, 0.0f, 8000.0f, -120.0f);
    gridDensity = 1/85f;  // every 85 pixel one grid line, on average
    Resources res = getResources();
    minDB = Float.parseFloat(res.getString(R.string.max_DB_range));
    sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
    dBLowerBound = Double.parseDouble(sharedPref.getString("spectrogramRange",
                   Double.toString(dBLowerBound)));
    axisBounds.bottom = Float.parseFloat(sharedPref.getString("spectrumRange",
                        Double.toString(axisBounds.bottom)));
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
      Log.e(TAG, "genLinearGridPoints(): startValue == endValue !");
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
    if (scale_mode == 0 || scale_mode == 2 || intervalValue <= 1) {  // Linear scale (Hz)
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
//    Log.i(AnalyzeActivity.TAG, "  gridIntervalGuess = " + Double.toString(gridIntervalGuess) + "  gridIntervalBig = " + Double.toString(gridIntervalBig));

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
  double[][] oldGridPointBoundaryArray = new double[3][2];
  
  double[][][] gridPointsArray = {gridPoints2, gridPoints2dB, gridPoints2T};
  StringBuilder[][] gridPointsStrArray = new StringBuilder[3][0];
  char[][][] gridPointsStArray = new char[3][0][];
  
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
//      Log.i(AnalyzeActivity.TAG, "  Update grid label scale_mode_id=" + Integer.toString(scale_mode_id));
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
    return (canvasX4axis(x) - xShift) * xZoom;
  }
  
  float canvasViewY4axis(float y) {
    return (canvasY4axis(y) - yShift) * yZoom;
  }
  
  private void drawGridLines(Canvas c, float nx, float ny) {
//    Log.i(AnalyzeActivity.TAG, " axisBounds.height(): " + Double.toString(axisBounds.height()));
    updateGridLabels(getMinX(), getMaxX(), nx, GridScaleType.FREQ);
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
    float labelLaegeLen = 0.03f * canvasHeight;
    float textHeigh     = labelPaint.getFontMetrics(null);
    return canvasHeight - 0.6f*labelLaegeLen - textHeigh;
  }
  
  private float getLabelBeginX() {
    float labelLaegeLen = 0.03f * canvasWidth;
    float textHeigh     = labelPaint.getFontMetrics(null);
    return 0.6f*labelLaegeLen + 1.5f*textHeigh;
  }

  // Draw frequency axis for spectrogram
  // Working in the original canvas frame
  private void drawFreqAxis(Canvas c, float labelBeginX, float labelBeginY, float nx) {
    float axisMin = getMinX();
    float axisMax = getMaxX();
    float canvasMin = labelBeginX;
    float canvasMax = canvasWidth;
    updateGridLabels(axisMin, axisMax, nx, GridScaleType.FREQ);
    
    // plot axis mark
    float xPos, yPos;
    float labelLargeLen = 0.03f * canvasHeight;
    float labelSmallLen = 0.02f * canvasHeight;
    float textHeigh     = labelPaint.getFontMetrics(null);
    for(int i = 0; i < gridPoints2[0].length; i++) {
      xPos = ((float)gridPoints2[0][i] - axisMin) / (axisMax-axisMin) * (canvasMax - canvasMin) + canvasMin;
      c.drawLine(xPos, labelBeginY, xPos, labelBeginY+labelLargeLen, gridPaint);
    }
    for(int i = 0; i < gridPoints2[1].length; i++) {
      xPos =((float)gridPoints2[1][i] - axisMin) / (axisMax-axisMin) * (canvasMax - canvasMin) + canvasMin;
      c.drawLine(xPos, labelBeginY, xPos, labelBeginY+labelSmallLen, gridPaint);
    }
    c.drawLine(canvasMin, labelBeginY, canvasMax, labelBeginY, labelPaint);

    // plot labels
    float widthHz    = labelPaint.measureText("Hz");
    float widthDigit = labelPaint.measureText("0");
    yPos = labelBeginY + 0.5f*labelLargeLen + textHeigh;
    for(int i = 0; i < gridPoints2Str.length; i++) {
      xPos = ((float)gridPoints2[0][i] - axisMin) / (axisMax-axisMin) * (canvasMax - canvasMin) + canvasMin;
      if (xPos + widthDigit * gridPoints2Str[i].length() + 1.5f*widthHz > canvasWidth) {
        continue;
      }
      c.drawText(gridPoints2st[i], 0, gridPoints2Str[i].length(), xPos, yPos, labelPaint);
    }
    c.drawText("Hz", canvasWidth - 1.3f*widthHz, yPos, labelPaint);
  }
  
  // Draw time axis for spectrogram
  // Working in the original canvas frame
  private void drawTimeAxis(Canvas c, float labelBeginX, float labelBeginY, float nt) {
    float axisMin = (float) timeWatch * timeMultiplier;
    float axisMax = 0;
    float canvasMin = 0;
    float canvasMax = labelBeginY;
    updateGridLabels(axisMin, axisMax, nt, GridScaleType.TIME);
    
    // plot axis mark
    float yPos;
    float labelLargeLen = 0.03f * canvasWidth;
    float labelSmallLen = 0.02f * canvasWidth;
    float textHeigh     = labelPaint.getFontMetrics(null);
    for(int i = 0; i < gridPoints2T[0].length; i++) {
      yPos = ((float)gridPoints2T[0][i] - axisMin) / (axisMax-axisMin) * (canvasMax - canvasMin) + canvasMin;
      c.drawLine(labelBeginX-labelLargeLen, yPos, labelBeginX, yPos, gridPaint);
    }
    for(int i = 0; i < gridPoints2T[1].length; i++) {
      yPos =((float)gridPoints2T[1][i] - axisMin) / (axisMax-axisMin) * (canvasMax - canvasMin) + canvasMin;
      c.drawLine(labelBeginX-labelSmallLen, yPos, labelBeginX, yPos, gridPaint);
    }
    c.drawLine(labelBeginX, canvasMin, labelBeginX, canvasMax, labelPaint);

    // plot labels
    float widthDigit = labelPaint.measureText("0");
    yPos = labelBeginY + 0.5f*labelLargeLen + textHeigh;
    for(int i = 0; i < gridPoints2StrT.length; i++) {
      yPos = ((float)gridPoints2T[0][i] - axisMin) / (axisMax-axisMin) * (canvasMax - canvasMin) + canvasMin;
      if (yPos > canvasMax - 1.3f*textHeigh) {
        continue;
      }
      c.drawText(gridPoints2stT[i], 0, gridPoints2StrT[i].length(),
                 labelBeginX - widthDigit * gridPoints2StrT[i].length() - 0.5f * labelLargeLen, yPos, labelPaint);
    }
    c.drawText("Sec", labelBeginX - widthDigit * 3 - 0.5f * labelLargeLen, canvasMax, labelPaint);
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
    if (value < getMinY() || Float.isNaN(value)) {
      value = getMinY();
    } else if (value > getMaxY()) {
      value = getMaxY();
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
    isBusy = false;
  }
  
  private boolean intersects(float x, float y) {
    getLocationOnScreen(myLocation);
    return x >= myLocation[0] && y >= myLocation[1] &&
       x < myLocation[0] + getWidth() && y < myLocation[1] + getHeight();
  }
  
  public boolean setCursor(float x, float y) {
    if (intersects(x, y)) {
      // Log.i(AnalyzeActivity.TAG, x + "," + y);
      float current = getXShift();
      if (x <= 3 && xShift > 0f) {
        setXShift(current - 10f) ;
      } else if (x >=  canvasWidth - 3) {
        setXShift(current + 10f);
      } else {
        cursorX = xShift + (x - myLocation[0])/xZoom;  // coordinate in canvas
        cursorY = yShift + (y - myLocation[1])/yZoom;
      }
      return true;
    } else {
      return false;
    }
  }
  
  public float getCursorX() {
    return  canvasWidth == 0 ? 0 : axisBounds.width() * cursorX / canvasWidth;
  }
  
  public float getCursorY() {
    return  canvasHeight == 0 ? 0 : axisBounds.height() * cursorY / canvasHeight;
  }
  
  // in the axisBounds frame
  private void drawCursor(Canvas c) {
    if (xShift < cursorX && cursorX <= xShift + canvasWidth/xZoom) {
      c.drawLine(cursorX, yShift, cursorX, yShift + canvasHeight/yZoom, cursorPaint); 
    }
    if (yShift < cursorY && cursorY <= yShift + canvasHeight/yZoom) {
      c.drawLine(xShift, cursorY, xShift + canvasWidth/xZoom, cursorY, cursorPaint); 
    }
  }
  
  // In axis frame
  public float getMinX() {
    return canvasWidth == 0 ? 0 : axisBounds.width() * xShift / canvasWidth;
  }
  
  public float getMaxX() {
    return canvasWidth == 0 ? 0 : axisBounds.width() * (xShift * xZoom + canvasWidth) / (canvasWidth * xZoom);
  }
  
  public float getMaxY() {
    return canvasHeight == 0 ? 0 : axisBounds.height() * yShift / canvasHeight;
  }
  
  public float getMinY() {
    return canvasHeight == 0 ? 0 : axisBounds.height() * (yShift * yZoom + canvasHeight) / (canvasHeight * yZoom);
  }
  
  public float getMinCanvasY() {
    return canvasHeight == 0 ? 0 : yShift + canvasHeight/yZoom;
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
    return clamp(offset, 0f, canvasWidth - canvasWidth / xZoom);
  }

  private float clampYShift(float offset) {
    // limit to -180dB ~ 12 dB
    return clamp(offset, canvasY4axis(12f), canvasY4axis(minDB) - canvasHeight / yZoom);
    //return clamp(offset, 0f, canvasHeight - canvasHeight / yZoom);
  }
  
  public void setScale(float s) {
    xZoom = Math.max(s, 1f); 
    xShift = clamp(xShift, 0f, (xZoom - 1f) * canvasWidth );
    computeMatrix();
    invalidate();
  }
  
  public void setXShift(float offset) {
    xShift = clampXShift(offset);
    computeMatrix();
    invalidate();
  }
  
  public void setYShift(float offset) {
    yShift = clampYShift(offset);
    computeMatrix();
    invalidate();
  }
  
  public void resetViewScale() {
    xShift = 0;
    xZoom = 1;
    yShift = 0;
    yZoom = 1;
    computeMatrix();
    invalidate();
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
    if (canvasWidth*0.13f < xDiffOld) {
      xZoom  = clamp(xZoomOld * Math.abs(x1-x2)/xDiffOld, 1f, axisBounds.width()/200f);    // 100 sample frequency full screen
    }
    xShift = clampXShift(xShiftOld + xMidOld/xZoomOld - (x1+x2)/2f/xZoom);
    if (canvasHeight*0.13f < yDiffOld) {
      yZoom  = clamp(yZoomOld * Math.abs(y1-y2)/yDiffOld, 1f, -axisBounds.height()/6f);  // ~ 3dB full screen
    }
    yShift = clampYShift(yShiftOld + yMidOld/yZoomOld - (y1+y2)/2f/yZoom);
    computeMatrix();
    invalidate();
  }
  
  private void computeMatrix() {
    matrix.reset();
    matrix.setTranslate(-xShift, -yShift);
    matrix.postScale(xZoom, yZoom);
    matrix0.reset();
    matrix0.setTranslate(0f, 0f);
    matrix0.postScale(1f, 1f);
    // Log.i(AnalyzeActivity.TAG, "  computeMatrix(): xShift=" + xShift + " xZoom=" + xZoom);
  }
  
  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    isBusy = true;
    this.canvasHeight = h;
    this.canvasWidth = w;
    Log.i(TAG, "onSizeChanged(): canvas (" + oldw + "," + oldh + ") -> (" + w + "," + h + ")");
    if (oldh == 0 && h > 0 && readyCallback != null) {
      readyCallback.ready();
    }
    isBusy = false;
  }
  
  int[] spectrogramColors;  // int:ARGB, nFreqPoints columns, nTimePoints rows
  int showMode = 0;         // 0: Spectrum, 1:Spectrogram
  int showModeSpectrogram = 1;  // 0: moving spectrogram, 1: overwriting in loop
  int nFreqPoints;
  double timeInc;
  double timeWatch = 4.0;
  volatile int timeMultiplier = 1;  // should be accorded with nFFTAverage in AnalyzeActivity
  int nTimePoints;
  int spectrogramColorsPt;
  Matrix matrixSpectrogram = new Matrix();
  static final int[] cma = ColorMapArray.hot;
  double dBLowerBound = -120;
  
  public int getShowMode() {
    return showMode;
  }
  
  public void setTimeMultiplier(int nAve) {
    timeMultiplier = nAve;
  }
  
  public void setSpectrogramModeShifting(boolean b) {
    if (b) {
      showModeSpectrogram = 0;
    } else {
      showModeSpectrogram = 1;
    }
  }
  
  float oldYShift = 0;
  float oldYZoom = 1;

  public void switch2Spectrum() {
    yShift = oldYShift;
    yZoom = oldYZoom;
    showMode = 0;
    computeMatrix();
  }
  
  public void switch2Spectrogram(int sampleRate, int fftLen) {
    showMode = 1;
    setupSpectrogram(sampleRate, fftLen);
  }
  
  public void setupSpectrogram(int sampleRate, int fftLen) {
    if (sharedPref != null) {
      timeWatch = Double.parseDouble(sharedPref.getString("spectrogramDuration",
                  Double.toString(4.0)));
    }
    oldYShift = yShift;
    oldYZoom  = yZoom;
    nFreqPoints = fftLen / 2;                 // no direct current term
    timeInc     = fftLen / 2.0 / sampleRate;  // time of each slice. /2.0 due to overlap window
    nTimePoints = (int)Math.ceil(timeWatch / timeInc);
    spectrogramColorsPt = 0;                  // pointer to the row to be filled (row major)
    synchronized (this) {
      if (spectrogramColors == null || spectrogramColors.length != nFreqPoints * nTimePoints) {
        spectrogramColors = new int[nFreqPoints * nTimePoints];
      }
      Arrays.fill(spectrogramColors, 0);
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
  
  public void pushRawSpectrum(double[] db) {
    isBusy = true;
    synchronized (this) {
      int c;
      int pRef; 
      double d;
      if (showModeSpectrogram == 0) {
        System.arraycopy(spectrogramColors, nFreqPoints,
                         spectrogramColors, 0, spectrogramColors.length - nFreqPoints);
        pRef = spectrogramColors.length - nFreqPoints - 1;
        for (int i = 1; i < db.length; i++) {  // no direct current term
          d = db[i];
          if (d >= 0) {
            c = cma[0];
          } else if (d <= dBLowerBound || Double.isInfinite(d) || Double.isNaN(d)) {
            c = cma[cma.length-1];
          } else {
            c = cma[(int)(cma.length * d / dBLowerBound)];
          }
          spectrogramColors[pRef + i] = colorFromDB(db[i]);
        }
      } else {
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
    isBusy = false;
  }

  @Override
  protected void onDraw(Canvas c) {
    isBusy = true;
    c.concat(matrix0);
    c.save();
    if (showMode == 0) {
      drawGridLines(c, canvasWidth * gridDensity, canvasHeight * gridDensity);
      c.concat(matrix);
      c.drawPath(path, linePaint);
      drawCursor(c);
      c.restore();
      drawGridLabels(c);
    } else {
      float labelBeginX = getLabelBeginX();  // labelBeginX will make the scaling gesture inaccurate
      float labelBeginY = getLabelBeginY();
      // show Spectrogram
      float xBmpScale = (canvasWidth-labelBeginX)/nFreqPoints;
      float halfFreqResolutionShift = xZoom*(canvasWidth-labelBeginX)/nFreqPoints/2;
      matrixSpectrogram.reset();
      // when xZoom== 1: nFreqPoints -> canvasWidth; 0 -> labelBeginX
      matrixSpectrogram.postScale(xZoom*xBmpScale, labelBeginY/nTimePoints);
      matrixSpectrogram.postTranslate(labelBeginX - xShift/canvasWidth*xZoom*(canvasWidth-labelBeginX) + halfFreqResolutionShift, 0f);
      c.concat(matrixSpectrogram);
      
      // public void drawBitmap (int[] colors, int offset, int stride, float x, float y,
      //                         int width, int height, boolean hasAlpha, Paint paint)
      float x = 0;
      float y = 0;
      synchronized (this) {
        c.drawBitmap(spectrogramColors, 0, nFreqPoints, x, y,
                     nFreqPoints, nTimePoints, false, null);
      }
      if (showModeSpectrogram == 1) {
        c.drawLine(0, spectrogramColorsPt, nFreqPoints, spectrogramColorsPt, cursorPaint);
      }
      c.restore();
      drawFreqAxis(c, labelBeginX, labelBeginY, canvasWidth * gridDensity);
      if (labelBeginX > 0) {
        c.drawRect(0, 0, labelBeginX, labelBeginY, backgroundPaint);
        drawTimeAxis(c, labelBeginX, labelBeginY, canvasHeight * gridDensity);
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
    state.cx = cursorX;
    state.cy = cursorY;
    state.scale = xZoom;
    state.xlate = xShift;
    state.bounds = axisBounds;
    return state;
  }
  
  @Override
  public void onRestoreInstanceState(Parcelable state) {
    if (state instanceof State) {
      State s = (State) state;
      super.onRestoreInstanceState(s.getSuperState());
      this.cursorX = s.cx;
      this.cursorY = s.cy;
      this.xZoom = s.scale;
      this.xShift = s.xlate;
      this.axisBounds = s.bounds;
      // Log.i(AnalyzeActivity.TAG, "Restore: " + cx + "," + cy + " " + scale + ":" + xlate + " (" + bounds + ")");
      computeMatrix();
      invalidate();
    } else {
      super.onRestoreInstanceState(state);
    }
  }
  
  public static interface Ready {
    public void ready();
  }
  
  public static class State extends BaseSavedState {
    float cx, cy; 
    float scale;
    float xlate;
    RectF bounds;
    
    State(Parcelable state) {
      super(state);
    }
    
    @Override
    public void writeToParcel(Parcel out, int flags) {
      super.writeToParcel(out, flags);
      out.writeFloat(cx);
      out.writeFloat(cy);
      out.writeFloat(scale);
      out.writeFloat(xlate);
      bounds.writeToParcel(out, flags);
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
      scale = in.readFloat();
      xlate = in.readFloat();
      bounds = RectF.CREATOR.createFromParcel(in);
    }
  }
  
}
