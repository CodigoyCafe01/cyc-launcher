package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Gestiona la actualización del reloj y la fecha en pantalla.
 *
 * Cuando el RTC del headunit arranca sin fecha válida (p. ej. 2006), muestra
 * la última fecha conocida guardada en SharedPreferences en lugar de la fecha
 * incorrecta del sistema. La hora siempre se toma del sistema (el RTC la
 * mantiene). Cada 10 minutos verifica si el sistema ya tiene fecha válida; en
 * ese caso la muestra y la persiste para el próximo arranque.
 */
public class ClockManager {

    private static final long INTERVALO_ACTUALIZACION = 30_000L;
    private static final long INTERVALO_VERIFICACION_FECHA = 10 * 60 * 1_000L;

    // Misma cota que MainActivity para coherencia
    private static final long TIEMPO_MINIMO_VALIDO = 1_767_225_600_000L; // 2026-01-01 UTC

    private static final String PREF_NOMBRE = "alsvin_launcher_prefs";
    private static final String PREF_TIMESTAMP_FECHA = "ultima_fecha_valida_ts";

    private final Activity actividad;
    private final TextView textoReloj;
    private final TextView textoFecha;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private boolean corriendo = false;
    private boolean usandoFechaGuardada = false;

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

    // Verifica periódicamente si el sistema ya tiene fecha válida para dejar
    // de mostrar la fecha del SP y mostrar la real.
    private final Runnable tareaVerificarFecha = new Runnable() {
        @Override
        public void run() {
            if (!corriendo || !usandoFechaGuardada) return;
            if (tiempoValido()) {
                actualizar(); // actualizar() detecta fecha válida y cambia al sistema
            } else {
                handler.postDelayed(this, INTERVALO_VERIFICACION_FECHA);
            }
        }
    };

    public ClockManager(Activity actividad, TextView textoReloj, TextView textoFecha) {
        this.actividad = actividad;
        this.textoReloj = textoReloj;
        this.textoFecha = textoFecha;
        this.prefs = actividad.getSharedPreferences(PREF_NOMBRE, Activity.MODE_PRIVATE);
    }

    public void iniciar() {
        if (corriendo) return;

        corriendo = true;
        actualizar();
        handler.postDelayed(tareaActualizar, INTERVALO_ACTUALIZACION);

        IntentFilter filtro = new IntentFilter();
        filtro.addAction(Intent.ACTION_TIME_CHANGED);
        filtro.addAction(Intent.ACTION_DATE_CHANGED);
        filtro.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        actividad.registerReceiver(receptorCambioTiempo, filtro);
    }

    public void actualizar() {
        if (textoReloj == null || textoFecha == null) return;

        long ahora = System.currentTimeMillis();
        // La hora del sistema es siempre correcta (el RTC la mantiene)
        textoReloj.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ahora)));

        if (tiempoValido()) {
            // El sistema tiene fecha correcta: mostrarla y persistirla para futuros arranques
            textoFecha.setText(
                    new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(new Date(ahora)));
            persistirFecha(ahora);

            if (usandoFechaGuardada) {
                usandoFechaGuardada = false;
                handler.removeCallbacks(tareaVerificarFecha);
            }
        } else {
            // Fecha del sistema inválida: usar la última fecha conocida del SP
            long tsGuardado = prefs.getLong(PREF_TIMESTAMP_FECHA, 0);
            if (tsGuardado > 0) {
                textoFecha.setText(
                        new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
                                .format(new Date(tsGuardado)));
            } else {
                // Sin fecha guardada aún (primera instalación): mostrar lo que haya
                textoFecha.setText(
                        new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
                                .format(new Date(ahora)));
            }

            if (!usandoFechaGuardada) {
                usandoFechaGuardada = true;
                handler.postDelayed(tareaVerificarFecha, INTERVALO_VERIFICACION_FECHA);
            }
        }
    }

    private boolean tiempoValido() {
        return System.currentTimeMillis() >= TIEMPO_MINIMO_VALIDO;
    }

    private void persistirFecha(long timestamp) {
        long guardado = prefs.getLong(PREF_TIMESTAMP_FECHA, 0);
        if (timestamp > guardado) {
            prefs.edit().putLong(PREF_TIMESTAMP_FECHA, timestamp).apply();
        }
    }

    public void detener() {
        corriendo = false;
        handler.removeCallbacks(tareaActualizar);
        handler.removeCallbacks(tareaVerificarFecha);

        try {
            actividad.unregisterReceiver(receptorCambioTiempo);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
