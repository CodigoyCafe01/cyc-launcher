package codigo.cafe.alsvinlaunch.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import codigo.cafe.alsvinlaunch.services.ServicioNotificaciones;
import codigo.cafe.alsvinlaunch.util.LocaleHelper;

public class BroadcastSnifferActivity extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private static final int TAB_TODOS      = 0;
    private static final int TAB_BT         = 1;
    private static final int TAB_RADIO      = 2;
    private static final int TAB_SESIONES   = 3;
    private int tabActual = TAB_TODOS;

    // ── UI ────────────────────────────────────────────────────────────────────
    private TextView tvLog;
    private ScrollView scrollView;
    private TextView[] tabBotones;

    // ── Datos ─────────────────────────────────────────────────────────────────
    private final StringBuilder logTodos    = new StringBuilder();
    private final StringBuilder logBt       = new StringBuilder();
    private final StringBuilder logRadio    = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private BroadcastReceiver espía;

    // Palabras clave para clasificar automáticamente
    private static final String[] PALABRAS_BT = {
            "bluetooth", "avrcp", "a2dp", "headset", "hfp", "sco", "pbap"
    };
    private static final String[] PALABRAS_RADIO = {
            "radio", "fm", "frequency", "freq", "tuner", "ts.main", "ts.radio",
            "dvdplayer", "hiworld", "changan", "dsp"
    };

    // ── Acciones a escuchar ───────────────────────────────────────────────────
    private static final String[] ACCIONES = {
            // Media genérico
            "com.android.music.metachanged",
            "com.android.music.playstatechanged",
            "com.android.music.playbackcomplete",
            "android.media.METADATA_CHANGED",
            "android.media.AUDIO_BECOMING_NOISY",
            "android.media.ACTION_SCO_AUDIO_STATE_UPDATED",
            "android.intent.action.MEDIA_BUTTON",
            "android.intent.action.HEADSET_PLUG",
            "android.media.VOLUME_CHANGED_ACTION",

            // AVRCP estándar Android
            "android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT",
            "android.bluetooth.avrcp-controller.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.avrcp-controller.profile.action.PLAYER_APP_SETTING_CHANGED",
            "android.bluetooth.avrcp.action.TRACK_CHANGED",
            "android.bluetooth.avrcp.action.PLAY_STATUS_CHANGED",

            // A2DP / BT genérico
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED",
            "android.bluetooth.device.action.ACL_CONNECTED",
            "android.bluetooth.device.action.ACL_DISCONNECTED",
            "android.bluetooth.adapter.action.STATE_CHANGED",

            // MediaTek BT stack
            "com.mediatek.bluetooth.avrcp.TRACK_CHANGED",
            "com.mediatek.bluetooth.avrcp.PLAY_STATUS",
            "com.mediatek.bluetooth.PROFILE_STATE_CHANGED",

            // TS8227L — radio FM (com.ts.main)
            "com.ts.radio.ACTION_RADIO_INFO",
            "com.ts.radio.FREQUENCY_CHANGED",
            "com.ts.radio.STATE_CHANGED",
            "com.ts.radio.PLAY_STATE",
            "com.ts.main.RADIO_INFO",
            "com.ts.main.MEDIA_META",
            "com.ts.dvdplayer.MEDIA_META_CHANGED",
            "com.ts.dvdplayer.PLAY_STATE",

            // HiveOS / radios chinas genéricas
            "com.hiworld.radio.STATE",
            "com.hiworld.radio.INFO",
            "com.hiworld.fmradio",
            "hiveos.radio.state",
            "hiveos.media.changed",
            "android.car.media.CAR_RADIO",

            // Changan / DSP
            "com.changan.radio",
            "com.changan.media",
            "com.dsp.radio",
            "dsp.radio.info",
            "dsp.fm.frequency",

            // Nuestro propio broadcast (para ver qué está mandando ServicioNotificaciones)
            ServicioNotificaciones.ACCION_MEDIA_CAMBIO,
    };

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0D0D1A"));
        root.setPadding(12, 28, 12, 12);
        setContentView(root);

        // ── Encabezado ────────────────────────────────────────────────────────
        TextView titulo = new TextView(this);
        titulo.setText("📡 Broadcast Sniffer — TS8227L");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(15f);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        titulo.setPadding(0, 0, 0, 10);
        root.addView(titulo);

        // ── Tabs ──────────────────────────────────────────────────────────────
        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);

        String[] tabNombres = {"🌐 Todos", "🔵 BT", "📻 Radio", "🎵 Sesiones"};
        tabBotones = new TextView[tabNombres.length];
        for (int i = 0; i < tabNombres.length; i++) {
            final int idx = i;
            TextView tab = new TextView(this);
            tab.setText(tabNombres[i]);
            tab.setTextSize(12f);
            tab.setPadding(18, 10, 18, 10);
            tab.setClickable(true);
            tab.setFocusable(true);
            tab.setOnClickListener(v -> cambiarTab(idx));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.setMargins(0, 0, 6, 0);
            tabsRow.addView(tab, tp);
            tabBotones[i] = tab;
        }
        hScroll.addView(tabsRow);
        root.addView(hScroll);
        actualizarEstiloTabs();

        // ── Botones de acción ─────────────────────────────────────────────────
        LinearLayout accionesRow = new LinearLayout(this);
        accionesRow.setOrientation(LinearLayout.HORIZONTAL);
        accionesRow.setPadding(0, 8, 0, 8);

        agregarBoton(accionesRow, "🗑 Limpiar", () -> {
            logTodos.setLength(0);
            logBt.setLength(0);
            logRadio.setLength(0);
            tvLog.setText("Esperando broadcasts...\n");
        });
        agregarBoton(accionesRow, "📋 Copiar", () -> {
            android.content.ClipboardManager cb =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(android.content.ClipData.newPlainText("sniffer", logActual()));
            Toast.makeText(this, "¡Copiado!", Toast.LENGTH_SHORT).show();
        });
        agregarBoton(accionesRow, "🔍 Sesiones", this::dumpMediaSessions);
        agregarBoton(accionesRow, "✖ Cerrar", this::finish);
        root.addView(accionesRow);

        // ── Log ───────────────────────────────────────────────────────────────
        scrollView = new ScrollView(this);
        tvLog = new TextView(this);
        tvLog.setText("Esperando broadcasts...\nAbre la Radio o reproduce música por BT.\n");
        tvLog.setTextColor(Color.parseColor("#00FF88"));
        tvLog.setTextSize(10.5f);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setPadding(8, 8, 8, 8);
        scrollView.addView(tvLog);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sp.topMargin = 4;
        root.addView(scrollView, sp);

        registrarEspía();
        // Volcar sesiones al arrancar
        handler.postDelayed(this::dumpMediaSessions, 500);
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private void cambiarTab(int idx) {
        tabActual = idx;
        actualizarEstiloTabs();
        if (idx == TAB_SESIONES) {
            dumpMediaSessions();
        } else {
            tvLog.setText(logActual().length() > 0 ? logActual() : "Esperando broadcasts...\n");
            autoScroll();
        }
    }

    private void actualizarEstiloTabs() {
        for (int i = 0; i < tabBotones.length; i++) {
            boolean activo = i == tabActual;
            tabBotones[i].setBackgroundColor(activo
                    ? Color.parseColor("#6C63FF") : Color.parseColor("#1E2140"));
            tabBotones[i].setTextColor(activo ? Color.WHITE : Color.parseColor("#8888AA"));
        }
    }

    private String logActual() {
        switch (tabActual) {
            case TAB_BT:     return logBt.toString();
            case TAB_RADIO:  return logRadio.toString();
            default:         return logTodos.toString();
        }
    }

    // ── Botón helper ──────────────────────────────────────────────────────────

    private void agregarBoton(LinearLayout parent, String texto, Runnable accion) {
        Button btn = new Button(this);
        btn.setText(texto);
        btn.setTextSize(11f);
        btn.setAllCaps(false);
        btn.setOnClickListener(v -> accion.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(2, 0, 2, 0);
        parent.addView(btn, p);
    }

    // ── Receptor ──────────────────────────────────────────────────────────────

    private void registrarEspía() {
        IntentFilter filtro = new IntentFilter();
        for (String accion : ACCIONES) filtro.addAction(accion);

        espía = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String accion = intent.getAction();
                if (accion == null) return;
                String hora = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                        .format(new Date());

                StringBuilder sb = new StringBuilder();
                sb.append("\n──────────── ").append(hora).append(" ────────────\n");
                sb.append("ACTION: ").append(accion).append("\n");
                if (intent.getPackage() != null)
                    sb.append("PKG:    ").append(intent.getPackage()).append("\n");

                Bundle extras = intent.getExtras();
                if (extras != null && !extras.isEmpty()) {
                    for (String key : extras.keySet()) {
                        Object val = extras.get(key);
                        String valStr = (val != null) ? val.toString() : "null";
                        // Truncar valores muy largos
                        if (valStr.length() > 120) valStr = valStr.substring(0, 120) + "…";
                        sb.append("  ").append(key).append(" = ").append(valStr).append("\n");
                    }
                } else {
                    sb.append("  (sin extras)\n");
                }

                String entrada = sb.toString();
                String accionLow = accion.toLowerCase(Locale.US);

                // Clasificar en tabs
                boolean esBt    = contieneAlguna(accionLow, PALABRAS_BT);
                boolean esRadio = contieneAlguna(accionLow, PALABRAS_RADIO);

                logTodos.append(entrada);
                if (esBt)    logBt.append(entrada);
                if (esRadio) logRadio.append(entrada);

                handler.post(() -> {
                    if (tabActual == TAB_TODOS
                            || (tabActual == TAB_BT    && esBt)
                            || (tabActual == TAB_RADIO && esRadio)) {
                        tvLog.setText(logActual());
                        autoScroll();
                    }
                });
            }
        };

        try {
            registerReceiver(espía, filtro);
            agregarEntradaLog("✅ Sniffer activo — escuchando " + ACCIONES.length + " acciones\n"
                    + "Abre Radio nativa o reproduce BT para capturar broadcasts.");
        } catch (Exception e) {
            agregarEntradaLog("❌ Error al registrar receptor: " + e.getMessage());
        }
    }

    // ── Dump de MediaSessions ─────────────────────────────────────────────────

    private void dumpMediaSessions() {
        tabActual = TAB_SESIONES;
        actualizarEstiloTabs();

        StringBuilder sb = new StringBuilder();
        sb.append("════════════ MEDIA SESSIONS ════════════\n");
        sb.append(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()))
                .append("\n\n");

        // Estado de AudioManager
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            sb.append("🔊 AudioManager.isMusicActive() = ")
                    .append(am != null && am.isMusicActive()).append("\n\n");
        } catch (Exception e) {
            sb.append("AudioManager ERROR: ").append(e.getMessage()).append("\n\n");
        }

        // MediaSessions activas
        try {
            MediaSessionManager msm =
                    (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(this, ServicioNotificaciones.class);
            List<MediaController> controladores = msm.getActiveSessions(cn);

            if (controladores.isEmpty()) {
                sb.append("⚠️ Sin sesiones activas.\n");
                sb.append("   Asegúrate de que AlsvinLauncher tiene permiso\n");
                sb.append("   de acceso a notificaciones.\n");
            } else {
                sb.append("📱 Sesiones encontradas: ").append(controladores.size()).append("\n\n");
                for (int i = 0; i < controladores.size(); i++) {
                    dumpControlador(sb, i + 1, controladores.get(i));
                }
            }
        } catch (SecurityException se) {
            sb.append("❌ Sin permiso de NotificationListener.\n");
            sb.append("   Ve a: Ajustes → Seguridad → Acceso a notificaciones\n");
        } catch (Exception e) {
            sb.append("❌ Error: ").append(e.getMessage()).append("\n");
        }

        // App en primer plano
        sb.append("\n────────────────────────────────────────\n");
        sb.append("🖥️  App en primer plano:\n");
        try {
            android.app.ActivityManager acm =
                    (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<android.app.ActivityManager.RunningAppProcessInfo> procs =
                    acm.getRunningAppProcesses();
            if (procs != null) {
                for (android.app.ActivityManager.RunningAppProcessInfo p : procs) {
                    if (p.importance ==
                            android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        sb.append("   ").append(p.processName).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            sb.append("   (no disponible)\n");
        }

        final String resultado = sb.toString();
        handler.post(() -> {
            tvLog.setText(resultado);
            autoScroll();
        });
    }

    private void dumpControlador(StringBuilder sb, int num, MediaController ctrl) {
        sb.append("┌─ Sesión #").append(num).append(" ─────────────────────\n");
        sb.append("│ Paquete : ").append(ctrl.getPackageName()).append("\n");

        // PlaybackState
        PlaybackState estado = ctrl.getPlaybackState();
        if (estado != null) {
            sb.append("│ Estado  : ").append(estadoStr(estado.getState())).append("\n");
            sb.append("│ Posición: ").append(estado.getPosition() / 1000).append("s\n");
            sb.append("│ Vel.    : ").append(estado.getPlaybackSpeed()).append("x\n");
            sb.append("│ Acciones: 0x").append(Long.toHexString(estado.getActions())).append("\n");
        } else {
            sb.append("│ Estado  : null\n");
        }

        // MediaMetadata
        MediaMetadata meta = ctrl.getMetadata();
        if (meta != null) {
            sb.append("│ TITLE   : ").append(meta.getString(MediaMetadata.METADATA_KEY_TITLE)).append("\n");
            sb.append("│ ARTIST  : ").append(meta.getString(MediaMetadata.METADATA_KEY_ARTIST)).append("\n");
            sb.append("│ ALBUM   : ").append(meta.getString(MediaMetadata.METADATA_KEY_ALBUM)).append("\n");
            sb.append("│ DISP_TIT: ").append(meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)).append("\n");
            sb.append("│ DISP_SUB: ").append(meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)).append("\n");
            sb.append("│ MEDIA_ID: ").append(meta.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)).append("\n");
            long dur = meta.getLong(MediaMetadata.METADATA_KEY_DURATION);
            sb.append("│ DURATION: ").append(dur > 0 ? dur / 1000 + "s" : "0 (sin duración)").append("\n");
            sb.append("│ ART     : ").append(
                    meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null
                            ? "✅ disponible" : "null").append("\n");
        } else {
            sb.append("│ Metadata: null ← aquí está el problema\n");
        }

        // Queue
        try {
            List<android.media.session.MediaSession.QueueItem> queue = ctrl.getQueue();
            if (queue != null && !queue.isEmpty()) {
                sb.append("│ Queue[0]: ");
                android.media.MediaDescription desc = queue.get(0).getDescription();
                if (desc != null) {
                    sb.append(desc.getTitle()).append(" / ").append(desc.getSubtitle());
                }
                sb.append("\n│ Queue sz: ").append(queue.size()).append("\n");
            } else {
                sb.append("│ Queue   : null/vacía\n");
            }
        } catch (Exception e) {
            sb.append("│ Queue   : error — ").append(e.getMessage()).append("\n");
        }

        // Extras de la sesión
        try {
            Bundle extras = ctrl.getExtras();
            if (extras != null && !extras.isEmpty()) {
                sb.append("│ Extras:\n");
                for (String k : extras.keySet()) {
                    sb.append("│   ").append(k).append(" = ").append(extras.get(k)).append("\n");
                }
            }
        } catch (Exception ignored) {}

        sb.append("└──────────────────────────────────────\n\n");
    }

    private String estadoStr(int estado) {
        switch (estado) {
            case PlaybackState.STATE_PLAYING:    return "▶ PLAYING";
            case PlaybackState.STATE_PAUSED:     return "⏸ PAUSED";
            case PlaybackState.STATE_STOPPED:    return "⏹ STOPPED";
            case PlaybackState.STATE_BUFFERING:  return "⏳ BUFFERING";
            case PlaybackState.STATE_CONNECTING: return "🔗 CONNECTING";
            case PlaybackState.STATE_ERROR:      return "❌ ERROR";
            case PlaybackState.STATE_NONE:       return "○ NONE";
            default:                             return "? " + estado;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean contieneAlguna(String texto, String[] palabras) {
        for (String p : palabras) if (texto.contains(p)) return true;
        return false;
    }

    private void agregarEntradaLog(String texto) {
        logTodos.append(texto).append("\n");
        handler.post(() -> {
            tvLog.setText(logActual());
            autoScroll();
        });
    }

    private void autoScroll() {
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (espía != null) {
            try { unregisterReceiver(espía); } catch (Exception ignored) {}
        }
    }
}
