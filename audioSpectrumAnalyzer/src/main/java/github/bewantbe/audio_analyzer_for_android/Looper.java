package github.bewantbe.audio_analyzer_for_android;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.SystemClock;
import android.util.Log;

import java.util.Arrays;

/**
 * Read a snapshot of audio data at a regular interval, and compute the FFT
 * @author suhler@google.com
 *         bewantbe@gmail.com
 */

class Looper extends Thread {
    private final String TAG = "Looper";
    private volatile boolean isRunning = true;
    private volatile boolean isPaused1 = false;
    private STFT stft;   // use with care
    private AnalyzerParameters analyzerParam = null;

    private DoubleSineGen sineGen1;
    private DoubleSineGen sineGen2;
    private double[] spectrumDBcopy;   // XXX, transfers data from Looper to AnalyzeView

    private AnalyzeActivity activity;

    double wavSecRemain;
    double wavSec = 0;

    Looper(AnalyzeActivity _activity, AnalyzerParameters _analyzerParam) {
        activity = _activity;
        analyzerParam = _analyzerParam;

        isPaused1 = ((SelectorText) activity.findViewById(R.id.run)).getText().toString().equals("stop");
        // Signal sources for testing
        double fq0 = Double.parseDouble(activity.getString(R.string.test_signal_1_freq1));
        double amp0 = Math.pow(10, 1/20.0 * Double.parseDouble(activity.getString(R.string.test_signal_1_db1)));
        double fq1 = Double.parseDouble(activity.getString(R.string.test_signal_2_freq1));
        double amp1 = Math.pow(10, 1/20.0 * Double.parseDouble(activity.getString(R.string.test_signal_2_db1)));
        double fq2 = Double.parseDouble(activity.getString(R.string.test_signal_2_freq2));
        double amp2 = Math.pow(10, 1/20.0 * Double.parseDouble(activity.getString(R.string.test_signal_2_db2)));
        if (analyzerParam.audioSourceId == 1000) {
            sineGen1 = new DoubleSineGen(fq0, analyzerParam.sampleRate, analyzerParam.SAMPLE_VALUE_MAX * amp0);
        } else {
            sineGen1 = new DoubleSineGen(fq1, analyzerParam.sampleRate, analyzerParam.SAMPLE_VALUE_MAX * amp1);
        }
        sineGen2 = new DoubleSineGen(fq2, analyzerParam.sampleRate, analyzerParam.SAMPLE_VALUE_MAX * amp2);
    }

