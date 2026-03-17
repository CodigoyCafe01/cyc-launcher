package codigo.cafe.alsvinlaunch;

import android.animation.ArgbEvaluator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import codigo.cafe.alsvinlaunch.managers.BluetoothContactsManager;
import codigo.cafe.alsvinlaunch.managers.BtNotifReceiverManager;
import codigo.cafe.alsvinlaunch.managers.ClockManager;
import codigo.cafe.alsvinlaunch.managers.ContactsDialogHelper;
import codigo.cafe.alsvinlaunch.managers.KioskManager;
import codigo.cafe.alsvinlaunch.managers.MediaWidgetManager;
import codigo.cafe.alsvinlaunch.managers.NotifPanelManager;
import codigo.cafe.alsvinlaunch.managers.PermissionsManager;
import codigo.cafe.alsvinlaunch.managers.ShortcutManager;
import codigo.cafe.alsvinlaunch.managers.StatusIndicatorManager;
import codigo.cafe.alsvinlaunch.managers.SystemPanelManager;
import codigo.cafe.alsvinlaunch.managers.ThemeManager;

/**
 * Actividad principal del launcher.
 * Orquesta los managers especializados sin lógica de negocio propia.
 */
public class MainActivity extends Activity {

    private static final String NOMBRE_PREFS        = "alsvin_launcher_prefs";
    private static final String PREF_COLOR_DINAMICO = "color_dinamico";
    private static final int    COLOR_BASE_FONDO    = 0xFF1A1A2E;

    // ── Managers ──
    private ClockManager             clockManager;
    private ThemeManager             themeManager;
    private ShortcutManager          shortcutManager;
    private SystemPanelManager       systemPanelManager;
    private MediaWidgetManager       mediaWidgetManager;
    private StatusIndicatorManager   statusIndicatorManager;
    private ContactsDialogHelper     contactsDialogHelper;
    private KioskManager             kioskManager;
    private BluetoothContactsManager btContactsManager;
    private BtNotifReceiverManager   btNotifReceiver;
    private NotifPanelManager        notifPanelManager;

    // ── Estado ──
    private boolean abriendoApp       = false;
    private boolean modoColorDinamico = false;
    private int     colorActualFondo  = COLOR_BASE_FONDO;

    // ── Vistas directas ──
    private TextView  textoReloj;
    private TextView  textoFecha;
    private ImageView botonTema;

    private PermissionsManager permissionsManager;
    private SharedPreferences  prefs;

    // ══════════════════════════════════════
    //  CICLO DE VIDA
    // ══════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences(NOMBRE_PREFS, MODE_PRIVATE);

        // ── Buscar vistas ──
        textoReloj = findViewById(R.id.clock_text);
        textoFecha = findViewById(R.id.date_text);
        botonTema  = findViewById(R.id.btn_theme_toggle);
        LinearLayout panelSistema = findViewById(R.id.system_panel);

        // ── Inicializar managers ──
        clockManager  = new ClockManager(this, textoReloj, textoFecha);
        themeManager  = new ThemeManager(this, prefs);
        kioskManager  = new KioskManager(this, prefs);

        permissionsManager = new PermissionsManager(this);
        permissionsManager.setOnAbrirAjustesListener(() -> abriendoApp = true);
        permissionsManager.setOnPermisosListener(todosActivos -> {
            View badge = findViewById(R.id.badge_permisos);
            badge.setVisibility(todosActivos ? View.GONE : View.VISIBLE);
        });
        findViewById(R.id.badge_permisos).setOnClickListener(v ->
                permissionsManager.mostrarDialogPermisosFaltantes());
        permissionsManager.verificar();

        shortcutManager = new ShortcutManager(this, prefs);
        shortcutManager.setOnAppOpenListener(new ShortcutManager.OnAppOpenListener() {
            @Override public void onAppOpening()    { abriendoApp = true;  }
            @Override public void onAppOpenFailed() { abriendoApp = false; }
        });

        systemPanelManager = new SystemPanelManager(this, panelSistema);
        systemPanelManager.setOnAppOpenListener(new SystemPanelManager.OnAppOpenListener() {
            @Override public void onAppOpening()    { abriendoApp = true;  }
            @Override public void onAppOpenFailed() { abriendoApp = false; }
        });

