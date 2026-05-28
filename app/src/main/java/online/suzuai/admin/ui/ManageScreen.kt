package online.suzuai.admin.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.suzuai.admin.data.Prefs
import online.suzuai.admin.net.AdminApi
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ManageScreen(
    snackbar: SnackbarHostState,
    onReinstall: () -> Unit,
    onForgetServer: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val serverUrl = remember { Prefs.get(ctx, Prefs.K_SERVER_URL).ifBlank { "https://${Prefs.get(ctx, "install_domain")}" } }
    var adminToken by rememberSaveable { mutableStateOf(Prefs.get(ctx, Prefs.K_ADMIN_TOKEN)) }
    val api = remember(serverUrl, adminToken) { AdminApi(serverUrl, adminToken) }

    var loading by rememberSaveable { mutableStateOf(false) }
    var config by remember { mutableStateOf<JSONObject?>(null) }
    val users = remember { mutableStateListOf<JSONObject>() }
    val tokens = remember { mutableStateListOf<JSONObject>() }

    // Editable config fields
    var cfgBaseUrl by rememberSaveable { mutableStateOf("") }
    var cfgModel by rememberSaveable { mutableStateOf("") }
    var cfgApiKey by rememberSaveable { mutableStateOf("") }
    var cfgDomain by rememberSaveable { mutableStateOf("") }

    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    suspend fun loadAll() {
        loading = true
        val cfgRes = api.getConfig()
        cfgRes.onSuccess { c ->
            config = c
            cfgBaseUrl = c.optString("base_url")
            cfgModel = c.optString("model")
            cfgDomain = c.optString("domain")
            cfgApiKey = "" // server never returns the live key
        }
        cfgRes.onFailure { toast("Tidak dapat baca config: ${it.message?.take(120)}") }

        val uRes = api.listUsers()
        uRes.onSuccess { arr ->
            users.clear()
            for (i in 0 until arr.length()) users.add(arr.getJSONObject(i))
        }
        uRes.onFailure { toast("Tidak dapat baca senarai user: ${it.message?.take(120)}") }

        val tRes = api.listTokens()
        tRes.onSuccess { arr ->
            tokens.clear()
            for (i in 0 until arr.length()) tokens.add(arr.getJSONObject(i))
        }
        tRes.onFailure { /* token pool may not exist on older servers — ignore silently */ }
        loading = false
    }

    LaunchedEffect(adminToken, serverUrl) {
        if (adminToken.isNotBlank() && serverUrl.isNotBlank()) loadAll()
    }

    // File pickers for Export / Import
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val tmp = File(ctx.cacheDir, "suzu-backup.tmp.zip")
            val res = api.downloadBackup(tmp)
            res.onSuccess { size ->
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        tmp.inputStream().use { it.copyTo(out) }
                    }
                    tmp.delete()
                }
                toast("Backup tersimpan (${size / 1024} KB)")
            }
            res.onFailure { toast("Export gagal: ${it.message?.take(120)}") }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val tmp = File(ctx.cacheDir, "suzu-restore.tmp.zip")
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
            }
            val res = api.uploadRestore(tmp)
            res.onSuccess { j ->
                toast("Restore selesai · ${j.optInt("users")} user dipulihkan")
                tmp.delete()
                loadAll()
            }
            res.onFailure { toast("Restore gagal: ${it.message?.take(120)}") }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Server connection
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Server", style = MaterialTheme.typography.titleSmall)
                Text(serverUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = adminToken,
                    onValueChange = {
                        adminToken = it.trim()
                        Prefs.set(ctx, Prefs.K_ADMIN_TOKEN, adminToken)
                    },
                    label = { Text("Admin token (SUZU_ADMIN_TOKEN)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { scope.launch { loadAll() } },
                        enabled = !loading && adminToken.isNotBlank(),
                    ) { Text(if (loading) "Memuatkan…" else "Refresh") }
                    OutlinedButton(onClick = onReinstall) { Text("Re-install") }
                    OutlinedButton(onClick = onForgetServer) { Text("Lupa server") }
                }
            }
        }

        // Config card
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI Config", style = MaterialTheme.typography.titleSmall)
                Text("Disimpan ke .env di server, panas-reload tanpa restart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = cfgBaseUrl,
                    onValueChange = { cfgBaseUrl = it.trim() },
                    label = { Text("AI_API_BASE_URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cfgModel,
                    onValueChange = { cfgModel = it.trim() },
                    label = { Text("AI_DEFAULT_MODEL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cfgApiKey,
                    onValueChange = { cfgApiKey = it },
                    label = { Text("AI_API_KEY (kosongkan = jangan ubah)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cfgDomain,
                    onValueChange = { cfgDomain = it.trim() },
                    label = { Text("Domain") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        scope.launch {
                            val res = api.setConfig(
                                baseApiUrl = cfgBaseUrl.ifBlank { null },
                                model = cfgModel.ifBlank { null },
                                apiKey = cfgApiKey.ifBlank { null },
                                domain = cfgDomain.ifBlank { null },
                            )
                            res.onSuccess { toast("Config dikemas kini") ; cfgApiKey = "" ; loadAll() }
                            res.onFailure { toast("Gagal: ${it.message?.take(120)}") }
                        }
                    },
                    enabled = !loading,
                ) { Text("Simpan config") }
            }
        }

        // Backup / Restore
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup / Restore", style = MaterialTheme.typography.titleSmall)
                Text("Export semua data user sebagai ZIP. Restore dari ZIP yang anda backup sebelum ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                            exportLauncher.launch("suzu-backup-$stamp.zip")
                        },
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    ) { Text("Export") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    ) { Text("Import") }
                }
            }
        }

        // Tokens (multi-key failover)
        TokenPoolCard(
            tokens = tokens,
            loading = loading,
            onAdd = { name, baseUrl, apiKey, model, priority ->
                scope.launch {
                    api.addToken(name, baseUrl, apiKey, model, priority)
                        .onSuccess { toast("Token ditambah"); loadAll() }
                        .onFailure { toast("Gagal: ${it.message?.take(120)}") }
                }
            },
            onReactivate = { id ->
                scope.launch {
                    api.updateToken(id, status = "active")
                        .onSuccess { toast("Token diaktifkan semula"); loadAll() }
                        .onFailure { toast("Gagal: ${it.message?.take(120)}") }
                }
            },
            onDelete = { id ->
                scope.launch {
                    api.deleteToken(id)
                        .onSuccess { toast("Token dipadam"); loadAll() }
                        .onFailure { toast("Gagal: ${it.message?.take(120)}") }
                }
            },
        )

        // Users
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Users (${users.size})", style = MaterialTheme.typography.titleSmall)
                    if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 4.dp))
                }
                if (users.isEmpty() && !loading) {
                    Text("Belum ada user, atau load gagal.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                for (u in users) {
                    UserRow(
                        u = u,
                        onGrant = { id, dur ->
                            scope.launch {
                                api.grantPremium(id, dur).onSuccess { toast("Premium diberi: $dur") ; loadAll() }
                                    .onFailure { toast("Gagal: ${it.message?.take(120)}") }
                            }
                        },
                        onRevoke = { id ->
                            scope.launch {
                                api.revokePremium(id).onSuccess { toast("Premium dicabut") ; loadAll() }
                                    .onFailure { toast("Gagal: ${it.message?.take(120)}") }
                            }
                        },
                        onResetTokens = { id ->
                            scope.launch {
                                api.resetTokens(id).onSuccess { toast("Token direset") ; loadAll() }
                                    .onFailure { toast("Gagal: ${it.message?.take(120)}") }
                            }
                        },
                        onCopyId = { id ->
                            val cb = ctx.getSystemService(android.content.ClipboardManager::class.java)
                            cb?.setPrimaryClip(android.content.ClipData.newPlainText("user_id", id))
                            toast("UID disalin")
                        },
                    )
                    HorizontalDivider(color = Color(0x22FFFFFF))
                }
            }
        }
    }
}

