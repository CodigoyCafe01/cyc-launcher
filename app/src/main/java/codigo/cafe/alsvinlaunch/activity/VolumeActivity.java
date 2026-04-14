package codigo.cafe.alsvinlaunch.activity;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

import codigo.cafe.alsvinlaunch.R;

/**
 * VolumeActivity – Iguala y controla todos los streams de audio del sistema.
 * Streams: MUSIC (YT Music, apps), BLUETOOTH, RING, ALARM, NOTIFICATION, SYSTEM
 */
public class VolumeActivity extends Activity {

    private AudioManager audioManager;

    // Streams que vamos a controlar
    private static final int[] STREAMS = {
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM
    };

    // IDs de los SeekBars en el layout
    private static final int[] SEEKBAR_IDS = {
            R.id.seekbar_music,
            R.id.seekbar_bluetooth,
            R.id.seekbar_ring,
            R.id.seekbar_alarm,
            R.id.seekbar_notification,
            R.id.seekbar_system
    };

    // IDs de los TextViews con el valor actual
    private static final int[] VALUE_IDS = {
            R.id.value_music,
            R.id.value_bluetooth,
            R.id.value_ring,
            R.id.value_alarm,
            R.id.value_notification,
            R.id.value_system
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volume);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        inicializarSliders();
        configurarBotonIgualar();
        configurarBotonCerrar();
        configurarBotonMaximo();
    }

    /**
     * Conecta cada SeekBar con su stream de audio
     */
    private void inicializarSliders() {
        for (int i = 0; i < STREAMS.length; i++) {
            final int stream = STREAMS[i];
            final int idx = i;

            SeekBar seekBar = (SeekBar) findViewById(SEEKBAR_IDS[i]);
            final TextView valueText = (TextView) findViewById(VALUE_IDS[i]);

            if (seekBar == null) continue;

            int maxVol = audioManager.getStreamMaxVolume(stream);
            int curVol = audioManager.getStreamVolume(stream);

            seekBar.setMax(maxVol);
            seekBar.setProgress(curVol);
            if (valueText != null) valueText.setText(curVol + "/" + maxVol);

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        audioManager.setStreamVolume(stream, progress, 0);
                        int max = audioManager.getStreamMaxVolume(stream);
                        if (valueText != null) valueText.setText(progress + "/" + max);
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    /**
     * Iguala todos los streams al volumen de STREAM_MUSIC (proporcional)
     */
    private void configurarBotonIgualar() {
        View btnIgualar = findViewById(R.id.btn_igualar);
        if (btnIgualar == null) return;

        btnIgualar.setOnClickListener(v -> {
            int musicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int musicVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            float proporcion = (musicMax > 0) ? (float) musicVol / musicMax : 1f;

            for (int i = 0; i < STREAMS.length; i++) {
                int stream = STREAMS[i];
                int maxVol = audioManager.getStreamMaxVolume(stream);
                int targetVol = Math.round(proporcion * maxVol);
                audioManager.setStreamVolume(stream, targetVol, 0);
            }

            // Refrescar sliders
            refrescarSliders();
        });
    }

    /**
     * Sube todos los streams al máximo
     */
    private void configurarBotonMaximo() {
        View btnMax = findViewById(R.id.btn_maximo);
        if (btnMax == null) return;

        btnMax.setOnClickListener(v -> {
            for (int stream : STREAMS) {
                int maxVol = audioManager.getStreamMaxVolume(stream);
                audioManager.setStreamVolume(stream, maxVol, 0);
            }
            refrescarSliders();
        });
    }

    private void configurarBotonCerrar() {
        View btnCerrar = findViewById(R.id.btn_cerrar);
        if (btnCerrar == null) return;
        btnCerrar.setOnClickListener(v -> finish());
    }

    /**
     * Re-lee los volúmenes actuales y actualiza todos los sliders
     */
    private void refrescarSliders() {
        for (int i = 0; i < STREAMS.length; i++) {
            int stream = STREAMS[i];
            SeekBar seekBar = (SeekBar) findViewById(SEEKBAR_IDS[i]);
            TextView valueText = (TextView) findViewById(VALUE_IDS[i]);

            if (seekBar == null) continue;

            int maxVol = audioManager.getStreamMaxVolume(stream);
            int curVol = audioManager.getStreamVolume(stream);
            seekBar.setProgress(curVol);
            if (valueText != null) valueText.setText(curVol + "/" + maxVol);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refrescarSliders();
    }
}
