package com.sensorberg.filepreferences

import android.annotation.TargetApi
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.io.File

internal object PreferencesMigration {

	fun migrate(context: Context, name: String, destination: SharedPreferences): Boolean {

		val prefsFile = findPreferencesFile(context, name)
		if (prefsFile?.exists() != true) {
			return false
		}

		var anyValueMigrated = false
		val oldPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
		with(destination.edit()){
			for (entry in oldPrefs.all.entries) {
				anyValueMigrated = true
				val key = entry.key
				val value = entry.value
				Timber.d("Migrating $key from $name preferences")
				when (value) {
					is String -> putString(key, value)
					is Boolean -> putBoolean(key, value)
					is Long -> putLong(key, value)
					is Float -> putFloat(key, value)
					is Int -> putInt(key, value)
					is MutableSet<*> -> putStringSet(key, value as MutableSet<String>)
					null -> throw IllegalArgumentException("Value should never be null")
					else -> throw IllegalArgumentException("Unexpected type ${value::class.java.simpleName}")
				}
			}
			commit()
		}
		oldPrefs.edit().clear().commit()
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			deletePreferencesFileAPI24(context, name)
		}
		prefsFile.delete()
		return anyValueMigrated
	}

	@TargetApi(Build.VERSION_CODES.N)
	private fun deletePreferencesFileAPI24(context: Context, name: String) {
		context.deleteSharedPreferences(name)
	}

	internal fun findPreferencesFile(context: Context, name: String): File? {
		return findPreferencesFileDirectly(context, name) ?: findPreferencesFileRecursively(context, name)
	}

	internal fun findPreferencesFileDirectly(context: Context, name: String): File? {
		val rootDir = ContextCompat.getDataDir(context)
		val folder = File(rootDir, "shared_prefs/")
		val file = File(folder, "$name.xml")
		return if (file.exists()) file else null
	}

	internal fun findPreferencesFileRecursively(context: Context, name: String): File? {
		val rootDir = ContextCompat.getDataDir(context)
		rootDir?.walk(FileWalkDirection.TOP_DOWN)?.forEach {
				if (it.name == "$name.xml") {
					return it
				}
			}
		return null
	}
}