    private void SleepWithoutInterrupt(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private double baseTimeMs = SystemClock.uptimeMillis();

    private void LimitFrameRate(double updateMs) {
        // Limit the frame rate by wait `delay' ms.
        baseTimeMs += updateMs;
        long delay = (int) (baseTimeMs - SystemClock.uptimeMillis());
//      Log.i(TAG, "delay = " + delay);
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Log.i(TAG, "Sleep interrupted");  // seems never reached
            }
        } else {
            baseTimeMs -= delay;  // get current time
            // Log.i(TAG, "time: cmp t="+Long.toString(SystemClock.uptimeMillis())
            //            + " v.s. t'=" + Long.toString(baseTimeMs));
        }
    }

    private double[] mdata;

    // Generate test data.
    private int readTestData(short[] a, int offsetInShorts, int sizeInShorts, int id) {
        if (mdata == null || mdata.length != sizeInShorts) {
            mdata = new double[sizeInShorts];
        }
        Arrays.fill(mdata, 0.0);
        switch (id - 1000) {
            case 1:
                sineGen2.getSamples(mdata);
                // No break, so values of mdata added.
            case 0:
                sineGen1.addSamples(mdata);
                for (int i = 0; i < sizeInShorts; i++) {
                    a[offsetInShorts + i] = (short) Math.round(mdata[i]);
                }
                break;
            case 2:
                for (int i = 0; i < sizeInShorts; i++) {
                    a[i] = (short) (analyzerParam.SAMPLE_VALUE_MAX * (2.0*Math.random() - 1));
                }
                break;
            default:
                Log.w(TAG, "readTestData(): No this source id = " + analyzerParam.audioSourceId);
        }
        // Block this thread, so that behave as if read from real device.
        LimitFrameRate(1000.0*sizeInShorts / analyzerParam.sampleRate);
        return sizeInShorts;
    }

    @Override
    public void run() {
        AudioRecord record;

        activity.setupView();
        // Wait until previous instance of AudioRecord fully released.
        SleepWithoutInterrupt(500);

        int minBytes = AudioRecord.getMinBufferSize(analyzerParam.sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBytes == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Looper::run(): Invalid AudioRecord parameter.\n");
            return;
        }

        /**
         * Develop -> Reference -> AudioRecord
         *    Data should be read from the audio hardware in chunks of sizes
         *    inferior to the total recording buffer size.
         */
        // Determine size of buffers for AudioRecord and AudioRecord::read()
        int readChunkSize    = analyzerParam.fftLen/2;  // /2 due to overlapped analyze window
        readChunkSize        = Math.min(readChunkSize, 2048);  // read in a smaller chunk, hopefully smaller delay
        int bufferSampleSize = Math.max(minBytes / analyzerParam.BYTE_OF_SAMPLE, analyzerParam.fftLen/2) * 2;
        // tolerate up to about 1 sec.
        bufferSampleSize = (int)Math.ceil(1.0 * analyzerParam.sampleRate / bufferSampleSize) * bufferSampleSize;

        // Use the mic with AGC turned off. e.g. VOICE_RECOGNITION
        // The buffer size here seems not relate to the delay.
        // So choose a larger size (~1sec) so that overrun is unlikely.
        if (analyzerParam.audioSourceId < 1000) {
            record = new AudioRecord(analyzerParam.audioSourceId, analyzerParam.sampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, analyzerParam.BYTE_OF_SAMPLE * bufferSampleSize);
        } else {
            record = new AudioRecord(analyzerParam.RECORDER_AGC_OFF, analyzerParam.sampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, analyzerParam.BYTE_OF_SAMPLE * bufferSampleSize);
        }
        Log.i(TAG, "Looper::Run(): Starting recorder... \n" +
                "  source          : " + analyzerParam.getAudioSourceName() + "\n" +
                String.format("  sample rate     : %d Hz (request %d Hz)\n", record.getSampleRate(), analyzerParam.sampleRate) +
                String.format("  min buffer size : %d samples, %d Bytes\n", minBytes / analyzerParam.BYTE_OF_SAMPLE, minBytes) +
                String.format("  buffer size     : %d samples, %d Bytes\n", bufferSampleSize, analyzerParam.BYTE_OF_SAMPLE*bufferSampleSize) +
                String.format("  read chunk size : %d samples, %d Bytes\n", readChunkSize, analyzerParam.BYTE_OF_SAMPLE*readChunkSize) +
                String.format("  FFT length      : %d\n", analyzerParam.fftLen) +
                String.format("  nFFTAverage     : %d\n", analyzerParam.nFFTAverage));
        analyzerParam.sampleRate = record.getSampleRate();

        if (record.getState() == AudioRecord.STATE_UNINITIALIZED) {
            Log.e(TAG, "Looper::run(): Fail to initialize AudioRecord()");
            // If failed somehow, leave user a chance to change preference.
            return;
        }

        short[] audioSamples = new short[readChunkSize];
        int numOfReadShort;

        stft = new STFT(analyzerParam.fftLen, analyzerParam.sampleRate, analyzerParam.wndFuncName);
        stft.setAWeighting(analyzerParam.isAWeighting);
        if (spectrumDBcopy == null || spectrumDBcopy.length != analyzerParam.fftLen/2+1) {
            spectrumDBcopy = new double[analyzerParam.fftLen/2+1];
        }

        RecorderMonitor recorderMonitor = new RecorderMonitor(analyzerParam.sampleRate, bufferSampleSize, "Looper::run()");
        recorderMonitor.start();

//      FramesPerSecondCounter fpsCounter = new FramesPerSecondCounter("Looper::run()");

        WavWriter wavWriter = new WavWriter(analyzerParam.sampleRate);
        boolean bSaveWavLoop = activity.bSaveWav;  // change of bSaveWav during loop will only affect next enter.
        if (bSaveWavLoop) {
            wavWriter.start();
            wavSecRemain = wavWriter.secondsLeft();
            wavSec = 0;
            Log.i(TAG, "PCM write to file " + wavWriter.getPath());
        }

        // Start recording
        record.startRecording();

        // Main loop
        // When running in this loop (including when paused), you can not change properties
        // related to recorder: e.g. audioSourceId, sampleRate, bufferSampleSize
        // TODO: allow change of FFT length on the fly.
        while (isRunning) {
            // Read data
            if (analyzerParam.audioSourceId >= 1000) {
                numOfReadShort = readTestData(audioSamples, 0, readChunkSize, analyzerParam.audioSourceId);
            } else {
                numOfReadShort = record.read(audioSamples, 0, readChunkSize);   // pulling
            }
            if ( recorderMonitor.updateState(numOfReadShort) ) {  // performed a check
                if (recorderMonitor.getLastCheckOverrun())
                    activity.notifyOverrun();
                if (bSaveWavLoop)
                    wavSecRemain = wavWriter.secondsLeft();
            }
            if (bSaveWavLoop) {
                wavWriter.pushAudioShort(audioSamples, numOfReadShort);  // Maybe move this to another thread?
                wavSec = wavWriter.secondsWritten();
                activity.updateRec(wavSec);
            }
            if (isPaused1) {
//          fpsCounter.inc();
                // keep reading data, for overrun checker and for write wav data
                continue;
            }

            stft.feedData(audioSamples, numOfReadShort);

            // If there is new spectrum data, do plot
            if (stft.nElemSpectrumAmp() >= analyzerParam.nFFTAverage) {
                // Update spectrum or spectrogram
                final double[] spectrumDB = stft.getSpectrumAmpDB();
                System.arraycopy(spectrumDB, 0, spectrumDBcopy, 0, spectrumDB.length);
                activity.update(spectrumDBcopy);
//          fpsCounter.inc();

                stft.calculatePeak();
                activity.maxAmpFreq = stft.maxAmpFreq;
                activity.maxAmpDB = stft.maxAmpDB;

                // get RMS
                activity.dtRMS = stft.getRMS();
                activity.dtRMSFromFT = stft.getRMSFromFT();
            }
        }
        Log.i(TAG, "Looper::Run(): Actual sample rate: " + recorderMonitor.getSampleRate());
        Log.i(TAG, "Looper::Run(): Stopping and releasing recorder.");
        record.stop();
        record.release();
        record = null;
        if (bSaveWavLoop) {
            Log.i(TAG, "Looper::Run(): Ending saved wav.");
            wavWriter.stop();
            activity.notifyWAVSaved(wavWriter.relativeDir);
        }
    }

    void setAWeighting(boolean isAWeighting) {
        if (stft != null) {
            stft.setAWeighting(isAWeighting);
        }
    }

    void setPause(boolean pause) {
        this.isPaused1 = pause;
    }

    boolean getPause() {
        return this.isPaused1;
    }

    void finish() {
        isRunning = false;
        interrupt();
    }
}
