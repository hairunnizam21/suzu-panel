package online.suzuai.admin.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Prefs {
    private const val FILE = "suzu-admin-prefs"

    // VPS credentials (used by SSH installer only; we never send these to the web server)
    const val K_VPS_HOST = "vps_host"
    const val K_VPS_USER = "vps_user"
    const val K_VPS_PASSWORD = "vps_password"
    const val K_VPS_PORT = "vps_port"

    // Suzu AI server admin token + base URL (used by AdminApi for REST calls)
    const val K_SERVER_URL = "server_url"
    const val K_ADMIN_TOKEN = "admin_token"

    // True once installer has reported success on this VPS, so Manage screen unlocks
    const val K_INSTALL_DONE = "install_done"

    private fun prefs(ctx: Context): SharedPreferences {
        val key = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            FILE,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun get(ctx: Context, key: String, default: String = ""): String =
        prefs(ctx).getString(key, default) ?: default

    fun set(ctx: Context, key: String, value: String) {
        prefs(ctx).edit().putString(key, value).apply()
    }

    fun getBool(ctx: Context, key: String, default: Boolean = false): Boolean =
        prefs(ctx).getBoolean(key, default)

    fun setBool(ctx: Context, key: String, value: Boolean) {
        prefs(ctx).edit().putBoolean(key, value).apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
