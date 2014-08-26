package com.google.corp.productivity.specialprojects.android.samples.fft;

import java.util.LinkedList;
import java.util.Queue;

import android.util.Log;

import com.google.corp.productivity.specialprojects.android.fft.RealDoubleFFT;

// Short Time Fourier Transform
public class STFT {
  // data for frequency Analysis
  private double[] spectrumAmpOut;
  private double[] spectrumAmpOutTmp;
  private double[] spectrumAmpIn;
  private double[] spectrumAmpInTmp;
  private double[] wnd;
  private int spectrumAmpPt;
  private RealDoubleFFT spectrumAmpFFT;
  public Queue<double[]> spectrumAmpOutQueue = new LinkedList<double[]>();
  private double[][] spectrumAmpOutArray = new double[2][];                // 2 since half overlap
  private int spectrumAmpOutArrayPt = 0;                                   // Pointer for spectrumAmpOutArray

  // data for spectrogram
  public Queue<double[]> spectrogram;

  public STFT(int i_fftlen) {
    if (((-i_fftlen)&i_fftlen) != i_fftlen) {
      // error: i_fftlen should be power of 2
      throw new IllegalArgumentException("Currently, only power of 2 are supported in fftlen");
    }
    spectrumAmpOut   = new double[i_fftlen/2+1];
    spectrumAmpIn    = new double[i_fftlen];
    spectrumAmpInTmp = new double[i_fftlen];
    wnd              = new double[i_fftlen];
    spectrumAmpFFT   = new RealDoubleFFT(spectrumAmpIn.length);
    
    double normalizeFactor = 0;
    for (int i=0; i<wnd.length; i++) {
      //wnd[i] = 1;
      // Hanning, hw=1
      //wnd[i] = 0.5*(1-Math.cos(2*Math.PI*i/(wnd.length-1.))) *2;  // *2 to preserve the peak
      // Blackman, hw=2
      //wnd[i] = 0.42-0.5*Math.cos(2*Math.PI*i/(wnd.length-1))+0.08*Math.cos(4*Math.PI*i/(wnd.length-1));
      // Blackman_Harris, hw=3
      wnd[i] = (0.35875-0.48829*Math.cos(2*Math.PI*i/(wnd.length-1))+0.14128*Math.cos(4*Math.PI*i/(wnd.length-1))-0.01168*Math.cos(6*Math.PI*i/(wnd.length-1))) *2;
      normalizeFactor += wnd[i];
    }
    normalizeFactor = wnd.length / normalizeFactor;
    for (int i=0; i<wnd.length; i++) {
      wnd[i] *= normalizeFactor;
    }
  }

  public void feedData(short[] ds) {
    feedData(ds, ds.length);
  }
  public void feedData(short[] ds, int dsLen) {
    if (dsLen > ds.length) {
      Log.e("STFT", "dsLen > ds.length !");
      dsLen = ds.length;
    }
    int dsPt = 0;           // input data point to be read
    while (dsPt < dsLen) {
      while (spectrumAmpPt < spectrumAmpIn.length && dsPt < dsLen) {
        spectrumAmpIn[spectrumAmpPt] = ds[dsPt] / 32768.0;
        spectrumAmpPt++;
        dsPt++;
      }
      if (spectrumAmpPt == spectrumAmpIn.length) {    // enough data for one FFT
        for (int i = 0; i < wnd.length; i++) {
          spectrumAmpInTmp[i] = spectrumAmpIn[i] * wnd[i];
        }
        spectrumAmpFFT.ft(spectrumAmpInTmp);
        spectrumAmpOutTmp = new double[spectrumAmpOut.length];
        fftToAmp(spectrumAmpOutTmp, spectrumAmpInTmp);
        spectrumAmpOutQueue.add(spectrumAmpOutTmp);
//        spectrumAmpPt = 0;                          // no overlap
        // half overlap
        int n2 = spectrumAmpIn.length / 2;
        for (int i=0; i<n2; i++) {
          spectrumAmpIn[i] = spectrumAmpIn[i + n2];
        }
        spectrumAmpPt = n2;
      }
    }
  }

  private void fftToAmp(double[] dataOut, double[] data) {
    // Should preallocate dataOut properly
    // data.length should be even number
    double scaler = 2.0*2.0 / (data.length * data.length);  // *2 since there is negative frequency part
    dataOut[0] = data[0]*data[0] * scaler / 4.0;
    int j = 1;
    for (int i = 1; i < data.length - 1; i += 2, j++) {
      dataOut[j] = (data[i]*data[i] + data[i+1]*data[i+1]) * scaler;
    }
    dataOut[j] = data[data.length-1]*data[data.length-1] * scaler / 4.0;
  }

//  private void fftToAmpDB(double[] dataOut, double[] data) {
//    fftToAmp(dataOut, data);
//    for (int i = 0; i < dataOut.length; i++) {
//      dataOut[i] = 10.0 * Math.log10(dataOut[i]);
//    }
//  }

  // return recently calculated spectrum, and clean spectrum queue 
  public double[] pollSpectrumAmp() {
    int sz = spectrumAmpOutQueue.size();
    if (sz == 0) {
      return spectrumAmpOut;
    }
    for (int j = 0; j < spectrumAmpOut.length; j++) {
      spectrumAmpOut[j] = 0;
    }
    while (spectrumAmpOutQueue.size() > 0) {
      spectrumAmpOutTmp = spectrumAmpOutQueue.poll();
      for (int j = 0; j < spectrumAmpOut.length; j++) {
        spectrumAmpOut[j] += spectrumAmpOutTmp[j];
      }
//      Log.i(AnalyzeActivity.TAG, "pollSpectrumAmp(): while loop, st[1] = " + Double.toString(spectrumAmpOutTmp[1]));
    }
//    spectrumAmpOutQueue.clear();
    for (int j = 0; j < spectrumAmpOut.length; j++) {
      spectrumAmpOut[j] /= sz;
    }
//    Log.i(AnalyzeActivity.TAG, "pollSpectrumAmp(): sz = " + Integer.toString(sz) + "  s[1]=" + Double.toString(spectrumAmpOut[1]));
    return spectrumAmpOut;
  }
  
  public double[] getSpectrumAmp() {
    return pollSpectrumAmp();
  }

  public double[] getSpectrumAmpDB() {
    spectrumAmpOutTmp = getSpectrumAmp();
    for (int i = 0; i < spectrumAmpOutTmp.length; i++) {
      spectrumAmpOutTmp[i] = 10.0 * Math.log10(spectrumAmpOutTmp[i]);
    }
//  Log.i(AnalyzeActivity.TAG, "pollSpectrumAmp():  s[1]=" + Double.toString(spectrumAmpOutTmp[1]));
    return spectrumAmpOutTmp;
  }

  public int nElemSpectrumAmp() {
    return spectrumAmpOutQueue.size();
  }
  
  public void clear() {
    spectrumAmpOutQueue.clear();
    spectrumAmpPt = 0;
    for (int i=0; i<spectrumAmpOut.length; i++) {
      spectrumAmpOut[i] = 0;
    }
  }

}
