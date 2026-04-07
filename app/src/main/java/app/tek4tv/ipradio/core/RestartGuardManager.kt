package app.tek4tv.ipradio.core

import app.tek4tv.ipradio.utils.GlobalProperties

class RestartGuardManager(
    private val globalProperties: GlobalProperties
) {

    companion object {
        private const val IGNORE_AFTER_APP_START_MS = 5_000L   // bỏ qua 15 giây đầu
        private const val RESTART_WINDOW_MS = 10_000L           // cửa sổ 60 giây
        private const val MAX_RESTART_IN_WINDOW = 2             // tối đa 2 lần / 60 giây
        private const val BLOCK_DURATION_MS =
            5 * 60_000L       // block 5 phút ki nhận quá nhiều lệnh
    }

    @Volatile
    private var isHandlingRestart = false

    fun canHandleRestartApp(now: Long = System.currentTimeMillis()): Boolean {
        val appStartTime = globalProperties.getAppStartTime()
        val blockedUntil = globalProperties.getRestartBlockedUntil()

        if (isHandlingRestart) {
            return false
        }

        if (now - appStartTime < IGNORE_AFTER_APP_START_MS) {
            return false
        }

        if (now < blockedUntil) {
            return false
        }

        return true
    }

    fun markHandlingRestart(handling: Boolean) {
        isHandlingRestart = handling
    }

    fun registerRestartAndCheckAllowed(now: Long = System.currentTimeMillis()): Boolean {
        var count = globalProperties.getCountRestartApp()
        val lastRestartTime = globalProperties.getLastRestartTime()

        if (now - lastRestartTime > RESTART_WINDOW_MS) {
            count = 0
        }

        count += 1

        if (count > MAX_RESTART_IN_WINDOW) {
            globalProperties.saveRestartBlockedUntil(now + BLOCK_DURATION_MS)
            globalProperties.saveCountRestartApp(0)
            globalProperties.saveLastRestartTime(now)
            return false
        }

        globalProperties.saveCountRestartApp(count)
        globalProperties.saveLastRestartTime(now)
        return true
    }

    fun getCurrentRestartCount(): Int {
        return globalProperties.getCountRestartApp()
    }

    fun getBlockedUntil(): Long {
        return globalProperties.getRestartBlockedUntil()
    }

    fun resetWindowIfExpired(now: Long = System.currentTimeMillis()) {
        val lastRestartTime = globalProperties.getLastRestartTime()
        if (now - lastRestartTime > RESTART_WINDOW_MS) {
            globalProperties.saveCountRestartApp(0)
        }
    }

}