package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;

/**
 * Gestiona el modo kiosco: retorno automático al launcher tras X segundos.
 */
public class KioskManager {

    private static final long DEMORA_RETORNO = 30_000;

    private final Activity actividad;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler();
    private final Runnable tareaRetorno;
    private boolean activo;

    public KioskManager(Activity actividad, SharedPreferences prefs) {
        this.actividad = actividad;
        this.prefs = prefs;
        this.activo = prefs.getBoolean("modo_kiosco", false);
        this.tareaRetorno = () -> {
            Intent intent = new Intent(actividad, actividad.getClass());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            actividad.startActivity(intent);
        };
    }

    public boolean isActivo() {
        return activo;
    }

    public void alternar() {
        activo = !activo;
        prefs.edit().putBoolean("modo_kiosco", activo).apply();
    }

    /** Programar retorno al launcher (llamar en onPause si no se abrió app intencionalmente). */
    public void programarRetorno() {
        if (activo) {
            handler.postDelayed(tareaRetorno, DEMORA_RETORNO);
        }
    }

    /** Cancelar retorno programado (llamar en onResume). */
    public void cancelarRetorno() {
        handler.removeCallbacks(tareaRetorno);
    }

    public void destruir() {
        handler.removeCallbacks(tareaRetorno);
    }
}
