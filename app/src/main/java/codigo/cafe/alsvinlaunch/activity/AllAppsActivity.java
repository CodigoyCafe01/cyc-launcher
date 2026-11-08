package codigo.cafe.alsvinlaunch.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
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
import codigo.cafe.alsvinlaunch.util.LocaleHelper;

public class AllAppsActivity extends Activity {

    private List<AppInfo> appsNormales;
    private List<AppInfo> appsSistema;
    private List<AppInfo> appList;
    private AppsAdapter adapter;
    private boolean mostrandoSistema = false;
    private boolean cargandoSistema = false;
    private TextView tabTodas;
    private TextView tabSistema;
    private TextView tvCargando;
    private GridView grid;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_apps);

        grid     = (GridView) findViewById(R.id.apps_grid);
        tabTodas   = (TextView) findViewById(R.id.tab_todas);
        tabSistema = (TextView) findViewById(R.id.tab_sistema);
        tvCargando = (TextView) findViewById(R.id.tv_cargando);

        // Empezamos con una lista vacía para que el adapter no falle mientras carga.
        appList = new ArrayList<>();

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
        tabTodas.setOnClickListener(v -> seleccionarTab(false));
        tabSistema.setOnClickListener(v -> seleccionarTab(true));
        actualizarTabs();

        // La carga de apps hace muchas llamadas al PackageManager (queryIntentActivities,
        // getLaunchIntentForPackage, loadLabel, loadIcon). Ejecutarla en el hilo principal
        // bloquea la UI y provoca ANR, así que la movemos a un hilo de fondo.
        tvCargando.setVisibility(View.VISIBLE);
        grid.setVisibility(View.INVISIBLE);
        // La pestaña de sistema depende de appsNormales, así que se habilita al terminar.
        tabSistema.setEnabled(false);
        new Thread(() -> {
            List<AppInfo> cargadas = loadAppsNormales();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                appsNormales = cargadas;
                tvCargando.setVisibility(View.GONE);
                grid.setVisibility(View.VISIBLE);
                tabSistema.setEnabled(true);
                if (!mostrandoSistema) {
                    appList = appsNormales;
                    adapter.notifyDataSetChanged();
                }
            });
        }, "load-normal-apps").start();
    }

    private void actualizarTabs() {
        tabTodas.setSelected(!mostrandoSistema);
        tabSistema.setSelected(mostrandoSistema);
    }

    private void seleccionarTab(boolean sistema) {
        if (sistema == mostrandoSistema) return;
        if (sistema && appsNormales == null) return; // aún cargando las apps normales
        mostrandoSistema = sistema;
        actualizarTabs();

        if (!sistema) {
            tvCargando.setVisibility(View.GONE);
            grid.setVisibility(View.VISIBLE);
            appList = appsNormales;
            adapter.notifyDataSetChanged();
            return;
        }

        if (appsSistema != null) {
            appList = appsSistema;
            adapter.notifyDataSetChanged();
            return;
        }

        tvCargando.setVisibility(View.VISIBLE);
        grid.setVisibility(View.INVISIBLE);
        if (cargandoSistema) return;
        cargandoSistema = true;
        new Thread(() -> {
            List<AppInfo> cargadas = loadAppsSistemaOcultas();
            runOnUiThread(() -> {
                cargandoSistema = false;
                if (isFinishing() || isDestroyed()) return;
                appsSistema = cargadas;
                // Si el usuario volvió a "Todas" mientras cargaba, no pisamos su lista.
                if (!mostrandoSistema) return;
                tvCargando.setVisibility(View.GONE);
                grid.setVisibility(View.VISIBLE);
                appList = appsSistema;
                adapter.notifyDataSetChanged();
            });
        }, "load-system-apps").start();
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
