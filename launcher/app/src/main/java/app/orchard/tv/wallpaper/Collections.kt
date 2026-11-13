package app.orchard.tv.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import app.orchard.tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random

/**
 * ROM "TV": wallpaper collections. Endless themed pictures (space, ocean, animals, cars…) taken at
 * random from Wikimedia Commons' featured pictures: free to use, credited, and served by Commons at
 * the screen's own width, so each one is a few hundred KB. The owner ticks the themes; the wallpaper
 * moves on to another picture of one of them every [minutes].
 *
 * Kept light on purpose: one picture is shown and the next is fetched ahead, so a change is instant;
 * only these two files are kept. Nothing is fetched while the selection is something else.
 */
object Collections {
    private const val TAG = "TVCollections"
    private const val PREFS = "collections"
    private const val API = "https://commons.wikimedia.org/w/api.php"
    /** Wikimedia asks every client to name itself. */
    private const val AGENT = "TV-Launcher/1.0 (Aerial fork for a TV box ROM)"
    /** Featured pictures: reviewed by Commons for quality, the best of its 100 million files. */
    private const val POOL = "incategory:Featured_pictures_on_Wikimedia_Commons"
    /**
     * Photographs only: Commons' featured pictures also hold paintings, prints, maps and scans,
     * whose paper margins and old colours read badly as a TV wallpaper.
     */
    private const val PHOTOS_ONLY = "-painting -paintings -lithograph -engraving -etching -drawing -illustration " +
        "-print -map -poster -artwork -watercolor -\"oil on canvas\" -scan -manuscript -diagram -stamp -coin " +
        "-incategory:Featured_pictures_of_paintings -incategory:Featured_pictures_of_maps"
    /** CirrusSearch reads no further into results than this. */
    private const val MAX_OFFSET = 9_900
    private const val TOTALS_MS = 3L * 24 * 60 * 60 * 1000

    class Theme(val id: String, val label: Int, val query: String)

    val THEMES = listOf(
        Theme("space", R.string.col_space, "nebula OR galaxy OR \"milky way\" OR \"star trails\" OR planet OR moon"),
        Theme("ocean", R.string.col_ocean, "ocean OR sea OR waves OR underwater OR coral"),
        Theme("animals", R.string.col_animals, "animal OR wildlife OR mammal OR lion OR tiger OR elephant OR deer OR fox"),
        Theme("birds", R.string.col_birds, "bird OR eagle OR owl OR parrot OR hummingbird"),
        Theme("cars", R.string.col_cars, "car OR automobile OR \"sports car\" OR racing"),
        Theme("nature", R.string.col_nature, "landscape OR nature OR valley OR meadow OR lake"),
        Theme("mountains", R.string.col_mountains, "mountain OR alps OR peak OR glacier"),
        Theme("forests", R.string.col_forests, "forest OR woods OR trees"),
        Theme("waterfalls", R.string.col_waterfalls, "waterfall OR cascade"),
        Theme("beaches", R.string.col_beaches, "beach OR coast OR island OR bay"),
        Theme("deserts", R.string.col_deserts, "desert OR dunes OR canyon"),
        Theme("sunsets", R.string.col_sunsets, "sunset OR sunrise OR dusk"),
        Theme("auroras", R.string.col_auroras, "aurora OR \"northern lights\""),
        Theme("snow", R.string.col_snow, "snow OR winter OR ice"),
        Theme("cities", R.string.col_cities, "skyline OR \"city at night\" OR cityscape"),
        Theme("architecture", R.string.col_architecture, "castle OR cathedral OR palace OR bridge"),
        Theme("aircraft", R.string.col_aircraft, "aircraft OR airplane OR airliner OR jet"),
        Theme("trains", R.string.col_trains, "train OR locomotive OR railway"),
        Theme("flowers", R.string.col_flowers, "flowers OR \"flower field\" OR tulips OR lavender"),
    )
    val DEFAULT_THEMES = setOf("space", "ocean", "animals", "nature")
    val MINUTES = listOf(15, 30, 60, 24 * 60)
    const val DEFAULT_MINUTES = 30

