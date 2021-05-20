package app.tek4tv.ipradio.utils

import android.content.Context
import app.tek4tv.ipradio.model.ForceMedia

object ConfigUtil {
    const val PREFERENCE_FILE_NAME = "vn.tek4tv.radioip"
    //const val ACTION_SCHEDULED = "vn.tek4tv.radioip"
    const val HAVE_FORCE_MEDIA = "HAVE_FORCE_MEDIA"
    const val FORCE_MEDIA = "FORCE_MEDIA"
    const val MONTHLY_SCENARIO_URL = "MONTHLY_SCENARIO_URL"
    fun putString(context: Context, key: String?, value: String?) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun getString(context: Context, key: String?, defaultValue: String?): String? {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(key, defaultValue)
    }

    fun putBool(context: Context, key: String, value: Boolean) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    fun getBool(context: Context, key: String, defaultValue: Boolean): Boolean {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(key, defaultValue)
    }
}