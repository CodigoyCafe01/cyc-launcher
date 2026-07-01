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

    // ── HeadUnit Revived (Android Auto host) ──
    public static final String PAQUETE_HEADUNIT       = "com.andrerinas.headunitrevived";
    public static final String ACTIVIDAD_HU_MAIN      = "com.andrerinas.headunitrevived.main.MainActivity";
    public static final String ACTIVIDAD_HU_SETTINGS  = "com.andrerinas.headunitrevived.main.SettingsActivity";
    // Extra keys consumed by SettingsActivity to deep-link into a sub-screen.
    public static final String EXTRA_HU_START_DEST   = "start_destination";
    public static final String HU_DEST_USB           = "usb";

    // ── Apps del panel del sistema (las que aparecen en el FAB) ──
    public static final SystemApp[] APPS_PANEL_SISTEMA = {
            new SystemApp(R.string.sys_telefono,    PAQUETE_HIVEOS, ACTIVIDAD_TELEFONO,   R.drawable.ic_phone),
            new SystemApp(R.string.sys_radio,       PAQUETE_HIVEOS, ACTIVIDAD_RADIO,      R.drawable.ic_radio),
            new SystemApp(R.string.sys_navegacion,  PAQUETE_HIVEOS, ACTIVIDAD_NAVI,       R.drawable.ic_navi),
            new SystemApp(R.string.sys_musica_usb,  PAQUETE_HIVEOS, ACTIVIDAD_MUSICA_USB, R.drawable.ic_music),
            new SystemApp(R.string.sys_bluetooth,   PAQUETE_HIVEOS, ACTIVIDAD_BLUETOOTH,  R.drawable.ic_bluetooth),
            new SystemApp(R.string.sys_ajustes,     PAQUETE_HIVEOS, ACTIVIDAD_AJUSTES,    R.drawable.ic_settings),
            new SystemApp(R.string.sys_camara_reversa, PAQUETE_HIVEOS, ACTIVIDAD_CAMARA, R.drawable.ic_camera),
            new SystemApp(R.string.sys_info_auto,   PAQUETE_HIVEOS, ACTIVIDAD_INFO_AUTO,  R.drawable.ic_car),
            new SystemApp(R.string.sys_usb_headunit,       PAQUETE_HEADUNIT, ACTIVIDAD_HU_SETTINGS, R.drawable.ic_usb,
                    SystemApp.extra(EXTRA_HU_START_DEST, HU_DEST_USB)),
            new SystemApp(R.string.sys_config_headunit, PAQUETE_HEADUNIT, ACTIVIDAD_HU_SETTINGS, R.drawable.ic_settings),

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