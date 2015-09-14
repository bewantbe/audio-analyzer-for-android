package github.bewantbe.audio_analyzer_for_android;

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
  
  // call this when number of frames plus one
  public void inc() {
    frameCount++;
    long timeNow = SystemClock.uptimeMillis();
    if (timeOld + timeUpdateInterval <= timeNow) {
      fps = 1000 * (double) frameCount / (timeNow - timeOld);
      Log.i(TAG_OUTSIDE, "FPS: " + Math.round(100*fps)/100.0 +
            " (" + frameCount + "/" + (timeNow - timeOld) + "ms)");
      timeOld = timeNow;
      frameCount = 0;
    }
  }
  
  public double getFPS() {
    return fps;
  }
}
