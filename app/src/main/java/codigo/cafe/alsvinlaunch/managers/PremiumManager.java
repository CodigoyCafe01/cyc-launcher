package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Gestiona el período de prueba de 15 días y la compra única premium.
 *
 * Uso:
 *   premiumManager = new PremiumManager(this, prefs);
 *   premiumManager.verificar(acceso -> {
 *       if (!acceso) mostrarDialogPago();
 *   });
 */
public class PremiumManager {

    private static final String TAG = "PremiumManager";

    // ── Claves SharedPreferences ──
    private static final String PREF_FECHA_INSTALL   = "premium_install_date";
    private static final String PREF_ES_PREMIUM      = "premium_purchased";

    // ── Producto en Google Play Console ──
    public static final String PRODUCT_ID = "premium_cyclauncher";

    // ── Días de prueba gratuita ──
    private static final int DIAS_TRIAL = 15;

    // ── Año mínimo válido para la fecha del dispositivo ──
    private static final int ANO_MINIMO = 2026;

    private final Activity        activity;
    private final SharedPreferences prefs;
    private       BillingClient   billingClient;
    private       ProductDetails  productoDetails;

    public interface OnAccesoListener {
        /** @param tieneAcceso true = puede usar la app (trial o premium) */
        void onResultado(boolean tieneAcceso);
    }

    public interface OnCompraListener {
        void onCompraExitosa();
        void onCompraError(String mensaje);
    }

    // ══════════════════════════════════════════════════════
    //  Constructor
    // ══════════════════════════════════════════════════════

    public PremiumManager(Activity activity, SharedPreferences prefs) {
        this.activity = activity;
        this.prefs    = prefs;
        registrarFechaInstalacionSiNecesario();
        inicializarBilling();
    }

    // ══════════════════════════════════════════════════════
    //  API pública
    // ══════════════════════════════════════════════════════

    /**
     * Verifica si el usuario tiene acceso (trial vigente o ya pagó).
     * Siempre llama al listener en el hilo principal.
     */
    public void verificar(OnAccesoListener listener) {
        if (listener == null) return;

        // 1. Si ya pagó → acceso inmediato
        if (prefs.getBoolean(PREF_ES_PREMIUM, false)) {
            listener.onResultado(true);
            return;
        }

        // 2. Validar que la fecha del dispositivo sea confiable
        if (!fechaDispositivoEsValida()) {
            // Fecha sospechosa (antes de 2026) → bloqueamos acceso
            listener.onResultado(false);
            return;
        }

        // 3. Verificar si el trial sigue activo
        long fechaInstall = prefs.getLong(PREF_FECHA_INSTALL, 0L);
        long diasTranscurridos = TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - fechaInstall);

        boolean trialActivo = diasTranscurridos < DIAS_TRIAL;
        listener.onResultado(trialActivo);
    }

    /**
     * Devuelve los días restantes de trial (0 si ya expiró o si ya es premium).
     */
    public int diasRestantesTrial() {
        if (prefs.getBoolean(PREF_ES_PREMIUM, false)) return 0;
        if (!fechaDispositivoEsValida()) return 0;

        long fechaInstall = prefs.getLong(PREF_FECHA_INSTALL, 0L);
        long diasTranscurridos = TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - fechaInstall);

        int restantes = (int) (DIAS_TRIAL - diasTranscurridos);
        return Math.max(restantes, 0);
    }

    /** ¿Ya compró el premium? */
    public boolean esPremium() {
        return prefs.getBoolean(PREF_ES_PREMIUM, false);
    }

    /**
     * Lanza el flujo de compra de Google Play.
     * Requiere que el BillingClient esté conectado (se conecta solo en el constructor).
     */
    public void iniciarCompra(OnCompraListener listener) {
        if (productoDetails == null) {
            // Intentar reconectar y consultar producto
            consultarProducto(() -> lanzarFlujoCompra(listener));
            return;
        }
        lanzarFlujoCompra(listener);
    }

    /**
     * Llama esto desde onResume() de tu Activity para verificar compras pendientes.
     */
    public void verificarComprasPendientes(OnCompraListener listener) {
        if (billingClient == null || !billingClient.isReady()) return;

        billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                (billingResult, purchases) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        for (Purchase purchase : purchases) {
                            procesarCompra(purchase, listener);
                        }
                    }
                }
        );
    }

    /** Libera recursos — llamar desde onDestroy() */
    public void destruir() {
        if (billingClient != null) {
            billingClient.endConnection();
            billingClient = null;
        }
    }

    // ══════════════════════════════════════════════════════
    //  Lógica interna
    // ══════════════════════════════════════════════════════

    private void registrarFechaInstalacionSiNecesario() {
        if (!prefs.contains(PREF_FECHA_INSTALL)) {
            prefs.edit()
                    .putLong(PREF_FECHA_INSTALL, System.currentTimeMillis())
                    .apply();
        }
    }

    /**
     * Valida que el reloj del dispositivo sea >= 1 enero 2026.
     * Previene que el usuario retrase la fecha para evadir el trial.
     */
    private boolean fechaDispositivoEsValida() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR) >= ANO_MINIMO;
    }

    private void inicializarBilling() {
        billingClient = BillingClient.newBuilder(activity)
                .setListener((billingResult, purchases) -> {
                    // Listener de compras en tiempo real
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                            && purchases != null) {
                        for (Purchase purchase : purchases) {
                            procesarCompra(purchase, null);
                        }
                    }
                })
                .enablePendingPurchases()
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    consultarProducto(null);
                } else {
                    Log.w(TAG, "Billing setup failed: " + result.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected");
                billingClient.startConnection(this); // reconectar
            }
        });
    }

    private void consultarProducto(Runnable onListo) {
        List<QueryProductDetailsParams.Product> productos = Collections.singletonList(
                QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
        );

        billingClient.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder()
                        .setProductList(productos)
                        .build(),
                (billingResult, productDetailsList) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                            && productDetailsList != null
                            && !productDetailsList.isEmpty()) {
                        productoDetails = productDetailsList.get(0);
                        if (onListo != null) activity.runOnUiThread(onListo);
                    } else {
                        Log.w(TAG, "No se encontró el producto: " + billingResult.getDebugMessage());
                    }
                }
        );
    }

    private void lanzarFlujoCompra(OnCompraListener listener) {
        if (productoDetails == null) {
            if (listener != null) listener.onCompraError("Producto no disponible. Intenta más tarde.");
            return;
        }

        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productoDetails)
                                .build()
                ))
                .build();

        BillingResult result = billingClient.launchBillingFlow(activity, params);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            if (listener != null) listener.onCompraError("Error al abrir tienda: " + result.getDebugMessage());
        }
    }

    private void procesarCompra(Purchase purchase, OnCompraListener listener) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;

        // Guardar premium localmente
        prefs.edit().putBoolean(PREF_ES_PREMIUM, true).apply();

        // Acknowledger la compra (obligatorio en 3 días o Google la revierte)
        if (!purchase.isAcknowledged()) {
            AcknowledgePurchaseParams ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();
            billingClient.acknowledgePurchase(ackParams, billingResult -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Compra acknowledged OK");
                    if (listener != null) {
                        activity.runOnUiThread(listener::onCompraExitosa);
                    }
                }
            });
        } else {
            if (listener != null) {
                activity.runOnUiThread(listener::onCompraExitosa);
            }
        }
    }
}