package com.sensorberg.filepreferences

import android.content.SharedPreferences
import com.sensorberg.executioner.Executioner.POOL
import com.sensorberg.executioner.Executioner.SINGLE
import com.sensorberg.executioner.Executioner.UI
import com.sensorberg.executioner.Executioner.runOn
import org.json.JSONObject
import timber.log.Timber
import java.io.*
import java.util.concurrent.FutureTask

class FilePreferences private constructor(private val file: File) : SharedPreferences {

	private object lock

	private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
	private var diskData: String? = null

	private val data: JSONObject by lazy {
		if (file.exists()) {
			diskData = getStringFromFile(file)
			JSONObject(diskData)
		} else {
			JSONObject()
		}
	}

	init {
		runOn(POOL) {
			// this data.hashCode() is on purpose to force creation of the object
			// that means, we try to load the json (I/O operation) on background,
			// but SharedPreferences API dictates that all actions can be done synchronously,
			// so if main thread directly tries to access it, it will have that I/O on main thread
			Timber.d("Loading ${data.hashCode()} from ${file.absolutePath}")
		}
	}

	override fun contains(key: String): Boolean {
		return data.has(key)
	}

	override fun getBoolean(key: String, defValue: Boolean): Boolean {
		return data.optBoolean(key, defValue)
	}

	override fun getString(key: String, defValue: String?): String? {
		return when {
			data.has(key) -> data.optString(key)
			defValue == null -> null
			else -> data.optString(key, defValue)
		}
	}

	override fun getInt(key: String, defValue: Int): Int {
		return data.optInt(key, defValue)
	}

	override fun getLong(key: String, defValue: Long): Long {
		return data.optLong(key, defValue)
	}

	override fun getFloat(key: String, defValue: Float): Float {
		return data.optDouble(key, defValue.toDouble()).toFloat()
	}

	override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String> {
		return if (data.has(key)) {
			val array = data.optJSONArray(key)
			val set = mutableSetOf<String>()
			for (i in 0..array.length()) {
				set.add(array.getString(i))
			}
			set
		} else defValues ?: mutableSetOf()
	}

	override fun getAll(): MutableMap<String, *> {
		val map = mutableMapOf<String, Any>()
		data.keys().forEach {
			map[it] = data.get(it)
		}
		return map
	}

	override fun edit(): SharedPreferences.Editor {
		return PreferencesEditor(this, JSONObject(data.toString()))
	}

	override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
		synchronized(listeners) {
			listeners.add(listener)
		}
	}

	override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
		synchronized(listeners) {
			listeners.remove(listener)
		}
	}

	internal fun commit(jsonObject: JSONObject, changedKeys: Set<String>): Boolean {
		updateData(jsonObject)
		val future = saveOnFile(jsonObject)
		updateListeners(changedKeys)
		return future.get()
	}

	internal fun apply(jsonObject: JSONObject, changedKeys: Set<String>) {
		updateData(jsonObject)
		saveOnFile(jsonObject)
		updateListeners(changedKeys)
	}

	private fun saveOnFile(jsonObject: JSONObject): FutureTask<Boolean> {
		val task = FutureTask<Boolean> {
			try {
				val stringData = jsonObject.toString()
				if (!file.exists() || stringData != diskData) {
					setStringToFile(file, stringData)
					diskData = stringData
				}
				true
			} catch (e: Exception) {
				Timber.e(e, "Failed to write shared preferences on ${file.absolutePath}")
				false
			}
		}
		runOn(SINGLE, task)
		return task
	}

	private fun updateListeners(changedKeys: Set<String>) {
		runOn(UI) {
			synchronized(listeners) {
				listeners.forEach { listener ->
					changedKeys.forEach { key ->
						listener.onSharedPreferenceChanged(this, key)
					}
				}
			}
		}
	}

	private fun updateData(newData: JSONObject) {
		val toBeRemoved = mutableSetOf<String>()
		data.keys().forEach {
			if (!newData.has(it)) {
				toBeRemoved.add(it)
			}
		}

		newData.keys().forEach { data.put(it, newData.get(it)) }
		toBeRemoved.forEach { data.remove(it) }
	}

	companion object {

		private fun getStringFromFile(file: File): String {
			val stream = FileInputStream(file)
			val reader = BufferedReader(InputStreamReader(stream))
			val sb = StringBuilder()
			var line: String? = reader.readLine()
			while (line != null) {
				sb.append(line)
				line = reader.readLine()
			}
			reader.close()
			stream.close()
			return sb.toString()
		}

		private fun setStringToFile(file: File, string: String) {
			val stream = FileOutputStream(file)
			val writer = OutputStreamWriter(stream)
			writer.write(string)
			stream.flush()
			writer.close()
			stream.close()
		}

		private val defaultFactory = object : Factory {

			private val instances = mutableMapOf<File, SharedPreferences>()

			override fun create(filePath: String): SharedPreferences {
				return create(File(filePath))
			}

			override fun create(file: File): SharedPreferences {
				var instance = instances[file]
				if (instance == null) {
					instance = FilePreferences(file)
					instances[file] = instance
				}
				return instance
			}
		}
		private var factory: Factory = defaultFactory
		fun setFactory(factory: Factory?) {
			if (factory == null) {
				this.factory = defaultFactory
			} else {
				this.factory = factory
			}
		}

		fun create(filePath: String): SharedPreferences {
			return factory.create(filePath)
		}

		fun create(file: File): SharedPreferences {
			return factory.create(file)
		}
	}

	interface Factory {
		fun create(filePath: String): SharedPreferences
		fun create(file: File): SharedPreferences
	}
}