package codigo.cafe.alsvinlaunch.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import java.util.List;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.model.AppInfo;

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
                    intent.setComponent(new ComponentName(app.getPackageName(), app.getClassName()));
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

    private List<AppInfo> loadApps() {
        PackageManager pm = getPackageManager();
        List<AppInfo> apps = new ArrayList<>();
        String myPkg = getPackageName();
        java.util.Set<String> addedPackages = new java.util.HashSet<>();

        // Flag compatible con API 23+
        // MATCH_ALL es API 23, no uses valores de API superiores directamente
        int queryFlags;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            queryFlags = PackageManager.MATCH_ALL;
        } else {
            queryFlags = PackageManager.GET_DISABLED_COMPONENTS;
        }

        // 1. Apps con launcher icon
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherApps = pm.queryIntentActivities(launcherIntent, queryFlags);

        for (ResolveInfo ri : launcherApps) {
            if (ri.activityInfo == null) continue;
            if (ri.activityInfo.packageName.equals(myPkg)) continue;

            AppInfo info = new AppInfo();
            info.setLabel(ri.loadLabel(pm).toString());
            info.setPackageName(ri.activityInfo.packageName);
            info.setClassName(ri.activityInfo.name);
            info.setIcon(ri.loadIcon(pm));
            apps.add(info);
            addedPackages.add(info.getPackageName());
        }

        // 2. Apps instaladas con icono y launch intent (cubre sistema, BT, Settings, etc.)
        int installedFlags = PackageManager.GET_META_DATA;
        // MATCH_UNINSTALLED_PACKAGES se llama GET_UNINSTALLED_PACKAGES en API < 24
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            installedFlags |= PackageManager.MATCH_UNINSTALLED_PACKAGES;
        } else {
            //noinspection deprecation
            installedFlags |= PackageManager.GET_UNINSTALLED_PACKAGES;
        }

        List<android.content.pm.ApplicationInfo> allInstalled =
                pm.getInstalledApplications(installedFlags);

        for (android.content.pm.ApplicationInfo appInfo : allInstalled) {
            if (addedPackages.contains(appInfo.packageName)) continue;
            if (appInfo.packageName.equals(myPkg)) continue;
            if (appInfo.icon == 0) continue;

            Intent launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName);
            if (launchIntent == null) continue;

            ComponentName comp = launchIntent.getComponent();
            AppInfo info = new AppInfo();
            info.setLabel(appInfo.loadLabel(pm).toString());
            info.setPackageName(appInfo.packageName);
            info.setClassName((comp != null) ? comp.getClassName() : "");
            info.setIcon(appInfo.loadIcon(pm));
            apps.add(info);
            addedPackages.add(info.getPackageName());

        }

        Collections.sort(apps, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        return apps;
    }

    private class AppsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return appList.size();
        }

        @Override
        public Object getItem(int position) {
            return appList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(AllAppsActivity.this)
                        .inflate(R.layout.item_app, parent, false);
            }

            AppInfo app = appList.get(position);
            ImageView icon = (ImageView) convertView.findViewById(R.id.app_icon);
            TextView label = (TextView) convertView.findViewById(R.id.app_label);

            icon.setImageDrawable(app.getIcon());
            label.setText(app.getLabel());

            return convertView;
        }
    }


}
