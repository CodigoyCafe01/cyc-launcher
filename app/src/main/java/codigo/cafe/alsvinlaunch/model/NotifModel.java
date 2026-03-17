package codigo.cafe.alsvinlaunch.model;

import org.json.JSONObject;

/**
 * Modelo de una notificación recibida vía Bluetooth desde Notisvin.
 * JSON: { packageName, title, text, timestamp }
 */
public class NotifModel {

    public final String packageName;
    public final String title;
    public final String text;
    public final long   timestamp;

    public NotifModel(String packageName, String title, String text, long timestamp) {
        this.packageName = packageName;
        this.title       = title;
        this.text        = text;
        this.timestamp   = timestamp;
    }

    /** Parsear desde JSON string. Retorna null si hay error. */
    public static NotifModel fromJson(String json) {
        try {
            JSONObject o = new JSONObject(json);
            return new NotifModel(
                o.optString("packageName", ""),
                o.optString("title",       ""),
                o.optString("text",        ""),
                o.optLong  ("timestamp",    System.currentTimeMillis())
            );
        } catch (Exception e) {
            return null;
        }
    }

    /** Nombre corto de la app (última parte del packageName) */
    public String appShortName() {
        if (packageName.contains(".")) {
            return packageName.substring(packageName.lastIndexOf('.') + 1);
        }
        return packageName;
    }

    /** Hora formateada HH:mm */
    public String timeFormatted() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        return String.format("%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY),
                                          c.get(java.util.Calendar.MINUTE));
    }
}
