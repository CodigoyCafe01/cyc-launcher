package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.util.ConstantesSistema;

public class ContactsDialogHelper {

    private static final int MAX_CONTACTOS = 3;

    private final Activity actividad;
    private final SharedPreferences prefs;
    private final BluetoothContactsManager btContactsManager;
    private OnAppOpenListener listener;

    // Probar la base de datos de contactos del sistema del carro
    private static final String[] URIS_SISTEMA = {
            // Contactos normales de Android (a veces el carro los sincroniza aquí)
            "content://com.android.contacts/data/phones",
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI.toString(),
            // HiveOS específico
            "content://com.hive.contacts/phones",
            "content://contacts/phones"
    };

    public interface OnAppOpenListener {
        void onAppOpening();
    }

    public ContactsDialogHelper(Activity actividad, SharedPreferences prefs,
                                BluetoothContactsManager btContactsManager) {
        this.actividad = actividad;
        this.prefs = prefs;
        this.btContactsManager = btContactsManager;
    }

    public void setOnAppOpenListener(OnAppOpenListener listener) {
        this.listener = listener;
    }

    // ── Diálogo principal ──

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

        dialogo.show();
    }

    private void configurarFilaContacto(View fila, int indice, AlertDialog dialogo) {
        String nombre = prefs.getString("contacto_nombre_" + indice, null);
        String numero = prefs.getString("contacto_numero_" + indice, null);

        LinearLayout grupoContacto = fila.findViewById(R.id.grupo_contacto);
        TextView botonAgregar     = fila.findViewById(R.id.boton_agregar);

        if (nombre != null && numero != null) {
            grupoContacto.setVisibility(View.VISIBLE);
            botonAgregar.setVisibility(View.GONE);

            ((TextView) fila.findViewById(R.id.texto_nombre)).setText(nombre);
            ((TextView) fila.findViewById(R.id.texto_numero)).setText(numero);

            View.OnClickListener accionLlamar = v -> realizarLlamada(numero);
            fila.findViewById(R.id.icono_llamar).setOnClickListener(accionLlamar);
            fila.findViewById(R.id.info_contacto).setOnClickListener(accionLlamar);

            fila.findViewById(R.id.boton_eliminar).setOnClickListener(v -> {
                prefs.edit()
                        .remove("contacto_nombre_" + indice)
                        .remove("contacto_numero_" + indice)
                        .apply();
                Toast.makeText(actividad, "Contacto eliminado", Toast.LENGTH_SHORT).show();
                dialogo.dismiss();
                mostrar();
            });

        } else {
            grupoContacto.setVisibility(View.GONE);
            botonAgregar.setVisibility(View.VISIBLE);
            botonAgregar.setText("+ Agregar contacto " + (indice + 1));
            botonAgregar.setOnClickListener(v -> {
                dialogo.dismiss();
                mostrarDialogoAgregar(indice);
            });
        }
    }

    // ── Diálogo agregar ──

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
                Toast.makeText(actividad, "Completa ambos campos", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit()
                    .putString("contacto_nombre_" + indice, nombre)
                    .putString("contacto_numero_" + indice, numero)
                    .apply();
            Toast.makeText(actividad, "Contacto guardado", Toast.LENGTH_SHORT).show();
            dialogo.dismiss();
            mostrar();
        });

        vista.findViewById(R.id.boton_importar).setOnClickListener(v -> {
            dialogo.dismiss();
            btContactsManager.mostrarSelectorContactos((nombre, numero) -> {
                prefs.edit()
                        .putString("contacto_nombre_" + indice, nombre)
                        .putString("contacto_numero_" + indice, numero)
                        .apply();
                Toast.makeText(actividad, "Contacto importado: " + nombre,
                        Toast.LENGTH_SHORT).show();
                mostrar();
            });
        });

        dialogo.show();
    }

    // ── Llamadas ──

    private void realizarLlamada(String numero) {
        try {
            if (listener != null) listener.onAppOpening();
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + numero));
            actividad.startActivity(intent);
        } catch (SecurityException e) {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + numero));
            actividad.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(
                        ConstantesSistema.PAQUETE_HIVEOS,
                        ConstantesSistema.ACTIVIDAD_TELEFONO));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                actividad.startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(actividad, "No se pudo realizar la llamada",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}