package com.sensorberg.filepreferences

import android.content.Context
import android.content.SharedPreferences
import androidx.test.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.*

class PreferencesMigrationTest {

	private lateinit var context: Context
	private lateinit var name: String
	private lateinit var destinationFile: File

	@Before fun setup() {
		context = InstrumentationRegistry.getTargetContext()
		name = "prefs-${UUID.randomUUID()}"
		destinationFile = File(context.cacheDir, "prefs-${UUID.randomUUID()}")
	}

	@After fun after() {
		destinationFile.delete()
		context.deleteSharedPreferences(name)
	}

	private fun getContextPrefs(): SharedPreferences {
		return context.getSharedPreferences(name, Context.MODE_PRIVATE)
	}

	private fun getFiletPrefs(): SharedPreferences {
		return FilePreferences.create(destinationFile)
	}

	@Test fun migration_works_for_all_data_types() {
		getFiletPrefs().edit()
			.putBoolean("bool", true)
			.putString("string", "hello world")
			.putFloat("float", 1.234f)
			.putInt("int", 42)
			.putLong("long", 666)
			.putStringSet("set", mutableSetOf("this", "is", "a", "set"))
			.commit()
		val prefs = getFiletPrefs()
		PreferencesMigration.migrate(context, name, prefs)
		assertEquals(true, prefs.getBoolean("bool", false))
		assertEquals("hello world", prefs.getString("string", null))
		assertEquals(1.234f, prefs.getFloat("float", -1f))
		assertEquals(42, prefs.getInt("int", -1))
		assertEquals(666, prefs.getLong("long", -1L))
		assertEquals(mutableSetOf("this", "is", "a", "set"), prefs.getStringSet("set", null))
	}

	@Test fun preference_file_is_deleted_after_migration() {
		getContextPrefs().edit().putBoolean("key", true).commit()
		val file = PreferencesMigration.findPreferencesFile(context, name)!!
		assertTrue(file.exists())
		PreferencesMigration.migrate(context, name, getFiletPrefs())
		assertFalse(file.exists())
	}

	@Test fun context_preference_is_empty_after_migration() {
		getContextPrefs().edit().putBoolean("key", true).commit()
		PreferencesMigration.migrate(context, name, getFiletPrefs())
		assertEquals(0, getContextPrefs().all.size)
	}

	@Test fun find_file_finds_the_preference_file() {
		getContextPrefs().edit().putBoolean("key", true).commit()
		assertNotNull(PreferencesMigration.findPreferencesFile(context, name))
	}

	@Test fun find_recursively_finds_the_preference_file() {
		getContextPrefs().edit().putBoolean("key", true).commit()
		assertNotNull(PreferencesMigration.findPreferencesFileRecursively(context, name))
	}

	@Test fun find_directly_finds_the_preference_file() {
		getContextPrefs().edit().putBoolean("key", true).commit()
		assertNotNull(PreferencesMigration.findPreferencesFileDirectly(context, name))
	}

	@Test fun returns_true_when_there_are_values_to_migrate() {
		getContextPrefs().edit().putBoolean("key", true).commit()
		assertTrue(PreferencesMigration.migrate(context, name, getFiletPrefs()))
	}

	@Test fun returns_false_when_there_are_no_values_to_migrate() {
		getContextPrefs().edit().clear().commit()
		assertFalse(PreferencesMigration.migrate(context, name, getFiletPrefs()))
	}

	@Test fun returns_false_when_preferences_file_doesnt_exist() {
		getContextPrefs().edit().clear().commit()
		PreferencesMigration.findPreferencesFile(context, name)?.delete()
		assertFalse(PreferencesMigration.migrate(context, name, getFiletPrefs()))
	}
}