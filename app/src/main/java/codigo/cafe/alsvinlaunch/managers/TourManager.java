package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import codigo.cafe.alsvinlaunch.R;

/**
 * Guided tour shown on first launch. Highlights each UI element with an
 * animated spotlight and an explanatory tooltip card.
 *
 * Entry points:
 *   TourManager.showIfNeeded(activity, root) — first launch only
 *   TourManager.show(activity, root)         — force-show (e.g. from help gesture)
 */
public class TourManager {

    private static final String PREFS_NAME   = "alsvin_launcher_prefs";
    private static final String PREF_TOUR_DONE = "tour_done_v1";

    private final Activity      activity;
    private final FrameLayout   rootLayout;
    private TourOverlayView     overlayView;
    private FrameLayout         tooltipCard;
    private TextView            tvTitle, tvDesc, tvCounter;
    private TextView            btnPrev, btnNext, btnSkip;
    private int                 currentStep = 0;

    private final TourStep[] steps;

    // ── Inner data class ─────────────────────────────────────────────────────

    private static class TourStep {
        final int    viewId;
        final String title;
        final String description;

        TourStep(int viewId, String title, String description) {
            this.viewId      = viewId;
            this.title       = title;
            this.description = description;
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public static void showIfNeeded(Activity activity, FrameLayout root) {
        SharedPreferences prefs =
                activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(PREF_TOUR_DONE, false)) {
            new TourManager(activity, root).start();
        }
    }

    public static void show(Activity activity, FrameLayout root) {
        new TourManager(activity, root).start();
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    private TourManager(Activity activity, FrameLayout root) {
        this.activity   = activity;
        this.rootLayout = root;
        this.steps      = buildSteps();
    }

    private TourStep[] buildSteps() {
        return new TourStep[]{
            new TourStep(R.id.shortcut_0,
                "Accesos directos",
                "Toca para abrir la app asignada. Mantén presionado para cambiar qué app aparece en cada acceso directo."),
            new TourStep(R.id.btn_all_apps,
                "Todas las apps",
                "Explora y abre cualquier app instalada en el headunit. Mantén presionado aquí para volver a ver este tour."),
            new TourStep(R.id.boton_reloj,
                "Reloj",
                "Toca para ir a la pantalla de bloqueo. Mantén presionado para abrir los ajustes de fecha y hora."),
            new TourStep(R.id.speedometer_view,
                "Velocímetro GPS",
                "Muestra tu velocidad actual en km/h usando el GPS del dispositivo. Requiere permiso de ubicación."),
            new TourStep(R.id.gear_widget,
                "Indicador de marcha",
                "Muestra la marcha estimada según tu velocidad. Toca para configurar los rangos de velocidad de cada marcha."),
            new TourStep(R.id.widget_reproductor,
                "Reproductor de audio",
                "Controla música, radio o cualquier audio sin salir del launcher. Mantén presionado para activar el color dinámico de fondo."),
            new TourStep(R.id.indicador_wifi,
                "Estado WiFi",
                "Muestra la red WiFi conectada en tiempo real. Toca para abrir la configuración de red."),
            new TourStep(R.id.indicador_bluetooth,
                "Bluetooth",
                "Muestra el dispositivo vinculado. Toca para abrir la configuración de Bluetooth."),
            new TourStep(R.id.btn_theme_toggle,
                "Bloqueo y Modo Kiosk",
                "Toca para bloquear la pantalla. Mantén presionado para activar el Modo Kiosk — el launcher regresará automáticamente tras 30 s en otra app."),
        };
    }

    // ── Core ─────────────────────────────────────────────────────────────────

    private void start() {
        overlayView = new TourOverlayView(activity);
        overlayView.setClickable(true); // swallow touches so the background UI is inactive
        rootLayout.addView(overlayView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        buildTooltipCard();
        showStep(0);
    }

    private void buildTooltipCard() {
        float d = activity.getResources().getDisplayMetrics().density;
        int cardWidthPx = (int) (340 * d);

        tooltipCard = new FrameLayout(activity);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.argb(242, 14, 14, 38));
        cardBg.setCornerRadius(12 * d);
        cardBg.setStroke((int) d, Color.argb(90, 108, 99, 255));
        tooltipCard.setBackground(cardBg);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int)(16*d), (int)(14*d), (int)(16*d), (int)(12*d));

        // ── Title row ──
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        tvTitle = new TextView(activity);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(15);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tvCounter = new TextView(activity);
        tvCounter.setTextColor(Color.argb(130, 155, 155, 195));
        tvCounter.setTextSize(11);
        tvCounter.setPadding((int)(8*d), 0, 0, 0);

        headerRow.addView(tvTitle);
        headerRow.addView(tvCounter);

        // ── Description ──
        tvDesc = new TextView(activity);
        tvDesc.setTextColor(Color.argb(210, 160, 160, 190));
        tvDesc.setTextSize(13);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.topMargin    = (int)(8*d);
        descParams.bottomMargin = (int)(12*d);
        tvDesc.setLayoutParams(descParams);
        tvDesc.setLineSpacing(0, 1.3f);

