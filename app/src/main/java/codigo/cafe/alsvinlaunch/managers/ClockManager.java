package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Gestiona la actualización del reloj y la fecha en pantalla.
 */
public class ClockManager {

    private static final long INTERVALO_ACTUALIZACION = 30_000;

    private final Activity actividad;
    private final TextView textoReloj;
    private final TextView textoFecha;
    private Timer temporizador;

    public ClockManager(Activity actividad, TextView textoReloj, TextView textoFecha) {
        this.actividad = actividad;
        this.textoReloj = textoReloj;
        this.textoFecha = textoFecha;
    }

    public void iniciar() {
        temporizador = new Timer();
        temporizador.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                actividad.runOnUiThread(() -> actualizar());
            }
        }, 0, INTERVALO_ACTUALIZACION);
    }

    public void actualizar() {
        Date ahora = new Date();
        textoReloj.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(ahora));
        textoFecha.setText(new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(ahora));
    }

    public void detener() {
        if (temporizador != null) {
            temporizador.cancel();
            temporizador = null;
        }
    }
}
