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

public class AppPickerActivity extends Activity {

    private List<AppInfo> appsNormales;
    private List<AppInfo> appsSistema;
    private List<AppInfo> appList;
    private PickerAdapter adapter;
    private boolean mostrandoSistema = false;
    private TextView btnToggle;
    private TextView tvCargando;
    private GridView grid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        grid = (GridView) findViewById(R.id.picker_grid);
        btnToggle = (TextView) findViewById(R.id.btn_toggle_sistema);
        tvCargando = (TextView) findViewById(R.id.tv_cargando);

        appList = new ArrayList<>();
        adapter = new PickerAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo app = appList.get(position);
            Intent result = new Intent();
            result.putExtra("package_name", app.getPackageName());
            result.putExtra("class_name", app.getClassName());
            result.putExtra("app_label", app.getLabel());
            setResult(RESULT_OK, result);
            finish();
        });

        findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnToggle.setOnClickListener(v -> toggleModo());

        // Cargar apps en background para no bloquear el hilo principal
        tvCargando.setVisibility(View.VISIBLE);
        grid.setVisibility(View.INVISIBLE);
        btnToggle.setEnabled(false);
        new Thread(() -> {
            appsNormales = loadAppsNormales();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                appList = appsNormales;
                adapter.notifyDataSetChanged();
                tvCargando.setVisibility(View.GONE);
                grid.setVisibility(View.VISIBLE);
                btnToggle.setEnabled(true);
            });
        }, "load-apps").start();
    }

    private void toggleModo() {
        if (appsNormales == null) return; // todavía cargando
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
            ((TextView) findViewById(R.id.tv_titulo)).setText("Elegir App");
            appList = appsNormales;
            adapter.notifyDataSetChanged();
        }
    }

    private List<AppInfo> loadAppsNormales() {
        PackageManager pm = getPackageManager();
        List<AppInfo> apps = new ArrayList<>();
        String myPkg = getPackageName();
        Set<String> addedPackages = new HashSet<>();

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

        List<android.content.pm.ApplicationInfo> allInstalled =
                pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (android.content.pm.ApplicationInfo appInfo : allInstalled) {
            if (addedPackages.contains(appInfo.packageName)) continue;
            if (appInfo.packageName.equals(myPkg)) continue;
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

    // Carga actividades exportadas que no aparecen en el launcher normal
    private List<AppInfo> loadAppsSistemaOcultas() {
        PackageManager pm = getPackageManager();
        List<AppInfo> apps = new ArrayList<>();
        String myPkg = getPackageName();

        // Construir set de componentes ya visibles
        Set<String> componentesVisibles = new HashSet<>();
        for (AppInfo a : appsNormales) {
            componentesVisibles.add(a.getPackageName() + "/" + a.getClassName());
            componentesVisibles.add(a.getPackageName()); // para excluir por paquete completo si se desea
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

            for (ActivityInfo actInfo : pkgInfo.activities) {
                if (!actInfo.exported) continue;

                String componente = pkgInfo.packageName + "/" + actInfo.name;
                if (componentesVisibles.contains(componente)) continue;

                // Excluir actividades puramente internas de Android
                if (actInfo.name.contains("$") ) continue;

                AppInfo info = new AppInfo();
                // Label de la actividad; si no tiene propio, usar el del paquete
                CharSequence actLabel = actInfo.loadLabel(pm);
                String appLabel = pkgInfo.applicationInfo.loadLabel(pm).toString();
                String label = (actLabel != null && actLabel.length() > 0)
                        ? actLabel.toString() : appLabel;
                // En el grid mostrar "Nombre · App" si la actividad tiene su propio nombre
                // para ayudar a identificarla, pero guardar solo el label limpio
                String labelDisplay = label.equals(appLabel)
                        ? label : label + " · " + appLabel;
                info.setLabel(label);       // guardado en shortcut: limpio
                info.setDisplayLabel(labelDisplay); // mostrado en grid: con contexto
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

        Collections.sort(apps, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        return apps;
    }

    private class PickerAdapter extends BaseAdapter {
        @Override public int getCount() { return appList.size(); }
        @Override public Object getItem(int position) { return appList.get(position); }
        @Override public long getItemId(int position) { return position; }

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
            // En modo sistema usa getDisplayLabel() ("Nombre · App") para el contexto visual
            label.setText(app.getDisplayLabel());
            return convertView;
        }
    }
}
