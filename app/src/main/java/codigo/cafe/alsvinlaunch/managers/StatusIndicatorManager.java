package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import codigo.cafe.alsvinlaunch.R;

/**
 * Gestiona los indicadores de estado WiFi y Bluetooth.
 *
 * Fixes respecto a la versión anterior:
 *  - BT binder null: se obtiene BluetoothAdapter vía BluetoothManager en lugar de
 *    getDefaultAdapter(), que en HiveOS devuelve un adaptador con binder nulo.
 *    Además se añade un guard explícito antes de cualquier llamada al adaptador.
 *  - Timer de respaldo ahora solo refresca WiFi. BT tiene su propio BroadcastReceiver
 *    y no necesita polling — llamarlo cada 10 s era la fuente del spam en logcat.
 *  - btAdapterDisponible: flag que evita reintentar si el sistema no tiene BT real.
 */
public class StatusIndicatorManager {

    private static final long INTERVALO_RESPALDO   = 10_000;
    private static final int  COLOR_WIFI_CONECTADO = Color.parseColor("#4CAF50");
    private static final int  COLOR_BT_ACTIVO      = Color.parseColor("#2196F3");
    private static final int  COLOR_INACTIVO       = Color.parseColor("#B0B0B0");

    private final Activity  actividad;
    private final ImageView iconoWifi;
    private final TextView  textoWifi;
    private final ImageView iconoBluetooth;
    private final TextView  textoBluetooth;

    private Timer             temporizador;
    private BroadcastReceiver receptorWifi;
    private BroadcastReceiver receptorBluetooth;

    // Caché del último nombre BT conocido (evita parpadeos)
    private String  ultimoNombreBt    = null;
    // Flag: si el sistema no tiene BT funcional, no seguir intentando
    private boolean btAdapterDisponible = true;
    // Referencia al adaptador obtenido una sola vez via BluetoothManager
    private BluetoothAdapter btAdapter = null;

    private boolean corriendo = false; // ← agregar campo



    public StatusIndicatorManager(Activity actividad) {
        this.actividad      = actividad;
        this.iconoWifi      = actividad.findViewById(R.id.icono_wifi);
        this.textoWifi      = actividad.findViewById(R.id.texto_wifi);
        this.iconoBluetooth = actividad.findViewById(R.id.icono_bluetooth);
        this.textoBluetooth = actividad.findViewById(R.id.texto_bluetooth);

        // Obtener adaptador una sola vez — más seguro que getDefaultAdapter() en HiveOS
        inicializarBluetoothAdapter();
    }

    // ══════════════════════════════════════
    //  CICLO DE VIDA
    // ══════════════════════════════════════

    public void iniciar() {
        if (corriendo) return; // ← guard
        corriendo = true;
        registrarReceptorWifi();
        registrarReceptorBluetooth();
        iniciarTimerRespaldo();

        actualizarWifi();
        actualizarBluetooth();
    }

    public void detener() {
        corriendo = false; // ← resetear guard

        if (temporizador != null) {
            temporizador.cancel();
            temporizador.purge();
            temporizador = null;
        }
        try {
            if (receptorWifi != null) {
                actividad.unregisterReceiver(receptorWifi);
                receptorWifi = null; // ← limpiar referencia
            }
            if (receptorBluetooth != null) {
                actividad.unregisterReceiver(receptorBluetooth);
                receptorBluetooth = null; // ← limpiar referencia
            }
        } catch (IllegalArgumentException ignored) {}
    }
    public void actualizar() {
        actualizarWifi();
        actualizarBluetooth();
    }
    // ══════════════════════════════════════
    //  BLUETOOTH — inicialización segura
    // ══════════════════════════════════════

    /**
     * Obtiene el adaptador a través de BluetoothManager (API 18+).
     * En HiveOS/Android 6, getDefaultAdapter() puede devolver una instancia
     * con binder nulo que lanza excepciones en cada llamada. BluetoothManager
     * es la vía recomendada y falla de forma limpia si BT no está disponible.
     */
    private void inicializarBluetoothAdapter() {
        try {
            BluetoothManager btManager = (BluetoothManager)
                    actividad.getSystemService(Context.BLUETOOTH_SERVICE);
            if (btManager != null) {
                btAdapter = btManager.getAdapter();
            }
            if (btAdapter == null) {
                // El dispositivo no tiene BT o el servicio no está disponible aún
                btAdapterDisponible = false;
            }
        } catch (Exception e) {
            btAdapter = null;
            btAdapterDisponible = false;
        }
    }

