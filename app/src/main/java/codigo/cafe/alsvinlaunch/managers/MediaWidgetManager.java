package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.view.KeyEvent;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;

import java.util.List;
import java.util.Locale;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.services.ServicioNotificaciones;

public class MediaWidgetManager {

    public interface OnColorExtraidoListener {
        void onColor(int colorDominante);

        void onSinColor();
    }

    private static final int SEEKBAR_MAX = 1000;
    private static final long INTERVALO_REFRESCO_MS = 500L;

    private final Activity actividad;
    private final LinearLayout widgetReproductor;
    private final TextView reproductorTitulo;
    private final TextView reproductorArtista;
    private final ImageView reproductorIcono;
    private final ImageView botonPlayPausa;
    private final ImageView botonAnterior;
    private final ImageView botonSiguiente;
    private final SeekBar reproductorSeekbar;
    private final TextView reproductorTiempoActual;
    private final TextView reproductorTiempoTotal;

    private MediaSessionManager msm;
    private MediaController controladorActual;
    private MediaController.Callback callbackControlador;
    private MediaSessionManager.OnActiveSessionsChangedListener listenerSesiones;
    private BroadcastReceiver receptorMedia;

    private OnColorExtraidoListener colorListener;

    private boolean estaReproduciendo = false;
    private boolean iniciado = false;
    private boolean destruido = false;

    private long duracionMs = 0L;
    private boolean usuarioArrastrando = false;
    private boolean puedeBuscar = false;

    private static final long INTERVALO_SONDEO_MS = 3000L;

    // Modo BT sin MediaSession (Android 6 / stacks OEM): el widget se alimenta de
    // los broadcasts AVRCP que reenvía ServicioNotificaciones y los botones usan
    // KeyEvents de media.
    private boolean modoBt = false;
    private String tituloBt = "";
    private String artistaBt = "";
    private long ultimoEventoBtMs = 0L;
    private static final long TIMEOUT_BT_PAUSADO_MS = 10 * 60_000L;

    private final Handler handlerProgreso = new Handler(Looper.getMainLooper());
    private final Runnable runnableProgreso = new Runnable() {
        @Override
        public void run() {
            if (destruido || controladorActual == null) return;
            actualizarProgresoDesdeEstado();
            if (estaReproduciendo) {
                handlerProgreso.postDelayed(this, INTERVALO_REFRESCO_MS);
            }
        }
    };

    // Caché de permiso de notificaciones: evita queries a ContentResolver en cada tick
    private boolean cachePerm = false;
    private long cachePermTs = 0L;
    private static final long CACHE_PERM_TTL_MS = 5_000L;

    // Caché nombre legible de paquetes ya consultados
    private final java.util.Map<String, String> cachePaquetes = new java.util.HashMap<>();

    // Re-enganche post-attach: la sesión a veces entrega PlaybackState/metadata
    // obsoletos o nulos justo al obtener el controlador (p. ej. al volver al
    // launcher). Releemos el estado real unas cuantas veces durante ~2s.
    private final Handler handlerReenganche = new Handler(Looper.getMainLooper());
    private int reenganchesRestantes = 0;
    private final Runnable runnableReenganche = new Runnable() {
        @Override
        public void run() {
            if (destruido || controladorActual == null) return;
            refrescarDesdeControlador();
            if (reenganchesRestantes-- > 0 && controladorActual != null) {
                handlerReenganche.postDelayed(this, 400L);
            }
        }
    };

    private final Handler handlerSondeo = new Handler(Looper.getMainLooper());
    private final Runnable runnableSondeo = new Runnable() {
        @Override
        public void run() {
            if (destruido) return;
            if (controladorActual == null && tienePermisoNotificaciones()) {
                actualizar();
            }
            // BT pausado sin eventos durante mucho tiempo: probablemente se
            // desconectó sin avisar; ocultar el widget.
            if (modoBt && !estaReproduciendo && SystemClock.elapsedRealtime()
                    - ultimoEventoBtMs > TIMEOUT_BT_PAUSADO_MS) {
                limpiarControlador();
                return;
            }
            if (!destruido && controladorActual == null) {
                handlerSondeo.removeCallbacks(this);
                handlerSondeo.postDelayed(this, INTERVALO_SONDEO_MS);
            }
        }
    };

