package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.ProxyProfile

@Composable
fun ProfileEditDialog(
    profile: ProxyProfile?,
    onDismiss: () -> Unit,
    onSave: (ProxyProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var uuid by remember { mutableStateOf(profile?.uuid ?: "") }
    var workerUrl by remember { mutableStateOf(profile?.workerUrl ?: "") }
    var cleanIp by remember { mutableStateOf(profile?.cleanIp ?: "") }
    var localPortStr by remember { mutableStateOf(profile?.localPort?.toString() ?: "40446") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (profile == null) "New Proxy Profile" else "Edit Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_name"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uuid,
                    onValueChange = { uuid = it },
                    label = { Text("UUID (Key)") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    singleLine = true,
                    placeholder = { Text("e.g. 63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_uuid"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = workerUrl,
                    onValueChange = { workerUrl = it },
                    label = { Text("Worker URL / Host") },
                    leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) },
                    singleLine = true,
                    placeholder = { Text("e.g. twilight-dream-9e3c.workers.dev") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_worker_url"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = cleanIp,
                    onValueChange = { cleanIp = it },
                    label = { Text("Clean IP (Optional)") },
                    leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    singleLine = true,
                    placeholder = { Text("e.g. 172.67.70.244") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_clean_ip"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = localPortStr,
                    onValueChange = { localPortStr = it },
                    label = { Text("SOCKS5 Listen Port") },
                    leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("40446") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_port"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Profile name cannot be blank."
                        return@Button
                    }
                    if (uuid.isBlank()) {
                        errorMessage = "UUID cannot be blank."
                        return@Button
                    }
                    if (workerUrl.isBlank()) {
                        errorMessage = "Worker host cannot be blank."
                        return@Button
                    }
                    val port = localPortStr.toIntOrNull() ?: 40446
                    if (port !in 1024..65535) {
                        errorMessage = "Port must be between 1024 and 65535."
                        return@Button
                    }

                    val updated = (profile ?: ProxyProfile(
                        name = name.trim(),
                        uuid = uuid.trim(),
                        workerUrl = workerUrl.trim(),
                        cleanIp = cleanIp.trim(),
                        localPort = port
                    )).copy(
                        name = name.trim(),
                        uuid = uuid.trim(),
                        workerUrl = workerUrl.trim(),
                        cleanIp = cleanIp.trim(),
                        localPort = port
                    )
                    onSave(updated)
                },
                modifier = Modifier.testTag("save_profile_button")
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_profile_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
