package com.sensorberg.filepreferences

import android.content.Context
import android.content.SharedPreferences
import com.sensorberg.executioner.Executioner.POOL
import com.sensorberg.executioner.Executioner.SINGLE
import com.sensorberg.executioner.Executioner.UI
import com.sensorberg.executioner.Executioner.runOn
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicInteger

class FilePreferences private constructor(private val fileAccess: FileAccess) : SharedPreferences {

	internal var writeCounter: AtomicInteger? = null

	private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

	private val data: JSONObject by lazy {
		val data = fileAccess.loadData()
		if (data != null) {
			JSONObject(data)
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
			Timber.d("Loading ${data.hashCode()} from $fileAccess")
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

	override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
		return if (data.has(key)) {
			val array = data.optJSONArray(key)
			val set = mutableSetOf<String>()
			for (i in 0 until array.length()) {
				set.add(array.getString(i))
			}
			set
		} else defValues
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
		val future = saveToFileAsync(jsonObject)
		updateListeners(changedKeys)
		return future.get()
	}

	internal fun apply(jsonObject: JSONObject, changedKeys: Set<String>) {
		updateData(jsonObject)
		saveToFileAsync(jsonObject)
		updateListeners(changedKeys)
	}

	private fun saveToFileAsync(jsonObject: JSONObject): FutureTask<Boolean> {
		val task = FutureTask<Boolean> {
			try {
				val stringData = jsonObject.toString()
				fileAccess.saveData(stringData)
				writeCounter?.decrementAndGet()
				true
			} catch (e: Exception) {
				Timber.e(e, "Failed to write shared preferences on $fileAccess")
				false
			}
		}
		writeCounter?.incrementAndGet()
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

		private val defaultFactory = object : Factory {

			private val instances = mutableMapOf<File, SharedPreferences>()

			override fun create(file: File): SharedPreferences {
				var instance = instances[file]
				if (instance == null) {
					instance = FilePreferences(RealFileAccess(file))
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
			return create(File(filePath))
		}

		fun create(file: File): SharedPreferences {
			return factory.create(file)
		}

		fun migrate(context: Context, name: String, destination: SharedPreferences) {
			PreferencesMigration.migrate(context, name, destination)
		}
	}

	interface Factory {
		fun create(file: File): SharedPreferences
	}
}