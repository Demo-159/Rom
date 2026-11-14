package app.orchard.tv.system

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.RomFlags
import app.orchard.tv.wallpaper.PhoneServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * ROM "TV": a QR code on Home for the phone remote (rom/remote/tvremote.c, port 8791): the owner lost
 * the box's remote. Shown from Home's start while the remote's page answers, until the first key
 * press of any kind (from the phone page too), so it does not stay over Home once in use.
 */
object PhoneRemoteHint {
    private const val PORT = 8791

    /** A key was pressed since Aerial started: the hint stays hidden. */
    var dismissed by mutableStateOf(false)

    fun onKey() {
        if (!dismissed) dismissed = true
    }

    private fun answers(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 500) }
        true
    }.getOrDefault(false)

    @Composable
    fun Card(modifier: Modifier = Modifier) {
        if (!RomFlags.PHONE_REMOTE || dismissed) return
        val context = LocalContext.current
        var url by remember { mutableStateOf<String?>(null) }
        var qr by remember { mutableStateOf<ImageBitmap?>(null) }
        // The address can come late (Ethernet or Wi-Fi after Home) or change: looked at again.
        LaunchedEffect(Unit) {
            while (!dismissed) {
                val found = withContext(Dispatchers.IO) {
                    if (!answers()) null else PhoneServer.lanAddress(context)?.let { "http://$it:$PORT/" }
                }
                if (found != url) {
                    url = found
                    qr = found?.let { withContext(Dispatchers.Default) { PhoneServer.qr(it, 4).asImageBitmap() } }
                }
                delay(5000)
            }
        }
        val link = url ?: return
        val code = qr ?: return
        Row(
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE6101218))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                code, null,
                Modifier.size(132.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(6.dp),
                filterQuality = FilterQuality.None,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.widthIn(max = 260.dp)) {
                BasicText(stringResource(R.string.phone_remote_title), style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.size(6.dp))
                BasicText(stringResource(R.string.phone_remote_hint), style = TextStyle(color = Color(0xFFB8C0D0), fontSize = 13.sp))
                Spacer(Modifier.size(6.dp))
                BasicText(link, style = TextStyle(color = Color(0xFF8FB4FF), fontSize = 14.sp))
            }
        }
    }
}
