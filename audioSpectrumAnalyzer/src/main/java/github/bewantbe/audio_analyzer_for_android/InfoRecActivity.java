/* Copyright 2014 Eddy Xiao <bewantbe@gmail.com>
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

// Test all (including unknown) recorder sources by open it and read data.

public class InfoRecActivity extends Activity {
	AnalyzerUtil analyzerUtil;
	CharSequence testResultSt = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_info_rec);
		// Show the Up button in the action bar.
		setupActionBar();

		analyzerUtil = new AnalyzerUtil(this);
		testResultSt = null;
	}

	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			if (getActionBar() != null)
				getActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.info_rec, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResume() {
		super.onResume();
		final TextView tv = (TextView) findViewById(R.id.textview_info_rec);

		if (testResultSt != null) {
			tv.setText(testResultSt);
			return;
		}

		tv.setMovementMethod(new ScrollingMovementMethod());
		tv.setText("(Only MONO, 16BIT format is tested)\n");

		Thread testerThread = new Thread(new Runnable() {
			@Override
			public void run() {
				TestAudioRecorder(tv);
			}
		});

		testerThread.start();
	}

	private void appendTextData(final TextView tv, final String st) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				tv.append(st);
				tv.invalidate();
			}
		});
	}

	// Show supported sample rate and corresponding minimum buffer size.
	private void TestAudioRecorder(final TextView tv) {
		Locale LC = Locale.getDefault();

		// All possible sample rate
		String[] sampleRates = getResources().getStringArray(R.array.std_sampling_rates);
		String st = "SampleRate MinBuf    (Time)\n";

		ArrayList<String> resultMinBuffer = new ArrayList<>();
		for (String sr : sampleRates) {
			int rate = Integer.parseInt(sr.trim());
			int minBufSize = AudioRecord
					.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
							AudioFormat.ENCODING_PCM_16BIT);
			if (minBufSize != AudioRecord.ERROR_BAD_VALUE) {
				resultMinBuffer.add(sr);
				// /2.0 due to ENCODING_PCM_16BIT, CHANNEL_IN_MONO
				st += String.format(LC, "%5d Hz  %4d Byte  (%4.1f ms)\n", rate, minBufSize, 1000.0*minBufSize/2.0/rate);
			} else {
				st += sr + "  ERROR\n";
			}
		}
		sampleRates = resultMinBuffer.toArray(new String[0]);

		appendTextData(tv, st);

		// Get audio source list
		int[] audioSourceId = analyzerUtil.GetAllAudioSource(0);
		ArrayList<String> audioSourceStringList = new ArrayList<>();
		for (int id : audioSourceId) {
			audioSourceStringList.add(analyzerUtil.getAudioSourceName(id));
		}
		String[] audioSourceString = audioSourceStringList.toArray(new String[0]);

		appendTextData(tv, "\n-- Audio Source Test --\n");
		for (int ias = 0; ias < audioSourceId.length; ias++) {
			st = "\nSource: " + audioSourceString[ias] + "\n";
			appendTextData(tv, st);
			for (String sr : sampleRates) {
				int sampleRate = Integer.parseInt(sr.trim());
				int recBufferSize = AudioRecord.getMinBufferSize(sampleRate,
						AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
				st = String.format(LC, "%5d Hz  ", sampleRate);

				// wait for AudioRecord fully released...
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				AudioRecord record;
				try {
					record = new AudioRecord(audioSourceId[ias], sampleRate,
							AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recBufferSize);
				} catch (IllegalArgumentException e) {
					st += "Illegal Argument.\n";
					record = null;
				}
				if (record != null) {
					if (record.getState() == AudioRecord.STATE_INITIALIZED) {
						int numOfReadShort = 0;
						try {
							record.startRecording();
							short[] audioSamples = new short[recBufferSize];
							numOfReadShort = record.read(audioSamples, 0, recBufferSize);
						} catch (IllegalStateException e) {
							numOfReadShort = -1;
						}
						if (numOfReadShort > 0) {
							st += "succeed";
						} else if (numOfReadShort == 0) {
							st += "read 0 byte";
						} else {
							st += "fail to record.";
						}
						int as = record.getAudioSource();
						if (as != audioSourceId[ias]) {  // audio source altered
							int i = 0;
							while (i < audioSourceId.length) {
								if (as == audioSourceId[ias]) {
									break;
								}
								i++;
							}
							if (i >= audioSourceId.length) {
								st += "(auto set to \"unknown source\")";
							} else {
								st += "(auto set to " + audioSourceString[i] + ")";
							}
						}
						st += ".\n";
						record.stop();
					} else {
						st += "fail to initialize.\n";
					}
					record.release();
				}
				appendTextData(tv, st);
			}
		}

		testResultSt = tv.getText();
	}
}
