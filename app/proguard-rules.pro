# Mantener clases necesarias para Google Billing
-keep class com.android.billingclient.** { *; }

# Mantener modelos si usas JSON (evita crashes)
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Evitar eliminar constructores usados por reflexión
-keepattributes *Annotation*

# Ocultar logs en producción
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}