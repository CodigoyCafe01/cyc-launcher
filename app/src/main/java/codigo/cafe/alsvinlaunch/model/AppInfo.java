package codigo.cafe.alsvinlaunch.model;

import android.graphics.drawable.Drawable;

public class AppInfo {
    private String label;
    private String displayLabel; // label con contexto para mostrar en listas (ej. "Nombre · App")
    private String packageName;
    private String className;
    private Drawable icon;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** Devuelve displayLabel si está definido, si no cae al label normal. */
    public String getDisplayLabel() {
        return (displayLabel != null && !displayLabel.isEmpty()) ? displayLabel : label;
    }

    public void setDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }
}