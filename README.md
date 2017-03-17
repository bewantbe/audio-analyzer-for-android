Audio Spectrum Analyzer for Android
===================================

>  A fork of [Audio spectrum Analyzer for Android](https://code.google.com/p/audio-analyzer-for-android/) (See README.old for its original readme)

  This software shows the frequency components' magnitude distribution (called spectrum) of the sound heard by your cell phone.

  You may install this app through **Google Play Store**: [Audio Spectrum Analyzer](https://play.google.com/store/apps/details?id=github.bewantbe.audio_analyzer_for_android)

  This software, [Audio Spectrum Analyzer for Android](https://github.com/bewantbe/audio-analyzer-for-android), is released under the Apache License, Version 2.0.


Features
--------

* Show [spectrum](http://en.wikipedia.org/wiki/Frequency_spectrum) or [spectrogram](http://en.wikipedia.org/wiki/Spectrogram) in real-time, with decent axis labels.
* Log and Linear frequency axis support.
* You can put a cursor in the plot, for measurement or as a marker.
* Easy gestures to fine exam the spectrum: i.e. pinch for scaling and swipe for  view move.
* Show peak frequency in a moderate accuracy (FFT + interpolation).
* Show dB or [A-weighting dB (dBA)](http://en.wikipedia.org/wiki/A-weighting), although not suitable for serious application.
* Possible to take averages of several spectrum then plot, make the spectrum smoother.
* You may record the sound (while analyzing!) to a WAV file (PCM format). Then deal with it with your favorite tool.
* Support all recorder sources except those need root privilege (see list in Android reference: [MediaRecorder.AudioSource](http://developer.android.com/reference/android/media/MediaRecorder.AudioSource.html))
* Support all possible sampling rates that your phone is capable. e.g. useful to find out the native (or best) sampling format for you phone.


Installation Requirements
-------------------------

* >= Android 2.2 (API Level 8). The recent version need Android 2.3 (API Level 9).
* External storage (e.g MicroSD card), if you want to record the sound.


Development
-----------

`git clone` then open it use Android Studio. Install the SDK platform if requested (e.g. rev 116 needs API level 20), or tune the `compileSdkVersion` to the value that fits your needs.


### For old revision (rev <= 115)

Import eclipse project to Android Studio (tested in Android Studio 1.1.0 with OpenJDK-7 v2.5.4)

* As Gradle-based projects (recommended)

  1. git clone *repo-path* audio-analyzer-for-android
  2. Copy the standard library project "android-support-v7-appcompat" to "audio-analyzer-for-android/android-support-v7-appcompat".
  3. Modify "audio-analyzer-for-android/audioAnalyzer/project.properties", change "android.library.reference.2=../../../workspace/android-support-v7-appcompat" to "android.library.reference.2=../android-support-v7-appcompat".
  4. Click "Import project" in the welcome dialog box of Android Studio.
  5. Choose the sub-directory "audio-analyzer-for-android/audioAnalyzer".
  6. Choose a name for Destination Directory. Next.
  7. (check the two "Replace ... when possible") Finish. Then import-summary.txt will be generated.

    You should have a workable copy now.

* Or, as IntelliJ "classic" projects

  1. git clone *repo-path* audio-analyzer-for-android
  2. Click "Import project" in the welcome dialog box of Android Studio.
  3. Choose the directory "audio-analyzer-for-android".
  4. Select "Create project from existing sources", Next.
  5. Next.
  6. Uncheck the directories that end with "gen", codes there are auto generated. Next.
  7. (review libraries found) Next.
  8. (review suggested module structure) Next.
  9. (select project SDK) Next.
 10. (review frameworks) Finish.

    Now if you build the project, you will get an error "Cannot resolve symbol '@style/Theme.AppCompat'". This theme is in "android.support.v7.appcompat", I don't know how to import these values yet. Just choose another built-in theme will make it work. e.g. change to android:theme="@android:style/Theme.Black".


Thanks
------

The code [Audio spectrum Analyzer for Android](https://code.google.com/p/audio-analyzer-for-android/) gives me a good starting point, for learning Java and write this software (that I desired long ago).

