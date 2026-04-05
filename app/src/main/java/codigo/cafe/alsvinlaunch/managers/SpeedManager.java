package codigo.cafe.alsvinlaunch.managers;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import codigo.cafe.alsvinlaunch.R;

/**
 * SpeedManager — obtiene velocidad por GPS y actualiza el TextView speed_text.
 * <p>
 * Ciclo de vida esperado:
 * onCreate  → new SpeedManager(...)
 * onStart   → iniciar()
 * onStop    → detener()
 * onDestroy → destruir()
 * <p>
 * Si no hay permiso ACCESS_FINE_LOCATION o el GPS no está disponible,
 * el widget se oculta automáticamente (GONE).
 */
public class SpeedManager {

    // Actualizar cada 1 segundo, mínimo 0 metros de desplazamiento
    private static final long INTERVALO_MS = 1000;
    private static final float DISTANCIA_MIN = 0f;

    // Por debajo de este umbral se muestra 0 (filtra ruido GPS en reposo)
    private static final float UMBRAL_MS = 0.5f; // ~1.8 km/h

    private final Activity actividad;
    private final TextView vistaVelocidad;
    private LocationManager locationManager;
    private LocationListener listener;
    private boolean activo = false;

    private final View contenedorVelocidad;  // LinearLayout speed_text

    public SpeedManager(Activity actividad) {
        this.actividad = actividad;
        this.contenedorVelocidad = actividad.findViewById(R.id.speed_text);
        this.vistaVelocidad = actividad.findViewById(R.id.speed_number);
    }

    // ── Ciclo de vida ──────────────────────────────────────────────

    public void iniciar() {
        if (!tienePermiso()) {
            ocultarWidget();
            return;
        }

        locationManager = (LocationManager) actividad.getSystemService(
                android.content.Context.LOCATION_SERVICE);

        if (locationManager == null
                || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            ocultarWidget();
            return;
        }

        mostrarWidget();

        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                actualizarVelocidad(location);
            }

            // Android 6 requiere estos métodos aunque estén vacíos
            @Override
            public void onStatusChanged(String p, int s, Bundle e) {
            }

            @Override
            public void onProviderEnabled(String provider) {
                mostrarWidget();
            }

            @Override
            public void onProviderDisabled(String provider) {
                ocultarWidget();
            }
        };

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    INTERVALO_MS,
                    DISTANCIA_MIN,
                    listener);
            activo = true;
        } catch (SecurityException e) {
            ocultarWidget();
        }
    }

    public void detener() {
        if (locationManager != null && listener != null) {
            try {
                locationManager.removeUpdates(listener);
            } catch (Exception ignored) {
            }
        }
        activo = false;
    }

    public void destruir() {
        detener();
        locationManager = null;
        listener = null;
    }

    // ── Visibilidad pública (bloqueo de pantalla) ──────────────────

    /**
     * Oculta el widget sin detener el GPS.
     */
    public void ocultar() {
        ocultarWidget();
    }

    /**
     * Restaura el widget solo si el GPS está activo y hay velocidad > 0.
     * Si el GPS no estaba corriendo, llama a iniciar() para arrancarlo.
     */
    public void mostrar() {
        if (activo) {
            // GPS ya corre — la próxima actualización de ubicación
            // decidirá sola si mostrar o no según la velocidad
        } else {
            iniciar();
        }
    }

    // ── Lógica interna ─────────────────────────────────────────────

    private void actualizarVelocidad(Location location) {
        if (vistaVelocidad == null) return;

        if (location.hasSpeed()) {
            float ms = location.getSpeed();
            int kmh = (ms < UMBRAL_MS) ? 0 : Math.round(ms * 3.6f);
            if (kmh == 0) {
                ocultarWidget();
            } else {
                vistaVelocidad.setText(String.valueOf(kmh));
                mostrarWidget();
            }
        } else {
            ocultarWidget();
        }
    }

    private void mostrarWidget() {
        if (contenedorVelocidad != null) {
            actividad.runOnUiThread(() ->
                    contenedorVelocidad.setVisibility(View.VISIBLE));
        }
    }

    private void ocultarWidget() {
        if (contenedorVelocidad != null) {
            actividad.runOnUiThread(() ->
                    contenedorVelocidad.setVisibility(View.GONE));
        }
    }

    private boolean tienePermiso() {
        return actividad.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}