package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import codigo.cafe.alsvinlaunch.R;

/**
 * Gestiona el acceso a contactos del teléfono conectado por Bluetooth (PBAP).
 *
 * Estrategia de búsqueda (en orden):
 * 1. ContentProvider PBAP de HiveOS (com.android.bluetooth)
 * 2. ContactsContract estándar (si HiveOS sincroniza PBAP al sistema)
 * 3. Fallback: contactos rápidos manuales
 */
public class BluetoothContactsManager {

    private static final String TAG = "BtContacts";

    // URIs conocidos de proveedores PBAP en head units Android
    private static final String[] PBAP_URIS = {
            "content://com.android.bluetooth.pbap/phone",
            "content://com.android.bluetooth.pbap.phonebook/phone",
            "content://com.android.bluetooth.pbap/phonebook",
            "content://com.android.bluetooth.pbap.BluetoothPhonebookProvider/phonebook",
    };

    private final Activity actividad;

    public interface OnContactSelectedListener {
        void onContactSelected(String nombre, String numero);
    }

    public BluetoothContactsManager(Activity actividad) {
        this.actividad = actividad;
    }

    // ══════════════════════════════════════
    //  OBTENER CONTACTOS
    // ══════════════════════════════════════

    /**
     * Intenta obtener contactos del teléfono conectado por BT.
     * Prueba múltiples fuentes en orden de prioridad.
     */
    public List<ContactoBt> obtenerContactos() {
        List<ContactoBt> contactos;

        // 1. Intentar proveedor PBAP directo
        contactos = intentarPbapDirecto();
        if (!contactos.isEmpty()) {
            Log.d(TAG, "Contactos obtenidos vía PBAP directo: " + contactos.size());
            return contactos;
        }

        // 2. Intentar ContactsContract del sistema
        contactos = intentarContactsContract();
        if (!contactos.isEmpty()) {
            Log.d(TAG, "Contactos obtenidos vía ContactsContract: " + contactos.size());
            return contactos;
        }

        // 3. Intentar cuenta Bluetooth en ContactsContract
        contactos = intentarCuentaBluetooth();
        if (!contactos.isEmpty()) {
            Log.d(TAG, "Contactos obtenidos vía cuenta BT: " + contactos.size());
            return contactos;
        }

        Log.d(TAG, "No se encontraron contactos Bluetooth");
        return contactos;
    }

    // ── Estrategia 1: PBAP ContentProvider directo ──

