package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.palette.graphics.Palette;

import java.util.List;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.services.ServicioNotificaciones;

/**
 * Gestiona el widget de reproductor multimedia.
 *
 * Cambios respecto a la versión anterior:
 *  - OnActiveSessionsChangedListener: detecta nuevas sesiones en caliente.
 *  - MediaController.Callback: actualiza play/pausa/metadata sin polling.
 *  - Artwork del álbum: muestra portada desde MediaMetadata.
 *  - Marquee: título scrollea automáticamente cuando es largo.
 *  - OnColorExtraidoListener: extrae color dominante del artwork con Palette
 *    para que MainActivity anime el fondo del launcher.
 *  - destruir() libera todos los recursos.
 */
public class MediaWidgetManager {

    // ──────────────────────────────────────────────
    //  Interface — color dinámico
    // ──────────────────────────────────────────────

    public interface OnColorExtraidoListener {
        /** Se llama cuando hay artwork y se extrae el color dominante. */
        void onColor(int colorDominante);
        /** Se llama cuando no hay música o no hay artwork. */
        void onSinColor();
    }

    // ──────────────────────────────────────────────
    //  Campos
    // ──────────────────────────────────────────────

    private final Activity actividad;
    private final LinearLayout widgetReproductor;
    private final TextView reproductorTitulo;
    private final TextView reproductorArtista;
    private final ImageView reproductorIcono;
    private final ImageView botonPlayPausa;
    private final ImageView botonAnterior;
    private final ImageView botonSiguiente;

    private MediaSessionManager msm;
    private MediaController controladorActual;
    private MediaController.Callback callbackControlador;
    private MediaSessionManager.OnActiveSessionsChangedListener listenerSesiones;
    private BroadcastReceiver receptorMedia;

    private OnColorExtraidoListener colorListener;

    private boolean estaReproduciendo = false;
    private boolean iniciado = false; // ← guard
    private boolean destruido = false; // ← guard para Palette

    // ──────────────────────────────────────────────
    //  Constructor
    // ──────────────────────────────────────────────

