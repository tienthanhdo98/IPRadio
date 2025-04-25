package app.tek4tv.ipradio.repo

import android.util.Log
import app.tek4tv.ipradio.MyApp.Companion.appContext
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.ui.MainActivity.Companion.appStorageManager
import app.tek4tv.ipradio.ui.MainActivity.Companion.jsonListMediaItemAdapter
import app.tek4tv.ipradio.ui.MainActivity.Companion.jsonMapAdapter
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.ConfigUtil.MONTHLY_SCENARIO_WSS_DATA
import app.tek4tv.ipradio.utils.ConfigUtil.SCENARIO_DATA
import app.tek4tv.ipradio.utils.media.DownloadHelper2
import app.tek4tv.ipradio.utils.media.DownloadItem
import app.tek4tv.ipradio.utils.storage.PLAYLIST_FILE_NAME
import app.tek4tv.ipradio.utils.storage.getConfigFileExtension
import app.tek4tv.ipradio.utils.storage.getFileNameFromUrl
import app.tek4tv.ipradio.utils.storage.isFileExisted
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.launch
import java.io.File
import java.util.*


class MediaRepo constructor(
    private val moshi: Moshi,
) : BaseRepo<MediaItemModel>(fileStoragePath = "${appContext.filesDir.path}/$PLAYLIST_FILE_NAME") {
    val TAG = "MEDIA_REPO"
    override val jsonAdapter: JsonAdapter<List<MediaItemModel>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, MediaItemModel::class.java)
    )
    var broadcastList = mutableListOf<MediaItemModel>()

    var scheduledList = LinkedHashMap<String, List<MediaItemModel>>()

    var timeDividers = LinkedHashMap<String, List<MediaItemModel>>()

    private val downloadHelper = DownloadHelper2.getInstance()

    private val mediaStoragePath = appContext.filesDir.path

    var startTimeDefaultScenario = ""
    var endTimeDefaultScenario = ""
    var listDefaultScenario = listOf<MediaItemModel>()
    var isHaveScheduler = false

    //250mb
    val Limit_Storage: Long = 262144000

    fun updateMonthlyScenarioWss(message: String) {
        try {
            Log.e(TAG, "updateMonthlyScenario")
            ConfigUtil.putString(
                appContext,
                SCENARIO_DATA,
                message
            )
            val scenario = Utils.parseJson(jsonMessage = message, moshi = moshi)

            //lich dat theo ngay
            val mapMonthSchedule = convertWssMessToPlaylist(scenario?.payload!!)
            ConfigUtil.putString(
                appContext,
                MONTHLY_SCENARIO_WSS_DATA,
                jsonMapAdapter.toJson(mapMonthSchedule)
            )

        } catch (e: Exception) {
            Log.e(TAG, "Cannot update daily scenario ${e.message}")
        }
    }

    fun getScenarioWss(): List<MediaItemModel> {
        scheduledList.clear()
        timeDividers.clear()

        var res = listOf<MediaItemModel>()
        val scenarioData = ConfigUtil.getString(
            appContext,
            SCENARIO_DATA, ""
        )
        if (scenarioData != "") {
            val scenario = Utils.parseJson(jsonMessage = scenarioData!!, moshi = moshi)
            if (scenario != null) {
                //lich dat mac dinh
                listDefaultScenario =
                    convertToMediaItem(scenario.payload?.danhSachBanTin!!, scenario.payload!!)
                startTimeDefaultScenario = scenario.payload!!.GioBatDau!!
                endTimeDefaultScenario = scenario.payload!!.GioKeThuc!!
            }
        }

        val jsonMonthlyListData =
            ConfigUtil.getString(appContext, MONTHLY_SCENARIO_WSS_DATA, "") ?: ""
        Log.e(TAG, "get scenario $jsonMonthlyListData")
        if (jsonMonthlyListData != "") {
            try {
                val monthlyListData = jsonMapAdapter.fromJson(jsonMonthlyListData)
                if (monthlyListData?.isNotEmpty() == true) {
                    for (i in monthlyListData) {
                        isHaveScheduler = true
                        res = jsonListMediaItemAdapter.fromJson(i.value)!!
                    }
                } else {
                    Log.d(TAG, "Nothing str list to play")
                    res = listOf()
                }
            } catch (e: Exception) {
                Log.d(TAG, "getScenarioWss: ${e.message}")
            }

        } else {
            Log.d(TAG, "Nothing str list to play")
            res = listOf()
        }

        broadcastList.clear()
        broadcastList.addAll(res)
        Log.d(TAG, "local ${res.size}")
        filterMedia(broadcastList)
        return broadcastList
    }

    private fun convertToMediaItem(
        listBanTin: List<DanhSachBanTin>,
        playload: ReceivedPayloadSchedule
    ): List<MediaItemModel> {
        val listMedia = mutableListOf<MediaItemModel>()
        listBanTin.forEach {
            val obj = MediaItemModel(
                id = countId,
                mediaID = countId.toString(),
                projectID = it.banTinCongCongID,
                sourceID = playload.nguonID,
                sourceName = playload.tenNguon,
                destinationID = playload.dichID,
                destinationName = playload.tenDich,
                title = it.tieuDe,
                description = it.noiDungTomTat,
                duration = it.thoiLuong,
                contentType = Utils.convertContentType(it.loaiBanTin!!),
                fieldType = Utils.convertFiend(it.loaiLinhVuc!!),
                priority = Utils.convertPriority(it.mucDoUuTien!!),
                category = Utils.convertFiend(it.loaiLinhVuc!!),
                author = Utils.convertAuthor(it.tacGia!!),
                body = it.noiDung,
                sourceReference = it.tenDiaBanTAO,
                type = Utils.getTypeOfMediaItem(it.loaiBanTin!!.toInt()),
                createDate = it.thoiGianSANXuat.toString(),
                metadata = mutableListOf(),
                tag = mutableListOf(),
                fixTime = "",
                startTime = "",
                start = "",
                end = "",
                broadcastRegion = BroadcastRegion(
                    id = it.vungPhat,
                    value = it.vungPhat!!
                ),

                )
            countId++
            listMedia.add(obj)
        }
        return listMedia
    }

    private fun convertWssMessToPlaylist(payload: ReceivedPayloadSchedule): Map<String, String> {

        val mapNgayPhat: MutableMap<String, String> = mutableMapOf()
        val danhSachNgayPhat = payload.DanhSachNgayPhat
        //Lay lich tong cac ngay phat
        val date = Calendar.getInstance()
        val listNgayPhat: MutableList<DanhSachNgayPhat> = mutableListOf()
        Log.d(TAG, "Hom nay ${formatDateByFormat(date)}")
        danhSachNgayPhat?.forEach {
            Log.d(TAG, "Lich phat ${unixTimeStampToDateTime(it!!.ngayPhat!!)}")
            if (unixTimeStampToDateTime(it.ngayPhat!!) == formatDateByFormat(date)) {
                listNgayPhat.add(it)
            }
        }
        if (listNgayPhat.isNotEmpty()) {
            val jsonSchedule = findScheduleByDate(
                listNgayPhat,
                payload,
            )
            mapNgayPhat[formatDateByFormat(date)] =
                jsonSchedule
        }
        return mapNgayPhat
    }

    private var countId = 0
    private fun findScheduleByDate(
        listNgayPhat: MutableList<DanhSachNgayPhat>,
        playload: ReceivedPayloadSchedule
    ): String {
        val danhSachBanTinTrongNgay: MutableList<MediaItemModel> = mutableListOf()
        countId = 0
        listNgayPhat.forEach {
            it.thoiDiemPhat?.forEach {
                danhSachBanTinTrongNgay.addAll(convertToTimeFrame(it, playload))
            }
        }

        return jsonListMediaItemAdapter.toJson(danhSachBanTinTrongNgay)
    }

    private fun convertToTimeFrame(
        thoiDiemPhat: ThoiDiemPhat,
        playload: ReceivedPayloadSchedule
    ): MutableList<MediaItemModel> {
        val startTimeFrame = convertTimeStampToHHMMSS(thoiDiemPhat.thoiGianBatDau!!)
        val endTimeFrame = convertTimeStampToHHMMSS(thoiDiemPhat.thoiGianKetThuc!!)
        val newList: MutableList<MediaItemModel> = mutableListOf()
        val tObjStart = MediaItemModel(
            id = countId,
            mediaID = "0",
            projectID = "0",
            title = "Khung $startTimeFrame - $endTimeFrame",
            body = "start",
            startTime = startTimeFrame,
            start = startTimeFrame,
            fixTime = startTimeFrame,
            end = endTimeFrame,
        )
        newList.add(tObjStart)
        countId++
        thoiDiemPhat.danhSachBanTin?.forEach {
            val obj = MediaItemModel(
                id = countId,
                mediaID = countId.toString(),
                projectID = it.banTinCongCongID,
                sourceID = playload.nguonID,
                sourceName = playload.tenNguon,
                destinationID = playload.dichID,
                destinationName = playload.tenDich,
                title = it.tieuDe,
                description = it.noiDungTomTat,
                duration = it.thoiLuong,
                contentType = Utils.convertContentType(it.loaiBanTin!!),
                fieldType = Utils.convertFiend(it.loaiLinhVuc!!),
                priority = Utils.convertPriority(it.mucDoUuTien!!),
                category = Utils.convertFiend(it.loaiLinhVuc!!),
                author = Utils.convertAuthor(it.tacGia!!),
                body = it.noiDung,
                sourceReference = it.tenDiaBanTAO,
                type = Utils.getTypeOfMediaItem(it.loaiBanTin!!.toInt()),
                createDate = it.thoiGianSANXuat.toString(),
                metadata = mutableListOf(),
                tag = mutableListOf(),
                fixTime = "",
                startTime = "",
                start = "",
                end = "",
                broadcastRegion = BroadcastRegion(
                    id = it.vungPhat,
                    value = it.vungPhat!!
                ),

                )
            countId++
            newList.add(obj)
        }
        val tObEnd = MediaItemModel(
            id = countId,
            mediaID = "0",
            projectID = "0",
            title = "Khung $startTimeFrame - $endTimeFrame",
            body = "end",
            startTime = startTimeFrame,
            fixTime = endTimeFrame,
            end = endTimeFrame,
            start = startTimeFrame
        )
        newList.add(tObEnd)
        countId++
        return newList
    }


    private fun filterMedia(mediaList: List<MediaItemModel>) {
        val dividerIndices = mediaList.indices.filter {
            mediaList[it].body == "start" || mediaList[it].body == "end"
        }.chunked(2)

        val scheduledIndex = mutableListOf<Int>()

        dividerIndices.forEach {
            if (it.size == 2) {
                val startIndex = it[0]
                val endIndex = it[1]

                val key = "${mediaList[startIndex].fixTime}-${mediaList[endIndex].fixTime}"
                scheduledList[key] = mediaList.subList(startIndex + 1, endIndex)

                timeDividers[key] = listOf(broadcastList[startIndex], broadcastList[endIndex])

                //adding the index of scheduled media
                scheduledIndex.addAll((startIndex..endIndex))
            }
        }
        Log.d(TAG, "filterMedia: $scheduledList")
        Log.d(TAG, "filterMedia: $timeDividers")
    }



    fun startDownloadDefaultMedia() {
        if (listDefaultScenario.isEmpty()) return
        listDefaultScenario.forEach {
            downloadAMediaItem(it)
        }
    }

    fun startDownloadMedia() {
        if (broadcastList.isEmpty()) return

        broadcastList.forEach {
            downloadAMediaItem(it)
        }
    }


    fun downloadAMediaItem(it: MediaItemModel) {
        //1 - bản tin ký tự
        //2 - bản tin hình ảnh
        //3 - bản tin âm thanh
        //4 - Bản tin video
        //5 - Bản tin trực tiếp
        //6 - Bản tin FM
        //7 - Bản tin ghi âm trực tiếp
        //không phải files thì không tải, chỉ tải type 3 , type 4
        //Khong tai khi dung luong con 250mb(de tranh day gay mat ket noi)
        if(appStorageManager.getAvailableStorage() <= Limit_Storage) return
        if (it.body == "start" || it.body == "end") return
        if (it.contentType?.value != "4" && it.contentType?.value != "3" && it.contentType?.value != "2" ) return

        //xác định có là file hay không, lấy đuôi file
        val extMedia = File(it.body ?: "").extension

        if(extMedia.isEmpty()) return
        val isFile = getConfigFileExtension(appContext)!!.contains(extMedia)

        if (!isFile) return

        val url = it.body

        val fileName = getFileNameFromUrl(url!!)
        if (isFileExisted(mediaStoragePath, fileName)) {
            //handle broken path
            if (it.body != "$mediaStoragePath/$fileName") {
                Log.d("ĐÂSDF", "${mediaStoragePath}/${fileName}")
                Log.d("ĐÂSDF", "filterMedia: ${it.body}")
                it.body = "$mediaStoragePath/$fileName"
//                coroutineScope.launch {
//                    saveItemsToFile(broadcastList)
//                    Log.e("SavefileLog", "Save in downloadAMediaItem: Case handle broken path")
//                }
            }
            return
        }

        Log.d("downloaditem", url)

        downloadHelper.addToQueue(DownloadItem().apply {
            itDownloadUrl = url
            itStoragePath = mediaStoragePath
            itFileName = fileName
            priority = 0
            downloadListener = object : OnDownloadListener {
                override fun onDownloadComplete() {
                    it.pathBackup = it.body!!
                    it.body = "$mediaStoragePath/$fileName"

                    coroutineScope.launch {
                        saveItemsToFile(broadcastList)
                        Log.e("SavefileLog", "Save in onDownloadComplete: onDownloadComplete")
                    }

                    Log.d("downloadcomplete", it.body!!)
                }

                override fun onError(error: Error?) {
                    Log.d("downloaderror", it.body!!)
                }

            }
        })
    }
}