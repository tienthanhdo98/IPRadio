import android.net.TrafficStats
import android.os.Process
import android.util.Log
import kotlinx.coroutines.*
import java.util.ArrayDeque

class BandwidthManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val intervalMs: Long = 1000L,
    private val minBytesThreshold: Long = 5 * 1024 // bỏ qua mẫu < 5KB
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val uid = Process.myUid()
            var prevBytes = TrafficStats.getUidRxBytes(uid)
            var prevTime = System.currentTimeMillis()
            val window = ArrayDeque<Double>() // giữ 3 mẫu gần nhất (Kbps)
            val windowSize = 3

            while (isActive) {
                delay(intervalMs)
                val nowBytes = TrafficStats.getUidRxBytes(uid)
                val nowTime = System.currentTimeMillis()

                val bytesDiff = (nowBytes - prevBytes).coerceAtLeast(0)
                val sec = (nowTime - prevTime) / 1000.0

                prevBytes = nowBytes
                prevTime = nowTime

                // Bỏ qua mẫu gần như không tải
                if (bytesDiff < minBytesThreshold || sec <= 0) continue

                val kbps = (bytesDiff * 8.0 / sec) / 1024.0
                if (window.size == windowSize) window.removeFirst()
                window.addLast(kbps)

                val avgKbps = window.average()
                Log.d(
                    "LOGDEVICEINFO banwidth", "Download inst: %.2f Kbps, avg(3s): %.2f Kbps"
                        .format(kbps, avgKbps)
                )
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}