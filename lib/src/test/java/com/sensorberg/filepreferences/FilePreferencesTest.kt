package com.sensorberg.filepreferences

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

/**
 * Those tests were extracted from the source:
 * https://android.googlesource.com/platform/cts/+/master/tests/tests/content/src/android/content/cts/SharedPreferencesTest.java
 */
class FilePreferencesTest {

	private lateinit var test: SharedPreferences
	private lateinit var mPrefsFile: File

	@Before
	fun setup() {
		//mPrefsFile = File(InstrumentationRegistry.getTargetContext().cacheDir, "test-${UUID.randomUUID()}.json")
		mPrefsFile = File.createTempFile("test-${UUID.randomUUID()}", ".json")
		mPrefsFile.delete()
		test = FilePreferences.create(mPrefsFile)
	}

	private fun getPrefs(): SharedPreferences {
		return test
	}

	@Test fun testNoFileInitially() {
		assertFalse(mPrefsFile.exists())
	}

	@Test fun testCommitCreatesFiles() {
		val prefs = getPrefs()
		assertFalse(mPrefsFile.exists())
		prefs.edit().putString("foo", "bar").commit()
		assertTrue(mPrefsFile.exists())
	}

	@Test fun testDefaults() {
		val prefs = getPrefs()
		val key = "not-set"
		assertFalse(prefs.contains(key))
		assertEquals(0, prefs.all.size)
		assertTrue(prefs.all.isEmpty())
		assertEquals(false, prefs.getBoolean(key, false))
		assertEquals(true, prefs.getBoolean(key, true))
		assertEquals(0.5f, prefs.getFloat(key, 0.5f))
		assertEquals(123, prefs.getInt(key, 123))
		assertEquals(999L, prefs.getLong(key, 999L))
		assertEquals("default", prefs.getString(key, "default"))
	}

	@Test fun testPutNullRemovesKey() {
		val prefs = getPrefs()
		prefs.edit().putString("test-key", "test-value").commit()
		assertEquals("test-value", prefs.getString("test-key", null))
		val editor = prefs.edit().putString("test-key", null)
		assertEquals("test-value", prefs.getString("test-key", null))
		editor.commit()
		assertNull(prefs.getString("test-key", null))
		assertFalse(prefs.contains("test-key"))
	}

	private abstract inner class RedundantWriteTest {
		// Do some initial operation on editor.  No commit needed.
		abstract fun setUp(editor: SharedPreferences.Editor)

		// Do some later operation on editor (e.g. a redundant edit).
		// No commit needed.
		abstract fun subsequentEdit(editor: SharedPreferences.Editor)

		open fun expectingMutation(): Boolean {
			return false
		}

		// Tests that a redundant edit after an initital setup doesn't
		// result in a duplicate write-out to disk.
		fun test() {
			val prefs = getPrefs()
			var editor: SharedPreferences.Editor
			assertFalse(mPrefsFile.exists())
			prefs.edit().commit()
			assertTrue(mPrefsFile.exists())
			editor = prefs.edit()
			setUp(editor)
			editor.commit()
			val modtimeMillis1 = mPrefsFile.lastModified()
			// Wait a second and modify the preferences in a dummy,
			// redundant way.  Wish I could inject a clock or disk mock
			// here, but can't.  Instead relying on checking modtime of
			// file on disk.
			Thread.sleep(1000) // ms
			editor = prefs.edit()
			subsequentEdit(editor)
			editor.commit()
			val modtimeMillis2 = mPrefsFile.lastModified()
			assertEquals(expectingMutation(), modtimeMillis1 != modtimeMillis2)
		}
	}

	@Test fun testRedundantBoolean() {
		object : RedundantWriteTest() {
			override fun setUp(editor: SharedPreferences.Editor) {
				editor.putBoolean("foo", true)
			}

			override fun subsequentEdit(editor: SharedPreferences.Editor) {
				editor.putBoolean("foo", true)
			}
		}.test()
	}

	@Test fun testRedundantString() {
		object : RedundantWriteTest() {
			override fun setUp(editor: SharedPreferences.Editor) {
				editor.putString("foo", "bar")
			}

			override fun subsequentEdit(editor: SharedPreferences.Editor) {
				editor.putString("foo", "bar")
			}
		}.test()
	}

