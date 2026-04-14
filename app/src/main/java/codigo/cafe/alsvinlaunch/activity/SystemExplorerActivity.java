package codigo.cafe.alsvinlaunch.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

import codigo.cafe.alsvinlaunch.R;

public class SystemExplorerActivity extends Activity {

    private TextView tvLog;
    private ScrollView scrollView;
    private BroadcastReceiver espía;
    private final StringBuilder log = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean snifferActivo = false;

    // ── Todas las apps nativas encontradas ──
    private static final String PKG = "com.ts.MainUI";

    private static final String[][] APPS_MEDIA = {
            {"Radio",        "com.ts.main.radio.RadioMainActivity"},
            {"USB/Música",   "com.ts.main.Media.USBMainActivity"},
            {"iPod",         "com.ts.main.ipod.IpodMainActivity"},
            {"DVD",          "com.ts.main.Media.DvdMainActivity"},
            {"BT Música",    "com.ts.bt.BtMusicActivity"},
            {"BT Conectar",  "com.ts.bt.BtConnectActivity"},
            {"BT Marcador",  "com.ts.bt.BtDialActivity"},
            {"Radio Ext",    "com.ts.can.CanExRadioActivity"},
    };

    private static final String[][] APPS_CAN = {
            {"Info Carro",   "com.ts.can.CanCarActivity"},
            {"AC",           "com.ts.can.CanACActivity"},
            {"Puertas",      "com.ts.can.CanDoorActivity"},
            {"Radar",        "com.ts.can.CanRadarActivity"},
            {"Cámara CAN",   "com.ts.can.CanCameraActivity"},
            {"CAN Main",     "com.ts.can.CanMainActivity"},
            {"CD Externo",   "com.ts.can.CanExCDActivity"},
            {"Ford Sync",    "com.ts.can.CanFordSyncActivity"},
            {"Golf Status",  "com.ts.can.CanGolfCarStatusActivity"},
    };

    private static final String[][] APPS_TPMS = {
            {"TPMS Llantas", "com.ts.main.tpms.TPMSMainActivity"},
            {"TPMS Config",  "com.ts.main.tpms.TPMSSetMainActivity"},
    };

    private static final String[][] APPS_SISTEMA = {
            {"Navegación",   "com.ts.main.navi.NaviMainActivity"},
            {"Teléfono",     "com.ts.main.TsDailer.TsDailerActivity"},
            {"Cámara 360",   "com.ts.main.view360.View360MainActivity"},
            {"Cámara Tras.", "com.ts.backcar.BackcarMainActivity"},
            {"ADAS",         "com.ts.main.Media.ADASMainActivity"},
            {"Ajustes",      "com.ts.set.SetMainActivity"},
            {"Bluetooth Set","com.ts.set.SettingBtActivity"},
            {"Volumen",      "com.ts.set.SetVolumeActivity"},
    };

    // ── Broadcasts a escuchar ──
    private static final String[] BROADCASTS = {
            "com.ts.main.radio.STATE",
            "com.ts.main.radio.FREQUENCY",
            "com.ts.radio.info",
            "com.ts.media.changed",
            "com.ts.can.AC_STATE",
            "com.ts.can.DOOR_STATE",
            "com.ts.can.MCU_DATA",
            "com.ts.can.CAR_INFO",
            "com.ts.tpms.DATA",
            "com.ts.bt.STATE",
            "com.ts.bt.MUSIC",
            "android.media.VOLUME_CHANGED_ACTION",
            "android.intent.action.HEADSET_PLUG",
            "com.android.music.metachanged",
            "com.android.music.playstatechanged",
            "android.media.ACTION_SCO_AUDIO_STATE_UPDATED",
            // Intentos extra
            "com.ts.main.Media.STATE",
            "com.ts.can.CAN_DATA",
            "com.ts.system.STATE",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_explorer);

        // Vistas
        tvLog       = findViewById(R.id.tv_log);
        scrollView  = findViewById(R.id.scroll_log);
        txtEstado   = findViewById(R.id.txt_sniffer_estado);
        txtCount    = findViewById(R.id.txt_broadcasts_count);

        // Tabs
        findViewById(R.id.tab_media)  .setOnClickListener(v -> mostrarDialogoApps("📻 Media",    APPS_MEDIA));
        findViewById(R.id.tab_can)    .setOnClickListener(v -> mostrarDialogoApps("🚗 CAN",      APPS_CAN));
        findViewById(R.id.tab_tpms)   .setOnClickListener(v -> mostrarDialogoApps("🛞 TPMS",     APPS_TPMS));
        findViewById(R.id.tab_sistema).setOnClickListener(v -> mostrarDialogoApps("⚙️ Sistema",  APPS_SISTEMA));
        findViewById(R.id.tab_sniffer).setOnClickListener(v -> mostrarPanelSniffer());

        // Botones inferiores
        findViewById(R.id.btn_limpiar) .setOnClickListener(v -> { log.setLength(0); tvLog.setText(""); broadcastCount = 0; actualizarContador(); });
        findViewById(R.id.btn_copiar)  .setOnClickListener(v -> copiarLog());
        findViewById(R.id.btn_verificar).setOnClickListener(v -> probarTodasLasApps());
        findViewById(R.id.btn_cerrar)  .setOnClickListener(v -> finish());