    public MediaWidgetManager(Activity actividad) {
        this.actividad = actividad;
        widgetReproductor = actividad.findViewById(R.id.widget_reproductor);
        reproductorTitulo = actividad.findViewById(R.id.reproductor_titulo);
        reproductorArtista = actividad.findViewById(R.id.reproductor_artista);
        reproductorIcono = actividad.findViewById(R.id.reproductor_icono);
        botonPlayPausa = actividad.findViewById(R.id.btn_play_pausa);
        botonAnterior = actividad.findViewById(R.id.btn_anterior);
        botonSiguiente = actividad.findViewById(R.id.btn_siguiente);
        reproductorSeekbar = actividad.findViewById(R.id.reproductor_seekbar);
        reproductorTiempoActual = actividad.findViewById(R.id.reproductor_tiempo_actual);
        reproductorTiempoTotal = actividad.findViewById(R.id.reproductor_tiempo_total);
        msm = (MediaSessionManager) actividad.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    // ──────────────────────────────────────────────
    //  API pública
    // ──────────────────────────────────────────────

    public void setOnColorExtraidoListener(OnColorExtraidoListener listener) {
        this.colorListener = listener;
    }

    // ──────────────────────────────────────────────
    //  Ciclo de vida
    // ──────────────────────────────────────────────

    public void iniciar() {
        if (iniciado) return;
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
        handlerSondeo.postDelayed(runnableSondeo, INTERVALO_SONDEO_MS);
    }

    public void actualizar() {
        if (!tienePermisoNotificaciones()) return;
        try {
            ComponentName cn = new ComponentName(actividad, ServicioNotificaciones.class);
            List<MediaController> controladores = msm.getActiveSessions(cn);
            MediaController elegido = elegirMejorControlador(controladores);
            if (elegido != null) {
                adjuntarControlador(elegido);
            } else if (!modoBt) {
                limpiarControlador();
            }
        } catch (Exception e) {
            if (!modoBt) limpiarControlador();
        }
    }

    /** Fuentes propias del headunit (radio, DVD/USB, AUX) que no se muestran en el widget. */
    private boolean esFuenteHeadunit(String paquete) {
        if (paquete == null) return false;
        String p = paquete.toLowerCase(Locale.US);
        return p.contains("radio")
                || p.equals("com.ts.main") || p.contains("ts.main")
                || p.contains("dvdplayer") || p.contains("ts.dvd")
                || p.contains("avin") || p.contains("aux");
    }

    private MediaController elegirMejorControlador(List<MediaController> controladores) {
        if (controladores == null || controladores.isEmpty()) return null;
        MediaController reproduciendo = null;
        MediaController enBuffer = null;
        MediaController conMetadata = null;
        for (MediaController c : controladores) {
            if (c == null) continue;
            if (esFuenteHeadunit(c.getPackageName())) continue;
            PlaybackState s = c.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                reproduciendo = c;
                break;
            }
            if (enBuffer == null && s != null &&
                    (s.getState() == PlaybackState.STATE_BUFFERING ||
                     s.getState() == PlaybackState.STATE_CONNECTING)) {
                enBuffer = c;
            }
            if (conMetadata == null && c.getMetadata() != null) {
                conMetadata = c;
            }
        }
        if (reproduciendo != null) return reproduciendo;
        if (enBuffer != null) return enBuffer;
        if (conMetadata != null) return conMetadata;
        return null;
    }

    private String nombreAmigablePaquete(String paquete) {
        if (paquete == null) return "Reproduciendo";
        String cached = cachePaquetes.get(paquete);
        if (cached != null) return cached;
        String result = resolverNombrePaquete(paquete);
        cachePaquetes.put(paquete, result);
        return result;
    }

