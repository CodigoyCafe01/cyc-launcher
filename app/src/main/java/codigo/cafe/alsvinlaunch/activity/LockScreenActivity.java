package codigo.cafe.alsvinlaunch.activity;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import codigo.cafe.alsvinlaunch.R;

public class LockScreenActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView clockText;
    private TextView dateText;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            actualizarReloj();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Pantalla encendida, sobre la lock-screen del sistema
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        setContentView(R.layout.activity_lock_screen);

        clockText = findViewById(R.id.lock_clock);
        dateText  = findViewById(R.id.lock_date);

        // Toca en cualquier lugar → cierra y vuelve al launcher
        findViewById(R.id.lock_root).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        actualizarReloj();
        handler.post(tickRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tickRunnable);
    }

    private void actualizarReloj() {
        Date now = new Date();
        clockText.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        dateText.setText(new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(now));
    }
}