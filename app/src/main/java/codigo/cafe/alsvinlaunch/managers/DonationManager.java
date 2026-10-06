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
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * Donación opcional vía Google Play Billing.
 * Si el usuario dona, el flag se persiste y el botón de la UI se oculta.
 */
public class DonationManager {

    private static final String TAG = "DonationManager";

    // Reutilizamos la clave del antiguo flujo "premium" para no perder
    // el estado de quienes ya pagaron en versiones anteriores.
    private static final String PREF_HA_DONADO = "premium_purchased";

    public static final String PRODUCT_ID = "premium_cyclauncher";

    private final Activity        activity;
    private final SharedPreferences prefs;
    private       BillingClient   billingClient;
    private       ProductDetails  productoDetails;

    public interface OnDonacionListener {
        void onDonacionExitosa();
        void onDonacionError(String mensaje);
    }

    public DonationManager(Activity activity, SharedPreferences prefs) {
        this.activity = activity;
        this.prefs    = prefs;
        inicializarBilling();
    }

    /** ¿El usuario ya donó? */
    public boolean haDonado() {
        return prefs.getBoolean(PREF_HA_DONADO, false);
    }

    /** Lanza el flujo de donación de Google Play. */
    public void iniciarDonacion(OnDonacionListener listener) {
        if (productoDetails == null) {
            consultarProducto(() -> lanzarFlujoCompra(listener));
            return;
        }
        lanzarFlujoCompra(listener);
    }

    /** Verifica compras pendientes/restauradas. Llamar desde onResume(). */
    public void verificarComprasPendientes(OnDonacionListener listener) {
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

    private void inicializarBilling() {
        billingClient = BillingClient.newBuilder(activity)
                .setListener((billingResult, purchases) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                            && purchases != null) {
                        for (Purchase purchase : purchases) {
                            procesarCompra(purchase, null);
                        }
                    }
                })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build())
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
                billingClient.startConnection(this);
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
                (billingResult, queryResult) -> {
                    List<ProductDetails> productDetailsList = queryResult.getProductDetailsList();
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

    private void lanzarFlujoCompra(OnDonacionListener listener) {
        if (productoDetails == null) {
            if (listener != null) listener.onDonacionError("Producto no disponible. Intenta más tarde.");
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
            if (listener != null) listener.onDonacionError("Error al abrir tienda: " + result.getDebugMessage());
        }
    }

    private void procesarCompra(Purchase purchase, OnDonacionListener listener) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;

        prefs.edit().putBoolean(PREF_HA_DONADO, true).apply();

        // Acknowledger la compra (obligatorio en 3 días o Google la revierte)
        if (!purchase.isAcknowledged()) {
            AcknowledgePurchaseParams ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();
            billingClient.acknowledgePurchase(ackParams, billingResult -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Compra acknowledged OK");
                    if (listener != null) {
                        activity.runOnUiThread(listener::onDonacionExitosa);
                    }
                }
            });
        } else {
            if (listener != null) {
                activity.runOnUiThread(listener::onDonacionExitosa);
            }
        }
    }
}
