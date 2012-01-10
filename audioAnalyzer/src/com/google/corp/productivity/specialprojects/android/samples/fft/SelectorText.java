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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.RotateAnimation;
import android.widget.TextView;

/**
 * Text view that toggles through a set of values.
 * @author suhler@google.com (Stephen Uhler)
 */

public class SelectorText extends TextView {
  private static final int ANIMATION_DELAY = 100;
  private String[] values;
  private Paint paint, bg;
  private RectF rect = new RectF();
  private RectF bgRect = new RectF();
  private float r = 3;
  
  public SelectorText(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setup(context, attrs);
  }
  public SelectorText(Context context, AttributeSet attrs) {
    super(context, attrs);
    setup(context, attrs);
  }
  public SelectorText(Context context) {
    super(context);
    setup(context, null);
  }
  
  @Override
  public boolean performClick() {
    Animation an = createAnimation(true, ANIMATION_DELAY);
    an.setAnimationListener(new AnimationListener() {
      @Override
      public void onAnimationEnd(Animation animation) {
        nextValue();
        SelectorText.super.performClick();
        createAnimation(false, ANIMATION_DELAY).start();
      }
      @Override public void onAnimationRepeat(Animation animation) {}
      @Override public void onAnimationStart(Animation animation) {}
    });
    an.start();
    return true;
  }
  
  /**
   * Choose an arbitrary animation for the text view.
   * @param start   If true, animate the old value "out", otherwise animate the old value in
   * @param millis  Animation time for this step, ms
   */
  
  private Animation createAnimation(boolean start, int millis) {
    RotateAnimation ra = new RotateAnimation(start?0f:180f, start?180f:360f, getWidth()/2, getHeight()/2);
    ra.setDuration(millis);
    setAnimation(ra);
    return ra;
  }
  
  /**
   * Compute the value of our "select" indicator.
   */
  
  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    rect.set(2f, h/2 - 6f, 12f, h/2 + 6f);
    bgRect.set(1f, 1f, w - 2f, h - 2f);
  }
  
  /**
   * Draw the selector, then the selected text.
   */
  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.drawRoundRect(rect, r, r, paint);
    c.drawRoundRect(bgRect, r, r, bg);
  }
  
  /**
   * Adjust the padding to make room for the select indicator.
   */
  
  @Override
  public
  void setPadding(int left, int top, int right, int bottom) {
    super.setPadding(left + 14, top, right, bottom);
  }
  
  /**
   * Initialize our selector.  We could make most of the features customizable via XML.
   */
  
  private void setup(Context context, AttributeSet attrs) {
    bg = new Paint();
    bg.setStrokeWidth(2);
    bg.setColor(Color.GRAY);
    bg.setStyle(Paint.Style.STROKE);
    paint = new Paint(bg);
    paint.setColor(Color.GREEN);
    
    setClickable(true);
    if (attrs != null) {
      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SelectorText);
      String items = a.getString(R.styleable.SelectorText_items);
      String delim = getValue(a, R.styleable.SelectorText_itemDelim, " ");
      if (items != null) {
        Log.i(AnalyzeActivity.TAG, "items: " + items);
        setValues(items.split(delim));
      }
      a.recycle();
    }
  }
  
  private static String getValue(TypedArray a, int index, String dflt) {
    String result = a.getString(index);
    return result == null ? dflt : result;
  }
  
  public void setValues(String[] values) {
    this.values = values;
    adjustWidth();
    invalidate();
  }
  
  public String[] getValues() {
    return values;
  }
  
  public String nextValue() {
    if (values != null) {
      int i;
      for(i = 0; i < values.length; i++) {
        if (getText().equals(values[i])) {
          setText(values[(i+1)%values.length]);
          return getText().toString();
        }
      }
      setText(values[0]);
    }
    return getText().toString();
  }
  
  private void adjustWidth() {
    Paint p = getPaint();
    int adj = getPaddingLeft() + getPaddingRight();
    int width = 0;
    for (String s : values) {
      width = Math.max(width, Math.round(p.measureText(s)));
    }
    setMinWidth(width + adj + 4);
  }
}