    // ══════════════════════════════════════
    //  RECEPTORES DE BROADCAST
    // ══════════════════════════════════════

    private void registrarReceptorWifi() {
        receptorWifi = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                actualizarWifi();
            }
        };
        IntentFilter filtro = new IntentFilter();
        filtro.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filtro.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filtro.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        //actividad.registerReceiver(receptorWifi, filtro);
        ContextCompat.registerReceiver(actividad, receptorWifi, filtro,
                ContextCompat.RECEIVER_EXPORTED);
    }

    private void registrarReceptorBluetooth() {
        receptorBluetooth = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String accion = intent.getAction();

                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(accion)) {
                    int estado = intent.getIntExtra(
                            BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    if (estado == BluetoothAdapter.STATE_ON) {
                        // BT acaba de encenderse — puede que btAdapter estuviera nulo
                        if (btAdapter == null) inicializarBluetoothAdapter();
                        actualizarBluetooth();
                    } else if (estado == BluetoothAdapter.STATE_OFF
                            || estado == BluetoothAdapter.STATE_TURNING_OFF) {
                        ultimoNombreBt = null;
                        setBtEstado(R.drawable.ic_bt_off, actividad.getString(R.string.estado_bt_off), COLOR_INACTIVO);
                    }

                } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(accion)) {
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && device.getName() != null
                            && !device.getName().isEmpty()) {
                        ultimoNombreBt = device.getName();
                    }
                    actualizarBluetooth();

                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(accion)) {
                    ultimoNombreBt = null;
                    actualizarBluetooth();
                }
            }
        };

        IntentFilter filtro = new IntentFilter();
        filtro.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filtro.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filtro.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        //actividad.registerReceiver(receptorBluetooth, filtro);
        ContextCompat.registerReceiver(actividad, receptorBluetooth, filtro,
                ContextCompat.RECEIVER_EXPORTED);
    }

    /**
     * FIX CLAVE: el timer solo refresca WiFi.
     * BT tiene su BroadcastReceiver y no necesita polling — era la causa
     * del "Bluetooth binder is null" repetido cada 10 s en logcat.
     */
    private void iniciarTimerRespaldo() {
        temporizador = new Timer();
        temporizador.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                actividad.runOnUiThread(() -> actualizarWifi());
                // BT deliberadamente excluido del timer
            }
        }, INTERVALO_RESPALDO, INTERVALO_RESPALDO);
    }

    // ══════════════════════════════════════
    //  WIFI
    // ══════════════════════════════════════

    public void actualizarWifi() {
        try {
            WifiManager wm = (WifiManager) actividad.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);

            if (wm == null || !wm.isWifiEnabled()) {
                setWifiEstado(R.drawable.ic_wifi_off, actividad.getString(R.string.estado_wifi_off), COLOR_INACTIVO);
                return;
            }

            ConnectivityManager cm = (ConnectivityManager)
                    actividad.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm != null ? cm.getActiveNetworkInfo() : null;
            boolean hayConexionWifi = netInfo != null
                    && netInfo.isConnected()
                    && netInfo.getType() == ConnectivityManager.TYPE_WIFI;

            if (!hayConexionWifi) {
                setWifiEstado(R.drawable.ic_wifi_off, actividad.getString(R.string.estado_sin_red), COLOR_INACTIVO);
                return;
            }

            WifiInfo info = wm.getConnectionInfo();
            if (info != null && info.getNetworkId() != -1) {
                String ssid = info.getSSID();
                if (ssid != null) ssid = ssid.replace("\"", "");
                boolean ssidValido = ssid != null
                        && !ssid.isEmpty()
                        && !ssid.equals("<unknown ssid>");
                setWifiEstado(R.drawable.ic_wifi_on,
                        ssidValido ? ssid : actividad.getString(R.string.estado_conectado),
                        COLOR_WIFI_CONECTADO);
            } else {
                setWifiEstado(R.drawable.ic_wifi_off, actividad.getString(R.string.estado_sin_red), COLOR_INACTIVO);
            }
        } catch (Exception e) {
            setWifiEstado(R.drawable.ic_wifi_off, "WiFi", COLOR_INACTIVO);
        }
    }

    private void setWifiEstado(int icono, String texto, int color) {
        iconoWifi.setImageResource(icono);
        textoWifi.setText(texto);
        textoWifi.setTextColor(color);
    }

    // ══════════════════════════════════════
    //  BLUETOOTH
    // ══════════════════════════════════════

    public void actualizarBluetooth() {
        // Guard: si sabemos que BT no está disponible, no intentar nada
        if (!btAdapterDisponible || btAdapter == null) {
            setBtEstado(R.drawable.ic_bt_off, actividad.getString(R.string.estado_bt_off), COLOR_INACTIVO);
            return;
        }

        try {
            // Guard adicional: verificar que isEnabled() no lance excepción
            boolean habilitado;
            try {
                habilitado = btAdapter.isEnabled();
            } catch (Exception e) {
                // Binder nulo u otro error del sistema — no reintentar
                btAdapterDisponible = false;
                setBtEstado(R.drawable.ic_bt_off, actividad.getString(R.string.estado_bt_off), COLOR_INACTIVO);
                return;
            }

            if (habilitado) {
                String nombre = obtenerDispositivoConectado();
                iconoBluetooth.setImageResource(R.drawable.ic_bt_on);
                textoBluetooth.setText(nombre != null ? nombre : actividad.getString(R.string.estado_bt_on));
                textoBluetooth.setTextColor(COLOR_BT_ACTIVO);
            } else {
                ultimoNombreBt = null;
                setBtEstado(R.drawable.ic_bt_off, actividad.getString(R.string.estado_bt_off), COLOR_INACTIVO);
            }
        } catch (Exception e) {
            setBtEstado(R.drawable.ic_bt_off, "BT", COLOR_INACTIVO);
        }
    }

    private void setBtEstado(int icono, String texto, int color) {
        iconoBluetooth.setImageResource(icono);
        textoBluetooth.setText(texto);
        textoBluetooth.setTextColor(color);
    }

    private String obtenerDispositivoConectado() {
        if (btAdapter == null) return null;

        try {
            // ── Método 1: BluetoothManager oficial ──
            BluetoothManager btManager = (BluetoothManager)
                    actividad.getSystemService(Context.BLUETOOTH_SERVICE);
            if (btManager != null) {
                int[] perfiles = {
                        BluetoothProfile.A2DP,
                        BluetoothProfile.HEADSET,
                        BluetoothProfile.GATT
                };
                for (int perfil : perfiles) {
                    try {
                        List<BluetoothDevice> conectados =
                                btManager.getConnectedDevices(perfil);
                        if (conectados != null && !conectados.isEmpty()) {
                            String nombre = conectados.get(0).getName();
                            ultimoNombreBt = (nombre != null && !nombre.isEmpty())
                                    ? nombre : actividad.getString(R.string.estado_conectado);
                            return ultimoNombreBt;
                        }
                    } catch (Exception ignored) {}
                }
            }

            // ── Método 2: reflexión sobre dispositivos vinculados ──
            if (btAdapter.getBondedDevices() != null) {
                for (BluetoothDevice dispositivo : btAdapter.getBondedDevices()) {
                    try {
                        Method esConectado = dispositivo.getClass()
                                .getMethod("isConnected");
                        if ((boolean) esConectado.invoke(dispositivo)) {
                            String nombre = dispositivo.getName();
                            ultimoNombreBt = (nombre != null && !nombre.isEmpty())
                                    ? nombre : actividad.getString(R.string.estado_conectado);
                            return ultimoNombreBt;
                        }
                    } catch (Exception ignored) {}
                }
            }

            // ── Método 3: getProfileConnectionState (último recurso) ──
            int[] perfiles = {BluetoothProfile.A2DP, BluetoothProfile.HEADSET};
            for (int perfil : perfiles) {
                try {
                    Method getState = btAdapter.getClass()
                            .getMethod("getProfileConnectionState", int.class);
                    int estado = (int) getState.invoke(btAdapter, perfil);
                    if (estado == BluetoothProfile.STATE_CONNECTED) {
                        return ultimoNombreBt != null ? ultimoNombreBt : actividad.getString(R.string.estado_conectado);
                    }
                } catch (Exception ignored) {}
            }

        } catch (Exception ignored) {}

        ultimoNombreBt = null;
        return null;
    }



}