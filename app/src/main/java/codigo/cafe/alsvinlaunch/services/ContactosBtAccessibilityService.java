package codigo.cafe.alsvinlaunch.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import codigo.cafe.alsvinlaunch.managers.BluetoothContactsManager;
import codigo.cafe.alsvinlaunch.managers.ContactosLocalesManager;

/**
 * Accessibility Service que lee la app nativa de contactos BT del head unit.
 *
 * FLUJO:
 * 1. Usuario abre la app nativa de BT → Book/Contactos
 * 2. Este servicio detecta la pantalla y empieza a leer los nodos
 * 3. Extrae nombres + números y los guarda en ContactosLocalesManager
 * 4. Si hay scroll, baja automáticamente para leer todos
 * 5. Envía broadcast cuando termina → tu app muestra resultado
 *
 * BROADCAST de resultado: "codigo.cafe.alsvinlaunch.CONTACTOS_IMPORTADOS"
 *   extra "cantidad" → int con el número de contactos importados
 */
public class ContactosBtAccessibilityService extends AccessibilityService {

    private static final String TAG = "BtA11y";

    // Broadcast que se envía al terminar
    public static final String ACTION_CONTACTOS_IMPORTADOS =
            "codigo.cafe.alsvinlaunch.CONTACTOS_IMPORTADOS";
    public static final String EXTRA_CANTIDAD = "cantidad";

    // Paquetes de la app nativa BT del head unit a monitorear
    private static final String[] PAQUETES_BT = {
            "com.hive.phone",
            "com.android.phone",
            "com.mediatek.phone",
            "com.hive.bluetooth",
            "com.android.bluetooth",
            "com.hive.btphone",
            "com.hive.incallui"
    };

    // Patrón para detectar números de teléfono
    private static final Pattern PATRON_TELEFONO = Pattern.compile(
            "^[+]?[0-9\\s\\-().]{7,20}$"
    );

    private ContactosLocalesManager locales;
    private final List<BluetoothContactsManager.ContactoBt> contactosLeidos = new ArrayList<>();
    private boolean leyendo = false;
    private String paqueteActivo = null;

    // Para auto-scroll
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runableScroll;
    private int scrollsRealizados = 0;
    private static final int MAX_SCROLLS = 50; // hasta 50 páginas de contactos
    private int contactosAntesDeScroll = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        locales = new ContactosLocalesManager(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String paquete = event.getPackageName() != null ?
                event.getPackageName().toString() : "";

        // Verificar si es la app BT nativa
        if (!esPaqueteBt(paquete)) return;

        paqueteActivo = paquete;

        int tipo = event.getEventType();

        if (tipo == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                tipo == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                tipo == AccessibilityEvent.TYPE_VIEW_SCROLLED) {

            // Pequeño delay para que la UI termine de renderizar
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(this::leerPantallaActual, 300);
        }
    }

    // ══════════════════════════════════════
    //  LECTURA DE PANTALLA
    // ══════════════════════════════════════

    private void leerPantallaActual() {
        AccessibilityNodeInfo raiz = getRootInActiveWindow();
        if (raiz == null) return;

        try {
            int antesDeLeer = contactosLeidos.size();
            leerNodos(raiz, null);
            int nuevos = contactosLeidos.size() - antesDeLeer;

            if (nuevos > 0) {
                Log.d(TAG, "Leídos " + nuevos + " contactos nuevos. Total: " + contactosLeidos.size());
                leyendo = true;
            }

            // Intentar scroll para cargar más
            if (leyendo) {
                intentarScrollAbajo(raiz);
            }
        } finally {
            raiz.recycle();
        }
    }

    /**
     * Recorre el árbol de nodos buscando pares nombre+teléfono.
     * Estrategia: busca nodos de texto y determina si son nombre o número.
     */
    private void leerNodos(AccessibilityNodeInfo nodo, String nombrePadre) {
        if (nodo == null) return;

        String texto = nodo.getText() != null ? nodo.getText().toString().trim() : "";
        String desc = nodo.getContentDescription() != null ?
                nodo.getContentDescription().toString().trim() : "";
        String valor = !texto.isEmpty() ? texto : desc;

        if (!valor.isEmpty()) {
            if (esTelefono(valor)) {
                // Es un número → el nombre debería estar cerca (nodo hermano o padre)
                String nombre = nombrePadre != null ? nombrePadre : "Contacto";
                agregarContacto(nombre, valor);
            } else if (!esCabeceraUiGenérica(valor)) {
                // Podría ser un nombre → guardar para asociar con el siguiente teléfono
                nombrePadre = valor;
            }
        }

        // Recorrer hijos
        for (int i = 0; i < nodo.getChildCount(); i++) {
            AccessibilityNodeInfo hijo = nodo.getChild(i);
            if (hijo != null) {
                leerNodos(hijo, nombrePadre);
                hijo.recycle();
            }
        }
    }