    public MediaWidgetManager(Activity actividad) {
        this.actividad = actividad;
        widgetReproductor  = actividad.findViewById(R.id.widget_reproductor);
        reproductorTitulo  = actividad.findViewById(R.id.reproductor_titulo);
        reproductorArtista = actividad.findViewById(R.id.reproductor_artista);
        reproductorIcono   = actividad.findViewById(R.id.reproductor_icono);
        botonPlayPausa     = actividad.findViewById(R.id.btn_play_pausa);
        botonAnterior      = actividad.findViewById(R.id.btn_anterior);
        botonSiguiente     = actividad.findViewById(R.id.btn_siguiente);

        msm = (MediaSessionManager) actividad.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    // ──────────────────────────────────────────────
    //  API pública
    // ──────────────────────────────────────────────

    public void setOnColorExtraidoListener(OnColorExtraidoListener listener) {
        this.colorListener = listener;
    }

    // ──────────────────────────────────────────────
    //  Ciclo de vida público
    // ──────────────────────────────────────────────

    public void iniciar() {
        if (iniciado) return; // ← guard principal
        iniciado = true;
        destruido = false;
        configurarBotones();
        registrarReceptorBroadcast();

        if (!tienePermisoNotificaciones()) {
            Toast.makeText(actividad,
                    "Activa AlsvinLauncher en Ajustes → Seguridad → Acceso a notificaciones",
                    Toast.LENGTH_LONG).show();
            widgetReproductor.setVisibility(View.GONE);
            return;
        }

        registrarListenerSesiones();
        actualizar();
    }

    /** Llamado desde onResume — refresca estado si cambió con la Activity en pausa. */
    public void actualizar() {
        if (!tienePermisoNotificaciones()) return;
        try {
            ComponentName cn = new ComponentName(actividad, ServicioNotificaciones.class);
            List<MediaController> controladores = msm.getActiveSessions(cn);

            if (!controladores.isEmpty()) {
                adjuntarControlador(controladores.get(0));
            } else {
                limpiarControlador();
            }
        } catch (Exception e) {
            limpiarControlador();
        }
    }
    public void detener() {
        // Para usar en onStop — pausa sin destruir
        if (receptorMedia != null) {
            try { actividad.unregisterReceiver(receptorMedia); } catch (Exception ignored) {}
            receptorMedia = null;
        }
        if (listenerSesiones != null && msm != null) {
            try { msm.removeOnActiveSessionsChangedListener(listenerSesiones); } catch (Exception ignored) {}
            listenerSesiones = null;
        }
        desconectarCallback();
        iniciado = false; // ← permite reiniciar en onStart
    }
    public void destruir() {
        destruido = true; // ← cancela callbacks de Palette pendientes
        detener();
        controladorActual = null;
        colorListener     = null;
        msm               = null;
    }

    // ──────────────────────────────────────────────
    //  Configuración interna
    // ──────────────────────────────────────────────

    private void configurarBotones() {
        botonPlayPausa.setOnClickListener(v -> controlarReproduccion());
        botonAnterior.setOnClickListener(v -> {
            if (controladorActual != null)
                controladorActual.getTransportControls().skipToPrevious();
        });
        botonSiguiente.setOnClickListener(v -> {
            if (controladorActual != null)
                controladorActual.getTransportControls().skipToNext();
        });
    }

    /**
     * Escucha nuevas sesiones que aparezcan DESPUÉS de que el launcher arrancó.
     */
    private void registrarListenerSesiones() {
        ComponentName cn = new ComponentName(actividad, ServicioNotificaciones.class);
        listenerSesiones = controllers -> {
            actividad.runOnUiThread(() -> {
                if (controllers != null && !controllers.isEmpty()) {
                    adjuntarControlador(controllers.get(0));
                } else {
                    limpiarControlador();
                }
            });
        };
        try {
            msm.addOnActiveSessionsChangedListener(listenerSesiones, cn);
        } catch (Exception e) {
            listenerSesiones = null;
        }
    }

    /**
     * BroadcastReceiver como canal secundario (enviado por ServicioNotificaciones).
     * Garantiza compatibilidad con Android 6 donde las APIs de sesión son limitadas.
     */
    private void registrarReceptorBroadcast() {
        receptorMedia = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String titulo         = intent.getStringExtra("titulo");
                String artista        = intent.getStringExtra("artista");
                boolean reproduciendo = intent.getBooleanExtra("reproduciendo", false);

                if (titulo != null && !titulo.isEmpty()) {
                    estaReproduciendo = reproduciendo;

                    // Intentar adjuntar controlador si aún no tenemos uno
                    if (controladorActual == null && tienePermisoNotificaciones()) {
                        try {
                            ComponentName cn = new ComponentName(actividad, ServicioNotificaciones.class);
                            List<MediaController> cs = msm.getActiveSessions(cn);
                            if (!cs.isEmpty()) adjuntarControlador(cs.get(0));
                        } catch (Exception ignored) {}
                    }

                    mostrarWidget(titulo, artista);
                } else {
                    limpiarControlador();
                }
            }
        };
        actividad.registerReceiver(receptorMedia,
                new IntentFilter(ServicioNotificaciones.ACCION_MEDIA_CAMBIO));
    }

    // ──────────────────────────────────────────────
    //  Gestión del controlador
    // ──────────────────────────────────────────────

    private void adjuntarControlador(MediaController nuevo) {
        if (controladorActual != null &&
                controladorActual.getSessionToken().equals(nuevo.getSessionToken())) {
            refrescarDesdeControlador();
            return;
        }

        desconectarCallback();
        controladorActual = nuevo;

        callbackControlador = new MediaController.Callback() {
            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                actividad.runOnUiThread(() -> refrescarDesdeControlador());
            }
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                actividad.runOnUiThread(() -> {
                    estaReproduciendo = state != null &&
                            state.getState() == PlaybackState.STATE_PLAYING;
                    botonPlayPausa.setImageResource(
                            estaReproduciendo ? R.drawable.ic_pausa : R.drawable.ic_play);
                });
            }
            @Override
            public void onSessionDestroyed() {
                actividad.runOnUiThread(() -> limpiarControlador());
            }
        };
        controladorActual.registerCallback(callbackControlador);
        refrescarDesdeControlador();
    }

    private void desconectarCallback() {
        if (controladorActual != null && callbackControlador != null) {
            try { controladorActual.unregisterCallback(callbackControlador); } catch (Exception ignored) {}
        }
        callbackControlador = null;
    }

    private void refrescarDesdeControlador() {
        if (controladorActual == null) { limpiarControlador(); return; }

        MediaMetadata metadata = controladorActual.getMetadata();
        PlaybackState estado   = controladorActual.getPlaybackState();

        if (metadata == null) { limpiarControlador(); return; }

        String titulo  = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artista = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);

        estaReproduciendo = estado != null &&
                estado.getState() == PlaybackState.STATE_PLAYING;

        // ── Artwork + color dinámico ──────────────────────────────────────────
        Bitmap bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bitmap == null)
            bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);

        if (bitmap != null) {
            reproductorIcono.setImageBitmap(bitmap);
            extraerColor(bitmap);
        } else {
            reproductorIcono.setImageResource(R.drawable.ic_music);
            if (colorListener != null) colorListener.onSinColor();
        }
        // ─────────────────────────────────────────────────────────────────────

        if (titulo != null && !titulo.isEmpty()) {
            mostrarWidget(titulo, artista);
        } else {
            limpiarControlador();
        }
    }

    private void limpiarControlador() {
        desconectarCallback();
        controladorActual = null;
        estaReproduciendo = false;
        widgetReproductor.setVisibility(View.GONE);
        if (colorListener != null) colorListener.onSinColor();
    }

    // ──────────────────────────────────────────────
    //  Color dinámico (Palette)
    // ──────────────────────────────────────────────

    /**
     * Extrae el color dominante oscuro del bitmap en background thread.
     * Prioridad: DarkVibrant → DarkMuted → color base del launcher.
     */
    private void extraerColor(Bitmap bitmap) {
        if (colorListener == null) return;
        Palette.from(bitmap).generate(palette -> {
            if (destruido || colorListener == null) return; // ← guard
            if (palette == null) {
                actividad.runOnUiThread(() -> {
                    if (!destruido) colorListener.onSinColor();
                });
                return;
            }
            int color = palette.getDarkVibrantColor(
                    palette.getDarkMutedColor(0xFF1A1A2E));
            actividad.runOnUiThread(() -> {
                if (!destruido) colorListener.onColor(color); // ← guard
            });
        });
    }

    // ──────────────────────────────────────────────
    //  UI
    // ──────────────────────────────────────────────

    private void mostrarWidget(String titulo, String artista) {
        widgetReproductor.setVisibility(View.VISIBLE);

        reproductorTitulo.setText(titulo);
        reproductorTitulo.setSelected(true); // activa marquee scroll

        reproductorArtista.setText(artista != null ? artista : "");

        botonPlayPausa.setImageResource(
                estaReproduciendo ? R.drawable.ic_pausa : R.drawable.ic_play);

        View.OnClickListener abrirApp = v -> abrirAppReproductora();
        widgetReproductor.setOnClickListener(abrirApp);
        reproductorTitulo.setOnClickListener(abrirApp);
        reproductorArtista.setOnClickListener(abrirApp);
    }

    private void controlarReproduccion() {
        if (controladorActual == null) return;
        if (estaReproduciendo) {
            controladorActual.getTransportControls().pause();
        } else {
            controladorActual.getTransportControls().play();
        }
    }

    private void abrirAppReproductora() {
        if (controladorActual == null) return;
        try {
            android.app.PendingIntent intent = controladorActual.getSessionActivity();
            if (intent != null) {
                intent.send();
            } else {
                String paquete = controladorActual.getPackageName();
                Intent i = actividad.getPackageManager().getLaunchIntentForPackage(paquete);
                if (i != null) actividad.startActivity(i);
            }
        } catch (Exception e) {
            Toast.makeText(actividad, "No se pudo abrir la app", Toast.LENGTH_SHORT).show();
        }
    }

    // ──────────────────────────────────────────────
    //  Utilidades
    // ──────────────────────────────────────────────

    private boolean tienePermisoNotificaciones() {
        String activos = android.provider.Settings.Secure.getString(
                actividad.getContentResolver(), "enabled_notification_listeners");
        return activos != null && activos.contains(actividad.getPackageName());
    }
}