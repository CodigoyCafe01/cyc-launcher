package codigo.cafe.alsvinlaunch;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import codigo.cafe.alsvinlaunch.managers.BluetoothContactsManager;
import codigo.cafe.alsvinlaunch.managers.ClockManager;
import codigo.cafe.alsvinlaunch.managers.ContactsDialogHelper;
import codigo.cafe.alsvinlaunch.managers.KioskManager;
import codigo.cafe.alsvinlaunch.managers.MediaWidgetManager;
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

    // ── Estado ──
    private boolean abriendoApp = false;

    // ── Vistas directas ──
    private TextView textoReloj;
    private TextView textoFecha;
    private ImageView botonTema;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        SharedPreferences prefs = getSharedPreferences(NOMBRE_PREFS, MODE_PRIVATE);

        // ── Buscar vistas ──
        textoReloj = (TextView) findViewById(R.id.clock_text);
        textoFecha = (TextView) findViewById(R.id.date_text);
        botonTema = (ImageView) findViewById(R.id.btn_theme_toggle);
        LinearLayout panelSistema = (LinearLayout) findViewById(R.id.system_panel);

        // ── Inicializar managers ──
        clockManager = new ClockManager(this, textoReloj, textoFecha);
        themeManager = new ThemeManager(this, prefs);
        kioskManager = new KioskManager(this, prefs);

        shortcutManager = new ShortcutManager(this, prefs);
        shortcutManager.setOnAppOpenListener(new ShortcutManager.OnAppOpenListener() {
            @Override
            public void onAppOpening() { abriendoApp = true; }
            @Override
            public void onAppOpenFailed() { abriendoApp = false; }
        });

        systemPanelManager = new SystemPanelManager(this, panelSistema);
        systemPanelManager.setOnAppOpenListener(new SystemPanelManager.OnAppOpenListener() {
            @Override
            public void onAppOpening() { abriendoApp = true; }
            @Override
            public void onAppOpenFailed() { abriendoApp = false; }
        });

        btContactsManager = new BluetoothContactsManager(this);

        mediaWidgetManager = new MediaWidgetManager(this);
        statusIndicatorManager = new StatusIndicatorManager(this);

        contactsDialogHelper = new ContactsDialogHelper(this, prefs, btContactsManager);
        contactsDialogHelper.setOnAppOpenListener(() -> abriendoApp = true);



        // ── Configurar ──
        shortcutManager.precargarSiNecesario();
        shortcutManager.configurarVistas(() -> systemPanelManager.ocultar());
        systemPanelManager.construir();

        configurarReloj();
        configurarBotonTema();
        configurarBotonApps();
        configurarBotonSistema();
        configurarIndicadoresEstado();

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
        // Long press en el reloj → abre sniffer
        findViewById(R.id.boton_reloj).setOnLongClickListener(v -> {
            abriendoApp = true; // ← ESTO faltaba
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
        findViewById(R.id.fab_system).setOnClickListener(v -> systemPanelManager.alternar());
        findViewById(R.id.root_layout).setOnClickListener(v -> systemPanelManager.ocultar());
        findViewById(R.id.fab_system).setOnLongClickListener(v -> {
            abriendoApp = true; // ← ESTO faltaba
            startActivity(new Intent(this, SystemExplorerActivity.class));
            return true;
        });
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

        // Bluetooth → contactos rápidos
        findViewById(R.id.indicador_bluetooth).setOnClickListener(
                v -> contactsDialogHelper.mostrar());

        findViewById(R.id.indicador_bluetooth).setOnLongClickListener(v -> {
            // Mostrar diagnóstico en lugar de ajustes BT
            String diag = btContactsManager.generarDiagnostico();
            Log.d("BT_DIAG", diag);
            // O mostrar en un AlertDialog
            return true;
        });

        // Bluetooth largo → ajustes BT
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
        mediaWidgetManager.actualizar();
        aplicarTema();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockManager.detener();
        kioskManager.destruir();
        mediaWidgetManager.destruir();
        statusIndicatorManager.detener();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        shortcutManager.procesarResultado(requestCode, resultCode, data);
    }

    // ══════════════════════════════════════
    //  UTILIDADES
    // ══════════════════════════════════════

    private void mostrarMensaje(String mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
    }
}