    private List<ContactoBt> intentarPbapDirecto() {
        List<ContactoBt> contactos = new ArrayList<>();
        ContentResolver cr = actividad.getContentResolver();

        for (String uriStr : PBAP_URIS) {
            try {
                Uri uri = Uri.parse(uriStr);
                Cursor cursor = cr.query(uri, null, null, null, null);
                if (cursor != null) {
                    Log.d(TAG, "PBAP URI encontrado: " + uriStr +
                            " columnas: " + java.util.Arrays.toString(cursor.getColumnNames()));
                    while (cursor.moveToNext()) {
                        String nombre = extraerColumna(cursor, "name", "display_name",
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                        String numero = extraerColumna(cursor, "number", "phone",
                                ContactsContract.CommonDataKinds.Phone.NUMBER);
                        if (nombre != null && numero != null) {
                            contactos.add(new ContactoBt(nombre, numero));
                        }
                    }
                    cursor.close();
                    if (!contactos.isEmpty()) return contactos;
                }
            } catch (Exception e) {
                Log.d(TAG, "PBAP URI no disponible: " + uriStr + " → " + e.getMessage());
            }
        }
        return contactos;
    }

    // ── Estrategia 2: ContactsContract estándar ──

    private List<ContactoBt> intentarContactsContract() {
        List<ContactoBt> contactos = new ArrayList<>();
        try {
            ContentResolver cr = actividad.getContentResolver();
            Cursor cursor = cr.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String nombre = cursor.getString(0);
                    String numero = cursor.getString(1);
                    if (nombre != null && numero != null && !numero.isEmpty()) {
                        contactos.add(new ContactoBt(nombre, numero.trim()));
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error ContactsContract: " + e.getMessage());
        }
        return contactos;
    }

    // ── Estrategia 3: Cuenta tipo Bluetooth en ContactsContract ──

    private List<ContactoBt> intentarCuentaBluetooth() {
        List<ContactoBt> contactos = new ArrayList<>();
        try {
            ContentResolver cr = actividad.getContentResolver();

            // Buscar contactos cuya cuenta sea de tipo bluetooth/pbap
            String selection = ContactsContract.RawContacts.ACCOUNT_TYPE + " LIKE ?";
            String[] args = {"%bluetooth%"};

            Cursor rawCursor = cr.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    new String[]{ContactsContract.RawContacts._ID},
                    selection, args, null
            );

            if (rawCursor != null) {
                List<String> rawIds = new ArrayList<>();
                while (rawCursor.moveToNext()) {
                    rawIds.add(rawCursor.getString(0));
                }
                rawCursor.close();

                // Para cada raw contact, obtener nombre y teléfono
                for (String rawId : rawIds) {
                    Cursor dataCursor = cr.query(
                            ContactsContract.Data.CONTENT_URI,
                            new String[]{
                                    ContactsContract.Data.MIMETYPE,
                                    ContactsContract.Data.DATA1
                            },
                            ContactsContract.Data.RAW_CONTACT_ID + " = ?",
                            new String[]{rawId},
                            null
                    );

                    if (dataCursor != null) {
                        String nombre = null;
                        String numero = null;
                        while (dataCursor.moveToNext()) {
                            String mime = dataCursor.getString(0);
                            String valor = dataCursor.getString(1);
                            if (ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                                    .equals(mime)) {
                                nombre = valor;
                            } else if (ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                                    .equals(mime)) {
                                numero = valor;
                            }
                        }
                        dataCursor.close();
                        if (nombre != null && numero != null) {
                            contactos.add(new ContactoBt(nombre, numero));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cuenta BT: " + e.getMessage());
        }
        return contactos;
    }

    // ══════════════════════════════════════
    //  DIAGNÓSTICO (para depuración)
    // ══════════════════════════════════════

    /**
     * Genera un reporte de diagnóstico mostrando qué fuentes están disponibles.
     * Útil para depurar en el head unit real.
     */
    public String generarDiagnostico() {
        StringBuilder sb = new StringBuilder();
        ContentResolver cr = actividad.getContentResolver();

        sb.append("=== DIAGNÓSTICO CONTACTOS BT ===\n\n");

        // Verificar dispositivo BT conectado
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt != null && bt.isEnabled()) {
                sb.append("✓ Bluetooth activo\n");
                for (BluetoothDevice dev : bt.getBondedDevices()) {
                    try {
                        Method m = dev.getClass().getMethod("isConnected");
                        boolean conectado = (boolean) m.invoke(dev);
                        sb.append(conectado ? "  ✓ " : "  ○ ")
                                .append(dev.getName())
                                .append(" (").append(dev.getAddress()).append(")\n");
                    } catch (Exception e) {
                        sb.append("  ? ").append(dev.getName()).append("\n");
                    }
                }
            } else {
                sb.append("✗ Bluetooth inactivo\n");
            }
        } catch (Exception e) {
            sb.append("✗ Error BT: ").append(e.getMessage()).append("\n");
        }

        sb.append("\n");

        // Probar URIs PBAP
        sb.append("── Proveedores PBAP ──\n");
        for (String uriStr : PBAP_URIS) {
            try {
                Cursor c = cr.query(Uri.parse(uriStr), null, null, null, null);
                if (c != null) {
                    sb.append("✓ ").append(uriStr)
                            .append(" → ").append(c.getCount()).append(" registros")
                            .append(" [").append(String.join(", ", c.getColumnNames())).append("]\n");
                    c.close();
                } else {
                    sb.append("○ ").append(uriStr).append(" → null\n");
                }
            } catch (Exception e) {
                sb.append("✗ ").append(uriStr).append(" → ").append(e.getMessage()).append("\n");
            }
        }

        sb.append("\n");

        // Probar ContactsContract
        sb.append("── ContactsContract ──\n");
        try {
            Cursor c = cr.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null, null, null, null);
            if (c != null) {
                sb.append("✓ Contactos del sistema: ").append(c.getCount()).append("\n");
                c.close();
            }
        } catch (Exception e) {
            sb.append("✗ Error: ").append(e.getMessage()).append("\n");
        }

        // Verificar cuentas tipo bluetooth
        sb.append("\n── Cuentas Bluetooth ──\n");
        try {
            Cursor c = cr.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    new String[]{ContactsContract.RawContacts.ACCOUNT_TYPE,
                            ContactsContract.RawContacts.ACCOUNT_NAME},
                    ContactsContract.RawContacts.ACCOUNT_TYPE + " LIKE ?",
                    new String[]{"%bluetooth%"},
                    null);
            if (c != null) {
                sb.append("Cuentas BT: ").append(c.getCount()).append("\n");
                while (c.moveToNext()) {
                    sb.append("  → ").append(c.getString(0))
                            .append(" / ").append(c.getString(1)).append("\n");
                }
                c.close();
            }
        } catch (Exception e) {
            sb.append("✗ Error: ").append(e.getMessage()).append("\n");
        }

        // Listar TODAS las cuentas de contactos (para descubrir)
        sb.append("\n── Todas las cuentas ──\n");
        try {
            Cursor c = cr.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    new String[]{
                            "DISTINCT " + ContactsContract.RawContacts.ACCOUNT_TYPE,
                            ContactsContract.RawContacts.ACCOUNT_NAME
                    },
                    null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    sb.append("  ").append(c.getString(0))
                            .append(" / ").append(c.getString(1)).append("\n");
                }
                c.close();
            }
        } catch (Exception e) {
            sb.append("✗ ").append(e.getMessage()).append("\n");
        }

