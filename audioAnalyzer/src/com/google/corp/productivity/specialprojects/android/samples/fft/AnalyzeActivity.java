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

import com.google.corp.productivity.specialprojects.android.samples.fft.AnalyzeView.Ready;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v4.view.GestureDetectorCompat;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * Audio "FFT" analyzer.
 * @author suhler@google.com (Stephen Uhler)
 */

public class AnalyzeActivity extends Activity
    implements OnLongClickListener, OnClickListener,
               OnItemClickListener, Ready {
  static final String TAG="AnalyzeActivity";

  private AnalyzeView graphView;
  private Looper samplingThread;
  private GestureDetectorCompat mDetector;

  private final static double SAMPLE_VALUE_MAX = 32767.0;   // Maximum signal value
  private final static int RECORDER_AGC_OFF = MediaRecorder.AudioSource.VOICE_RECOGNITION;
  private final static int BYTE_OF_SAMPLE = 2;
  
  private static int fftLen = 2048;
  private static int sampleRate = 8000;
  private static String wndFuncName;
  private static int nFFTAverage = 2;

  private static boolean showLines;
  private boolean isTesting = false;
  private boolean isMeasure = true;
  private boolean isAWeighting = false;
  
  float listItemTextSize = 20;  // XXX define it in res

  PopupWindow popupMenuSampleRate;
  PopupWindow popupMenuFFTLen;
  PopupWindow popupMenuAverage;
  
  public PopupWindow popupMenuCreate(String[] popUpContents, int resId) {
    
    // initialize a pop up window type
    PopupWindow popupWindow = new PopupWindow(this);

    // the drop down list is a list view
    ListView listView = new ListView(this);
    
    // set our adapter and pass our pop up window contents
    ArrayAdapter<String> aa = popupMenuAdapter(popUpContents);
    listView.setAdapter(aa);
    
    // set the item click listener
    listView.setOnItemClickListener(this);

    listView.setTag(resId);  // button res ID, so we can trace back which button is pressed
    
    // get max text width
    Paint mTestPaint = new Paint();
    mTestPaint.setTextSize(listItemTextSize);
    float w = 0;
    for (int i = 0; i < popUpContents.length; i++) {
      String st = popUpContents[i].split("::")[0];
      float wi = mTestPaint.measureText(st);
      if (w < wi) {
        w = wi;
      }
    }
    
    // left and right padding, at least +7, or the whole app will stop respond, don't know why
    w = w + 25;
    if (w < 60) {
      w = 60;
    }

    // some other visual settings
    popupWindow.setFocusable(true);
    popupWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
    // Set window width according to max text width
    popupWindow.setWidth((int)(w));
    // also set button width
    ((Button) findViewById(resId)).setWidth((int)w);
    
    // set the list view as pop up window content
    popupWindow.setContentView(listView);
    
    return popupWindow;
  }
  
  /*
   * adapter where the list values will be set
   */
  private ArrayAdapter<String> popupMenuAdapter(String itemTagArray[]) {
    ArrayAdapter<String> adapter =
      new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, itemTagArray) {
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
          // setting the ID and text for every items in the list
          String item = getItem(position);
          String[] itemArr = item.split("::");
          String text = itemArr[0];
          String id = itemArr[1];

          // visual settings for the list item
          TextView listItem = new TextView(AnalyzeActivity.this);

          listItem.setText(text);
          listItem.setTag(id);
          listItem.setTextSize(listItemTextSize);
          listItem.setPadding(5, 5, 5, 5);
          listItem.setTextColor(Color.WHITE);
          listItem.setGravity(android.view.Gravity.CENTER);
          
          return listItem;
        }
      };
    return adapter;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
