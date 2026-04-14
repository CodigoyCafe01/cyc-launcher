package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import codigo.cafe.alsvinlaunch.activity.AppPickerActivity;
import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.util.ConstantesSistema;

/**
 * Gestiona la carga, persistencia y apertura de accesos directos.
 */
public class ShortcutManager {

    public static final int SOLICITUD_ELEGIR_APP = 100;
    private static final int CANTIDAD_ACCESOS = 5;
    private static final String[] NOMBRES_PREDEFINIDOS = {"Música", "Navegación", "Teléfono", "Radio", "Ajustes"};

    private static final int[] IDS_ICONOS = {
            R.id.shortcut_icon_0, R.id.shortcut_icon_1, R.id.shortcut_icon_2,
            R.id.shortcut_icon_3, R.id.shortcut_icon_4
    };
    private static final int[] IDS_NOMBRES = {
            R.id.shortcut_name_0, R.id.shortcut_name_1, R.id.shortcut_name_2,
            R.id.shortcut_name_3, R.id.shortcut_name_4
    };

    private final Activity actividad;
    private final SharedPreferences prefs;
    private final LinearLayout[] vistasAccesos = new LinearLayout[CANTIDAD_ACCESOS];
    private int indiceAccesoPendiente = -1;

    public interface OnAppOpenListener {
        void onAppOpening();
        void onAppOpenFailed();
    }

    private OnAppOpenListener listener;

    public ShortcutManager(Activity actividad, SharedPreferences prefs) {
        this.actividad = actividad;
        this.prefs = prefs;
    }

    public void setOnAppOpenListener(OnAppOpenListener listener) {
        this.listener = listener;
    }

    public int getCantidadAccesos() { return CANTIDAD_ACCESOS; }
    public int[] getIdsNombres() { return IDS_NOMBRES; }
    public LinearLayout[] getVistasAccesos() { return vistasAccesos; }

    public void precargarSiNecesario() {
        if (prefs.getBoolean("accesos_precargados", false)) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (int i = 0; i < ConstantesSistema.ACCESOS_PREDEFINIDOS.length && i < CANTIDAD_ACCESOS; i++) {
            editor.putString("acceso_paquete_" + i, ConstantesSistema.ACCESOS_PREDEFINIDOS[i][1]);
            editor.putString("acceso_clase_" + i, ConstantesSistema.ACCESOS_PREDEFINIDOS[i][2]);
            editor.putString("acceso_nombre_" + i, ConstantesSistema.ACCESOS_PREDEFINIDOS[i][0]);
        }
        editor.putBoolean("accesos_precargados", true);
        editor.apply();
    }

    public void configurarVistas(Runnable onBeforeOpen) {
        int[] idsAccesos = {
                R.id.shortcut_0, R.id.shortcut_1, R.id.shortcut_2,
                R.id.shortcut_3, R.id.shortcut_4
        };
        for (int i = 0; i < CANTIDAD_ACCESOS; i++) {
            vistasAccesos[i] = (LinearLayout) actividad.findViewById(idsAccesos[i]);
            final int indice = i;
            vistasAccesos[i].setOnClickListener(v -> {
                if (onBeforeOpen != null) onBeforeOpen.run();
                abrir(indice);
            });
            vistasAccesos[i].setOnLongClickListener(v -> {
                elegirApp(indice);
                return true;
            });
        }
    }

    public void cargar() {
        PackageManager pm = actividad.getPackageManager();
        for (int i = 0; i < CANTIDAD_ACCESOS; i++) {
            String paquete = prefs.getString("acceso_paquete_" + i, null);
            String clase = prefs.getString("acceso_clase_" + i, null);
            String nombre = prefs.getString("acceso_nombre_" + i, null);
            ImageView icono = (ImageView) actividad.findViewById(IDS_ICONOS[i]);
            TextView etiqueta = (TextView) actividad.findViewById(IDS_NOMBRES[i]);
            if (paquete != null && clase != null) {
                try {
                    Drawable iconoApp = pm.getActivityIcon(new ComponentName(paquete, clase));
                    icono.setImageDrawable(iconoApp);
                    etiqueta.setText(nombre != null ? nombre : paquete);
                } catch (PackageManager.NameNotFoundException e) {
                    establecerVacio(icono, etiqueta, i);
                }
            } else {
                establecerVacio(icono, etiqueta, i);
            }
        }
    }

    public void abrir(int indice) {
        String paquete = prefs.getString("acceso_paquete_" + indice, null);
        String clase = prefs.getString("acceso_clase_" + indice, null);
        if (paquete != null && clase != null) {
            try {
                notificarAbriendo();
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(paquete, clase));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                actividad.startActivity(intent);
            } catch (Exception e) {
                notificarFallo();
                Toast.makeText(actividad, "No se pudo abrir la app", Toast.LENGTH_SHORT).show();
            }
        } else {
            elegirApp(indice);
        }
    }

    public void elegirApp(int indice) {
        indiceAccesoPendiente = indice;
        // ← guardar en prefs para sobrevivir destroy
        prefs.edit().putInt("acceso_pendiente_idx", indice).apply();
        notificarAbriendo();
        actividad.startActivityForResult(
                new Intent(actividad, AppPickerActivity.class), SOLICITUD_ELEGIR_APP);
    }

    public void procesarResultado(int requestCode, int resultCode, Intent data) {
        if (requestCode != SOLICITUD_ELEGIR_APP || resultCode != Activity.RESULT_OK || data == null) {
            prefs.edit().remove("acceso_pendiente_idx").apply();
            indiceAccesoPendiente = -1;
            return;
        }

        // ← restaurar índice desde prefs si se perdió por destroy
        if (indiceAccesoPendiente < 0) {
            indiceAccesoPendiente = prefs.getInt("acceso_pendiente_idx", -1);
        }

        String paquete = data.getStringExtra("package_name");
        String clase   = data.getStringExtra("class_name");
        String nombre  = data.getStringExtra("app_label");

        if (indiceAccesoPendiente >= 0 && paquete != null) {
            prefs.edit()
                    .putString("acceso_paquete_" + indiceAccesoPendiente, paquete)
                    .putString("acceso_clase_"   + indiceAccesoPendiente, clase)
                    .putString("acceso_nombre_"  + indiceAccesoPendiente, nombre)
                    .remove("acceso_pendiente_idx")  // ← limpiar
                    .apply();
            cargar();
        }
        indiceAccesoPendiente = -1;
    }

    private void establecerVacio(ImageView icono, TextView etiqueta, int indice) {
        icono.setImageResource(R.drawable.ic_add_shortcut);
        etiqueta.setText(indice < NOMBRES_PREDEFINIDOS.length ? NOMBRES_PREDEFINIDOS[indice] : "Vacío");
    }

    private void notificarAbriendo() {
        if (listener != null) listener.onAppOpening();
    }

    private void notificarFallo() {
        if (listener != null) listener.onAppOpenFailed();
    }
}
