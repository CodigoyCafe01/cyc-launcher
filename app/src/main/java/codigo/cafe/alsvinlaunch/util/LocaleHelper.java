package codigo.cafe.alsvinlaunch.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

/**
 * Gestiona el idioma de la aplicación de forma persistente.
 *
 * El idioma elegido se guarda en las mismas preferencias del launcher y se
 * aplica envolviendo el {@link Context} base de cada Activity mediante
 * {@link #onAttach(Context)} (llamado desde attachBaseContext). Así el cambio
 * afecta a todos los recursos (strings.xml) sin depender del idioma del sistema.
 */
public final class LocaleHelper {

    private static final String NOMBRE_PREFS = "alsvin_launcher_prefs";
    private static final String PREF_IDIOMA  = "app_idioma";

    /** Idiomas soportados por el selector. */
    public static final String ESPANOL = "es";
    public static final String INGLES  = "en";

    private LocaleHelper() { }

    /**
     * Devuelve el código de idioma guardado. Si no hay ninguno, usa el del
     * sistema cuando es uno de los soportados; en caso contrario, español.
     */
    public static String getIdiomaGuardado(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE);
        String guardado = prefs.getString(PREF_IDIOMA, null);
        if (guardado != null) return guardado;

        String sistema = Locale.getDefault().getLanguage();
        return INGLES.equals(sistema) ? INGLES : ESPANOL;
    }

    /** Persiste el idioma elegido. */
    public static void guardarIdioma(Context context, String idioma) {
        context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_IDIOMA, idioma)
                .apply();
    }

    /**
     * Envuelve el contexto con el idioma guardado. Llamar desde
     * attachBaseContext(Context) de cada Activity:
     * {@code super.attachBaseContext(LocaleHelper.onAttach(base));}
     */
    public static Context onAttach(Context context) {
        return aplicar(context, getIdiomaGuardado(context));
    }

    /** Aplica un idioma concreto a un contexto y devuelve el contexto resultante. */
    public static Context aplicar(Context context, String idioma) {
        Locale locale = new Locale(idioma);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
            return context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
            return context;
        }
    }
}
