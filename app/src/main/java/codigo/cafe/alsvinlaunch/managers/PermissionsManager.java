package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.provider.Settings;

/**
 * Verifica y solicita los permisos del launcher.
 *
 * Permiso gestionado:
 *  - Notification Listener (widget de música)
 *
 * No hay permisos runtime — CALL_PHONE y READ_CONTACTS fueron eliminados
 * porque ACTION_DIAL no los requiere y el picker nativo maneja READ_CONTACTS.
 */
public class PermissionsManager {

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

    /** Verificar y notificar. Llamar en onResume. */
    public void verificar() {
        if (listener != null) {
            listener.onEstadoPermisos(tieneNotificationListener());
        }
    }

    /** Muestra dialog si falta el Notification Listener. */
    public void mostrarDialogPermisosFaltantes() {
        if (tieneNotificationListener()) return;

        new AlertDialog.Builder(actividad)
                .setTitle("⚠  Permiso necesario")
                .setMessage("Activá el acceso a notificaciones para que " +
                        "el widget de música funcione correctamente.\n\n" +
                        "• Acceso a notificaciones (widget de música)")
                .setPositiveButton("Abrir ajustes", (d, w) -> abrirNotificationSettings())
                .setNegativeButton("Ahora no", null)
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