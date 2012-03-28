package com.cognitiva.sconnect;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.text.InputType;

public class AppPreferencesActivity extends PreferenceActivity {
	
	@Override 
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		//restrict some preferences to numeric values 
		EditTextPreference pref = (EditTextPreference)findPreference("port");
		pref.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
		pref = (EditTextPreference)findPreference("client");
		pref.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
	}
}
