package codigo.cafe.alsvinlaunch.managers;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import codigo.cafe.alsvinlaunch.R;

public class PermissionsManager {

    public static final int REQUEST_LOCATION = 1001;

    private final Activity actividad;
    private OnPermisosListener listener;
    private Runnable onAbrirAjustes;

    public interface OnPermisosListener {
        void onEstadoPermisos(boolean todosActivos);
    }

    public PermissionsManager(Activity actividad) {
        this.actividad = actividad;
    }

    public void setOnPermisosListener(OnPermisosListener listener) {
        this.listener = listener;
    }

    public void setOnAbrirAjustesListener(Runnable r) {
        this.onAbrirAjustes = r;
    }

    // ──────────────────────────────────────────────
    //  API pública
    // ──────────────────────────────────────────────

    public void verificar() {
        // Solicitar ubicación si no está concedido (necesario para SSID WiFi)
        if (!tieneUbicacion()) {
            ActivityCompat.requestPermissions(actividad,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION);
        }

        if (listener != null) {
            listener.onEstadoPermisos(tieneNotificationListener());
        }
    }

    public void mostrarDialogPermisosFaltantes() {
        if (tieneNotificationListener()) return;

        new AlertDialog.Builder(actividad)
                .setTitle(R.string.perm_titulo)
                .setMessage(R.string.perm_mensaje)
                .setPositiveButton(R.string.btn_abrir_ajustes, (d, w) -> abrirNotificationSettings())
                .setNegativeButton(R.string.btn_ahora_no, null)
                .show();
    }

    // ──────────────────────────────────────────────
    //  Verificación
    // ──────────────────────────────────────────────

    public boolean tieneNotificationListener() {
        String activos = Settings.Secure.getString(
                actividad.getContentResolver(), "enabled_notification_listeners");
        return activos != null && activos.contains(actividad.getPackageName());
    }

    public boolean tieneUbicacion() {
        return ContextCompat.checkSelfPermission(actividad,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ──────────────────────────────────────────────
    //  Privados
    // ──────────────────────────────────────────────

    private void abrirNotificationSettings() {
        if (onAbrirAjustes != null) onAbrirAjustes.run();
        try {
            actividad.startActivity(
                    new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        } catch (Exception e) {
            try {
                actividad.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            } catch (Exception ignored) {}
        }
    }
}