package codigo.cafe.alsvinlaunch;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import codigo.cafe.alsvinlaunch.activity.AllAppsActivity;
import codigo.cafe.alsvinlaunch.activity.LockScreenActivity;
import codigo.cafe.alsvinlaunch.managers.BluetoothContactsManager;
import codigo.cafe.alsvinlaunch.managers.BtNotifReceiverManager;
import codigo.cafe.alsvinlaunch.managers.ClockManager;
import codigo.cafe.alsvinlaunch.managers.ContactsDialogHelper;
import codigo.cafe.alsvinlaunch.managers.KioskManager;
import codigo.cafe.alsvinlaunch.managers.MediaWidgetManager;
import codigo.cafe.alsvinlaunch.managers.NotifPanelManager;
import codigo.cafe.alsvinlaunch.managers.PermissionsManager;
import codigo.cafe.alsvinlaunch.managers.PremiumManager;
import codigo.cafe.alsvinlaunch.managers.ShortcutManager;
import codigo.cafe.alsvinlaunch.managers.SpeedManager;
import codigo.cafe.alsvinlaunch.managers.StatusIndicatorManager;
import codigo.cafe.alsvinlaunch.managers.SystemPanelManager;

/**
 * Actividad principal del launcher.
 * Orquesta los managers especializados sin lógica de negocio propia.
 */
public class MainActivity extends Activity {

    private static final String NOMBRE_PREFS = "alsvin_launcher_prefs";
    private static final String PREF_COLOR_DINAMICO = "color_dinamico";
    private static final int COLOR_BASE_FONDO = 0xFF1A1A2E;

    // Código de solicitud para el permiso de ubicación
    private static final int SOLICITUD_PERMISO_GPS = 200;

    // Hora mínima válida — antes de esto consideramos que el RTC no se sincronizó.
    // Evita la cascada que congela la app cuando el reloj salta de 2006 a la hora real.
    private static final long TIEMPO_MINIMO_VALIDO = 1_735_689_600_000L; // 2025-01-01 UTC
    private static final long TIMEOUT_ESPERA_HORA_MS = 30_000L;
    private static final long DELAY_RECREATE_TRAS_TIMECHANGE_MS = 3_000L;

    // Sobrevive a recreate(). Si se activa por timeout, no reentramos en modo espera.
    private static boolean omitirEsperaHora = false;

    // ── Managers ──
    private ClockManager clockManager;
    private ShortcutManager shortcutManager;
    private SystemPanelManager systemPanelManager;
    private MediaWidgetManager mediaWidgetManager;
    private StatusIndicatorManager statusIndicatorManager;
    private ContactsDialogHelper contactsDialogHelper;
    private KioskManager kioskManager;
    private BluetoothContactsManager btContactsManager;
    private BtNotifReceiverManager btNotifReceiver;
    private NotifPanelManager notifPanelManager;
    private SpeedManager speedManager;
    private PremiumManager premiumManager;
    // ── Estado ──
    private boolean abriendoApp = false;
    private boolean modoColorDinamico = false;
    private int colorActualFondo = COLOR_BASE_FONDO;
    private boolean primerResume = true;
    private boolean pantallaBloqueada = false;
    private boolean modoEsperaHora = false;
    private BroadcastReceiver receptorEsperaHora;
    private final Handler handlerEspera = new Handler(Looper.getMainLooper());

    // ── Vistas directas ──
    private TextView textoReloj;
    private TextView textoFecha;
    private ImageView botonBloqueo;
    private View topBar;

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

        // ── Vistas directas (siempre necesarias) ──
        textoReloj = findViewById(R.id.clock_text);
        textoFecha = findViewById(R.id.date_text);
        botonBloqueo = findViewById(R.id.btn_theme_toggle);
        topBar = findViewById(R.id.top_bar);

        // ── Reloj siempre activo, incluso con fecha incorrecta ──
        clockManager = new ClockManager(this, textoReloj, textoFecha);

        // Si el RTC no tiene batería, el headunit arranca en 2006 y NTP
        // luego salta ~20 años. Esa cascada (alarmas atrasadas, re-sync
        // de MediaSessionManager, re-validación SSL…) congela la Activity.
        // Posponemos el init pesado hasta que la hora sea válida.
        if (!omitirEsperaHora && !tiempoValido()) {
            iniciarModoEsperaHora();
            return;
        }

