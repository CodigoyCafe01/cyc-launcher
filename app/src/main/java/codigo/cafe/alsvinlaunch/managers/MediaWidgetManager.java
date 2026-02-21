package codigo.cafe.alsvinlaunch.managers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.services.ServicioNotificaciones;

/**
 * Gestiona el widget de reproductor multimedia (play/pause, anterior, siguiente).
 */
public class MediaWidgetManager {

    private final Activity actividad;
    private final LinearLayout widgetReproductor;
    private final TextView reproductorTitulo;
    private final TextView reproductorArtista;
    private final ImageView botonPlayPausa;
    private final ImageView botonAnterior;
    private final ImageView botonSiguiente;

    private MediaController controladorActual;
    private boolean estaReproduciendo = false;
    private BroadcastReceiver receptorMedia;

    public MediaWidgetManager(Activity actividad) {
        this.actividad = actividad;
        this.widgetReproductor = (LinearLayout) actividad.findViewById(R.id.widget_reproductor);
        this.reproductorTitulo = (TextView) actividad.findViewById(R.id.reproductor_titulo);
        this.reproductorArtista = (TextView) actividad.findViewById(R.id.reproductor_artista);
        this.botonPlayPausa = (ImageView) actividad.findViewById(R.id.btn_play_pausa);
        this.botonAnterior = (ImageView) actividad.findViewById(R.id.btn_anterior);
        this.botonSiguiente = (ImageView) actividad.findViewById(R.id.btn_siguiente);
    }

    public void iniciar() {
        botonPlayPausa.setOnClickListener(v -> controlarReproduccion());
        botonAnterior.setOnClickListener(v -> {
            if (controladorActual != null) controladorActual.getTransportControls().skipToPrevious();
        });
        botonSiguiente.setOnClickListener(v -> {
            if (controladorActual != null) controladorActual.getTransportControls().skipToNext();
        });

        registrarReceptor();
        verificarPermisoNotificaciones();
        actualizar();
    }

    public void actualizar() {
        try {
            MediaSessionManager msm = (MediaSessionManager)
                    actividad.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> controladores = msm.getActiveSessions(
                    new ComponentName(actividad, ServicioNotificaciones.class));

            if (!controladores.isEmpty()) {
                controladorActual = controladores.get(0);
                MediaMetadata metadata = controladorActual.getMetadata();
                PlaybackState estado = controladorActual.getPlaybackState();

                if (metadata != null) {
                    String titulo = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                    String artista = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    estaReproduciendo = estado != null &&
                            estado.getState() == PlaybackState.STATE_PLAYING;

                    if (titulo != null && !titulo.isEmpty()) {
                        mostrarWidget(titulo, artista);
                        return;
                    }
                }
            }
            controladorActual = null;
            widgetReproductor.setVisibility(View.GONE);
        } catch (Exception e) {
            widgetReproductor.setVisibility(View.GONE);
        }
    }

    public void destruir() {
        if (receptorMedia != null) {
            actividad.unregisterReceiver(receptorMedia);
            receptorMedia = null;
        }
    }

    // ── Privados ──

    private void controlarReproduccion() {
        if (controladorActual == null) return;
        if (estaReproduciendo) {
            controladorActual.getTransportControls().pause();
        } else {
            controladorActual.getTransportControls().play();
        }
    }

    private void registrarReceptor() {
        receptorMedia = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String titulo = intent.getStringExtra("titulo");
                String artista = intent.getStringExtra("artista");
                boolean reproduciendo = intent.getBooleanExtra("reproduciendo", false);

                if (titulo != null && !titulo.isEmpty()) {
                    estaReproduciendo = reproduciendo;
                    mostrarWidget(titulo, artista);
                } else {
                    widgetReproductor.setVisibility(View.GONE);
                }
            }
        };
        actividad.registerReceiver(receptorMedia,
                new IntentFilter(ServicioNotificaciones.ACCION_MEDIA_CAMBIO));
    }

    // En mostrarWidget(), después de setVisibility(View.VISIBLE):
    private void mostrarWidget(String titulo, String artista) {
        widgetReproductor.setVisibility(View.VISIBLE);
        reproductorTitulo.setText(titulo);
        reproductorArtista.setText(artista != null ? artista : "");
        botonPlayPausa.setImageResource(
                estaReproduciendo ? R.drawable.ic_pausa : R.drawable.ic_play);

        // ── NUEVO: tocar título/artista abre la app reproductora ──
        View.OnClickListener abrirApp = v -> abrirAppReproductora();
        reproductorTitulo.setOnClickListener(abrirApp);
        reproductorArtista.setOnClickListener(abrirApp);
        widgetReproductor.setOnClickListener(abrirApp); // toda la tarjeta también
    }

    private void abrirAppReproductora() {
        if (controladorActual == null) return;
        try {
            android.app.PendingIntent intent = controladorActual.getSessionActivity();
            if (intent != null) {
                intent.send();
            } else {
                // Fallback: abrir por nombre de paquete
                String paquete = controladorActual.getPackageName();
                Intent i = actividad.getPackageManager()
                        .getLaunchIntentForPackage(paquete);
                if (i != null) actividad.startActivity(i);
            }
        } catch (Exception e) {
            Toast.makeText(actividad, "No se pudo abrir la app", Toast.LENGTH_SHORT).show();
        }
    }
    private void verificarPermisoNotificaciones() {
        String servicioActivo = android.provider.Settings.Secure.getString(
                actividad.getContentResolver(), "enabled_notification_listeners");

        if (servicioActivo != null && servicioActivo.contains(actividad.getPackageName())) return;

        Toast.makeText(actividad, "Activa AlsvinLauncher en la lista", Toast.LENGTH_SHORT).show();
        try {
            actividad.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        } catch (Exception e) {
            try {
                actividad.startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));
            } catch (Exception ex) {
                Toast.makeText(actividad,
                        "Ve a Ajustes → Seguridad → Acceso a notificaciones", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
