package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Color;
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

import java.util.ArrayList;
import java.util.List;

import codigo.cafe.alsvinlaunch.R;

/**
 * Gestiona contactos del dispositivo vía ContactsContract (Google/locales).
 *
 * Requiere: READ_CONTACTS (ya declarado en el manifest).
 * No requiere: accesibilidad, PBAP, URIs especiales de BT.
 */
public class BluetoothContactsManager {

    private static final String TAG = "Contactos";

    private final Activity actividad;
    private final ContactosLocalesManager locales;

    public interface OnContactSelectedListener {
        void onContactSelected(String nombre, String numero);
    }

    public BluetoothContactsManager(Activity actividad) {
        this.actividad = actividad;
        this.locales   = new ContactosLocalesManager(actividad);
    }

    // ══════════════════════════════════════
    //  OBTENER CONTACTOS DEL DISPOSITIVO
    // ══════════════════════════════════════

    /**
     * Lee todos los contactos con número de teléfono del dispositivo.
     * Incluye contactos de Google, locales, y cualquier cuenta sincronizada.
     */
    public List<ContactoBt> obtenerContactos() {
        List<ContactoBt> contactos = new ArrayList<>();
        ContentResolver cr = actividad.getContentResolver();

        try {
            Cursor cursor = cr.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.TYPE
                    },
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String nombre = cursor.getString(0);
                    String numero = cursor.getString(1);
                    if (numero != null && !numero.trim().isEmpty()) {
                        contactos.add(new ContactoBt(
                                nombre != null ? nombre : numero,
                                numero.trim()
                        ));
                    }
                }
                cursor.close();
            }

            Log.d(TAG, "Contactos encontrados: " + contactos.size());

        } catch (Exception e) {
            Log.e(TAG, "Error leyendo contactos: " + e.getMessage());
        }

        return contactos;
    }

    // ══════════════════════════════════════
    //  DIÁLOGO — IMPORTAR Y GUARDAR LOCALMENTE
    // ══════════════════════════════════════

    /**
     * Muestra la lista de contactos del dispositivo para importar
     * los seleccionados al almacenamiento local de la app.
     */
    public void mostrarImportador() {
        actividad.runOnUiThread(() ->
                Toast.makeText(actividad, "Cargando contactos...", Toast.LENGTH_SHORT).show());

        new Thread(() -> {
            final List<ContactoBt> contactos = obtenerContactos();
            actividad.runOnUiThread(() -> {
                if (contactos.isEmpty()) {
                    Toast.makeText(actividad,
                            "No se encontraron contactos en el dispositivo",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                mostrarDialogoImportacion(contactos);
            });
        }).start();
    }

    private void mostrarDialogoImportacion(List<ContactoBt> contactos) {
        LinearLayout raiz = construirRaizDialog();

        // Título
        raiz.addView(crearTitulo("Importar contactos (" + contactos.size() + ")"));
        raiz.addView(crearSubtitulo("Seleccioná los que querés guardar en la app"));

        // Búsqueda
        android.widget.EditText busqueda = crearBuscador();
        raiz.addView(busqueda);

        // Botones seleccionar todos / ninguno
        final boolean[] seleccionados = new boolean[contactos.size()];
        for (int i = 0; i < seleccionados.length; i++) seleccionados[i] = true;

        LinearLayout filaBotones = new LinearLayout(actividad);
        filaBotones.setOrientation(LinearLayout.HORIZONTAL);
        filaBotones.setPadding(0, 8, 0, 4);
        TextView btnTodos   = crearBotonTexto("Seleccionar todos", "#2979FF");
        TextView btnNinguno = crearBotonTexto("Ninguno", "#888888");
        btnTodos.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        btnNinguno.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        filaBotones.addView(btnTodos);
        filaBotones.addView(btnNinguno);
        raiz.addView(filaBotones);

        // Lista
        ScrollView scroll = new ScrollView(actividad);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 500));
        LinearLayout lista = new LinearLayout(actividad);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(0, 4, 0, 0);
        scroll.addView(lista);
        raiz.addView(scroll);

        poblarListaImportacion(lista, contactos, seleccionados, "");

        busqueda.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                poblarListaImportacion(lista, contactos, seleccionados,
                        s.toString().toLowerCase().trim());
            }
        });

        btnTodos.setOnClickListener(v -> {
            for (int i = 0; i < seleccionados.length; i++) seleccionados[i] = true;
            poblarListaImportacion(lista, contactos, seleccionados,
                    busqueda.getText().toString().toLowerCase().trim());
        });
        btnNinguno.setOnClickListener(v -> {
            for (int i = 0; i < seleccionados.length; i++) seleccionados[i] = false;
            poblarListaImportacion(lista, contactos, seleccionados,
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
        btnImportar.setPadding(40, 14, 40, 14);
        LinearLayout.LayoutParams lpImp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpImp.leftMargin = 20;
        btnImportar.setLayoutParams(lpImp);

        filaFinal.addView(btnCancelar);
        filaFinal.addView(btnImportar);
        raiz.addView(filaFinal);

        AlertDialog dialogo = new AlertDialog.Builder(actividad)
                .setView(raiz).create();
        if (dialogo.getWindow() != null)
            dialogo.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnCancelar.setOnClickListener(v -> dialogo.dismiss());

        btnImportar.setOnClickListener(v -> {
            List<ContactoBt> aGuardar = new ArrayList<>();
            for (int i = 0; i < contactos.size(); i++) {
                if (seleccionados[i]) aGuardar.add(contactos.get(i));
            }
            int guardados = locales.agregarVarios(aGuardar);
            Toast.makeText(actividad,
                    guardados + " contactos importados", Toast.LENGTH_SHORT).show();
            dialogo.dismiss();
        });

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
    //  DIÁLOGO — SELECTOR (contactos guardados)
    // ══════════════════════════════════════

    /**
     * Muestra los contactos ya importados para seleccionar uno y llamar.
     * Incluye botón para importar más desde el dispositivo.
     */
    public void mostrarSelectorContactos(final OnContactSelectedListener listener) {
        List<ContactoBt> guardados = locales.obtenerTodos();

        LinearLayout raiz = construirRaizDialog();
        raiz.addView(crearTitulo("Contactos (" + guardados.size() + ")"));

        // Botón importar más
        TextView btnImportar = crearBotonTexto("📱 Importar contactos del dispositivo", "#2979FF");
        btnImportar.setGravity(Gravity.CENTER);
        btnImportar.setPadding(0, 6, 0, 10);
        raiz.addView(btnImportar);

        // Búsqueda
        android.widget.EditText busqueda = crearBuscador();
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

        poblarListaContactos(lista, guardados, listener, dialogo);

        if (guardados.isEmpty()) {
            TextView empty = new TextView(actividad);
            empty.setText("No hay contactos guardados.\nUsá 'Importar' para agregarlos.");
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

        btnImportar.setOnClickListener(v -> {
            dialogo.dismiss();
            mostrarImportador();
        });
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
    //  ACCESO AL MANAGER LOCAL
    // ══════════════════════════════════════

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
    //  UTILIDADES DE UI
    // ══════════════════════════════════════

    private LinearLayout construirRaizDialog() {
        LinearLayout raiz = new LinearLayout(actividad);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundResource(R.drawable.dialogo_bg);
        raiz.setPadding(40, 30, 40, 30);
        return raiz;
    }

    private TextView crearTitulo(String texto) {
        TextView tv = new TextView(actividad);
        tv.setText(texto);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(18);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 0, 0, 4);
        return tv;
    }

    private TextView crearSubtitulo(String texto) {
        TextView tv = new TextView(actividad);
        tv.setText(texto);
        tv.setTextColor(Color.parseColor("#B0B0B0"));
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 0, 0, 12);
        return tv;
    }

    private android.widget.EditText crearBuscador() {
        android.widget.EditText et = new android.widget.EditText(actividad);
        et.setHint("Buscar...");
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(Color.parseColor("#555555"));
        et.setTextSize(13);
        et.setSingleLine(true);
        et.setPadding(16, 10, 16, 10);
        et.setBackgroundResource(R.drawable.system_item_bg);
        return et;
    }

    private TextView crearBotonTexto(String texto, String colorHex) {
        TextView tv = new TextView(actividad);
        tv.setText(texto);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTextSize(14);
        tv.setPadding(12, 10, 12, 10);
        return tv;
    }
}