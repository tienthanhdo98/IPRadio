package app.tek4tv.ipradio.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

@SuppressLint("HardwareIds", "MissingPermission")
fun getImsi(context: Context): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return ""

    if (
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return ""
    }

    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        ?: return ""

    return try {
        tm.subscriberId?.takeIf { it.isNotBlank() } ?: ""
    } catch (_: Throwable) {
        ""
    }
}


/**
 * Loại kết nối hiện tại: 0=ETH, 1=WiFi, 2=2G, 3=3G, 4=4G, 5=5G.
 * Trả về -1 nếu không xác định được.
 */
fun convertMobileDataToInt(networkTypeString: String): Int {
    // getprop có thể trả về "LTE,Unknown" nếu máy có 2 SIM. Ta lấy phần tử đầu tiên.
    val firstSimType = networkTypeString.split(",").firstOrNull() ?: ""

    // Chuẩn hóa chuỗi: xóa khoảng trắng, xuống dòng và viết hoa hết
    val type = firstSimType.trim().uppercase()

    return when (type) {
        // --- 5G ---
        "NR" -> 5

        // --- 4G ---
        "LTE", "LTE_CA" -> 4

        // --- 3G ---
        "UMTS", "HSDPA", "HSUPA", "HSPA", "HSPAP", "HSPA+", "EVDO_0", "EVDO_A", "EVDO_B", "EHRPD", "TD_SCDMA" -> 3

        // --- 2G ---
        "GPRS", "EDGE", "CDMA", "1XRTT", "IDEN", "GSM" -> 2

        // --- WiFi / LAN (Dành cho việc mở rộng) ---
        "WIFI", "WLAN" -> 1
        "ETH", "ETHERNET" -> 0

        // --- Không xác định hoặc không có mạng ---
        else -> -1 // Hoặc trả về 2 (mặc định thấp nhất) tùy logic của bạn
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
fun getSimPhoneNumber(context: Context): String {
    val hasPermission =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) return ""

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val subManager = context.getSystemService(SubscriptionManager::class.java)
                ?: return ""
            val subId = SubscriptionManager.getDefaultSubscriptionId()
            subManager.getPhoneNumber(subId).takeIf { it.isNotBlank() } ?: ""
        } else {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return ""
            @Suppress("DEPRECATION")
            tm.line1Number?.takeIf { it.isNotBlank() } ?: ""
        }
    } catch (_: Throwable) {
        ""
    }
}