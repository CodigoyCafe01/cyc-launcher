package codigo.cafe.alsvinlaunch.managers;

import android.content.Context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SystemApp {
    /** Recurso de string con el nombre traducible de la app. */
    final int labelRes;
    final String packageName;
    final String className;
    final int iconRes;
    final Map<String, String> extras;

    public SystemApp(int labelRes, String packageName, String className, int iconRes) {
        this(labelRes, packageName, className, iconRes, Collections.emptyMap());
    }

    public SystemApp(int labelRes, String packageName, String className, int iconRes,
                     Map<String, String> extras) {
        this.labelRes = labelRes;
        this.packageName = packageName;
        this.className = className;
        this.iconRes = iconRes;
        this.extras = (extras == null) ? Collections.emptyMap() : extras;
    }

    /** Nombre localizado según el idioma actual del contexto. */
    public String getLabel(Context context) {
        return context.getString(labelRes);
    }

    public static Map<String, String> extra(String key, String value) {
        Map<String, String> m = new LinkedHashMap<>(1);
        m.put(key, value);
        return m;
    }
}
