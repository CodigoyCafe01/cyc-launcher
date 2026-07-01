package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import codigo.cafe.alsvinlaunch.R;

public class ContactsDialogHelper {

    public static final int REQUEST_PICK_CONTACT = 2001;
    private static final int MAX_CONTACTOS = 3;

    private final Activity actividad;
    private final SharedPreferences prefs;
    private final BluetoothCallManager btCallManager;   // ← NUEVO
    private OnAppOpenListener listenerApp;
    private int indicePendiente = -1;

    public interface OnAppOpenListener {
        void onAppOpening();
    }

    public ContactsDialogHelper(Activity actividad, SharedPreferences prefs,
                                BluetoothContactsManager btContactsManager) {
        this.actividad      = actividad;
        this.prefs          = prefs;
        this.btCallManager  = new BluetoothCallManager(actividad);  // ← NUEVO
    }

    public void setOnAppOpenListener(OnAppOpenListener listener) {
        this.listenerApp = listener;
    }

    // ══════════════════════════════════════
    //  Diálogo principal
    // ══════════════════════════════════════

    public void mostrar() {
        View vista = LayoutInflater.from(actividad)
                .inflate(R.layout.dialog_contactos_rapidos, null);

        int[] filaIds = {
                R.id.fila_contacto_0,
                R.id.fila_contacto_1,
                R.id.fila_contacto_2
        };

        AlertDialog dialogo = new AlertDialog.Builder(actividad)
                .setView(vista)
                .create();

        if (dialogo.getWindow() != null)
            dialogo.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        for (int i = 0; i < MAX_CONTACTOS; i++) {
            configurarFilaContacto(vista.findViewById(filaIds[i]), i, dialogo);
        }

        vista.findViewById(R.id.boton_cerrar)
                .setOnClickListener(v -> dialogo.dismiss());

        // ── Botón diagnóstico BT ──────────────────────────────────────
        // Si tu layout tiene un boton_diagnostico_bt, lo conectamos aquí.
        // Si no existe en el layout, esta línea simplemente no hace nada.
        View btnDiag = vista.findViewById(R.id.boton_diagnostico_bt);
        if (btnDiag != null) {
            btnDiag.setOnClickListener(v -> mostrarDiagnosticoBt());
        }
        // ─────────────────────────────────────────────────────────────

        dialogo.show();
    }

    // ══════════════════════════════════════
    //  Filas de contacto
    // ══════════════════════════════════════

    private void configurarFilaContacto(View fila, int indice, AlertDialog dialogo) {
        String nombre = prefs.getString("contacto_nombre_" + indice, null);
        String numero = prefs.getString("contacto_numero_" + indice, null);

        LinearLayout grupoContacto = fila.findViewById(R.id.grupo_contacto);
        TextView botonAgregar      = fila.findViewById(R.id.boton_agregar);

        if (nombre != null && numero != null) {
            grupoContacto.setVisibility(View.VISIBLE);
            botonAgregar.setVisibility(View.GONE);

            ((TextView) fila.findViewById(R.id.texto_nombre)).setText(nombre);
            ((TextView) fila.findViewById(R.id.texto_numero)).setText(numero);

            // ── Llamada vía BT ────────────────────────────────────────
            final String numeroCopia = numero;
            View.OnClickListener accionLlamar = v -> realizarLlamada(numeroCopia);
            fila.findViewById(R.id.icono_llamar).setOnClickListener(accionLlamar);
            fila.findViewById(R.id.info_contacto).setOnClickListener(accionLlamar);
            // ─────────────────────────────────────────────────────────

            fila.findViewById(R.id.boton_eliminar).setOnClickListener(v -> {
                prefs.edit()
                        .remove("contacto_nombre_" + indice)
                        .remove("contacto_numero_" + indice)
                        .commit();
                Toast.makeText(actividad, actividad.getString(R.string.msg_contacto_eliminado), Toast.LENGTH_SHORT).show();
                dialogo.dismiss();
                mostrar();
            });

        } else {
            grupoContacto.setVisibility(View.GONE);
            botonAgregar.setVisibility(View.VISIBLE);
            botonAgregar.setText(actividad.getString(R.string.btn_agregar_contacto, indice + 1));
            botonAgregar.setOnClickListener(v -> {
                dialogo.dismiss();
                mostrarDialogoAgregar(indice);
            });
        }
    }

    // ══════════════════════════════════════
    //  Diálogo agregar contacto
    // ══════════════════════════════════════

