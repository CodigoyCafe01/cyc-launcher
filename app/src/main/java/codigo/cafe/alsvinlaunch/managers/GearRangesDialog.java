package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import codigo.cafe.alsvinlaunch.R;

/**
 * GearRangesDialog
 *
 * Diálogo para configurar los rangos de velocidad (km/h) en los que el
 * indicador de marcha sugiere subir de cambio.
 *
 * Presets por cilindrada (motores pequeños suben antes para no quedarse
 * faltos de torque, motores grandes pueden estirar más):
 *
 *   1.0 → 15 / 28 / 45 / 65
 *   1.2 → 18 / 32 / 50 / 70
 *   1.4 → 20 / 35 / 55 / 80
 *   1.5 → 22 / 38 / 58 / 85   ← Alsvin 1.5
 *   1.6 → 25 / 42 / 65 / 95
 *   2.0 → 28 / 50 / 75 / 105
 *
 * Persistencia: SharedPreferences (mismo archivo que el resto del launcher).
 */
public class GearRangesDialog {

    public static final String PREFS_NAME       = "alsvin_launcher_prefs";
    public static final String PREF_MOTOR       = "gear_motor_tipo";
    public static final String PREF_CORTE_PREFIX = "gear_corte_";   // _1, _2, _3, _4
    public static final int    DEFAULT_MOTOR_INDEX = 3; // 1.5 (Alsvin)

    /** Etiquetas de los motores (orden = índice en SharedPreferences). */
    public static final String[] MOTORES = {
            "1.0", "1.2", "1.4", "1.5", "1.6", "2.0", "Personalizado"
    };

    /** Presets de cortes (1→2, 2→3, 3→4, 4→5) por índice de motor. */
    private static final int[][] PRESETS = {
            { 15, 28, 45,  65 }, // 1.0
            { 18, 32, 50,  70 }, // 1.2
            { 20, 35, 55,  80 }, // 1.4
            { 22, 38, 58,  85 }, // 1.5
            { 25, 42, 65,  95 }, // 1.6
            { 28, 50, 75, 105 }, // 2.0
    };

    private static final int INDICE_PERSONALIZADO = 6;

    public interface OnSavedListener {
        void onCortesActualizados();
    }

    /** API estática: leer los cortes guardados (con fallback a preset 1.5). */
    public static int[] cortesGuardados(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int motor = prefs.getInt(PREF_MOTOR, DEFAULT_MOTOR_INDEX);

        int[] base = (motor >= 0 && motor < PRESETS.length)
                ? PRESETS[motor]
                : PRESETS[DEFAULT_MOTOR_INDEX];

        int[] cortes = new int[4];
        for (int i = 0; i < 4; i++) {
            cortes[i] = prefs.getInt(PREF_CORTE_PREFIX + (i + 1), base[i]);
        }
        return cortes;
    }

    /** Abre el diálogo. Llama al listener cuando el usuario guarda. */
    public static void mostrar(Activity actividad, OnSavedListener listener) {
        SharedPreferences prefs = actividad.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        View vista = LayoutInflater.from(actividad)
                .inflate(R.layout.dialog_gear_ranges, null);

        Spinner spinnerMotor = vista.findViewById(R.id.spinner_motor);
        EditText[] inputs = {
                vista.findViewById(R.id.input_corte_1),
                vista.findViewById(R.id.input_corte_2),
                vista.findViewById(R.id.input_corte_3),
                vista.findViewById(R.id.input_corte_4),
        };
        TextView botonRestablecer = vista.findViewById(R.id.boton_restablecer);
        TextView botonCancelar    = vista.findViewById(R.id.boton_cancelar);
        TextView botonGuardar     = vista.findViewById(R.id.boton_guardar);

        // Adapter del spinner — los strings se muestran sobre fondo oscuro
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                actividad,
                android.R.layout.simple_spinner_item,
                MOTORES) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(0xFFFFFFFF);
                v.setText(textoMotor(position));
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMotor.setAdapter(adapter);