    // ══════════════════════════════════════
    //  AUTO-SCROLL
    // ══════════════════════════════════════

    private void intentarScrollAbajo(AccessibilityNodeInfo raiz) {
        if (scrollsRealizados >= MAX_SCROLLS) {
            finalizarImportacion();
            return;
        }

        // Buscar nodo scrollable
        AccessibilityNodeInfo scrollable = buscarScrollable(raiz);
        if (scrollable == null) {
            // No hay scroll → terminamos
            finalizarImportacion();
            return;
        }

        boolean puedeScrollear = scrollable.isScrollable() &&
                scrollable.getActionList() != null;

        if (!puedeScrollear) {
            finalizarImportacion();
            scrollable.recycle();
            return;
        }

        int contactosAntes = contactosLeidos.size();

        boolean scrollExitoso = scrollable.performAction(
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        scrollable.recycle();
        scrollsRealizados++;

        if (scrollExitoso) {
            // Esperar a que cargue y verificar si aparecieron contactos nuevos
            handler.postDelayed(() -> {
                if (contactosLeidos.size() == contactosAntes) {
                    // No hay nuevos → llegamos al final
                    Log.d(TAG, "Scroll sin nuevos contactos → fin");
                    finalizarImportacion();
                }
                // Si hay nuevos, leerPantallaActual() los capturará via event
            }, 600);
        } else {
            finalizarImportacion();
        }
    }

    private AccessibilityNodeInfo buscarScrollable(AccessibilityNodeInfo nodo) {
        if (nodo == null) return null;
        if (nodo.isScrollable()) return nodo;
        for (int i = 0; i < nodo.getChildCount(); i++) {
            AccessibilityNodeInfo hijo = nodo.getChild(i);
            if (hijo != null) {
                AccessibilityNodeInfo resultado = buscarScrollable(hijo);
                if (resultado != null) {
                    hijo.recycle();
                    return resultado;
                }
                hijo.recycle();
            }
        }
        return null;
    }

    // ══════════════════════════════════════
    //  FINALIZAR Y GUARDAR
    // ══════════════════════════════════════

    private void finalizarImportacion() {
        if (!leyendo || contactosLeidos.isEmpty()) return;
        leyendo = false;
        scrollsRealizados = 0;

        int agregados = locales.agregarVarios(contactosLeidos);
        contactosLeidos.clear();

        Log.d(TAG, "Importación finalizada. Guardados: " + agregados);

        // Broadcast → la app principal lo recibe y muestra notificación
        Intent broadcast = new Intent(ACTION_CONTACTOS_IMPORTADOS);
        broadcast.putExtra(EXTRA_CANTIDAD, agregados);
        sendBroadcast(broadcast);
    }

    // ══════════════════════════════════════
    //  UTILIDADES
    // ══════════════════════════════════════

    private void agregarContacto(String nombre, String numero) {
        // Evitar duplicados en la sesión actual
        for (BluetoothContactsManager.ContactoBt c : contactosLeidos) {
            if (c.numero.equals(numero)) return;
        }
        contactosLeidos.add(new BluetoothContactsManager.ContactoBt(nombre, numero));
    }

    private boolean esTelefono(String texto) {
        return PATRON_TELEFONO.matcher(texto.trim()).matches();
    }

    private boolean esCabeceraUiGenérica(String texto) {
        String lower = texto.toLowerCase();
        return lower.equals("book") || lower.equals("contactos") ||
                lower.equals("connect") || lower.equals("dial") ||
                lower.equals("log") || lower.equals("music") ||
                lower.equals("buscar") || lower.equals("search") ||
                lower.length() <= 1;
    }

    private boolean esPaqueteBt(String paquete) {
        for (String p : PAQUETES_BT) {
            if (paquete.startsWith(p)) return true;
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Servicio interrumpido");
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
