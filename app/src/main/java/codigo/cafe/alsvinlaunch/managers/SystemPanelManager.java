package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.util.ConstantesSistema;

/**
 * Gestiona el panel flotante de apps del sistema.
 */
public class SystemPanelManager {

    private final Activity actividad;
    private final LinearLayout panelSistema;
    private final List<SystemApp> appsSistema;
    private boolean visible = false;

    public interface OnAppOpenListener {
        void onAppOpening();
        void onAppOpenFailed();
    }

    private OnAppOpenListener listener;

    public SystemPanelManager(Activity actividad, LinearLayout panelSistema) {
        this.actividad = actividad;
        this.panelSistema = panelSistema;
        this.appsSistema = new ArrayList<>();
        for (SystemApp app : ConstantesSistema.APPS_PANEL_SISTEMA) {
            appsSistema.add(app);
        }
    }

    public void setOnAppOpenListener(OnAppOpenListener listener) {
        this.listener = listener;
    }

    public boolean isVisible() {
        return visible;
    }

    public void construir() {
        panelSistema.removeAllViews();

        ScrollView scroll = new ScrollView(actividad);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout contenedor = new LinearLayout(actividad);
        contenedor.setOrientation(LinearLayout.VERTICAL);
        contenedor.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        for (final SystemApp app : appsSistema) {
            contenedor.addView(crearElemento(app));
        }

        scroll.addView(contenedor);
        panelSistema.addView(scroll);
    }

    public void alternar() {
        if (visible) {
            ocultar();
        } else {
            panelSistema.setVisibility(View.VISIBLE);
            visible = true;
        }
    }

    public void ocultar() {
        panelSistema.setVisibility(View.GONE);
        visible = false;
    }

    // ── Privados ──

    private LinearLayout crearElemento(final SystemApp app) {
        LinearLayout elemento = new LinearLayout(actividad);
        elemento.setOrientation(LinearLayout.HORIZONTAL);
        elemento.setGravity(Gravity.CENTER_VERTICAL);
        elemento.setBackgroundResource(R.drawable.system_item_bg);
        elemento.setPadding(16, 12, 24, 12);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 4;
        elemento.setLayoutParams(params);

        ImageView icono = new ImageView(actividad);
        icono.setImageResource(app.iconRes);
        LinearLayout.LayoutParams paramIcono = new LinearLayout.LayoutParams(32, 32);
        paramIcono.rightMargin = 16;
        icono.setLayoutParams(paramIcono);

        TextView etiqueta = new TextView(actividad);
        etiqueta.setText(app.label);
        etiqueta.setTextColor(Color.WHITE);
        etiqueta.setTextSize(14);

        elemento.addView(icono);
        elemento.addView(etiqueta);

        elemento.setOnClickListener(v -> {
            ocultar();
            abrirApp(app);
        });

        return elemento;
    }

    private void abrirApp(SystemApp app) {
        try {
            if (listener != null) listener.onAppOpening();
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(app.packageName, app.className));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            for (Map.Entry<String, String> e : app.extras.entrySet()) {
                intent.putExtra(e.getKey(), e.getValue());
            }
            actividad.startActivity(intent);
        } catch (Exception e) {
            if (listener != null) listener.onAppOpenFailed();
            Toast.makeText(actividad, "No se pudo abrir " + app.label, Toast.LENGTH_SHORT).show();
        }
    }
}
