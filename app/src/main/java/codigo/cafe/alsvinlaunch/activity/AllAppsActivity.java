package codigo.cafe.alsvinlaunch.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.model.AppInfo;

public class AllAppsActivity extends Activity {

    private List<AppInfo> appsNormales;
    private List<AppInfo> appsSistema;
    private List<AppInfo> appList;
    private AppsAdapter adapter;
    private boolean mostrandoSistema = false;
    private TextView btnToggle;
    private TextView tvCargando;
    private GridView grid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_apps);

        grid     = (GridView) findViewById(R.id.apps_grid);
        btnToggle = (TextView) findViewById(R.id.btn_toggle_sistema);
        tvCargando = (TextView) findViewById(R.id.tv_cargando);

        appsNormales = loadAppsNormales();
        appList = appsNormales;

        adapter = new AppsAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo app = appList.get(position);
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(app.getPackageName(), app.getClassName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnToggle.setOnClickListener(v -> toggleModo());
    }

    private void toggleModo() {
        if (!mostrandoSistema) {
            mostrandoSistema = true;
            btnToggle.setText("Normal");
            ((TextView) findViewById(R.id.tv_titulo)).setText("Apps del Sistema");

            if (appsSistema != null) {
                appList = appsSistema;
                adapter.notifyDataSetChanged();
            } else {
                tvCargando.setVisibility(View.VISIBLE);
                grid.setVisibility(View.INVISIBLE);
                btnToggle.setEnabled(false);
                new Thread(() -> {
                    appsSistema = loadAppsSistemaOcultas();
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        tvCargando.setVisibility(View.GONE);
                        grid.setVisibility(View.VISIBLE);
                        btnToggle.setEnabled(true);
                        appList = appsSistema;
                        adapter.notifyDataSetChanged();
                    });
                }, "load-system-apps").start();
            }
        } else {
            mostrandoSistema = false;
            btnToggle.setText("Sistema");
            ((TextView) findViewById(R.id.tv_titulo)).setText("Todas las Apps");
            appList = appsNormales;
            adapter.notifyDataSetChanged();
        }
    }

    private List<AppInfo> loadAppsNormales() {
        PackageManager pm = getPackageManager();
        List<AppInfo> apps = new ArrayList<>();
        String myPkg = getPackageName();
        Set<String> addedPackages = new HashSet<>();

        int queryFlags;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            queryFlags = PackageManager.MATCH_ALL;
        } else {
            queryFlags = PackageManager.GET_DISABLED_COMPONENTS;
        }

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

        int installedFlags = PackageManager.GET_META_DATA;
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

    private List<AppInfo> loadAppsSistemaOcultas() {
        PackageManager pm = getPackageManager();
        List<AppInfo> apps = new ArrayList<>();
        String myPkg = getPackageName();

        Set<String> componentesVisibles = new HashSet<>();
        for (AppInfo a : appsNormales) {
            componentesVisibles.add(a.getPackageName() + "/" + a.getClassName());
        }

        List<PackageInfo> packages;
        try {
            packages = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES);
        } catch (Exception e) {
            return apps;
        }

        for (PackageInfo pkgInfo : packages) {
            if (pkgInfo.packageName.equals(myPkg)) continue;
            if (pkgInfo.activities == null) continue;

            String appLabel = pkgInfo.applicationInfo.loadLabel(pm).toString();

            for (ActivityInfo actInfo : pkgInfo.activities) {
                if (!actInfo.exported) continue;
                if (actInfo.name.contains("$")) continue;

                String componente = pkgInfo.packageName + "/" + actInfo.name;
                if (componentesVisibles.contains(componente)) continue;

                CharSequence actLabel = actInfo.loadLabel(pm);
                String label = (actLabel != null && actLabel.length() > 0)
                        ? actLabel.toString() : appLabel;

                String labelDisplay = label.equals(appLabel)
                        ? label : label + " · " + appLabel;

                AppInfo info = new AppInfo();
                info.setLabel(label);
                info.setDisplayLabel(labelDisplay);
                info.setPackageName(pkgInfo.packageName);
                info.setClassName(actInfo.name);

                try {
                    info.setIcon(actInfo.loadIcon(pm));
                } catch (Exception e) {
                    try {
                        info.setIcon(pkgInfo.applicationInfo.loadIcon(pm));
                    } catch (Exception ex) {
                        info.setIcon(getDrawable(android.R.drawable.sym_def_app_icon));
                    }
                }

                apps.add(info);
                componentesVisibles.add(componente);
            }
        }

        Collections.sort(apps, (a, b) -> a.getDisplayLabel().compareToIgnoreCase(b.getDisplayLabel()));
        return apps;
    }

    private class AppsAdapter extends BaseAdapter {
        @Override public int getCount() { return appList.size(); }
        @Override public Object getItem(int position) { return appList.get(position); }
        @Override public long getItemId(int position) { return position; }

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
            label.setText(app.getDisplayLabel());
            return convertView;
        }
    }
}
