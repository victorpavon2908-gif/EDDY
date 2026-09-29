package com.niko.assistant

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.niko.assistant.smarthome.LocalSmartHomeClient
import com.niko.assistant.ui.theme.NikoTheme

class SmartHomeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NikoTheme {
                val prefs = remember {
                    applicationContext.getSharedPreferences(
                        LocalSmartHomeClient.PREFS_NAME,
                        Context.MODE_PRIVATE,
                    )
                }
                var baseUrl by remember {
                    mutableStateOf(prefs.getString(LocalSmartHomeClient.KEY_BASE_URL, "").orEmpty())
                }
                var token by remember {
                    mutableStateOf(prefs.getString(LocalSmartHomeClient.KEY_TOKEN, "").orEmpty())
                }
                var saved by remember { mutableStateOf(false) }

                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Text(
                            text = "Casa inteligente local",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Conectá NIKO con Home Assistant usando la dirección local y un token de larga duración. Los datos quedan guardados solo en este teléfono.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(18.dp))

                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = {
                                baseUrl = it
                                saved = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("URL local") },
                            placeholder = { Text("http://192.168.1.50:8123") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = token,
                            onValueChange = {
                                token = it
                                saved = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Token de Home Assistant") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )

                        Spacer(Modifier.height(18.dp))

                        Button(
                            onClick = {
                                prefs.edit()
                                    .putString(LocalSmartHomeClient.KEY_BASE_URL, baseUrl.trim())
                                    .putString(LocalSmartHomeClient.KEY_TOKEN, token.trim())
                                    .apply()
                                saved = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Guardar en este teléfono")
                        }

                        if (saved) {
                            Spacer(Modifier.height(12.dp))
                            Text("Listo. Ya podés decir: “NIKO, apagá la luz de la sala”.")
                        }
                    }
                }
            }
        }
    }
}