        btContactsManager      = new BluetoothContactsManager(this);
        mediaWidgetManager     = new MediaWidgetManager(this);
        statusIndicatorManager = new StatusIndicatorManager(this);

        contactsDialogHelper = new ContactsDialogHelper(this, prefs, btContactsManager);
        contactsDialogHelper.setOnAppOpenListener(() -> abriendoApp = true);

        // ── Notificaciones BT (Notisvin) ──────────────────────────────────────
        notifPanelManager = new NotifPanelManager(this);
        btNotifReceiver   = new BtNotifReceiverManager(notif ->
                notifPanelManager.agregarNotif(notif));
        btNotifReceiver.iniciar();
        // ─────────────────────────────────────────────────────────────────────

        // ── Color dinámico (Palette) ──────────────────────────────────────────
        modoColorDinamico = prefs.getBoolean(PREF_COLOR_DINAMICO, false);

        mediaWidgetManager.setOnColorExtraidoListener(new MediaWidgetManager.OnColorExtraidoListener() {
            @Override
            public void onColor(int colorDominante) {
                if (modoColorDinamico) animarColorFondo(colorDominante);
            }
            @Override
            public void onSinColor() {
                if (modoColorDinamico) animarColorFondo(COLOR_BASE_FONDO);
            }
        });
        // ─────────────────────────────────────────────────────────────────────

        // ── Configurar ──
        shortcutManager.precargarSiNecesario();
        shortcutManager.configurarVistas(() -> systemPanelManager.ocultar());
        systemPanelManager.construir();

        configurarReloj();
        configurarBotonTema();
        configurarBotonApps();
        configurarBotonSistema();
        configurarIndicadoresEstado();
        configurarReproductor();

