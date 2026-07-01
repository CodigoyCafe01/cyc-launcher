package codigo.cafe.alsvinlaunch;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import codigo.cafe.alsvinlaunch.activity.AllAppsActivity;
import codigo.cafe.alsvinlaunch.activity.LockScreenActivity;
import codigo.cafe.alsvinlaunch.managers.BluetoothContactsManager;
import codigo.cafe.alsvinlaunch.managers.BtNotifReceiverManager;
import codigo.cafe.alsvinlaunch.managers.ClockManager;
import codigo.cafe.alsvinlaunch.managers.ContactsDialogHelper;
import codigo.cafe.alsvinlaunch.managers.DonationManager;
import codigo.cafe.alsvinlaunch.managers.KioskManager;
import codigo.cafe.alsvinlaunch.managers.MediaWidgetManager;
import codigo.cafe.alsvinlaunch.managers.NotifPanelManager;
import codigo.cafe.alsvinlaunch.managers.PermissionsManager;
import codigo.cafe.alsvinlaunch.managers.ShortcutManager;
import codigo.cafe.alsvinlaunch.managers.SpeedManager;
import codigo.cafe.alsvinlaunch.managers.StatusIndicatorManager;
import codigo.cafe.alsvinlaunch.managers.SystemPanelManager;
import codigo.cafe.alsvinlaunch.managers.TourManager;
import codigo.cafe.alsvinlaunch.util.LocaleHelper;

/**
 * Actividad principal del launcher.
 * Orquesta los managers especializados sin lógica de negocio propia.
 */
public class MainActivity extends Activity {

    private static final String NOMBRE_PREFS = "alsvin_launcher_prefs";
    private static final String PREF_COLOR_DINAMICO = "color_dinamico";
    private static final String PREF_COLOR_FONDO = "color_fondo_personalizado";
    private static final int COLOR_BASE_FONDO = 0xFF1A1A2E;

    // ── Tema de la pantalla de inicio ──
    // Se guarda qué actividad-launcher debe mostrarse. La MainActivity (única
    // con filtro HOME) actúa como despachador y redirige al tema elegido.
    public static final String PREF_TEMA_INICIO = "tema_inicio";
    public static final int TEMA_PRINCIPAL   = 0;
    public static final int TEMA_ALTERNATIVO = 1;

    // Código de solicitud para el permiso de ubicación
    private static final int SOLICITUD_PERMISO_GPS = 200;

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
    private DonationManager donationManager;
    private View botonDonacion;
    // ── Estado ──
    private boolean abriendoApp = false;
    private boolean modoColorDinamico = false;
    private int colorActualFondo = COLOR_BASE_FONDO;
    private boolean primerResume = true;
    // Se pone a true al final de inicializarManagersCompletos(). Los métodos
    // del ciclo de vida (onStart/onResume/...) que corren ANTES de que el
    // Runnable diferido se ejecute deben hacer no-op mientras esto sea false.
    private boolean managersListos = false;
    // Resultado de AppPicker que llegó antes de que los managers estuvieran listos
    private int pendingResultRequestCode = -1;
    private int pendingResultResultCode  = -1;
    private Intent pendingResultData     = null;

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
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    /**
     * Layout que infla esta pantalla de inicio. Las subclases (temas
     * alternativos) lo sobreescriben para reutilizar toda la lógica de
     * MainActivity cambiando únicamente el diseño.
     */
    protected int getLayoutResId() {
        return R.layout.activity_main;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // La MainActivity es la única con filtro HOME: si el usuario eligió un
        // tema alternativo, redirige a su actividad y termina antes de inflar
        // nada ni crear managers en esta instancia.
        if (redirigirATemaSeleccionado()) return;

        setContentView(getLayoutResId());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences(NOMBRE_PREFS, MODE_PRIVATE);

        // Pre-calentar prefs en hilo de fondo. La 1ª lectura desde el hilo UI
        // bloquea mientras Android parsea el XML; haciéndolo aquí primero,
        // cuando los managers lo lean luego, ya estará en caché.
        precargarPrefsEnBackground();

        // ── Vistas directas (siempre necesarias) ──
        textoReloj = findViewById(R.id.clock_text);
        textoFecha = findViewById(R.id.date_text);
        botonBloqueo = findViewById(R.id.btn_theme_toggle);
        topBar = findViewById(R.id.top_bar);

        clockManager = new ClockManager(this, textoReloj, textoFecha);

        // Diferimos la inicialización pesada hasta DESPUÉS del primer frame:
        // así el launcher aparece de inmediato y la creación de managers,
        // findViewById en cadena y carga de iconos no bloquean la primera
        // pintura. El usuario ve la UI al instante en lugar de pantalla
        // congelada durante 1-2 segundos.
        getWindow().getDecorView().post(this::inicializarManagersCompletos);
    }

