package com.sensorberg.filepreferences

import android.content.Context
import androidx.test.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedPreferencesChecks {

	@Test fun check() {
		val context = InstrumentationRegistry.getTargetContext()
		val p1 = context.getSharedPreferences("test", Context.MODE_PRIVATE)
		val p2 = context.getSharedPreferences("test", Context.MODE_PRIVATE)

		assertEquals(p1, p2)
	}
}