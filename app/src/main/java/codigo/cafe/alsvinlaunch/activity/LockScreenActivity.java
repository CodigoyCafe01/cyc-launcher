package codigo.cafe.alsvinlaunch.activity;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.managers.ClockManager;
import codigo.cafe.alsvinlaunch.util.LocaleHelper;

public class LockScreenActivity extends Activity {

    private ClockManager clockManager;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        setContentView(R.layout.activity_lock_screen);

        TextView clockText = findViewById(R.id.lock_clock);
        TextView dateText  = findViewById(R.id.lock_date);

        clockManager = new ClockManager(this, clockText, dateText);

        GestureDetector gestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        finish();
                        return true;
                    }
                });

        View root = findViewById(R.id.lock_root);
        root.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }

    @Override
    protected void onResume() {
        super.onResume();
        clockManager.iniciar();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clockManager.detener();
    }
}
