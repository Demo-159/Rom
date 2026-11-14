/*
 * ROM "TV": the phone as the remote, without Google and without installing anything on the phone.
 *
 * A virtual input device (Linux uinput) that Android takes for one more USB keyboard/remote, so it
 * works on every screen (setup wizard included), and a small web page on port 8791 whose buttons
 * press its keys. The launcher (Aerial) shows a QR code with the address. Only answers addresses of
 * the local network. Started by init (/vendor/etc/init/tvremote.rc) in its own SELinux domain.
 *
 * Build: rom/remote/build.sh (static, 32-bit ARM: the base runs a 32-bit kernel and userspace).
 */
#include <arpa/inet.h>
#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <netinet/in.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>

#define PORT 8791

static int ui = -1;

static const struct { const char *name; int code; } NAMED[] = {
    {"UP", KEY_UP}, {"DOWN", KEY_DOWN}, {"LEFT", KEY_LEFT}, {"RIGHT", KEY_RIGHT},
    {"OK", KEY_SELECT}, {"ENTER", KEY_ENTER}, {"BACK", KEY_BACK}, {"HOME", KEY_HOMEPAGE},
    {"MENU", KEY_MENU}, {"VOLUP", KEY_VOLUMEUP}, {"VOLDOWN", KEY_VOLUMEDOWN}, {"MUTE", KEY_MUTE},
    {"PLAYPAUSE", KEY_PLAYPAUSE}, {"POWER", KEY_POWER}, {"DEL", KEY_BACKSPACE},
    {"SEARCH", KEY_SEARCH}, {"NEXT", KEY_NEXTSONG}, {"PREV", KEY_PREVIOUSSONG},
};

/* US layout, as Android's Generic.kcm reads an alphabetic keyboard. */
static int char_key(char c, int *shift) {
    static const char *row1 = "1234567890", *row1s = "!@#$%^&*()";
    static const int digits[] = {KEY_1, KEY_2, KEY_3, KEY_4, KEY_5, KEY_6, KEY_7, KEY_8, KEY_9, KEY_0};
    static const int letters[] = {KEY_A, KEY_B, KEY_C, KEY_D, KEY_E, KEY_F, KEY_G, KEY_H, KEY_I, KEY_J,
        KEY_K, KEY_L, KEY_M, KEY_N, KEY_O, KEY_P, KEY_Q, KEY_R, KEY_S, KEY_T, KEY_U, KEY_V, KEY_W,
        KEY_X, KEY_Y, KEY_Z};
    static const struct { char c, s; int code; } sym[] = {
        {'-', '_', KEY_MINUS}, {'=', '+', KEY_EQUAL}, {'[', '{', KEY_LEFTBRACE}, {']', '}', KEY_RIGHTBRACE},
        {'\\', '|', KEY_BACKSLASH}, {';', ':', KEY_SEMICOLON}, {'\'', '"', KEY_APOSTROPHE},
        {',', '<', KEY_COMMA}, {'.', '>', KEY_DOT}, {'/', '?', KEY_SLASH}, {'`', '~', KEY_GRAVE},
    };
    *shift = 0;
    if (c >= 'a' && c <= 'z') return letters[c - 'a'];
    if (c >= 'A' && c <= 'Z') { *shift = 1; return letters[c - 'A']; }
    if (c == ' ') return KEY_SPACE;
    for (int i = 0; i < 10; i++) {
        if (c == row1[i]) return digits[i];
        if (c == row1s[i]) { *shift = 1; return digits[i]; }
    }
    for (size_t i = 0; i < sizeof sym / sizeof sym[0]; i++) {
        if (c == sym[i].c) return sym[i].code;
        if (c == sym[i].s) { *shift = 1; return sym[i].code; }
    }
    return -1;
}

/* struct input_event as the kernel reads it from a process of its own word size (a 32-bit kernel
 * here): a timeval of two longs, which uinput fills in itself. Declared here because newer C library
 * headers lay it out for 64-bit time on 32-bit builds. */
struct kernel_input_event { long sec, usec; unsigned short type, code; int value; };

static void emit(int type, int code, int value) {
    struct kernel_input_event ev;
    memset(&ev, 0, sizeof ev);
    ev.type = type;
    ev.code = code;
    ev.value = value;
    if (write(ui, &ev, sizeof ev) < 0) perror("tvremote: write");
}

static int open_uinput(void);

