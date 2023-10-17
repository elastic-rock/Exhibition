package com.elasticrock.exhibition

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class DataStoreRepository(private val dataStore: DataStore<Preferences>) {

    private val timeoutValueKey = intPreferencesKey("timeout_value")

    suspend fun saveTimeoutValue(timeout: Int) {
        try {
            dataStore.edit { preferences ->
                preferences[timeoutValueKey] = timeout
            }
        } catch (e: IOException) {
            Log.e("DataStore","Error writing timeout value")
        }
    }

    suspend fun readTimeoutValue() : Int {
        val timeoutValue: Flow<Int> = dataStore.data
            .map { preferences ->
                preferences[timeoutValueKey] ?: 10000
            }
        return timeoutValue.first()
    }
}