package app.tek4tv.ipradio.utils.network

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Process
import android.telephony.*
import androidx.appcompat.app.AppCompatActivity
import java.util.*


class NetworkUtils private constructor() {

    companion object {
        private var INSTANCE: NetworkUtils? = null
        val instance: NetworkUtils
            get() {
                if (INSTANCE == null) {
                    INSTANCE = NetworkUtils()
                }
                return INSTANCE!!
            }

        fun isNetworkConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting
        }

        fun getNetworkClass(context: Context): String {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = cm.activeNetworkInfo
            if (info == null || !info.isConnected) return "---" // not connected
            if (info.type == ConnectivityManager.TYPE_WIFI) return "WIFI"
            if (info.type == ConnectivityManager.TYPE_ETHERNET) return "ENET"
            if (info.type == ConnectivityManager.TYPE_MOBILE) {
                return when (info.subtype) {
                    TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN, TelephonyManager.NETWORK_TYPE_GSM -> "2G"
                    TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
                    TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN, 19 -> "4G"
                    else -> "?"
                }
            }
            return "---"
        }

        @SuppressLint("MissingPermission")
        fun getCellSignalStrength(context: Context): Int {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            val cellInfo = tm.allCellInfo[0]

            if (cellInfo != null) {
                return when (cellInfo) {
                    is CellInfoLte -> cellInfo.cellSignalStrength.level
                    is CellInfoCdma -> cellInfo.cellSignalStrength.level
                    is CellInfoGsm -> cellInfo.cellSignalStrength.level
                    is CellInfoWcdma -> cellInfo.cellSignalStrength.level
                    else -> CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN
                }
            }

            return CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN
        }

        @SuppressLint("MissingPermission")
        fun getCellSignalStrengthInDbm(context: Context): Int {
            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

                val cellInfo = tm.allCellInfo[0]

                if (cellInfo != null) {
                    return when (cellInfo) {
                        is CellInfoLte -> cellInfo.cellSignalStrength.dbm
                        is CellInfoCdma -> cellInfo.cellSignalStrength.dbm
                        is CellInfoGsm -> cellInfo.cellSignalStrength.dbm
                        is CellInfoWcdma -> cellInfo.cellSignalStrength.dbm
                        else -> CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN
                    }
                }

                return CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN
            } catch (e: Exception) {
                return CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN
            }
        }

        fun getWifiSignalStrength(appContext: Context): Int {
            val wifiManager =
                appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val rssi = wifiManager.connectionInfo.rssi
            return rssi//WifiManager.calculateSignalLevel(rssi, 5)
        }

        fun checkForNetworkStatPermission(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager?
            val mode = appOps!!.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun getNetworkUsage(appContext: Context): String {
            return try {
                val netStatManager =
                    appContext.getSystemService(AppCompatActivity.NETWORK_STATS_SERVICE) as NetworkStatsManager
                val helper = NetworkStatsHelper(netStatManager)
                val startTime = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val endTime = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, 1)
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                val allRxByte =
                    helper.getAllRxByte(appContext, startTime.timeInMillis, endTime.timeInMillis)
                val allTxByte =
                    helper.getAllTxByte(appContext, startTime.timeInMillis, endTime.timeInMillis)
                //data nhận, data gửi
                "$allRxByte,$allTxByte"
            } catch (e: Exception) {
                e.printStackTrace()
                "-1,-1"
            }
        }

        fun getNetworkInfo(appContext: Context): String {
            return try {
                val networkClass = getNetworkClass(appContext)
//                val dataUsage = getNetworkUsage(appContext)

                val level = when (networkClass) {
                    "WIFI" -> "${getWifiSignalStrength(appContext)}dbm"
                    "---" -> ""
                    "ENET" -> ""
                    else -> {
                        val cellStrength = getCellSignalStrengthInDbm(appContext)
                        if (cellStrength != CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN)
                            "${cellStrength}dbm"
                        else
                            "NONE_OR_UNKNOWN"
                    }
                }

//                return "$networkClass,$dataUsage,$level"
                return "$networkClass,$level"
            } catch (e: Exception) {
                e.printStackTrace()
                "error"
            }
        }

        fun setWifiEnable(appContext: Context, enable : Boolean)
        {
            val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled = enable
        }
    }
}