        inicializarManagersCompletos();
    }

    // ══════════════════════════════════════
    //  MODO ESPERA POR HORA INVÁLIDA
    // ══════════════════════════════════════

    private boolean tiempoValido() {
        return System.currentTimeMillis() >= TIEMPO_MINIMO_VALIDO;
    }

    private void iniciarModoEsperaHora() {
        modoEsperaHora = true;

        receptorEsperaHora = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (tiempoValido()) procesarTimeChangeValido();
            }
        };
        IntentFilter filtro = new IntentFilter();
        filtro.addAction(Intent.ACTION_TIME_CHANGED);
        filtro.addAction(Intent.ACTION_DATE_CHANGED);
        registerReceiver(receptorEsperaHora, filtro);

        // Fallback: si NTP nunca llega (sin internet), tras 30 s
        // inicializamos igual para que el launcher sea funcional.
        handlerEspera.postDelayed(() -> {
            if (!modoEsperaHora) return;
            omitirEsperaHora = true;
            if (!isFinishing() && !isDestroyed()) recreate();
        }, TIMEOUT_ESPERA_HORA_MS);
    }

    private void procesarTimeChangeValido() {
        // Damos unos segundos al sistema para que termine la cascada
        // de eventos antes de recrear con un estado limpio.
        handlerEspera.removeCallbacksAndMessages(null);
        handlerEspera.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) recreate();
        }, DELAY_RECREATE_TRAS_TIMECHANGE_MS);
    }

    // ══════════════════════════════════════
    //  INICIALIZACIÓN COMPLETA
    // ══════════════════════════════════════

    private void inicializarManagersCompletos() {
        LinearLayout panelSistema = findViewById(R.id.system_panel);

        // ── Inicializar managers ──
        kioskManager = new KioskManager(this, prefs);
        speedManager = new SpeedManager(this);

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

        // ── Configurar vistas y botones ──
        shortcutManager.precargarSiNecesario();
        shortcutManager.configurarVistas(() -> systemPanelManager.ocultar());
        systemPanelManager.construir();

        configurarReloj();
        configurarBotonBloqueo();
        configurarBotonApps();
        configurarBotonSistema();
        configurarIndicadoresEstado();
        configurarReproductor();

        // ── Carga inicial de datos ──
        shortcutManager.cargar();
        permissionsManager.verificar();

        solicitarPermisoGpsSiNecesario();

        // ── Premium / trial ──
        // PremiumManager espera internamente a que la fecha sea válida
        // antes de fallar, así que es seguro llamarlo aquí incluso si el
        // init completo se gatilló por timeout sin internet.
        premiumManager = new PremiumManager(this, prefs);
        premiumManager.verificar(tieneAcceso -> {
            if (!tieneAcceso) {
                runOnUiThread(this::mostrarDialogPremium);
            } else if (!premiumManager.esPremium()) {
                int dias = premiumManager.diasRestantesTrial();
                if (dias <= 3) {
                    runOnUiThread(() ->
                            mostrarMensaje("⏳ Trial: " + dias + " día(s) restante(s)"));
                }
            }
        });
    }

    // ══════════════════════════════════════
    //  CICLO DE VIDA — onStart / onStop
    // ══════════════════════════════════════

    @Override
    protected void onStart() {
        super.onStart();
        clockManager.iniciar();
        if (modoEsperaHora) return;

        mediaWidgetManager.iniciar();
        statusIndicatorManager.iniciar();
        speedManager.iniciar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        clockManager.detener();
        if (modoEsperaHora) return;

        mediaWidgetManager.detener();
        statusIndicatorManager.detener();
        speedManager.detener();
    }

    // ══════════════════════════════════════
    //  CICLO DE VIDA — onResume
    // ══════════════════════════════════════

    @Override
    protected void onResume() {
        super.onResume();
        clockManager.actualizar();
        if (modoEsperaHora) return;

        abriendoApp = false;
        kioskManager.cancelarRetorno();

        mediaWidgetManager.actualizar();
        statusIndicatorManager.actualizar();

        if (primerResume) {
            permissionsManager.verificar();
            primerResume = false;
        }

        if (modoColorDinamico && colorActualFondo != COLOR_BASE_FONDO) {
            findViewById(R.id.root_layout).setBackgroundColor(colorActualFondo);
        }

        TextView titulo = findViewById(R.id.reproductor_titulo);
        if (titulo != null) titulo.setSelected(true);

        if (premiumManager != null) {
            premiumManager.verificarComprasPendientes(new PremiumManager.OnCompraListener() {
                @Override
                public void onCompraExitosa() {
                    mostrarMensaje("✓ Premium activado. ¡Gracias!");
                }

                @Override
                public void onCompraError(String mensaje) {
                    // silencioso en onResume
                }
            });
        }
    }

    // ══════════════════════════════════════
    //  CICLO DE VIDA — onPause
    // ══════════════════════════════════════

    @Override
    protected void onPause() {
        super.onPause();
        if (modoEsperaHora) return;

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

        if (clockManager != null) clockManager.detener();

        // Limpieza del modo espera (tanto si seguimos en él como si ya salimos)
        handlerEspera.removeCallbacksAndMessages(null);
        if (receptorEsperaHora != null) {
            try {
                unregisterReceiver(receptorEsperaHora);
            } catch (IllegalArgumentException ignored) {
            }
            receptorEsperaHora = null;
        }

        if (modoEsperaHora) {
            textoReloj = null;
            textoFecha = null;
            botonBloqueo = null;
            return;
        }

        mediaWidgetManager.destruir();
        kioskManager.destruir();
        statusIndicatorManager.detener();
        speedManager.destruir();
        contactsDialogHelper.setOnAppOpenListener(null);

        if (btNotifReceiver != null) btNotifReceiver.detener();
        if (premiumManager != null) premiumManager.destruir();

        textoReloj = null;
        textoFecha = null;
        botonBloqueo = null;
    }

    // ══════════════════════════════════════
    //  onBackPressed
    // ══════════════════════════════════════

    @Override
    public void onBackPressed() {
        if (modoEsperaHora) return;
        if (systemPanelManager.isVisible()) {
            systemPanelManager.ocultar();
        }
        // No llamar super — en un launcher el Back no debe salir
    }

    // ══════════════════════════════════════
    //  PERMISO GPS
    // ══════════════════════════════════════

    /**
     * Solicita ACCESS_FINE_LOCATION en Android 6+ si aún no fue concedido.
     * SpeedManager#iniciar() verifica el permiso por su cuenta; esta llamada
     * solo sirve para que el sistema muestre el diálogo al usuario la primera vez.
     */
    private void solicitarPermisoGpsSiNecesario() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    SOLICITUD_PERMISO_GPS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SOLICITUD_PERMISO_GPS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso recién concedido → arrancar SpeedManager ahora
                speedManager.iniciar();
            }
            // Si fue denegado, SpeedManager ya ocultó el widget en iniciar()
        }
    }

    // ══════════════════════════════════════
    //  CONFIGURACIÓN DE BOTONES
    // ══════════════════════════════════════

    private void configurarReloj() {
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

    private void configurarBotonBloqueo() {
        botonBloqueo.setOnClickListener(v -> bloquearPantalla());

        botonBloqueo.setOnLongClickListener(v -> {
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

        findViewById(R.id.indicador_bluetooth).setOnClickListener(v -> {
            abriendoApp = true;
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                } catch (Exception ex) {
                    abriendoApp = false;
                    mostrarMensaje("No se pudo abrir Bluetooth");
                }
            }
        });

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
                new ArgbEvaluator(), colorActualFondo, colorDestino);
        animator.setDuration(800);
        animator.addUpdateListener(anim ->
                findViewById(R.id.root_layout)
                        .setBackgroundColor((int) anim.getAnimatedValue()));
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
                if (proceso.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND
                        && !proceso.processName.equals(getPackageName())) {
                    am.killBackgroundProcesses(proceso.processName);
                    appsMatadas += "\n• " + proceso.processName;
                    liberados++;
                }
            }

            final int totalLiberados = liberados;
            final String listaApps = appsMatadas;
            new android.os.Handler().postDelayed(() -> {
                am.getMemoryInfo(memInfo);
                long ramLibreDespues = memInfo.availMem / (1024 * 1024);
                long ganancia = ramLibreDespues - ramLibreAntes;

                mostrarMensaje("✓ " + totalLiberados + " apps cerradas\n"
                        + "RAM antes: " + ramLibreAntes + "MB\n"
                        + "RAM ahora: " + ramLibreDespues + "MB\n"
                        + (ganancia > 0
                        ? "+" + ganancia + "MB liberados"
                        : "sin cambio"));
            }, 500);

        } catch (Exception e) {
            mostrarMensaje("Error: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════
    //  BLOQUEO DE PANTALLA
    // ══════════════════════════════════════

    private void bloquearPantalla() {
        abriendoApp = true;
        startActivity(new Intent(this, LockScreenActivity.class));
    }

    // ══════════════════════════════════════
    //  Dialogo premium
    // ══════════════════════════════════════


    private void mostrarDialogPremium() {
        View vista = LayoutInflater.from(this)
                .inflate(R.layout.dialog_premium, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(vista)
                .setCancelable(false)   // no puede cerrarse sin pagar
                .create();

        // Botón comprar
        vista.findViewById(R.id.btn_comprar).setOnClickListener(v ->
                premiumManager.iniciarCompra(new PremiumManager.OnCompraListener() {
                    @Override
                    public void onCompraExitosa() {
                        dialog.dismiss();
                        mostrarMensaje("🎉 ¡Premium activado! Gracias por tu apoyo.");
                    }

                    @Override
                    public void onCompraError(String mensaje) {
                        mostrarMensaje("Error: " + mensaje);
                    }
                })
        );

        // Botón restaurar (para quien ya compró en otro dispositivo)
        vista.findViewById(R.id.btn_restaurar).setOnClickListener(v ->
                premiumManager.verificarComprasPendientes(new PremiumManager.OnCompraListener() {
                    @Override
                    public void onCompraExitosa() {
                        dialog.dismiss();
                        mostrarMensaje("✓ Compra restaurada correctamente.");
                    }

                    @Override
                    public void onCompraError(String mensaje) {
                        mostrarMensaje("No se encontró una compra previa.");
                    }
                })
        );

        dialog.show();
    }


    // ══════════════════════════════════════
    //  RESULTADOS Y PERMISOS
    // ══════════════════════════════════════

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ShortcutManager.SOLICITUD_ELEGIR_APP) {
            shortcutManager.procesarResultado(requestCode, resultCode, data);
            return;
        }

        contactsDialogHelper.procesarResultado(requestCode, resultCode, data);
    }

    // ══════════════════════════════════════
    //  UTILIDADES
    // ══════════════════════════════════════

    private void mostrarMensaje(String mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
    }
}