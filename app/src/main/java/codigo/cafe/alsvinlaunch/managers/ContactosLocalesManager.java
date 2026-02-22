package codigo.cafe.alsvinlaunch.managers;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persiste contactos importados desde BT en SharedPreferences (JSON).
 * Los contactos quedan "fijos" aunque el teléfono se desconecte.
 */
public class ContactosLocalesManager {

    private static final String PREFS_NAME = "contactos_locales";
    private static final String KEY_CONTACTOS = "lista_contactos";

    private final SharedPreferences prefs;

    public ContactosLocalesManager(Context ctx) {
        this.prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ══════════════════════════════════════
    //  LEER
    // ══════════════════════════════════════

    public List<BluetoothContactsManager.ContactoBt> obtenerTodos() {
        List<BluetoothContactsManager.ContactoBt> lista = new ArrayList<>();
        try {
            String json = prefs.getString(KEY_CONTACTOS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                lista.add(new BluetoothContactsManager.ContactoBt(
                        obj.getString("nombre"),
                        obj.getString("numero")
                ));
            }
        } catch (Exception e) {
            // devuelve lista vacía
        }
        return lista;
    }

    // ══════════════════════════════════════
    //  ESCRIBIR
    // ══════════════════════════════════════

    /** Reemplaza toda la lista con los contactos dados */
    public void guardarTodos(List<BluetoothContactsManager.ContactoBt> contactos) {
        try {
            JSONArray arr = new JSONArray();
            for (BluetoothContactsManager.ContactoBt c : contactos) {
                JSONObject obj = new JSONObject();
                obj.put("nombre", c.nombre);
                obj.put("numero", c.numero);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_CONTACTOS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Agrega un solo contacto (si no existe ya por número) */
    public boolean agregarContacto(BluetoothContactsManager.ContactoBt nuevo) {
        List<BluetoothContactsManager.ContactoBt> lista = obtenerTodos();
        for (BluetoothContactsManager.ContactoBt c : lista) {
            if (c.numero.equals(nuevo.numero)) return false; // ya existe
        }
        lista.add(nuevo);
        guardarTodos(lista);
        return true;
    }

    /** Agrega múltiples contactos, devuelve cuántos se agregaron realmente */
    public int agregarVarios(List<BluetoothContactsManager.ContactoBt> nuevos) {
        List<BluetoothContactsManager.ContactoBt> lista = obtenerTodos();
        int agregados = 0;
        for (BluetoothContactsManager.ContactoBt nuevo : nuevos) {
            boolean existe = false;
            for (BluetoothContactsManager.ContactoBt c : lista) {
                if (c.numero.equals(nuevo.numero)) { existe = true; break; }
            }
            if (!existe) {
                lista.add(nuevo);
                agregados++;
            }
        }
        guardarTodos(lista);
        return agregados;
    }

    /** Elimina contacto por número */
    public void eliminar(String numero) {
        List<BluetoothContactsManager.ContactoBt> lista = obtenerTodos();
        lista.removeIf(c -> c.numero.equals(numero));
        guardarTodos(lista);
    }

    public void eliminarTodos() {
        prefs.edit().remove(KEY_CONTACTOS).apply();
    }

    public int cantidad() {
        return obtenerTodos().size();
    }
}