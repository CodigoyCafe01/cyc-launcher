package codigo.cafe.alsvinlaunch.managers;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.services.ImportadorBtHelper;

/**
 * Gestiona el acceso a contactos del teléfono conectado por Bluetooth (PBAP).
 *
 * IMPORTANTE - Hallazgo del diagnóstico:
 *   content://contacts/bt_phones → SecurityException (URI EXISTE pero requiere permiso)
 *   Todos los demás → null (no existen en este sistema)
 *
 * Estrategia de búsqueda:
 * 1. content://contacts/bt_phones  (URI confirmado que existe en este head unit)
 * 2. Resto de URIs PBAP conocidos
 * 3. ContactsContract estándar (si HiveOS sincroniza PBAP)
 * 4. Cuenta Bluetooth en ContactsContract
 * 5. App de teléfono nativa
 */
public class BluetoothContactsManager {

    private static final String TAG = "BtContacts";

    // ── URI confirmado que EXISTE en el head unit (diagnóstico mostró SecurityException, no null) ──
    private static final String URI_CONFIRMADO = "content://contacts/bt_phones";

    // URIs alternativos a probar
    private static final String[] PBAP_URIS = {
            // El confirmado va primero
            "content://contacts/bt_phones",
            // Estándar Android
            "content://com.android.bluetooth.pbap/phone",
            "content://com.android.bluetooth.pbap.phonebook/phone",
            "content://com.android.bluetooth.pbap/phonebook",
            "content://com.android.bluetooth.pbap.BluetoothPhonebookProvider/phonebook",
            // MTK
            "content://com.mediatek.bluetooth/pbap",
            "content://com.mediatek.bluetooth.pbap/phone",
            "content://btcontacts/contacts",
            // HiveOS / Allwinner / Rockchip
            "content://com.hive.bluetooth.pbap/phone",
            "content://com.hive.contacts/phones",
            "content://bluetooth_phonebook/contacts",
            "content://com.android.car.bluetooth/contacts",
            // Genéricos
            "content://phonebook/contacts",
            "content://com.android.bluetooth/contacts"
    };

    // Columnas posibles que pueden contener nombre/número
    private static final String[] COLS_NOMBRE = {
            "name", "display_name", "DISPLAY_NAME", "contact_name",
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
    };
    private static final String[] COLS_NUMERO = {
            "number", "phone", "NUMBER", "PHONE", "tel",
            ContactsContract.CommonDataKinds.Phone.NUMBER
    };

    private final Activity actividad;
    private final ContactosLocalesManager locales;

    public interface OnContactSelectedListener {
        void onContactSelected(String nombre, String numero);
    }

    public BluetoothContactsManager(Activity actividad) {
        this.actividad = actividad;
        this.locales = new ContactosLocalesManager(actividad);
    }

    // ══════════════════════════════════════
    //  OBTENER CONTACTOS BT (sin persistir)
    // ══════════════════════════════════════

    public List<ContactoBt> obtenerContactos() {
        List<ContactoBt> contactos;

        // 1. URI confirmado - intentar con múltiples proyecciones
        contactos = intentarUriConfirmado();
        if (!contactos.isEmpty()) {
            Log.d(TAG, "✓ Contactos desde URI confirmado: " + contactos.size());
            return contactos;
        }

        // 2. Resto de proveedores PBAP
        contactos = intentarPbapDirecto();
        if (!contactos.isEmpty()) {
            Log.d(TAG, "✓ Contactos vía PBAP: " + contactos.size());
            return contactos;
        }

        // 3. ContactsContract
        contactos = intentarContactsContract();
        if (!contactos.isEmpty()) {
            Log.d(TAG, "✓ Contactos vía ContactsContract: " + contactos.size());
            return contactos;
        }

        // 4. Cuenta BT en ContactsContract
        contactos = intentarCuentaBluetooth();
        if (!contactos.isEmpty()) {
            Log.d(TAG, "✓ Contactos vía cuenta BT: " + contactos.size());
            return contactos;
        }

        // 5. App nativa
        contactos = intentarAppTelefonoNativa();
        if (!contactos.isEmpty()) return contactos;

        Log.w(TAG, "✗ No se encontraron contactos BT");
        return contactos;
    }

    // ── Estrategia 1: URI confirmado (content://contacts/bt_phones) ──