        return sb.toString();
    }

    // ══════════════════════════════════════
    //  DIÁLOGO SELECTOR DE CONTACTOS BT
    // ══════════════════════════════════════

    /**
     * Muestra un diálogo con los contactos del teléfono BT
     * para que el usuario seleccione uno.
     */
    public void mostrarSelectorContactos(final OnContactSelectedListener listener) {
        final List<ContactoBt> contactos = obtenerContactos();

        if (contactos.isEmpty()) {
            // Mostrar diagnóstico si no hay contactos
            mostrarDialogoDiagnostico();
            return;
        }

        // Construir diálogo con lista de contactos
        LinearLayout raiz = new LinearLayout(actividad);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundResource(R.drawable.dialogo_bg);
        raiz.setPadding(40, 30, 40, 30);

        // Título
        TextView titulo = new TextView(actividad);
        titulo.setText("Contactos del Teléfono (" + contactos.size() + ")");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(18);
        titulo.setGravity(Gravity.CENTER);
        titulo.setPadding(0, 0, 0, 16);
        raiz.addView(titulo);

        // Barra de búsqueda
        final android.widget.EditText campoBusqueda = new android.widget.EditText(actividad);
        campoBusqueda.setHint("Buscar contacto...");
        campoBusqueda.setTextColor(Color.WHITE);
        campoBusqueda.setHintTextColor(Color.parseColor("#555555"));
        campoBusqueda.setTextSize(14);
        campoBusqueda.setSingleLine(true);
        campoBusqueda.setPadding(16, 12, 16, 12);
        campoBusqueda.setBackgroundResource(R.drawable.system_item_bg);
        raiz.addView(campoBusqueda);

        // ScrollView con contactos
        ScrollView scroll = new ScrollView(actividad);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 600));

        final LinearLayout listaContactos = new LinearLayout(actividad);
        listaContactos.setOrientation(LinearLayout.VERTICAL);
        listaContactos.setPadding(0, 8, 0, 0);
        scroll.addView(listaContactos);
        raiz.addView(scroll);

        // Botón cerrar
        TextView botonCerrar = new TextView(actividad);
        botonCerrar.setText("Cerrar");
        botonCerrar.setTextColor(Color.parseColor("#B0B0B0"));
        botonCerrar.setTextSize(14);
        botonCerrar.setGravity(Gravity.CENTER);
        botonCerrar.setPadding(0, 16, 0, 4);
        raiz.addView(botonCerrar);

        final AlertDialog dialogo = new AlertDialog.Builder(actividad)
                .setView(raiz).create();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Poblar lista
        poblarListaContactos(listaContactos, contactos, listener, dialogo);

        // Filtro de búsqueda
        campoBusqueda.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String filtro = s.toString().toLowerCase().trim();
                List<ContactoBt> filtrados = new ArrayList<>();
                for (ContactoBt c : contactos) {
                    if (c.nombre.toLowerCase().contains(filtro) ||
                            c.numero.contains(filtro)) {
                        filtrados.add(c);
                    }
                }
                poblarListaContactos(listaContactos, filtrados, listener, dialogo);
            }
        });

        botonCerrar.setOnClickListener(v -> dialogo.dismiss());
        dialogo.show();
    }

    private void poblarListaContactos(LinearLayout contenedor, List<ContactoBt> contactos,
                                      OnContactSelectedListener listener, AlertDialog dialogo) {
        contenedor.removeAllViews();
        for (final ContactoBt contacto : contactos) {
            LinearLayout fila = new LinearLayout(actividad);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setGravity(Gravity.CENTER_VERTICAL);
            fila.setPadding(12, 14, 12, 14);
            fila.setBackgroundResource(R.drawable.system_item_bg);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = 4;
            fila.setLayoutParams(params);

            // Icono contacto
            ImageView icono = new ImageView(actividad);
            icono.setImageResource(R.drawable.ic_llamar);
            LinearLayout.LayoutParams pi = new LinearLayout.LayoutParams(32, 32);
            pi.rightMargin = 12;
            icono.setLayoutParams(pi);

            // Nombre y número
            LinearLayout info = new LinearLayout(actividad);
            info.setOrientation(LinearLayout.VERTICAL);

            TextView textoNombre = new TextView(actividad);
            textoNombre.setText(contacto.nombre);
            textoNombre.setTextColor(Color.WHITE);
            textoNombre.setTextSize(14);

            TextView textoNumero = new TextView(actividad);
            textoNumero.setText(contacto.numero);
            textoNumero.setTextColor(Color.parseColor("#B0B0B0"));
            textoNumero.setTextSize(11);

            info.addView(textoNombre);
            info.addView(textoNumero);

            fila.addView(icono);
            fila.addView(info);

            fila.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onContactSelected(contacto.nombre, contacto.numero);
                }
                dialogo.dismiss();
            });

            contenedor.addView(fila);
        }
    }

    // ── Diálogo de diagnóstico ──

    private void mostrarDialogoDiagnostico() {
        String diagnostico = generarDiagnostico();

        LinearLayout raiz = new LinearLayout(actividad);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundResource(R.drawable.dialogo_bg);
        raiz.setPadding(40, 30, 40, 30);

        TextView titulo = new TextView(actividad);
        titulo.setText("Sin contactos Bluetooth");
        titulo.setTextColor(Color.parseColor("#FF9800"));
        titulo.setTextSize(16);
        titulo.setGravity(Gravity.CENTER);
        titulo.setPadding(0, 0, 0, 12);
        raiz.addView(titulo);

        TextView mensaje = new TextView(actividad);
        mensaje.setText("No se encontraron contactos del teléfono.\n\n" +
                "Asegúrate de:\n" +
                "• El teléfono está conectado por BT\n" +
                "• Permitiste compartir contactos\n" +
                "• PBAP está habilitado en la conexión");
        mensaje.setTextColor(Color.parseColor("#B0B0B0"));
        mensaje.setTextSize(13);
        raiz.addView(mensaje);

        // Mostrar diagnóstico técnico (colapsable)
        TextView botonTecnico = new TextView(actividad);
        botonTecnico.setText("▶ Ver diagnóstico técnico");
        botonTecnico.setTextColor(Color.parseColor("#2979FF"));
        botonTecnico.setTextSize(12);
        botonTecnico.setPadding(0, 16, 0, 0);
        raiz.addView(botonTecnico);

        ScrollView scrollDiag = new ScrollView(actividad);
        scrollDiag.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 300));
        scrollDiag.setVisibility(View.GONE);

        TextView textoDiag = new TextView(actividad);
        textoDiag.setText(diagnostico);
        textoDiag.setTextColor(Color.parseColor("#888888"));
        textoDiag.setTextSize(10);
        textoDiag.setTypeface(android.graphics.Typeface.MONOSPACE);
        scrollDiag.addView(textoDiag);
        raiz.addView(scrollDiag);

        botonTecnico.setOnClickListener(v -> {
            if (scrollDiag.getVisibility() == View.GONE) {
                scrollDiag.setVisibility(View.VISIBLE);
                botonTecnico.setText("▼ Ocultar diagnóstico técnico");
            } else {
                scrollDiag.setVisibility(View.GONE);
                botonTecnico.setText("▶ Ver diagnóstico técnico");
            }
        });

        TextView botonCerrar = new TextView(actividad);
        botonCerrar.setText("Cerrar");
        botonCerrar.setTextColor(Color.parseColor("#B0B0B0"));
        botonCerrar.setTextSize(14);
        botonCerrar.setGravity(Gravity.CENTER);
        botonCerrar.setPadding(0, 20, 0, 4);
        raiz.addView(botonCerrar);

        AlertDialog dialogo = new AlertDialog.Builder(actividad)
                .setView(raiz).create();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        botonCerrar.setOnClickListener(v -> dialogo.dismiss());
        dialogo.show();
    }

    // ══════════════════════════════════════
    //  MODELO
    // ══════════════════════════════════════

    public static class ContactoBt {
        public final String nombre;
        public final String numero;

        public ContactoBt(String nombre, String numero) {
            this.nombre = nombre;
            this.numero = numero;
        }
    }

    // ══════════════════════════════════════
    //  UTILIDADES
    // ══════════════════════════════════════

    private String extraerColumna(Cursor cursor, String... posiblesNombres) {
        for (String nombre : posiblesNombres) {
            int idx = cursor.getColumnIndex(nombre);
            if (idx >= 0) {
                return cursor.getString(idx);
            }
        }
        return null;
    }
}