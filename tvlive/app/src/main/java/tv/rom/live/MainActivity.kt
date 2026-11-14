package tv.rom.live

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * ROM "TV": live TV from m3u8 (HLS) links, the picture stretched over the whole screen.
 * Up/Down (or Channel +/-) change channel; OK, Menu or Left open the list, where the last row adds
 * a channel and a long press on one deletes it. Links opened from other apps play too.
 */
class MainActivity : Activity() {
    private lateinit var channels: Channels
    private lateinit var surface: SurfaceView
    private lateinit var banner: TextView
    private lateinit var status: TextView
    private lateinit var panel: ListView
    private var player: ExoPlayer? = null
    private var current = 0
    /** A link opened from another app, played instead of the list until a channel is chosen. */
    private var external: String? = null
    private val main = Handler(Looper.getMainLooper())
    private val hideBanner = Runnable { banner.visibility = View.GONE }
    private var nowTitle = ""
    private var nowUrl = ""
    /** After an error, the same link again (a live stream that dropped, or the network). */
    private val retry = Runnable { if (nowUrl.isNotEmpty()) playUrl(nowTitle, nowUrl) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        channels = Channels(this)
        current = channels.last
        external = intent?.dataString
        setContentView(layout())
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun layout(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        // The surface fills the screen and the video is scaled to it: stretched, no black bars.
        surface = SurfaceView(this)
        root.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        banner = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = ColorDrawable(0xAA000000.toInt())
            visibility = View.GONE
        }
        root.addView(banner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins(dp(32), dp(24), 0, 0)
        })
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = ColorDrawable(0xAA000000.toInt())
            visibility = View.GONE
        }
        root.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        panel = ListView(this).apply {
            setBackgroundColor(0xE6101018.toInt())
            setPadding(dp(8), dp(16), dp(8), dp(16))
            visibility = View.GONE
            isFocusable = true
            setOnItemClickListener { _, _, position, _ ->
                if (position == channels.all.size) addChannel() else {
                    external = null
                    hidePanel()
                    play(position)
                }
            }
            setOnItemLongClickListener { _, _, position, _ ->
                if (position < channels.all.size) deleteChannel(position)
                true
            }
        }
        root.addView(panel, FrameLayout.LayoutParams(dp(320), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        return root
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val link = intent.dataString ?: return
        external = link
        hidePanel()
        playUrl(getString(R.string.external), link)
    }

    override fun onStart() {
        super.onStart()
        // A player only while on screen: nothing left running (or holding memory) in the background.
        player = ExoPlayer.Builder(this).build().also { p ->
            p.setVideoSurfaceView(surface)
            p.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> showStatus(getString(R.string.loading))
                        Player.STATE_READY -> status.visibility = View.GONE
                        else -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    showStatus(getString(R.string.unavailable))
                    main.removeCallbacks(retry)
                    main.postDelayed(retry, RETRY_MS)
                }
            })
        }
        val link = external
        if (link != null) playUrl(getString(R.string.external), link)
        else if (channels.all.isEmpty()) { showStatus(getString(R.string.empty)); showPanel() }
        else play(current)
    }

    override fun onStop() {
        main.removeCallbacks(retry)
        main.removeCallbacks(hideBanner)
        player?.release()
        player = null
        super.onStop()
    }

    private fun play(index: Int) {
        val list = channels.all
        if (list.isEmpty()) return
        current = Math.floorMod(index, list.size)
        channels.last = current
        val ch = list[current]
        playUrl("${current + 1}  ${ch.name}", ch.url)
    }

    private fun playUrl(title: String, url: String) {
        val p = player ?: return
        main.removeCallbacks(retry)
        nowTitle = title
        nowUrl = url
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
        banner.text = title
        banner.visibility = View.VISIBLE
        main.removeCallbacks(hideBanner)
        main.postDelayed(hideBanner, BANNER_MS)
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    private fun showPanel() {
        val names = channels.all.mapIndexed { i, c -> "${i + 1}  ${c.name}" } + getString(R.string.add)
        panel.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        panel.visibility = View.VISIBLE
        panel.requestFocus()
        panel.setSelection(if (channels.all.isEmpty()) 0 else current)
    }

    private fun hidePanel() {
        panel.visibility = View.GONE
    }

    private fun addChannel() {
        val name = EditText(this).apply {
            hint = getString(R.string.name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
        }
        val url = EditText(this).apply {
            hint = getString(R.string.url_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText("https://")
            setSingleLine()
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(name)
            addView(url)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_title)
            .setView(form)
            .setPositiveButton(R.string.save) { _, _ ->
                val link = url.text.toString().trim()
                if (link.startsWith("http://") || link.startsWith("https://")) {
                    val label = name.text.toString().trim().ifEmpty { getString(R.string.channel_n, channels.all.size + 1) }
                    channels.add(Channel(label, link))
                    external = null
                    hidePanel()
                    play(channels.all.size - 1)
                } else showStatus(getString(R.string.bad_url))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteChannel(index: Int) {
        val ch = channels.all[index]
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_title, ch.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                channels.remove(index)
                if (current >= channels.all.size) current = (channels.all.size - 1).coerceAtLeast(0)
                showPanel()
                if (channels.all.isEmpty()) { player?.stop(); showStatus(getString(R.string.empty)) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (panel.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { hidePanel(); return true }
            return super.onKeyDown(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { external = null; play(current - 1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { external = null; play(current + 1); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DPAD_LEFT -> { showPanel(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val BANNER_MS = 3000L
        private const val RETRY_MS = 5000L
    }
}
