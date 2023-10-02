package com.elasticrock.exhibition

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class DataStore(private val dataStore: DataStore<Preferences>) {

    private val mediaStoreVersionKey = stringPreferencesKey("mediastore_version")

    suspend fun saveMediaStoreVersion(version: String) {
        try {
            dataStore.edit { preferences ->
                preferences[mediaStoreVersionKey] = version
            }
        } catch (e: IOException) {
            Log.e("DataStore","Error writing previous brightness")
        }
    }

    suspend fun readMediaStoreVersion() : String {
        val mediaStoreVersion: Flow<String> = dataStore.data
            .map { preferences ->
                preferences[mediaStoreVersionKey] ?: ""
            }
        return mediaStoreVersion.first()
    }
}