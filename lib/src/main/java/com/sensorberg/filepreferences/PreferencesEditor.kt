package com.sensorberg.filepreferences

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class PreferencesEditor(private val preferences: FilePreferences, private var data: JSONObject) : SharedPreferences.Editor {

	private val changedKeys = mutableSetOf<String>()

	override fun clear(): SharedPreferences.Editor {
		data = JSONObject()
		return this
	}

	override fun putLong(key: String, value: Long): SharedPreferences.Editor {
		changedKeys.add(key)
		data.put(key, value)
		return this
	}

	override fun putInt(key: String, value: Int): SharedPreferences.Editor {
		changedKeys.add(key)
		data.put(key, value)
		return this
	}

	override fun remove(key: String): SharedPreferences.Editor {
		changedKeys.add(key)
		data.remove(key)
		return this
	}

	override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
		changedKeys.add(key)
		data.put(key, value)
		return this
	}

	override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
		changedKeys.add(key)
		if (values == null) data.remove(key)
		else {
			val array = JSONArray()
			for (value in values) array.put(value)
			data.put(key, array)
		}
		return this
	}

	override fun commit(): Boolean {
		return preferences.commit(data, changedKeys)
	}

	override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
		changedKeys.add(key)
		data.put(key, value.toDouble())
		return this
	}

	override fun apply() {
		preferences.apply(data, changedKeys)
	}

	override fun putString(key: String, value: String?): SharedPreferences.Editor {
		changedKeys.add(key)
		if (value == null) data.remove(key)
		else data.put(key, value)
		return this
	}
}