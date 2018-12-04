package com.sensorberg.filepreferences.test

import android.content.SharedPreferences
import com.sensorberg.filepreferences.FileAccess
import com.sensorberg.filepreferences.FilePreferences
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

class FilePreferencesTestRule : TestWatcher() {

	override fun starting(description: Description?) {
		super.starting(description)
		FilePreferences.setFactory(object : FilePreferences.Factory {

			private val instances = mutableMapOf<File, SharedPreferences>()

			override fun create(file: File): SharedPreferences {
				var value = instances[file]
				if (value == null) {
					value = createNew()
					instances[file] = value
				}
				return value
			}
		})
	}

	override fun finished(description: Description?) {
		FilePreferences.setFactory(null)
		super.finished(description)
	}

	private fun createNew(): SharedPreferences {
		val emptyFileAccess = object : FileAccess {
			override fun loadData(): String? {
				return null
			}

			override fun saveData(data: String) {
				Thread.sleep(22)
			}
		}
		val klazz = FilePreferences::class.java
		val consttructor = klazz.declaredConstructors[0]
		consttructor.isAccessible = true
		return consttructor.newInstance(emptyFileAccess) as SharedPreferences
	}
}