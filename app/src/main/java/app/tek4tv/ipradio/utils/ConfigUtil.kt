package app.tek4tv.ipradio.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

object ConfigUtil {
    const val PREFERENCE_FILE_NAME = "vn.tek4tv.radioip"

    const val HAVE_FORCE_MEDIA = "HAVE_FORCE_MEDIA"

    //count each 5 reset app
    const val CountForHardReset = "KeepAliveInterval"
    const val KEY_EXTEND_FILE = "KEY_EXTEND_FILE"

    //MGW
    const val BASE_ALARM_ID = "BASE_ALARM_ID"
    const val FORCE_MEDIA_INFO = "FORCE_MEDIA_INFO"

    fun putString(context: Context, key: String?, value: String?) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        sharedPref.edit {
            putString(key, value)
        }
    }

    fun putStringEncrypt(context: Context, key: String?, value: String?) {
        getEncryptedSharedPreferences(context).edit {
            putString(key, value)
        }
    }

    fun getStringEncrypt(context: Context, key: String, defaultValue: String?): String? {

        return getEncryptedSharedPreferences(context).getString(key, defaultValue)
    }

    private fun getEncryptedSharedPreferences(context: Context): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            PREFERENCE_FILE_NAME,
            getKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun getKey(context: Context): MasterKey {
        return MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }

    fun putLong(context: Context, key: String?, value: Long) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        sharedPref.edit {
            putLong(key, value)
        }
    }

    fun getLong(context: Context, key: String, defaultValue: Long): Long {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        return sharedPref.getLong(key, defaultValue)
    }


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

    fun clearAllPrefs(context: Context) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        sharedPref.edit { clear() }
    }
}