        // ── Divider ──
        View divider = new View(activity);
        divider.setBackgroundColor(Color.argb(45, 108, 99, 255));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divParams.bottomMargin = (int)(10*d);
        divider.setLayoutParams(divParams);

        // ── Button row ──
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);

        btnSkip = makeButton("Saltar", Color.argb(130, 145, 145, 165), d);
        btnSkip.setOnClickListener(v -> finish());

        View spacer = new View(activity);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));

        btnPrev = makeButton("‹ Anterior", Color.argb(200, 108, 99, 255), d);
        btnPrev.setVisibility(View.INVISIBLE);
        btnPrev.setOnClickListener(v -> { if (currentStep > 0) showStep(currentStep - 1); });

        btnNext = makeButton("Siguiente ›", Color.WHITE, d);
        btnNext.setTypeface(null, Typeface.BOLD);
        btnNext.setOnClickListener(v -> {
            if (currentStep < steps.length - 1) showStep(currentStep + 1);
            else finish();
        });

        btnRow.addView(btnSkip);
        btnRow.addView(spacer);
        btnRow.addView(btnPrev);
        btnRow.addView(btnNext);

        content.addView(headerRow);
        content.addView(tvDesc);
        content.addView(divider);
        content.addView(btnRow);
        tooltipCard.addView(content);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                cardWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        rootLayout.addView(tooltipCard, cardParams);
    }

    private TextView makeButton(String text, int textColor, float d) {
        TypedValue outValue = new TypedValue();
        boolean hasRipple = activity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, outValue, true);

        TextView btn = new TextView(activity);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(12);
        btn.setPadding((int)(10*d), (int)(8*d), (int)(10*d), (int)(8*d));
        btn.setClickable(true);
        btn.setFocusable(true);
        if (hasRipple) btn.setBackgroundResource(outValue.resourceId);
        return btn;
    }

    // ── Step navigation ──────────────────────────────────────────────────────

    private void showStep(int index) {
        // Skip steps whose target view is absent or not visible
        while (index < steps.length) {
            View t = activity.findViewById(steps[index].viewId);
            if (t != null && t.getVisibility() == View.VISIBLE) break;
            index++;
        }
        if (index >= steps.length) { finish(); return; }

        currentStep = index;
        TourStep step = steps[index];
        View target = activity.findViewById(step.viewId);

        tvTitle.setText(step.title);
        tvDesc.setText(step.description);
        tvCounter.setText((index + 1) + " / " + steps.length);
        btnPrev.setVisibility(index > 0 ? View.VISIBLE : View.INVISIBLE);
        btnNext.setText(index == steps.length - 1 ? "Finalizar" : "Siguiente ›");
        btnSkip.setVisibility(index < steps.length - 1 ? View.VISIBLE : View.GONE);

        // Wait for the card to be laid out before computing its position
        tooltipCard.post(() -> {
            if (target.getParent() == null) return;

            int[] rootLoc = new int[2];
            rootLayout.getLocationOnScreen(rootLoc);
            int[] targetLoc = new int[2];
            target.getLocationOnScreen(targetLoc);

            float d  = activity.getResources().getDisplayMetrics().density;
            float cx = (targetLoc[0] - rootLoc[0]) + target.getWidth()  / 2f;
            float cy = (targetLoc[1] - rootLoc[1]) + target.getHeight() / 2f;
            float r  = Math.max(target.getWidth(), target.getHeight()) / 2f + 16 * d;

            overlayView.setTarget(cx, cy, r);
            positionTooltip(cx, cy, r);
        });
    }

    private void positionTooltip(float spotCx, float spotCy, float spotR) {
        float d       = activity.getResources().getDisplayMetrics().density;
        float margin  = 16 * d;
        int   screenH = rootLayout.getHeight();
        int   screenW = rootLayout.getWidth();
        int   cardW   = tooltipCard.getWidth();
        int   cardH   = tooltipCard.getHeight();

        // Prefer tooltip below the spotlight; fall back to above
        float top;
        if (spotCy + spotR + margin + cardH <= screenH) {
            top = spotCy + spotR + margin;
        } else {
            top = spotCy - spotR - margin - cardH;
        }
        top = Math.max(margin, Math.min(top, screenH - cardH - margin));

        float left = spotCx - cardW / 2f;
        left = Math.max(margin, Math.min(left, screenW - cardW - margin));

        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) tooltipCard.getLayoutParams();
        p.gravity    = Gravity.TOP | Gravity.START;
        p.leftMargin = (int) left;
        p.topMargin  = (int) top;
        tooltipCard.setLayoutParams(p);
    }

    // ── Finish ───────────────────────────────────────────────────────────────

    private void finish() {
        overlayView.dismiss();
        if (overlayView.getParent() != null) rootLayout.removeView(overlayView);
        if (tooltipCard  .getParent() != null) rootLayout.removeView(tooltipCard);
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_TOUR_DONE, true).apply();
    }
}