    /**
     * Fuerza la carga de los archivos SharedPreferences en un hilo de fondo.
     * SharedPreferencesImpl carga el XML async, pero el 1er get* bloquea
     * hasta que termina. Adelantar esto evita que el hilo UI se quede
     * esperando cuando los managers empiezan a leer prefs.
     */
    private void precargarPrefsEnBackground() {
        final SharedPreferences prefsPrincipal = prefs;
        final SharedPreferences prefsContactos =
                getSharedPreferences("contactos_locales", MODE_PRIVATE);
        new Thread(() -> {
            // getAll() fuerza la espera del load async
            prefsPrincipal.getAll();
            prefsContactos.getAll();
        }, "prefs-warmup").start();
    }

    // ══════════════════════════════════════
    //  INICIALIZACIÓN COMPLETA
    // ══════════════════════════════════════

    private void inicializarManagersCompletos() {
        LinearLayout panelSistema = findViewById(R.id.system_panel);

        // ── Inicializar managers ──
        kioskManager = new KioskManager(this, prefs);
        speedManager = new SpeedManager(this);

        // Gear widget: oculto por defecto.
        // Doble tap en velocímetro → mostrar. Doble tap en marcha → ocultar.
        // Tap simple en marcha → diálogo de rangos.
        View gearWidget = findViewById(R.id.gear_widget);
        if (gearWidget != null) {
            gearWidget.setVisibility(View.GONE);

            GestureDetector gearDetector = new GestureDetector(this,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onSingleTapConfirmed(MotionEvent e) {
                            codigo.cafe.alsvinlaunch.managers.GearRangesDialog.mostrar(
                                    MainActivity.this,
                                    () -> speedManager.recargarCortes());
                            return true;
                        }
                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            gearWidget.setVisibility(View.GONE);
                            return true;
                        }
                    });
            gearWidget.setOnTouchListener((v, event) -> {
                gearDetector.onTouchEvent(event);
                return true;
            });