    private List<ContactoBt> intentarUriConfirmado() {
        List<ContactoBt> contactos = new ArrayList<>();
        ContentResolver cr = actividad.getContentResolver();

        // Proyecciones a intentar (del más específico al más genérico)
        String[][] proyecciones = {
                { "name", "number" },
                { "display_name", "phone" },
                { "display_name", "number" },
                { "DISPLAY_NAME", "NUMBER" },
                null  // todas las columnas
        };

        for (String[] proj : proyecciones) {
            try {
                Uri uri = Uri.parse(URI_CONFIRMADO);
                Cursor cursor = cr.query(uri, proj, null, null, null);
                if (cursor != null) {
                    Log.d(TAG, "URI confirmado OK con proj=" + java.util.Arrays.toString(proj)
                            + " cols=" + java.util.Arrays.toString(cursor.getColumnNames())
                            + " rows=" + cursor.getCount());
                    while (cursor.moveToNext()) {
                        String nombre = extraerColumna(cursor, COLS_NOMBRE);
                        String numero = extraerColumna(cursor, COLS_NUMERO);
                        if (numero != null && !numero.isEmpty()) {
                            contactos.add(new ContactoBt(
                                    nombre != null ? nombre : numero,
                                    numero.trim()
                            ));
                        }
                    }
                    cursor.close();
                    if (!contactos.isEmpty()) return contactos;
                }
            } catch (SecurityException se) {
                // Este URI existe pero necesita permiso - loguear info útil
                Log.w(TAG, "SecurityException en " + URI_CONFIRMADO + ": " + se.getMessage());
                // No tiene caso seguir intentando con distintas proyecciones
                break;
            } catch (Exception e) {
                Log.d(TAG, "Error en URI confirmado (proj=" + java.util.Arrays.toString(proj)
                        + "): " + e.getMessage());
            }
        }
        return contactos;
    }

    // ── Estrategia 2: PBAP ContentProvider directo ──

    private List<ContactoBt> intentarPbapDirecto() {
        List<ContactoBt> contactos = new ArrayList<>();
        ContentResolver cr = actividad.getContentResolver();

        for (String uriStr : PBAP_URIS) {
            if (uriStr.equals(URI_CONFIRMADO)) continue; // ya se intentó
            try {
                Uri uri = Uri.parse(uriStr);
                Cursor cursor = cr.query(uri, null, null, null, null);
                if (cursor != null) {
                    Log.d(TAG, "PBAP URI encontrado: " + uriStr
                            + " cols=" + java.util.Arrays.toString(cursor.getColumnNames()));
                    while (cursor.moveToNext()) {
                        String nombre = extraerColumna(cursor, COLS_NOMBRE);
                        String numero = extraerColumna(cursor, COLS_NUMERO);
                        if (numero != null && !numero.isEmpty()) {
                            contactos.add(new ContactoBt(
                                    nombre != null ? nombre : numero,
                                    numero.trim()
                            ));
                        }
                    }
                    cursor.close();
                    if (!contactos.isEmpty()) return contactos;
                }
            } catch (Exception e) {
                Log.d(TAG, "PBAP URI no disponible: " + uriStr);
            }
        }
        return contactos;
    }

