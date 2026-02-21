package codigo.cafe.alsvinlaunch.util;

import codigo.cafe.alsvinlaunch.R;
import codigo.cafe.alsvinlaunch.managers.SystemApp;

/**
 * Constantes de las apps del sistema HiveOS.
 * Para agregar más apps, solo agrega una nueva entrada aquí.
 */
public class ConstantesSistema {

    // ── Paquete principal de HiveOS ──
    public static final String PAQUETE_HIVEOS = "com.ts.MainUI";

    // ── Activities del sistema ──
    public static final String ACTIVIDAD_TELEFONO   = "com.ts.main.TsDailer.TsDailerActivity";
    public static final String ACTIVIDAD_RADIO      = "com.ts.main.radio.RadioMainActivity";
    public static final String ACTIVIDAD_NAVI       = "com.ts.main.navi.NaviMainActivity";
    public static final String ACTIVIDAD_MUSICA_USB = "com.ts.main.Media.USBMainActivity";
    public static final String ACTIVIDAD_MUSICA_SD  = "com.ts.main.Media.SDMainActivity";
    public static final String ACTIVIDAD_BLUETOOTH  = "com.ts.bt.WrcActivity";
    public static final String ACTIVIDAD_VIDEO      = "com.ts.main.cmmb.CmmbMainActivity";
    public static final String ACTIVIDAD_ATV        = "com.ts.main.cmmb.AtvMainActivity";
    public static final String ACTIVIDAD_ENTRADA_AV = "com.ts.main.avin.AvinMainActivity";
    public static final String ACTIVIDAD_AUX        = "com.ts.main.avin2.AuxMainActivity";
    public static final String ACTIVIDAD_AJUSTES    = "com.ts.factoryset.FsMainActivity";
    public static final String ACTIVIDAD_CAMARA     = "com.ts.backcar.BackcarMainActivity";
    public static final String ACTIVIDAD_CAMARA_DER = "com.ts.backcar.RightCameraActivity";
    public static final String ACTIVIDAD_DVR        = "com.ts.main.dvr.DvrMainActivity";
    public static final String ACTIVIDAD_CAMARA_F   = "com.ts.main.fcamera.FcameraMainActivity";
    public static final String ACTIVIDAD_IPOD       = "com.ts.main.ipod.IpodMainActivity";
    public static final String ACTIVIDAD_VISTA_360  = "com.ts.main.view360.View360MainActivity";
    public static final String ACTIVIDAD_INFO_AUTO  = "com.ts.main.CStudy.CStudyMainActivity";
    public static final String ACTIVIDAD_ADAS       = "com.ts.main.Media.ADASMainActivity";

    // ── Apps del panel del sistema (las que aparecen en el FAB) ──
    public static final SystemApp[] APPS_PANEL_SISTEMA = {
            new SystemApp("Teléfono",    PAQUETE_HIVEOS, ACTIVIDAD_TELEFONO,   R.drawable.ic_phone),
            new SystemApp("Radio",       PAQUETE_HIVEOS, ACTIVIDAD_RADIO,      R.drawable.ic_radio),
            new SystemApp("Navegación",  PAQUETE_HIVEOS, ACTIVIDAD_NAVI,       R.drawable.ic_navi),
            new SystemApp("Música USB",  PAQUETE_HIVEOS, ACTIVIDAD_MUSICA_USB, R.drawable.ic_music),
            new SystemApp("Bluetooth",   PAQUETE_HIVEOS, ACTIVIDAD_BLUETOOTH,  R.drawable.ic_bluetooth),
            new SystemApp("Ajustes",     PAQUETE_HIVEOS, ACTIVIDAD_AJUSTES,    R.drawable.ic_settings),
            new SystemApp("Cámara Reversa", PAQUETE_HIVEOS, ACTIVIDAD_CAMARA, R.drawable.ic_camera),
            new SystemApp("Info Auto",   PAQUETE_HIVEOS, ACTIVIDAD_INFO_AUTO,  R.drawable.ic_car),

    };

    // ── Accesos directos predefinidos (pantalla principal) ──
    public static final String[][] ACCESOS_PREDEFINIDOS = {
            {"Música",     PAQUETE_HIVEOS, ACTIVIDAD_MUSICA_USB},
            {"Navegación", PAQUETE_HIVEOS, ACTIVIDAD_NAVI},
            {"Teléfono",   PAQUETE_HIVEOS, ACTIVIDAD_TELEFONO},
            {"Radio",      PAQUETE_HIVEOS, ACTIVIDAD_RADIO},
            {"Ajustes",    PAQUETE_HIVEOS, ACTIVIDAD_AJUSTES},
    };
}