static void press(int code, int shift) {
    if (ui < 0) ui = open_uinput();   /* not there at start: tried again on every key */
    if (ui < 0) return;
    if (shift) { emit(EV_KEY, KEY_LEFTSHIFT, 1); emit(EV_SYN, SYN_REPORT, 0); }
    emit(EV_KEY, code, 1); emit(EV_SYN, SYN_REPORT, 0);
    emit(EV_KEY, code, 0); emit(EV_SYN, SYN_REPORT, 0);
    if (shift) { emit(EV_KEY, KEY_LEFTSHIFT, 0); emit(EV_SYN, SYN_REPORT, 0); }
    usleep(15000);
}

static int open_uinput(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) fd = open("/dev/input/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { perror("tvremote: uinput"); return -1; }
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    ioctl(fd, UI_SET_EVBIT, EV_SYN);
    for (size_t i = 0; i < sizeof NAMED / sizeof NAMED[0]; i++) ioctl(fd, UI_SET_KEYBIT, NAMED[i].code);
    for (int c = 32; c < 127; c++) { int s, k = char_key((char)c, &s); if (k >= 0) ioctl(fd, UI_SET_KEYBIT, k); }
    ioctl(fd, UI_SET_KEYBIT, KEY_LEFTSHIFT);
    struct uinput_user_dev dev;
    memset(&dev, 0, sizeof dev);
    snprintf(dev.name, UINPUT_MAX_NAME_SIZE, "TV ROM phone remote");
    dev.id.bustype = BUS_VIRTUAL;
    dev.id.vendor = 0x1d6b;
    dev.id.product = 0x7672;
    dev.id.version = 1;
    if (write(fd, &dev, sizeof dev) != sizeof dev || ioctl(fd, UI_DEV_CREATE) < 0) {
        perror("tvremote: create");
        close(fd);
        return -1;
    }
    return fd;
}

static const char PAGE[] =
"<!doctype html><html lang=es><head><meta charset=utf-8>"
"<meta name=viewport content='width=device-width,initial-scale=1,user-scalable=no'>"
"<title>Control TV</title><style>"
"*{box-sizing:border-box;-webkit-tap-highlight-color:transparent;user-select:none}"
"body{margin:0;background:#101218;color:#eef;font-family:system-ui,sans-serif;padding:16px;max-width:460px;margin:auto}"
"h1{font-size:18px;font-weight:600;margin:4px 0 16px;color:#9ab}"
".pad{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:8px 0 18px}"
"button{border:0;border-radius:16px;background:#232736;color:#eef;font-size:18px;padding:0;height:64px;width:100%}"
"button:active{background:#3a4260}"
".ok{background:#3b5bdb;font-weight:700}.ok:active{background:#2f49b0}"
".row{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-bottom:10px}"
".pw{background:#5c1f24}"
"input{width:100%;height:52px;border-radius:14px;border:1px solid #333a50;background:#181b26;color:#fff;font-size:17px;padding:0 14px;margin:10px 0}"
".hint{color:#7a8396;font-size:13px;margin-top:14px}"
"</style></head><body><h1>Control remoto del TV</h1>"
"<div class=pad><span></span><button data-k=UP>&#9650;</button><span></span>"
"<button data-k=LEFT>&#9664;</button><button class=ok data-k=OK>OK</button><button data-k=RIGHT>&#9654;</button>"
"<span></span><button data-k=DOWN>&#9660;</button><span></span></div>"
"<div class=row><button data-k=BACK>&#8617; Atr&aacute;s</button><button data-k=HOME>&#8962; Inicio</button><button data-k=MENU>&#9776; Men&uacute;</button></div>"
"<div class=row><button data-k=VOLDOWN>Vol &minus;</button><button data-k=MUTE>Silencio</button><button data-k=VOLUP>Vol +</button></div>"
"<div class=row><button data-k=PREV>&#9198;</button><button data-k=PLAYPAUSE>&#9199;</button><button data-k=NEXT>&#9197;</button></div>"
"<div class=row><button data-k=SEARCH>&#128269; Buscar</button><button data-k=DEL>&#9003; Borrar</button><button class=pw data-k=POWER>&#9211; Encender</button></div>"
"<input id=t placeholder='Escribir texto (clave Wi-Fi, correo...)' autocomplete=off autocapitalize=off>"
"<div class=row><button id=send style='grid-column:span 2'>Enviar texto</button><button data-k=ENTER>Enter</button></div>"
"<p class=hint>Primero selecciona en la TV el campo donde quieres escribir. Funciona con el celular en el mismo Wi-Fi que el TV.</p>"
"<script>"
"function k(c){fetch('/k?c='+c).catch(function(){});if(navigator.vibrate)navigator.vibrate(15)}"
"document.querySelectorAll('[data-k]').forEach(function(b){b.addEventListener('click',function(){k(b.dataset.k)})});"
"document.getElementById('send').addEventListener('click',function(){var t=document.getElementById('t');"
"if(!t.value)return;fetch('/t?s='+encodeURIComponent(t.value)).catch(function(){});t.value='';if(navigator.vibrate)navigator.vibrate(30)});"
"</script></body></html>";

