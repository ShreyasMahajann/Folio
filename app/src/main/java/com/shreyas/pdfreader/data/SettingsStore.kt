package com.shreyas.pdfreader.data

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shreyas.pdfreader.pdf.HighlightColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

enum class ReadingMode { PAGED, SCROLL }
enum class FitMode { WIDTH, PAGE }
enum class PageTheme { LIGHT, SEPIA, DARK }

enum class ReaderFont { SERIF, SANS }

/** Line height of text mode, as a multiple of the font size. */
enum class LineSpacing(val factor: Float) { COMPACT(1.3f), COMFORTABLE(1.5f), SPACIOUS(1.75f) }

/** Space left and right of the text in text mode, in dp. */
enum class PageMargins(val dp: Int) { NARROW(12), STANDARD(20), WIDE(32) }

data class ReaderSettings(
    val mode: ReadingMode = ReadingMode.PAGED,
    val fit: FitMode = FitMode.PAGE,
    val theme: PageTheme = PageTheme.LIGHT,
    val keepAwake: Boolean = true,
    val lockOrientation: Boolean = false,
    /** 0..1, or null to follow the system brightness. */
    val brightness: Float? = null,
    /** Font size of text mode, in sp. One of [TEXT_SIZES]. */
    val textSize: Float = DEFAULT_TEXT_SIZE,
    val font: ReaderFont = ReaderFont.SERIF,
    val spacing: LineSpacing = LineSpacing.COMFORTABLE,
    val margins: PageMargins = PageMargins.STANDARD,
    val justify: Boolean = false,
    /** Color of the highlighter mode. The last color the user picked. */
    val highlighter: HighlightColor = HighlightColor.YELLOW,
) {
    companion object {
        const val DEFAULT_TEXT_SIZE = 18f

        /** A small number of sizes with a clear difference, as on an e-reader. */
        val TEXT_SIZES = listOf(14f, 16f, 18f, 20f, 23f, 26f, 30f)

        /** The size of [TEXT_SIZES] nearest to [size]. Older versions stored 12 to 40 in steps of 2. */
        fun nearestTextSize(size: Float): Float = TEXT_SIZES.minBy { abs(it - size) }
    }
}

data class UpdateState(
    /** Time of the last successful check, in milliseconds. 0 when never checked. */
    val lastCheck: Long,
    val latest: Release?,
    /** The version the user answered "Later" to. */
    val dismissedVersion: String?,
)

private val Context.dataStore by preferencesDataStore(name = "reader_settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            mode = prefs.enum(MODE, ReadingMode.PAGED),
            fit = prefs.enum(FIT, FitMode.PAGE),
            theme = prefs.enum(THEME, systemTheme()),
            keepAwake = prefs[KEEP_AWAKE] ?: true,
            lockOrientation = prefs[LOCK_ORIENTATION] ?: false,
            brightness = prefs[BRIGHTNESS],
            textSize = ReaderSettings.nearestTextSize(prefs[TEXT_SIZE] ?: ReaderSettings.DEFAULT_TEXT_SIZE),
            font = prefs.enum(FONT, ReaderFont.SERIF),
            spacing = prefs.enum(SPACING, LineSpacing.COMFORTABLE),
            margins = prefs.enum(MARGINS, PageMargins.STANDARD),
            justify = prefs[JUSTIFY] ?: false,
            highlighter = prefs.enum(HIGHLIGHTER, HighlightColor.YELLOW),
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
            prefs[TEXT_SIZE] = settings.textSize
            prefs[FONT] = settings.font.name
            prefs[SPACING] = settings.spacing.name
            prefs[MARGINS] = settings.margins.name
            prefs[JUSTIFY] = settings.justify
            prefs[HIGHLIGHTER] = settings.highlighter.name
        }
    }

    /** Past web searches, newest first. */
    val searchHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[SEARCH_HISTORY].orEmpty().split('\n').filter { it.isNotBlank() }
    }

    suspend fun addSearch(query: String) {
        context.dataStore.edit { prefs ->
            val history = prefs[SEARCH_HISTORY].orEmpty().split('\n').filter { it.isNotBlank() }
            prefs[SEARCH_HISTORY] = updatedHistory(history, query).joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it.remove(SEARCH_HISTORY) }
    }

    val updateState: Flow<UpdateState> = context.dataStore.data.map { prefs ->
        val version = prefs[LATEST_VERSION]
        val url = prefs[LATEST_URL]
        UpdateState(
            lastCheck = prefs[LAST_UPDATE_CHECK] ?: 0L,
            latest = if (version != null && url != null) Release(version, url) else null,
            dismissedVersion = prefs[DISMISSED_VERSION],
        )
    }

    suspend fun saveLatestRelease(release: Release, checkedAt: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_UPDATE_CHECK] = checkedAt
            prefs[LATEST_VERSION] = release.version
            prefs[LATEST_URL] = release.url
        }
    }

    suspend fun dismissUpdate(version: String) {
        context.dataStore.edit { it[DISMISSED_VERSION] = version }
    }

    /** The theme until the reader picks one: dark when the phone is in dark mode. */
    private fun systemTheme(): PageTheme {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (night == Configuration.UI_MODE_NIGHT_YES) PageTheme.DARK else PageTheme.LIGHT
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
        val TEXT_SIZE = floatPreferencesKey("text_size")
        val FONT = stringPreferencesKey("font")
        val SPACING = stringPreferencesKey("spacing")
        val MARGINS = stringPreferencesKey("margins")
        val JUSTIFY = booleanPreferencesKey("justify")
        val HIGHLIGHTER = stringPreferencesKey("highlighter_color")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val LATEST_VERSION = stringPreferencesKey("latest_version")
        val LATEST_URL = stringPreferencesKey("latest_url")
        val DISMISSED_VERSION = stringPreferencesKey("dismissed_version")
    }
}