        agregarLog("🚀 SystemExplorer listo — Ts8227L / com.ts.MainUI");
        agregarLog("⚠️  Algunas apps solo responden estando en el carro\n");
    }

    private TextView txtEstado;
    private TextView txtCount;
    private int broadcastCount = 0;

    private void actualizarContador() {
        handler.post(() -> {
            txtEstado.setText("📡 Sniffer: " + (snifferActivo ? "ACTIVO 🟢" : "INACTIVO 🔴"));
            txtCount.setText(broadcastCount + " broadcasts capturados");
        });
    }

    private void copiarLog() {
        android.content.ClipboardManager cb =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cb.setPrimaryClip(android.content.ClipData.newPlainText("log", log.toString()));
        Toast.makeText(this, "¡Copiado!", Toast.LENGTH_SHORT).show();
    }
    // ══════════════════════════════════
    //  DIÁLOGO DE APPS POR CATEGORÍA
    // ══════════════════════════════════

    private void mostrarDialogoApps(String categoria, String[][] apps) {
        String[] nombres = new String[apps.length];
        for (int i = 0; i < apps.length; i++) nombres[i] = apps[i][0];

        new AlertDialog.Builder(this)
                .setTitle(categoria)
                .setItems(nombres, (d, which) -> lanzarApp(apps[which][0], apps[which][1]))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void lanzarApp(String nombre, String clase) {
        agregarLog("▶ Lanzando: " + nombre);
        agregarLog("  PKG: " + PKG);
        agregarLog("  CLS: " + clase);
        try {
            Intent i = new Intent();
            i.setClassName(PKG, clase);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            agregarLog("  ✅ OK\n");
        } catch (Exception e) {
            agregarLog("  ❌ Error: " + e.getMessage() + "\n");
        }
    }

    // ══════════════════════════════════
    //  PANEL SNIFFER
    // ══════════════════════════════════

    private void mostrarPanelSniffer() {
        String estado = snifferActivo ? "ACTIVO 🟢" : "INACTIVO 🔴";
        String accion = snifferActivo ? "Detener Sniffer" : "Iniciar Sniffer";

        new AlertDialog.Builder(this)
                .setTitle("📡 Sniffer — Estado: " + estado)
                .setMessage("Escucha " + BROADCASTS.length + " broadcasts del sistema Ts8227L.\n\n" +
                        "Cuando estés en el carro:\n" +
                        "1. Inicia el sniffer\n" +
                        "2. Abre la radio, cambia estaciones\n" +
                        "3. Sube/baja AC\n" +
                        "4. Abre/cierra puertas\n" +
                        "5. Revisa el log aquí abajo\n" +
                        "6. Copia y comparte el resultado")
                .setPositiveButton(accion, (d, w) -> {
                    if (snifferActivo) detenerSniffer();
                    else iniciarSniffer();
                })
                .setNeutralButton("Probar todas las apps", (d, w) -> probarTodasLasApps())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void iniciarSniffer() {
        IntentFilter filtro = new IntentFilter();
        for (String action : BROADCASTS) filtro.addAction(action);

        espía = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        .format(new Date());
                StringBuilder sb = new StringBuilder();
                sb.append("\n📨 ").append(hora).append(" — ").append(intent.getAction()).append("\n");
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    for (String key : extras.keySet()) {
                        sb.append("   🔑 ").append(key).append(" = ")
                                .append(extras.get(key)).append("\n");
                    }
                } else {
                    sb.append("   (sin extras)\n");
                }
                handler.post(() -> agregarLog(sb.toString()));
                broadcastCount++;
                actualizarContador();
            }
        };

        registerReceiver(espía, filtro);
        snifferActivo = true;
        agregarLog("📡 Sniffer INICIADO — escuchando " + BROADCASTS.length + " broadcasts\n");
        Toast.makeText(this, "Sniffer activo", Toast.LENGTH_SHORT).show();
    }

    private void detenerSniffer() {
        if (espía != null) {
            unregisterReceiver(espía);
            espía = null;
        }
        snifferActivo = false;
        agregarLog("🔴 Sniffer DETENIDO\n");
    }

    // ══════════════════════════════════
    //  PROBAR TODAS LAS APPS
    // ══════════════════════════════════

    private void probarTodasLasApps() {
        agregarLog("\n🔄 Probando TODAS las apps (sin lanzar, solo verificando)...\n");
        List<String[]> todas = new ArrayList<>();
        todas.addAll(Arrays.asList(APPS_MEDIA));
        todas.addAll(Arrays.asList(APPS_CAN));
        todas.addAll(Arrays.asList(APPS_TPMS));
        todas.addAll(Arrays.asList(APPS_SISTEMA));

        android.content.pm.PackageManager pm = getPackageManager();
        for (String[] app : todas) {
            Intent i = new Intent();
            i.setClassName(PKG, app[1]);
            boolean existe = pm.resolveActivity(i, 0) != null;
            agregarLog((existe ? "  ✅ " : "  ❌ ") + app[0] + " → " + (existe ? "DISPONIBLE" : "no encontrada"));
        }
        agregarLog("\n✔ Verificación completa\n");
    }

    // ══════════════════════════════════
    //  UTILIDADES
    // ══════════════════════════════════

    private Button crearBoton(String texto, Runnable accion) {
        Button b = new Button(this);
        b.setText(texto);
        b.setTextSize(11f);
        b.setBackgroundColor(Color.parseColor("#1E3A5F"));
        b.setTextColor(Color.WHITE);
        b.setOnClickListener(v -> accion.run());
        return b;
    }

    private void agregarLog(String texto) {
        log.append(texto).append("\n");
        handler.post(() -> {
            tvLog.setText(log.toString());
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detenerSniffer();
    }
}