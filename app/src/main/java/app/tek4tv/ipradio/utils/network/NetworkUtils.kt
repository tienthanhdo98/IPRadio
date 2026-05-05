package app.tek4tv.ipradio.utils.network

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.TelephonyManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Calendar
import java.util.Collections
import java.util.LinkedList


class NetworkUtils private constructor() {
    private val TAG = "NetworkUtils"
    var isNetworkConnected = false
        private set
    private val mCallbacks: MutableList<ConnectionCallback> = LinkedList()
    var mNetworkLive = MutableLiveData<Boolean>()
    private val mNetRequest =
        NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
//    private val mNetCallback: NetworkCallback = object : NetworkCallback() {
//        override fun onAvailable(network: Network) {
//            super.onAvailable(network)
//            Log.d(TAG, "available")
//            mNetworkLive.postValue(true)
//            isNetworkConnected = true
//            for (callback in mCallbacks) {
//                callback.onChange(true)
//            }
//        }
//
//        override fun onLost(network: Network) {
//            super.onLost(network)
//            Log.d(TAG, "lost")
//            mNetworkLive.postValue(false)
//            isNetworkConnected = false
//            for (callback in mCallbacks) {
//                callback?.onChange(false)
//            }
//        }
//    }

    private var appContext: Context? = null

    private val mNetCallback: NetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            val connected = appContext?.let { getCurrentConnectedState(it) } ?: true
            Log.d(TAG, "available -> connected=$connected")
            mNetworkLive.postValue(connected)
            isNetworkConnected = connected
            for (callback in mCallbacks) {
                callback.onChange(connected)
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            val connected = appContext?.let { getCurrentConnectedState(it) } ?: false
            Log.d(TAG, "lost -> connected=$connected")
            mNetworkLive.postValue(connected)
            isNetworkConnected = connected
            for (callback in mCallbacks) {
                callback.onChange(connected)
            }
        }
    }

    //    fun startNetworkListener(context: Context) {
//        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        if (manager != null) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                manager.registerDefaultNetworkCallback(mNetCallback)
//            } else {
//                manager.registerNetworkCallback(mNetRequest, mNetCallback)
//            }
//            Log.d(TAG, "#startNetworkListener() success")
//        } else {
//            Log.d(TAG, "#startNetworkListener() failed")
//        }
//    }
    fun startNetworkListener(context: Context) {
        appContext = context.applicationContext
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.registerDefaultNetworkCallback(mNetCallback)
        } else {
            manager.registerNetworkCallback(mNetRequest, mNetCallback)
        }
        isNetworkConnected = getCurrentConnectedState(appContext!!)
        mNetworkLive.postValue(isNetworkConnected)
        Log.d(TAG, "#startNetworkListener() success, connected=$isNetworkConnected")
    }

    //    fun stopNetworkListener(context: Context) {
