package tv.rom.youtubebridge;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Hands whatever was meant for YouTube to TizenTube, then finishes. */
public class Bridge extends Activity {
    private static final String TIZENTUBE = "io.gh.reisxd.tizentube.cobalt";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent in = getIntent();
        Intent out = null;
        Uri data = in.getData();
        if (data != null) {
            String link = data.toString();
            if ("vnd.youtube".equals(data.getScheme())) {
                String id = data.getSchemeSpecificPart();
                if (id.startsWith("//")) id = id.substring(2);
                link = "https://www.youtube.com/watch?v=" + id;
            }
            out = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        } else {
            String query = in.getStringExtra(SearchManager.QUERY);
            if (query == null) query = in.getStringExtra("query");
            if (query != null && query.trim().length() > 0) {
                out = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query.trim())));
            }
        }
        if (out != null) {
            out.setPackage(TIZENTUBE);
            out.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(out);
                out = null;
            } catch (RuntimeException e) {
                out = null; // fall through to just opening TizenTube
                open();
            }
        } else {
            open();
        }
        finish();
    }

    private void open() {
        Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(TIZENTUBE);
        if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(TIZENTUBE);
        if (launch == null) return;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(launch);
        } catch (RuntimeException ignored) {
        }
    }
}