//  Debug.startMethodTracing("calc");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    
    // filter out the invalid sampling rates
    SelectorText st = (SelectorText) findViewById(R.id.sampling_rate);
    st.setValues(validateAudioRates(st.getValues()));
    
    // set and get preferences in PreferenceActivity
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    updatePreferenceSaved();
    
    // travel Views, and attach ClickListener to the views that contain android:tag="select"  
    graphView = (AnalyzeView) findViewById(R.id.plot);
    visit((ViewGroup) graphView.getRootView(), new Visit() {
      @Override
      public void exec(View view) {
        view.setOnLongClickListener(AnalyzeActivity.this);
        view.setOnClickListener(AnalyzeActivity.this);
        ((TextView) view).setFreezesText(true);
      }
    }, "select");
    
    Resources res = getResources();
    
    // http://www.codeofaninja.com/2013/04/show-listview-as-drop-down-android.html
    ////////////// initialize pop up window items list ////////////////
    popupMenuSampleRate = popupMenuCreate( validateAudioRates(
        res.getStringArray(R.array.sample_rates)), R.id.button_sample_rate);
    popupMenuFFTLen = popupMenuCreate(
        res.getStringArray(R.array.fft_len), R.id.button_fftlen);
    popupMenuAverage = popupMenuCreate(
        res.getStringArray(R.array.fft_ave_num), R.id.button_average);
    
    mDetector = new GestureDetectorCompat(this, new AnalyzerGestureListener());

    setTextViewFontSize();
  }

  // Set text font size of textview_cur and textview_peak
  // according to space left
  @SuppressWarnings("deprecation")
  private void setTextViewFontSize() {
    TextView tv = (TextView) findViewById(R.id.textview_cur);

    Paint mTestPaint = new Paint();
    mTestPaint.set(tv.getPaint());
    mTestPaint.setTextSize(tv.getTextSize());
    mTestPaint.setTypeface(Typeface.MONOSPACE);
    
    final String text = "Peak:XXXXX.XHz(AX#+XX) -XXX.XdB";
    Display display = getWindowManager().getDefaultDisplay();
    Resources r = getResources();
    float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60, r.getDisplayMetrics());
    px = display.getWidth() - px - 5;  // pixels left
    
    // At this point tv.getWidth(), tv.getLineCount() will return 0
    Log.i(TAG, "  px = " + px);
    Log.i(TAG, "  mTestPaint.measureText(text) = " + mTestPaint.measureText(text));
    
    float fs = tv.getTextSize();
    Log.i(TAG, "  fs_0 = " + fs);
    while (mTestPaint.measureText(text) > px && fs > 5) {
      fs -= 0.5;
      mTestPaint.setTextSize(fs);
    }
    Log.i(TAG, "  fs_1 = " + fs);
    ((TextView) findViewById(R.id.textview_cur)).setTextSize(fs);
    ((TextView) findViewById(R.id.textview_peak)).setTextSize(fs);

  }
  
  @SuppressWarnings("deprecation")
  public void showPopupMenu(View view) {
    // popup menu position
    // In API 19, we can use showAsDropDown(View anchor, int xoff, int yoff, int gravity)
    // The problem in showAsDropDown (View anchor, int xoff, int yoff) is
    // it may show the window in wrong direction (so that we can't see it)
    int[] wl = new int[2];
    view.getLocationInWindow(wl);
    int x_left = wl[0];
    int y_bottom = getWindowManager().getDefaultDisplay().getHeight() - wl[1];
    int gravity = android.view.Gravity.LEFT | android.view.Gravity.BOTTOM;
    Log.i(TAG, " showPupupMenu()");
    Log.i(TAG, " wl = " + wl[0] + ", " + wl[1]);
    
    switch (view.getId()) {
    case R.id.button_sample_rate:
      popupMenuSampleRate.showAtLocation(view, gravity, x_left, y_bottom);
      break;
    case R.id.button_fftlen:
      popupMenuFFTLen.showAtLocation(view, gravity, x_left, y_bottom);
      break;
    case R.id.button_average:
      popupMenuAverage.showAtLocation(view, gravity, x_left, y_bottom);
      break;
    }
  }

  // popup menu click listener
  @Override
  public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
    // get the text and set it as the button text
    String selectedItemText = ((TextView) v).getText().toString();

    int buttonId = Integer.parseInt((parent.getTag().toString()));
    Button buttonView = (Button) findViewById(buttonId);

    buttonView.setText(selectedItemText);

    // dismiss the pop up
    switch (buttonId) {
    case R.id.button_sample_rate:
      popupMenuSampleRate.dismiss();
      break;
    case R.id.button_fftlen:
      popupMenuFFTLen.dismiss();
      break;
    case R.id.button_average:
      popupMenuAverage.dismiss();
      break;
    }
    
    // get the tag, which is the value we are going to use
    String selectedItemTag = ((TextView) v).getTag().toString();
    Toast.makeText(this, "Dog ID is: " + selectedItemTag, Toast.LENGTH_SHORT).show();
    
    Log.i(TAG, "position = " + position + "  id = " + id);
    Log.i(TAG, "parent.getTag() = " + parent.getTag());
  }

  /**
   * Run processClick() for views, transferring the state in the textView to our
   * internal state, then begin sampling and processing audio data
   */

  @Override
  protected void onResume() {
    super.onResume();
    // travel the views with android:tag="select" to get default setting values  
    visit((ViewGroup) graphView.getRootView(), new Visit() {
      @Override
      public void exec(View view) {
        processClick(view);
      }
    }, "select");
    graphView.setReady(this);

    samplingThread = new Looper();
    samplingThread.start();
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  @Override
  protected void onPause() {
    super.onPause();
    samplingThread.finish();
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  @Override
  protected void onDestroy() {
//    Debug.stopMethodTracing();
    super.onDestroy();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
  }

  @Override
  public void onRestoreInstanceState(Bundle bundle) {
    super.onRestoreInstanceState(bundle);
  }
  
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.info, menu);
      return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
      Log.i(TAG, item.toString());
      switch (item.getItemId()) {
      case R.id.info:
        showInstructions();
        return true;
      case R.id.settings:
        Intent settings = new Intent(getBaseContext(), MyPreferences.class);
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

  private void showInstructions() {
    TextView tv = new TextView(this);
    tv.setMovementMethod(new ScrollingMovementMethod());
    tv.setText(Html.fromHtml(getString(R.string.instructions_text)));
    new AlertDialog.Builder(this)
      .setTitle(R.string.instructions_title)
      .setView(tv)
      .setNegativeButton(R.string.dismiss, null)
      .create().show();
  }

  void updatePreferenceSaved() {
    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    showLines   = sharedPref.getBoolean("showLines", false);
    wndFuncName = sharedPref.getString("windowFunction", "Blackman Harris");
    sampleRate  = Integer.parseInt(sharedPref.getString("sampleRate", "16000"));
    fftLen      = Integer.parseInt(sharedPref.getString("fftBins", "1024"));
    nFFTAverage = Integer.parseInt(sharedPref.getString("nFFTAverage", "2"));
  }
  
  // I'm using a old cell phone -- API level 9 (android 2.3.6)
  @SuppressWarnings("deprecation")
  public static class MyPreferences extends PreferenceActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preferences);
    }

    SharedPreferences.OnSharedPreferenceChangeListener prefListener =
        new SharedPreferences.OnSharedPreferenceChangeListener() {
      public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Log.i(TAG, key + "=" + prefs);
        if (key == null || key.equals("showLines")) {
          showLines = prefs.getBoolean("showLines", false);
        }
        if (key == null || key.equals("refreshRate")) {
//          updateMs = 1000 / Integer.parseInt(prefs.getString(pref, "25"));
        }
//        if (key == null || key.equals("fftBins")) {
//          fftLen = Integer.parseInt(prefs.getString("fftBins", "1024"));
//        }
//        if (key == null || key.equals("sampleRate")) {
//          sampleRate = Integer.parseInt(prefs.getString("sampleRate", "16000"));
//        }
        if (key == null || key.equals("windowFunction")) {
          wndFuncName = prefs.getString("windowFunction", "Blackman Harris");
          Preference connectionPref = findPreference(key);
          connectionPref.setSummary(prefs.getString(key, ""));
        }
        if (key == null || key.equals("nFFTAverage")) {
          nFFTAverage = Integer.parseInt(prefs.getString("nFFTAverage", "2"));
        }
      }
    };
    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(prefListener);
    }
    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(prefListener);
    }
  }
  
  private boolean isInGraphView(float x, float y) {
    graphView.getLocationInWindow(windowLocation);
    return x>windowLocation[0] && y>windowLocation[1] && x<windowLocation[0]+graphView.getWidth() && y<windowLocation[1]+graphView.getHeight();
  }
  
  /**
   * Gesture Listener for graphView (and possibly other views)
   * XXX  How to attach these events to the graphView?
   * @author xyy
   */
  class AnalyzerGestureListener extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onDown(MotionEvent event) {
      return true;
    }
    
    @Override
    public boolean onDoubleTap(MotionEvent event) {
      if (isInGraphView(event.getX(0), event.getY(0))) {
        if (isMeasure == false) {  // go from scale mode to measure mode (one way)
          isMeasure = !isMeasure;
          SelectorText st = (SelectorText) findViewById(R.id.graph_view_mode);
          st.performClick();
          Log.d(TAG, "  onDoubleTap(): ");
        } else {
          graphView.resetViewScale();
        }
      }
      return false;
    }

    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2, 
            float velocityX, float velocityY) {
      Log.d(TAG, "  AnalyzerGestureListener::onFling: " + event1.toString()+event2.toString());
      // Fly the canvas in graphView when in scale mode
      return true;
    }
  }
  
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    this.mDetector.onTouchEvent(event);
    if (isMeasure) {
      measureEvent(event);
    } else {
      scaleEvent(event);
    }
    graphView.invalidate();
    return super.onTouchEvent(event);
  }
  
  /**
   *  Manage cursor for measurement
   */
  private void measureEvent(MotionEvent event) {
    switch (event.getPointerCount()) {
      case 1:
        if (graphView.setCursor(event.getX(), event.getY())) {
          updateAllLabels();
        }
        break;
      case 2:
        if (isInGraphView(event.getX(0), event.getY(0)) && isInGraphView(event.getX(1), event.getY(1))) {
          isMeasure = !isMeasure;
          SelectorText st = (SelectorText) findViewById(R.id.graph_view_mode);
          st.performClick();
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
      Log.i(TAG, "scaleEvent(): Skip event " + event.getAction());
      return;
    }
//    Log.i(TAG, "scaleEvent(): switch " + event.getAction());
    switch (event.getPointerCount()) {
      case 2 :
        if (isPinching)  {
          graphView.setShiftScale(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
          updateAllLabels();
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
            graphView.setYShift(yShift0 + (y0 - y) / graphView.getYZoom());
          } else if (y0 < windowLocation[1] + 50) {
            graphView.setXShift(xShift0 + (x0 - x) / graphView.getXZoom());
          } else {
            graphView.setXShift(xShift0 + (x0 - x) / graphView.getXZoom());
            graphView.setYShift(yShift0 + (y0 - y) / graphView.getYZoom());
          }
          updateAllLabels();
        }
        isPinching = false;
        break;
      default:
        Log.v(TAG, "Invalid touch count");
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
//    Log.i(TAG, "onClick(): " + v.toString());
    if (processClick(v)) {
      reRecur();
      updateAllLabels();
    }
  }

  private void reRecur() {
    samplingThread.finish();
    try {
      samplingThread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    samplingThread = new Looper();
    samplingThread.start();
    if (samplingThread.stft != null) {
      samplingThread.stft.setAWeighting(isAWeighting);
    }
  }

  /**
   * Process a click on one of our selectors.
   * @param v   The view that was clicked
   * @return    true if we need to update the graph
   */

  public boolean processClick(View v) {
    String value = ((TextView) v).getText().toString();
    if (v.getId() == R.id.test) {
      isTesting = value.equals("test");
      return false;
    }
    if (v.getId() == R.id.run) {
      boolean pause = value.equals("stop");
      if (samplingThread != null && samplingThread.getPause() != pause) {
        samplingThread.setPause(pause);
      }
      return false;
    }
    if (v.getId() == R.id.graph_view_mode) {
      isMeasure = !value.equals("scale");
      return false;
    }
    if (v.getId() == R.id.dbA) {
      isAWeighting = !value.equals("dB");
      if (samplingThread != null && samplingThread.stft != null) {
        samplingThread.stft.setAWeighting(isAWeighting);
      }
      return false;
    }
    if (v.getId() == R.id.bins) {
      fftLen = Integer.parseInt(value);
    } else if (v.getId() == R.id.sampling_rate) {
      sampleRate = Integer.parseInt(value);
      RectF bounds = graphView.getBounds();
      bounds.right = sampleRate / 2;
      bounds.bottom = -120;
      graphView.setBounds(bounds);
    }
    return true;
  }

  private void updateAllLabels() {
    refreshCursorLabel();
  }

  DecimalFormat dfDB= new DecimalFormat("* ####.0");
  DecimalFormat dfFreq= new DecimalFormat("* #####.0");
  StringBuilder sCent = new StringBuilder("");
  private void refreshCursorLabel() {
    double f1 = graphView.getCursorX();
    freq2Cent(sCent, f1, " ");
    ((TextView) findViewById(R.id.textview_cur))
      .setText("Cur :" + dfFreq.format(f1)+ "Hz(" + sCent + ") " + dfDB.format(graphView.getCursorY()) + "dB");
  }

  /**
   * recompute the spectra "chart"
   * @param data    The normalized FFT output
   */
  public void recompute(double[] data) {
  	if (graphView.isBusy() == true) {
  		Log.d(TAG, "recompute(): isBusy == true");  // seems it's never busy
  	}
    graphView.replotRawSpectrum(data, 1, data.length, showLines);
    graphView.invalidate();
  }
  
  /**
   * Return a verified audio sampling rates.
   * @param requested
   */
  private static String[] validateAudioRates(String[] requested) {
    ArrayList<String> validated = new ArrayList<String>();
    for (String s : requested) {
      int rate;
      String[] sv = s.split("::");
      if (sv.length == 1) {
        rate = Integer.parseInt(sv[0]);
      } else {
        rate = Integer.parseInt(sv[1]);
      }
      if (AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT) != AudioRecord.ERROR_BAD_VALUE) {
        validated.add(s);
      }
    }
    return validated.toArray(new String[0]);
  }

  // Convert frequency to pitch
  // Fill with sFill until length is 6. If sFill=="", do not fill
  public void freq2Cent(StringBuilder a, double f, String sFill) {
    a.setLength(0);
    if (f<=0 || Double.isNaN(f) || Double.isInfinite(f)) {
      a.append("      ");
      return;
    }
    // A4 = 440Hz
    double p = 69 + 12 * Math.log(f/440.0)/Math.log(2);  // MIDI pitch
    int pi = (int) Math.round(p);
    int po = (int) Math.floor(pi/12.0);
    final String[] L = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    a.append(L[pi-po*12]);
    a.append(po-1);
    if (p-pi>0) {
      a.append('+');
    }
    a.append(Math.round(100*(p-pi)));
    while (a.length() < 6 && sFill!=null && sFill.length()>0) {
      a.append(sFill);
    }
  }
  
  /**
   * Read a snapshot of audio data at a regular interval, and compute the FFT
   * @author suhler@google.com
   *         xyy82148@gmail.com
   */
  public class Looper extends Thread {
    AudioRecord record;
    boolean isRunning = true;
    boolean isPaused1 = false;
    double dtRMS = 0;
    double dtRMSFromFT = 0;
    double maxAmpDB;
    double maxAmpFreq;
    double actualSampleRate;   // sample rate based on SystemClock.uptimeMillis()
    File filePathDebug;
    FileWriter fileDebug;
    public STFT stft;   // use with care

    public Looper() {
    }

    private void SleepWithoutInterrupt(long millis) {
      try {
        Thread.sleep(millis);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    private double baseTimeMs = SystemClock.uptimeMillis();

    private void LimitFrameRate(double updateMs) {
      // Limit the frame rate by wait `delay' ms.
      baseTimeMs += updateMs;
      long delay = (int) (baseTimeMs - SystemClock.uptimeMillis());
//      Log.i(TAG, "delay = " + delay);
      if (delay > 0) {
        try {
          Thread.sleep(delay);
        } catch (InterruptedException e) {
          Log.i(TAG, "Sleep interrupted");  // seems never reached
        }
      } else {
        baseTimeMs -= delay;  // get current time
        // Log.i(TAG, "time: cmp t="+Long.toString(SystemClock.uptimeMillis())
        //            + " v.s. t'=" + Long.toString(baseTimeMs));
      }
    }
    
    DoubleSineGen sineGen1;
    DoubleSineGen sineGen2;
    double[] mdata;
    
    // generate test data
    private int readTestData(short[] a, int offsetInShorts, int sizeInShorts) {
      sineGen1.getSamples(mdata);  // mdata.length should be even
      sineGen2.addSamples(mdata);
      for (int i = 0; i < sizeInShorts; i++) {
        a[offsetInShorts + i] = (short) Math.round(mdata[i]);
      }
//      for (int i = 0; i < sizeInShorts; i++) {
//        a[i] = (short) (32767.0 * (2.0*Math.random() - 1));
//      }
//      for (int i = 0; i < sizeInShorts; i++) {
//        a[i] = (short) (32767.0 * Math.sin(625.0 * 2 * Math.PI * i/16000.0));
//      }
      LimitFrameRate(1000.0*sizeInShorts / sampleRate);
      return sizeInShorts;
    }
    
    @Override
    public void run() {
      // Wait until previous instance of AudioRecord fully released.
      SleepWithoutInterrupt(500);
      
      int minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
                                                  AudioFormat.ENCODING_PCM_16BIT);
      if (minBytes == AudioRecord.ERROR_BAD_VALUE) {
        Log.e(TAG, "Looper::run(): Invalid AudioRecord parameter.\n");
        return;
      }

      /**
       * Develop -> Reference -> AudioRecord
       *    Data should be read from the audio hardware in chunks of sizes
       *    inferior to the total recording buffer size.
       */
      // Determine size of each read() operation
      int readChunkSize    = fftLen/2;  // /2 due to overlapped analyze window
      readChunkSize = Math.min(readChunkSize, 2048);
      int bufferSampleSize = Math.max(minBytes / BYTE_OF_SAMPLE, fftLen/2) * 2;
      // tolerate up to 1 sec.
      bufferSampleSize = (int)Math.ceil(1.0 * sampleRate / bufferSampleSize) * bufferSampleSize; 

      // Use the mic with AGC turned off. e.g. VOICE_RECOGNITION
      // The buffer size here seems not relate to the delay.
      // So choose a larger size (~1sec) so that overrun is unlikely.
      record = new AudioRecord(RECORDER_AGC_OFF, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                               AudioFormat.ENCODING_PCM_16BIT, BYTE_OF_SAMPLE * bufferSampleSize);

      Log.i(TAG, "Looper::Run(): Starting recorder... \n" +
        String.format("  sample rate     : %d Hz (request %d Hz)\n", record.getSampleRate(), sampleRate) +
        String.format("  min buffer size : %d samples, %d Bytes\n", minBytes / BYTE_OF_SAMPLE, minBytes) +
        String.format("  buffer size     : %d samples, %d Bytes\n", bufferSampleSize, BYTE_OF_SAMPLE*bufferSampleSize) +
        String.format("  read chunk size : %d samples, %d Bytes\n", readChunkSize, BYTE_OF_SAMPLE*readChunkSize) +
        String.format("  FFT length      : %d\n", fftLen));
      sampleRate = record.getSampleRate();
      actualSampleRate = sampleRate;

      if (record == null || record.getState() == AudioRecord.STATE_UNINITIALIZED) {
        Log.e(TAG, "Looper::run(): Fail to initialize AudioRecord()"); 
        return;
      }
      
      // Signal source for testing
      sineGen1 = new DoubleSineGen(625.0 , sampleRate, SAMPLE_VALUE_MAX * 0.5);
      sineGen2 = new DoubleSineGen(1875.0, sampleRate, SAMPLE_VALUE_MAX * 0.25);
      mdata = new double[readChunkSize];

      short[] audioSamples = new short[readChunkSize];
      int numOfReadShort;

      stft = new STFT(fftLen, sampleRate, wndFuncName);
      stft.setAWeighting(isAWeighting);

      // Variables for count FPS, and Debug
      long timeNow;
      long timeDebugInterval = 2000;                     // output debug information per timeDebugInterval ms 
      long time4SampleCount = SystemClock.uptimeMillis();
      int frameCount = 0;

      boolean isTestingOld = isTesting;

      // Start recording
      record.startRecording();
      long startTimeMs = SystemClock.uptimeMillis();     // time of recording start
      long nSamplesRead = 0;         // It's will overflow after millions of years of recording

      while (isRunning) {
        if (isTestingOld != isTesting) {
          isTestingOld = isTesting;
          stft.clear();
          startTimeMs = SystemClock.uptimeMillis();
          nSamplesRead = 0;
        }

        // Read data
        if (isTesting) {
          numOfReadShort = readTestData(audioSamples, 0, readChunkSize);
        } else {
          numOfReadShort = record.read(audioSamples, 0, readChunkSize);   // pulling
        }
        // Log.i(TAG, "Read: " + Integer.toString(numOfReadShort) + " samples");
        timeNow = SystemClock.uptimeMillis();
        if (nSamplesRead == 0) {      // get overrun checker synchronized
          startTimeMs = timeNow - numOfReadShort*1000/sampleRate;
        }
        nSamplesRead += numOfReadShort;
        if (isPaused1) {
          continue;  // keep reading data, so overrun checker data still valid
        }
        stft.feedData(audioSamples, numOfReadShort);

        // If there is new spectrum data, do plot
        if (stft.nElemSpectrumAmp() >= nFFTAverage) {
          // compute Root-Mean-Square
          dtRMS = stft.getRMS();

          // Update graph plot
          final double[] spectrumDB = stft.getSpectrumAmpDB();
          update(spectrumDB);
          frameCount++;

          // Count and show peak amplitude
          maxAmpDB  = 20 * Math.log10(0.5/32768);
          maxAmpFreq = 0;
          for (int i = 1; i < spectrumDB.length; i++) {  // skip the direct current term
            if (spectrumDB[i] > maxAmpDB) {
              maxAmpDB  = spectrumDB[i];
              maxAmpFreq = i;
            }
          }
          maxAmpFreq = maxAmpFreq * sampleRate / fftLen;
          dtRMSFromFT = stft.getRMSFromFT();
        }

        // Show debug information
        if (time4SampleCount + timeDebugInterval <= timeNow) {
          // Count and show FPS
          double fps = 1000 * (double) frameCount / (timeNow - time4SampleCount);
          Log.i(TAG, "FPS: " + Math.round(10*fps)/10.0 +
                " (" + frameCount + "/" + (timeNow - time4SampleCount) + "ms)");
          time4SampleCount += timeDebugInterval;
          frameCount = 0;
          // Check whether buffer overrun occur
          long nSamplesFromTime = (long)((timeNow - startTimeMs) * actualSampleRate / 1000);
          double f1 = (double) nSamplesRead / actualSampleRate;
          double f2 = (double) nSamplesFromTime / actualSampleRate;
//          Log.i(TAG, "Buffer"
//              + " should read " + nSamplesFromTime + " (" + Math.round(f2*1000)/1000.0 + "s),"
//              + " actual read " + nSamplesRead + " (" + Math.round(f1*1000)/1000.0 + "s)\n"
//              + " diff " + (nSamplesFromTime-nSamplesRead) + " (" + Math.round((f2-f1)*1000)/1e3 + "s)"
//              + " sampleRate = " + Math.round(actualSampleRate*100)/100.0);
          if (nSamplesFromTime > bufferSampleSize + nSamplesRead) {
            Log.w(TAG, "Buffer Overrun occured !\n"
                + " should read " + nSamplesFromTime + " (" + Math.round(f2*1000)/1000.0 + "s),"
                + " actual read " + nSamplesRead + " (" + Math.round(f1*1000)/1000.0 + "s)\n"
                + " diff " + (nSamplesFromTime-nSamplesRead) + " (" + Math.round((f2-f1)*1000)/1e3 + "s)"
                + " sampleRate = " + Math.round(actualSampleRate*100)/100.0
                + "\n Overrun counter reseted.");
            // XXX log somewhere to the file
            nSamplesRead = 0;  // start over
          }
          // Update actual sample rate
          if (nSamplesRead > 10*sampleRate) {
            actualSampleRate = 0.9*actualSampleRate + 0.1*(nSamplesRead * 1000.0 / (timeNow - startTimeMs));
            if (Math.abs(actualSampleRate-sampleRate) > 0.0145*sampleRate) {  // 0.0145 = 25 cent
              Log.w(TAG, "Looper::run(): Sample rate inaccurate, possible hardware problem !\n");
              nSamplesRead = 0;
            }
          }
        }
      }
      Log.i(TAG, "Looper::Run(): Stopping and releasing recorder.");
      record.stop();
      record.release();
      record = null;
    }

    private void update(final double[] data) {
      AnalyzeActivity.this.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          AnalyzeActivity.this.recompute(data);
          // RMS
          TextView tv = (TextView) findViewById(R.id.textview_RMS);
          tv.setText("RMS:dB \n" + dfDB.format(20*Math.log10(dtRMSFromFT)));
          tv.invalidate();
          // peak frequency
          freq2Cent(sCent, maxAmpFreq, " ");
          tv = (TextView) findViewById(R.id.textview_peak);
          tv.setText("Peak:" + dfFreq.format(maxAmpFreq)+ "Hz(" + sCent + ") " + dfDB.format(maxAmpDB) + "dB");
          tv.invalidate();
        }
      });
    }

    public void setPause(boolean pause) {
      this.isPaused1 = pause;
      // Note: When paused (or not), it is not allowed to change the recorder (sample rate, fftLen etc.)
    }

    public boolean getPause() {
      return this.isPaused1;
    }
    
    public void finish() {
      isRunning = false;
      interrupt();
    }
    
    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
      String state = Environment.getExternalStorageState();
      if (Environment.MEDIA_MOUNTED.equals(state)) {
        return true;
      }
      return false;
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
    public void exec(View view);
  }

  /**
   * The graph view size has been determined - update the labels accordingly.
   */
  @Override
  public void ready() {
    updateAllLabels();
  }
}