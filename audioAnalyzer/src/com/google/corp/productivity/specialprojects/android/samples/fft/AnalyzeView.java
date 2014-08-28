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
 */

package com.google.corp.productivity.specialprojects.android.samples.fft;

import java.text.DecimalFormat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * Custom view to draw the FFT graph
 */

public class AnalyzeView extends View {
  private float cursorX, cursorY; // cursor location
  private float xZoom;     // horizontal scaling
  private float xShift;    // horizontal translation
  private float yZoom;     // vertical scaling
  private float yShift;    // vertical translation
  private float mark;
  private RectF axisBounds;
  private Ready readyCallback = null;      // callback to caller when rendering is complete
  
  private int canvasWidth, canvasHeight;   // size of my canvas
  private Paint linePaint;
  private Paint cursorPaint;
  private Paint gridPaint;
  private Paint labelPaint;
  private Path path;
  private int[] myLocation = {0, 0}; // window location on screen
  private Matrix matrix = new Matrix();
  private Matrix matrix0 = new Matrix();
  private static boolean isBusy = false;
  
  private float gridDensity;
  private double[][] gridPoints2   = new double[2][0];
  private double[][] gridPoints2dB = new double[2][0];
  private StringBuffer[] gridPoints2Str = new StringBuffer[0];
  private StringBuffer[] gridPoints2StrDB = new StringBuffer[0];
  
  public boolean isBusy() {
    return isBusy;
  }
  
