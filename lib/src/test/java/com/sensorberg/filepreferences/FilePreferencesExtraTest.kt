package com.sensorberg.filepreferences

import android.content.SharedPreferences
import com.sensorberg.executioner.Executioner.POOL
import com.sensorberg.executioner.Executioner.runOn
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * This file has a couple more tests on top of Android CTS,
 * Those tests were created to complete coverage
 */
class FilePreferencesExtraTest {

	private lateinit var prefs: SharedPreferences
	private lateinit var prefsFile: File
	private val random = Random()

	@Before
	fun setup() {
		prefsFile = File.createTempFile("prefs-${UUID.randomUUID()}", ".json")
		prefsFile.delete()
		prefs = FilePreferences.create(prefsFile)
	}

	@Test fun test_string_set() {
		val key = "test_set"
		val value = mutableSetOf<String>()
		for (i in 0 until 10) value.add("string-${UUID.randomUUID()}")
		assertNull(prefs.getStringSet(key, null))
		prefs.edit().putStringSet(key, value).commit()
		assertEquals(value, prefs.getStringSet(key, null))
		prefs.edit().putStringSet(key, null).commit()
		assertNull(prefs.getStringSet(key, null))
	}

	@Test fun test_long() {
		val key = "test_set"
		val value = random.nextLong()
		assertEquals(Long.MIN_VALUE, prefs.getLong(key, Long.MIN_VALUE))
		prefs.edit().putLong(key, value).commit()
		assertEquals(value, prefs.getLong(key, Long.MIN_VALUE))
	}

	@Test fun test_int() {
		val key = "test_set"
		val value = random.nextInt(Int.MAX_VALUE)
		assertEquals(Int.MIN_VALUE, prefs.getInt(key, Int.MIN_VALUE))
		prefs.edit().putInt(key, value).commit()
		assertEquals(value, prefs.getInt(key, Int.MIN_VALUE))
	}

	@Test fun test_float() {
		val key = "test_set"
		val value = random.nextFloat()
		assertEquals(Float.NaN, prefs.getFloat(key, Float.NaN))
		prefs.edit().putFloat(key, value).commit()
		assertEquals(value, prefs.getFloat(key, Float.NaN))
	}

	@Test fun test_listener() {
		val waitForIt = CountDownLatch(1)
		val key = "test_set"
		val value = UUID.randomUUID().toString()
		prefs.registerOnSharedPreferenceChangeListener { sharedPreferences, receivedKey ->
			assertEquals(key, receivedKey)
			assertEquals(value, sharedPreferences.getString(receivedKey, null))
			waitForIt.countDown()
		}
		prefs.edit().putString(key, value).commit()
		assertTrue(waitForIt.await(1, TimeUnit.SECONDS))
	}

	@Test fun editor_clear_doesnt_crash_concurrent_modification_exception() {
		val expectedChanges = mutableSetOf("1", "3")
		val waitForIt = CountDownLatch(expectedChanges.size)
		prefs.edit().putString("1", "2").putString("3", "4").apply()
		prefs.registerOnSharedPreferenceChangeListener { _, key ->
			if (expectedChanges.contains(key)) {
				expectedChanges.remove(key)
				waitForIt.countDown()
			}
		}
		prefs.edit().clear().apply()
		assertTrue(prefs.all.isEmpty())
		assertTrue(waitForIt.await(1, TimeUnit.SECONDS))
	}

	@Test fun editor_clear_apply_doesnt_freeze() {
		val waitForIt = CountDownLatch(1)
		runOn(POOL) {
			prefs.edit().putString("key", "value").commit()
			prefs.edit().clear().commit()
			prefs.edit().clear().commit()
			waitForIt.countDown()
		}
		assertTrue(waitForIt.await(100, TimeUnit.MILLISECONDS))
	}
}