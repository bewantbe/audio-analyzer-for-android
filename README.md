Audio Spectrum Analyzer for Android
===================================

>  A fork of [Audio spectrum Analyzer for Android](https://code.google.com/p/audio-analyzer-for-android/) (See README.old for its original readme)

  This software shows the frequency components' magnitude distribution (called spectrum) of the sound heard by your cell phone.

Features:
---------

* Show [spectrum](http://en.wikipedia.org/wiki/Frequency_spectrum) or [spectrogram](http://en.wikipedia.org/wiki/Spectrogram) in real-time, with decent axis labels.
* In spectrum mode you may put a cursor in the plot, for measurement or as a marker.
* Fine exam the spectrum by gestures: i.e. pinch for scaling and swipe for  view move.
* Show peak frequency, in a moderate accuracy (FFT + interpolation).
* Show dB or [A-weighting dB (dBA)](http://en.wikipedia.org/wiki/A-weighting), although not suitable for serious application.
* Take averages of several spectrum then plot, make the spectrum smoother.
* You may record the sound (while analyzing or not) to a WAV file (PCM format). Then deal with it with your favarite tool.
* You can choose various recorder source. (see Android reference: [MediaRecorder.AudioSource](http://developer.android.com/reference/android/media/MediaRecorder.AudioSource.html))
* You can try different sampling rates that your phone supports. Useful for example to find out the native (or best) sampling formate for you phone.


Installation Requirements:
--------------------------

* >= Android 2.2 (API Level 8)
* External storage (e.g MicroSD card), if you want to record the sound.

### Thanks

The code [Audio spectrum Analyzer for Android](https://code.google.com/p/audio-analyzer-for-android/) gives me a good starting point, for leanring Java and write this software (that I desired long ago).

