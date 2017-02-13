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

/**
 * Custom view to draw the FFT graph
 */

public class AnalyzerGraphic extends View {
  private final String TAG = "AnalyzerGraphic:";
  private Ready readyCallback = null;      // callback to caller when rendering is complete
  private float xZoom, yZoom;     // horizontal and vertical scaling
  private float xShift, yShift;   // horizontal and vertical translation, in unit 1 unit
  //private RectF axisBounds;       // bounds for the physical context
  private double[] savedDBSpectrum = new double[0];
  static float minDB = -144f;    // hard lower bound for dB
  static float maxDB = 12f;      // hard upper bound for dB

  private int canvasWidth, canvasHeight;   // size of my canvas
  private int[] myLocation = {0, 0}; // window location on screen
  private Matrix matrix0 = new Matrix();
  private volatile static boolean isBusy = false;

  SpectrumPlot spectrumPlot;
  SpectrogramPlot spectrogramPlot;

  Context context;

  static boolean isBusy() {
    return isBusy;
  }

  static void setIsBusy(boolean b) { isBusy = b; }

  public AnalyzerGraphic(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setup(attrs, context);
  }

  public AnalyzerGraphic(Context context, AttributeSet attrs) {
    super(context, attrs);
    setup(attrs, context);
  }

  public AnalyzerGraphic(Context context) {
    super(context);
    setup(null, context);
  }

  public void setReady(Ready ready) {
    this.readyCallback = ready;
  }

  private void setup(AttributeSet attrs, Context _context) {
    context = _context;
    Log.v(TAG, "setup():");
    matrix0.reset();
    matrix0.setTranslate(0f, 0f);
    matrix0.postScale(1f, 1f);

    Resources res = _context.getResources();
    minDB = Float.parseFloat(res.getString(R.string.max_DB_range));

    xZoom=1f;
    xShift=0f;
    yZoom=1f;
    yShift=0f;
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

  void setSpectrumDBLowerBound(float b) {
    spectrumPlot.axisY.vHigherBound = b;
  }

  void setSpectrogramDBLowerBound(float b) {
    spectrogramPlot.dBLowerBound = b;
  }

  public void setShowLines(boolean b) {
    spectrumPlot.showLines = b;
  }

  // All FFT data will enter this view through this interface
  // Will be called in another thread (SamplingLoop)
  public void saveSpectrum(double[] db) {
    synchronized (savedDBSpectrum) {  // TODO: need lock on savedDBSpectrum, but how?
      if (savedDBSpectrum == null || savedDBSpectrum.length != db.length) {
        savedDBSpectrum = new double[db.length];
      }
      System.arraycopy(db, 0, savedDBSpectrum, 0, db.length);  // TODO: sync?
    }
    // TODO: Should run on another thread? Or lock on data Or CompletionService?
    if (showMode == 1) {
      spectrogramPlot.saveRowSpectrumAsColor(savedDBSpectrum);
    }
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
      // limit view to minDB ~ maxDB, assume linear in dB scale
      return clamp(offset, (maxDB - spectrumPlot.axisY.vLowerBound) / spectrumPlot.axisY.diffVBounds(),
                           (minDB - spectrumPlot.axisY.vLowerBound) / spectrumPlot.axisY.diffVBounds() - 1 / yZoom);
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

  private int showMode = 0;                      // 0: Spectrum, 1:Spectrogram

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

  public void setShowFreqAlongX(boolean b) {
    spectrogramPlot.setShowFreqAlongX(b);
  }

  public void setSmoothRender(boolean b) {
    spectrogramPlot.setSmoothRender(b);
  }

  float oldYShift = 0;
  float oldXShift = 0;
  float oldYZoom = 1;
  float oldXZoom = 1;

  // Note: Assume setupPlot() was called once.
  public void switch2Spectrum() {
    Log.v(TAG, "switch2Spectrum()");
    if (showMode == 0) {
      return;
    }
    // execute when switch from Spectrogram mode to Spectrum mode
    showMode = 0;
    if (spectrogramPlot.showFreqAlongX) {
      //< the frequency range is the same
    } else {
      // get frequency range
      xShift = 1 - yShift - 1/yZoom;
      xZoom = yZoom;
    }
    yShift = oldYShift;
    yZoom = oldYZoom;
    updateAxisZoomShift();
  }

  // Note: Assume setupPlot() was called once.
  public void switch2Spectrogram() {
    if (showMode == 0 && canvasHeight > 0) { // canvasHeight==0 means the program is just start
      oldXShift = xShift;
      oldXZoom  = xZoom;
      oldYShift = yShift;
      oldYZoom  = yZoom;
      if (spectrogramPlot.showFreqAlongX) {
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
    showMode = 1;
    updateAxisZoomShift();
  }

  private void updateAxisZoomShift() {
    if (showMode == 0) {
      spectrumPlot.setZooms(xZoom, xShift, yZoom, yShift);
    } else {
      spectrogramPlot.setZooms(xZoom, xShift, yZoom, yShift);
    }
  }

  float freq_lower_bound = 0f;

  // Call this when settings changed.
  void setupPlot(int sampleRate, int fftLen, double timeDurationE, int nAve) {
    freq_lower_bound = sampleRate/fftLen;
    float freq_lower_bound_local = 0;
    if (spectrumPlot.axisX.mapTypeInt != ScreenPhysicalMapping.Type.LINEAR.getValue()) {
      freq_lower_bound_local = freq_lower_bound;
    }
    // Spectrum
    RectF axisBounds = new RectF(freq_lower_bound_local, 0.0f, sampleRate/2.0f, spectrumPlot.axisY.vHigherBound);
    Log.i(TAG, "setupPlot(): W=" + canvasWidth + "  H=" + canvasHeight + "  dB=" + spectrumPlot.axisY.vHigherBound);
    spectrumPlot.setCanvas(canvasWidth, canvasHeight, axisBounds);

    // Spectrogram
    spectrogramPlot.setupSpectrogram(sampleRate, fftLen, timeDurationE, nAve);
    if (spectrogramPlot.showFreqAlongX) {
      axisBounds = new RectF(0.0f, 0.0f, sampleRate/2.0f, (float)timeDurationE * nAve);
    } else {
      axisBounds = new RectF(0.0f, 0.0f, (float)timeDurationE * nAve, sampleRate/2.0f);
    }
    spectrogramPlot.setCanvas(canvasWidth, canvasHeight, axisBounds);
  }

  void setLinearLogRulerMode(boolean b) {
    if (showMode == 0) {
      if (b) {
        spectrumPlot.setAxisMode(ScreenPhysicalMapping.Type.LINEAR, 0f);
      } else {
        spectrumPlot.setAxisMode(ScreenPhysicalMapping.Type.LOG, freq_lower_bound);
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
      spectrumPlot.drawSpectrumPlot(c, savedDBSpectrum);
    } else {
      spectrogramPlot.drawSpectrogramPlot(c);
    }
    isBusy = false;
  }

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
    state.OyZ = oldYZoom;
    state.xS = xShift;
    state.yS = yShift;
    state.OyS = oldYShift;
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
      this.oldYZoom = s.OyZ;
      this.xShift = s.xS;
      this.yShift = s.yS;
      this.oldYShift = s.OyS;
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

  public static interface Ready {
    public void ready();
  }

  public static class State extends BaseSavedState {
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
