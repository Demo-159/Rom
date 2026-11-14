package tv.rom.live

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Channel(val name: String, val url: String)

/** The channel list, kept in the app's preferences; MEGA until the owner changes it. */
class Channels(context: Context) {
    private val prefs = context.getSharedPreferences("channels", Context.MODE_PRIVATE)

    var all: List<Channel> = load(); private set

    var last: Int
        get() = prefs.getInt("last", 0).coerceIn(0, (all.size - 1).coerceAtLeast(0))
        set(value) = prefs.edit().putInt("last", value).apply()

    fun add(channel: Channel) = save(all + channel)

    fun remove(index: Int) = save(all.filterIndexed { i, _ -> i != index })

    private fun load(): List<Channel> {
        val text = prefs.getString("list", null) ?: return DEFAULTS
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).map { array.getJSONObject(it) }.map { Channel(it.getString("name"), it.getString("url")) }
        }.getOrDefault(DEFAULTS)
    }

    private fun save(list: List<Channel>) {
        all = list
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs.edit().putString("list", array.toString()).apply()
    }

    companion object {
        val DEFAULTS = listOf(Channel("MEGA", "https://tv-mgmt.gtd.cl/bpk-tv/MEGA/default/index.m3u8"))
    }
}
