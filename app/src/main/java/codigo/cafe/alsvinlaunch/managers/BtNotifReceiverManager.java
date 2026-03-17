package codigo.cafe.alsvinlaunch.managers;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;

import codigo.cafe.alsvinlaunch.model.NotifModel;


/**
 * Manager que abre un servidor SPP Bluetooth y escucha notificaciones
 * enviadas por Notisvin (la app en el teléfono del usuario).
 *
 * Protocolo: cada notificación es una línea JSON terminada en '\n'.
 * UUID SPP estándar: 00001101-0000-1000-8000-00805F9B34FB
 *
 * Uso:
 *   manager = new BtNotifReceiverManager(context, notif -> actualizarUI(notif));
 *   manager.iniciar();
 *   // en onDestroy:
 *   manager.detener();
 */
public class BtNotifReceiverManager {

    private static final String TAG = "BtNotifReceiver";
    private static final UUID  SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String SERVICE_NAME = "NotisvinReceiver";

    public interface OnNotifListener {
        void onNotifRecibida(NotifModel notif);
    }

    private final OnNotifListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Thread         serverThread;
    private Thread         readerThread;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket       clientSocket;
    private volatile boolean      corriendo = false;

    public BtNotifReceiverManager(OnNotifListener listener) {
        this.listener = listener;
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    public void iniciar() {
        if (corriendo) return;
        corriendo = true;
        serverThread = new Thread(this::loopServidor, "BtNotifServer");
        serverThread.setDaemon(true);
        serverThread.start();
        Log.d(TAG, "Servidor BT SPP iniciado");
    }

    public void detener() {
        corriendo = false;
        cerrarSockets();
        if (serverThread != null) serverThread.interrupt();
        if (readerThread != null) readerThread.interrupt();
        Log.d(TAG, "Servidor BT SPP detenido");
    }

    public boolean isConectado() {
        return clientSocket != null && clientSocket.isConnected();
    }

    // ─── Servidor ────────────────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    private void loopServidor() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.w(TAG, "Bluetooth no disponible o apagado");
            return;
        }

        while (corriendo) {
            try {
                // Abrir socket servidor
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID);
                Log.d(TAG, "Esperando conexión BT...");

                // Bloquea hasta que un cliente se conecte
                clientSocket = serverSocket.accept();
                serverSocket.close(); // Ya no necesitamos aceptar más clientes
                serverSocket = null;

                //Log.d(TAG, "Cliente BT conectado: " + clientSocket.getRemoteDevice().getName());

                // Leer en hilo separado
                readerThread = new Thread(this::loopLector, "BtNotifReader");
                readerThread.setDaemon(true);
                readerThread.start();
                readerThread.join(); // Esperar a que el lector termine (cliente desconectado)

                Log.d(TAG, "Cliente BT desconectado, volviendo a escuchar...");

            } catch (Exception e) {
                if (corriendo) {
                    Log.w(TAG, "Error en servidor BT, reintentando en 3s: " + e.getMessage());
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    // ─── Lector de líneas ────────────────────────────────────────────────────

    private void loopLector() {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream())
            );

            String linea;
            while (corriendo && (linea = reader.readLine()) != null) {
                final String json = linea.trim();
                if (json.isEmpty()) continue;

                Log.d(TAG, "JSON recibido: " + json);
                NotifModel notif = NotifModel.fromJson(json);
                if (notif != null) {
                    // Publicar en hilo principal
                    mainHandler.post(() -> listener.onNotifRecibida(notif));
                }
            }
        } catch (Exception e) {
            if (corriendo) Log.w(TAG, "Conexión BT cerrada: " + e.getMessage());
        } finally {
            cerrarClientSocket();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void cerrarSockets() {
        cerrarClientSocket();
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Exception ignored) {}
            serverSocket = null;
        }
    }

    private void cerrarClientSocket() {
        if (clientSocket != null) {
            try { clientSocket.close(); } catch (Exception ignored) {}
            clientSocket = null;
        }
    }
}
