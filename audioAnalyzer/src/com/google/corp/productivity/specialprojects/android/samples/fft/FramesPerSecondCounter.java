package com.google.corp.productivity.specialprojects.android.samples.fft;

import android.os.SystemClock;
import android.util.Log;

public class FramesPerSecondCounter {
  long frameCount;
  long timeOld, timeUpdateInterval;  // in ms
  double fps;
  String TAG_OUTSIDE;
  
  FramesPerSecondCounter(String TAG) {
    timeUpdateInterval = 2000;
    TAG_OUTSIDE = TAG;
    frameCount = 0;
    timeOld = SystemClock.uptimeMillis();
  }
  
  public void inc() {
    frameCount++;
    long timeNow = SystemClock.uptimeMillis();
    if (timeOld + timeUpdateInterval <= timeNow) {
      fps = 1000 * (double) frameCount / (timeNow - timeOld);
      Log.i(TAG_OUTSIDE, "FPS: " + Math.round(10*fps)/10.0 +
            " (" + frameCount + "/" + (timeNow - timeOld) + "ms)");
      timeOld = timeNow;
      frameCount = 0;
    }
  }
  
  public double getFPS() {
    return fps;
  }
}