@Composable
private fun UserRow(
    u: JSONObject,
    onGrant: (id: String, duration: String) -> Unit,
    onRevoke: (id: String) -> Unit,
    onResetTokens: (id: String) -> Unit,
    onCopyId: (id: String) -> Unit,
) {
    val id = u.optString("id")
    val email = u.optString("email")
    val plan = u.optString("plan", "free")
    val used = u.optLong("tokens_used_today", 0L)
    val limit = u.optLong("tokens_limit_daily", 0L)
    val expires = u.optString("plan_expires_at").takeIf { it.isNotBlank() && it != "null" }

    var menuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (plan == "premium") "★ Premium" else "Free",
                        color = if (plan == "premium") Color(0xFFE6B83C) else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(email, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "Tokens $used / $limit" + (expires?.let { " · expires $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { menuOpen = true }) { Text("⋯") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Salin UID") }, onClick = { menuOpen = false; onCopyId(id) })
                DropdownMenuItem(text = { Text("Grant Premium · 1 hari") }, onClick = { menuOpen = false; onGrant(id, "1d") })
                DropdownMenuItem(text = { Text("Grant Premium · 7 hari") }, onClick = { menuOpen = false; onGrant(id, "7d") })
                DropdownMenuItem(text = { Text("Grant Premium · 30 hari") }, onClick = { menuOpen = false; onGrant(id, "30d") })
                DropdownMenuItem(text = { Text("Revoke Premium") }, onClick = { menuOpen = false; onRevoke(id) })
                DropdownMenuItem(text = { Text("Reset tokens hari ini") }, onClick = { menuOpen = false; onResetTokens(id) })
            }
        }
    }
}

