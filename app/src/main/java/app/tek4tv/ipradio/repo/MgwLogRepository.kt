package app.tek4tv.ipradio.repo

import android.os.SystemClock
import android.util.Log
import app.tek4tv.ipradio.model.McuLogData
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MgwLogRepository @Inject constructor() {

    private val TAG = "LOG_REPO"

    private val pendingLogs = LinkedList<McuLogData>()

    private var lastLogType: Int? = null
    private var lastLogDetail: String? = null
    private var lastLogAt: Long = 0L

    fun addLog(log: McuLogData) {
        synchronized(pendingLogs) {
            val now = SystemClock.elapsedRealtime()

            val isDuplicate = lastLogType == log.type &&
                    lastLogDetail == log.detail &&
                    now - lastLogAt < 3000

            if (isDuplicate) {
                Log.d(TAG, "skip duplicate log: type=${log.type}, detail=${log.detail}")
                return
            }

            pendingLogs.add(log)
            lastLogType = log.type
            lastLogDetail = log.detail
            lastLogAt = now
        }
        Log.d(TAG, "addLog: id=${log.id}, type=${log.type}, detail=${log.detail}")
    }

    fun addLogs(logs: List<McuLogData>) {
        if (logs.isEmpty()) return
        synchronized(pendingLogs) {
            pendingLogs.addAll(logs)
        }
        Log.d(TAG, "addLogs: size=${logs.size}")
    }

    fun getTopPendingLogs(limit: Int = 10): List<McuLogData> {
        synchronized(pendingLogs) {
            return pendingLogs.take(limit).toList()
        }
    }

    fun removeLogs(logs: List<McuLogData>) {
        if (logs.isEmpty()) return
        synchronized(pendingLogs) {
            pendingLogs.removeAll(logs.toSet())
        }
        Log.d(TAG, "removeLogs: size=${logs.size}")
    }

    fun clearAll() {
        synchronized(pendingLogs) {
            pendingLogs.clear()
        }
        Log.d(TAG, "clearAll")
    }

    fun size(): Int {
        synchronized(pendingLogs) {
            return pendingLogs.size
        }
    }

    fun hasPendingLogs(): Boolean {
        synchronized(pendingLogs) {
            return pendingLogs.isNotEmpty()
        }
    }
}