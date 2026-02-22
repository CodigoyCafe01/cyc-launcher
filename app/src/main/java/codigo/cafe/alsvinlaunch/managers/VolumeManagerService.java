package codigo.cafe.alsvinlaunch.managers;

import android.app.Service;
import android.content.Intent;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;

/**
 * VolumeManagerService
 *
 * Service en background que mantiene todos los streams de audio
 * sincronizados. Cuando el usuario cambia el volumen en cualquier
 * app, este servicio iguala los demás streams de forma proporcional.
 *
 * Inicio: llamar startService(new Intent(this, VolumeManagerService.class))
 * Parar:  llamar stopService(...)  o desde la VolumeActivity
 */
public class VolumeManagerService extends Service {

    private AudioManager audioManager;
    private Handler handler;
    private VolumeObserver volumeObserver;

    // Streams que queremos mantener sincronizados
    private static final int[] STREAMS = {
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM
    };

    private static final String[] SETTINGS_KEYS = {
            "volume_music",
            "volume_ring",
            "volume_alarm",
            "volume_notification",
            "volume_system"
    };

    // Flag para evitar loops (observer detecta cambio → cambiamos → observer se dispara de nuevo)
    private boolean sincronizando = false;

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        handler = new Handler();

        volumeObserver = new VolumeObserver(handler);

        // Registrar observer para cada stream
        for (String key : SETTINGS_KEYS) {
            Uri uri = Settings.System.getUriFor(key);
            getContentResolver().registerContentObserver(uri, false, volumeObserver);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Si el sistema mata el servicio, que lo reinicie solo
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (volumeObserver != null) {
            getContentResolver().unregisterContentObserver(volumeObserver);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // No es un bound service
    }

    // ══════════════════════════════════════
    //  LÓGICA DE SINCRONIZACIÓN
    // ══════════════════════════════════════

    /**
     * Detecta qué stream cambió y aplica ese nivel (proporcional) a todos los demás.
     * Stream de referencia: el que tenga el volumen más alto (el que acaba de subir).
     */
    private void sincronizarVolumenes() {
        if (sincronizando) return;
        sincronizando = true;

        try {
            // Encontrar el stream con la proporción más alta (el que cambió)
            float maxProporcion = 0f;
            int streamReferencia = AudioManager.STREAM_MUSIC;

            for (int stream : STREAMS) {
                int max = audioManager.getStreamMaxVolume(stream);
                int cur = audioManager.getStreamVolume(stream);
                if (max > 0) {
                    float prop = (float) cur / max;
                    if (prop > maxProporcion) {
                        maxProporcion = prop;
                        streamReferencia = stream;
                    }
                }
            }

            // Aplicar esa proporción a todos los demás streams
            final float proporcionFinal = maxProporcion;
            for (int stream : STREAMS) {
                if (stream == streamReferencia) continue;
                int max = audioManager.getStreamMaxVolume(stream);
                int target = Math.round(proporcionFinal * max);
                // Asegurar mínimo 1 si el target > 0
                if (proporcionFinal > 0 && target == 0) target = 1;
                audioManager.setStreamVolume(stream, target, 0);
            }

        } finally {
            // Liberar el flag con un pequeño delay para evitar loops
            handler.postDelayed(() -> sincronizando = false, 500);
        }
    }

    // ══════════════════════════════════════
    //  CONTENT OBSERVER
    // ══════════════════════════════════════

    private class VolumeObserver extends ContentObserver {

        VolumeObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            // Pequeño delay para que Android termine de aplicar el cambio
            handler.postDelayed(() -> sincronizarVolumenes(), 150);
        }
    }
}