    private String resolverNombrePaquete(String paquete) {
        String p = paquete.toLowerCase(Locale.US);
        if (p.contains("bluetooth")) return "Bluetooth";
        if (p.contains("radio"))     return "Radio FM";
        if (p.equals("com.ts.main"))            return "Radio FM";
        if (p.equals("com.ts.dvdplayer"))       return "Media";
        if (p.equals("com.android.sdvdplayer")) return "Media";
        if (p.contains("dvdplayer"))            return "Media";
        if (p.contains("ts.main"))              return "Radio FM";
        try {
            android.content.pm.PackageManager pm = actividad.getPackageManager();
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(paquete, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            if (label != null) return label.toString();
        } catch (Exception ignored) {
        }
        return "Reproduciendo";
    }

    public void detener() {
        handlerProgreso.removeCallbacks(runnableProgreso);
        handlerSondeo.removeCallbacks(runnableSondeo);
        handlerReenganche.removeCallbacks(runnableReenganche);
        if (receptorMedia != null) {
            try {
                actividad.unregisterReceiver(receptorMedia);
            } catch (Exception ignored) {
            }
            receptorMedia = null;
        }
        if (listenerSesiones != null && msm != null) {
            try {
                msm.removeOnActiveSessionsChangedListener(listenerSesiones);
            } catch (Exception ignored) {
            }
            listenerSesiones = null;
        }
        desconectarCallback();
        // Soltar el controlador para que al volver se haga un re-enganche limpio
        // (re-registra el callback y relanza el runnable de progreso). Si se
        // conservara, adjuntarControlador() tomaría el atajo por mismo token y
        // el widget quedaría sin callbacks (icono/tiempo congelados o desaparece).
        controladorActual = null;
        estaReproduciendo = false;
        iniciado = false;
    }

    public void destruir() {
        destruido = true;
        detener();
        controladorActual = null;
        colorListener = null;
        msm = null;
    }

    // ──────────────────────────────────────────────
    //  Visibilidad pública (bloqueo de pantalla)
    // ──────────────────────────────────────────────

    /**
     * Oculta el widget sin tocar el estado interno ni el controlador.
     */
    public void ocultar() {
        widgetReproductor.setVisibility(View.GONE);
    }

    /**
     * Restaura el widget si hay sesión activa, lo mantiene oculto si no hay música.
     * Llama a actualizar() por si algo cambió mientras la pantalla estaba bloqueada.
     */
    public void mostrar() {
        if (controladorActual != null || modoBt) {
            widgetReproductor.setVisibility(View.VISIBLE);
        }
        actualizar();
    }

    // ──────────────────────────────────────────────
    //  Configuración interna
    // ──────────────────────────────────────────────

    private void configurarBotones() {
        botonPlayPausa.setOnClickListener(v -> controlarReproduccion());
        botonAnterior.setOnClickListener(v -> {
            if (controladorActual != null) {
                controladorActual.getTransportControls().skipToPrevious();
            } else {
                enviarKeyEventMedia(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            }
        });
        botonSiguiente.setOnClickListener(v -> {
            if (controladorActual != null) {
                controladorActual.getTransportControls().skipToNext();
            } else {
                enviarKeyEventMedia(KeyEvent.KEYCODE_MEDIA_NEXT);
            }
        });

        reproductorSeekbar.setMax(SEEKBAR_MAX);
        reproductorSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && duracionMs > 0) {
                    long tiempoMs = (long) (((double) progress / SEEKBAR_MAX) * duracionMs);
                    reproductorTiempoActual.setText(formatearTiempo(tiempoMs));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                usuarioArrastrando = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                usuarioArrastrando = false;
                if (controladorActual != null && puedeBuscar && duracionMs > 0) {
                    long tiempoMs = (long) (((double) seekBar.getProgress() / SEEKBAR_MAX) * duracionMs);
                    try {
                        controladorActual.getTransportControls().seekTo(tiempoMs);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private void registrarListenerSesiones() {
        ComponentName cn = new ComponentName(actividad, ServicioNotificaciones.class);
        listenerSesiones = controllers -> {
            actividad.runOnUiThread(() -> {
                MediaController elegido = elegirMejorControlador(controllers);
                if (elegido != null) {
                    adjuntarControlador(elegido);
                } else {
                    // Delay antes de limpiar: apps como YT Music destruyen y recrean
                    // su sesión al cambiar de canción; un clear inmediato esconde el widget.
                    handlerSondeo.postDelayed(() -> {
                        if (destruido || controladorActual != null) return;
                        actualizar();
                        if (controladorActual == null && !modoBt) limpiarControlador();
                    }, 1500);
                }
            });
        };
        try {
            msm.addOnActiveSessionsChangedListener(listenerSesiones, cn);
        } catch (Exception e) {
            listenerSesiones = null;
        }
    }

    private void registrarReceptorBroadcast() {
        receptorMedia = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean esAvrcp = intent.getBooleanExtra(
                        ServicioNotificaciones.EXTRA_FUENTE_AVRCP, false);
                if (esAvrcp) {
                    manejarEventoBt(intent);
                    return;
                }

                String paquete = intent.getStringExtra("paquete");
                if (paquete != null && esFuenteHeadunit(paquete)) return; // Ignorar Radio/DVD/AUX

                // En modo BT el estado lo dictan los eventos AVRCP, no el sondeo de sesiones
                if (!modoBt) estaReproduciendo = intent.getBooleanExtra("reproduciendo", false);
                if (controladorActual == null && tienePermisoNotificaciones()) {
                    try {
                        ComponentName cn = new ComponentName(actividad, ServicioNotificaciones.class);
                        List<MediaController> cs = msm.getActiveSessions(cn);
                        MediaController elegido = elegirMejorControlador(cs);
                        if (elegido != null) adjuntarControlador(elegido);
                    } catch (Exception ignored) {
                    }
                }
            }
        };
        ContextCompat.registerReceiver(actividad, receptorMedia,
                new IntentFilter(ServicioNotificaciones.ACCION_MEDIA_CAMBIO),
                ContextCompat.RECEIVER_EXPORTED);
    }

    /**
     * Audio BT sin MediaSession (típico en Android 6: la app BT nativa no publica
     * sesión). Se muestra el widget con la metadata AVRCP del broadcast; si llega
     * una MediaSession real, esa tiene prioridad.
     */
    private void manejarEventoBt(Intent intent) {
        if (controladorActual != null) return;
        if (intent.getBooleanExtra(ServicioNotificaciones.EXTRA_BT_DESCONECTADO, false)) {
            if (modoBt) limpiarControlador();
            return;
        }
        String titulo = intent.getStringExtra("titulo");
        if (titulo != null && !titulo.isEmpty()) {
            // Evento de pista: reemplaza ambos (el artista puede venir vacío)
            String artista = intent.getStringExtra("artista");
            tituloBt = titulo;
            artistaBt = artista != null ? artista : "";
        }
        modoBt = true;
        ultimoEventoBtMs = SystemClock.elapsedRealtime();
        estaReproduciendo = intent.getBooleanExtra("reproduciendo", true);

        duracionMs = 0L;
        puedeBuscar = false;
        reproductorSeekbar.setProgress(0);
        reproductorSeekbar.setEnabled(false);
        reproductorTiempoActual.setText("0:00");
        reproductorTiempoTotal.setText("0:00");
        reproductorIcono.setImageResource(R.drawable.ic_music);
        if (colorListener != null) colorListener.onSinColor();

        mostrarWidget(tituloBt.isEmpty() ? "Bluetooth" : tituloBt, artistaBt);
    }

    // ──────────────────────────────────────────────
    //  Gestión del controlador
    // ──────────────────────────────────────────────

    private void adjuntarControlador(MediaController nuevo) {
        if (controladorActual != null &&
                controladorActual.getSessionToken().equals(nuevo.getSessionToken())) {
            refrescarDesdeControlador();
            programarReenganche();
            return;
        }
        desconectarCallback();
        controladorActual = nuevo;
        modoBt = false;
        handlerSondeo.removeCallbacks(runnableSondeo);

        callbackControlador = new MediaController.Callback() {
            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                actividad.runOnUiThread(() -> refrescarDesdeControlador());
            }

            // Queue llega asíncronamente en sesiones AVRCP BT
            @Override
            public void onQueueChanged(List<android.media.session.MediaSession.QueueItem> queue) {
                actividad.runOnUiThread(() -> refrescarDesdeControlador());
            }

            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                actividad.runOnUiThread(() -> {
                    estaReproduciendo = state != null &&
                            state.getState() == PlaybackState.STATE_PLAYING;
                    botonPlayPausa.setImageResource(
                            estaReproduciendo ? R.drawable.ic_pausa : R.drawable.ic_play);
                    actualizarCapacidadSeek(state);
                    actualizarProgresoDesdeEstado();
                    reprogramarRunnableProgreso();
                });
            }

            @Override
            public void onSessionDestroyed() {
                actividad.runOnUiThread(() -> limpiarControlador());
            }
        };
        controladorActual.registerCallback(callbackControlador);
        refrescarDesdeControlador();
        programarReenganche();
    }

    /**
     * Relee el estado del controlador varias veces durante ~2s tras adjuntarlo.
     * Cubre el caso en que la sesión entrega un PlaybackState/metadata obsoleto o
     * nulo justo al obtener el controlador (icono sin actualizar, tiempo en 0).
     */
    private void programarReenganche() {
        handlerReenganche.removeCallbacks(runnableReenganche);
        if (destruido || controladorActual == null) return;
        reenganchesRestantes = 5; // 5 reintentos * 400ms ≈ 2s de ventana
        handlerReenganche.postDelayed(runnableReenganche, 400L);
    }

    private void desconectarCallback() {
        if (controladorActual != null && callbackControlador != null) {
            try {
                controladorActual.unregisterCallback(callbackControlador);
            } catch (Exception ignored) {
            }
        }
        callbackControlador = null;
    }

    /**
     * Extrae título y artista de un MediaController usando TODAS las fuentes disponibles:
     * 1. MediaMetadata estándar (TITLE, DISPLAY_TITLE)
     * 2. Queue del reproductor (primer ítem)
     * 3. Extras del bundle de estado
     * Devuelve String[2] = {titulo, artista}, nunca null.
     */
    private String[] extraerTituloArtista(MediaController ctrl) {
        String titulo  = null;
        String artista = null;

        // ── Fuente 1: MediaMetadata ────────────────────────────────────────────
        MediaMetadata meta = ctrl.getMetadata();
        if (meta != null) {
            titulo  = primerNoVacioMeta(meta,
                    MediaMetadata.METADATA_KEY_TITLE,
                    MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
                    MediaMetadata.METADATA_KEY_MEDIA_ID);
            artista = primerNoVacioMeta(meta,
                    MediaMetadata.METADATA_KEY_ARTIST,
                    MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                    MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                    MediaMetadata.METADATA_KEY_COMPOSER);
        }

        // ── Fuente 2: Queue (primer ítem — común en AVRCP Controller de Android) ─
        if (titulo == null || titulo.isEmpty()) {
            try {
                List<android.media.session.MediaSession.QueueItem> queue = ctrl.getQueue();
                if (queue != null && !queue.isEmpty()) {
                    android.media.MediaDescription desc = queue.get(0).getDescription();
                    if (desc != null) {
                        if (desc.getTitle() != null) titulo  = desc.getTitle().toString();
                        if (desc.getSubtitle() != null) artista = desc.getSubtitle().toString();
                    }
                }
            } catch (Exception ignored) {}
        }

        return new String[]{titulo != null ? titulo : "", artista != null ? artista : ""};
    }

    private String primerNoVacioMeta(MediaMetadata meta, String... claves) {
        for (String clave : claves) {
            String val = meta.getString(clave);
            if (val != null && !val.isEmpty()) return val;
        }
        return null;
    }

    private void refrescarDesdeControlador() {
        if (controladorActual == null) {
            limpiarControlador();
            return;
        }

        PlaybackState estado = controladorActual.getPlaybackState();
        estaReproduciendo = estado != null &&
                estado.getState() == PlaybackState.STATE_PLAYING;

        // ── Extraer título/artista de todas las fuentes disponibles ────────────
        String[] taResult = extraerTituloArtista(controladorActual);
        String titulo  = taResult[0];
        String artista = taResult[1];

        // ── Duración y portada desde MediaMetadata ────────────────────────────
        MediaMetadata metadata = controladorActual.getMetadata();
        Bitmap bitmap = null;
        duracionMs = 0L;
        if (metadata != null) {
            duracionMs = Math.max(0L, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
            bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (bitmap == null) bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }

        // ── Sin título: resolver ───────────────────────────────────────────────
        if (titulo.isEmpty()) {
            boolean estadoUtil = estado != null && (
                    estado.getState() == PlaybackState.STATE_PLAYING ||
                    estado.getState() == PlaybackState.STATE_PAUSED  ||
                    estado.getState() == PlaybackState.STATE_BUFFERING ||
                    estado.getState() == PlaybackState.STATE_CONNECTING);

            if (!estadoUtil && metadata == null) {
                limpiarControlador();
                return;
            }
            titulo = nombreAmigablePaquete(controladorActual.getPackageName());
        }

        reproductorTiempoTotal.setText(formatearTiempo(duracionMs));
        actualizarCapacidadSeek(estado);
        actualizarProgresoDesdeEstado();
        reprogramarRunnableProgreso();

        if (bitmap != null) {
            reproductorIcono.setImageBitmap(bitmap);
            extraerColor(bitmap);
        } else {
            reproductorIcono.setImageResource(R.drawable.ic_music);
            if (colorListener != null) colorListener.onSinColor();
        }

        mostrarWidget(titulo, artista);
    }

    private void limpiarControlador() {
        desconectarCallback();
        handlerProgreso.removeCallbacks(runnableProgreso);
        handlerReenganche.removeCallbacks(runnableReenganche);
        controladorActual = null;
        estaReproduciendo = false;
        modoBt = false;
        tituloBt = "";
        artistaBt = "";
        duracionMs = 0L;
        puedeBuscar = false;
        if (!usuarioArrastrando) {
            reproductorSeekbar.setProgress(0);
        }
        reproductorSeekbar.setEnabled(false);
        reproductorTiempoActual.setText("0:00");
        reproductorTiempoTotal.setText("0:00");
        widgetReproductor.setVisibility(View.GONE);
        if (colorListener != null) colorListener.onSinColor();
        if (!destruido) {
            handlerSondeo.removeCallbacks(runnableSondeo);
            handlerSondeo.postDelayed(runnableSondeo, INTERVALO_SONDEO_MS);
        }
    }

    // ──────────────────────────────────────────────
    //  Progreso y tiempos
    // ──────────────────────────────────────────────

    private void actualizarProgresoDesdeEstado() {
        if (controladorActual == null) return;
        PlaybackState estado = controladorActual.getPlaybackState();
        long posicionMs = calcularPosicionActual(estado);

        if (!usuarioArrastrando) {
            int progreso = duracionMs > 0
                    ? (int) (((double) posicionMs / duracionMs) * SEEKBAR_MAX)
                    : 0;
            if (progreso < 0) progreso = 0;
            if (progreso > SEEKBAR_MAX) progreso = SEEKBAR_MAX;
            reproductorSeekbar.setProgress(progreso);
            reproductorTiempoActual.setText(formatearTiempo(posicionMs));
        }
    }

    private long calcularPosicionActual(PlaybackState estado) {
        if (estado == null) return 0L;
        long posicion = estado.getPosition();
        if (estado.getState() == PlaybackState.STATE_PLAYING) {
            long delta = SystemClock.elapsedRealtime() - estado.getLastPositionUpdateTime();
            posicion += (long) (delta * estado.getPlaybackSpeed());
        }
        if (posicion < 0) posicion = 0;
        if (duracionMs > 0 && posicion > duracionMs) posicion = duracionMs;
        return posicion;
    }

    private void actualizarCapacidadSeek(PlaybackState estado) {
        puedeBuscar = estado != null && duracionMs > 0
                && (estado.getActions() & PlaybackState.ACTION_SEEK_TO) != 0;
        reproductorSeekbar.setEnabled(puedeBuscar);
    }

    private void reprogramarRunnableProgreso() {
        handlerProgreso.removeCallbacks(runnableProgreso);
        if (estaReproduciendo && !destruido && controladorActual != null) {
            handlerProgreso.postDelayed(runnableProgreso, INTERVALO_REFRESCO_MS);
        }
    }

    private String formatearTiempo(long ms) {
        if (ms < 0) ms = 0;
        long totalSegundos = ms / 1000L;
        long horas = totalSegundos / 3600L;
        long minutos = (totalSegundos % 3600L) / 60L;
        long segundos = totalSegundos % 60L;
        if (horas > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", horas, minutos, segundos);
        }
        return String.format(Locale.US, "%d:%02d", minutos, segundos);
    }

    // ──────────────────────────────────────────────
    //  Color dinámico (Palette)
    // ──────────────────────────────────────────────

    private void extraerColor(Bitmap bitmap) {
        if (colorListener == null) return;
        Palette.from(bitmap).generate(palette -> {
            if (destruido || colorListener == null) return;
            if (palette == null) {
                actividad.runOnUiThread(() -> {
                    if (!destruido) colorListener.onSinColor();
                });
                return;
            }
            int color = palette.getDarkVibrantColor(
                    palette.getDarkMutedColor(0xFF1A1A2E));
            actividad.runOnUiThread(() -> {
                if (!destruido) colorListener.onColor(color);
            });
        });
    }

    // ──────────────────────────────────────────────
    //  UI
    // ──────────────────────────────────────────────

    private void mostrarWidget(String titulo, String artista) {
        widgetReproductor.setVisibility(View.VISIBLE);
        reproductorTitulo.setText(titulo);
        reproductorTitulo.setSelected(true);
        reproductorArtista.setText(artista != null ? artista : "");
        botonPlayPausa.setImageResource(
                estaReproduciendo ? R.drawable.ic_pausa : R.drawable.ic_play);

        View.OnClickListener abrirApp = v -> abrirAppReproductora();
        widgetReproductor.setOnClickListener(abrirApp);
        reproductorTitulo.setOnClickListener(abrirApp);
        reproductorArtista.setOnClickListener(abrirApp);
    }

    private void controlarReproduccion() {
        if (controladorActual != null) {
            if (estaReproduciendo) {
                controladorActual.getTransportControls().pause();
            } else {
                controladorActual.getTransportControls().play();
            }
        } else {
            // Fallback BT: despachar KeyEvent de media al sistema
            enviarKeyEventMedia(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            estaReproduciendo = !estaReproduciendo;
            botonPlayPausa.setImageResource(
                    estaReproduciendo ? R.drawable.ic_pausa : R.drawable.ic_play);
        }
    }

    /** Envía un KeyEvent de media al AudioManager para controlar BT/Radio sin MediaSession. */
    private void enviarKeyEventMedia(int keyCode) {
        try {
            AudioManager am = (AudioManager) actividad.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   keyCode));
            }
        } catch (Exception ignored) {}
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
            Toast.makeText(actividad, actividad.getString(R.string.msg_no_abrir_app), Toast.LENGTH_SHORT).show();
        }
    }

    // ──────────────────────────────────────────────
    //  Utilidades
    // ──────────────────────────────────────────────

    private boolean tienePermisoNotificaciones() {
        long ahora = android.os.SystemClock.elapsedRealtime();
        if (ahora - cachePermTs < CACHE_PERM_TTL_MS) return cachePerm;
        String activos = android.provider.Settings.Secure.getString(
                actividad.getContentResolver(), "enabled_notification_listeners");
        cachePerm = activos != null && activos.contains(actividad.getPackageName());
        cachePermTs = ahora;
        return cachePerm;
    }
}