    private void mostrarDialogoAgregar(int indice) {
        View vista = LayoutInflater.from(actividad)
                .inflate(R.layout.dialog_agregar_contacto, null);

        EditText campoNombre = vista.findViewById(R.id.campo_nombre);
        EditText campoNumero = vista.findViewById(R.id.campo_numero);

        AlertDialog dialogo = new AlertDialog.Builder(actividad)
                .setView(vista)
                .create();

        if (dialogo.getWindow() != null)
            dialogo.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        vista.findViewById(R.id.boton_cancelar).setOnClickListener(v -> {
            dialogo.dismiss();
            mostrar();
        });

        vista.findViewById(R.id.boton_guardar).setOnClickListener(v -> {
            String nombre = campoNombre.getText().toString().trim();
            String numero = campoNumero.getText().toString().trim();
            if (nombre.isEmpty() || numero.isEmpty()) {
                Toast.makeText(actividad, actividad.getString(R.string.msg_completa_campos), Toast.LENGTH_SHORT).show();
                return;
            }
            guardarContacto(indice, nombre, numero);
            dialogo.dismiss();
            mostrar();
        });

        vista.findViewById(R.id.boton_importar).setOnClickListener(v -> {
            dialogo.dismiss();
            abrirPickerContactos(indice);
        });

        dialogo.show();
    }

    // ══════════════════════════════════════
    //  Picker de contactos del sistema
    // ══════════════════════════════════════

    private void abrirPickerContactos(int indice) {
        indicePendiente = indice;
        prefs.edit().putInt("contacto_pendiente_idx", indice).commit();
        if (listenerApp != null) listenerApp.onAppOpening();

        try {
            Intent intent = new Intent(Intent.ACTION_PICK,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            actividad.startActivityForResult(intent, REQUEST_PICK_CONTACT);
        } catch (Exception e) {
            Toast.makeText(actividad,
                    "No se pudo abrir contactos", Toast.LENGTH_SHORT).show();
            indicePendiente = -1;
            prefs.edit().remove("contacto_pendiente_idx").commit();
        }
    }

    public void procesarResultado(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK_CONTACT) return;

        if (indicePendiente < 0) {
            indicePendiente = prefs.getInt("contacto_pendiente_idx", -1);
        }

        if (resultCode != Activity.RESULT_OK || data == null || indicePendiente < 0) {
            indicePendiente = -1;
            prefs.edit().remove("contacto_pendiente_idx").commit();
            return;
        }

        try {
            Cursor cursor = actividad.getContentResolver().query(
                    data.getData(),
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                String nombre = cursor.getString(0);
                String numero = cursor.getString(1);
                cursor.close();

                if (numero != null && !numero.trim().isEmpty()) {
                    guardarContacto(indicePendiente, nombre, numero.trim());
                    Toast.makeText(actividad,
                            "Contacto guardado: " + nombre, Toast.LENGTH_SHORT).show();
                    mostrar();
                } else {
                    Toast.makeText(actividad,
                            "El contacto no tiene número", Toast.LENGTH_SHORT).show();
                }
            } else {
                if (cursor != null) cursor.close();
                Toast.makeText(actividad,
                        "No se pudo leer el contacto", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(actividad,
                    "Error al importar contacto", Toast.LENGTH_SHORT).show();
        }

        indicePendiente = -1;
        prefs.edit().remove("contacto_pendiente_idx").commit();
    }

    // ══════════════════════════════════════
    //  Llamada vía BT
    // ══════════════════════════════════════

    /**
     * Delega la llamada al BluetoothCallManager.
     * Notifica a MainActivity que vamos a abrir otra app (evita retorno kiosco).
     */
    private void realizarLlamada(String numero) {
        if (listenerApp != null) listenerApp.onAppOpening();
        btCallManager.llamar(numero);
    }

    // ══════════════════════════════════════
    //  Diagnóstico BT
    // ══════════════════════════════════════

    /**
     * Muestra un AlertDialog con info del estado BT y la app detectada.
     * Útil para identificar el paquete correcto sin ADB.
     */
    private void mostrarDiagnosticoBt() {
        String info = btCallManager.obtenerInfoDiagnostico();

        new AlertDialog.Builder(actividad)
                .setTitle(R.string.dlg_diagnostico_bt)
                .setMessage(info)
                .setPositiveButton(R.string.btn_cerrar, null)
                .show();
    }

    // ══════════════════════════════════════
    //  Utilidades
    // ══════════════════════════════════════

    private void guardarContacto(int indice, String nombre, String numero) {
        prefs.edit()
                .putString("contacto_nombre_" + indice, nombre)
                .putString("contacto_numero_" + indice, numero)
                .commit();
    }
}