  public AnalyzeView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setup(attrs);
  }
  
  public AnalyzeView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setup(attrs);
  }
  
  public AnalyzeView(Context context) {
    super(context);
    setup(null);
  }
  
  public void setReady(Ready ready) {
    // Log.i(AnalyzeActivity.TAG, "Setting Ready");
    this.readyCallback = ready;
  }
  
  private void setup(AttributeSet attrs) {
    // Log.i(AnalyzeActivity.TAG, "Setup plot view");
    path = new Path();
    
    linePaint = new Paint();
    linePaint.setColor(Color.RED);
    linePaint.setStyle(Paint.Style.STROKE);
    linePaint.setStrokeWidth(0);
    
    cursorPaint = new Paint(linePaint);
    cursorPaint.setColor(Color.BLUE);
    
    gridPaint = new Paint(linePaint);
    gridPaint.setColor(Color.DKGRAY);

    labelPaint = new Paint(linePaint);
    labelPaint.setColor(Color.GRAY);

    cursorX = cursorY = 0f;
    xZoom=1f;
    xShift=0f;
    yZoom=1f;
    yShift=0f;
    canvasWidth = canvasHeight = 0;
    axisBounds = new RectF(0.0f, 0.0f, 8000.0f, -90.0f);
    gridDensity = 1/85f;  // every 85 pixel one grid line, on average
  }
  
  public void setBounds(RectF bounds) {
    this.axisBounds = bounds;
  }
  
  public RectF getBounds() {
    return new RectF(axisBounds);
  }
  
  // return position of grid lines, there are roughly gridDensity lines for the bigger grid
  private void genLinearGridPoints(double[][] gridPointsArray, double startValue, double endValue,
                                   double gridDensity, int scale_mode) {
    if (startValue == endValue) {
      Log.e(AnalyzeActivity.TAG, "genLinearGridPoints(): startValue == endValue !");
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
    if (scale_mode == 0 || intervalValue <= 1) {  // Linear scale (Hz)
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
      Log.e(AnalyzeActivity.TAG, " genLinearGridPoints(): empty array!!");
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
  
  private DecimalFormat myFormatter = new DecimalFormat("@@");
  double[][] oldGridPointBoundaryArray = new double[2][2];
  
  double[][][] gridPointsArray = {gridPoints2, gridPoints2dB};
  StringBuffer[][] gridPointsStrArray = new StringBuffer[2][0];
  
  public enum GridScaleType {
    FREQ(0), DB(1);
    
    private final int value;
    private GridScaleType(int value) { this.value = value; }
    public int getValue() { return value; }
  }

  // It's so ugly to write these StringBuffer stuff -- in order to reduce garbage -- and without big success!
  // Also, since there is no "pass by reference", modify array is also ugly...
  void updateGridLabels(double startValue, double endValue, double gridDensity, GridScaleType scale_mode) {
    int scale_mode_id = scale_mode.getValue();
    double[][] gridPoints = gridPointsArray[scale_mode_id];
    StringBuffer[] gridPointsStr = gridPointsStrArray[scale_mode_id];
    double[] oldGridPointBoundary = oldGridPointBoundaryArray[scale_mode_id];

    genLinearGridPoints(gridPoints, startValue, endValue, gridDensity, scale_mode_id);
    double[] gridPointsBig = gridPoints[0];
    boolean needUpdate = false;
    if (gridPointsBig.length != gridPointsStr.length) {
      gridPointsStrArray[scale_mode_id] = new StringBuffer[gridPointsBig.length];
      gridPointsStr = gridPointsStrArray[scale_mode_id];
      for (int i = 0; i < gridPointsBig.length; i++) {
        gridPointsStr[i] = new StringBuffer();
      }
      if (scale_mode_id == 0) {
        gridPoints2Str = gridPointsStr;
      } else {
        gridPoints2StrDB = gridPointsStr;
      }
      needUpdate = true;
    }
    if (gridPointsBig.length > 0 && (needUpdate || gridPointsBig[0] != oldGridPointBoundary[0]
        || gridPointsBig[gridPointsBig.length-1] != oldGridPointBoundary[1])) {
      oldGridPointBoundary[0] = gridPointsBig[0];
      oldGridPointBoundary[1] = gridPointsBig[gridPointsBig.length-1];
      for (int i = 0; i < gridPointsStr.length; i++) {
        gridPointsStr[i].setLength(0);
        gridPointsStr[i].append(myFormatter.format(gridPointsBig[i]));
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
      c.drawLine(xPos, 0, xPos, 0.03f * canvasHeight, gridPaint);
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
  
  // The coordinate frame of this function is identical to its view(id=plot).
  private void drawGridLabels(Canvas c) {
    for(int i = 0; i < gridPoints2Str.length; i++) {
      float xPos = canvasViewX4axis((float)gridPoints2[0][i]);
      c.drawText(gridPoints2Str[i].toString(), xPos, 15, labelPaint);
      c.drawLine(0, 0, canvasWidth, 0, labelPaint);
    }
    for(int i = 0; i < gridPoints2StrDB.length; i++) {
      float yPos = canvasViewY4axis((float)gridPoints2dB[0][i]);
      c.drawText(gridPoints2StrDB[i].toString(), 0, yPos, labelPaint);
      c.drawLine(0, 0, 0, canvasHeight, labelPaint);
    }
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
    float minDB = canvasY4axis(getMinY());
    path.reset();
    if (bars) {
      for (int i = start; i < end; i++) {
        float x = (float) i / db.length * canvasWidth;
        float y = canvasY4axis(clampDB((float)db[i]));
        if (y != canvasHeight) {
          //path.moveTo(x, canvasHeight);
          path.moveTo(x, minDB);
          path.lineTo(x, y);
        }
      }
    } else {
      // (0,0) is the upper left of the View, in pixel unit
      path.moveTo((float) start / db.length * canvasWidth, canvasY4axis(clampDB((float)db[start])));
      for (int i = start+1; i < end; i++) {
        float x = (float) i / db.length * canvasWidth;
        float y = canvasY4axis(clampDB((float)db[i]));
        path.lineTo(x, y);
      }
    }
    isBusy = false;
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
        cursorX = x + myLocation[0];
        cursorY = y - myLocation[1];
        cursorX = cursorX/xZoom + xShift; // ??
      }
      return true;
    } else {
      return false;
    }
  }
  
  // XXX this doesn't reset on size changes
  
  public void setMark(double hz) {
    float x = (float) (hz / axisBounds.width() * canvasWidth);
    mark = (x + myLocation[0]) / xZoom + xShift; 
    // Log.i(AnalyzeActivity.TAG, "mark=" + mark);
  }
  
  @Override
  public float getX() {
    // return bounds.width() * (xlate + cx) / (scale * w);
    return  canvasWidth == 0 ? 0 : axisBounds.width() * cursorX / canvasWidth;
  }
  
  /**
   * Translate a mouse event X coordinate into a graph coordinate.
   */
  public float xlateX(float x) {
    getLocationOnScreen(myLocation);
    float tmp =  (x + myLocation[0]) / xZoom + xShift;
    return canvasWidth == 0 ? 0.0f : axisBounds.width() * tmp / canvasWidth;
  }
  
  public float getY() {
    return  canvasHeight == 0 ? 0 : axisBounds.height() * cursorY / canvasHeight;
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
  
  private boolean intersects(float x, float y) {
    getLocationOnScreen(myLocation);
    return x >= myLocation[0] && y >= myLocation[1] &&
       x < myLocation[0] + getWidth() && y < myLocation[1] + getHeight();
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
    return clamp(offset, canvasY4axis(12f), canvasY4axis(-144f) - canvasHeight / yZoom);
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
    if (canvasWidth*0.2f < xDiffOld) {
      xZoom  = clamp(xZoomOld * Math.abs(x1-x2)/xDiffOld, 1f, axisBounds.width()/100f);    // 100 sample frequency full screen
    }
    xShift = clampXShift(xShiftOld + xMidOld/xZoomOld - (x1+x2)/2f/xZoom);
    if (canvasHeight*0.2f < yDiffOld) {
      yZoom  = clamp(yZoomOld * Math.abs(y1-y2)/yDiffOld, 1f, -axisBounds.height()/12f);  // ~ 3dB full screen
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
    Log.i(AnalyzeActivity.TAG, "  onSizeChanged(): canvas (" + oldw + "," + oldh + ") -> (" + w + "," + h + ")");
    if (oldh == 0 && h > 0 && readyCallback != null) {
      readyCallback.ready();
    }
    isBusy = false;
  }
  
  @Override
  protected void onDraw(Canvas c) {
    isBusy = true;
    c.concat(matrix0);
    c.save();
    drawGridLines(c, canvasWidth * gridDensity, canvasHeight * gridDensity);
    c.concat(matrix);
    c.drawPath(path, linePaint);
    if (cursorX > 0) {
      c.drawLine(cursorX, 0, cursorX, canvasHeight, cursorPaint); 
    }
    if (cursorY > 0) {
      c.drawLine(0, cursorY, canvasWidth, cursorY, cursorPaint); 
    }
    if (mark > 0f) {
      c.drawLine(mark - 3, 0, mark, 25, cursorPaint);
      c.drawLine(mark + 3, 0, mark, 25, cursorPaint);
    }
    c.restore();
    drawGridLabels(c);
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
