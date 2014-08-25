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

import android.annotation.SuppressLint;
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
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Audio "FFT" analyzer.
 * @author suhler@google.com (Stephen Uhler)
 */

public class AnalyzeActivity extends Activity implements OnLongClickListener, OnClickListener,
      Ready, OnSharedPreferenceChangeListener {
  static final String TAG="audio";
  private final static double SAMPLE_VALUE_MAX = 32767.0;   // Maximum signal value
  private final static int RECORDER_AGC_OFF = MediaRecorder.AudioSource.VOICE_RECOGNITION;
  private final static int BYTE_OF_SAMPLE = 2;

  private int fftLen = 2048;
  private int sampleRate = 8000;
  private int updateMs = 100;
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
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
    @SuppressWarnings("deprecation")
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

  private SharedPreferences getPreferences() {
    return PreferenceManager.getDefaultSharedPreferences(this);
  }

  private void applyPreferences(SharedPreferences prefs, String pref) {
    if (pref == null || pref.equals("showLines")) {
      showLines = prefs.getBoolean("showLines", false);
    }
    if (pref == null || pref.equals("refreshRate")) {
      updateMs = 1000 / Integer.parseInt(prefs.getString(pref, "12"));
    }
    if (pref == null || pref.equals("fftBins")) {
      fftLen = Integer.parseInt(prefs.getString("fftBins", "1024"));
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
  float x0;
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
          start = graphView.getXlate();
          x0 = x;
          // Log.i(TAG, "Setting xlate point: " + start);
        } else {
          graphView.setXlate(start + (x0 - x) / graphView.getScale());
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
      fftLen = Integer.parseInt(value);
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

  @SuppressLint("NewApi")
  private void refreshCursorLabel() {
    double f = graphView.getX();
    ((TextView) findViewById(R.id.freq_db)).setText(
        Math.round(f) + "Hz\n" + Math.round(graphView.getY()) + "dB");
  }

  private void refreshMinFreqLabel() {
      ((TextView) findViewById(R.id.min)).setText(Math.round(graphView.getMin()) + "Hz");
  }
  private void refreshMaxFreqLabel() {
      ((TextView) findViewById(R.id.max)).setText(Math.round(graphView.getMax()) + "Hz");
  }

  /**
   * recompute the spectra "chart"
   * @param data    The normalized FFT output
   */

  public void recompute(double[] data) {
    //graphView.recompute(data, 1, data.length / 2, showLines);
  	if (graphView.isBusy() == true) {
  		Log.d(TAG, "isBusy");  // seems it's never busy
  	}
    graphView.recompute(data, 1, data.length, showLines);
    graphView.invalidate();
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
   *         xyy82148@gmail.com
   */
  public class Looper extends Thread {
    AudioRecord record;
    boolean isRunning = true;
    boolean isPaused1 = false;
    double dtRMS = 0;

    private void SleepWithoutInterrupt(long millis) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    public Looper() {
    }

    @Override
    public void run() {
      // Initialize
      // TODO: if failed, use another fallback option
      int minBytes = AudioRecord.getMinBufferSize(sampleRate,
          AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
      // Determine size of each read() operation
      int bufSizeInShorts = Math.max(minBytes / BYTE_OF_SAMPLE, fftLen);
      // Wait until previous instance of AudioRecord fully released.
      SleepWithoutInterrupt(500);
      // Signal source for testing FFT
      DoubleSineGen sineGen1 = new DoubleSineGen(1234.0, sampleRate, SAMPLE_VALUE_MAX * 0.5);
      DoubleSineGen sineGen2 = new DoubleSineGen(3300.0, sampleRate, SAMPLE_VALUE_MAX * 0.25);

      // Use the mic with AGC turned off. e.g. VOICE_RECOGNITION
      record = new AudioRecord(RECORDER_AGC_OFF, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                               AudioFormat.ENCODING_PCM_16BIT, BYTE_OF_SAMPLE * bufSizeInShorts);
      Log.d(TAG, "Buffer size: " + minBytes + "bytes (" + record.getSampleRate()
                 + "=" + sampleRate + ")");

      Log.i(TAG, "Recorder Info:\n"
                 + "Sample rate: " + Integer.toString(record.getSampleRate()) + "Hz, "
                 + "buf: " + Integer.toString(2*bufSizeInShorts) + "bytes, "
                 + "FFT length: " + Integer.toString(fftLen));

      // Start recording
      record.startRecording();
      SleepWithoutInterrupt(100);

      short[] audioSamples = new short[bufSizeInShorts];
      STFT stft = new STFT(fftLen);

      long baseTimeMs  = SystemClock.uptimeMillis();       // time that the plot get updated
      long startTimeMs = SystemClock.uptimeMillis();       // time of recording start
      
      // Variables for count FPS
      long timeInterval = 2000;                            // output FPS per timeInterval ms 
      long time4FrameCount = SystemClock.uptimeMillis();
      int frameCount = 0;
      
      long nFramesRead = 0;         // It's will overflow after millions of years of recording
      int numOfReadShort = bufSizeInShorts;
      double[] mdata = new double[bufSizeInShorts];

      boolean isTestingOld = isTesting;

      while (isRunning) {
        // long aha = SystemClock.uptimeMillis();
        if (isTestingOld != isTesting) {
          isTestingOld = isTesting;
          stft.clear();
          startTimeMs = SystemClock.uptimeMillis();
          nFramesRead = 0;
        }

        // Read data
        if (isTesting) {
          sineGen1.getSamples(mdata);  // mdata.length should be even
          sineGen2.addSamples(mdata);
          for (int i = 0; i < bufSizeInShorts; i++) {
            // //audioSamples[i] = (short) (16384.0 * (Math.random() - 0.5));
            audioSamples[i] = (short) Math.round(mdata[i]);
          }
          numOfReadShort = bufSizeInShorts;
        } else {
          numOfReadShort = record.read(audioSamples, 0, bufSizeInShorts);
          // Log.i(TAG, "Read: " + Integer.toString(numOfReadShort) + " samples");
        }
        nFramesRead += numOfReadShort;
        if (isPaused1) {
          continue;  // keep reading data, so that buffer not get overflowed?
        }
        stft.feedData(audioSamples, numOfReadShort);
        // Log.i(TAG, "time " + Long.toString(SystemClock.uptimeMillis()-aha)
        // + "ms  shorts read:" + Integer.toString(numOfReadShort));

        // Plot if there is enough data
        if (stft.nElemSpectrumAmp() > 0) {
          dtRMS = 0;
          for (int i = 0; i < numOfReadShort; i++) {
            double s = audioSamples[i] / 32768.0;
            dtRMS += s * s;                           // assume mean value is zero
          }
          dtRMS = Math.sqrt(dtRMS / numOfReadShort);  // compute Root-Mean-Square

          // Limit the frame rate by wait `delay' ms.
          // May cause buffer overrun, so choose a small updateMs.
          baseTimeMs += updateMs;
          int delay = (int) (baseTimeMs - SystemClock.uptimeMillis());
          // Log.i(TAG, "delay = " + delay);
          if (delay > 0) {
            try {
              Thread.sleep(delay);
            } catch (InterruptedException e) {
              Log.i(TAG, "Sleep interrupted");
              break;
            }
          } else {
            baseTimeMs -= delay;  // get current time. Time to go.
            // Log.i(TAG, "time: cmp t="+Long.toString(SystemClock.uptimeMillis())
            //            + " v.s. t'=" + Long.toString(baseTimeMs));
          }
          frameCount++;
          // update graph
          update(stft.getSpectrumAmp());
        }

        long timeNow = SystemClock.uptimeMillis();
        if (time4FrameCount + timeInterval <= timeNow) {
          // Show FPS
          Log.i(TAG, "FPS: " + Double.toString(1000 * (double) frameCount / (timeNow - time4FrameCount))
                     + "(" + Integer.toString(frameCount) + "/"
                     + Long.toString(timeNow - time4FrameCount) + "ms)");
          time4FrameCount += timeInterval;
          frameCount = 0;
          // Check whether buffer overrun occur
          long nFramesFromTime = (timeNow - startTimeMs) * record.getSampleRate() / 1000;
          if (nFramesFromTime > 2 * bufSizeInShorts + nFramesRead) {
            Log.w(TAG, "Buffer Overrun occured !\n"
                       + " (Read " + Long.toString(nFramesRead) + " frames ("
                       + Double.toString((double) nFramesRead / record.getSampleRate()) + "sec)\n"
                       + "  Should read " + Long.toString(nFramesFromTime) + " frames ("
                       + Double.toString((double) nFramesFromTime / record.getSampleRate()) + "sec))");
          }
          // Show peak amplitude
          double[] am = stft.getSpectrumAmp();
          double max_amp = -9e9;
          for (double d : am) {
            if (d > max_amp) {
              max_amp = d;
            }
          }
          Log.i(TAG, "max spectrum amplitude: " + Double.toString(max_amp) + " dB");
        }
      }
      Log.i(TAG, "Releasing Audio. Looper().Run()");
      record.stop();
      record.release();
      record = null;
    }

    private void update(final double[] data) {
      AnalyzeActivity.this.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          AnalyzeActivity.this.recompute(data);
          TextView tv = (TextView) findViewById(R.id.textview_subhead);
          tv.setText(String.format("RMS: %6.2fdB", 20 * Math.log10(dtRMS)));
          tv.invalidate();
        }
      });
    }

    public void setPause(boolean pause) {
      this.isPaused1 = pause;
      // Note: When paused (or not), it is not allowed to change the recorder (sample rate, fftLen etc.)
      // Recreate the whole thread would be a safe way to go.
    }

    public void finish() {
      isRunning = false;
      interrupt();
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