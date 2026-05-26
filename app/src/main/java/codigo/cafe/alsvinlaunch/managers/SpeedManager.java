package codigo.cafe.alsvinlaunch.managers;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.TextView;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.views.SpeedometerView;

/**
 * SpeedManager — GPS → SpeedometerView.
 *
 * El velocímetro es SIEMPRE visible (muestra "--" en reposo).
 * Solo se oculta si no hay permiso GPS o el proveedor está desactivado,
 * pero en ese caso igual muestra "--" sin anillo.
 *
 * Ciclo de vida:
 *   onCreate  → new SpeedManager(...)
 *   onStart   → iniciar()
 *   onStop    → detener()
 *   onDestroy → destruir()
 */
public class SpeedManager {

    private static final long  INTERVALO_MS  = 1000;
    private static final float DISTANCIA_MIN = 0f;
    private static final float UMBRAL_MS     = 0.5f; // ~1.8 km/h
    private static final int   MAX_KMH       = 120;

    // Histéresis (km/h) — margen para que el indicador no parpadee al rondar un umbral
    private static final int   HISTERESIS    = 2;

    private final Activity        actividad;
    private final SpeedometerView speedometerView;
    private final TextView        gearIndicator;

    // Cortes de subida de marcha (km/h): 1→2, 2→3, 3→4, 4→5
    // Cargados desde SharedPreferences vía GearRangesDialog (configurable).
    private int[] cortesMarcha;

    private LocationManager  locationManager;
    private LocationListener listener;
    private boolean          activo = false;
    private int              marchaActual = 0; // 0 = parado / "N"

    public SpeedManager(Activity actividad) {
        this.actividad       = actividad;
        this.speedometerView = actividad.findViewById(R.id.speedometer_view);
        this.gearIndicator   = actividad.findViewById(R.id.gear_indicator);
        this.cortesMarcha    = GearRangesDialog.cortesGuardados(actividad);
    }

    /** Recarga los cortes desde SharedPreferences (llamar tras editar). */
    public void recargarCortes() {
        this.cortesMarcha = GearRangesDialog.cortesGuardados(actividad);
        // Forzar recálculo en el próximo update — reseteamos para que
        // la rama "veníamos de parado" reasigne en el rango correcto
        marchaActual = 0;
    }

    // ── Ciclo de vida ──────────────────────────────────────────────

    public void iniciar() {
        // Mostrar "--" mientras se obtiene señal
        if (speedometerView != null) speedometerView.resetear();
        resetearMarcha();

        if (!tienePermiso()) return;

        locationManager = (LocationManager) actividad
                .getSystemService(android.content.Context.LOCATION_SERVICE);

        if (locationManager == null
                || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return;
        }

        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                actualizarVelocidad(location);
            }
            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String provider)  {}
            @Override public void onProviderDisabled(String provider) {
                // GPS desactivado → volver a "--"
                actividad.runOnUiThread(() -> {
                    if (speedometerView != null) speedometerView.resetear();
                    resetearMarcha();
                });
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
            if (speedometerView != null) speedometerView.resetear();
            resetearMarcha();
        }
    }

    public void detener() {
        if (locationManager != null && listener != null) {
            try { locationManager.removeUpdates(listener); } catch (Exception ignored) {}
        }
        activo = false;
    }

    public void destruir() {
        detener();
        locationManager = null;
        listener        = null;
    }

    // ── Visibilidad pública (bloqueo de pantalla) ──────────────────

    /**
     * En bloqueo de pantalla el contenedor speed_text lo oculta MainActivity.
     * SpeedManager no necesita tocar visibilidad — solo sigue actualizando
     * internamente para que al desbloquear ya tenga el valor correcto.
     */
    public void ocultar()  { /* visibilidad la maneja MainActivity vía speed_text */ }
    public void mostrar()  {
        if (!activo) iniciar();
    }

    // ── Lógica interna ─────────────────────────────────────────────

    private void actualizarVelocidad(Location location) {
        if (speedometerView == null) return;

        actividad.runOnUiThread(() -> {
            if (location.hasSpeed()) {
                float ms  = location.getSpeed();
                int   kmh = (ms < UMBRAL_MS) ? 0 : Math.round(ms * 3.6f);
                // 0 km/h → resetear muestra "--", anillo vacío
                speedometerView.setVelocidad(kmh, MAX_KMH);
                actualizarMarcha(kmh);
            } else {
                speedometerView.resetear();
                resetearMarcha();
            }
        });
    }

    /**
     * Calcula la marcha recomendada según km/h con histéresis para evitar
     * que el indicador parpadee al rondar un umbral.
     *
     * Para subir: requiere superar el corte + HISTERESIS.
     * Para bajar: requiere caer por debajo del corte − HISTERESIS.
     */
    private void actualizarMarcha(int kmh) {
        if (gearIndicator == null) return;

        if (kmh == 0) {
            if (marchaActual != 0) {
                marchaActual = 0;
                gearIndicator.setText("N");
            }
            return;
        }

        if (marchaActual == 0) {
            // Veníamos de parado: encajar directamente en el rango correcto
            if      (kmh < cortesMarcha[0]) marchaActual = 1;
            else if (kmh < cortesMarcha[1]) marchaActual = 2;
            else if (kmh < cortesMarcha[2]) marchaActual = 3;
            else if (kmh < cortesMarcha[3]) marchaActual = 4;
            else                             marchaActual = 5;
        } else if (marchaActual < 5
                && kmh >= cortesMarcha[marchaActual - 1] + HISTERESIS) {
            marchaActual++;
        } else if (marchaActual > 1
                && kmh < cortesMarcha[marchaActual - 2] - HISTERESIS) {
            marchaActual--;
        }

        gearIndicator.setText(String.valueOf(marchaActual));
    }

    private void resetearMarcha() {
        marchaActual = 0;
        if (gearIndicator != null) gearIndicator.setText("--");
    }

    private boolean tienePermiso() {
        return actividad.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}