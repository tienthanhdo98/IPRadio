package app.tek4tv.ipradio.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object ConfigUtil {
    const val PREFERENCE_FILE_NAME = "vn.tek4tv.radioip"

    //const val ACTION_SCHEDULED = "vn.tek4tv.radioip"
    const val HAVE_FORCE_MEDIA = "HAVE_FORCE_MEDIA"
    const val FORCE_MEDIA = "FORCE_MEDIA"
    const val MONTHLY_SCENARIO_URL = "MONTHLY_SCENARIO_URL"
    const val DISCONNECT_START_TIME = "DISCONNECT_START_TIME"
    const val DISCONNECT_STATE = "DISCONNECT_STATE"
    const val URL_HUB = "URL_HUB"
    const val URL_TOKEN = "URL_TOKEN"
    const val KEY_LIST_LEFT_PREF = "KEY_LIST_LEFT_PREF"
    const val PROVIDER = "PROVIDER"
    const val DEFAULT_ANDROID_VOLUME = "DEFAULT_ANDROID_VOLUME"
    const val DEFAULT_VOLUME = "DEFAULT_VOLUME"
    const val TIME_UPDATE_MONTHLY_SCENARIO = "TIME_UPDATE_MONTHLY_SCENARIO"
    const val MONTHLY_SCENARIO_WSS_DATA = "MONTHLY_SCENARIO_WSS_DATA"
    const val CountForHardReset = "KeepAliveInterval"
    const val KEY_EXTEND_FILE = "KEY_EXTEND_FILE"


    fun putString(context: Context, key: String?, value: String?) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun putStringEncrypt(context: Context, key: String?, value: String?) {
        val editor = getEncryptedSharedPreferences(context).edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun getStringEncrypt(context: Context, key: String?, defaultValue: String?): String? {

        return getEncryptedSharedPreferences(context).getString(key,defaultValue)
    }
    private fun getEncryptedSharedPreferences(context: Context): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context, PREFERENCE_FILE_NAME,getKey(context), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun getKey(context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
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

    fun putFloat(context: Context, key: String?, value: Float) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putFloat(key, value)
        editor.apply()
    }

    fun getFloat(context: Context, key: String, defaultValue: Float): Float {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        return sharedPref.getFloat(key, defaultValue)
    }
    fun putInt(context: Context, key: String?, value: Int) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    fun getInt(context: Context, key: String, defaultValue: Int): Int {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        return sharedPref.getInt(key, defaultValue)
    }

    fun checkSharef(context: Context, key: String): Boolean {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        return if (sharedPref.contains(key)) {
            return true
        } else {
            false
        }
    }

    fun removeSharef(context: Context, key: String) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        sharedPref.edit().remove(key).apply()
    }
}