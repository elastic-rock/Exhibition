package com.elasticrock.exhibition

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme.typography
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.elasticrock.exhibition.ui.theme.ExhibitionTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

val Context.dataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(name = "preferences")

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExhibitionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    ExhibitionApp(dataStore)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ExhibitionApp(dataStore: androidx.datastore.core.DataStore<Preferences>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        "android.permission.READ_MEDIA_IMAGES"
    } else {
        "android.permission.READ_EXTERNAL_STORAGE"
    }
    var isPermissionGranted by remember { mutableIntStateOf(ContextCompat.checkSelfPermission(context, permission)) }
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        isPermissionGranted = if (isGranted) {
            PackageManager.PERMISSION_GRANTED
        } else {
            PackageManager.PERMISSION_DENIED
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = stringResource(R.string.app_name),
            style = typography.displayLarge)
        Button(
            onClick = { requestPermissionLauncher.launch(permission) },
            enabled = isPermissionGranted != PackageManager.PERMISSION_GRANTED
        ) {
            Text(text = if (isPermissionGranted == PackageManager.PERMISSION_DENIED) {
                stringResource(R.string.grant_access_to_storage)
            } else {
                stringResource(R.string.permission_granted)
            }
            )
        }
        Text(text = stringResource(R.string.image_timeout))
        var timeout by remember { mutableIntStateOf(runBlocking { DataStore(dataStore).readTimeoutValue() }) }
        RadioButtonPreference(selected = timeout == 30000, onClick = { timeout = 30000; scope.launch { DataStore(dataStore).saveTimeoutValue(30000) } }, description = pluralStringResource(R.plurals.second, 30, 30))
        RadioButtonPreference(selected = timeout == 20000, onClick = { timeout = 20000; scope.launch { DataStore(dataStore).saveTimeoutValue(20000) } }, description = pluralStringResource(R.plurals.second, 20, 20))
        RadioButtonPreference(selected = timeout == 15000, onClick = { timeout = 15000; scope.launch { DataStore(dataStore).saveTimeoutValue(15000) } }, description = pluralStringResource(R.plurals.second, 15, 15))
        RadioButtonPreference(selected = timeout == 10000, onClick = { timeout = 10000; scope.launch { DataStore(dataStore).saveTimeoutValue(10000) } }, description = pluralStringResource(R.plurals.second, 10, 10))
        RadioButtonPreference(selected = timeout == 5000, onClick = { timeout = 5000; scope.launch { DataStore(dataStore).saveTimeoutValue(5000) } }, description = pluralStringResource(R.plurals.second, 5, 5))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RadioButtonPreference(
    selected: Boolean,
    onClick: (() -> Unit),
    description: String
) {
    Surface(Modifier.clickable { onClick() }) {
        Row {
            RadioButton(
                selected = selected,
                onClick = null
            )
            Text(text = description)
        }
    }
}