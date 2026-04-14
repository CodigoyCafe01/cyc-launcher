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

public class AppPickerActivity extends Activity {

    private List<AppInfo> appList;

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
                AppInfo app = appList.get(position);
                Intent result = new Intent();
                result.putExtra("package_name", app.getPackageName());
                result.putExtra("class_name", app.getClassName());
                result.putExtra("app_label", app.getLabel());
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

    private List<AppInfo> loadApps() {
        PackageManager pm = getPackageManager();
        List<AppInfo> apps = new ArrayList<>();
        String myPkg = getPackageName();
        java.util.Set<String> addedPackages = new java.util.HashSet<>();

        // 1. Apps normales con launcher
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherApps = pm.queryIntentActivities(launcherIntent, 0);

        for (ResolveInfo ri : launcherApps) {
            if (ri.activityInfo.packageName.equals(myPkg)) continue;

            AppInfo info = new AppInfo();
            info.setLabel(ri.loadLabel(pm).toString());
            info.setPackageName(ri.activityInfo.packageName);
            info.setClassName(ri.activityInfo.name);
            info.setIcon(ri.loadIcon(pm));
            apps.add(info);
            addedPackages.add(info.getPackageName());
        }

        // 2. Apps del sistema con icono real (BT, Settings, etc.)
        List<android.content.pm.ApplicationInfo> allInstalled =
                pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (android.content.pm.ApplicationInfo appInfo : allInstalled) {
            if (addedPackages.contains(appInfo.packageName)) continue;
            if (appInfo.packageName.equals(myPkg)) continue;


            Intent launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName);
            if (launchIntent == null) continue; // ← sin activity lanzable = fuera

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

            AppInfo app = appList.get(position);
            ImageView icon = (ImageView) convertView.findViewById(R.id.app_icon);
            TextView label = (TextView) convertView.findViewById(R.id.app_label);

            icon.setImageDrawable(app.getIcon());
            label.setText(app.getLabel());

            return convertView;
        }
    }
}
