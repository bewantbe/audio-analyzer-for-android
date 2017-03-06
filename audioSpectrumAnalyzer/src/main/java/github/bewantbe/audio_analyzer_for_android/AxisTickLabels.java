package github.bewantbe.audio_analyzer_for_android;

import android.graphics.Canvas;
import android.graphics.Paint;

class AxisTickLabels {
    private static final String[] axisNames = {"Hz", "dB", "Sec", "Hz"};

    // Draw ticks and labels for a axisMap.
    // labelPaint should use fixed width font
    static void draw(Canvas c, ScreenPhysicalMapping axisMap, GridLabel gridLabel,
                     float axisBeginX, float axisBeginY, int directionI, int labelSide,
                     Paint labelPaint, Paint gridPaint, Paint rulerBrightPaint) {
        String axisName = axisNames[gridLabel.getGridType().getValue()];

        float textHeight = labelPaint.getFontMetrics(null);
        float labelLargeLen = 0.7f * textHeight;
        float labelSmallLen = 0.6f * labelLargeLen;

        // directionI: 0:+x, 1:+y, 2:-x, 3:-y
        // labelSide:  1: label at further side of axis, -1: otherwise
        boolean drawOnXAxis = directionI % 2 == 0;
        int directionSign = directionI <=1 ? 1 : -1;

        // Plot axis marks
        float posAlongAxis;
        for (int k = 0; k < 2; k ++) {
            float labelLen   = (k == 0 ? labelSmallLen : labelLargeLen) * labelSide;
            Paint tickPainter = k == 0 ? gridPaint : rulerBrightPaint;
            double[] values   = k == 0 ? gridLabel.ticks : gridLabel.values;
            for (int i = 0; i < values.length; i++) {
                posAlongAxis = axisMap.pixelFromV((float) values[i]) * directionSign;
                if (drawOnXAxis) {
                    c.drawLine(axisBeginX + posAlongAxis, axisBeginY,
                               axisBeginX + posAlongAxis, axisBeginY + labelLen, tickPainter);
                } else {
                    c.drawLine(axisBeginX,            axisBeginY + posAlongAxis,
                               axisBeginX + labelLen, axisBeginY + posAlongAxis, tickPainter);
                }
            }
        }
        if (drawOnXAxis) {
            c.drawLine(axisBeginX, axisBeginY, axisBeginX + axisMap.nCanvasPixel * (1-directionI), axisBeginY, labelPaint);
        } else {
            c.drawLine(axisBeginX, axisBeginY, axisBeginX, axisBeginY + axisMap.nCanvasPixel * (2-directionI), labelPaint);
        }

        // Plot labels
        float widthDigit = labelPaint.measureText("0");
        float widthAxisName = widthDigit * axisName.length();
        float widthAxisNameExt = widthAxisName +.5f*widthDigit;  // with a safe boundary

        // For drawOnXAxis == true
        float axisNamePosX = directionSign==1
                ? - widthAxisNameExt + axisMap.nCanvasPixel
                : - widthAxisNameExt;
        // For drawOnXAxis == false
        // always show y-axis name at the smaller (in pixel) position.
        float axisNamePosY = directionSign==1
                ? textHeight
                : textHeight - axisMap.nCanvasPixel;
        if (gridLabel.getGridType() == GridLabel.Type.DB) {
            // For dB axis, show axis name far from 0dB (directionSign==1)
            axisNamePosY = axisMap.nCanvasPixel - 0.8f*widthDigit;
        }

        float labelPosY = axisBeginY + ( labelSide == 1 ? 0.1f*labelLargeLen + textHeight : -0.3f*labelLargeLen);
        float labelPosX;
        int notShowNextLabel = 0;

        for(int i = 0; i < gridLabel.strings.length; i++) {
            posAlongAxis = axisMap.pixelFromV((float)gridLabel.values[i]) * directionSign;
            // Avoid label overlap:
            // (1) No overlap to axis name like "Hz";
            // (2) If no (1), no overlap to important label 1, 10, 100, 1000, 10000, 1k, 10k;
            // (3) If no (1) and (2), no overlap to previous label.
            if (drawOnXAxis) {
                float thisDigitWidth = widthDigit*gridLabel.strings[i].length() + 0.3f*widthDigit;
                if (isIntvOverlap(posAlongAxis, thisDigitWidth,
                        axisNamePosX, widthAxisNameExt)) {
                    continue;  // case (1)
                }
                if (notShowNextLabel > 0) {
                    notShowNextLabel--;
                    continue;  // case (3)
                }
                int j = i+1;
                while (j < gridLabel.strings.length) {
                    float nextDigitPos = axisMap.pixelFromV((float)gridLabel.values[j]) * directionSign;
                    float nextDigitWidth = widthDigit*gridLabel.strings[j].length() + 0.3f*widthDigit;
                    if (! isIntvOverlap(posAlongAxis, thisDigitWidth,
                            nextDigitPos, nextDigitWidth)) {
                        break;  // no overlap of case (3)
                    }
                    notShowNextLabel++;
                    if (gridLabel.isImportantLabel(j)) {
                        // do not show label i (case (2))
                        // but also check case (1) for label j
                        if (! isIntvOverlap(nextDigitPos, nextDigitWidth,
                                axisNamePosX, widthAxisNameExt)) {
                            notShowNextLabel = -1;
                            break;
                        }
                    }
                    j++;
                }
                if (notShowNextLabel == -1) {
                    notShowNextLabel = j - i - 1;  // show the label in case (2)
                    continue;
                }
                c.drawText(gridLabel.chars[i], 0, gridLabel.strings[i].length(),
                           axisBeginX + posAlongAxis, labelPosY, labelPaint);
            } else {
                if (isIntvOverlap(posAlongAxis, -textHeight,
                        axisNamePosY, -textHeight)) {
                    continue;
                }
                labelPosX = labelSide == -1
                        ? axisBeginX - 0.5f * labelLargeLen - widthDigit * gridLabel.strings[i].length()
                        : axisBeginX + 0.5f * labelLargeLen;
                c.drawText(gridLabel.chars[i], 0, gridLabel.strings[i].length(),
                           labelPosX, axisBeginY + posAlongAxis, labelPaint);
            }
        }
        if (drawOnXAxis) {
            c.drawText(axisName, axisBeginX + axisNamePosX, labelPosY, labelPaint);
        } else {
            labelPosX = labelSide == -1
                    ? axisBeginX - 0.5f * labelLargeLen - widthAxisName
                    : axisBeginX + 0.5f * labelLargeLen;
            c.drawText(axisName, labelPosX, axisBeginY + axisNamePosY, labelPaint);
        }

    }

    private static boolean isIntvOverlap(float pos1, float len1, float pos2, float len2) {
        if (len1 < 0) { pos1 -= len1; len1 = -len1; }
        if (len2 < 0) { pos2 -= len2; len2 = -len2; }
        return pos1 <= pos2 && pos2 <= pos1+len1 || pos2 <= pos1 && pos1 <= pos2+len2;
    }
}