// ---------- Token Pool Card ----------

@Composable
private fun TokenPoolCard(
    tokens: List<JSONObject>,
    loading: Boolean,
    onAdd: (name: String, baseUrl: String, apiKey: String, models: String, priority: Int) -> Unit,
    onReactivate: (id: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    var showForm by rememberSaveable { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("API Tokens (${tokens.size})", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    TextButton(onClick = { showForm = !showForm }) {
                        Text(if (showForm) "Tutup" else "+ Add")
                    }
                }
            }

            if (showForm) {
                AddTokenForm(
                    loading = loading,
                    onAdd = { name, baseUrl, apiKey, models, priority ->
                        onAdd(name, baseUrl, apiKey, models, priority)
                        showForm = false
                    },
                    onCancel = { showForm = false },
                )
            }

            if (tokens.isEmpty() && !loading) {
                Text(
                    "Belum ada token. Tekan + Add untuk tambah API key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            for (t in tokens) {
                TokenListItem(t = t, onReactivate = onReactivate, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun AddTokenForm(
    loading: Boolean,
    onAdd: (name: String, baseUrl: String, apiKey: String, models: String, priority: Int) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf("https://core.fiqstr.com/v1") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var models by rememberSaveable { mutableStateOf("fiqstr/claude-opus-4.7-thinking-agentic, fiqstr/gpt-5.5, fiqstr/claude-sonnet-4.6-thinking-agentic") }
    var priority by rememberSaveable { mutableStateOf("100") }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tambah Token Baru", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nama (cth: Fiqstr-1)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it.trim() },
                label = { Text("Base URL") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API Key") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = models, onValueChange = { models = it },
                label = { Text("Models (pisah dengan koma)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 4,
            )
            OutlinedTextField(
                value = priority,
                onValueChange = { priority = it.filter { c -> c.isDigit() } },
                label = { Text("Priority (rendah = utama)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Batal")
                }
                Button(
                    onClick = {
                        if (name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank() && models.isNotBlank()) {
                            onAdd(name.trim(), baseUrl, apiKey, models.trim(), priority.toIntOrNull() ?: 100)
                            name = ""; apiKey = ""
                        }
                    },
                    enabled = !loading && name.isNotBlank() && apiKey.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Tambah") }
            }
        }
    }
}

@Composable
private fun TokenListItem(
    t: JSONObject,
    onReactivate: (id: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    val id = t.optLong("id")
    val name = t.optString("name")
    val status = t.optString("status", "active")
    val model = t.optString("model")
    val maskedKey = t.optString("api_key_masked", "***")
    val failReason = t.optString("failure_reason").takeIf { it.isNotBlank() && it != "null" }

    var expanded by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val statusColor = when (status) {
        "active" -> Color(0xFF4CAF50)
        "exhausted" -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusDot = when (status) {
        "active" -> "\u25CF"
        "exhausted" -> "\u25CF"
        else -> "\u25CB"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16162A)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            // Compact header row — tap to expand
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(statusDot, color = statusColor, style = MaterialTheme.typography.bodySmall)
                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        maskedKey,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Row {
                    Text(
                        if (expanded) "\u25B2" else "\u25BC",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { expanded = !expanded }.padding(horizontal = 4.dp),
                    )
                    TextButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) { Text("⋯") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (status == "exhausted" || status == "disabled") {
                            DropdownMenuItem(
                                text = { Text("Aktifkan semula") },
                                onClick = { menuOpen = false; onReactivate(id) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Padam", color = Color(0xFFF44336)) },
                            onClick = { menuOpen = false; onDelete(id) },
                        )
                    }
                }
            }

            // Expanded details
            if (expanded) {
                Column(
                    Modifier.padding(start = 20.dp, top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val models = model.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    Text("Models:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (m in models) {
                        Text(
                            "  \u2022 $m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (failReason != null) {
                        Text(
                            "Error: $failReason",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF44336),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
