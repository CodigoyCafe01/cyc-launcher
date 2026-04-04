package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Gestiona la actualización del reloj y la fecha en pantalla.
 * Usa Handler (reloj monotónico) en lugar de Timer (reloj de pared)
 * para evitar congelamientos cuando el sistema corrige la fecha.
 */
public class ClockManager {

    private static final long INTERVALO_ACTUALIZACION = 30_000;

    private final Activity actividad;
    private final TextView textoReloj;
    private final TextView textoFecha;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean corriendo = false;

    /**
     * Receiver que detecta cambios de fecha/hora del sistema
     * y actualiza el reloj inmediatamente.
     */
    private final BroadcastReceiver receptorCambioTiempo = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            actualizar();
        }
    };

    private final Runnable tareaActualizar = new Runnable() {
        @Override
        public void run() {
            if (!corriendo) return;
            actualizar();
            handler.postDelayed(this, INTERVALO_ACTUALIZACION);
        }
    };

    public ClockManager(Activity actividad, TextView textoReloj, TextView textoFecha) {
        this.actividad = actividad;
        this.textoReloj = textoReloj;
        this.textoFecha = textoFecha;
    }

    public void iniciar() {
        if (corriendo) return;

        corriendo = true;
        actualizar();
        handler.postDelayed(tareaActualizar, INTERVALO_ACTUALIZACION);

        // Escuchar cambios de fecha/hora para actualizar de inmediato
        IntentFilter filtro = new IntentFilter();
        filtro.addAction(Intent.ACTION_TIME_CHANGED);
        filtro.addAction(Intent.ACTION_DATE_CHANGED);
        filtro.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        actividad.registerReceiver(receptorCambioTiempo, filtro);
    }

    public void actualizar() {
        if (textoReloj == null || textoFecha == null) return;
        Date ahora = new Date();
        textoReloj.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(ahora));
        textoFecha.setText(new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(ahora));
    }

    public void detener() {
        corriendo = false;
        handler.removeCallbacks(tareaActualizar);

        try {
            actividad.unregisterReceiver(receptorCambioTiempo);
        } catch (IllegalArgumentException ignored) {
            // Receiver no estaba registrado
        }
    }
}
