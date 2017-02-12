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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.view.GestureDetectorCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.TextView;

/**
 * Audio "FFT" analyzer.
 * @author suhler@google.com (Stephen Uhler)
 */

// TODO: Require the permission for android >=6.
// https://developer.android.com/training/permissions/requesting.html
// https://developer.android.com/guide/topics/permissions/requesting.html

public class AnalyzerActivity extends Activity
    implements OnLongClickListener, OnClickListener,
               OnItemClickListener, AnalyzerGraphic.Ready {
  static final String TAG="AnalyzerActivity";

  AnalyzerViews analyzerViews;
  SamplingLoop samplingThread;
  private GestureDetectorCompat mDetector;

  AnalyzerParameters analyzerParam = null;

  double dtRMS = 0;
  double dtRMSFromFT = 0;
  double maxAmpDB;
  double maxAmpFreq;

  private boolean isMeasure = true;
  volatile boolean bSaveWav = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
//  Debug.startMethodTracing("calc");
    final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
    Log.i(TAG, " max runtime mem = " + maxMemory + "k");

    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    Resources res = getResources();
    analyzerParam = new AnalyzerParameters(res);

    // Set and get preferences in PreferenceActivity
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    // Set variable according to the preferences
    loadPreferenceForView();

    analyzerViews = new AnalyzerViews(this);

    // travel Views, and attach ClickListener to the views that contain android:tag="select"  
    visit((ViewGroup) analyzerViews.graphView.getRootView(), new Visit() {
      @Override
      public void exec(View view) {
        view.setOnLongClickListener(AnalyzerActivity.this);
        view.setOnClickListener(AnalyzerActivity.this);
        ((TextView) view).setFreezesText(true);
      }
    }, "select");

    mDetector = new GestureDetectorCompat(this, new AnalyzerGestureListener());
  }

  /**
   * Run processClick() for views, transferring the state in the textView to our
   * internal state, then begin sampling and processing audio data
   */

  @Override
  protected void onResume() {
    super.onResume();

    // Load preferences
    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

    boolean keepScreenOn = sharedPref.getBoolean("keepScreenOn", true);
    if (keepScreenOn) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    } else {
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    analyzerParam.audioSourceId = Integer.parseInt(sharedPref.getString("audioSource", Integer.toString(analyzerParam.RECORDER_AGC_OFF)));
    analyzerParam.wndFuncName = sharedPref.getString("windowFunction", "Hanning");
    analyzerParam.timeDurationPref = Double.parseDouble(sharedPref.getString("spectrogramDuration",
            Double.toString(6.0)));

    // Crash detection and recovery.
    SharedPreferences.Editor editor = sharedPref.edit();
    boolean bCrashed = sharedPref.getBoolean("Crashed", false);
    if (bCrashed) {  // If crashed last time, use default audio source.
      Log.w(TAG, "onResume(): abnormal exit detected. Changing default audio source.");
      analyzerParam.audioSourceId = analyzerParam.RECORDER_AGC_OFF;
      editor.putInt("audioSource", analyzerParam.audioSourceId);
    }
    editor.putBoolean("Crashed", true);  // will be reset in normal exit.
    editor.commit();

    // Settings of graph view
    // spectrum
    analyzerViews.graphView.setShowLines( sharedPref.getBoolean("showLines", false) );
    // set spectrum show range
    analyzerViews.graphView.setSpectrumDBLowerBound(
            Float.parseFloat(sharedPref.getString("spectrumRange", Double.toString(AnalyzerGraphic.minDB)))
    );

    // spectrogram
    analyzerViews.graphView.setSpectrogramModeShifting(sharedPref.getBoolean("spectrogramShifting", false));
    analyzerViews.graphView.setShowTimeAxis           (sharedPref.getBoolean("spectrogramTimeAxis", true));
    analyzerViews.graphView.setShowFreqAlongX         (sharedPref.getBoolean("spectrogramShowFreqAlongX", true));
    analyzerViews.graphView.setSmoothRender           (sharedPref.getBoolean("spectrogramSmoothRender", false));
    // set spectrogram show range
    analyzerViews.graphView.setSpectrogramDBLowerBound(
            Float.parseFloat(sharedPref.getString("spectrogramRange", Double.toString(analyzerViews.graphView.spectrogramPlot.dBLowerBound)))
    );

    analyzerViews.bWarnOverrun = sharedPref.getBoolean("warnOverrun", false);

    // Travel the views with android:tag="select" to get default setting values
    visit((ViewGroup) analyzerViews.graphView.getRootView(), new Visit() {
      @Override
      public void exec(View view) {
        processClick(view);
      }
    }, "select");
    analyzerViews.graphView.setReady(this);
    analyzerViews.enableSaveWavView(bSaveWav);

    // Start sampling
    samplingThread = new SamplingLoop(this, analyzerParam);
    samplingThread.start();
  }

  @Override
  protected void onPause() {
    super.onPause();
    samplingThread.finish();
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    SharedPreferences.Editor editor = sharedPref.edit();
    editor.putBoolean("Crashed", false);  // will be reset in normal exit.
    editor.commit();
  }

  @Override
  protected void onDestroy() {
//    Debug.stopMethodTracing();
    super.onDestroy();
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    savedInstanceState.putDouble("dtRMS",       dtRMS);
    savedInstanceState.putDouble("dtRMSFromFT", dtRMSFromFT);
    savedInstanceState.putDouble("maxAmpDB",    maxAmpDB);
    savedInstanceState.putDouble("maxAmpFreq",  maxAmpFreq);

    super.onSaveInstanceState(savedInstanceState);
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    // will be calls after the onStart()
    super.onRestoreInstanceState(savedInstanceState);

    dtRMS       = savedInstanceState.getDouble("dtRMS");
    dtRMSFromFT = savedInstanceState.getDouble("dtRMSFromFT");
    maxAmpDB    = savedInstanceState.getDouble("maxAmpDB");
    maxAmpFreq  = savedInstanceState.getDouble("maxAmpFreq");
  }
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.info, menu);
      return true;
  }
  
  // for pass audioSourceIDs and audioSourceNames to MyPreferences
  public final static String MYPREFERENCES_MSG_SOURCE_ID = "AnalyzerActivity.SOURCE_ID";
  public final static String MYPREFERENCES_MSG_SOURCE_NAME = "AnalyzerActivity.SOURCE_NAME";

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
      Log.i(TAG, item.toString());
      switch (item.getItemId()) {
      case R.id.info:
        analyzerViews.showInstructions();
        return true;
      case R.id.settings:
        Intent settings = new Intent(getBaseContext(), MyPreferences.class);
        settings.putExtra(MYPREFERENCES_MSG_SOURCE_ID, analyzerParam.audioSourceIDs);
        settings.putExtra(MYPREFERENCES_MSG_SOURCE_NAME, analyzerParam.audioSourceNames);
        startActivity(settings);
        return true;
      case R.id.info_recoder:
        Intent int_info_rec = new Intent(this, InfoRecActivity.class);
        startActivity(int_info_rec);
      return true;
      default:
          return super.onOptionsItemSelected(item);
      }
  }

  // Popup menu click listener
  // Read chosen preference, save the preference, set the state.
  @Override
  public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
    // get the tag, which is the value we are going to use
    String selectedItemTag = v.getTag().toString();
    // if tag() is "0" then do not update anything (it is a title)
    if (selectedItemTag.equals("0")) {
      return ;
    }

    // get the text and set it as the button text
    String selectedItemText = ((TextView) v).getText().toString();

    int buttonId = Integer.parseInt((parent.getTag().toString()));
    Button buttonView = (Button) findViewById(buttonId);
    buttonView.setText(selectedItemText);

    boolean b_need_restart_audio;

    // Save the choosen preference
    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    SharedPreferences.Editor editor = sharedPref.edit();
    
    // dismiss the pop up
    switch (buttonId) {
    case R.id.button_sample_rate:
      analyzerViews.popupMenuSampleRate.dismiss();
      analyzerParam.sampleRate = Integer.parseInt(selectedItemTag);
      b_need_restart_audio = true;
      editor.putInt("button_sample_rate", analyzerParam.sampleRate);
      break;
    case R.id.button_fftlen:
      analyzerViews.popupMenuFFTLen.dismiss();
      analyzerParam.fftLen = Integer.parseInt(selectedItemTag);
      b_need_restart_audio = true;
      editor.putInt("button_fftlen", analyzerParam.fftLen);
      break;
    case R.id.button_average:
      analyzerViews.popupMenuAverage.dismiss();
      analyzerParam.nFFTAverage = Integer.parseInt(selectedItemTag);
      if (analyzerViews.graphView != null) {
        analyzerViews.graphView.setTimeMultiplier(analyzerParam.nFFTAverage);
      }
      b_need_restart_audio = false;
      editor.putInt("button_average", analyzerParam.nFFTAverage);
      break;
    default:
      Log.w(TAG, "onItemClick(): no this button");
      b_need_restart_audio = false;
    }

    editor.commit();
    
    if (b_need_restart_audio) {
      reRecur();
    }
  }

  // Load preferences for Views
  // When this function is called, the SamplingLoop must not running in the meanwhile.
  void loadPreferenceForView() {
    // load preferences for buttons
    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    analyzerParam.sampleRate   = sharedPref.getInt("button_sample_rate", 8000);
    analyzerParam.fftLen       = sharedPref.getInt("button_fftlen",      1024);
    analyzerParam.nFFTAverage  = sharedPref.getInt("button_average",        1);
    analyzerParam.isAWeighting = sharedPref.getBoolean("dbA", false);
    if (analyzerParam.isAWeighting) {
      ((SelectorText) findViewById(R.id.dbA)).nextValue();
    }
    boolean isSpam = sharedPref.getBoolean("spectrum_spectrogram_mode", true);
    if (!isSpam) {
      ((SelectorText) findViewById(R.id.spectrum_spectrogram_mode)).nextValue();
    }
    
    Log.i(TAG, "loadPreferenceForView(): sampleRate  = " + analyzerParam.sampleRate);
    Log.i(TAG, "loadPreferenceForView(): fftLen      = " + analyzerParam.fftLen);
    Log.i(TAG, "loadPreferenceForView(): nFFTAverage = " + analyzerParam.nFFTAverage);
    ((Button) findViewById(R.id.button_sample_rate)).setText(Integer.toString(analyzerParam.sampleRate));
    ((Button) findViewById(R.id.button_fftlen     )).setText(Integer.toString(analyzerParam.fftLen));
    ((Button) findViewById(R.id.button_average    )).setText(Integer.toString(analyzerParam.nFFTAverage));
  }
  
  private boolean isInGraphView(float x, float y) {
    analyzerViews.graphView.getLocationInWindow(windowLocation);
    return x >= windowLocation[0] && y >= windowLocation[1] &&
            x < windowLocation[0] + analyzerViews.graphView.getWidth() &&
            y < windowLocation[1] + analyzerViews.graphView.getHeight();
  }

  // Button processing
  public void showPopupMenu(View view) {
    analyzerViews.showPopupMenu(view);
  }

  /**
   * Gesture Listener for graphView (and possibly other views)
   * How to attach these events to the graphView?
   * @author xyy
   */
  class AnalyzerGestureListener extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onDown(MotionEvent event) {  // enter here when down action happen
      flyingMoveHandler.removeCallbacks(flyingMoveRunnable);
      return true;
    }

    @Override
    public void onLongPress(MotionEvent event) {
      if (isInGraphView(event.getX(0), event.getY(0))) {
        if (!isMeasure) {  // go from "scale" mode to "cursor" mode
          switchMeasureAndScaleMode();
        }
      }
      measureEvent(event);  // force insert this event
    }
    
    @Override
    public boolean onDoubleTap(MotionEvent event) {
      if (!isMeasure) {
        scaleEvent(event);            // ends scale mode
        analyzerViews.graphView.resetViewScale();
      }
      return true;
    }

    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2, 
            float velocityX, float velocityY) {
      if (isMeasure) {
        // seems never reach here...
        return true;
      }
//      Log.d(TAG, "  AnalyzerGestureListener::onFling: " + event1.toString()+event2.toString());
      // Fly the canvas in graphView when in scale mode
      shiftingVelocity = (float) Math.sqrt(velocityX*velocityX + velocityY*velocityY);
      shiftingComponentX = velocityX / shiftingVelocity;
      shiftingComponentY = velocityY / shiftingVelocity;
      float DPRatio = getResources().getDisplayMetrics().density;
      flyAcceleration = 1200f * DPRatio;
      timeFlingStart = SystemClock.uptimeMillis();
      flyingMoveHandler.postDelayed(flyingMoveRunnable, 0);
      return true;
    }
    
    Handler flyingMoveHandler = new Handler();
    long timeFlingStart;                     // Prevent from running forever
    float flyDt = 1/20f;                     // delta t of refresh
    float shiftingVelocity;                  // fling velocity
    float shiftingComponentX;                // fling direction x
    float shiftingComponentY;                // fling direction y
    float flyAcceleration = 1200f;           // damping acceleration of fling, pixels/second^2
    
    Runnable flyingMoveRunnable = new Runnable() {
      @Override
      public void run() {
        float shiftingVelocityNew = shiftingVelocity - flyAcceleration*flyDt;
        if (shiftingVelocityNew < 0) shiftingVelocityNew = 0;
        // Number of pixels that should move in this time step
        float shiftingPixel = (shiftingVelocityNew + shiftingVelocity)/2 * flyDt;
        shiftingVelocity = shiftingVelocityNew;
        if (shiftingVelocity > 0f
            && SystemClock.uptimeMillis() - timeFlingStart < 10000) {
//          Log.i(TAG, "  fly pixels x=" + shiftingPixelX + " y=" + shiftingPixelY);
          AnalyzerGraphic graphView = analyzerViews.graphView;
          graphView.setXShift(graphView.getXShift() - shiftingComponentX*shiftingPixel / graphView.getCanvasWidth() / graphView.getXZoom());
          graphView.setYShift(graphView.getYShift() - shiftingComponentY*shiftingPixel / graphView.getCanvasHeight() / graphView.getYZoom());
          // Am I need to use runOnUiThread() ?
          analyzerViews.invalidateGraphView();
          flyingMoveHandler.postDelayed(flyingMoveRunnable, (int)(1000*flyDt));
        }
      }
    };
  }

  private void switchMeasureAndScaleMode() {
    isMeasure = !isMeasure;
    SelectorText st = (SelectorText) findViewById(R.id.graph_view_mode);
    st.performClick();
  }
  
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (isInGraphView(event.getX(0), event.getY(0))) {
      this.mDetector.onTouchEvent(event);
      if (isMeasure) {
        measureEvent(event);
      } else {
        scaleEvent(event);
      }
      analyzerViews.invalidateGraphView();
      // Go to scaling mode when user release finger in measure mode.
      if (event.getActionMasked() == MotionEvent.ACTION_UP) {
        if (isMeasure) {
          switchMeasureAndScaleMode();
        }
      }
    } else {
      // When finger is outside the plot, hide the cursor and go to scaling mode.
      if (isMeasure) {
        analyzerViews.graphView.hideCursor();
        switchMeasureAndScaleMode();
      }
    }
    return super.onTouchEvent(event);
  }
  
  /**
   *  Manage cursor for measurement
   */
  private void measureEvent(MotionEvent event) {
    switch (event.getPointerCount()) {
      case 1:
        analyzerViews.graphView.setCursor(event.getX(), event.getY());
        // TODO: if touch point is very close to boundary for a long time, move the view
        break;
      case 2:
        if (isInGraphView(event.getX(1), event.getY(1))) {
          switchMeasureAndScaleMode();
        }
    }
  }

  /**
   *  Manage scroll and zoom
   */
  final private static float INIT = Float.MIN_VALUE;
  private boolean isPinching = false;
  private float xShift0 = INIT, yShift0 = INIT;
  float x0, y0;
  int[] windowLocation = new int[2];

  private void scaleEvent(MotionEvent event) {
    if (event.getAction() != MotionEvent.ACTION_MOVE) {
      xShift0 = INIT;
      yShift0 = INIT;
      isPinching = false;
//      Log.i(TAG, "scaleEvent(): Skip event " + event.getAction());
      return;
    }
//    Log.i(TAG, "scaleEvent(): switch " + event.getAction());
    AnalyzerGraphic graphView = analyzerViews.graphView;
    switch (event.getPointerCount()) {
      case 2 :
        if (isPinching)  {
          graphView.setShiftScale(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
        } else {
          graphView.setShiftScaleBegin(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
        }
        isPinching = true;
        break;
      case 1:
        float x = event.getX(0);
        float y = event.getY(0);
        graphView.getLocationInWindow(windowLocation);
//        Log.i(TAG, "scaleEvent(): xy=" + x + " " + y + "  wc = " + wc[0] + " " + wc[1]);
        if (isPinching || xShift0 == INIT) {
          xShift0 = graphView.getXShift();
          x0 = x;
          yShift0 = graphView.getYShift();
          y0 = y;
        } else {
          // when close to the axis, scroll that axis only
          if (x0 < windowLocation[0] + 50) {
            graphView.setYShift(yShift0 + (y0 - y) / graphView.getCanvasHeight() / graphView.getYZoom());
          } else if (y0 < windowLocation[1] + 50) {
            graphView.setXShift(xShift0 + (x0 - x) / graphView.getCanvasWidth() / graphView.getXZoom());
          } else {
            graphView.setXShift(xShift0 + (x0 - x) / graphView.getCanvasWidth() / graphView.getXZoom());
            graphView.setYShift(yShift0 + (y0 - y) / graphView.getCanvasHeight() / graphView.getYZoom());
          }
        }
        isPinching = false;
        break;
      default:
        Log.i(TAG, "Invalid touch count");
        break;
    }
  }
  
  @Override
  public boolean onLongClick(View view) {
    vibrate(300);
    Log.i(TAG, "long click: " + view.toString());
    return true;
  }

  // Responds to layout with android:tag="select"
  // Called from SelectorText.super.performClick()
  @Override
  public void onClick(View v) {
    if (processClick(v)) {
      reRecur();
    }
    analyzerViews.invalidateGraphView();
  }

  private void reRecur() {
    samplingThread.finish();
    try {
      samplingThread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    samplingThread = new SamplingLoop(this, analyzerParam);
    samplingThread.start();
  }

  /**
   * Process a click on one of our selectors.
   * @param v   The view that was clicked
   * @return    true if we need to update the graph
   */

  public boolean processClick(View v) {
    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    SharedPreferences.Editor editor = sharedPref.edit();
    String value = ((TextView) v).getText().toString();
    switch (v.getId()) {
      case R.id.button_recording:
        bSaveWav = value.equals("Rec");
//        SelectorText st = (SelectorText) findViewById(R.id.run);
//        if (bSaveWav && ! st.getText().toString().equals("stop")) {
//          st.nextValue();
//          if (samplingThread != null) {
//            samplingThread.setPause(true);
//          }
//        }
        analyzerViews.enableSaveWavView(bSaveWav);
        return true;
      case R.id.run:
        boolean pause = value.equals("stop");
        if (samplingThread != null && samplingThread.getPause() != pause) {
          samplingThread.setPause(pause);
        }
        return false;
      case R.id.graph_view_mode:
        isMeasure = !value.equals("scale");
        return false;
      case R.id.dbA:
        analyzerParam.isAWeighting = !value.equals("dB");
        if (samplingThread != null) {
          samplingThread.setAWeighting(analyzerParam.isAWeighting);
        }
        editor.putBoolean("dbA", analyzerParam.isAWeighting);
        editor.commit();
        return false;
      case R.id.spectrum_spectrogram_mode:
        if (value.equals("spum")) {
          analyzerViews.graphView.switch2Spectrum();
        } else {
          analyzerViews.graphView.switch2Spectrogram();
        }
        editor.putBoolean("spectrum_spectrogram_mode", value.equals("spum"));
        editor.commit();
        return false;
      default:
        return true;
    }
  }

  private void vibrate(int ms) {
    //((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(ms);
  }

  /**
   * Visit all subviews of this view group and run command
   * @param group   The parent view group
   * @param cmd     The command to run for each view
   * @param select  The tag value that must match. Null implies all views
   */

  private void visit(ViewGroup group, Visit cmd, String select) {
    exec(group, cmd, select);
    for (int i = 0; i < group.getChildCount(); i++) {
      View c = group.getChildAt(i);
      if (c instanceof ViewGroup) {
        visit((ViewGroup) c, cmd, select);
      } else {
        exec(c, cmd, select);
      }
    }
  }

  private void exec(View v, Visit cmd, String select) {
    if (select == null || select.equals(v.getTag())) {
        cmd.exec(v);
    }
  }

  /**
   * Interface for view hierarchy visitor
   */
  interface Visit {
    void exec(View view);
  }

  /**
   * The graph view size has been determined - update the labels accordingly.
   */
  @Override
  public void ready() {
    // put code here for the moment that graph size just changed
    Log.v(TAG, "ready()");
    analyzerViews.invalidateGraphView();
  }
}
