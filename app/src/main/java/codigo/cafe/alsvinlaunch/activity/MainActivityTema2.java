package codigo.cafe.alsvinlaunch.activity;

import codigo.cafe.alsvinlaunch.MainActivity;
import codigo.cafe.alsvinlaunch.R;

/**
 * Tema alternativo de la pantalla de inicio.
 *
 * Reutiliza por completo la lógica de {@link MainActivity} (managers de reloj,
 * reproductor, atajos, velocímetro, sistema, permisos, etc.) y solo cambia el
 * layout inflado. Al extender MainActivity, hereda toda la configuración de
 * botones y ciclo de vida sin duplicar código: basta con que el nuevo layout
 * contenga los mismos IDs de vistas que los managers esperan.
 */
public class MainActivityTema2 extends MainActivity {

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_main_tema2;
    }
}
