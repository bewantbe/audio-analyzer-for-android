package github.bewantbe.audio_analyzer_for_android;

import android.content.res.Resources;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * Basic properties of Analyzer.
 */

class AnalyzerParameters {
    final int RECORDER_AGC_OFF = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    int fftLen = 2048;
    int sampleRate = 16000;
    int nFFTAverage = 2;
    String wndFuncName;
    int audioSourceId = RECORDER_AGC_OFF;
    boolean isAWeighting = false;
    final int BYTE_OF_SAMPLE = 2;
    final double SAMPLE_VALUE_MAX = 32767.0;   // Maximum signal value
    double timeDurationPref = 4.0;

    AnalyzerParameters(Resources res) {
        getAudioSourceNameFromIdPrepare(res);
    }

    String[] audioSourceNames;
    int[] audioSourceIDs;
    private void getAudioSourceNameFromIdPrepare(Resources res) {
        audioSourceNames   = res.getStringArray(R.array.audio_source);
        String[] sasid = res.getStringArray(R.array.audio_source_id);
        audioSourceIDs = new int[audioSourceNames.length];
        for (int i = 0; i < audioSourceNames.length; i++) {
            audioSourceIDs[i] = Integer.parseInt(sasid[i]);
        }
    }

    // Get audio source name from its ID
    // Tell me if there is better way to do it.
    private String getAudioSourceNameFromId(int id) {
        for (int i = 0; i < audioSourceNames.length; i++) {
            if (audioSourceIDs[i] == id) {
                return audioSourceNames[i];
            }
        }
        Log.i("AnalyzerParameters", "getAudioSourceName(): non-standard entry.");
        return ((Integer)(id)).toString();
    }

    String getAudioSourceName() {
        return getAudioSourceNameFromId(audioSourceId);
    }
}
