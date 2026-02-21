package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.util.ConstantesSistema;

/**
 * Gestiona el diálogo de contactos rápidos (3 contactos con llamada directa).
 */
public class ContactsDialogHelper {

    private static final int MAX_CONTACTOS = 3;

    private final Activity actividad;
    private final SharedPreferences prefs;
    private BluetoothContactsManager btContactsManager;

    public interface OnAppOpenListener {
        void onAppOpening();
    }

    private OnAppOpenListener listener;

    public ContactsDialogHelper(Activity actividad, SharedPreferences prefs,
                                BluetoothContactsManager btContactsManager) {
        this.actividad = actividad;
        this.prefs = prefs;
        this.btContactsManager = btContactsManager;
    }


    public void setOnAppOpenListener(OnAppOpenListener listener) {
        this.listener = listener;
    }

    public void mostrar() {
        LinearLayout raiz = new LinearLayout(actividad);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundResource(R.drawable.dialogo_bg);
        raiz.setPadding(40, 30, 40, 30);

        // Título
        TextView titulo = crearTexto("Contactos Rápidos", Color.WHITE, 18);
        titulo.setGravity(Gravity.CENTER);
        titulo.setPadding(0, 0, 0, 20);
        raiz.addView(titulo);

        // Contactos
        for (int i = 0; i < MAX_CONTACTOS; i++) {
            raiz.addView(crearFilaContacto(i));
            if (i < MAX_CONTACTOS - 1) raiz.addView(crearSeparador());
        }

        // Botón cerrar
        TextView botonCerrar = crearTexto("Cerrar", Color.parseColor("#B0B0B0"), 14);
        botonCerrar.setGravity(Gravity.CENTER);
        botonCerrar.setPadding(0, 24, 0, 4);
        raiz.addView(botonCerrar);

        AlertDialog dialogo = new AlertDialog.Builder(actividad).setView(raiz).create();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        botonCerrar.setOnClickListener(v -> dialogo.dismiss());
        dialogo.show();
    }

    // ── Crear fila de contacto ──

    private LinearLayout crearFilaContacto(int indice) {
        String nombre = prefs.getString("contacto_nombre_" + indice, null);
        String numero = prefs.getString("contacto_numero_" + indice, null);

        LinearLayout fila = new LinearLayout(actividad);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        fila.setPadding(0, 10, 0, 10);

        if (nombre != null && numero != null) {
            agregarContactoExistente(fila, indice, nombre, numero);
        } else {
            agregarBotonAgregar(fila, indice);
        }
        return fila;
    }

    private void agregarContactoExistente(LinearLayout fila, int indice, String nombre, String numero) {
        // Icono llamar
        ImageView iconoLlamar = new ImageView(actividad);
        iconoLlamar.setImageResource(R.drawable.ic_llamar);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(40, 40);
        p1.rightMargin = 16;
        iconoLlamar.setLayoutParams(p1);
        iconoLlamar.setClickable(true);
        iconoLlamar.setFocusable(true);

        // Info
        LinearLayout infoContacto = new LinearLayout(actividad);
        infoContacto.setOrientation(LinearLayout.VERTICAL);
        infoContacto.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        infoContacto.addView(crearTexto(nombre, Color.WHITE, 14));
        infoContacto.addView(crearTexto(numero, Color.parseColor("#B0B0B0"), 12));

        // Botón eliminar
        ImageView botonEliminar = new ImageView(actividad);
        botonEliminar.setImageResource(R.drawable.ic_eliminar);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(36, 36);
        p2.leftMargin = 16;
        botonEliminar.setLayoutParams(p2);
        botonEliminar.setClickable(true);
        botonEliminar.setFocusable(true);

        fila.addView(iconoLlamar);
        fila.addView(infoContacto);
        fila.addView(botonEliminar);

        // Acciones
        View.OnClickListener accionLlamar = v -> realizarLlamada(numero);
        iconoLlamar.setOnClickListener(accionLlamar);
        infoContacto.setOnClickListener(accionLlamar);

        botonEliminar.setOnClickListener(v -> {
            prefs.edit()
                    .remove("contacto_nombre_" + indice)
                    .remove("contacto_numero_" + indice)
                    .apply();
            Toast.makeText(actividad, "Contacto eliminado", Toast.LENGTH_SHORT).show();
            mostrar(); // Reabrir
        });
    }

