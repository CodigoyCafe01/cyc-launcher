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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import codigo.cafe.alsvinlaunch.R;

/**
 * Gestiona los indicadores de estado WiFi y Bluetooth.
 * Usa BroadcastReceivers para reaccionar en tiempo real + Timer como respaldo.
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
    private String ultimoNombreBt = null;

    public StatusIndicatorManager(Activity actividad) {
        this.actividad      = actividad;
        this.iconoWifi      = actividad.findViewById(R.id.icono_wifi);
        this.textoWifi      = actividad.findViewById(R.id.texto_wifi);
        this.iconoBluetooth = actividad.findViewById(R.id.icono_bluetooth);
        this.textoBluetooth = actividad.findViewById(R.id.texto_bluetooth);
    }

    // ══════════════════════════════════════
    //  CICLO DE VIDA
    // ══════════════════════════════════════

    public void iniciar() {
        registrarReceptorWifi();
        registrarReceptorBluetooth();
        iniciarTimerRespaldo();

        // Estado inmediato al arrancar
        actualizarWifi();
        actualizarBluetooth();
    }

    public void detener() {
        if (temporizador != null) {
            temporizador.cancel();
            temporizador = null;
        }
        try {
            if (receptorWifi != null)      actividad.unregisterReceiver(receptorWifi);
            if (receptorBluetooth != null) actividad.unregisterReceiver(receptorBluetooth);
        } catch (IllegalArgumentException ignored) {}
    }

    // ══════════════════════════════════════
    //  RECEPTORES DE BROADCAST
    // ══════════════════════════════════════

    private void registrarReceptorWifi() {
        receptorWifi = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                actualizarWifi();
            }
        };

        IntentFilter filtro = new IntentFilter();
        filtro.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION); // cambia de red / SSID nuevo
        filtro.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);    // WiFi on/off
        filtro.addAction(ConnectivityManager.CONNECTIVITY_ACTION);  // conectado/desconectado

        actividad.registerReceiver(receptorWifi, filtro);
    }

    private void registrarReceptorBluetooth() {
        receptorBluetooth = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String accion = intent.getAction();

                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(accion)) {
                    // Capturar nombre directamente del evento — el más fiable
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && device.getName() != null && !device.getName().isEmpty()) {
                        ultimoNombreBt = device.getName();
                    }
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(accion)) {
                    ultimoNombreBt = null;
                }

                actualizarBluetooth();
            }
        };

        IntentFilter filtro = new IntentFilter();
        filtro.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filtro.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filtro.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);

        actividad.registerReceiver(receptorBluetooth, filtro);
    }

    private void iniciarTimerRespaldo() {
        temporizador = new Timer();
        temporizador.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                actividad.runOnUiThread(() -> {
                    actualizarWifi();
                    actualizarBluetooth();
                });
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
                setWifiEstado(R.drawable.ic_wifi_off, "WiFi Off", COLOR_INACTIVO);
                return;
            }

            // Verificar conectividad real, no solo que WiFi esté encendido
            ConnectivityManager cm = (ConnectivityManager)
                    actividad.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm != null ? cm.getActiveNetworkInfo() : null;
            boolean hayConexionWifi = netInfo != null
                    && netInfo.isConnected()
                    && netInfo.getType() == ConnectivityManager.TYPE_WIFI;

            if (!hayConexionWifi) {
                setWifiEstado(R.drawable.ic_wifi_off, "Sin red", COLOR_INACTIVO);
                return;
            }

            WifiInfo info = wm.getConnectionInfo();
            if (info != null && info.getNetworkId() != -1) {
                String ssid = info.getSSID();
                if (ssid != null) ssid = ssid.replace("\"", "");
                boolean ssidValido = ssid != null
                        && !ssid.isEmpty()
                        && !ssid.equals("<unknown ssid>");

                setWifiEstado(
                        R.drawable.ic_wifi_on,
                        ssidValido ? ssid : "Conectado",
                        COLOR_WIFI_CONECTADO
                );
            } else {
                setWifiEstado(R.drawable.ic_wifi_off, "Sin red", COLOR_INACTIVO);
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
        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter != null && btAdapter.isEnabled()) {
                String nombre = obtenerDispositivoConectado(btAdapter);
                iconoBluetooth.setImageResource(R.drawable.ic_bt_on);
                textoBluetooth.setText(nombre != null ? nombre : "BT On");
                textoBluetooth.setTextColor(COLOR_BT_ACTIVO);
            } else {
                ultimoNombreBt = null;
                setBtEstado(R.drawable.ic_bt_off, "BT Off", COLOR_INACTIVO);
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

    private String obtenerDispositivoConectado(BluetoothAdapter btAdapter) {
        try {
            // ── Método 1: BluetoothManager oficial (API 18+) ──
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
                                    ? nombre : "Conectado";
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
                                    ? nombre : "Conectado";
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
                        // Hay conexión pero no logramos el nombre → devolver caché
                        return ultimoNombreBt != null ? ultimoNombreBt : "Conectado";
                    }
                } catch (Exception ignored) {}
            }

        } catch (Exception ignored) {}

        ultimoNombreBt = null;
        return null;
    }
}