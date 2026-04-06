package codigo.cafe.alsvinlaunch.managers;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.views.SpeedometerView;

/**
 * SpeedManager — obtiene velocidad por GPS y actualiza SpeedometerView.
 * <p>
 * Ciclo de vida:
 * onCreate  → new SpeedManager(...)
 * onStart   → iniciar()
 * onStop    → detener()
 * onDestroy → destruir()
 * <p>
 * Si no hay permiso ACCESS_FINE_LOCATION o GPS no disponible → widget GONE.
 * Si velocidad == 0 → widget GONE.
 */
public class SpeedManager {

    private static final long INTERVALO_MS = 1000;
    private static final float DISTANCIA_MIN = 0f;
    private static final float UMBRAL_MS = 0.5f;  // ~1.8 km/h — filtra ruido
    private static final int MAX_KMH = 120;

    private final Activity actividad;
    private final View contenedor;       // LinearLayout speed_text
    private final SpeedometerView speedometerView;  // el anillo

    private LocationManager locationManager;
    private LocationListener listener;
    private boolean activo = false;

    public SpeedManager(Activity actividad) {
        this.actividad = actividad;
        this.contenedor = actividad.findViewById(R.id.speed_text);
        this.speedometerView = actividad.findViewById(R.id.speedometer_view);
    }

    // ── Ciclo de vida ──────────────────────────────────────────────

    public void iniciar() {
        if (!tienePermiso()) {
            ocultarWidget();
            return;
        }

        locationManager = (LocationManager) actividad
                .getSystemService(android.content.Context.LOCATION_SERVICE);

        if (locationManager == null
                || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            ocultarWidget();
            return;
        }

        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                actualizarVelocidad(location);
            }

            @Override
            public void onStatusChanged(String p, int s, Bundle e) {
            }

            @Override
            public void onProviderEnabled(String provider) { /* nada */ }

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

    public void ocultar() {
        ocultarWidget();
    }

    public void mostrar() {
        if (activo) {
            // GPS corriendo — la próxima ubicación decide si mostrar
        } else {
            iniciar();
        }
    }

    // ── Lógica interna ─────────────────────────────────────────────

    private void actualizarVelocidad(Location location) {
        if (speedometerView == null) return;

        actividad.runOnUiThread(() -> {
            if (location.hasSpeed()) {
                float ms = location.getSpeed();
                int kmh = (ms < UMBRAL_MS) ? 0 : Math.round(ms * 3.6f);

                if (kmh == 0) {
                    ocultarWidget();
                } else {
                    speedometerView.setVelocidad(kmh, MAX_KMH);
                    mostrarWidget();
                }
            } else {
                ocultarWidget();
            }
        });
    }

    private void mostrarWidget() {
        if (contenedor != null)
            contenedor.setVisibility(View.VISIBLE);
    }

    private void ocultarWidget() {
        if (contenedor != null)
            contenedor.setVisibility(View.GONE);
        if (speedometerView != null)
            speedometerView.resetear();
    }

    private boolean tienePermiso() {
        return actividad.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}