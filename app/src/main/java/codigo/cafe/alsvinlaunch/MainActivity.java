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

    private static final String NOMBRE_PREFS = "alsvin_launcher_prefs";
    private static final String PREF_COLOR_DINAMICO = "color_dinamico";
    private static final int COLOR_BASE_FONDO = 0xFF1A1A2E;

    // ── Managers ──
    private ClockManager clockManager;
    private ThemeManager themeManager;
    private ShortcutManager shortcutManager;
    private SystemPanelManager systemPanelManager;
    private MediaWidgetManager mediaWidgetManager;
    private StatusIndicatorManager statusIndicatorManager;
    private ContactsDialogHelper contactsDialogHelper;
    private KioskManager kioskManager;
    private BluetoothContactsManager btContactsManager;
    private BtNotifReceiverManager btNotifReceiver;
    private NotifPanelManager notifPanelManager;

    // ── Estado ──
    private boolean abriendoApp = false;
    private boolean modoColorDinamico = false;
    private int colorActualFondo = COLOR_BASE_FONDO;
    private boolean primerResume = true;

    // ── Vistas directas ──
    private TextView textoReloj;
    private TextView textoFecha;
    private ImageView botonTema;

    private PermissionsManager permissionsManager;
    private SharedPreferences prefs;

    // ══════════════════════════════════════
    //  CICLO DE VIDA — onCreate
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
        botonTema = findViewById(R.id.btn_theme_toggle);
        LinearLayout panelSistema = findViewById(R.id.system_panel);

        // ── Inicializar managers ──
        clockManager = new ClockManager(this, textoReloj, textoFecha);
        themeManager = new ThemeManager(this, prefs);
        kioskManager = new KioskManager(this, prefs);

        permissionsManager = new PermissionsManager(this);
        permissionsManager.setOnAbrirAjustesListener(() -> abriendoApp = true);
        permissionsManager.setOnPermisosListener(todosActivos -> {
            View badge = findViewById(R.id.badge_permisos);
            badge.setVisibility(todosActivos ? View.GONE : View.VISIBLE);
        });
        findViewById(R.id.badge_permisos).setOnClickListener(v ->
                permissionsManager.mostrarDialogPermisosFaltantes());

        shortcutManager = new ShortcutManager(this, prefs);
        shortcutManager.setOnAppOpenListener(new ShortcutManager.OnAppOpenListener() {
            @Override
            public void onAppOpening() {
                abriendoApp = true;
            }

            @Override
            public void onAppOpenFailed() {
                abriendoApp = false;
            }
        });

        systemPanelManager = new SystemPanelManager(this, panelSistema);
        systemPanelManager.setOnAppOpenListener(new SystemPanelManager.OnAppOpenListener() {
            @Override
            public void onAppOpening() {
                abriendoApp = true;
            }

            @Override
            public void onAppOpenFailed() {
                abriendoApp = false;
            }
        });

        btContactsManager = new BluetoothContactsManager(this);
        mediaWidgetManager = new MediaWidgetManager(this);
        statusIndicatorManager = new StatusIndicatorManager(this);

        contactsDialogHelper = new ContactsDialogHelper(this, prefs, btContactsManager);
        contactsDialogHelper.setOnAppOpenListener(() -> abriendoApp = true);

        // ── Notificaciones BT (Notisvin) ──
        notifPanelManager = new NotifPanelManager(this);
        btNotifReceiver = new BtNotifReceiverManager(notif ->
                notifPanelManager.agregarNotif(notif));
        btNotifReceiver.iniciar();

        // ── Color dinámico (Palette) ──
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

        // ── Configurar vistas y botones (solo una vez en onCreate) ──
        shortcutManager.precargarSiNecesario();
        shortcutManager.configurarVistas(() -> systemPanelManager.ocultar());
        systemPanelManager.construir();

        configurarReloj();
        configurarBotonTema();
        configurarBotonApps();
        configurarBotonSistema();
        configurarIndicadoresEstado();
        configurarReproductor();

        // ── Carga inicial de datos (solo onCreate, no onResume) ──
        shortcutManager.cargar();
        aplicarTema();
        permissionsManager.verificar();
    }

    // ══════════════════════════════════════
    //  CICLO DE VIDA — onStart / onStop
    // ══════════════════════════════════════

    @Override
    protected void onStart() {
        super.onStart();
        clockManager.iniciar();
        mediaWidgetManager.iniciar();
        statusIndicatorManager.iniciar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        clockManager.detener();
        mediaWidgetManager.detener();
        statusIndicatorManager.detener();
    }

    // ══════════════════════════════════════
    //  CICLO DE VIDA — onResume
    // ══════════════════════════════════════

    @Override
    protected void onResume() {
        super.onResume();
        abriendoApp = false;
        kioskManager.cancelarRetorno();

        clockManager.actualizar();
        mediaWidgetManager.actualizar();
        statusIndicatorManager.actualizar();

        if (primerResume) {
            aplicarTema();
            permissionsManager.verificar();
            primerResume = false;
        }

        if (modoColorDinamico && colorActualFondo != COLOR_BASE_FONDO) {
            findViewById(R.id.root_layout).setBackgroundColor(colorActualFondo);
        }

        TextView titulo = findViewById(R.id.reproductor_titulo);
        if (titulo != null) titulo.setSelected(true);
    }

    // ══════════════════════════════════════
    //  CICLO DE VIDA — onPause
    // ══════════════════════════════════════

    @Override
    protected void onPause() {
        super.onPause();
        systemPanelManager.ocultar();

        if (!abriendoApp && kioskManager.isActivo()) {
            kioskManager.programarRetorno();
        }
    }

    // ══════════════════════════════════════
    //  CICLO DE VIDA — onDestroy
    // ══════════════════════════════════════

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockManager.detener();
        mediaWidgetManager.destruir();
        kioskManager.destruir();
        statusIndicatorManager.detener();
        contactsDialogHelper.setOnAppOpenListener(null);

        if (btNotifReceiver != null) btNotifReceiver.detener();

        textoReloj = null;
        textoFecha = null;
        botonTema = null;
    }

    // ══════════════════════════════════════
    //  onBackPressed
    // ══════════════════════════════════════

    @Override
    public void onBackPressed() {
        if (systemPanelManager.isVisible()) {
            systemPanelManager.ocultar();
        }
        // No llamar super — en un launcher el Back no debe salir
    }

    // ══════════════════════════════════════
    //  CONFIGURACIÓN DE BOTONES
    // ══════════════════════════════════════

    private void configurarReloj() {
        // Long press → abre ajustes de fecha/hora
        findViewById(R.id.boton_reloj).setOnLongClickListener(v -> {
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


    }

    private void configurarBotonSistema() {
        findViewById(R.id.root_layout).setOnClickListener(v -> systemPanelManager.ocultar());
        findViewById(R.id.fab_clear_apps).setOnClickListener(v -> liberarMemoria());
    }

    private void configurarIndicadoresEstado() {
        findViewById(R.id.indicador_wifi).setOnClickListener(v -> {
            abriendoApp = true;
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
            } catch (Exception e) {
                abriendoApp = false;
            }
        });

        // BT → abre ajustes bluetooth directamente
        findViewById(R.id.indicador_bluetooth).setOnClickListener(v -> {
            abriendoApp = true;
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
            } catch (Exception e) {
                // Fallback — ajustes generales
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                } catch (Exception ex) {
                    abriendoApp = false;
                    mostrarMensaje("No se pudo abrir Bluetooth");
                }
            }
        });

        // Contactos → abre el diálogo de contactos rápidos
        findViewById(R.id.boton_contactos).setOnClickListener(
                v -> contactsDialogHelper.mostrar());
    }

    private void configurarReproductor() {
        findViewById(R.id.widget_reproductor).setOnLongClickListener(v -> {
            modoColorDinamico = !modoColorDinamico;
            prefs.edit().putBoolean(PREF_COLOR_DINAMICO, modoColorDinamico).apply();

            if (modoColorDinamico) {
                mostrarMensaje("Color dinámico activado — long press para desactivar");
            } else {
                mostrarMensaje("Color dinámico desactivado");
                animarColorFondo(COLOR_BASE_FONDO);
            }
            return true;
        });
    }

    // ══════════════════════════════════════
    //  COLOR DINÁMICO
    // ══════════════════════════════════════

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

    private void liberarMemoria() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) return;

            // RAM antes
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memInfo);
            long ramLibreAntes = memInfo.availMem / (1024 * 1024);

            List<ActivityManager.RunningAppProcessInfo> procesos = am.getRunningAppProcesses();
            if (procesos == null) {
                mostrarMensaje("No hay procesos");
                return;
            }

            int liberados = 0;
            String appsMatadas = "";

            for (ActivityManager.RunningAppProcessInfo proceso : procesos) {
                // En Android 6: IMPORTANCE_BACKGROUND = 400, IMPORTANCE_EMPTY = 500
                // Matar todo lo que esté en background o empty, excepto nosotros mismos
                if (proceso.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND
                        && !proceso.processName.equals(getPackageName())) {
                    am.killBackgroundProcesses(proceso.processName);
                    appsMatadas += "\n• " + proceso.processName;
                    liberados++;
                }
            }

            // Esperar 500ms y mostrar RAM liberada
            final int totalLiberados = liberados;
            final String listaApps = appsMatadas;
            new android.os.Handler().postDelayed(() -> {
                am.getMemoryInfo(memInfo);
                long ramLibreDespues = memInfo.availMem / (1024 * 1024);
                long ganancia = ramLibreDespues - ramLibreAntes;

                mostrarMensaje("✓ " + totalLiberados + " apps cerradas\n"
                        + "RAM antes: " + ramLibreAntes + "MB\n"
                        + "RAM ahora: " + ramLibreDespues + "MB\n"
                        + (ganancia > 0 ? "+" + ganancia + "MB liberados" : "sin cambio"));
            }, 500);

        } catch (Exception e) {
            mostrarMensaje("Error: " + e.getMessage());
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
    //  RESULTADOS Y PERMISOS
    // ══════════════════════════════════════

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // ← FIX: cada manager maneja solo su requestCode
        if (requestCode == ShortcutManager.SOLICITUD_ELEGIR_APP) {
            shortcutManager.procesarResultado(requestCode, resultCode, data);
            // procesarResultado ya guarda prefs y llama cargar() internamente
            return;
        }

        contactsDialogHelper.procesarResultado(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // ══════════════════════════════════════
    //  UTILIDADES
    // ══════════════════════════════════════

    private void mostrarMensaje(String mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
    }
}