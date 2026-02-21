package codigo.cafe.alsvinlaunch;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DebugPackagesActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ListView list = new ListView(this);
        list.setBackgroundColor(Color.parseColor("#1A1A2E"));
        list.setPadding(16, 16, 16, 16);
        setContentView(list);

        PackageManager pm = getPackageManager();
        List<String> packages = new ArrayList<>();

        List<PackageInfo> allPkgs = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES);

        for (PackageInfo pkg : allPkgs) {
            String label = pkg.applicationInfo.loadLabel(pm).toString();
            String line = label + "\n" + pkg.packageName;

            // Mostrar activities disponibles
            if (pkg.activities != null) {
                for (int i = 0; i < pkg.activities.length; i++) {
                    line += "\n  → " + pkg.activities[i].name;
                    if (pkg.activities[i].exported) {
                        line += " [EXPORTED]";
                    }
                }
            } else {
                line += "\n  (sin activities)";
            }

            packages.add(line);
        }

        Collections.sort(packages, String.CASE_INSENSITIVE_ORDER);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, packages) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(12);
                tv.setPadding(8, 12, 8, 12);
                return tv;
            }
        };

        list.setAdapter(adapter);
    }
}