package com.sensorberg.filepreferences

import com.sensorberg.filepreferences.test.FilePreferencesTestRule
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.*

class FilePreferencesTestRuleTest {

	@get:Rule val filePreferencesTestRule = FilePreferencesTestRule()

	@Test fun shared_preferences_with_test_rule_doesnt_create_a_file() {
		val prefsFile = File.createTempFile("test_rule-${UUID.randomUUID()}", ".json")
		prefsFile.delete()
		val prefs = FilePreferences.create(prefsFile)
		prefs.edit().putBoolean("key", true).commit()
		assertFalse(prefsFile.exists())
	}

}