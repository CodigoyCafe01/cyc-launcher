package codigo.cafe.alsvinlaunch;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AllAppsActivity extends Activity {

    private List<AppInfo> appList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_apps);

        GridView grid = (GridView) findViewById(R.id.apps_grid);
        appList = loadApps();

        grid.setAdapter(new AppsAdapter());
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfo app = appList.get(position);
                try {
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName(app.packageName, app.className));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private List<AllAppsActivity.AppInfo> loadApps() {
        PackageManager pm = getPackageManager();
        List<AllAppsActivity.AppInfo> apps = new ArrayList<>();
        String myPkg = getPackageName();

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherApps = pm.queryIntentActivities(launcherIntent, 0);

        for (ResolveInfo ri : launcherApps) {
            if (ri.activityInfo.packageName.equals(myPkg)) continue;
            if (ri.activityInfo.applicationInfo.icon == 0) continue; // ← sin icono = sistema

            AllAppsActivity.AppInfo info = new AllAppsActivity.AppInfo();
            info.label = ri.loadLabel(pm).toString();
            info.packageName = ri.activityInfo.packageName;
            info.className = ri.activityInfo.name;
            info.icon = ri.loadIcon(pm);
            apps.add(info);
        }

        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return apps;
    }

    private class AppsAdapter extends BaseAdapter {
        @Override
        public int getCount() { return appList.size(); }

        @Override
        public Object getItem(int position) { return appList.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(AllAppsActivity.this)
                    .inflate(R.layout.item_app, parent, false);
            }

            AppInfo app = appList.get(position);
            ImageView icon = (ImageView) convertView.findViewById(R.id.app_icon);
            TextView label = (TextView) convertView.findViewById(R.id.app_label);

            icon.setImageDrawable(app.icon);
            label.setText(app.label);

            return convertView;
        }
    }

    static class AppInfo {
        String label;
        String packageName;
        String className;
        Drawable icon;
    }
}
