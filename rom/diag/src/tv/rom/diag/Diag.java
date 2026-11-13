package tv.rom.diag;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.IBinder;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * ROM "TV" test builds: shows on the TV, over the boot animation, why Android has not finished
 * starting, so it can be photographed. Run as root by init through app_process; draws through
 * SurfaceFlinger directly (hidden APIs, by reflection), as the system UI is not up yet.
 * Exits once the boot completes.
 */
public final class Diag {
    public static void main(String[] args) throws Exception {
        int[] size = displaySize();
        int w = size[0], h = size[1];
        Class<?> sessionClass = Class.forName("android.view.SurfaceSession");
        Object session = sessionClass.newInstance();
        Class<?> sc = Class.forName("android.view.SurfaceControl");
        Constructor<?> ctor = sc.getConstructor(sessionClass, String.class, int.class, int.class, int.class, int.class);
        Object control = ctor.newInstance(session, "tv-diag", w, h, PixelFormat.OPAQUE, 0);
        sc.getMethod("openTransaction").invoke(null);
        sc.getMethod("setLayer", int.class).invoke(control, 2000000000);
        sc.getMethod("setPosition", float.class, float.class).invoke(control, 0f, 0f);
        sc.getMethod("show").invoke(control);
        sc.getMethod("closeTransaction").invoke(null);
        Constructor<Surface> sctor = Surface.class.getDeclaredConstructor();
        sctor.setAccessible(true);
        Surface surface = sctor.newInstance();
        Surface.class.getMethod("copyFrom", sc).invoke(surface, control);

        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.rgb(255, 193, 69));
        title.setTextSize(h / 26f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(h / 46f);
        text.setTypeface(Typeface.MONOSPACE);
        Paint error = new Paint(text);
        error.setColor(Color.rgb(255, 120, 120));

        int test = args.length > 0 && "--test".equals(args[0]) ? 3 : 0;
        while (test-- > 0 || !"1".equals(prop("sys.boot_completed"))) {
            List<String> lines = new ArrayList<>();
            lines.add("uptime " + run("cat /proc/uptime").trim() + " s   zygote=" + prop("init.svc.zygote")
                    + "  surfaceflinger=" + prop("init.svc.surfaceflinger") + "  bootanim=" + prop("init.svc.bootanim")
                    + "  selinux=" + run("getenforce").trim());
            lines.add("system_server: " + run("pidof system_server").trim() + "   " + prop("ro.build.display.id"));
            lines.add("");
            // What stops a boot: apps and services that crash, and what init says about them.
            for (String l : tail(useful(run("logcat -d -b crash -v brief")), 8)) lines.add("! " + l);
            for (String l : tail(kernel(run("dmesg")), 8)) lines.add("K " + l);
            for (String l : tail(useful(run("logcat -d -v brief *:E")), 20)) lines.add(l);

            Canvas c = surface.lockCanvas(null);
            c.drawColor(Color.rgb(12, 12, 20));
            float y = h / 18f;
            c.drawText("ROM TV - diagnostico de arranque (saca una foto a esta pantalla)", w / 40f, y, title);
            y += h / 22f;
            float step = text.getTextSize() * 1.25f;
            int max = (int) ((h - y - step) / step);
            int from = Math.max(2, lines.size() - (max - 2));
            List<String> shown = new ArrayList<>(lines.subList(0, 2));
            shown.addAll(lines.subList(from, lines.size()));
            for (String l : shown) {
                for (String part : wrap(l, text, w - w / 20f)) {
                    c.drawText(part, w / 40f, y, l.startsWith("!") || l.startsWith("E/") || l.startsWith("F/") || l.startsWith("K") ? error : text);
                    y += step;
                    if (y > h - step / 2) break;
                }
                if (y > h - step / 2) break;
            }
            surface.unlockCanvasAndPost(c);
            Thread.sleep(5000);
        }
        sc.getMethod("openTransaction").invoke(null);
        sc.getMethod("hide").invoke(control);
        sc.getMethod("closeTransaction").invoke(null);
        System.exit(0);
    }

    private static int[] displaySize() {
        try {
            Class<?> sc = Class.forName("android.view.SurfaceControl");
            IBinder display = (IBinder) sc.getMethod("getBuiltInDisplay", int.class).invoke(null, 0);
            Object[] configs = (Object[]) sc.getMethod("getDisplayConfigs", IBinder.class).invoke(null, display);
            int active = (Integer) sc.getMethod("getActiveConfig", IBinder.class).invoke(null, display);
            Object info = configs[Math.max(0, active)];
            Field fw = info.getClass().getField("width"), fh = info.getClass().getField("height");
            return new int[] { fw.getInt(info), fh.getInt(info) };
        } catch (Throwable t) {
            return new int[] { 1280, 720 };
        }
    }

    /** Log lines without the stack frames and "... n more" that follow an exception. */
    private static String useful(String log) {
        StringBuilder sb = new StringBuilder();
        for (String l : log.split("\n")) {
            int at = l.indexOf("): ");
            String msg = (at >= 0 ? l.substring(at + 3) : l).trim();
            if (msg.startsWith("at ") || msg.startsWith("...") || msg.startsWith("\u2026") || msg.startsWith("Caused by")) continue;
            sb.append(l).append('\n');
        }
        return sb.toString();
    }

    /** What init and SELinux report: services that exit or crash, and denials. */
    private static String kernel(String dmesg) {
        StringBuilder sb = new StringBuilder();
        for (String l : dmesg.split("\n")) {
            if ((l.contains("init: ") || l.contains("avc: ")) && !l.toLowerCase().contains("starting service")) sb.append(l).append('\n');
        }
        return sb.toString();
    }

    private static List<String> wrap(String line, Paint paint, float width) {
        List<String> out = new ArrayList<>();
        String rest = line;
        while (rest.length() > 0) {
            int n = paint.breakText(rest, true, width, null);
            if (n <= 0) break;
            out.add(rest.substring(0, n));
            rest = rest.substring(n);
            if (out.size() >= 3) break;
        }
        if (out.isEmpty()) out.add("");
        return out;
    }

    private static List<String> tail(String s, int n) {
        List<String> out = new ArrayList<>();
        for (String l : s.split("\n")) {
            if (l.trim().isEmpty() || l.startsWith("---------")) continue;
            out.add(l);
        }
        return out.subList(Math.max(0, out.size() - n), out.size());
    }

    private static String prop(String key) {
        try {
            Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
            return (String) get.invoke(null, key);
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String run(String cmd) {
        StringBuilder sb = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[] { "/system/bin/sh", "-c", cmd });
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            p.waitFor();
        } catch (Throwable t) {
            sb.append(t.toString());
        }
        return sb.toString();
    }
}
