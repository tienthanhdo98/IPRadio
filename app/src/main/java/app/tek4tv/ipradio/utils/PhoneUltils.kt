package app.tek4tv.ipradio.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

@SuppressLint("HardwareIds", "MissingPermission")
fun getImsi(context: Context): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return null

    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }

    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        ?: return null

    return try {
        tm.subscriberId?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }
}

@SuppressLint("HardwareIds")
@RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
fun getImsiBySubscription(context: Context, subId: Int): String? {
    val baseTm =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null

    val hasReadPhoneState = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasReadPhoneState) return null

    return try {
        baseTm.createForSubscriptionId(subId).subscriberId
    } catch (e: SecurityException) {
        null
    }
}

@SuppressLint("MissingPermission", "HardwareIds")
fun getSimPhoneNumber(context: Context): String? {
    val hasPermission =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) return null

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val subManager = context.getSystemService(SubscriptionManager::class.java)
                ?: return null
            val subId = SubscriptionManager.getDefaultSubscriptionId()
            subManager.getPhoneNumber(subId).takeIf { it.isNotBlank() }
        } else {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return null
            @Suppress("DEPRECATION")
            tm.line1Number?.takeIf { it.isNotBlank() }
        }
    } catch (_: Throwable) {
        null
    }
}