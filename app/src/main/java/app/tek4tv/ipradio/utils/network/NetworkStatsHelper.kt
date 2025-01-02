package app.tek4tv.ipradio.utils.network

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.RemoteException
import android.telephony.TelephonyManager

class NetworkStatsHelper {
    var networkStatsManager: NetworkStatsManager
    var packageUid = 0

    constructor(networkStatsManager: NetworkStatsManager) {
        this.networkStatsManager = networkStatsManager
    }

    constructor(networkStatsManager: NetworkStatsManager, packageUid: Int) {
        this.networkStatsManager = networkStatsManager
        this.packageUid = packageUid
    }

    private fun getAllRxBytesMobile(context: Context, startTime: Long, endTime: Long): Long {
        val bucket: NetworkStats.Bucket = try {
            networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                getSubscriberId(context, ConnectivityManager.TYPE_MOBILE),
                startTime, endTime
            )
        } catch (e: RemoteException) {
            return -1
        }
        return bucket.rxBytes
    }

    private fun getAllTxBytesMobile(context: Context, startTime: Long, endTime: Long): Long {
        val bucket: NetworkStats.Bucket = try {
            networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                getSubscriberId(context, ConnectivityManager.TYPE_MOBILE),
                startTime, endTime
            )
        } catch (e: RemoteException) {
            return -1
        }
        return bucket.txBytes
    }

    private fun getAllRxByteWifi(startTime: Long, endTime: Long): Long {
        val bucket: NetworkStats.Bucket = try {
            networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_WIFI,
                "",
                startTime, endTime
            )
        } catch (e: RemoteException) {
            return -1
        }
        return bucket.rxBytes
    }

    private fun getAllTxByteWifi(startTime: Long, endTime: Long): Long {
        val bucket: NetworkStats.Bucket = try {
            networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_WIFI,
                "",
                startTime, endTime
            )
        } catch (e: RemoteException) {
            return -1
        }
        return bucket.txBytes
    }


    fun getAllRxByte(context: Context, startTime: Long, endTime: Long): Long {
        return getAllRxByteWifi(startTime, endTime) + getAllRxBytesMobile(context, startTime, endTime)
    }

    fun getAllTxByte(context: Context, startTime: Long, endTime: Long): Long {
        return getAllTxByteWifi(startTime, endTime) + getAllTxBytesMobile(context, startTime, endTime)
    }

    /*fun getPackageRxBytesMobile(context: Context): Long {
        var networkStats: NetworkStats? = null
        networkStats = try {
            networkStatsManager.queryDetailsForUid(
                ConnectivityManager.TYPE_MOBILE,
                getSubscriberId(context, ConnectivityManager.TYPE_MOBILE),
                0,
                System.currentTimeMillis(),
                packageUid
            )
        } catch (e: RemoteException) {
            return -1
        }
        var rxBytes = 0L
        val bucket = NetworkStats.Bucket()
        while (networkStats?.hasNextBucket() == true) {
            networkStats.getNextBucket(bucket)
            rxBytes += bucket.rxBytes
        }
        networkStats?.close()
        return rxBytes
    }

    fun getPackageTxBytesMobile(context: Context): Long {
        var networkStats: NetworkStats? = null
        networkStats = try {
            networkStatsManager.queryDetailsForUid(
                ConnectivityManager.TYPE_MOBILE,
                getSubscriberId(context, ConnectivityManager.TYPE_MOBILE),
                0,
                System.currentTimeMillis(),
                packageUid
            )
        } catch (e: RemoteException) {
            return -1
        }
        var txBytes = 0L
        val bucket = NetworkStats.Bucket()
        while (networkStats?.hasNextBucket() == true) {
            networkStats.getNextBucket(bucket)
            txBytes += bucket.txBytes
        }
        networkStats?.close()
        return txBytes
    }

    val packageRxBytesWifi: Long
        get() {
            var networkStats: NetworkStats? = null
            networkStats = try {
                networkStatsManager.queryDetailsForUid(
                    ConnectivityManager.TYPE_WIFI,
                    "",
                    0,
                    System.currentTimeMillis(),
                    packageUid
                )
            } catch (e: RemoteException) {
                return -1
            }
            var rxBytes = 0L
            val bucket = NetworkStats.Bucket()
            while (networkStats?.hasNextBucket() == true) {
                networkStats.getNextBucket(bucket)
                rxBytes += bucket.rxBytes
            }
            networkStats?.close()
            return rxBytes
        }
    val packageTxBytesWifi: Long
        get() {
            var networkStats: NetworkStats? = null
            networkStats = try {
                networkStatsManager.queryDetailsForUid(
                    ConnectivityManager.TYPE_WIFI,
                    "",
                    0,
                    System.currentTimeMillis(),
                    packageUid
                )
            } catch (e: RemoteException) {
                return -1
            }
            var txBytes = 0L
            val bucket = NetworkStats.Bucket()
            while (networkStats?.hasNextBucket() == true) {
                networkStats.getNextBucket(bucket)
                txBytes += bucket.txBytes
            }
            networkStats?.close()
            return txBytes
        }*/

    @SuppressLint("MissingPermission")
    private fun getSubscriberId(context: Context, networkType: Int): String? {
        if (ConnectivityManager.TYPE_MOBILE == networkType) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            return tm.subscriberId
        }
        return ""
    }
}