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
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

public class InfoRecActivity extends Activity {

	int[]    stdSourceId;  // how to make it final?
	int[]    stdSourceApi;
	String[] stdSourceName;
	String[] stdAudioSourcePermission;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_info_rec);
		// Show the Up button in the action bar.
		setupActionBar();

		stdSourceId   = getResources().getIntArray(R.array.StdAudioSourceId);
		stdSourceApi  = getResources().getIntArray(R.array.StdAudioSourceApiLevel);
		stdSourceName            = getResources().getStringArray(R.array.StdAudioSourceName);
		stdAudioSourcePermission = getResources().getStringArray(R.array.StdAudioSourcePermission);
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
		String[] sampleRates = new String[] { "8000", "11025", "16000", "22050",
				"32000", "44100", "48000", "96000"};
		String st = "SampleRate MinBuf   (Time)\n";

		ArrayList<String> resultMinBuffer = new ArrayList<>();
		for (String sr : sampleRates) {
			int rate = Integer.parseInt(sr.trim());
			int minBufSize = AudioRecord
					.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
							AudioFormat.ENCODING_PCM_16BIT);
			if (minBufSize != AudioRecord.ERROR_BAD_VALUE) {
				resultMinBuffer.add(sr);
				// /2.0 due to ENCODING_PCM_16BIT, CHANNEL_IN_MONO
				st += String.format(LC, "%5d Hz  %4d Byte (%4.1f ms)\n", rate, minBufSize, 1000.0*minBufSize/2.0/rate);
			} else {
				st += sr + "  ERROR\n";
			}
		}
		sampleRates = resultMinBuffer.toArray(new String[0]);

		appendTextData(tv, st);

		// Get audio source list
		int[] audioSourceId = GetAllAudioSource(7);
		ArrayList<String> audioSourceStringList = new ArrayList<>();
		for (int id : audioSourceId) {
			int k = arrayContainInt(stdSourceId, id);
			if (k >= 0) {
				audioSourceStringList.add(stdSourceName[k]);
			} else {
				audioSourceStringList.add("(unknown)");
			}
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
						st += "succeed";
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
					} else {
						st += "fail.\n";
					}
					record.release();
				}
				appendTextData(tv, st);
			}
		}
	}

	// filterLevel = 0: no filter
	//             & 1: leave only standard source
	//             & 2: leave only permitted source (&1)
	//             & 4: leave only source coincide the API level (&1)
	int[] GetAllAudioSource(int filterLevel) {
		// Use reflection to get all possible audio source (in compilation environment)
		ArrayList<Integer> iList = new ArrayList<>();
		Class<MediaRecorder.AudioSource> clazz = MediaRecorder.AudioSource.class;
		Field[] arr = clazz.getFields();
		for (Field f : arr) {
			if (f.getType().equals(int.class)) {
				try {
					int id = (int)f.get(null);
					iList.add(id);
					Log.w("Sources:", "" + id);
				} catch (IllegalAccessException e) {}
			}
		}

		// Filter unnecessary audio source
		Iterator<Integer> iterator;
		ArrayList<Integer> iListRet = iList;
		if (filterLevel > 0) {
			iListRet = new ArrayList<>();
			iterator = iList.iterator();
			for (int i = 0; i < iList.size(); i++) {
				int id = iterator.next();
				int k = arrayContainInt(stdSourceId, id); // get the index to standard source if possible
				if (k < 0) continue;
				if ((filterLevel & 2) > 0 && !stdAudioSourcePermission[k].equals("")) continue;
				if ((filterLevel & 4) > 0 && stdSourceApi[k] > Build.VERSION.SDK_INT) continue;
				iListRet.add(id);
			}
		}

		// Return an int array
		int[] ids = new int[iListRet.size()];
		iterator = iListRet.iterator();
		for (int i = 0; i < ids.length; i++) {
			ids[i] = iterator.next();
		}
		return ids;
	}

	// Java s**ks
	int arrayContainInt(int[] arr, int v) {
		if (arr == null) return -1;
		for (int i = 0; i < arr.length; i++) {
			if (arr[i] == v) return i;
		}
		return -1;
	}

	int arrayContainString(String[] arr, String v) {
		if (arr == null) return -1;
		for (int i = 0; i < arr.length; i++) {
			if (arr[i].equals(v)) return i;
		}
		return -1;
	}
}
