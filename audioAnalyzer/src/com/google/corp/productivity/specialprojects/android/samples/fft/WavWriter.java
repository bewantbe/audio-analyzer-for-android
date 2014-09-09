package com.google.corp.productivity.specialprojects.android.samples.fft;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.os.Environment;
import android.util.Log;

public class WavWriter {
  final String TAG = "WavWriter::";
  File outPath;
  OutputStream out;
  private byte[] header = new byte[44];

  int channels = 1;
  byte RECORDER_BPP = 16;  // bits per sample
  int byteRate;            // Average bytes per second
  int totalDataLen  = 0;   // (file size) - 8
  int totalAudioLen = 0;   // bytes of audio raw data
  int framesWrited = 0;

  public WavWriter(int sampleRate) {
    byteRate = sampleRate*RECORDER_BPP/8*channels;
    
    header[0] = 'R';  // RIFF/WAVE header
    header[1] = 'I';
    header[2] = 'F';
    header[3] = 'F';
    header[4] = (byte) (totalDataLen & 0xff);
    header[5] = (byte) ((totalDataLen >> 8) & 0xff);
    header[6] = (byte) ((totalDataLen >> 16) & 0xff);
    header[7] = (byte) ((totalDataLen >> 24) & 0xff);
    header[8] = 'W';
    header[9] = 'A';
    header[10] = 'V';
    header[11] = 'E';
    header[12] = 'f';  // 'fmt ' chunk
    header[13] = 'm';
    header[14] = 't';
    header[15] = ' ';
    header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
    header[17] = 0;
    header[18] = 0;
    header[19] = 0;
    header[20] = 1;  // format = 1, PCM/uncompressed
    header[21] = 0;
    header[22] = (byte) channels;
    header[23] = 0;
    header[24] = (byte) (sampleRate & 0xff);
    header[25] = (byte) ((sampleRate >> 8) & 0xff);
    header[26] = (byte) ((sampleRate >> 16) & 0xff);
    header[27] = (byte) ((sampleRate >> 24) & 0xff);
    header[28] = (byte) (byteRate & 0xff);              // Average bytes per second
    header[29] = (byte) ((byteRate >> 8) & 0xff);
    header[30] = (byte) ((byteRate >> 16) & 0xff);
    header[31] = (byte) ((byteRate >> 24) & 0xff);
    header[32] = (byte) (channels * RECORDER_BPP / 8);  // Block align (number of bytes per sample slice)
    header[33] = 0;
    header[34] = RECORDER_BPP;                          // bits per sample (Significant bits per sample)
    header[35] = 0;                                     // Extra format bytes
    header[36] = 'd';
    header[37] = 'a';
    header[38] = 't';
    header[39] = 'a';
    header[40] = (byte) (totalAudioLen & 0xff);
    header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
    header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
    header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
  }
  
//  public double spaceLeft() {
//    long byteLeft = outPath.getFreeSpace();  // Need API level 9
//    if (byteRate == 0 || byteLeft == 0) {
//      return 0;
//    }
//    return (double)byteLeft / (byteRate);
//  }
//  
  public boolean start() {
    if (!isExternalStorageWritable()) {
      return false;
    }
    File path = new File(Environment.getExternalStorageDirectory().getPath() + "/Recorder");
    path.mkdirs();
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH'h'mm'm'ss.SSS's'", Locale.US);
    String nowStr = df.format(new Date());
    outPath = new File(path, "rec" + nowStr + ".wav");

    try {
      out = new FileOutputStream(outPath);
      out.write(header, 0, 44);
      // http://developer.android.com/reference/android/os/Environment.html#getExternalStoragePublicDirectory%28java.lang.String%29
    } catch (IOException e) {
      Log.w(TAG, "start(): Error writing " + outPath, e);
      out = null;
    }
    return true;
  }
  
  public void stop() {
    if (out == null) {
      Log.w(TAG, "stop(): Error closing " + outPath + "  null pointer");
      return;
    }
    try {
      out.close();
    } catch (IOException e) {
      Log.w(TAG, "stop(): Error closing " + outPath, e);
    }
    out = null;
    // XXX, modify totalDataLen and totalAudioLen
    RandomAccessFile raf;
    try {
      totalAudioLen = framesWrited * RECORDER_BPP / 8 * channels;
      totalDataLen = header.length + totalAudioLen - 8;
      raf = new RandomAccessFile(outPath, "rw");
      raf.seek(4);
      raf.write((byte) ((totalDataLen >>  0) & 0xff));
      raf.write((byte) ((totalDataLen >>  8) & 0xff));
      raf.write((byte) ((totalDataLen >> 16) & 0xff));
      raf.write((byte) ((totalDataLen >> 24) & 0xff));
      raf.seek(40);
      raf.write((byte) ((totalAudioLen >>  0) & 0xff));
      raf.write((byte) ((totalAudioLen >>  8) & 0xff));
      raf.write((byte) ((totalAudioLen >> 16) & 0xff));
      raf.write((byte) ((totalAudioLen >> 24) & 0xff));
      raf.close();
    } catch (IOException e) {
      Log.w(TAG, "stop(): Error modifying " + outPath, e);
    }
  }

  byte[] byteBuffer;
  public void pushAudioShort(short[] ss, int numOfReadShort) {
    if (out == null) {
      Log.w(TAG, "pushAudioShort(): Error writing " + outPath + "  null pointer");
      return;
    }
    if (byteBuffer == null || byteBuffer.length != ss.length*2) {
      byteBuffer = new byte[ss.length*2];
    }
    for (int i = 0; i < numOfReadShort; i++) {
      byteBuffer[2*i+0] = (byte)(ss[i] & 0xff);
      byteBuffer[2*i+1] = (byte)((ss[i]>>8) & 0xff);
    }
    framesWrited += numOfReadShort;
    try {
      out.write(byteBuffer, 0, numOfReadShort*2);
      // if use out.write(byte), then a lot of GC will generated
    } catch (IOException e) {
      Log.w(TAG, "pushAudioShort(): Error writing " + outPath, e);
      out = null;
    }
  }
  
  /* Checks if external storage is available for read and write */
  public boolean isExternalStorageWritable() {
    String state = Environment.getExternalStorageState();  // Need API level 8
    if (Environment.MEDIA_MOUNTED.equals(state)) {
      return true;
    }
    return false;
  }
  
}