        // Cargar valores actuales
        int motorActual = prefs.getInt(PREF_MOTOR, DEFAULT_MOTOR_INDEX);
        if (motorActual < 0 || motorActual >= MOTORES.length) motorActual = DEFAULT_MOTOR_INDEX;
        spinnerMotor.setSelection(motorActual);

        int[] cortesActuales = cortesGuardados(actividad);
        for (int i = 0; i < 4; i++) {
            inputs[i].setText(String.valueOf(cortesActuales[i]));
        }

        // Cuando cambia el motor, precargar el preset (excepto "Personalizado")
        final boolean[] ignorarPrimerCambio = { true };
        spinnerMotor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (ignorarPrimerCambio[0]) {
                    ignorarPrimerCambio[0] = false;
                    return;
                }
                if (position >= 0 && position < PRESETS.length) {
                    aplicarPreset(inputs, PRESETS[position]);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        botonRestablecer.setOnClickListener(v -> {
            int sel = spinnerMotor.getSelectedItemPosition();
            if (sel >= 0 && sel < PRESETS.length) {
                aplicarPreset(inputs, PRESETS[sel]);
            } else {
                aplicarPreset(inputs, PRESETS[DEFAULT_MOTOR_INDEX]);
                spinnerMotor.setSelection(DEFAULT_MOTOR_INDEX);
            }
        });

        AlertDialog dialogo = new AlertDialog.Builder(actividad)
                .setView(vista)
                .create();

        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        botonCancelar.setOnClickListener(v -> dialogo.dismiss());

        botonGuardar.setOnClickListener(v -> {
            int[] cortes = new int[4];
            for (int i = 0; i < 4; i++) {
                String txt = inputs[i].getText().toString().trim();
                if (TextUtils.isEmpty(txt)) {
                    Toast.makeText(actividad, actividad.getString(R.string.msg_completa_rangos),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    cortes[i] = Integer.parseInt(txt);
                } catch (NumberFormatException e) {
                    Toast.makeText(actividad, actividad.getString(R.string.msg_valor_invalido, i + 1, i + 2),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (cortes[i] < 5 || cortes[i] > 200) {
                    Toast.makeText(actividad, actividad.getString(R.string.msg_rango_limite),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            // Cortes deben ir en orden ascendente
            for (int i = 1; i < 4; i++) {
                if (cortes[i] <= cortes[i - 1]) {
                    Toast.makeText(actividad,
                            "Cada marcha debe ser mayor que la anterior",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Si el usuario editó manualmente y no coincide con el preset,
            // marcar como "Personalizado" para que se conserven sus valores
            int sel = spinnerMotor.getSelectedItemPosition();
            int motorAGuardar = sel;
            if (sel >= 0 && sel < PRESETS.length && !coincideConPreset(cortes, PRESETS[sel])) {
                motorAGuardar = INDICE_PERSONALIZADO;
            }

            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(PREF_MOTOR, motorAGuardar);
            for (int i = 0; i < 4; i++) {
                editor.putInt(PREF_CORTE_PREFIX + (i + 1), cortes[i]);
            }
            editor.apply();

            dialogo.dismiss();
            if (listener != null) listener.onCortesActualizados();
        });

        dialogo.show();
    }

    // ── Helpers ─────────────────────────────────────────────────

    private static void aplicarPreset(EditText[] inputs, int[] preset) {
        for (int i = 0; i < 4; i++) {
            inputs[i].setText(String.valueOf(preset[i]));
        }
    }

    private static boolean coincideConPreset(int[] valores, int[] preset) {
        for (int i = 0; i < 4; i++) {
            if (valores[i] != preset[i]) return false;
        }
        return true;
    }

    private static String textoMotor(int i) {
        if (i == INDICE_PERSONALIZADO) return "Personalizado";
        return "Motor " + MOTORES[i] + "L";
    }
}
