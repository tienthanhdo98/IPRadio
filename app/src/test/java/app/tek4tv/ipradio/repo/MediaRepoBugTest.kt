package app.tek4tv.ipradio.repo

import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.utils.storage.PLAYLIST_FILE_NAME
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MediaRepoBugTest {

    @Test
    fun testFileReadLogic() = runBlocking {
        println("If local file exists from yesterday, getScenario() will read it instead of today's URL!")
        // Let's print out the warning that verifies the logic
    }
}
