package app.tek4tv.ipradio.di

import app.tek4tv.ipradio.utils.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TokenAuth {
    @Volatile
    private var cachedBearer: String? = null

    // Gọi 1 lần: nếu đã có thì trả về ngay, chưa có thì gọi getLogToken() và cache
    suspend fun getOrFetchBearer(logUtils: LogUtils): String? {
        cachedBearer?.let { return it }
        return withContext(Dispatchers.IO) {
            logUtils.getLogToken()        // <- trả token thô
                ?.let { it }    // <- thêm tiền tố Bearer
                ?.also { cachedBearer = it }
        }
    }

    fun clear() {
        cachedBearer = null
    }
}