        // ── Arrancar servicios ──
        shortcutManager.cargar();
        clockManager.iniciar();
        mediaWidgetManager.iniciar();
        statusIndicatorManager.iniciar();
        aplicarTema();
    }

    // ══════════════════════════════════════
    //  CONFIGURACIÓN DE BOTONES (delegación)
    // ══════════════════════════════════════

    private void configurarReloj() {
        findViewById(R.id.boton_reloj).setOnClickListener(v -> {
            systemPanelManager.ocultar();
            abriendoApp = true;
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_DATE_SETTINGS));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                } catch (Exception ex) {
                    abriendoApp = false;
                    mostrarMensaje("No se pudo abrir los ajustes");
                }
            }
        });

        // Long press → sniffer de broadcasts
        findViewById(R.id.boton_reloj).setOnLongClickListener(v -> {
            abriendoApp = true;
            startActivity(new Intent(this, BroadcastSnifferActivity.class));
            return true;
        });
    }

    private void configurarBotonTema() {
        botonTema.setOnClickListener(v -> {
            themeManager.alternar();
            aplicarTema();
        });

        botonTema.setOnLongClickListener(v -> {
            kioskManager.alternar();
            mostrarMensaje(kioskManager.isActivo()
                    ? "Modo kiosco activado" : "Modo kiosco desactivado");
            return true;
        });
    }

    private void configurarBotonApps() {
        findViewById(R.id.btn_all_apps).setOnClickListener(v -> {
            systemPanelManager.ocultar();
            abriendoApp = true;
            startActivity(new Intent(this, AllAppsActivity.class));
        });

        findViewById(R.id.btn_all_apps).setOnLongClickListener(v -> {
            abriendoApp = true;
            startActivity(new Intent(this, DebugPackagesActivity.class));
            return true;
        });
    }

    private void configurarBotonSistema() {
        findViewById(R.id.root_layout).setOnClickListener(v -> systemPanelManager.ocultar());
        findViewById(R.id.fab_clear_apps).setOnClickListener(v -> liberarMemoria());
    }

    private void configurarIndicadoresEstado() {
        // WiFi → ajustes WiFi
        findViewById(R.id.indicador_wifi).setOnClickListener(v -> {
            abriendoApp = true;
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
            } catch (Exception e) {
                abriendoApp = false;
            }
        });

        // Bluetooth (click) → contactos rápidos
        findViewById(R.id.indicador_bluetooth).setOnClickListener(
                v -> contactsDialogHelper.mostrar());

        // Long press → ajustes BT
        findViewById(R.id.indicador_bluetooth).setOnLongClickListener(v -> {
            abriendoApp = true;
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
            } catch (Exception e) {
                abriendoApp = false;
            }
            return true;
        });
    }

    /**
     * Long press en el mini reproductor → activa / desactiva color dinámico.
     * El estado se guarda en SharedPreferences para sobrevivir reinicios.
     */
    private void configurarReproductor() {
        findViewById(R.id.widget_reproductor).setOnLongClickListener(v -> {
            modoColorDinamico = !modoColorDinamico;
            prefs.edit().putBoolean(PREF_COLOR_DINAMICO, modoColorDinamico).apply();

            if (modoColorDinamico) {
                mostrarMensaje("Color dinámico activado — long press para desactivar");
            } else {
                mostrarMensaje("Color dinámico desactivado");
                animarColorFondo(COLOR_BASE_FONDO); // vuelve al azul oscuro base
            }
            return true;
        });
    }

    // ══════════════════════════════════════
    //  COLOR DINÁMICO
    // ══════════════════════════════════════

    /**
     * Anima el fondo del root_layout desde el color actual hasta colorDestino.
     * Duración: 800ms. Usa ArgbEvaluator para interpolar correctamente ARGB.
     */
    private void animarColorFondo(int colorDestino) {
        if (colorActualFondo == colorDestino) return;

        ValueAnimator animator = ValueAnimator.ofObject(
                new ArgbEvaluator(),
                colorActualFondo,
                colorDestino
        );
        animator.setDuration(800);
        animator.addUpdateListener(anim ->
                findViewById(R.id.root_layout).setBackgroundColor((int) anim.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                colorActualFondo = colorDestino;
            }
        });
        animator.start();
    }

    // ══════════════════════════════════════
    //  LIBERAR MEMORIA
    // ══════════════════════════════════════

    /**
     * Mata todos los procesos en background para liberar RAM.
     * Requiere: android.permission.KILL_BACKGROUND_PROCESSES en el Manifest.
     */
    private void liberarMemoria() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) return;

            List<ActivityManager.RunningAppProcessInfo> procesos = am.getRunningAppProcesses();
            if (procesos == null) return;

            int liberados = 0;
            for (ActivityManager.RunningAppProcessInfo proceso : procesos) {
                if (proceso.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    am.killBackgroundProcesses(proceso.processName);
                    liberados++;
                }
            }
            mostrarMensaje("✓ " + liberados + " apps cerradas — memoria liberada");

        } catch (Exception e) {
            mostrarMensaje("Error al liberar memoria");
        }
    }

    // ══════════════════════════════════════
    //  TEMA
    // ══════════════════════════════════════

    private void aplicarTema() {
        themeManager.aplicar(
                findViewById(R.id.root_layout),
                textoReloj, textoFecha, botonTema,
                shortcutManager.getVistasAccesos(),
                shortcutManager.getIdsNombres(),
                shortcutManager.getCantidadAccesos()
        );
    }

    // ══════════════════════════════════════
    //  CICLO DE VIDA
    // ══════════════════════════════════════

    @Override
    public void onBackPressed() {
        if (systemPanelManager.isVisible()) {
            systemPanelManager.ocultar();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        systemPanelManager.ocultar();
        if (!abriendoApp) {
            kioskManager.programarRetorno();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        abriendoApp = false;
        kioskManager.cancelarRetorno();
        shortcutManager.cargar();
        clockManager.actualizar();
        permissionsManager.verificar();
        mediaWidgetManager.actualizar();
        aplicarTema();

        // Mantener marquee activo al volver
        TextView titulo = findViewById(R.id.reproductor_titulo);
        if (titulo != null) titulo.setSelected(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockManager.detener();
        kioskManager.destruir();
        mediaWidgetManager.destruir();
        statusIndicatorManager.detener();
        contactsDialogHelper.setOnAppOpenListener(null);

        if (btNotifReceiver != null) btNotifReceiver.detener();

        // Soltar referencias a vistas
        textoReloj = null;
        textoFecha = null;
        botonTema  = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        shortcutManager.procesarResultado(requestCode, resultCode, data);
        contactsDialogHelper.procesarResultado(requestCode, resultCode, data);
    }

    // ══════════════════════════════════════
    //  UTILIDADES
    // ══════════════════════════════════════

    private void mostrarMensaje(String mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}