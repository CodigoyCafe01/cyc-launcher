package codigo.cafe.alsvinlaunch.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * SpeedometerView
 *
 * View personalizado que dibuja:
 *  - Arco de fondo (pista gris oscuro)
 *  - Arco de progreso animado (azul) que se llena según la velocidad
 *  - Número de velocidad en el centro
 *  - Etiqueta "km/h" debajo del número
 *
 * Uso en XML:
 *   <codigo.cafe.alsvinlaunch.views.SpeedometerView
 *       android:id="@+id/speedometer_view"
 *       android:layout_width="140dp"
 *       android:layout_height="140dp" />
 *
 * Uso en código:
 *   speedometerView.setVelocidad(85, 120);  // velocidad actual, máximo
 */
public class SpeedometerView extends View {

    // ── Colores ────────────────────────────────────────────────────
    private static final int COLOR_PISTA    = 0xFF1E2140;  // gris oscuro
    private static final int COLOR_ARCO     = 0xFF4A50D0;  // azul
    private static final int COLOR_NUMERO   = 0xFFFFFFFF;  // blanco
    private static final int COLOR_ETIQUETA = 0xFF5A6080;  // gris claro

    // ── Ángulos del arco (0° = derecha, 90° = abajo) ───────────────
    // Arco de 240° centrado abajo: empieza en 150° y termina en 390° (=30°)
    private static final float ANGULO_INICIO = 150f;
    private static final float ANGULO_TOTAL  = 240f;

    // ── Paints ─────────────────────────────────────────────────────
    private final Paint paintPista   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintArco    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintNumero  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintEtiq    = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Estado ─────────────────────────────────────────────────────
    private float velocidadActual  = 0f;   // valor real (para el texto)
    private float progresoAnimado  = 0f;   // 0.0 – 1.0 (para el arco)
    private int   velocidadMaxima  = 120;

    private ValueAnimator animador;
    private final RectF rectArco = new RectF();

    // ── Constructores ──────────────────────────────────────────────

    public SpeedometerView(Context context) {
        super(context);
        init();
    }

    public SpeedometerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpeedometerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Pista de fondo
        paintPista.setStyle(Paint.Style.STROKE);
        paintPista.setColor(COLOR_PISTA);
        paintPista.setStrokeCap(Paint.Cap.ROUND);

        // Arco de progreso
        paintArco.setStyle(Paint.Style.STROKE);
        paintArco.setColor(COLOR_ARCO);
        paintArco.setStrokeCap(Paint.Cap.ROUND);

        // Número grande
        paintNumero.setColor(COLOR_NUMERO);
        paintNumero.setTextAlign(Paint.Align.CENTER);
        paintNumero.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));

        // Etiqueta km/h
        paintEtiq.setColor(COLOR_ETIQUETA);
        paintEtiq.setTextAlign(Paint.Align.CENTER);
        paintEtiq.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    // ── API pública ────────────────────────────────────────────────

    /**
     * Actualiza la velocidad con animación suave del arco.
     *
     * @param kmh      velocidad actual en km/h
     * @param maxKmh   velocidad máxima de la escala (ej: 120)
     */
    public void setVelocidad(int kmh, int maxKmh) {
        this.velocidadActual = kmh;
        this.velocidadMaxima = maxKmh;

        float targetProgreso = Math.min(1f, (float) kmh / maxKmh);
        float desde = progresoAnimado;

        if (animador != null) animador.cancel();

        animador = ValueAnimator.ofFloat(desde, targetProgreso);
        animador.setDuration(600);
        animador.setInterpolator(new DecelerateInterpolator());
        animador.addUpdateListener(anim -> {
            progresoAnimado = (float) anim.getAnimatedValue();
            invalidate();
        });
        animador.start();
    }

    /** Resetea a cero sin animación (cuando se oculta el widget). */
    public void resetear() {
        if (animador != null) animador.cancel();
        progresoAnimado = 0f;
        velocidadActual = 0f;
        invalidate();
    }

    // ── Dibujo ─────────────────────────────────────────────────────

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float grosor = w * 0.08f;  // ~8% del ancho como grosor del arco

        paintPista.setStrokeWidth(grosor);
        paintArco.setStrokeWidth(grosor);

        float margen = grosor / 2f + 4f;
        rectArco.set(margen, margen, w - margen, h - margen);

        // Número: ~38% del ancho
        paintNumero.setTextSize(w * 0.30f);
        // Etiqueta: ~14% del ancho
        paintEtiq.setTextSize(w * 0.13f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth()  / 2f;
        float cy = getHeight() / 2f;

        // 1. Pista de fondo (arco completo)
        canvas.drawArc(rectArco, ANGULO_INICIO, ANGULO_TOTAL, false, paintPista);

        // 2. Arco de progreso animado
        if (progresoAnimado > 0f) {
            float sweep = ANGULO_TOTAL * progresoAnimado;
            canvas.drawArc(rectArco, ANGULO_INICIO, sweep, false, paintArco);
        }

        // 3. Número de velocidad centrado verticalmente
        //    baseline = centro + la mitad de la altura del número
        float offsetNumero = (paintNumero.descent() + paintNumero.ascent()) / 2f;
        String textoNum = velocidadActual == 0f ? "--" : String.valueOf((int) velocidadActual);
        canvas.drawText(textoNum, cx, cy - offsetNumero - getHeight() * 0.04f, paintNumero);

        // 4. Etiqueta "km/h" debajo del número
        canvas.drawText("km/h", cx, cy - offsetNumero + getHeight() * 0.20f, paintEtiq);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animador != null) animador.cancel();
    }
}