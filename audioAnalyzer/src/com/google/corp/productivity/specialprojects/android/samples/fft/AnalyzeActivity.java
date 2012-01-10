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

import com.google.corp.productivity.specialprojects.android.fft.RealDoubleFFT;
import com.google.corp.productivity.specialprojects.android.samples.fft.AnalyzeView.Ready;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.RectF;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Audio "FFT" analyzer.
 * @author suhler@google.com (Stephen Uhler)
 */

public class AnalyzeActivity extends Activity implements OnLongClickListener, OnClickListener,
      Ready, OnSharedPreferenceChangeListener {
  static final String TAG="audio";
  private final static float MEAN_MAX = 16384f;   // Maximum signal value
  private final static int AGC_OFF = MediaRecorder.AudioSource.VOICE_RECOGNITION;

  private int fftBins = 2048;
  private int sampleRate = 8000;
  private int updateMs = 150;
  private AnalyzeView graphView;
  private Looper samplingThread;

  private boolean showLines;
  private boolean isTesting = false;
  private boolean isPaused = false;
  private boolean isMeasure = true;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    applyPreferences(getPreferences(), null);
    getPreferences().registerOnSharedPreferenceChangeListener(this);
    graphView = (AnalyzeView) findViewById(R.id.plot);
    SelectorText st = (SelectorText) findViewById(R.id.sampling_rate);
    st.setValues(validateAudioRates(st.getValues()));
    visit((ViewGroup) graphView.getRootView(), new Visit() {
      @Override
      public void exec(View view) {
        view.setOnLongClickListener(AnalyzeActivity.this);
        view.setOnClickListener(AnalyzeActivity.this);
        ((TextView) view).setFreezesText(true);
      }
    }, "select");
  }

  /**
   * Run processClick() for views, transferring the state in the textView to our
   * internal state, then begin sampling and processing audio data
   */

  @Override
  protected void onResume() {
    super.onResume();
    visit((ViewGroup) graphView.getRootView(), new Visit() {
      @Override
      public void exec(View view) {
        processClick(view);
      }
    }, "select");
    graphView.setReady(this);

    samplingThread = new Looper();
    samplingThread.start();
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
  protected void onPause() {
    super.onPause();
    samplingThread.finish();
  }

  @Override
  protected void onDestroy() {
    getPreferences().unregisterOnSharedPreferenceChangeListener(this);
    super.onDestroy();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
   Log.i(TAG, key + "=" + prefs);
   applyPreferences(prefs, key);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (isMeasure) {
      measureEvent(event);
    } else {
      scaleEvent(event);
    }
    graphView.invalidate();
    return super.onTouchEvent(event);
  }

  public static class MyPreferences extends PreferenceActivity {
    @Override
    public void onCreate(Bundle state) {
      super.onCreate(state);
      addPreferencesFromResource(R.xml.preferences);
    }
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

  private SharedPreferences getPreferences() {
    return PreferenceManager.getDefaultSharedPreferences(this);
  }

  private void applyPreferences(SharedPreferences prefs, String pref) {
    if (pref == null || pref.equals("showLines")) {
      showLines = prefs.getBoolean("showLines", false);
    }
    if (pref == null || pref.equals("refreshRate")) {
      updateMs = 1000 / Integer.parseInt(prefs.getString(pref, "5"));
    }
    if (pref == null || pref.equals("fftBins")) {
      fftBins = Integer.parseInt(prefs.getString("fftBins", "1024"));
    }
    if (pref == null || pref.equals("sampleRate")) {
      sampleRate = Integer.parseInt(prefs.getString("sampleRate", "16000"));
    }
  }
  
  // XXX this'll be factored into its own class when I get it working better

  private void measureEvent(MotionEvent event) {
    switch (event.getPointerCount()) {
      case 1:
        if (graphView.setCursor(event.getX(), event.getY())) {
          updateAllLabels();
        }
        break;
      case 2:
        // not implemented
    }
  }

  /**
   *  Manage horizontal scroll and zoom (XXX should be its own class)
   */

  final private static float INIT = Float.MIN_VALUE;
  private boolean isPinching = false;
  private float start = INIT;
  private float initScale;

  private void scaleEvent(MotionEvent event) {
    if (event.getAction() != MotionEvent.ACTION_MOVE) {
      start = INIT;
      isPinching = false;
      // Log.i(TAG, "Skip event " + event.getAction());
      return;
    }
    switch (event.getPointerCount()) {
      case 2 :
        float delta = Math.abs(event.getX(0) - event.getX(1));
        // Log.i(TAG, "delta=" + delta);
        if (isPinching)  {
          float scale = initScale * delta / start;
          graphView.setScale(Math.min(10f, scale));
          updateAllLabels();
        } else {
          // Log.i(TAG, "Start scale: " + start);
          start = delta;
          initScale = graphView.getScale();
        }
        isPinching = true;
        break;
      case 1:
        float x = event.getX(0);
        if (isPinching || start == INIT) {
          start = x + graphView.getXlate();
          // Log.i(TAG, "Setting xlate point: " + start);
        } else {
          graphView.setXlate(start - x);
          updateAllLabels();
        }
        isPinching = false;
        break;
      default:
        Log.i(TAG, "Invalid touch count");
        break;
    }
  }
  
  /**
   * TODO: add button-specific help on longclick
   */

  @Override
  public boolean onLongClick(View view) {
    vibrate(300);
    Log.i(TAG, "long click: " + view.toString());
    return true;
  }

  @Override
  public void onClick(View v) {
    vibrate(50);
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
      // Log.i(TAG, "Click: test=" + isTesting);
      return false;
    }
    if (v.getId() == R.id.run) {
      boolean pause = value.equals("stop");
      if (isPaused != pause) {
        isPaused = pause;
        if (samplingThread != null) {
          samplingThread.setPause(isPaused);
        }
      }
      return false;
    }
    if (v.getId() == R.id.mode) {
      isMeasure = !value.equals("scale");
        // Log.i(TAG, "Click: measure=" + isMeasure);
      return false;
    }
    if (v.getId() == R.id.bins) {
      fftBins = Integer.parseInt(value);
    } else if (v.getId() == R.id.sampling_rate) {
      sampleRate = Integer.parseInt(value);
      RectF bounds = graphView.getBounds();
      bounds.right = sampleRate / 2;
      graphView.setBounds(bounds);
    } else if (v.getId() == R.id.db) {
      RectF bounds = graphView.getBounds();
      bounds.bottom = Integer.parseInt(value);
      graphView.setBounds(bounds);
    }
    return true;
  }

  private void updateAllLabels() {
    refreshCursorLabel();
    refreshMaxFreqLabel();
    refreshMinFreqLabel();
  }

  private void refreshCursorLabel() {
    double f = graphView.getX();
    ((TextView) findViewById(R.id.freq_db)).setText(
        Math.round(f) + "hz\n" + Math.round(graphView.getY()) + "db");
  }

  private void refreshMinFreqLabel() {
      ((TextView) findViewById(R.id.min)).setText(Math.round(graphView.getMin()) + "hz");
  }
  private void refreshMaxFreqLabel() {
      ((TextView) findViewById(R.id.max)).setText(Math.round(graphView.getMax()) + "hz");
  }

  /**
   * recompute the spectral "chart"
   * @param data    The normalized fft output
   */

  public void recompute(double[] data) {
    graphView.recompute(data, 1, data.length / 2, showLines);
    graphView.invalidate();
  }

  /**
   * Convert our samples to double for fft.
   */
  private static double[] shortToDouble(short[] s, double[] d) {
    for (int i = 0; i < d.length; i++) {
      d[i] = s[i];
    }
    return d;
  }

  /**
   * Compute db of bin, where "max" is the reference db
   * @param r Real part
   * @param i complex part
   */
  private static double db2(double r, double i, double maxSquared) {
    return 5.0 * Math.log10((r * r + i * i) / maxSquared);
  }

  /**
   * Convert the fft output to DB
   */

  static double[] convertToDb(double[] data, double maxSquared) {
    data[0] = db2(data[0], 0.0, maxSquared);
    int j = 1;
    for (int i=1; i < data.length - 1; i+=2, j++) {
      data[j] = db2(data[i], data[i+1], maxSquared);
    }
    data[j] = data[0];
    return data;
  }

  /**
   * Verify the supplied audio rates are valid!
   * @param requested
   */
  private static String[] validateAudioRates(String[] requested) {
    ArrayList<String> validated = new ArrayList<String>();
    for (String s : requested) {
      int rate = Integer.parseInt(s);
      if (AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT) != AudioRecord.ERROR_BAD_VALUE) {
        validated.add(s);
      }
    }
    return validated.toArray(new String[0]);
  }
  /**
   * Read a snapshot of audio data at a regular interval, and compute the FFT
   * @author suhler@google.com 
   */

  public class Looper extends Thread {
    AudioRecord record;
    int minBytes;
    long baseTimeMs;
    boolean isRunning = true;
    boolean isPaused1 = false;
    // Choose 2 arbitrary test frequencies to verify FFT operation
    DoubleSineGen sineGen1 = new DoubleSineGen(1234.0, sampleRate, MEAN_MAX);
    DoubleSineGen sineGen2 = new DoubleSineGen(3300.0, sampleRate, MEAN_MAX / 4.0);
    double[] tmp = new double[fftBins];
    
    // Timers
    private int loops = 0;

    public Looper() {
      minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT);
      minBytes = Math.max(minBytes, fftBins);
      // VOICE_RECOGNITION: use the mic with AGC turned off!
      record =  new AudioRecord(AGC_OFF, sampleRate,
          AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,  minBytes);
      Log.d(TAG, "Buffer size: " + minBytes + " (" + record.getSampleRate() + "=" + sampleRate + ")");
    }

    @Override
    public void run() {
      final double[] fftData = new double[fftBins];
      RealDoubleFFT fft = new RealDoubleFFT(fftBins);
      double scale = MEAN_MAX * MEAN_MAX * fftBins * fftBins / 2d;
      short[] audioSamples = new short[minBytes];
      record.startRecording();

      baseTimeMs = SystemClock.uptimeMillis();
      while(isRunning) {
        loops++;
        baseTimeMs += updateMs;
        int delay = (int) (baseTimeMs - SystemClock.uptimeMillis());
        if (delay < 20) {
          Log.i(TAG, "wait: " + delay);
        }
        try {
          Thread.sleep(delay < 10 ? 10 : delay);
        } catch (InterruptedException e) {
          // Log.i(TAG, "Delay interrupted");
          continue;
        }

        if (isTesting) {
          sineGen1.getSamples(fftData);
          sineGen2.addSamples(fftData);
        } else {
          record.read(audioSamples, 0, minBytes);
          shortToDouble(audioSamples, fftData);
        }
        if (isPaused1) {
          continue;
        }
        fft.ft(fftData);
        convertToDb(fftData, scale);
        update(fftData);
      }
      Log.i(TAG, "Releasing Audio");
      record.stop();
      record.release();
      record = null;
    }

    private void update(final double[] data) {
      AnalyzeActivity.this.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          AnalyzeActivity.this.recompute(data);
        }
      });
    }

    public void setPause(boolean pause) {
      this.isPaused1 = pause;
    }

    public void finish() {
      isRunning=false;
      interrupt();
    }
  }

  private void vibrate(int ms) {
    ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(ms);
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
   * Interface for view heirarchy visitor
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