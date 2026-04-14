package codigo.cafe.alsvinlaunch.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BroadcastSnifferActivity extends Activity {

    private TextView tvLog;
    private ScrollView scrollView;
    private BroadcastReceiver espía;
    private final StringBuilder log = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Layout programático (sin XML) ──
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#FF1A1A2E"));
        root.setPadding(16, 32, 16, 16);

        // Título
        TextView titulo = new TextView(this);
        titulo.setText("📡 Broadcast Sniffer — Radio HiveOS");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(16f);
        titulo.setPadding(0, 0, 0, 16);
        root.addView(titulo);

        // Botones
        LinearLayout botonesRow = new LinearLayout(this);
        botonesRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnLimpiar = new Button(this);
        btnLimpiar.setText("Limpiar");
        btnLimpiar.setOnClickListener(v -> {
            log.setLength(0);
            tvLog.setText("Esperando broadcasts...\n");
        });

        Button btnCopiar = new Button(this);
        btnCopiar.setText("Copiar todo");
        btnCopiar.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("sniffer", log.toString()));
            android.widget.Toast.makeText(this, "¡Copiado!", android.widget.Toast.LENGTH_SHORT).show();
        });

        Button btnCerrar = new Button(this);
        btnCerrar.setText("Cerrar");
        btnCerrar.setOnClickListener(v -> finish());

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnParams.setMargins(4, 0, 4, 0);
        botonesRow.addView(btnLimpiar, btnParams);
        botonesRow.addView(btnCopiar, btnParams);
        botonesRow.addView(btnCerrar, btnParams);
        root.addView(botonesRow);

        // Log scrolleable
        scrollView = new ScrollView(this);
        tvLog = new TextView(this);
        tvLog.setText("Esperando broadcasts...\n");
        tvLog.setTextColor(Color.parseColor("#00FF88"));
        tvLog.setTextSize(11f);
        tvLog.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvLog.setPadding(8, 8, 8, 8);

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = 12;
        scrollView.addView(tvLog);
        root.addView(scrollView, scrollParams);

        setContentView(root);

        registrarEspía();
    }

    private void registrarEspía() {
        IntentFilter filtro = new IntentFilter();

        // ── Media genérico ──
        filtro.addAction(Intent.ACTION_MEDIA_BUTTON);
        filtro.addAction("android.media.AUDIO_BECOMING_NOISY");
        filtro.addAction("android.media.ACTION_SCO_AUDIO_STATE_UPDATED");

        // ── Radios chinas / HiveOS comunes ──
        filtro.addAction("com.hiworld.radio.STATE");
        filtro.addAction("com.hiworld.radio.INFO");
        filtro.addAction("com.hiworld.fmradio");
        filtro.addAction("hiveos.radio.state");
        filtro.addAction("hiveos.media.changed");
        filtro.addAction("android.car.media.CAR_RADIO");
        filtro.addAction("com.android.music.metachanged");
        filtro.addAction("com.android.music.playstatechanged");
        filtro.addAction("com.android.music.playbackcomplete");

        // ── Changan específicos (posibles) ──
        filtro.addAction("com.changan.radio");
        filtro.addAction("com.changan.media");
        filtro.addAction("com.dsp.radio");
        filtro.addAction("dsp.radio.info");
        filtro.addAction("dsp.fm.frequency");

        // ── Audio/media del sistema ──
        filtro.addAction("android.media.VOLUME_CHANGED_ACTION");
        filtro.addAction("android.intent.action.HEADSET_PLUG");

        espía = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        .format(new Date());

                StringBuilder entrada = new StringBuilder();
                entrada.append("\n──────────────────\n");
                entrada.append("⏰ ").append(hora).append("\n");
                entrada.append("📨 ACTION: ").append(intent.getAction()).append("\n");

                Bundle extras = intent.getExtras();
                if (extras != null) {
                    for (String key : extras.keySet()) {
                        entrada.append("   🔑 ").append(key)
                                .append(" = ").append(extras.get(key)).append("\n");
                    }
                } else {
                    entrada.append("   (sin extras)\n");
                }

                log.append(entrada);

                handler.post(() -> {
                    tvLog.setText(log.toString());
                    // Auto-scroll al final
                    scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
                });
            }
        };

        registerReceiver(espía, filtro);
        agregarLog("✅ Sniffer activo — Abre la radio nativa ahora");
    }

    private void agregarLog(String texto) {
        log.append(texto).append("\n");
        tvLog.setText(log.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (espía != null) unregisterReceiver(espía);
    }
}