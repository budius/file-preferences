package com.sensorberg.filepreferences

interface FileAccess {
	fun loadData(): String?
	fun saveData(data: String)
}