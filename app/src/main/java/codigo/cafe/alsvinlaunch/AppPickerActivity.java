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

public class AppPickerActivity extends Activity {

    private List<AllAppsActivity.AppInfo> appList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        GridView grid = (GridView) findViewById(R.id.picker_grid);
        appList = loadApps();

        grid.setAdapter(new PickerAdapter());
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AllAppsActivity.AppInfo app = appList.get(position);
                Intent result = new Intent();
                result.putExtra("package_name", app.packageName);
                result.putExtra("class_name", app.className);
                result.putExtra("app_label", app.label);
                setResult(RESULT_OK, result);
                finish();
            }
        });

        findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
    }

    private List<AllAppsActivity.AppInfo> loadApps() {
        PackageManager pm = getPackageManager();
        List<AllAppsActivity.AppInfo> apps = new ArrayList<>();
        String myPkg = getPackageName();
        java.util.Set<String> addedPackages = new java.util.HashSet<>();

        // 1. Apps con launcher intent (las normales)
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherApps = pm.queryIntentActivities(launcherIntent, 0);

        for (ResolveInfo ri : launcherApps) {
            if (ri.activityInfo.packageName.equals(myPkg)) continue;
            AllAppsActivity.AppInfo info = new AllAppsActivity.AppInfo();
            info.label = ri.loadLabel(pm).toString();
            info.packageName = ri.activityInfo.packageName;
            info.className = ri.activityInfo.name;
            info.icon = ri.loadIcon(pm);
            apps.add(info);
            addedPackages.add(info.packageName);
        }

        // 2. TODAS las apps instaladas (sistema incluidas)
        List<android.content.pm.ApplicationInfo> allInstalled =
                pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (android.content.pm.ApplicationInfo appInfo : allInstalled) {
            if (addedPackages.contains(appInfo.packageName)) continue;
            if (appInfo.packageName.equals(myPkg)) continue;

            // Intentar obtener launch intent
            Intent launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName);
            if (launchIntent != null) {
                ComponentName comp = launchIntent.getComponent();
                AllAppsActivity.AppInfo info = new AllAppsActivity.AppInfo();
                info.label = appInfo.loadLabel(pm).toString();
                info.packageName = appInfo.packageName;
                info.className = comp != null ? comp.getClassName() : "";
                info.icon = appInfo.loadIcon(pm);
                apps.add(info);
                addedPackages.add(info.packageName);
                continue;
            }

            // Si no tiene launch intent, buscar CUALQUIER activity
            try {
                android.content.pm.PackageInfo pkgInfo =
                        pm.getPackageInfo(appInfo.packageName,
                                PackageManager.GET_ACTIVITIES);

                if (pkgInfo.activities != null && pkgInfo.activities.length > 0) {
                    // Tomar la primera activity exportada
                    for (android.content.pm.ActivityInfo actInfo : pkgInfo.activities) {
                        if (actInfo.exported) {
                            AllAppsActivity.AppInfo info = new AllAppsActivity.AppInfo();
                            info.label = appInfo.loadLabel(pm).toString();
                            info.packageName = appInfo.packageName;
                            info.className = actInfo.name;
                            info.icon = appInfo.loadIcon(pm);
                            apps.add(info);
                            addedPackages.add(info.packageName);
                            break;
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                // ignorar
            }
        }

        Collections.sort(apps, new Comparator<AllAppsActivity.AppInfo>() {
            @Override
            public int compare(AllAppsActivity.AppInfo a, AllAppsActivity.AppInfo b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        return apps;
    }

    private class PickerAdapter extends BaseAdapter {
        @Override
        public int getCount() { return appList.size(); }

        @Override
        public Object getItem(int position) { return appList.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(AppPickerActivity.this)
                    .inflate(R.layout.item_app, parent, false);
            }

            AllAppsActivity.AppInfo app = appList.get(position);
            ImageView icon = (ImageView) convertView.findViewById(R.id.app_icon);
            TextView label = (TextView) convertView.findViewById(R.id.app_label);

            icon.setImageDrawable(app.icon);
            label.setText(app.label);

            return convertView;
        }
    }
}
