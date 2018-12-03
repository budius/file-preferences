package com.sensorberg.filepreferences

import java.io.*

internal class RealFileAccess(private val file: File) : FileAccess {

	private var cache: String? = null

	override fun loadData(): String? {
		cache = if (file.exists()) {
			getStringFromFile(file)
		} else {
			null
		}
		return cache
	}

	override fun saveData(data: String) {
		if (!file.exists() || data != cache) {
			setStringToFile(file, data)
			cache = data
		}
	}

	override fun toString(): String {
		return "FileAccess to ${file.absolutePath}"
	}

	companion object {
		private fun getStringFromFile(file: File): String {
			var stream: FileInputStream? = null
			var reader: BufferedReader? = null
			val sb = StringBuilder()
			try {
				stream = FileInputStream(file)
				reader = BufferedReader(InputStreamReader(stream))
				var line: String? = reader.readLine()
				while (line != null) {
					sb.append(line)
					line = reader.readLine()
				}
			} finally {
				reader?.close()
				stream?.close()
			}

			return sb.toString()
		}

		private fun setStringToFile(file: File, string: String) {
			var stream: FileOutputStream? = null
			var writer: OutputStreamWriter? = null
			try {
				stream = FileOutputStream(file)
				writer = OutputStreamWriter(stream)
				writer.write(string)
			} finally {
				writer?.close()
				stream?.flush()
				stream?.close()
			}
		}
	}
}