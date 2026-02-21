package codigo.cafe.alsvinlaunch.managers;

public class SystemApp {
    String label;
    String packageName;
    String className;
    int iconRes;

    public SystemApp(String label, String packageName, String className, int iconRes) {
        this.label = label;
        this.packageName = packageName;
        this.className = className;
        this.iconRes = iconRes;
    }
}