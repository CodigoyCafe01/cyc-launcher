package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import codigo.cafe.alsvinlaunch.R;

/**
 * Gestiona el tema visual (claro/oscuro) del launcher.
 */
public class ThemeManager {

    // ── Colores ──
    private static final int COLOR_FONDO_OSCURO = Color.parseColor("#1A1A2E");
    private static final int COLOR_FONDO_CLARO = Color.parseColor("#F0F2F5");
    private static final int COLOR_TEXTO_OSCURO = Color.parseColor("#FFFFFF");
    private static final int COLOR_TEXTO_CLARO = Color.parseColor("#1A1A2E");
    private static final int COLOR_SUBTEXTO_OSCURO = Color.parseColor("#B0B0B0");
    private static final int COLOR_SUBTEXTO_CLARO = Color.parseColor("#666666");

    private final Activity actividad;
    private final SharedPreferences prefs;
    private boolean esTemaOscuro;

    public ThemeManager(Activity actividad, SharedPreferences prefs) {
        this.actividad = actividad;
        this.prefs = prefs;
        this.esTemaOscuro = prefs.getBoolean("tema_oscuro", true);
    }

    public boolean esOscuro() {
        return esTemaOscuro;
    }

    public void alternar() {
        esTemaOscuro = !esTemaOscuro;
        prefs.edit().putBoolean("tema_oscuro", esTemaOscuro).apply();
    }

    /**
     * Aplica el tema actual a todas las vistas relevantes.
     *
     * @param raiz            Layout raíz
     * @param textoReloj      TextView del reloj
     * @param textoFecha      TextView de la fecha
     * @param botonTema       ImageView del botón de tema
     * @param vistasAccesos   Array de LinearLayouts de accesos directos
     * @param idsNombres      IDs de los TextViews de nombres de accesos
     * @param cantidadAccesos Número de accesos directos
     */
    public void aplicar(View raiz, TextView textoReloj, TextView textoFecha,
                         ImageView botonTema, LinearLayout[] vistasAccesos,
                         int[] idsNombres, int cantidadAccesos) {
        if (esTemaOscuro) {
            raiz.setBackgroundColor(COLOR_FONDO_OSCURO);
            textoReloj.setTextColor(COLOR_TEXTO_OSCURO);
            textoFecha.setTextColor(COLOR_SUBTEXTO_OSCURO);
            botonTema.setImageResource(R.drawable.ic_sun);
            for (int i = 0; i < cantidadAccesos; i++) {
                vistasAccesos[i].setBackgroundResource(R.drawable.shortcut_bg);
                ((TextView) actividad.findViewById(idsNombres[i])).setTextColor(COLOR_TEXTO_OSCURO);
            }
        } else {
            raiz.setBackgroundColor(COLOR_FONDO_CLARO);
            textoReloj.setTextColor(COLOR_TEXTO_CLARO);
            textoFecha.setTextColor(COLOR_SUBTEXTO_CLARO);
            botonTema.setImageResource(R.drawable.ic_moon);
            for (int i = 0; i < cantidadAccesos; i++) {
                vistasAccesos[i].setBackgroundResource(R.drawable.shortcut_bg_light);
                ((TextView) actividad.findViewById(idsNombres[i])).setTextColor(COLOR_TEXTO_CLARO);
            }
        }

        LinearLayout botonApps = (LinearLayout) actividad.findViewById(R.id.btn_all_apps);
        botonApps.setBackgroundResource(R.drawable.shortcut_bg_accent);
    }
}