//        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        if (manager != null) {
//            manager.unregisterNetworkCallback(mNetCallback)
//            Log.d(TAG, "#stopNetworkListener() success")
//        } else {
//            Log.d(TAG, "#stopNetworkListener() failed")
//        }
//    }
    fun stopNetworkListener(context: Context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            manager.unregisterNetworkCallback(mNetCallback)
        } catch (_: Exception) {
        }
        appContext = null
        Log.d(TAG, "#stopNetworkListener() success")
    }

    fun getCurrentConnectedState(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(activeNetwork) ?: return false

        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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

        private var INSTANCE: NetworkUtils? = null
        val instance: NetworkUtils
            get() {
                if (INSTANCE == null) {
                    INSTANCE = NetworkUtils()
                }
                return INSTANCE!!
            }

        fun getEthernetLanIp(context: Context): String? {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            cm.allNetworks.forEach { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@forEach
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    val lp = cm.getLinkProperties(network)
                    val ip = findIpv4FromLinkProperties(lp)
                    if (ip != null) return ip
                }
            }
            return null
        }

        fun getRealEthernetIp(): String? {
            try {
                // Lấy danh sách toàn bộ các cổng mạng hiện có dưới kernel
                val interfaces: MutableList<NetworkInterface> = Collections.list<NetworkInterface?>(
                    NetworkInterface.getNetworkInterfaces()
                )

                for (intf in interfaces) {
                    // Tìm đúng cổng mạng dây (thường là eth0)
                    if (intf.getName().equals("eth0", ignoreCase = true)) {
                        // Lấy danh sách các IP đang được gán cho cổng này

                        val addrs: MutableList<InetAddress> =
                            Collections.list<InetAddress?>(intf.getInetAddresses())

                        for (addr in addrs) {
                            // Bỏ qua địa chỉ loopback (127.0.0.1) và chỉ lấy IPv4
                            if (!addr.isLoopbackAddress() && addr is Inet4Address) {
                                return addr.getHostAddress() // Trả về IP tĩnh bạn vừa set
                            }
                        }
                    }
                }
            } catch (ex: java.lang.Exception) {
                ex.printStackTrace()
            }
            return null // Trả về null nếu không tìm thấy
        }


        /**
         * Lấy IPv4 cục bộ của interface Mobile data.
         * Đây là IP trên interface di động của máy, không chắc là public IP Internet.
         */
        fun getMobileLocalIp(context: Context): String? {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            cm.allNetworks.forEach { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@forEach
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    val lp = cm.getLinkProperties(network)
                    val ip = findIpv4FromLinkProperties(lp)
                    if (ip != null) return ip
                }
            }
            return ""
        }

        fun getCellularSignalPercent(context: Context): Int? {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            val allCells = try {
                tm.allCellInfo
            } catch (e: SecurityException) {
                null
            } catch (e: Exception) {
                null
            } ?: return null

            val registeredCell = allCells.firstOrNull { it.isRegistered } ?: return null

            val level0to4 = when (registeredCell) {
                is CellInfoGsm -> registeredCell.cellSignalStrength.level
                is CellInfoCdma -> registeredCell.cellSignalStrength.level
                is CellInfoWcdma -> registeredCell.cellSignalStrength.level
                is CellInfoLte -> registeredCell.cellSignalStrength.level
                else -> return null
            }

            return (level0to4 * 25).coerceIn(0, 100)
        }

        fun getWifiSignalPercent(context: Context): Int? {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            if (!wifiManager.isWifiEnabled) return null

            val wifiInfo = try {
                wifiManager.connectionInfo
            } catch (e: Exception) {
                null
            } ?: return null

            val rssi = wifiInfo.rssi

            // INVALID_RSSI là hidden API trong AOSP, giá trị thực tế là -127
            if (rssi <= -127) return null

            return WifiManager.calculateSignalLevel(rssi, 101).coerceIn(0, 100)
        }

        fun getAvailableWifiNetworks(appContext: Context): List<String> {
            return try {
                val wifiManager =
                    appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                if (!wifiManager.isWifiEnabled) return emptyList()
                val scanResults = wifiManager.scanResults
                scanResults.mapNotNull { it.SSID }.filter { it.isNotBlank() }.distinct()
            } catch (e: Exception) {
                Log.d("showWifi", "getAvailableWifiNetworks: ${e.message}")
                emptyList()

            }
        }


        private fun findIpv4FromLinkProperties(lp: LinkProperties?): String? {
            if (lp == null) return null
            for (linkAddress in lp.linkAddresses) {
                val address = linkAddress.address
                if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                    return address.hostAddress
                }
            }
            return null
        }

        fun isNetworkConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetworkInfo
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting
            }
            return false
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
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }


        fun getNetworkInfo(appContext: Context): String {
            return try {
                val networkClass = getNetworkClass(appContext)
                val level = when (networkClass) {
                    "Wifi" -> "${getWifiSignalStrength(appContext)}dbm"
                    "---" -> ""
                    "Ethernet" -> ""
                    else -> {
                        val cellStrength = getCellSignalStrengthInDbm(appContext)
                        if (cellStrength != CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN) "${cellStrength}dbm"
                        else ""
                    }
                }

//                return "$networkClass,$dataUsage,$level"
                //theo 2455 khong gui datausage o day
                if (level.isBlank()) networkClass else "$networkClass,$level"
            } catch (e: Exception) {
                e.printStackTrace()
                "error"
            }
        }

        fun setWifiEnable(appContext: Context, enable: Boolean) {
            val wifiManager =
                appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled = enable
        }

        fun bindProcessToTransport(context: Context, transport: Int): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps != null && caps.hasTransport(transport) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        cm.bindProcessToNetwork(network)
                    } else {
                        ConnectivityManager.setProcessDefaultNetwork(network)
                    }
                }
            }
            return false
        }
        
        fun clearProcessNetworkBinding(context: Context) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.bindProcessToNetwork(null)
            } else {
                ConnectivityManager.setProcessDefaultNetwork(null)
            }
        }
    }
}