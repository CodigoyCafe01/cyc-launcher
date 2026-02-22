package codigo.cafe.alsvinlaunch.services;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.services.ContactosBtAccessibilityService;

/**
 * Pantalla/diálogo que:
 * 1. Verifica si el Accessibility Service está activo
 * 2. Si no, lleva al usuario a Configuración para activarlo
 * 3. Muestra instrucciones para abrir la app nativa de BT
 * 4. Escucha el broadcast de contactos importados y muestra resultado
 *
 * Uso desde cualquier parte de la app:
 *   ImportadorBtHelper.iniciar(actividad);
 */
public class ImportadorBtHelper {

    public static void iniciar(Activity actividad) {
        if (estaActivado(actividad)) {
            mostrarInstrucciones(actividad);
        } else {
            mostrarDialogoActivar(actividad);
        }
    }

    // ── Verificar si el servicio está activo ──

    public static boolean estaActivado(Context ctx) {
        try {
            String habilitados = Settings.Secure.getString(
                    ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return habilitados != null &&
                    habilitados.contains(ctx.getPackageName() + "/" +
                            ContactosBtAccessibilityService.class.getName());
        } catch (Exception e) {
            return false;
        }
    }

    // ── Diálogo: activar servicio ──

    private static void mostrarDialogoActivar(Activity actividad) {
        LinearLayout raiz = new LinearLayout(actividad);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundResource(R.drawable.dialogo_bg);
        raiz.setPadding(50, 40, 50, 40);

        TextView titulo = crearTexto(actividad, "Permiso de accesibilidad",
                Color.WHITE, 17, true);
        raiz.addView(titulo);

        TextView msg = crearTexto(actividad,
                "Para importar contactos automáticamente necesitas activar el " +
                "servicio de accesibilidad de la app.\n\n" +
                "Se abrirá Configuración → busca 'AlsvinLaunch' en la lista → actívalo.\n\n" +
                "Solo necesitas hacerlo UNA VEZ.",
                Color.parseColor("#CCCCCC"), 13, false);
        msg.setPadding(0, 12, 0, 20);
        raiz.addView(msg);

        LinearLayout fila = new LinearLayout(actividad);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER);

        TextView btnCancelar = crearTexto(actividad, "Cancelar",
                Color.parseColor("#888888"), 14, false);
        btnCancelar.setPadding(12, 10, 12, 10);

        TextView btnIr = crearTexto(actividad, "Ir a Configuración",
                Color.WHITE, 14, false);
        btnIr.setBackgroundResource(R.drawable.boton_agregar_bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = 20;
        btnIr.setLayoutParams(lp);
        btnIr.setPadding(30, 14, 30, 14);

        fila.addView(btnCancelar);
        fila.addView(btnIr);
        raiz.addView(fila);

        AlertDialog dialogo = new AlertDialog.Builder(actividad)
                .setView(raiz).create();
        if (dialogo.getWindow() != null)
            dialogo.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnCancelar.setOnClickListener(v -> dialogo.dismiss());
        btnIr.setOnClickListener(v -> {
            dialogo.dismiss();
            actividad.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            // Mostrar instrucciones de vuelta cuando regrese
            Toast.makeText(actividad,
                    "Activa 'AlsvinLaunch' y vuelve a la app",
                    Toast.LENGTH_LONG).show();
        });

        dialogo.show();
    }

    // ── Diálogo: instrucciones de uso ──

    private static void mostrarInstrucciones(Activity actividad) {
        LinearLayout raiz = new LinearLayout(actividad);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundResource(R.drawable.dialogo_bg);
        raiz.setPadding(50, 40, 50, 40);

        TextView titulo = crearTexto(actividad, "✓ Listo para importar",
                Color.parseColor("#4CAF50"), 17, true);
        raiz.addView(titulo);

        TextView pasos = crearTexto(actividad,
                "Ahora haz esto:\n\n" +
                "1. Asegúrate que el teléfono está conectado por BT\n\n" +
                "2. Abre la app Bluetooth del head unit\n\n" +
                "3. Ve a  Book / Contactos\n\n" +
                "4. Espera que cargue la lista completa\n\n" +
                "5. Los contactos se importarán automáticamente\n\n" +
                "Cuando termine verás una notificación.",
                Color.parseColor("#CCCCCC"), 13, false);
        pasos.setPadding(0, 16, 0, 20);
        raiz.addView(pasos);

        // Registrar receiver para el resultado
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                int cantidad = intent.getIntExtra(
                        ContactosBtAccessibilityService.EXTRA_CANTIDAD, 0);
                actividad.runOnUiThread(() ->
                        Toast.makeText(actividad,
                                "✓ " + cantidad + " contactos importados correctamente",
                                Toast.LENGTH_LONG).show()
                );
                try { actividad.unregisterReceiver(this); } catch (Exception ignored) {}
            }
        };
        actividad.registerReceiver(receiver,
                new IntentFilter(ContactosBtAccessibilityService.ACTION_CONTACTOS_IMPORTADOS));

        TextView btnOk = crearTexto(actividad, "Entendido", Color.WHITE, 14, false);
        btnOk.setBackgroundResource(R.drawable.boton_agregar_bg);
        btnOk.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnOk.setLayoutParams(lp);
        btnOk.setPadding(0, 14, 0, 14);
        raiz.addView(btnOk);

        AlertDialog dialogo = new AlertDialog.Builder(actividad)
                .setView(raiz).create();
        if (dialogo.getWindow() != null)
            dialogo.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnOk.setOnClickListener(v -> dialogo.dismiss());
        dialogo.show();
    }

    private static TextView crearTexto(Activity a, String texto, int color,
                                        float size, boolean center) {
        TextView tv = new TextView(a);
        tv.setText(texto);
        tv.setTextColor(color);
        tv.setTextSize(size);
        if (center) tv.setGravity(Gravity.CENTER);
        return tv;
    }
}
