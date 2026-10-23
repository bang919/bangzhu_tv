package com.xiaohei.tv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xiaohei.tv.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val vpnEnabled: Boolean = false,
    val subscriptionName: String = DEFAULT_SUBSCRIPTION_NAME,
    val subscriptionUrl: String = DEFAULT_SUBSCRIPTION_URL,
    val autoStart: Boolean = false,
    val autoConnect: Boolean = false,
    val groupSelections: Map<String, String> = emptyMap(),
)

const val DEFAULT_SUBSCRIPTION_NAME = "iKuuu_V2"
val DEFAULT_SUBSCRIPTION_URL: String = BuildConfig.DEFAULT_SUBSCRIPTION_URL

class SettingsStore(private val context: Context) {
    private val selectionSerializer = MapSerializer(String.serializer(), String.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val VPN_ENABLED = booleanPreferencesKey("vpn_enabled")
        val NAME = stringPreferencesKey("subscription_name")
        val URL = stringPreferencesKey("subscription_url")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val SELECTIONS = stringPreferencesKey("group_selections")
    }

    val data: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            vpnEnabled = prefs[Keys.VPN_ENABLED] ?: false,
            subscriptionName = prefs[Keys.NAME] ?: DEFAULT_SUBSCRIPTION_NAME,
            subscriptionUrl = prefs[Keys.URL] ?: DEFAULT_SUBSCRIPTION_URL,
            autoStart = prefs[Keys.AUTO_START] ?: false,
            autoConnect = prefs[Keys.AUTO_CONNECT] ?: false,
            groupSelections = prefs[Keys.SELECTIONS]?.let { raw ->
                runCatching { json.decodeFromString(selectionSerializer, raw) }.getOrDefault(emptyMap())
            } ?: emptyMap(),
        )
    }

    suspend fun setVpnEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.VPN_ENABLED] = enabled }
    }

    suspend fun setSubscription(name: String, url: String) {
        context.settingsDataStore.edit {
            it[Keys.NAME] = name.trim().ifBlank { DEFAULT_SUBSCRIPTION_NAME }
            it[Keys.URL] = url.trim()
        }
    }

    suspend fun setAutoStart(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_START] = enabled }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_CONNECT] = enabled }
    }

    suspend fun saveSelection(group: String, proxy: String) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.SELECTIONS]?.let { raw ->
                runCatching { json.decodeFromString(selectionSerializer, raw) }.getOrDefault(emptyMap())
            } ?: emptyMap()
            prefs[Keys.SELECTIONS] = json.encodeToString(selectionSerializer, current + (group to proxy))
        }
    }
}