    private lateinit var app: Context
    private val prefs: SharedPreferences by lazy { app.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val dir by lazy { File(app.filesDir, "collections").apply { mkdirs() } }
    val current: File get() = File(dir, "current.jpg")
    private val next: File get() = File(dir, "next.jpg")
    private val lock = Mutex()

    var themes by mutableStateOf(DEFAULT_THEMES); private set
    var minutes by mutableStateOf(DEFAULT_MINUTES); private set
    /** "Author · licence", for the screensaver and the card's note. */
    var caption by mutableStateOf<String?>(null); private set

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        themes = prefs.getStringSet("themes", null)?.toSet()?.takeIf { it.isNotEmpty() } ?: DEFAULT_THEMES
        minutes = prefs.getInt("minutes", DEFAULT_MINUTES)
        caption = prefs.getString("caption", null)
    }

    fun toggle(id: String) {
        val now = if (id in themes) themes - id else themes + id
        if (now.isEmpty()) return // at least one theme stays on
        themes = now
        prefs.edit { putStringSet("themes", now) }
        next.delete() // the picture fetched ahead may be of a theme just turned off
    }

    fun chooseMinutes(m: Int) {
        minutes = m
        prefs.edit { putInt("minutes", m) }
    }

    /** Which turn it is: changes every [minutes], in local time. */
    fun slot(): Long {
        val now = System.currentTimeMillis()
        return (now + java.util.TimeZone.getDefault().getOffset(now)) / 60_000L / minutes
    }

    val due: Boolean get() = prefs.getLong("slot", Long.MIN_VALUE) != slot() || !current.exists()

    /** The last pictures shown, kept to go on changing without internet. */
    private val history by lazy { File(dir, "history").apply { mkdirs() } }
    private const val HISTORY_MAX = 20

