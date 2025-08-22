package com.example.cellularsocks.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object SettingsKeys {
    val PORT = intPreferencesKey("port")
    val AUTH_ENABLED = booleanPreferencesKey("auth_enabled")
    val USERNAME = stringPreferencesKey("username")
    val PASSWORD = stringPreferencesKey("password")
}

val Context.dataStore by preferencesDataStore(name = "cellularsocks")

data class Settings(
    val port: Int = 1080,
    val authEnabled: Boolean = false,
    val username: String = "",
    val password: String = ""
)

object SettingsRepo {
    fun flow(context: Context): Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            port = p[SettingsKeys.PORT] ?: 1080,
            authEnabled = p[SettingsKeys.AUTH_ENABLED] ?: false,
            username = p[SettingsKeys.USERNAME] ?: "",
            password = p[SettingsKeys.PASSWORD] ?: ""
        )
    }

    suspend fun update(context: Context, block: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val cur = Settings(
                port = p[SettingsKeys.PORT] ?: 1080,
                authEnabled = p[SettingsKeys.AUTH_ENABLED] ?: false,
                username = p[SettingsKeys.USERNAME] ?: "",
                password = p[SettingsKeys.PASSWORD] ?: ""
            )
            val n = block(cur)
            p[SettingsKeys.PORT] = n.port
            p[SettingsKeys.AUTH_ENABLED] = n.authEnabled
            p[SettingsKeys.USERNAME] = n.username
            p[SettingsKeys.PASSWORD] = n.password
        }
    }
} 