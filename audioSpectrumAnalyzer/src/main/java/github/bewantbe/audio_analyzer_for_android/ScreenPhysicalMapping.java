package github.bewantbe.audio_analyzer_for_android;

import static java.lang.Math.exp;
import static java.lang.Math.log;

/**
 * Mapping between physical value and screen pixel position
 * Use double or float ?
 */

class ScreenPhysicalMapping {

    enum Type {  // java's enum type is inconvenient
        LINEAR(0), LINEAR_ON(1), LOG(2);

        private final int value;
        Type(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    int mapTypeInt;        // Linear or Log
    // TODO: check nCanvasPixel==0
    float nCanvasPixel;
    // Physical limits
    float vLowerBound, vHigherBound;
    //double zoom, shift;  // zoom==1 means no zooming, shift=0 means no shift
    float zoom = 1, shift = 0;

    ScreenPhysicalMapping(float _nCanvasPixel, float _vLowerBound, float _vHigherBound, ScreenPhysicalMapping.Type _mapType) {
        nCanvasPixel = _nCanvasPixel;
        vLowerBound  = _vLowerBound;
        vHigherBound = _vHigherBound;
        mapTypeInt   = _mapType.getValue();
    }

    void setNCanvasPixel(float _nCanvasPixel) {
        nCanvasPixel = _nCanvasPixel;
    }

    void setBounds(float _vLowerBound, float _vHigherBound) {
        vLowerBound  = _vLowerBound;
        vHigherBound = _vHigherBound;
    }

    void setZoomShift(float _zoom, float _shift) {
        zoom = _zoom;
        shift = _shift;
    }

    //  | lower bound    ...       higher bound |     physcial unit
    //  | 0              ...                  1 |     "unit 1" (Mapping can be linear or log)

    // In LINEAR mode (default):
    //    |lower value  ...    higher value|  physical unit
    //    | shift       ... shift + 1/zoom |  "unit 1", 0=vLowerBound, 1=vHigherBound
    //    | 0 | 1 |     ...          | n-1 |  pixel

    // In LINEAR_ON mode (not implemented):
    //      |lower value ...    higher value|     physical unit
    //      | shift      ... shift + 1/zoom |     "unit 1" window
    //    | 0 | 1 |      ...             | n-1 |  pixel

    // this class do not verify if the input data are legal
    float pixelFromV(float v, float zoom, float shift) {
        // old: canvasX4axis
        // return (v - vLowerBound) / (vHigherBound - vLowerBound) * nCanvasPixel;
        // old: canvasViewX4axis
        if (vHigherBound == vLowerBound || vHigherBound != vHigherBound || vLowerBound != vLowerBound) {
            return 0;
        }
        if (mapTypeInt == 0) {
            return ((v - vLowerBound) / (vHigherBound - vLowerBound) - shift) * zoom * nCanvasPixel;
        } else {
            return pixelFromVLog(v, zoom, shift);
        }
    }

    float vFromPixel(float pixel, float zoom, float shift) {
        if (nCanvasPixel == 0 || zoom == 0) {
            return 0;
        }
        if (mapTypeInt == 0) {
            return (pixel / nCanvasPixel / zoom + shift) * (vHigherBound - vLowerBound) + vLowerBound;
        } else {
            return vLogFromPixel(pixel, zoom, shift);
        }
    }

//    float unit1FromV(float v) {
//        return (v - vLowerBound) / (vHigherBound - vLowerBound);
//    }

    private float pixelFromVLog(float v, float zoom, float shift) {
        return ((float)log(v/vLowerBound) / (float)log(vHigherBound/vLowerBound) - shift) * zoom * nCanvasPixel;
    }

    private float vLogFromPixel(float pixel, float zoom, float shift) {
        return (float)exp((pixel / nCanvasPixel / zoom + shift) * (float)log(vHigherBound/vLowerBound)) * vLowerBound;
    }

    float vMinInView(float zoom, float shift) {
        return vFromPixel(0, zoom, shift);
    }

    float vMaxInView(float zoom, float shift) {
        return vFromPixel(nCanvasPixel, zoom, shift);
    }

    float pixelFromV(float v) {
        return pixelFromV(v, zoom, shift);
    }

    float vFromPixel(float pixel) {
        return vFromPixel(pixel, zoom, shift);
    }

    float vMinInView() {
        return vFromPixel(0, zoom, shift);
    }

    float vMaxInView() {
        return vFromPixel(nCanvasPixel, zoom, shift);
    }

    float pixelNoZoomFromV(float v) {
        return pixelFromV(v, 1, 0);
    }

    float diffVBounds() { return vHigherBound - vLowerBound; };
}
