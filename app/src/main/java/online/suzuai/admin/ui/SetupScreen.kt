package online.suzuai.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import online.suzuai.admin.data.Prefs
import online.suzuai.admin.net.SshInstaller

@Composable
fun SetupScreen(
    onConnectExisting: () -> Unit,
    onInstallNew: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("22") }
    var password by rememberSaveable { mutableStateOf("") }

    var connecting by rememberSaveable { mutableStateOf(false) }
    var statusMsg by rememberSaveable { mutableStateOf("") }
    var isError by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        host = Prefs.get(ctx, Prefs.K_VPS_HOST)
        port = Prefs.get(ctx, Prefs.K_VPS_PORT, "22").ifBlank { "22" }
        password = Prefs.get(ctx, Prefs.K_VPS_PASSWORD)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Login ke server
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Login Server", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Masukkan IP dan password root VPS anda. App akan auto connect dan ambil admin token.",
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
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password root") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (statusMsg.isNotBlank()) {
                    Text(
                        statusMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) Color(0xFFF44336) else MaterialTheme.colorScheme.primary,
                    )
                }

                Button(
                    onClick = {
                        connecting = true
                        statusMsg = "Connecting ke $host..."
                        isError = false

                        Prefs.set(ctx, Prefs.K_VPS_HOST, host)
                        Prefs.set(ctx, Prefs.K_VPS_PORT, port)
                        Prefs.set(ctx, Prefs.K_VPS_USER, "root")
                        Prefs.set(ctx, Prefs.K_VPS_PASSWORD, password)

                        scope.launch {
                            // SSH in and grab admin token + domain from .env
                            val result = SshInstaller.runOnce(
                                host = host,
                                port = port.toIntOrNull() ?: 22,
                                user = "root",
                                password = password,
                                command = "grep -E '^(SUZU_ADMIN_TOKEN|SUZU_DOMAIN)=' /var/www/suzu-ai-web/.env 2>/dev/null || echo '__NOT_FOUND__'",
                            )

                            result.onSuccess { output ->
                                if (output.contains("__NOT_FOUND__") || !output.contains("SUZU_ADMIN_TOKEN")) {
                                    // Server belum install — offer install flow
                                    statusMsg = "Suzu AI belum install di server ini."
                                    isError = true
                                    connecting = false
                                    return@launch
                                }

                                var token = ""
                                var domain = ""
                                for (line in output.lines()) {
                                    when {
                                        line.startsWith("SUZU_ADMIN_TOKEN=") ->
                                            token = line.substringAfter("=").trim()
                                        line.startsWith("SUZU_DOMAIN=") ->
                                            domain = line.substringAfter("=").trim()
                                    }
                                }

                                if (token.isBlank()) {
                                    statusMsg = "Admin token kosong dalam .env"
                                    isError = true
                                    connecting = false
                                    return@launch
                                }

                                val serverUrl = if (domain.isNotBlank()) "https://$domain" else "https://$host"
                                Prefs.set(ctx, Prefs.K_ADMIN_TOKEN, token)
                                Prefs.set(ctx, Prefs.K_SERVER_URL, serverUrl)
                                Prefs.set(ctx, "install_domain", domain)
                                Prefs.setBool(ctx, Prefs.K_INSTALL_DONE, true)
                                connecting = false
                                statusMsg = ""
                                onConnectExisting()
                            }

                            result.onFailure { err ->
                                statusMsg = "Gagal connect: ${err.message?.take(150)}"
                                isError = true
                                connecting = false
                            }
                        }
                    },
                    enabled = host.isNotBlank() && password.isNotBlank() && !connecting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (connecting) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Login")
                    }
                }
            }
        }

        // Install baru
        if (statusMsg.contains("belum install") || !connecting) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Server Baru?", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "VPS kosong belum ada Suzu AI? Tekan Install untuk setup dari awal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            Prefs.set(ctx, Prefs.K_VPS_HOST, host)
                            Prefs.set(ctx, Prefs.K_VPS_PORT, port)
                            Prefs.set(ctx, Prefs.K_VPS_USER, "root")
                            Prefs.set(ctx, Prefs.K_VPS_PASSWORD, password)
                            onInstallNew()
                        },
                        enabled = host.isNotBlank() && password.isNotBlank() && !connecting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Install Suzu AI") }
                }
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
