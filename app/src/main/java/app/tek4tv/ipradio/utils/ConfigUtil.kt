package app.tek4tv.ipradio.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

object ConfigUtil {
    const val PREFERENCE_FILE_NAME = "vn.tek4tv.radioip"

    const val HAVE_FORCE_MEDIA = "HAVE_FORCE_MEDIA"
    const val FORCE_MEDIA = "FORCE_MEDIA"
    const val MONTHLY_SCENARIO_URL = "MONTHLY_SCENARIO_URL"
    const val TIME_UPDATE_MONTHLY_SCENARIO = "TIME_UPDATE_MONTHLY_SCENARIO"
    const val URL_HUB = "URL_HUB"
    const val PROVIDER = "PROVIDER"
    const val URL_TOKEN = "URL_TOKEN"
    const val URL_STREAM = "URL_STREAM"
    const val USER_NAME_STREAM = "USER_NAME_STREAM"
    const val PASSWORD_STREAM = "PASSWORD_STREAM"
    const val PROTOCOL_STREAM = "PROTOCOL_STREAM"
    const val BITRATE_STREAM = "BITRATE_STREAM"
    const val SAMPLERATE_STREAM = "SAMPLERATE_STREAM"
    const val Minute_TURN_ON_PA = "Minute_TURN_ON_PA"
    const val ServerTimeout = "ServerTimeout"
    const val KeepAliveInterval = "KeepAliveInterval"

    //count each 5 reset app
    const val CountForHardReset = "KeepAliveInterval"
    const val KEY_EXTEND_FILE = "KEY_EXTEND_FILE"
    const val KEY_MODIFY_TIME_PLAYLIST = "KEY_MODIFY_TIME_PLAYLIST"
    const val MONTHLY_SCENARIO_DATA = "MONTHLY_SCENARIO_DATA"
    const val PLAYLIST_LOCAL_DATE = "PLAYLIST_LOCAL_DATE"

    fun putString(context: Context, key: String?, value: String?) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        sharedPref.edit {
            putString(key, value)
        }
    }

//    fun putStringEncrypt(context: Context, key: String?, value: String?) {
//        val editor = getEncryptedSharedPreferences(context).edit()
//        editor.putString(key, value)
//        editor.apply()
//    }

//    fun getStringEncrypt(context: Context, key: String?, default: String? = ""): String? {
//
//        return getEncryptedSharedPreferences(context).getString(key, default)
//    }

//    private fun getEncryptedSharedPreferences(context: Context): SharedPreferences {
//        return EncryptedSharedPreferences.create(
//            context,
//            PREFERENCE_FILE_NAME,
//            getKey(context),
//            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
//            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
//        )
//    }

//    private fun getKey(context: Context): MasterKey {
//        return MasterKey.Builder(context)
//            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
//            .build()
//    }

    fun putInt(context: Context, key: String?, value: Int) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        sharedPref.edit {
            putInt(key, value)
        }
    }

    fun getInt(context: Context, key: String, defaultValue: Int): Int {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        return sharedPref.getInt(key, defaultValue)
    }


    fun getString(context: Context, key: String?, defaultValue: String?): String? {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(key, defaultValue)
    }

    fun putBool(context: Context, key: String, value: Boolean) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        sharedPref.edit {
            putBoolean(key, value)
        }
    }

    fun getBool(context: Context, key: String, defaultValue: Boolean): Boolean {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(key, defaultValue)
    }

    fun putFloat(context: Context, key: String?, value: Float) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        sharedPref.edit {
            putFloat(key, value)
        }
    }

    fun getFloat(context: Context, key: String, defaultValue: Float): Float {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        return sharedPref.getFloat(key, defaultValue)
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
        sharedPref.edit { remove(key) }
    }

    fun isFirstRun(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)

        if (isFirstRun) {
            // Đánh dấu đã chạy rồi
            prefs.edit { putBoolean("is_first_run", false) }
            return true
        }
        return false
    }

    fun clearAppPreferences(context: Context) {
        context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
            .edit {
                clear()
            }
    }
}