    /**
     * Moves on to the next picture: the one fetched ahead if there is one, else fetches one now;
     * offline, one of the last [HISTORY_MAX] pictures of the ticked themes. Then fetches the
     * following one ahead. Returns whether [current] changed. Off the main thread.
     */
    suspend fun advance(width: Int): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            var changed = false
            var theme: String? = null
            if (next.exists() && next.renameTo(current)) {
                caption = prefs.getString("next_caption", null)
                theme = prefs.getString("next_theme", null)
                changed = true
            } else fetchInto(current, width)?.let { (c, t) -> caption = c; theme = t; changed = true }
            if (changed) {
                keepInHistory(theme)
            } else if (fromHistory()) {
                changed = true
            }
            if (changed) prefs.edit { putLong("slot", slot()); putString("caption", caption) }
            fetchInto(next, width)?.let { (c, t) -> prefs.edit { putString("next_caption", c); putString("next_theme", t) } }
            changed
        }
    }

    /** Copies [current] into the history, named by time and theme, dropping the oldest past [HISTORY_MAX]. */
    private fun keepInHistory(theme: String?) {
        runCatching {
            val base = "${System.currentTimeMillis()}_${theme ?: "any"}"
            current.copyTo(File(history, "$base.jpg"), overwrite = true)
            File(history, "$base.txt").writeText(caption ?: "")
            history.listFiles { f -> f.name.endsWith(".jpg") }?.sortedByDescending { it.name }?.drop(HISTORY_MAX)?.forEach {
                it.delete(); File(it.path.removeSuffix(".jpg") + ".txt").delete()
            }
        }.onFailure { Log.w(TAG, "could not keep a picture in the history", it) }
    }

    /** Offline: another kept picture, of a ticked theme where there is one. */
    private fun fromHistory(): Boolean = runCatching {
        val all = history.listFiles { f -> f.name.endsWith(".jpg") }?.toList().orEmpty()
        val shown = prefs.getString("history_shown", null)
        val ofThemes = all.filter { it.nameWithoutExtension.substringAfter('_') in themes }
        val pick = (ofThemes.ifEmpty { all }).filter { it.name != shown }.randomOrNull() ?: return false
        pick.copyTo(current, overwrite = true)
        caption = File(pick.path.removeSuffix(".jpg") + ".txt").takeIf { it.exists() }?.readText()?.ifBlank { null }
        prefs.edit { putString("history_shown", pick.name) }
        true
    }.getOrDefault(false)

    /** A random picture of a random ticked theme into [file]; its caption and theme, or null. */
    private fun fetchInto(file: File, width: Int): Pair<String, String>? {
        repeat(3) {
            val theme = THEMES.filter { it.id in themes }.randomOrNull() ?: return null
            runCatching { fetchOne(theme, file, width) }
                .onFailure { Log.w(TAG, "could not fetch a ${theme.id} picture", it) }
                .getOrNull()?.let { return it to theme.id }
        }
        return null
    }

    private fun fetchOne(theme: Theme, file: File, width: Int): String? {
        val search = "$POOL (${theme.query}) filew:>1600 $PHOTOS_ONLY"
        val total = total(theme, search).coerceAtMost(MAX_OFFSET)
        if (total <= 0) return null
        val offset = Random.nextInt(0, maxOf(1, total - 10))
        val url = API + "?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=10" +
            "&gsroffset=$offset&gsrsearch=" + enc(search) +
            "&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&iiextmetadatafilter=Artist%7CLicenseShortName" +
            "&iiurlwidth=$width"
        val pages = JSONObject(get(url)).optJSONObject("query")?.optJSONObject("pages") ?: return null
        val candidates = pages.keys().asSequence().mapNotNull { pages.getJSONObject(it).optJSONArray("imageinfo")?.optJSONObject(0) }
            .filter { info ->
                val w = info.optInt("width"); val h = info.optInt("height")
                h > 0 && w.toFloat() / h in 1.3f..2.4f && info.optString("thumburl").isNotEmpty()
            }.toList()
        val pick = candidates.randomOrNull() ?: return null
        download(pick.getString("thumburl"), file)
        val meta = pick.optJSONObject("extmetadata")
        val artist = meta?.optJSONObject("Artist")?.optString("value")?.let(::plain)?.take(60)?.takeIf { it.isNotBlank() }
        val license = meta?.optJSONObject("LicenseShortName")?.optString("value")?.let(::plain)?.takeIf { it.isNotBlank() }
        return listOfNotNull(artist, license, "Wikimedia Commons").joinToString(" · ")
    }

    /** How many pictures a theme has, asked again every few days. */
    private fun total(theme: Theme, search: String): Int {
        val key = "total2:${theme.id}"
        val at = prefs.getLong("$key:at", 0)
        if (System.currentTimeMillis() - at < TOTALS_MS) prefs.getInt(key, 0).takeIf { it > 0 }?.let { return it }
        val url = "$API?action=query&format=json&list=search&srnamespace=6&srlimit=1&srinfo=totalhits&srprop=&srsearch=" + enc(search)
        val n = JSONObject(get(url)).getJSONObject("query").getJSONObject("searchinfo").getInt("totalhits")
        prefs.edit { putInt(key, n); putLong("$key:at", System.currentTimeMillis()) }
        return n
    }

    private fun plain(html: String) = html.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun connect(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", AGENT)
    }

    private fun get(url: String): String {
        val c = connect(url)
        return try { c.inputStream.use { it.readBytes().decodeToString() } } finally { c.disconnect() }
    }

    private fun download(url: String, to: File) {
        val part = File(to.path + ".part")
        val c = connect(url)
        try {
            c.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            check(part.length() > 10_000) { "too small" }
            to.delete()
            check(part.renameTo(to)) { "rename failed" }
        } finally {
            c.disconnect()
            part.delete()
        }
    }
}
