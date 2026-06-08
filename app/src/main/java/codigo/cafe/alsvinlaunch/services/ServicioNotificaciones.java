package codigo.cafe.alsvinlaunch.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaMetadata;
import java.util.Locale;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServicioNotificaciones extends NotificationListenerService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Acción para enviar info de lo que suena
    public static final String ACCION_MEDIA_CAMBIO = "codigo.cafe.alsvinlaunch.MEDIA_CAMBIO";

    // Intent extra para indicar que el broadcast viene del AVRCP BT
    public static final String EXTRA_FUENTE_AVRCP = "fuente_avrcp";

    // ── Receptor AVRCP ──────────────────────────────────────────────────────
    // El stack BT de Android emite estos intents cuando el teléfono conectado
    // via A2DP envía metadata de la canción en curso (AVRCP).
    private static final String AVRCP_TRACK_EVENT =
            "android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT";
    private static final String AVRCP_META_EXTRA =
            "android.bluetooth.avrcp-controller.profile.extra.METADATA";

    // Intents alternativos usados por algunos stacks BT de fabricantes chinos
    private static final String MUSIC_META_CHANGED  = "com.android.music.metachanged";
    private static final String MUSIC_PLAY_STATUS   = "com.android.music.playstatechanged";
    private static final String HICAR_META_CHANGED  = "android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED";
    // Stacks MediaTek y otros OEM
    private static final String MTK_AVRCP_META      = "com.mediatek.bluetooth.avrcp.TRACK_CHANGED";
    private static final String AVRCP_META_CHANGED  = "android.media.METADATA_CHANGED";
    private static final String AVRCP2_TRACK_EVENT  = "android.bluetooth.avrcp.action.TRACK_CHANGED";
    // Headunit TS8227L (radio FM com.ts.main.radio)
    private static final String TS_RADIO_INFO       = "com.ts.radio.ACTION_RADIO_INFO";
    private static final String TS_RADIO_FREQ       = "com.ts.radio.FREQUENCY_CHANGED";
    private static final String TS_MEDIA_META       = "com.ts.dvdplayer.MEDIA_META_CHANGED";

    private BroadcastReceiver receptorAvrcp;

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        registrarReceptorAvrcp();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if (receptorAvrcp != null) {
            try { unregisterReceiver(receptorAvrcp); } catch (Exception ignored) {}
            receptorAvrcp = null;
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        executor.execute(this::verificarMediaActiva);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        executor.execute(this::verificarMediaActiva);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        executor.execute(this::verificarMediaActiva);
    }

    // ── Receptor AVRCP ────────────────────────────────────────────────────────

    private void registrarReceptorAvrcp() {
        receptorAvrcp = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String accion = intent.getAction();
                if (accion == null) return;

                String titulo   = null;
                String artista  = null;
                String album    = null;
                boolean reproduciendo = true;

                switch (accion) {
                    case AVRCP_TRACK_EVENT: {
                        // Standard Android AVRCP Controller: metadata como parcelable
                        MediaMetadata meta = intent.getParcelableExtra(AVRCP_META_EXTRA);
                        if (meta != null) {
                            titulo  = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
                            artista = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
                            album   = meta.getString(MediaMetadata.METADATA_KEY_ALBUM);
                        }
                        break;
                    }
                    case MUSIC_META_CHANGED: {
                        // Stacks BT alternativos (Qualcomm, MTK, etc.) e implementaciones
                        // de fabricantes chinos: envían metadata como extras de cadena.
                        titulo  = primerNoVacio(
                                intent.getStringExtra("track"),
                                intent.getStringExtra("title"));
                        artista = intent.getStringExtra("artist");
                        album   = intent.getStringExtra("album");
                        break;
                    }
                    case MUSIC_PLAY_STATUS: {
                        // Estado play/pause
                        reproduciendo = intent.getBooleanExtra("playing", false);
                        titulo        = primerNoVacio(
                                intent.getStringExtra("track"),
                                intent.getStringExtra("title"));
                        artista       = intent.getStringExtra("artist");
                        album         = intent.getStringExtra("album");
                        break;
                    }
                    case HICAR_META_CHANGED:
                    case MTK_AVRCP_META:
                    case AVRCP2_TRACK_EVENT:
                    case AVRCP_META_CHANGED: {
                        titulo  = primerNoVacio(
                                intent.getStringExtra("title"),
                                intent.getStringExtra("track"),
                                intent.getStringExtra("name"));
                        artista = primerNoVacio(
                                intent.getStringExtra("artist"),
                                intent.getStringExtra("performer"));
                        album   = intent.getStringExtra("album");
                        break;
                    }
                    case TS_RADIO_INFO:
                    case TS_RADIO_FREQ: {
                        // Radio FM del headunit TS8227L: la frecuencia viene como float o string
                        String freq = primerNoVacio(
                                intent.getStringExtra("frequency"),
                                intent.getStringExtra("freq"));
                        if (freq == null) {
                            float freqF = intent.getFloatExtra("frequency", 0f);
                            if (freqF > 0) freq = String.format(Locale.US, "%.1f MHz", freqF);
                        } else {
                            // Asegurarse que tenga unidad
                            if (!freq.contains("MHz") && !freq.contains("FM")) {
                                freq = freq + " MHz";
                            }
                        }
                        titulo  = "Radio FM";
                        artista = freq != null ? freq : "";
                        break;
                    }
                    case TS_MEDIA_META: {
                        titulo  = primerNoVacio(
                                intent.getStringExtra("title"),
                                intent.getStringExtra("name"));
                        artista = intent.getStringExtra("artist");
                        album   = intent.getStringExtra("album");
                        break;
                    }
                    default:
                        return;
                }

                // Sólo emitimos si tenemos algún dato útil
                if (titulo == null) titulo = "";
                if (artista == null) artista = "";
                if (album == null) album = "";

                Intent broadcast = new Intent(ACCION_MEDIA_CAMBIO);
                broadcast.putExtra("titulo",        titulo);
                broadcast.putExtra("artista",       artista);
                broadcast.putExtra("album",         album);
                broadcast.putExtra("reproduciendo", reproduciendo);
                broadcast.putExtra("paquete",       "com.android.bluetooth");
                broadcast.putExtra(EXTRA_FUENTE_AVRCP, true);
                sendBroadcast(broadcast);
            }
        };

        IntentFilter filtro = new IntentFilter();
        filtro.addAction(AVRCP_TRACK_EVENT);
        filtro.addAction(MUSIC_META_CHANGED);
        filtro.addAction(MUSIC_PLAY_STATUS);
        filtro.addAction(HICAR_META_CHANGED);
        filtro.addAction(MTK_AVRCP_META);
        filtro.addAction(AVRCP2_TRACK_EVENT);
        filtro.addAction(AVRCP_META_CHANGED);
        filtro.addAction(TS_RADIO_INFO);
        filtro.addAction(TS_RADIO_FREQ);
        filtro.addAction(TS_MEDIA_META);
        try {
            registerReceiver(receptorAvrcp, filtro);
        } catch (Exception ignored) {}
    }

    // ── MediaSession estándar ────────────────────────────────────────────────

    private void verificarMediaActiva() {
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            List<MediaController> controladores = msm.getActiveSessions(
                    new android.content.ComponentName(this, ServicioNotificaciones.class)
            );

            if (!controladores.isEmpty()) {
                MediaController controlador = elegirMejorControlador(controladores);
                if (controlador == null) controlador = controladores.get(0);
                MediaMetadata metadata = controlador.getMetadata();
                PlaybackState estado = controlador.getPlaybackState();

                String titulo  = null;
                String artista = null;
                String album   = null;
                if (metadata != null) {
                    titulo  = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                    artista = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    album   = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
                }
                boolean reproduciendo = estado != null &&
                        estado.getState() == PlaybackState.STATE_PLAYING;

                Intent intent = new Intent(ACCION_MEDIA_CAMBIO);
                intent.putExtra("titulo",        titulo  != null ? titulo  : "");
                intent.putExtra("artista",       artista != null ? artista : "");
                intent.putExtra("album",         album   != null ? album   : "");
                intent.putExtra("reproduciendo", reproduciendo);
                intent.putExtra("paquete",       controlador.getPackageName());
                sendBroadcast(intent);
            } else {
                Intent intent = new Intent(ACCION_MEDIA_CAMBIO);
                intent.putExtra("titulo",        "");
                intent.putExtra("artista",       "");
                intent.putExtra("reproduciendo", false);
                sendBroadcast(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private MediaController elegirMejorControlador(List<MediaController> controladores) {
        if (controladores == null || controladores.isEmpty()) return null;
        MediaController reproduciendo = null;
        MediaController conMetadata   = null;
        for (MediaController c : controladores) {
            if (c == null) continue;
            PlaybackState s = c.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                reproduciendo = c;
                break;
            }
            if (conMetadata == null && c.getMetadata() != null) {
                conMetadata = c;
            }
        }
        if (reproduciendo != null) return reproduciendo;
        if (conMetadata   != null) return conMetadata;
        return controladores.get(0);
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private static String primerNoVacio(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }
}
