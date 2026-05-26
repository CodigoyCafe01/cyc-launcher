package codigo.cafe.alsvinlaunch.managers;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

public class TourOverlayView extends View {

    private final Paint overlayPaint;
    private final Paint clearPaint;
    private final Paint ringPaint;

    private float spotX, spotY, spotRadius;
    private float pulseOffset = 0f;
    private ValueAnimator pulseAnimator;

    public TourOverlayView(Context context) {
        super(context);
        // LAYER_TYPE_SOFTWARE is required for PorterDuff.Mode.CLEAR to work
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        overlayPaint = new Paint();
        overlayPaint.setColor(Color.argb(215, 6, 6, 18));

        clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setColor(Color.argb(160, 108, 99, 255));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2.5f);
    }

    public void setTarget(float cx, float cy, float radius) {
        spotX = cx;
        spotY = cy;
        spotRadius = radius;
        pulseOffset = 0f;
        startPulse();
        invalidate();
    }

    private void startPulse() {
        if (pulseAnimator != null) pulseAnimator.cancel();
        pulseAnimator = ValueAnimator.ofFloat(0f, 14f);
        pulseAnimator.setDuration(900);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addUpdateListener(a -> {
            pulseOffset = (float) a.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
    }

    public void dismiss() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);
        // Pulsating ring sits outside the cleared circle
        canvas.drawCircle(spotX, spotY, spotRadius + pulseOffset + 7, ringPaint);
        // Punch a transparent hole so the target view shows through
        canvas.drawCircle(spotX, spotY, spotRadius, clearPaint);
    }
}
