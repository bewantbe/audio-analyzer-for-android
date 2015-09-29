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

import java.util.ArrayList;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.support.v4.app.NavUtils;
import android.text.method.ScrollingMovementMethod;
import android.annotation.TargetApi;
import android.os.Build;

public class InfoRecActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_info_rec);
		// Show the Up button in the action bar.
		setupActionBar();
	}

	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
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
		TextView tv = (TextView) findViewById(R.id.textview_info_rec);
		tv.setMovementMethod(new ScrollingMovementMethod());

		tv.setText("Testing...");  // TODO: No use...
		tv.invalidate();

		// Show supported sample rate and corresponding minimum buffer size.
		String[] requested = new String[] { "8000", "11025", "16000", "22050",
				"32000", "44100", "48000", "96000"};
		String st = "sampleRate minBufSize\n";
		ArrayList<String> validated = new ArrayList<String>();
		for (String s : requested) {
			int rate = Integer.parseInt(s);
			int minBufSize = AudioRecord
					.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
							AudioFormat.ENCODING_PCM_16BIT);
			if (minBufSize != AudioRecord.ERROR_BAD_VALUE) {
				validated.add(s);
				st += s + "  \t" + Integer.toString(minBufSize) + "\n";
			}
		}
		requested = validated.toArray(new String[0]);

		tv.setText(st);
		tv.invalidate();

		// Test audio source
		String[] audioSourceString = new String[] { "DEFAULT", "MIC", "VOICE_UPLINK", "VOICE_DOWNLINK",
				"VOICE_CALL", "CAMCORDER", "VOICE_RECOGNITION" };
		int[] audioSourceId = new int[] {
				MediaRecorder.AudioSource.DEFAULT,           // Default audio source
				MediaRecorder.AudioSource.MIC,               // Microphone audio source
				MediaRecorder.AudioSource.VOICE_UPLINK,      // Voice call uplink (Tx) audio source
				MediaRecorder.AudioSource.VOICE_DOWNLINK,    // Voice call downlink (Rx) audio source
				MediaRecorder.AudioSource.VOICE_CALL,        // Voice call uplink + downlink audio source
				MediaRecorder.AudioSource.CAMCORDER,         // Microphone audio source with same orientation as camera if available, the main device microphone otherwise (apilv7)
				MediaRecorder.AudioSource.VOICE_RECOGNITION, // Microphone audio source tuned for voice recognition if available, behaves like DEFAULT otherwise. (apilv7)
//				MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Microphone audio source tuned for voice communications such as VoIP. It will for instance take advantage of echo cancellation or automatic gain control if available. It otherwise behaves like DEFAULT if no voice processing is applied. (apilv11)
//				MediaRecorder.AudioSource.REMOTE_SUBMIX,       // Audio source for a submix of audio streams to be presented remotely. (apilv19)
		};
		tv.append("\n-- Audio Source Test --");
		for (String s : requested) {
			int sampleRate = Integer.parseInt(s);
			int recBufferSize = AudioRecord.getMinBufferSize(sampleRate,
	            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
			tv.append("\n("+Integer.toString(sampleRate)+"Hz, MONO, 16BIT)\n");
			for (int iass = 0; iass<audioSourceId.length; iass++) {
				st = "";
				// wait for AudioRecord fully released...
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				AudioRecord record;
				record =  new AudioRecord(audioSourceId[iass], sampleRate,
				          AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,  recBufferSize);
				if (record.getState() == AudioRecord.STATE_INITIALIZED) {
					st += audioSourceString[iass] + " successed";
					int as = record.getAudioSource();
					if (as != audioSourceId[iass]) {
						int i = 0;
						while (i<audioSourceId.length) {
							if (as == audioSourceId[iass]) {
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
					st += "\n";
				} else {
					st += audioSourceString[iass] + " failed\n";
				}
				record.release();
				record = null;
				tv.append(st);
				tv.invalidate();
			}
		}

	}

}
