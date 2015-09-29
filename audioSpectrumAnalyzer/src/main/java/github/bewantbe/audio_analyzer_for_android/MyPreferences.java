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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

// I'm using an old cell phone -- API level 9 (android 2.3.6)
// http://developer.android.com/guide/topics/ui/settings.html
@SuppressWarnings("deprecation")
public class MyPreferences extends PreferenceActivity {
  static final String TAG = "MyPreference";
  static String[] as;
  static int[] asid;
  private static String getAudioSourceNameFromId(int id) {
    for (int i = 0; i < as.length; i++) {
      if (asid[i] == id) {
        return as[i];
      }
    }
    Log.e(TAG, "getAudioSourceName(): no this entry.");
    return "";
  }
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
    
    Intent intent = getIntent();
    asid = intent.getIntArrayExtra(AnalyzeActivity.MYPREFERENCES_MSG_SOURCE_ID);
    as = intent.getStringArrayExtra(AnalyzeActivity.MYPREFERENCES_MSG_SOURCE_NAME);

    // as soon as the user modifies a preference,
    // the system saves the changes to a default SharedPreferences file
  }

  SharedPreferences.OnSharedPreferenceChangeListener prefListener =
      new SharedPreferences.OnSharedPreferenceChangeListener() {
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
      Log.i(TAG, key + "=" + prefs);
      if (key == null || key.equals("windowFunction")) {
        Preference connectionPref = findPreference(key);
        connectionPref.setSummary(prefs.getString(key, ""));
      }
      if (key == null || key.equals("audioSource")) {
        String asi = prefs.getString("audioSource", getString(R.string.audio_source_id_default));
        int audioSourceId = Integer.parseInt(asi);
        Preference connectionPref = findPreference(key);
        connectionPref.setSummary(getAudioSourceNameFromId(audioSourceId));
      }
    }
  };
  @Override
  protected void onResume() {
      super.onResume();
      getPreferenceScreen().getSharedPreferences()
              .registerOnSharedPreferenceChangeListener(prefListener);
  }
  @Override
  protected void onPause() {
      super.onPause();
      getPreferenceScreen().getSharedPreferences()
              .unregisterOnSharedPreferenceChangeListener(prefListener);
  }
}