    // ── Estrategia 3: ContactsContract estándar ──

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
                    if (numero != null && !numero.isEmpty()) {
                        contactos.add(new ContactoBt(
                                nombre != null ? nombre : numero,
                                numero.trim()
                        ));
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error ContactsContract: " + e.getMessage());
        }
        return contactos;
    }

    // ── Estrategia 4: Cuenta tipo Bluetooth ──

    private List<ContactoBt> intentarCuentaBluetooth() {
        List<ContactoBt> contactos = new ArrayList<>();
        try {
            ContentResolver cr = actividad.getContentResolver();
            Cursor rawCursor = cr.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    new String[]{ContactsContract.RawContacts._ID},
                    ContactsContract.RawContacts.ACCOUNT_TYPE + " LIKE ?",
                    new String[]{"%bluetooth%"},
                    null
            );
            if (rawCursor != null) {
                List<String> rawIds = new ArrayList<>();
                while (rawCursor.moveToNext()) rawIds.add(rawCursor.getString(0));
                rawCursor.close();

                for (String rawId : rawIds) {
                    Cursor dc = cr.query(
                            ContactsContract.Data.CONTENT_URI,
                            new String[]{ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1},
                            ContactsContract.Data.RAW_CONTACT_ID + " = ?",
                            new String[]{rawId}, null
                    );
                    if (dc != null) {
                        String nombre = null, numero = null;
                        while (dc.moveToNext()) {
                            String mime = dc.getString(0);
                            String val = dc.getString(1);
                            if (ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(mime))
                                nombre = val;
                            else if (ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mime))
                                numero = val;
                        }
                        dc.close();
                        if (numero != null) contactos.add(new ContactoBt(
                                nombre != null ? nombre : numero, numero));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cuenta BT: " + e.getMessage());
        }
        return contactos;
    }

    // ── Estrategia 5: App de teléfono nativa ──

    private List<ContactoBt> intentarAppTelefonoNativa() {
        List<ContactoBt> contactos = new ArrayList<>();
        String[] paquetes = {"com.hive.phone", "com.android.phone", "com.mediatek.phone",
                "com.android.incallui", "com.hive.incallui"};
        String[] rutas = {"/btcontacts", "/bluetooth/contacts", "/phone/contacts", "/pbap/contacts"};
        ContentResolver cr = actividad.getContentResolver();

        for (String pkg : paquetes) {
            for (String ruta : rutas) {
                try {
                    Cursor cursor = cr.query(Uri.parse("content://" + pkg + ruta), null, null, null, null);
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            String nombre = extraerColumna(cursor, COLS_NOMBRE);
                            String numero = extraerColumna(cursor, COLS_NUMERO);
                            if (numero != null && !numero.isEmpty())
                                contactos.add(new ContactoBt(nombre != null ? nombre : numero, numero.trim()));
                        }
                        cursor.close();
                        if (!contactos.isEmpty()) return contactos;
                    }
                } catch (Exception ignored) {}
            }
        }
        return contactos;
    }

    // ══════════════════════════════════════
    //  DIÁLOGO - IMPORTAR Y GUARDAR
    // ══════════════════════════════════════

    /**
     * Muestra selector de contactos BT con opción de importar todos o seleccionar.
     * Los contactos importados se guardan localmente (persisten aunque BT se desconecte).
     */
    public void mostrarImportador() {
        actividad.runOnUiThread(() -> {
            // Loading
            Toast.makeText(actividad, "Buscando contactos BT...", Toast.LENGTH_SHORT).show();
        });

        new Thread(() -> {
            final List<ContactoBt> contactosBt = obtenerContactos();
            actividad.runOnUiThread(() -> {
                if (contactosBt.isEmpty()) {
                    mostrarDialogoDiagnostico();
                    return;
                }
                mostrarDialogoImportacion(contactosBt);
            });
        }).start();
    }

    private void mostrarDialogoImportacion(List<ContactoBt> contactosBt) {
        // Raíz del diálogo
        LinearLayout raiz = new LinearLayout(actividad);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundResource(R.drawable.dialogo_bg);
        raiz.setPadding(40, 30, 40, 30);

        // Título
        TextView titulo = new TextView(actividad);
        titulo.setText("Importar contactos BT (" + contactosBt.size() + ")");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(17);
        titulo.setGravity(Gravity.CENTER);
        titulo.setPadding(0, 0, 0, 8);
        raiz.addView(titulo);

        // Subtítulo
        TextView sub = new TextView(actividad);
        sub.setText("Selecciona los que quieres guardar en la app");
        sub.setTextColor(Color.parseColor("#B0B0B0"));
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, 12);
        raiz.addView(sub);

        // Barra de búsqueda
        android.widget.EditText busqueda = new android.widget.EditText(actividad);
        busqueda.setHint("Buscar...");
        busqueda.setTextColor(Color.WHITE);
        busqueda.setHintTextColor(Color.parseColor("#555555"));
        busqueda.setTextSize(13);
        busqueda.setSingleLine(true);
        busqueda.setPadding(16, 10, 16, 10);
        busqueda.setBackgroundResource(R.drawable.system_item_bg);
        raiz.addView(busqueda);

        // Botones Seleccionar Todos / Ninguno
        LinearLayout filaBotones = new LinearLayout(actividad);
        filaBotones.setOrientation(LinearLayout.HORIZONTAL);
        filaBotones.setPadding(0, 8, 0, 4);

        TextView btnTodos = crearBotonTexto("Seleccionar todos", "#2979FF");
        TextView btnNinguno = crearBotonTexto("Ninguno", "#888888");
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        btnTodos.setLayoutParams(lpBtn);
        btnNinguno.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        filaBotones.addView(btnTodos);
        filaBotones.addView(btnNinguno);
        raiz.addView(filaBotones);

        // Lista con checkboxes
        ScrollView scroll = new ScrollView(actividad);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 500));

        LinearLayout lista = new LinearLayout(actividad);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(0, 4, 0, 0);
        scroll.addView(lista);
        raiz.addView(scroll);

        // Estado de selección
        final boolean[] seleccionados = new boolean[contactosBt.size()];
        for (int i = 0; i < seleccionados.length; i++) seleccionados[i] = true; // todos por defecto

        // Poblar lista
        poblarListaImportacion(lista, contactosBt, seleccionados, "");

        // Búsqueda dinámica
        busqueda.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                poblarListaImportacion(lista, contactosBt, seleccionados,
                        s.toString().toLowerCase().trim());
            }
        });

        btnTodos.setOnClickListener(v -> {
            for (int i = 0; i < seleccionados.length; i++) seleccionados[i] = true;
            poblarListaImportacion(lista, contactosBt, seleccionados,
                    busqueda.getText().toString().toLowerCase().trim());
        });

        btnNinguno.setOnClickListener(v -> {
            for (int i = 0; i < seleccionados.length; i++) seleccionados[i] = false;
            poblarListaImportacion(lista, contactosBt, seleccionados,
                    busqueda.getText().toString().toLowerCase().trim());
        });

        // Botones finales
        LinearLayout filaFinal = new LinearLayout(actividad);
        filaFinal.setOrientation(LinearLayout.HORIZONTAL);
        filaFinal.setGravity(Gravity.CENTER);
        filaFinal.setPadding(0, 20, 0, 4);

        TextView btnCancelar = crearBotonTexto("Cancelar", "#888888");
        TextView btnImportar = crearBotonTexto("Importar", "#FFFFFF");
        btnImportar.setBackgroundResource(R.drawable.boton_agregar_bg);
        LinearLayout.LayoutParams lpImp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpImp.leftMargin = 20;
        btnImportar.setPadding(40, 14, 40, 14);
        btnImportar.setLayoutParams(lpImp);

        filaFinal.addView(btnCancelar);
        filaFinal.addView(btnImportar);
        raiz.addView(filaFinal);

        AlertDialog dialogo = new AlertDialog.Builder(actividad)
                .setView(raiz).create();
        if (dialogo.getWindow() != null)
            dialogo.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnCancelar.setOnClickListener(v -> dialogo.dismiss());

        btnImportar.setOnClickListener(v ->
                ImportadorBtHelper.iniciar(actividad)
        );

        dialogo.show();
    }

    private void poblarListaImportacion(LinearLayout contenedor,
                                        List<ContactoBt> todos,
                                        boolean[] seleccionados,
                                        String filtro) {
        contenedor.removeAllViews();
        for (int i = 0; i < todos.size(); i++) {
            final int idx = i;
            ContactoBt c = todos.get(i);

            // Filtro de búsqueda
            if (!filtro.isEmpty() &&
                    !c.nombre.toLowerCase().contains(filtro) &&
                    !c.numero.contains(filtro)) continue;

            LinearLayout fila = new LinearLayout(actividad);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setGravity(Gravity.CENTER_VERTICAL);
            fila.setPadding(12, 12, 12, 12);
            fila.setBackgroundResource(R.drawable.system_item_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 3;
            fila.setLayoutParams(lp);

            CheckBox cb = new CheckBox(actividad);
            cb.setChecked(seleccionados[idx]);
            cb.setPadding(0, 0, 12, 0);
            cb.setOnCheckedChangeListener((btn, checked) -> seleccionados[idx] = checked);

            LinearLayout info = new LinearLayout(actividad);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView nombre = new TextView(actividad);
            nombre.setText(c.nombre);
            nombre.setTextColor(Color.WHITE);
            nombre.setTextSize(14);

            TextView numero = new TextView(actividad);
            numero.setText(c.numero);
            numero.setTextColor(Color.parseColor("#B0B0B0"));
            numero.setTextSize(11);

            info.addView(nombre);
            info.addView(numero);

            fila.addView(cb);
            fila.addView(info);

            fila.setOnClickListener(v -> {
                seleccionados[idx] = !seleccionados[idx];
                cb.setChecked(seleccionados[idx]);
            });

            contenedor.addView(fila);
        }
    }

    // ══════════════════════════════════════
    //  SELECTOR DE CONTACTOS GUARDADOS
    //  (usa los contactos locales persistidos)
    // ══════════════════════════════════════

    /**
     * Muestra los contactos guardados localmente.
     * Incluye botón para importar más desde BT.
     */
    public void mostrarSelectorContactos(final OnContactSelectedListener listener) {
        List<ContactoBt> guardados = locales.obtenerTodos();

        LinearLayout raiz = new LinearLayout(actividad);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundResource(R.drawable.dialogo_bg);
        raiz.setPadding(40, 30, 40, 30);

        // Título
        TextView titulo = new TextView(actividad);
        titulo.setText("Contactos (" + guardados.size() + ")");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(18);
        titulo.setGravity(Gravity.CENTER);
        titulo.setPadding(0, 0, 0, 4);
        raiz.addView(titulo);

        // Botón importar
        TextView btnImportar = new TextView(actividad);
        btnImportar.setText("📱 Importar desde teléfono BT");
        btnImportar.setTextColor(Color.parseColor("#2979FF"));
        btnImportar.setTextSize(12);
        btnImportar.setGravity(Gravity.CENTER);
        btnImportar.setPadding(0, 6, 0, 10);
        raiz.addView(btnImportar);

        // Búsqueda
        android.widget.EditText busqueda = new android.widget.EditText(actividad);
        busqueda.setHint("Buscar...");
        busqueda.setTextColor(Color.WHITE);
        busqueda.setHintTextColor(Color.parseColor("#555555"));
        busqueda.setTextSize(13);
        busqueda.setSingleLine(true);
        busqueda.setPadding(16, 10, 16, 10);
        busqueda.setBackgroundResource(R.drawable.system_item_bg);
        raiz.addView(busqueda);

        ScrollView scroll = new ScrollView(actividad);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 550));

        final LinearLayout lista = new LinearLayout(actividad);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(0, 8, 0, 0);
        scroll.addView(lista);
        raiz.addView(scroll);

        TextView btnCerrar = crearBotonTexto("Cerrar", "#B0B0B0");
        btnCerrar.setGravity(Gravity.CENTER);
        btnCerrar.setPadding(0, 16, 0, 4);
        raiz.addView(btnCerrar);

        final AlertDialog dialogo = new AlertDialog.Builder(actividad)
                .setView(raiz).create();
        if (dialogo.getWindow() != null)
            dialogo.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Poblar con guardados
        poblarListaContactos(lista, guardados, listener, dialogo);

        // Si está vacío, mostrar mensaje
        if (guardados.isEmpty()) {
            TextView empty = new TextView(actividad);
            empty.setText("No hay contactos guardados.\nUsa '📱 Importar' para traerlos del teléfono.");
            empty.setTextColor(Color.parseColor("#888888"));
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 20, 0, 20);
            lista.addView(empty);
        }

        busqueda.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                String f = s.toString().toLowerCase().trim();
                List<ContactoBt> filtrados = new ArrayList<>();
                for (ContactoBt c2 : locales.obtenerTodos()) {
                    if (c2.nombre.toLowerCase().contains(f) || c2.numero.contains(f))
                        filtrados.add(c2);
                }
                poblarListaContactos(lista, filtrados, listener, dialogo);
            }
        });

        btnImportar.setOnClickListener(v ->
                ImportadorBtHelper.iniciar(actividad)
        );

        btnCerrar.setOnClickListener(v -> dialogo.dismiss());
        dialogo.show();
    }

    private void poblarListaContactos(LinearLayout contenedor, List<ContactoBt> contactos,
                                      OnContactSelectedListener listener, AlertDialog dialogo) {
        contenedor.removeAllViews();
        for (final ContactoBt c : contactos) {
            LinearLayout fila = new LinearLayout(actividad);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setGravity(Gravity.CENTER_VERTICAL);
            fila.setPadding(12, 14, 12, 14);
            fila.setBackgroundResource(R.drawable.system_item_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            fila.setLayoutParams(lp);

            ImageView icono = new ImageView(actividad);
            icono.setImageResource(R.drawable.ic_llamar);
            LinearLayout.LayoutParams pi = new LinearLayout.LayoutParams(32, 32);
            pi.rightMargin = 12;
            icono.setLayoutParams(pi);

            LinearLayout info = new LinearLayout(actividad);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvNombre = new TextView(actividad);
            tvNombre.setText(c.nombre);
            tvNombre.setTextColor(Color.WHITE);
            tvNombre.setTextSize(14);

            TextView tvNum = new TextView(actividad);
            tvNum.setText(c.numero);
            tvNum.setTextColor(Color.parseColor("#B0B0B0"));
            tvNum.setTextSize(11);

            info.addView(tvNombre);
            info.addView(tvNum);
            fila.addView(icono);
            fila.addView(info);

            fila.setOnClickListener(v -> {
                if (listener != null) listener.onContactSelected(c.nombre, c.numero);
                dialogo.dismiss();
            });

            contenedor.addView(fila);
        }
    }

    // ══════════════════════════════════════
    //  DIAGNÓSTICO
    // ══════════════════════════════════════

    public String generarDiagnostico() {
        StringBuilder sb = new StringBuilder();
        ContentResolver cr = actividad.getContentResolver();

        sb.append("=== DIAGNÓSTICO CONTACTOS BT ===\n\n");

        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt != null && bt.isEnabled()) {
                sb.append("✓ Bluetooth activo\n");

                // BLUETOOTH_CONNECT requerido en Android 12+ (API 31+)
                boolean tienePermisoBt = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        ActivityCompat.checkSelfPermission(actividad,
                                android.Manifest.permission.BLUETOOTH_CONNECT)
                                == PackageManager.PERMISSION_GRANTED;

                if (!tienePermisoBt) {
                    sb.append("⚠ Sin permiso BLUETOOTH_CONNECT (Android 12+)\n");
                } else {
                    @SuppressLint("MissingPermission")
                    java.util.Set<BluetoothDevice> bonded = bt.getBondedDevices();
                    for (BluetoothDevice dev : bonded) {
                        try {
                            Method m = dev.getClass().getMethod("isConnected");
                            boolean conectado = (boolean) m.invoke(dev);
                            @SuppressLint("MissingPermission")
                            String devName = dev.getName();
                            @SuppressLint("MissingPermission")
                            String devAddr = dev.getAddress();
                            sb.append(conectado ? "  ✓ " : "  ○ ")
                                    .append(devName)
                                    .append(" (").append(devAddr).append(")\n");
                        } catch (Exception e) {
                            @SuppressLint("MissingPermission")
                            String devName = dev.getName();
                            sb.append("  ? ").append(devName).append("\n");
                        }
                    }
                }
            } else {
                sb.append("✗ Bluetooth inactivo\n");
            }
        } catch (Exception e) {
            sb.append("✗ Error BT: ").append(e.getMessage()).append("\n");
        }

        sb.append("\n── Proveedores PBAP ──\n");
        for (String uriStr : PBAP_URIS) {
            try {
                Cursor c = cr.query(Uri.parse(uriStr), null, null, null, null);
                if (c != null) {
                    sb.append("✓ ").append(uriStr).append(" → ").append(c.getCount())
                            .append(" registros [")
                            .append(String.join(", ", c.getColumnNames())).append("]\n");
                    c.close();
                } else {
                    sb.append("○ ").append(uriStr).append(" → null\n");
                }
            } catch (SecurityException se) {
                sb.append("⚠ ").append(uriStr).append(" → PERMISO DENEGADO (URI existe!): ")
                        .append(se.getMessage()).append("\n");
            } catch (Exception e) {
                sb.append("✗ ").append(uriStr).append(" → ").append(e.getMessage()).append("\n");
            }
        }

        sb.append("\n── Contactos locales guardados ──\n");
        sb.append("Total: ").append(locales.cantidad()).append("\n");

        return sb.toString();
    }

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

        TextView botonCerrar = crearBotonTexto("Cerrar", "#B0B0B0");
        botonCerrar.setGravity(Gravity.CENTER);
        botonCerrar.setPadding(0, 20, 0, 4);
        raiz.addView(botonCerrar);

        AlertDialog dialogo = new AlertDialog.Builder(actividad)
                .setView(raiz).create();
        if (dialogo.getWindow() != null)
            dialogo.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        botonCerrar.setOnClickListener(v -> dialogo.dismiss());
        dialogo.show();
    }

    // ══════════════════════════════════════
    //  ACCESO AL MANAGER LOCAL
    // ══════════════════════════════════════

    /** Para manipular contactos guardados desde otras partes de la app */
    public ContactosLocalesManager getLocales() {
        return locales;
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

    private TextView crearBotonTexto(String texto, String colorHex) {
        TextView tv = new TextView(actividad);
        tv.setText(texto);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTextSize(14);
        tv.setPadding(12, 10, 12, 10);
        return tv;
    }

    private String extraerColumna(Cursor cursor, String... posiblesNombres) {
        for (String nombre : posiblesNombres) {
            int idx = cursor.getColumnIndex(nombre);
            if (idx >= 0) return cursor.getString(idx);
        }
        return null;
    }
}