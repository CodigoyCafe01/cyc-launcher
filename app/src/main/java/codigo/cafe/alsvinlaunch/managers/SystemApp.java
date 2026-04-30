package codigo.cafe.alsvinlaunch.managers;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SystemApp {
    final String label;
    final String packageName;
    final String className;
    final int iconRes;
    final Map<String, String> extras;

    public SystemApp(String label, String packageName, String className, int iconRes) {
        this(label, packageName, className, iconRes, Collections.emptyMap());
    }

    public SystemApp(String label, String packageName, String className, int iconRes,
                     Map<String, String> extras) {
        this.label = label;
        this.packageName = packageName;
        this.className = className;
        this.iconRes = iconRes;
        this.extras = (extras == null) ? Collections.emptyMap() : extras;
    }

    public static Map<String, String> extra(String key, String value) {
        Map<String, String> m = new LinkedHashMap<>(1);
        m.put(key, value);
        return m;
    }
}
