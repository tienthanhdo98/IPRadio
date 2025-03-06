package app.tek4tv.ipradio.utils.network

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
import android.telephony.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.util.*


class NetworkUtils private constructor() {
    private val TAG = "NetworkUtils"
    var isNetworkConnected = false
        private set
    private val mCallbacks: MutableList<ConnectionCallback> = LinkedList()
    var mNetworkLive = MutableLiveData<Boolean>()
    private val mNetRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    private val mNetCallback: NetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d(TAG, "available")
            mNetworkLive.postValue(true)
            isNetworkConnected = true
            for (callback in mCallbacks) {
                callback.onChange(true)
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(TAG, "lost")
            mNetworkLive.postValue(false)
            isNetworkConnected = false
            for (callback in mCallbacks) {
                callback.onChange(false)
            }
        }
    }

    fun startNetworkListener(context: Context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.registerDefaultNetworkCallback(mNetCallback)
        } else {
            manager.registerNetworkCallback(mNetRequest, mNetCallback)
        }
        Log.d(TAG, "#startNetworkListener() success")
    }

    fun stopNetworkListener(context: Context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.unregisterNetworkCallback(mNetCallback)
        Log.d(TAG, "#stopNetworkListener() success")
    }

    fun addObserver(owner: LifecycleOwner?, observer: Observer<Boolean>?) {
        mNetworkLive.observe(owner!!, observer!!)
    }

    fun removeObserver(observer: Observer<Boolean>?) {
        mNetworkLive.removeObserver(observer!!)
    }

    fun addCallback(callback: ConnectionCallback?) {
        if (callback != null) {
            mCallbacks.add(callback)
        }
    }

    fun removeCallback(callback: ConnectionCallback?) {
        if (callback != null && !mCallbacks.isEmpty()) {
            mCallbacks.remove(callback)
        }
    }

    fun clearCallbacks() {
        mCallbacks.clear()
    }

    interface ConnectionCallback {
        fun onChange(netWorkState: Boolean)
    }

    companion object {
        const val BASE_URL = "https://box.dev.tek4tv.vn/"

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
//                "$allRxByte,$allTxByte"
                val total = (allRxByte + allTxByte)/1024
                "$total"
            } catch (e: Exception) {
                e.printStackTrace()
                "-1,-1"
            }
        }

        fun getNetworkInfo(appContext: Context): String {
            return try {
                val networkClass = getNetworkClass(appContext)
                 val dataUsage = getNetworkUsage(appContext)

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

                return "$networkClass,$dataUsage,$level"
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