    private void agregarBotonAgregar(LinearLayout fila, int indice) {
        TextView textoAgregar = crearTexto("+ Agregar contacto " + (indice + 1),
                Color.parseColor("#2979FF"), 14);
        textoAgregar.setPadding(10, 14, 10, 14);
        textoAgregar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        textoAgregar.setOnClickListener(v -> mostrarDialogoAgregar(indice));
        fila.addView(textoAgregar);
    }

    // ── Diálogo agregar contacto ──

    private void mostrarDialogoAgregar(int indice) {


        LinearLayout raiz = new LinearLayout(actividad);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundResource(R.drawable.dialogo_bg);
        raiz.setPadding(40, 30, 40, 30);

        TextView titulo = crearTexto("Nuevo Contacto", Color.WHITE, 18);
        titulo.setGravity(Gravity.CENTER);
        titulo.setPadding(0, 0, 0, 20);
        raiz.addView(titulo);

        raiz.addView(crearTexto("Nombre:", Color.parseColor("#B0B0B0"), 12));
        EditText campoNombre = crearCampoTexto("Ej: Juan", InputType.TYPE_CLASS_TEXT);
        raiz.addView(campoNombre);

        TextView labelNumero = crearTexto("Número:", Color.parseColor("#B0B0B0"), 12);
        labelNumero.setPadding(0, 16, 0, 0);
        raiz.addView(labelNumero);
        EditText campoNumero = crearCampoTexto("Ej: 0991234567", InputType.TYPE_CLASS_PHONE);
        raiz.addView(campoNumero);

        // Botones
        LinearLayout botones = new LinearLayout(actividad);
        botones.setOrientation(LinearLayout.HORIZONTAL);
        botones.setGravity(Gravity.CENTER);
        botones.setPadding(0, 24, 0, 4);

        TextView botonCancelar = crearTexto("Cancelar", Color.parseColor("#B0B0B0"), 14);
        botonCancelar.setPadding(20, 12, 20, 12);

        TextView botonGuardar = crearTexto("  Guardar  ", Color.WHITE, 14);
        botonGuardar.setBackgroundResource(R.drawable.boton_agregar_bg);
        botonGuardar.setPadding(30, 12, 30, 12);
        LinearLayout.LayoutParams pg = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pg.leftMargin = 20;
        botonGuardar.setLayoutParams(pg);

        botones.addView(botonCancelar);
        botones.addView(botonGuardar);
        raiz.addView(botones);

        AlertDialog dialogo = new AlertDialog.Builder(actividad).setView(raiz).create();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView botonImportar = crearTexto("📱 Importar desde teléfono",
                Color.parseColor("#2979FF"), 14);
        botonImportar.setPadding(10, 16, 10, 8);
        botonImportar.setOnClickListener(v -> {
            dialogo.dismiss();
            btContactsManager.mostrarSelectorContactos((nombre, numero) -> {
                // Guardar directamente como contacto rápido
                prefs.edit()
                        .putString("contacto_nombre_" + indice, nombre)
                        .putString("contacto_numero_" + indice, numero)
                        .apply();
                Toast.makeText(actividad, "Contacto importado: " + nombre,
                        Toast.LENGTH_SHORT).show();
                mostrar(); // Reabrir diálogo principal
            });
        });
        raiz.addView(botonImportar);

        botonCancelar.setOnClickListener(v -> {
            dialogo.dismiss();
            mostrar();
        });

        botonGuardar.setOnClickListener(v -> {
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
                Toast.makeText(actividad, "No se pudo realizar la llamada", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ── Utilidades UI ──

    private TextView crearTexto(String texto, int color, float size) {
        TextView tv = new TextView(actividad);
        tv.setText(texto);
        tv.setTextColor(color);
        tv.setTextSize(size);
        return tv;
    }

    private EditText crearCampoTexto(String hint, int inputType) {
        EditText campo = new EditText(actividad);
        campo.setHint(hint);
        campo.setTextColor(Color.WHITE);
        campo.setHintTextColor(Color.parseColor("#555555"));
        campo.setTextSize(16);
        campo.setSingleLine(true);
        campo.setInputType(inputType);
        campo.setPadding(12, 12, 12, 12);
        return campo;
    }

    private View crearSeparador() {
        View separador = new View(actividad);
        separador.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        separador.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return separador;
    }
}
