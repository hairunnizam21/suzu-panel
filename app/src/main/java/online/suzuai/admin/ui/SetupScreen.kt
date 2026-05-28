package online.suzuai.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import online.suzuai.admin.data.Prefs

@Composable
fun SetupScreen(
    onConnectExisting: () -> Unit,
    onInstallNew: () -> Unit,
) {
    val ctx = LocalContext.current

    // Connect to existing server
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var adminToken by rememberSaveable { mutableStateOf("") }

    // Install new VPS
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("22") }
    var sshUser by rememberSaveable { mutableStateOf("root") }
    var password by rememberSaveable { mutableStateOf("") }
    var domain by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        serverUrl = Prefs.get(ctx, Prefs.K_SERVER_URL)
        adminToken = Prefs.get(ctx, Prefs.K_ADMIN_TOKEN)
        host = Prefs.get(ctx, Prefs.K_VPS_HOST)
        port = Prefs.get(ctx, Prefs.K_VPS_PORT, "22").ifBlank { "22" }
        sshUser = Prefs.get(ctx, Prefs.K_VPS_USER, "root").ifBlank { "root" }
        password = Prefs.get(ctx, Prefs.K_VPS_PASSWORD)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // -------- Option 1: Connect to existing server --------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connect ke Server", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Server dah siap install? Masukkan URL dan admin token untuk terus ke Manage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it.trim() },
                    label = { Text("Server URL (cth: https://suzu-ai.online)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = adminToken,
                    onValueChange = { adminToken = it.trim() },
                    label = { Text("Admin Token (SUZU_ADMIN_TOKEN)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        Prefs.set(ctx, Prefs.K_SERVER_URL, serverUrl)
                        Prefs.set(ctx, Prefs.K_ADMIN_TOKEN, adminToken)
                        Prefs.setBool(ctx, Prefs.K_INSTALL_DONE, true)
                        onConnectExisting()
                    },
                    enabled = serverUrl.isNotBlank() && adminToken.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Connect & Manage") }
            }
        }

        // Divider
        Text(
            "— atau —",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // -------- Option 2: Install on new VPS --------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Install Server Baru", style = MaterialTheme.typography.titleSmall)
                Text(
                    "VPS kosong? Masukkan SSH credentials, app akan install Suzu AI untuk anda. Config AI (base URL, API key, model) boleh diisi nanti dalam Manage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("IP VPS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Port SSH (default: 22)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sshUser,
                    onValueChange = { sshUser = it.trim() },
                    label = { Text("User SSH") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password root") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it.trim() },
                    label = { Text("Domain (cth: suzu-ai.online)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedButton(
                    onClick = {
                        Prefs.set(ctx, Prefs.K_VPS_HOST, host)
                        Prefs.set(ctx, Prefs.K_VPS_PORT, port)
                        Prefs.set(ctx, Prefs.K_VPS_USER, sshUser.ifBlank { "root" })
                        Prefs.set(ctx, Prefs.K_VPS_PASSWORD, password)
                        Prefs.set(ctx, "install_domain", domain)
                        if (domain.isNotBlank()) {
                            Prefs.set(ctx, Prefs.K_SERVER_URL, "https://$domain")
                        }
                        onInstallNew()
                    },
                    enabled = host.isNotBlank() && password.isNotBlank() && port.toIntOrNull() != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Teruskan ke Install") }
            }
        }

        Text(
            "Kredentian SSH disimpan dalam Android Keystore (encrypted).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