static int local_peer(struct sockaddr_in *a) {
    unsigned ip = ntohl(a->sin_addr.s_addr);
    return (ip >> 24) == 10 || (ip >> 20) == 0xAC1 || (ip >> 16) == 0xC0A8 || (ip >> 16) == 0xA9FE || (ip >> 24) == 127;
}

static int hexval(int c) {
    if (c >= '0' && c <= '9') return c - '0';
    c = tolower(c);
    return c >= 'a' && c <= 'f' ? c - 'a' + 10 : -1;
}

/* Decodes a %-encoded query value in place (UTF-8 bytes outside ASCII are skipped by char_key). */
static void urldecode(char *s) {
    char *o = s;
    for (; *s; s++) {
        if (*s == '%' && hexval(s[1]) >= 0 && hexval(s[2]) >= 0) { *o++ = (char)(hexval(s[1]) * 16 + hexval(s[2])); s += 2; }
        else if (*s == '+') *o++ = ' ';
        else *o++ = *s;
    }
    *o = 0;
}

static void reply(int c, const char *status, const char *type, const char *body, size_t len) {
    char head[256];
    int n = snprintf(head, sizeof head,
        "HTTP/1.1 %s\r\nContent-Type: %s\r\nContent-Length: %zu\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n",
        status, type, len);
    if (write(c, head, n) < 0) return;
    if (len && write(c, body, len) < 0) return;
}

static void handle(int c) {
    char req[2048];
    struct timeval tv = {3, 0};
    setsockopt(c, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof tv);
    ssize_t n = read(c, req, sizeof req - 1);
    if (n <= 0) return;
    req[n] = 0;
    if (strncmp(req, "GET ", 4)) { reply(c, "405 Method Not Allowed", "text/plain", "", 0); return; }
    char *path = req + 4, *sp = strchr(path, ' ');
    if (sp) *sp = 0;
    if (!strcmp(path, "/") || !strncmp(path, "/?", 2)) {
        reply(c, "200 OK", "text/html; charset=utf-8", PAGE, sizeof PAGE - 1);
    } else if (!strncmp(path, "/k?c=", 5)) {
        for (size_t i = 0; i < sizeof NAMED / sizeof NAMED[0]; i++)
            if (!strcmp(path + 5, NAMED[i].name)) press(NAMED[i].code, 0);
        reply(c, "204 No Content", "text/plain", "", 0);
    } else if (!strncmp(path, "/t?s=", 5)) {
        char *s = path + 5;
        urldecode(s);
        for (int i = 0; s[i] && i < 256; i++) { int sh, k = char_key(s[i], &sh); if (k >= 0) press(k, sh); }
        reply(c, "204 No Content", "text/plain", "", 0);
    } else {
        reply(c, "404 Not Found", "text/plain", "", 0);
    }
}

int main(void) {
    signal(SIGPIPE, SIG_IGN);
    for (int tries = 0; tries < 15 && (ui = open_uinput()) < 0; tries++) sleep(2);
    int s = socket(AF_INET, SOCK_STREAM, 0);
    int one = 1;
    setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &one, sizeof one);
    struct sockaddr_in a;
    memset(&a, 0, sizeof a);
    a.sin_family = AF_INET;
    a.sin_port = htons(PORT);
    a.sin_addr.s_addr = htonl(INADDR_ANY);
    while (bind(s, (struct sockaddr *)&a, sizeof a) < 0) { perror("tvremote: bind"); sleep(5); }
    listen(s, 8);
    fprintf(stderr, "tvremote: listening on %d\n", PORT);
    for (;;) {
        struct sockaddr_in peer;
        socklen_t pl = sizeof peer;
        int c = accept(s, (struct sockaddr *)&peer, &pl);
        if (c < 0) { if (errno != EINTR) usleep(100000); continue; }
        if (local_peer(&peer)) handle(c);
        close(c);
    }
}
