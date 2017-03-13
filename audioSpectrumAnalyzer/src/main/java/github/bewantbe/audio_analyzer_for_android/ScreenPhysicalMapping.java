/* Copyright 2017 Eddy Xiao <bewantbe@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package github.bewantbe.audio_analyzer_for_android;

import android.util.Log;

import static java.lang.Math.exp;
import static java.lang.Math.log;

/**
 * Mapping between physical value and screen pixel position
 * Use double or float ?
 */

class ScreenPhysicalMapping {
    private final static String TAG = "ScreenPhysicalMapping";

    enum Type {  // java's enum type is inconvenient
        LINEAR(0), LINEAR_ON(1), LOG(2);

        private final int value;
        Type(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    Type mapType;                     // Linear or Log
    double nCanvasPixel;
    double vLowerBound, vUpperBound;   // Physical limits
    private double zoom = 1, shift = 0;        // zoom==1 means no zooming, shift=0 means no shift

    ScreenPhysicalMapping(double _nCanvasPixel, double _vLowerBound, double _vHigherBound, ScreenPhysicalMapping.Type _mapType) {
        nCanvasPixel = _nCanvasPixel;
        vLowerBound  = _vLowerBound;
        vUpperBound = _vHigherBound;
        mapType      = _mapType;
    }

    ScreenPhysicalMapping(ScreenPhysicalMapping _axis) {
        mapType      = _axis.mapType;
        nCanvasPixel = _axis.nCanvasPixel;
        vLowerBound  = _axis.vLowerBound;
        vUpperBound  = _axis.vUpperBound;
        zoom         = _axis.zoom;
        shift        = _axis.shift;
    }

    void setNCanvasPixel(double _nCanvasPixel) {
        nCanvasPixel = _nCanvasPixel;
    }

    void setBounds(double _vLowerBound, double _vHigherBound) {
        vLowerBound  = _vLowerBound;
        vUpperBound = _vHigherBound;
    }

    void setZoomShift(double _zoom, double _shift) {
        zoom = _zoom;
        shift = _shift;
    }

    double getZoom() { return zoom; }
    double getShift() { return shift; }

    // set zoom and shift from physical value bounds
    void setZoomShiftFromV(double vLower, double vHigher) {
        if (vLower == vHigher) {
            return;  // Or throw an exception?
        }
        double nCanvasPixelSave = nCanvasPixel;
        nCanvasPixel = 1;                       // This function do not depends on nCanvasPixel
        double p1 = pixelNoZoomFromV(vLower);
        double p2 = pixelNoZoomFromV(vHigher);
        zoom = nCanvasPixel / (p2 - p1);
        shift = p1 / nCanvasPixel;
        nCanvasPixel = nCanvasPixelSave;
    }

    //    | lower bound  ...  higher bound |  physical unit
    //    | 0            ...             1 |  "unit 1" (Mapping can be linear or logarithmic)

    // In LINEAR mode (default):
    //    |lower value  ...    higher value|  physical unit
    //    | shift       ... shift + 1/zoom |  "unit 1", 0=vLowerBound, 1=vUpperBound
    //    | 0 | 1 |     ...          | n-1 |  pixel

    // In LINEAR_ON mode (not implemented):
    //      |lower value ...    higher value|     physical unit
    //      | shift      ... shift + 1/zoom |     "unit 1" window
    //    | 0 | 1 |      ...             | n-1 |  pixel

    // this class do not verify if the input data are legal
    double pixelFromV(double v, double zoom, double shift) {
        // old: canvasX4axis
        // return (v - vLowerBound) / (vUpperBound - vLowerBound) * nCanvasPixel;
        // old: canvasViewX4axis
        if (vUpperBound == vLowerBound || vUpperBound != vUpperBound || vLowerBound != vLowerBound) {
            return 0;
        }
        if (mapType == Type.LINEAR) {
            return ((v - vLowerBound) / (vUpperBound - vLowerBound) - shift) * zoom * nCanvasPixel;
        } else {
            return pixelFromVLog(v, zoom, shift);
        }
    }

    double vFromPixel(double pixel, double zoom, double shift) {
        if (nCanvasPixel == 0 || zoom == 0) {
            return 0;
        }
        if (mapType == Type.LINEAR) {
            return (pixel / nCanvasPixel / zoom + shift) * (vUpperBound - vLowerBound) + vLowerBound;
        } else {
            return vLogFromPixel(pixel, zoom, shift);
        }
    }

//    double unit1FromV(double v) {
//        return (v - vLowerBound) / (vUpperBound - vLowerBound);
//    }

    private double pixelFromVLog(double v, double zoom, double shift) {
        return (log(v/vLowerBound) / log(vUpperBound /vLowerBound) - shift) * zoom * nCanvasPixel;
    }

    private double vLogFromPixel(double pixel, double zoom, double shift) {
        return exp((pixel / nCanvasPixel / zoom + shift) * log(vUpperBound /vLowerBound)) * vLowerBound;
    }

    double vMinInView(double zoom, double shift) {
        return vFromPixel(0, zoom, shift);
    }

    double vMaxInView(double zoom, double shift) {
        return vFromPixel(nCanvasPixel, zoom, shift);
    }

    double pixelFromV(double v) {
        return pixelFromV(v, zoom, shift);
    }

    double vFromPixel(double pixel) {
        return vFromPixel(pixel, zoom, shift);
    }

    double vMinInView() {
        return vFromPixel(0, zoom, shift);
    }

    double vMaxInView() {
        return vFromPixel(nCanvasPixel, zoom, shift);
    }

    double pixelNoZoomFromV(double v) {
        return pixelFromV(v, 1, 0);
    }

    double diffVBounds() { return vUpperBound - vLowerBound; }

    void reverseBounds() {
//        double vL = vMinInView();
//        double vH = vMaxInView();
//        setBounds(vUpperBound, vLowerBound);
//        setZoomShiftFromV(vH, vL);

        shift = 1 - 1/zoom - shift;
        setBounds(vUpperBound, vLowerBound);
    }

    void setMappingType(ScreenPhysicalMapping.Type _mapType, double lower_bound_ref) {
        // Set internal variables if possible
        double vL = vMinInView();
        double vH = vMaxInView();
        if (_mapType == Type.LOG) {
            if (vLowerBound == 0) vLowerBound = lower_bound_ref;
            if (vUpperBound == 0) vUpperBound = lower_bound_ref;
        } else {
            if (vLowerBound == lower_bound_ref) vLowerBound = 0;
            if (vUpperBound == lower_bound_ref) vUpperBound = 0;
        }
        boolean bNeedUpdateZoomShift = mapType != _mapType;
        mapType = _mapType;
        if (!bNeedUpdateZoomShift || nCanvasPixel == 0 || vLowerBound == vUpperBound) {
            return;
        }

        // Update zoom and shift
        // lower_bound_ref is for how to map zero
        // Only support non-negative bounds
        if (_mapType == Type.LOG) {
            if (vL < 0 || vH < 0) {
                Log.e(TAG, "setMappingType(): negative bounds.");
                return;
            }
            if (vL  < lower_bound_ref) vL = lower_bound_ref;
            if (vH  < lower_bound_ref) vH = lower_bound_ref;
        } else {
            if (vL  <= lower_bound_ref) vL = 0;
            if (vH  <= lower_bound_ref) vH = 0;
        }
        setZoomShiftFromV(vL, vH);
    }
}