            View speedometerView = findViewById(R.id.speedometer_view);
            if (speedometerView != null) {
                GestureDetector speedDetector = new GestureDetector(this,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onDoubleTap(MotionEvent e) {
                                gearWidget.setVisibility(View.VISIBLE);
                                return true;
                            }
                        });
                speedometerView.setOnTouchListener((v, event) -> {
                    speedDetector.onTouchEvent(event);
                    return true;
                });
            }
        }

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
        configurarBotonColorFondo();
        configurarBotonIdioma();
        configurarBotonCambiarTema();

        // ── Carga inicial de datos ──
        shortcutManager.cargar();
        permissionsManager.verificar();

        solicitarPermisoGpsSiNecesario();

        // ── Donación opcional ──
        donationManager = new DonationManager(this, prefs);
        configurarBotonDonacion();

        // Los managers ya están listos: disparar lo que onStart / onResume
        // intentaron ejecutar antes (saltaron porque managersListos == false).
        managersListos = true;

        // Procesar resultado de AppPicker que llegó antes de que los managers estuvieran listos
        if (pendingResultRequestCode != -1) {
            onActivityResult(pendingResultRequestCode, pendingResultResultCode, pendingResultData);
            pendingResultRequestCode = -1;
            pendingResultData = null;
        }

        mediaWidgetManager.iniciar();
        statusIndicatorManager.iniciar();
        speedManager.iniciar();
        mediaWidgetManager.actualizar();
        statusIndicatorManager.actualizar();

        // Restaurar color de fondo guardado
        int colorGuardado = prefs.getInt(PREF_COLOR_FONDO, COLOR_BASE_FONDO);
        if (colorGuardado != COLOR_BASE_FONDO) {
            colorActualFondo = colorGuardado;
            findViewById(R.id.root_layout).setBackgroundColor(colorGuardado);
        }

        // Discovery tour — se muestra solo la primera vez que el usuario instala la app
        FrameLayout rootLayout = findViewById(R.id.root_layout);
        getWindow().getDecorView().post(() -> TourManager.showIfNeeded(this, rootLayout));
    }

    // ══════════════════════════════════════
    //  CICLO DE VIDA — onStart / onStop
    // ══════════════════════════════════════

    @Override
    protected void onStart() {
        super.onStart();
        // clockManager puede ser null si onCreate redirigió a otro tema y terminó.
        if (clockManager == null) return;
        clockManager.iniciar();
        if (!managersListos) return; // se dispararán al final de inicializarManagersCompletos()

        mediaWidgetManager.iniciar();
        statusIndicatorManager.iniciar();
        speedManager.iniciar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (clockManager == null) return;
        clockManager.detener();
        if (!managersListos) return;

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
        if (clockManager == null) return; // onCreate redirigió a otro tema y terminó
        clockManager.actualizar();
        if (!managersListos) return; // se reanudará al final de inicializarManagersCompletos()

        abriendoApp = false;
        kioskManager.cancelarRetorno();

        mediaWidgetManager.actualizar();
        statusIndicatorManager.actualizar();

        if (primerResume) {
            permissionsManager.verificar();
            primerResume = false;
        }

        if (colorActualFondo != COLOR_BASE_FONDO) {
            findViewById(R.id.root_layout).setBackgroundColor(colorActualFondo);
        }

        TextView titulo = findViewById(R.id.reproductor_titulo);
        if (titulo != null) titulo.setSelected(true);

        if (donationManager != null) {
            actualizarVisibilidadDonacion();
            donationManager.verificarComprasPendientes(new DonationManager.OnDonacionListener() {
                @Override
                public void onDonacionExitosa() {
                    mostrarMensaje(getString(R.string.msg_donacion_restaurada));
                    actualizarVisibilidadDonacion();
                }

                @Override
                public void onDonacionError(String mensaje) {
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
        if (!managersListos) return;

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

        if (!managersListos) {
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
        if (donationManager != null) donationManager.destruir();

        textoReloj = null;
        textoFecha = null;
        botonBloqueo = null;
    }

    // ══════════════════════════════════════
    //  onBackPressed
    // ══════════════════════════════════════

    @Override
    public void onBackPressed() {
        if (!managersListos) return;
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
                    mostrarMensaje(getString(R.string.msg_no_ajustes));
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
        View btnApps = findViewById(R.id.btn_all_apps);
        btnApps.setOnClickListener(v -> {
            systemPanelManager.ocultar();
            abriendoApp = true;
            startActivity(new Intent(this, AllAppsActivity.class));
        });
        btnApps.setOnLongClickListener(v -> {
            FrameLayout rootLayout = findViewById(R.id.root_layout);
            TourManager.show(this, rootLayout);
            return true;
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
                    mostrarMensaje(getString(R.string.msg_no_bluetooth));
                }
            }
        });
    }

    private void configurarReproductor() {
        findViewById(R.id.widget_reproductor).setOnLongClickListener(v -> {
            modoColorDinamico = !modoColorDinamico;
            prefs.edit().putBoolean(PREF_COLOR_DINAMICO, modoColorDinamico).apply();

            if (modoColorDinamico) {
                mostrarMensaje(getString(R.string.msg_color_dinamico_on));
            } else {
                mostrarMensaje(getString(R.string.msg_color_dinamico_off));
                animarColorFondo(COLOR_BASE_FONDO);
            }
            return true;
        });
    }

    // ══════════════════════════════════════
    //  SELECTOR DE COLOR DE FONDO
    // ══════════════════════════════════════

    private void configurarBotonColorFondo() {
        View btn = findViewById(R.id.btn_color_fondo);
        if (btn == null) return;
        btn.setOnClickListener(v -> mostrarPaletaColores());
    }

    // ══════════════════════════════════════
    //  SELECTOR DE IDIOMA
    // ══════════════════════════════════════

    private void configurarBotonIdioma() {
        View btn = findViewById(R.id.btn_idioma);
        if (btn == null) return;
        btn.setOnClickListener(v -> mostrarSelectorIdioma());
    }

    // ══════════════════════════════════════
    //  CAMBIO DE TEMA DE INICIO
    // ══════════════════════════════════════

    private void configurarBotonCambiarTema() {
        View btn = findViewById(R.id.btn_cambiar_tema);
        if (btn == null) return;
        btn.setOnClickListener(v -> alternarTemaInicio());
    }

    /**
     * Redirige al tema alternativo si está seleccionado. Solo la MainActivity
     * base despacha; las subclases (que YA son el tema destino) nunca redirigen,
     * evitando así un bucle de reenvíos.
     */
    private boolean redirigirATemaSeleccionado() {
        if (getClass() != MainActivity.class) return false;

        int tema = getSharedPreferences(NOMBRE_PREFS, MODE_PRIVATE)
                .getInt(PREF_TEMA_INICIO, TEMA_PRINCIPAL);
        if (tema == TEMA_ALTERNATIVO) {
            Intent i = new Intent(this,
                    codigo.cafe.alsvinlaunch.activity.MainActivityTema2.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i);
            overridePendingTransition(0, 0);
            finish();
            return true;
        }
        return false;
    }

    /** Alterna entre el tema principal y el alternativo y salta a su actividad. */
    private void alternarTemaInicio() {
        int actual = prefs.getInt(PREF_TEMA_INICIO, TEMA_PRINCIPAL);
        int nuevo  = (actual == TEMA_PRINCIPAL) ? TEMA_ALTERNATIVO : TEMA_PRINCIPAL;
        prefs.edit().putInt(PREF_TEMA_INICIO, nuevo).apply();

        Class<?> destino = (nuevo == TEMA_ALTERNATIVO)
                ? codigo.cafe.alsvinlaunch.activity.MainActivityTema2.class
                : MainActivity.class;
        if (getClass() == destino) return; // ya estamos en ese tema

        abriendoApp = true; // evitar que el modo kiosco interfiera con el salto
        Intent i = new Intent(this, destino);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(i);
        overridePendingTransition(0, 0);
        finish();
    }

    private void mostrarSelectorIdioma() {
        // Cada idioma se muestra en su propio nombre, no traducido.
        final String[] codigos  = { LocaleHelper.ESPANOL, LocaleHelper.INGLES };
        final String[] nombres  = { "Español", "English" };

        String actual = LocaleHelper.getIdiomaGuardado(this);
        int seleccion = actual.equals(LocaleHelper.INGLES) ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle(R.string.idioma_titulo)
                .setSingleChoiceItems(nombres, seleccion, (dialog, which) -> {
                    dialog.dismiss();
                    String nuevo = codigos[which];
                    if (!nuevo.equals(LocaleHelper.getIdiomaGuardado(this))) {
                        LocaleHelper.guardarIdioma(this, nuevo);
                        recrearParaAplicarIdioma();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Recrea la actividad para que el nuevo idioma se aplique a toda la UI. */
    private void recrearParaAplicarIdioma() {
        // recreate() reejecuta attachBaseContext -> LocaleHelper.onAttach con el
        // idioma ya guardado, así que toda la jerarquía se reconstruye traducida.
        recreate();
    }

    private void mostrarPaletaColores() {
        final int[] colores = {
            0xFF1A1A2E, 0xFF0D0D1A, 0xFF16213E, 0xFF1B2838, 0xFF0F2027,
            0xFF1A2A1A, 0xFF2A1A1A, 0xFF1A1A1A, 0xFF0A0A0A, 0xFF000000,
            0xFF1F3A5F, 0xFF2D4739, 0xFF4A1942, 0xFF3B2314, 0xFF1C3A4A,
            0xFF2E4057, 0xFF3D5A80, 0xFF2D6A4F, 0xFF6B2737, 0xFF4A3728,
            0xFF102542, 0xFF1B4332, 0xFF3C096C, 0xFF7B2D00, 0xFF003049,
        };

        android.content.Context ctx = this;
        float dp = getResources().getDisplayMetrics().density;
        int pad    = (int)(16 * dp);
        int margin = (int)(5  * dp);
        int stroke = (int)(3  * dp);
        int cols   = 5;

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(R.string.dlg_color_fondo_titulo);

        android.widget.ScrollView scroll = new android.widget.ScrollView(ctx);
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, pad);

        // ── Chips de colores ──
        // Guardamos los drawables para actualizar el resaltado dinámicamente
        GradientDrawable[] chipDrawables = new GradientDrawable[colores.length];
        // rgb[] y previewBgRef se declaran aquí para que los lambdas de los chips puedan usarlos
        final int[] rgb = {Color.red(colorActualFondo), Color.green(colorActualFondo), Color.blue(colorActualFondo)};
        final GradientDrawable[] previewBgRef = {null};

        // Creamos filas de 5 chips con tamaño fijo centradas
        int chipSize = (int)(44 * dp);
        int rows = (int) Math.ceil(colores.length / (double) cols);
        int idx = 0;
        for (int r = 0; r < rows; r++) {
            LinearLayout fila = new LinearLayout(ctx);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams filaLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            filaLp.bottomMargin = margin;
            fila.setLayoutParams(filaLp);

            for (int c = 0; c < cols; c++) {
                if (idx >= colores.length) break;

                View chip = new View(ctx);
                LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(chipSize, chipSize);
                chipLp.setMargins(margin, 0, margin, 0);
                chip.setLayoutParams(chipLp);

                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(colores[idx]);
                if (colores[idx] == colorActualFondo) {
                    bg.setStroke(stroke, 0xFFFFFFFF);
                }
                chip.setBackground(bg);
                chipDrawables[idx] = bg;

                final int colorElegido = colores[idx];
                final int chipIdx = idx;
                chip.setOnClickListener(cv -> {
                    // Quitar resaltado de todos
                    for (GradientDrawable d : chipDrawables) {
                        if (d != null) d.setStroke(0, Color.TRANSPARENT);
                    }
                    // Resaltar el elegido
                    chipDrawables[chipIdx].setStroke(stroke, 0xFFFFFFFF);
                    // Actualizar rgb[] y preview para que "Aplicar" use este color
                    rgb[0] = Color.red(colorElegido);
                    rgb[1] = Color.green(colorElegido);
                    rgb[2] = Color.blue(colorElegido);
                    if (previewBgRef[0] != null) previewBgRef[0].setColor(colorElegido);
                });
                fila.addView(chip);
                idx++;
            }
            container.addView(fila);
        }

        // ── Sliders RGB personalizados ──
        TextView labelPersonalizado = new TextView(ctx);
        labelPersonalizado.setText(R.string.lbl_personalizado_rgb);
        labelPersonalizado.setTextColor(0xFFB0B0B0);
        labelPersonalizado.setTextSize(12);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLp.topMargin = (int)(12 * dp);
        tvLp.bottomMargin = (int)(8 * dp);
        labelPersonalizado.setLayoutParams(tvLp);
        container.addView(labelPersonalizado);

        // Preview
        View preview = new View(ctx);
        LinearLayout.LayoutParams pvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(40 * dp));
        pvLp.bottomMargin = (int)(8 * dp);
        preview.setLayoutParams(pvLp);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setShape(GradientDrawable.RECTANGLE);
        previewBg.setCornerRadius(8 * dp);
        previewBg.setColor(colorActualFondo);
        preview.setBackground(previewBg);
        previewBgRef[0] = previewBg;
        container.addView(preview);

        String[] etiquetas = {"R", "G", "B"};
        int[] initVals = {rgb[0], rgb[1], rgb[2]};

        for (int i = 0; i < 3; i++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = margin;
            row.setLayoutParams(rowLp);

            TextView label = new TextView(ctx);
            label.setText(etiquetas[i]);
            label.setTextColor(0xFFFFFFFF);
            label.setTextSize(13);
            label.setMinWidth((int)(20 * dp));
            row.addView(label);

            SeekBar sb = new SeekBar(ctx);
            sb.setMax(255);
            sb.setProgress(initVals[i]);
            LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            sbLp.setMarginStart(margin);
            sb.setLayoutParams(sbLp);

            final int canal = i;
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    rgb[canal] = progress;
                    // Quitar resaltado de chips de preset
                    for (GradientDrawable d : chipDrawables) {
                        if (d != null) d.setStroke(0, Color.TRANSPARENT);
                    }
                    previewBg.setColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            row.addView(sb);
            container.addView(row);
        }

        scroll.addView(container);
        builder.setView(scroll);

        builder.setPositiveButton(R.string.btn_aplicar, (dialog, which) ->
                aplicarColorFondo(Color.rgb(rgb[0], rgb[1], rgb[2])));
        builder.setNeutralButton(R.string.btn_restablecer, (dialog, which) ->
                aplicarColorFondo(COLOR_BASE_FONDO));
        builder.setNegativeButton(R.string.btn_cancelar, null);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialogo_bg);
        }
        dialog.show();
    }

    private void aplicarColorFondo(int color) {
        // Desactivar modo dinámico para que no sobreescriba la elección manual
        if (modoColorDinamico) {
            modoColorDinamico = false;
            prefs.edit()
                    .putBoolean(PREF_COLOR_DINAMICO, false)
                    .putInt(PREF_COLOR_FONDO, color)
                    .apply();
        } else {
            prefs.edit().putInt(PREF_COLOR_FONDO, color).apply();
        }
        animarColorFondo(color);
    }

    // ══════════════════════════════════════
    //  COLOR DINÁMICO
    // ══════════════════════════════════════

    private void animarColorFondo(int colorDestino) {
        if (colorActualFondo == colorDestino) return;

        int colorOrigen = colorActualFondo;
        colorActualFondo = colorDestino; // actualizar de inmediato para evitar race conditions

        ValueAnimator animator = ValueAnimator.ofObject(
                new ArgbEvaluator(), colorOrigen, colorDestino);
        animator.setDuration(800);
        animator.addUpdateListener(anim ->
                findViewById(R.id.root_layout)
                        .setBackgroundColor((int) anim.getAnimatedValue()));
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
                mostrarMensaje(getString(R.string.msg_no_procesos));
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

                String detalleRam = ganancia > 0
                        ? getString(R.string.msg_ram_liberada, ganancia)
                        : getString(R.string.msg_ram_sin_cambio);
                mostrarMensaje(getString(R.string.msg_apps_cerradas,
                        totalLiberados, ramLibreAntes, ramLibreDespues, detalleRam));
            }, 500);

        } catch (Exception e) {
            mostrarMensaje(getString(R.string.msg_error, e.getMessage()));
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
    //  Botón de donación
    // ══════════════════════════════════════

    private void configurarBotonDonacion() {
        botonDonacion = findViewById(R.id.btn_donacion);
        if (botonDonacion == null) return;

        botonDonacion.setOnClickListener(v ->
                donationManager.iniciarDonacion(new DonationManager.OnDonacionListener() {
                    @Override
                    public void onDonacionExitosa() {
                        mostrarMensaje(getString(R.string.msg_gracias_apoyo));
                        actualizarVisibilidadDonacion();
                    }

                    @Override
                    public void onDonacionError(String mensaje) {
                        mostrarMensaje(getString(R.string.msg_error, mensaje));
                    }
                })
        );

        actualizarVisibilidadDonacion();
    }

    private void actualizarVisibilidadDonacion() {
        if (botonDonacion == null || donationManager == null) return;
        botonDonacion.setVisibility(donationManager.haDonado() ? View.GONE : View.VISIBLE);
    }


    // ══════════════════════════════════════
    //  RESULTADOS Y PERMISOS
    // ══════════════════════════════════════

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (!managersListos) {
            // Los managers aún no están listos (actividad recreada con .post()).
            // Guardamos el resultado para procesarlo al final de inicializarManagersCompletos().
            pendingResultRequestCode = requestCode;
            pendingResultResultCode  = resultCode;
            pendingResultData        = data;
            return;
        }

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