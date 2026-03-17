package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Deque;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.model.NotifModel;


/**
 * Maneja el panel de notificaciones BT en el layout del launcher.
 * Muestra hasta MAX_NOTIFS notificaciones recientes en una lista fija.
 */
public class NotifPanelManager {

    private static final int MAX_NOTIFS = 4;

    private final Activity activity;
  //  private final LinearLayout contenedor;
    private final Deque<NotifModel> historial = new ArrayDeque<>();

    public NotifPanelManager(Activity activity) {
        this.activity    = activity;
        //this.contenedor  = activity.findViewById(R.id.notif_panel_list);
    }

    /** Agregar una nueva notificación y actualizar la UI */
    public void agregarNotif(NotifModel notif) {
        historial.addFirst(notif);
        while (historial.size() > MAX_NOTIFS) historial.removeLast();
        renderizar();
    }

    /** Limpiar todas las notificaciones */
    public void limpiar() {
        historial.clear();
        renderizar();
    }

    // ─── Render ──────────────────────────────────────────────────────────────

    private void renderizar() {
      /*  if (contenedor == null) return;
        contenedor.removeAllViews();

        if (historial.isEmpty()) {
            contenedor.setVisibility(View.GONE);
            return;
        }

        contenedor.setVisibility(View.VISIBLE);

        for (NotifModel n : historial) {
            contenedor.addView(crearFila(n));
        }*/
    }

    private View crearFila(NotifModel n) {
        // Fila horizontal
        LinearLayout fila = new LinearLayout(activity);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        fila.setPadding(dp(10), dp(8), dp(10), dp(8));

        // Badge con inicial de la app
        TextView badge = new TextView(activity);
        badge.setText(n.appShortName().substring(0, 1).toUpperCase());
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(12);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(crearCirculo(appColor(n.appShortName())));
        LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(dp(32), dp(32));
        lpBadge.setMarginEnd(dp(8));
        badge.setLayoutParams(lpBadge);

        // Columna central: título + texto
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpCol = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(lpCol);

        TextView tvTitulo = new TextView(activity);
        tvTitulo.setText(n.title.isEmpty() ? n.appShortName() : n.title);
        tvTitulo.setTextColor(Color.WHITE);
        tvTitulo.setTextSize(13);
        tvTitulo.setMaxLines(1);
        tvTitulo.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView tvTexto = new TextView(activity);
        tvTexto.setText(n.text);
        tvTexto.setTextColor(Color.parseColor("#B0B0B0"));
        tvTexto.setTextSize(11);
        tvTexto.setMaxLines(1);
        tvTexto.setEllipsize(android.text.TextUtils.TruncateAt.END);

        col.addView(tvTitulo);
        col.addView(tvTexto);

        // Hora
        TextView tvHora = new TextView(activity);
        tvHora.setText(n.timeFormatted());
        tvHora.setTextColor(Color.parseColor("#606060"));
        tvHora.setTextSize(10);
        LinearLayout.LayoutParams lpHora = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpHora.setMarginStart(dp(6));
        tvHora.setLayoutParams(lpHora);

        fila.addView(badge);
        fila.addView(col);
        fila.addView(tvHora);

        // Separador
        View sep = new View(activity);
        sep.setBackgroundColor(Color.parseColor("#1AFFFFFF"));
        LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));

        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(fila);
        wrapper.addView(sep, lpSep);

        return wrapper;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private int dp(int val) {
        float density = activity.getResources().getDisplayMetrics().density;
        return (int) (val * density + 0.5f);
    }

    private android.graphics.drawable.GradientDrawable crearCirculo(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private int appColor(String name) {
        int[] colores = {
            Color.parseColor("#1565C0"), // azul
            Color.parseColor("#6A1B9A"), // violeta
            Color.parseColor("#00695C"), // teal
            Color.parseColor("#283593"), // indigo
            Color.parseColor("#00838F"), // cyan
        };
        return colores[Math.abs(name.hashCode()) % colores.length];
    }
}
