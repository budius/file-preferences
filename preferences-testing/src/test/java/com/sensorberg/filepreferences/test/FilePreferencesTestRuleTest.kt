package com.sensorberg.filepreferences.test

import com.sensorberg.filepreferences.FilePreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.*

class FilePreferencesTestRuleTest {

	@get:Rule val filePreferencesTestRule = FilePreferencesTestRule()

	/**
	 * those two values and tests:
	 * - values_are_not_persisted_within_different_tests_1
	 * - values_are_not_persisted_within_different_tests_2
	 * - values_are_persisted_within_same_test
	 * are part of just 1 test.
	 *
	 * SharedPreferences instances are internally cached in a map.
	 * Instances are also cached in a map when the test rule is active,
	 * but with an important difference:
	 * The cache must be cleared between one test and the next.
	 *
	 * That guarantees that within 1 test values are persisted,
	 * even when calling FilePreferences.create again.
	 *
	 * But also guarantees that the tests are isolated
	 * and that values from one test doesn't leak to the other
	 */
	private val not_persisted_within_different_tests = randomFile()
	private val not_persisted_key = "not_persisted_key"

	@Test fun values_are_not_persisted_within_different_tests_1() {
		val prefs = FilePreferences.create(not_persisted_within_different_tests)
		assertNull(prefs.getString(not_persisted_key, null))
		prefs.edit().putString(not_persisted_key, "hello world").commit()
	}

	@Test fun values_are_not_persisted_within_different_tests_2() {
		val prefs = FilePreferences.create(not_persisted_within_different_tests)
		assertNull(prefs.getString(not_persisted_key, null))
		prefs.edit().putString(not_persisted_key, "hello world").commit()
	}

	@Test fun values_are_persisted_within_same_test() {
		val prefsFile = randomFile()
		val prefs1 = FilePreferences.create(prefsFile)
		prefs1.edit().putString("key", "hello world").commit()
		val prefs2 = FilePreferences.create(prefsFile)
		assertEquals("hello world", prefs2.getString("key", null))
	}

	@Test fun shared_preferences_with_test_rule_doesnt_create_a_file() {
		val prefsFile = randomFile()
		val prefs = FilePreferences.create(prefsFile)
		prefs.edit().putBoolean("key", true).commit()
		assertFalse(prefsFile.exists())
	}

	private fun randomFile(): File {
		val file = File.createTempFile("test_rule-${UUID.randomUUID()}", ".json")
		file.delete()
		return file
	}

}