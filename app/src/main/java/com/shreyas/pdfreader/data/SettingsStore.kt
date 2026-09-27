package com.shreyas.pdfreader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ReadingMode { PAGED, SCROLL }
enum class FitMode { WIDTH, PAGE }
enum class PageTheme { LIGHT, DARK, SEPIA }

data class ReaderSettings(
    val mode: ReadingMode = ReadingMode.PAGED,
    val fit: FitMode = FitMode.PAGE,
    val theme: PageTheme = PageTheme.LIGHT,
    val keepAwake: Boolean = true,
    val lockOrientation: Boolean = false,
    /** 0..1, or null to follow the system brightness. */
    val brightness: Float? = null,
)

private val Context.dataStore by preferencesDataStore(name = "reader_settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            mode = prefs.enum(MODE, ReadingMode.PAGED),
            fit = prefs.enum(FIT, FitMode.PAGE),
            theme = prefs.enum(THEME, PageTheme.LIGHT),
            keepAwake = prefs[KEEP_AWAKE] ?: true,
            lockOrientation = prefs[LOCK_ORIENTATION] ?: false,
            brightness = prefs[BRIGHTNESS],
        )
    }

    suspend fun update(settings: ReaderSettings) {
        context.dataStore.edit { prefs ->
            prefs[MODE] = settings.mode.name
            prefs[FIT] = settings.fit.name
            prefs[THEME] = settings.theme.name
            prefs[KEEP_AWAKE] = settings.keepAwake
            prefs[LOCK_ORIENTATION] = settings.lockOrientation
            if (settings.brightness == null) prefs.remove(BRIGHTNESS) else prefs[BRIGHTNESS] = settings.brightness
        }
    }

    private inline fun <reified T : Enum<T>> Preferences.enum(key: Preferences.Key<String>, default: T): T =
        this[key]?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val FIT = stringPreferencesKey("fit")
        val THEME = stringPreferencesKey("theme")
        val KEEP_AWAKE = booleanPreferencesKey("keep_awake")
        val LOCK_ORIENTATION = booleanPreferencesKey("lock_orientation")
        val BRIGHTNESS = floatPreferencesKey("brightness")
    }
}
