package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/**
 * BluetoothCallManager
 *
 * Envía el número al teléfono conectado por BT (HFP) a través de
 * com.ts.bt.WrcActivity — la app nativa de HiveOS que ya maneja HFP.
 *
 * No usamos ACTION_CALL porque la HU no tiene SIM.
 * WrcActivity recibe el número y le envía el comando AT al teléfono BT.
 *
 * Prueba 4 variantes del Intent hasta que una funcione, porque
 * el formato exacto que espera WrcActivity no está documentado.
 */
public class BluetoothCallManager {

    private static final String PAQUETE   = "com.ts.bt";
    private static final String ACTIVIDAD = "com.ts.bt.WrcActivity";

    // Índice de la última variante exitosa (-1 = no probado aún)
    private int varianteExitosa = -1;

    private final Activity actividad;

    public BluetoothCallManager(Activity actividad) {
        this.actividad = actividad;
    }

    // ══════════════════════════════════════
    //  API pública
    // ══════════════════════════════════════

    public void llamar(String numero) {
        String num = limpiar(numero);
        if (num.isEmpty()) {
            mostrar("Número inválido");
            return;
        }

        // Si ya sabemos cuál variante funciona, usarla directo
        if (varianteExitosa >= 0) {
            lanzar(construir(varianteExitosa, num));
            return;
        }

        // Probar todas las variantes en orden
        for (int i = 0; i < 4; i++) {
            try {
                Intent intent = construir(i, num);
                actividad.startActivity(intent);
                varianteExitosa = i;
                return;
            } catch (Exception ignored) {}
        }

        mostrar("No se pudo conectar con la app BT.\nVerifica que el teléfono esté conectado.");
    }

    // ══════════════════════════════════════
    //  Variantes del Intent
    // ══════════════════════════════════════

    /**
     * Construye el Intent según la variante:
     *
     *  0 — ComponentName + ACTION_CALL + setData(tel:)
     *      Forma más estándar, funciona en muchos stacks HFP
     *
     *  1 — ComponentName + ACTION_DIAL + setData(tel:)
     *      Algunas HUs usan DIAL en lugar de CALL
     *
     *  2 — ComponentName + ACTION_VIEW + setData(tel:)
     *      Algunas actividades BT usan VIEW con URI tel:
     *
     *  3 — ComponentName + Intent vacío + extras "number"
     *      Actividades propietarias que leen el número de un extra
     */
    private Intent construir(int variante, String numero) {
        ComponentName cn = new ComponentName(PAQUETE, ACTIVIDAD);
        Intent intent;

        switch (variante) {
            case 0:
                intent = new Intent(Intent.ACTION_CALL);
                intent.setData(Uri.parse("tel:" + numero));
                intent.setComponent(cn);
                break;
            case 1:
                intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + numero));
                intent.setComponent(cn);
                break;
            case 2:
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("tel:" + numero));
                intent.setComponent(cn);
                break;
            case 3:
            default:
                intent = new Intent();
                intent.setComponent(cn);
                intent.putExtra("number", numero);
                intent.putExtra("phone_number", numero);
                intent.putExtra("dial_number", numero);
                break;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    // ══════════════════════════════════════
    //  Diagnóstico
    // ══════════════════════════════════════

    public String obtenerInfoDiagnostico() {
        StringBuilder sb = new StringBuilder();
        sb.append("App BT objetivo:\n  ").append(ACTIVIDAD).append("\n\n");

        try {
            actividad.getPackageManager().getPackageInfo(PAQUETE, 0);
            sb.append("Estado: instalado ✓\n");
        } catch (Exception e) {
            sb.append("Estado: NO encontrado ✗\n");
            sb.append("  (verifica que el paquete sea '").append(PAQUETE).append("')\n");
        }

        if (varianteExitosa >= 0) {
            sb.append("\nVariante exitosa: ").append(varianteExitosa)
                    .append(" (").append(nombreVariante(varianteExitosa)).append(")");
        } else {
            sb.append("\nAún no se ha realizado ninguna llamada.");
        }

        return sb.toString();
    }

    private String nombreVariante(int v) {
        switch (v) {
            case 0: return "ACTION_CALL + tel:";
            case 1: return "ACTION_DIAL + tel:";
            case 2: return "ACTION_VIEW + tel:";
            case 3: return "Intent extras 'number'";
            default: return "desconocida";
        }
    }

    // ══════════════════════════════════════
    //  Utilidades
    // ══════════════════════════════════════

    private void lanzar(Intent intent) {
        try {
            actividad.startActivity(intent);
        } catch (Exception e) {
            varianteExitosa = -1;
            mostrar("Error al abrir app BT: " + e.getMessage());
        }
    }

    private String limpiar(String numero) {
        if (numero == null) return "";
        return numero.replaceAll("[^+0-9]", "");
    }

    private void mostrar(String msg) {
        Toast.makeText(actividad, msg, Toast.LENGTH_SHORT).show();
    }
}