package com.google.corp.productivity.specialprojects.android.samples.fft;

public class SBNumFormat {
  static final char charDigits[] = {'0','1','2','3','4','5','6','7','8','9'};

  // Invent wheel... so we can eliminate GC
  static public void fillInNumFixedWidthPositive(StringBuilder sb, double d, int nInt, int nFrac, char padChar) {
    if (d<0) {
      for (int i = 3; i < nInt+nFrac+(nFrac>0?1:0); i++) {
        sb.append("don't");
      }
    }
    if (d >= Math.pow(10,nInt)) {
      sb.append("OFL");
      for (int i = 3; i < nInt+nFrac+(nFrac>0?1:0); i++) {
        sb.append(' ');
      }
      return;
    }
    if (Double.isNaN(d)) {
      sb.append("NaN");
      for (int i = 3; i < nInt+nFrac+(nFrac>0?1:0); i++) {
        sb.append(' ');
      }
      return;
    }
    while (nInt > 0) {
      nInt--;
      if (d < Math.pow(10,nInt) && nInt>0) {
        sb.append(padChar);
      } else {
        sb.append(charDigits[(int)(d / Math.pow(10,nInt) % 10.0)]);
      }
    }
    if (nFrac > 0) {
      sb.append('.');
      for (int i = 1; i <= nFrac; i++) {
        sb.append(charDigits[(int)(d * Math.pow(10,i) % 10.0)]);
      }
    }
  }
  
  static public void fillInNumFixedWidthPositive(StringBuilder sb, double d, int nInt, int nFrac) {
    fillInNumFixedWidthPositive(sb, d, nInt, nFrac, ' ');
  }
  
  static public void fillInNumFixedWidth(StringBuilder sb, double d, int nInt, int nFrac) {
    int it = sb.length();
    sb.append(' ');
    if (d < 0) {
      fillInNumFixedWidthPositive(sb, -d, nInt, nFrac);
      for (int i = it; i < sb.length(); i++) {
        if (sb.charAt(i+1) != ' ') {
          sb.setCharAt(i, '-');
          return;
        }
      }
    }
    fillInNumFixedWidthPositive(sb, d, nInt, nFrac);
  }

  static public void fillInNumFixedWidthSigned(StringBuilder sb, double d, int nInt, int nFrac) {
    int it = sb.length();
    sb.append(' ');
    fillInNumFixedWidthPositive(sb, Math.abs(d), nInt, nFrac);
    for (int i = it; i < sb.length(); i++) {
      if (sb.charAt(i+1) != ' ') {
        if (d < 0) {
          sb.setCharAt(i, '-');
        } else {
          sb.setCharAt(i, '+');
        }
        return;
      }
    }
  }

  static public void fillInNumFixedWidthSignedFirst(StringBuilder sb, double d, int nInt, int nFrac) {
    if (d < 0) {
      sb.append('-');
    } else {
      sb.append('+');
    }
    fillInNumFixedWidthPositive(sb, Math.abs(d), nInt, nFrac);
  }
  
  static public void fillInInt(StringBuilder sb, int in) {
    if (in == 0) {
      sb.append('0');
      return;
    }
    if (in<0) {
      sb.append('-');
      in = -in;
    }
    int it = sb.length();
    while (in > 0) {
      sb.insert(it, in%10);
      in /= 10;
    }
  }
  
  static public void fillTime(StringBuilder sb, double t, int nFrac) {
    // in format x0:00:00.x
    if (t<0) {
      t = -t;
      sb.append('-');
    }
    double u;
    // hours
    u = Math.floor(t/3600.0);
    fillInInt(sb, (int)u);
    sb.append(':');
    // minutes
    t -= u * 3600;
    u = Math.floor(t/60.0);
    fillInNumFixedWidthPositive(sb, u, 2, 0, '0');
    sb.append(':');
    // seconds
    t -= u * 60;
    fillInNumFixedWidthPositive(sb, t, 2, nFrac, '0');
  }
}