	@Test fun testNonRedundantString() {
		object : RedundantWriteTest() {
			override fun setUp(editor: SharedPreferences.Editor) {
				editor.putString("foo", "bar")
			}

			override fun subsequentEdit(editor: SharedPreferences.Editor) {
				editor.putString("foo", "baz")
			}

			override fun expectingMutation(): Boolean {
				return true
			}
		}.test()
	}

	@Test fun testRedundantClear() {
		object : RedundantWriteTest() {
			override fun setUp(editor: SharedPreferences.Editor) {
				editor.clear()
			}

			override fun subsequentEdit(editor: SharedPreferences.Editor) {
				editor.clear()
			}
		}.test()
	}

	@Test fun testNonRedundantClear() {
		object : RedundantWriteTest() {
			override fun setUp(editor: SharedPreferences.Editor) {
				editor.putString("foo", "bar")
			}

			override fun subsequentEdit(editor: SharedPreferences.Editor) {
				editor.clear()
			}

			override fun expectingMutation(): Boolean {
				return true
			}
		}.test()
	}

	@Test fun testRedundantRemove() {
		object : RedundantWriteTest() {
			override fun setUp(editor: SharedPreferences.Editor) {
				editor.putString("foo", "bar")
			}

			override fun subsequentEdit(editor: SharedPreferences.Editor) {
				editor.remove("not-exist-key")
			}
		}.test()
	}

	@Test fun testRedundantCommitWritesFileIfNotAlreadyExisting() {
		val prefs = getPrefs()
		assertFalse(mPrefsFile.exists())
		prefs.edit().putString("foo", "bar").commit()
		assertTrue(mPrefsFile.exists())
		// Delete the file out from under it.  (not sure why this
		// would happen in practice, but perhaps the app did it for
		// some reason...)
		mPrefsFile.delete()
		// And verify that a redundant edit (which would otherwise not
		// write do disk), still does write to disk if the file isn't
		// there.
		prefs.edit().putString("foo", "bar").commit()
		assertTrue(mPrefsFile.exists())
	}

	@Test fun testTorture() {
		val expectedMap = HashMap<String, String>()
		val rand = Random()
		var prefs = FilePreferences.create(mPrefsFile)
		prefs.edit().clear().commit()
		for (i in 0..99) {
			System.out.print("\nLoop: $i")
			prefs = FilePreferences.create(mPrefsFile)
			assertEquals(expectedMap, prefs.all)
			val key = rand.nextInt(25).toString()
			val value = i.toString()
			val editor = prefs.edit()
			if (rand.nextInt(100) < 85) {
				System.out.print("\nSetting $key=$value")
				editor.putString(key, value)
				expectedMap[key] = value
			} else {
				System.out.print("\nRemoving $key")
				editor.remove(key)
				expectedMap.remove(key)
			}
			// Use apply on most, but commit some too.
			if (rand.nextInt(100) < 85) {
				System.out.print("\napply.")
				editor.apply()
			} else {
				System.out.print("\ncommit.")
				editor.commit()
			}
			assertEquals(expectedMap, prefs.all)
		}
	}

	// Checks that an in-memory commit doesn't mutate a data structure
	// still being used while writing out to disk.
	@Test fun testTorture2() {
		val rand = Random()
		for (fi in 0..99) {
			val prefsName = "torture_$fi"
			val prefs = getPrefs()
			prefs.edit().clear().commit()
			val expectedMap = HashMap<String, String>()
			for (applies in 0..2) {
				val editor = prefs.edit()
				for (n in 0..999) {
					val key = Int(rand.nextInt(25)).toString()
					val value = n.toString()
					editor.putString(key, value)
					expectedMap[key] = value
				}
				editor.apply()
			}
			QueuedWork.waitToFinish()
			val clonePrefsName = prefsName + "_clone"
			val prefsFile = mContext.getSharedPrefsFile(prefsName)
			val prefsFileClone = mContext.getSharedPrefsFile(clonePrefsName)
			prefsFileClone.delete()
			try {
				val fos = FileOutputStream(prefsFileClone)
				val fis = FileInputStream(prefsFile)
				val buf = ByteArray(1024)
				var n: Int
				while ((n = fis.read(buf)) > 0) {
					fos.write(buf, 0, n)
				}
				fis.close()
				fos.close()
			} catch (e: IOException) {
			}

			val clonePrefs = mContext.getSharedPreferences(clonePrefsName, Context.MODE_PRIVATE)
			assertEquals(expectedMap, clonePrefs.getAll())
			prefsFile.delete()
			prefsFileClone.delete()
		}
	}

}