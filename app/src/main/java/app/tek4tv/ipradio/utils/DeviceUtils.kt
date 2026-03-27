package app.tek4tv.ipradio.utils

import android.os.Environment
import android.os.StatFs

object DeviceUtils {
    fun calculateSpkerr(runningList: List<Boolean>, errorList: List<Boolean>): Int {
        var err = 0
        var run = 0

        // Duyệt qua tối đa 4 loa theo bit 0 đến bit 3
        for (i in 0 until 4) {
            if (i < errorList.size && errorList[i]) {
                err = err or (1 shl i)
            }
            if (i < runningList.size && runningList[i]) {
                run = run or (1 shl i)
            }
        }

        // spkerr = (run << 8) | err
        return (run shl 8) or err
    }


    fun getSdTotal(): Long {
        val path = Environment.getDataDirectory().path
        val statFs = StatFs(path)
        // Chuyển từ Bytes sang KB
        return statFs.totalBytes / 1024
    }

    fun getSdFree(): Long {
        val path = Environment.getDataDirectory().path
        val statFs = StatFs(path)
        // Sử dụng availableBytes để lấy dung lượng thực tế còn dùng được
        // Chuyển từ Bytes sang KB
        return statFs.availableBytes / 1024
    }

}