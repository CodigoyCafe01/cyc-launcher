package codigo.cafe.alsvinlaunch.services;

import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.List;

public class ServicioNotificaciones extends NotificationListenerService {

    // Acción para enviar info de lo que suena
    public static final String ACCION_MEDIA_CAMBIO = "codigo.cafe.alsvinlaunch.MEDIA_CAMBIO";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        verificarMediaActiva();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        verificarMediaActiva();
    }

    private void verificarMediaActiva() {
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            List<MediaController> controladores = msm.getActiveSessions(
                    new android.content.ComponentName(this, ServicioNotificaciones.class)
            );

            if (!controladores.isEmpty()) {
                MediaController controlador = controladores.get(0);
                MediaMetadata metadata = controlador.getMetadata();
                PlaybackState estado = controlador.getPlaybackState();

                if (metadata != null) {
                    String titulo = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                    String artista = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    String album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
                    boolean reproduciendo = estado != null &&
                            estado.getState() == PlaybackState.STATE_PLAYING;

                    // Enviar broadcast con la info
                    Intent intent = new Intent(ACCION_MEDIA_CAMBIO);
                    intent.putExtra("titulo", titulo != null ? titulo : "");
                    intent.putExtra("artista", artista != null ? artista : "");
                    intent.putExtra("album", album != null ? album : "");
                    intent.putExtra("reproduciendo", reproduciendo);
                    intent.putExtra("paquete", controlador.getPackageName());
                    sendBroadcast(intent);
                }
            } else {
                // No hay nada reproduciéndose
                Intent intent = new Intent(ACCION_MEDIA_CAMBIO);
                intent.putExtra("titulo", "");
                intent.putExtra("artista", "");
                intent.putExtra("reproduciendo", false);
                sendBroadcast(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}