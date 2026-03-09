package app.tek4tv.ipradio.core

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import app.tek4tv.ipradio.core.mqtt.MQTTManager
import app.tek4tv.ipradio.di.JsonAdapterProvider.adapterSchedulerList
import app.tek4tv.ipradio.di.JsonAdapterProvider.bootstrapAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.fromVs
import app.tek4tv.ipradio.di.JsonAdapterProvider.jsonMediaItemAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.listAppDataItemAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.listMediaFileItemAdapter
import app.tek4tv.ipradio.di.MoshiInst.moshi
import app.tek4tv.ipradio.model.BootstrapResp
import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.OphMediaV3Data
import app.tek4tv.ipradio.model.OphRebootData
import app.tek4tv.ipradio.model.RepeatDays
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.network.ApiService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.ConfigUtil.SCHEDULE_WEEK
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.Utils.Companion.convertSecondsToCalendar
import app.tek4tv.ipradio.utils.convertCalendarToString
import app.tek4tv.ipradio.utils.crypto.b64Decode
import app.tek4tv.ipradio.utils.unixTimeStampToDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

@RequiresApi(Build.VERSION_CODES.N)
class HubManager(
    private val authService: ApiService,
    private val appContext: Context,
    private val coroutineScope: CoroutineScope,
    private var onMessageListener: (command: String?, message: String?) -> Unit,
) : IConnectionServer {
    private val TAG = "HubManager"

    private val CONNECT_COUNTER_LIMIT = 120
    private val PING_HUB_INTERVAL = 60000L
    private val API_CALL_INTERVAL = 60000L

    private var pingHubJob: Job? = null

    //    private var getTokenJob: Job? = null
    private var getBootstrapInfoJob: Job? = null

    //    val deviceImei = Utils.getDeviceId(appContext)
    val deviceImei = "54:78:C9:B8:10:0E"

    var pingCounter = 0

    var responseHub = ResponseHub()

    var currentMediaItem: MediaItem? = null

    var versionName = "M62.4.2c"

    private var bootstrapInfo: BootstrapResp? = null

    var isConnectSuccess = false

    private var getMQTTInfoJob: Job? = null

    var mqttClient: MQTTManager? = null


    //In BT Mode
    fun getBootstrapInfo() {
        try {
            val bootstrapInfoStr =
                ConfigUtil.getStringEncrypt(appContext, ConfigUtil.BOOTSTRAP_INFO)
            if (bootstrapInfoStr != "") {
                bootstrapInfo = bootstrapAdapter.fromJson(bootstrapInfoStr)
            } else {
                val call = authService.getBootstrapInfo("ss", "ss")
                val response = call.execute()
                if (response.isSuccessful) {
                    Log.i(TAG, "getBootstrapInfo Success")
                    ConfigUtil.putStringEncrypt(
                        appContext,
                        ConfigUtil.BOOTSTRAP_INFO,
                        bootstrapAdapter.toJson(response.body())
                    )
                    bootstrapInfo = response.body()
                } else {
                    Log.i(TAG, "getBootstrapInfo Success - Data null")
                    bootstrapInfo = null
                }
            }
        } catch (e: Exception) {
            bootstrapInfo = null
            Log.i(TAG, "getBootstrapInfo error: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun connectToServer() {
        getBootstrapInfoJob?.cancel()
        getBootstrapInfoJob = CoroutineScope(Dispatchers.Default).launch {
            while (bootstrapInfo == null) {
                getBootstrapInfo()
                if (bootstrapInfo == null) {
                    pingCounter++
                    delay(API_CALL_INTERVAL)
                    continue
                }
                // Cancel and start a new MQTT info retrieval job
                getMQTTInfoJob?.cancel()
                getMQTTInfoJob = launch {
                    mqttClient = MQTTManager(
                        appContext,
                        this@HubManager,
                        bootstrapInfo!!.thingID!!,
                        bootstrapInfo!!.thingKey!!,
                        bootstrapInfo!!.channels
                    )
                    while (!isConnectSuccess) {
                        mqttClient?.connectToMQTT()
                        delay(5000)
                    }

                }

            }
        }
    }

    fun sendGeneric() {

    }

    fun sendSettings() {

    }

    fun sendStatus() {

    }


    fun sendTest(mess: String) {
        val appdata = listAppDataItemAdapter.fromJson(mess)!!.first()
        Log.d("MGWtest", "sendTest: ${appdata.n}")
        Log.d("MGWtest", "sendTest: ${appdata.t}")
        Log.d("MGWtest", "sendTest: ${appdata.vs}")
        val name = appdata.n.split(":")
        val time = appdata.t
        val data = appdata.vs

        var hubCommand = ""
        val cmd = name[2]
        val message = ""
        when (cmd) {
            HubCommand.OPH_SETTINGS -> {

            }

            HubCommand.OPH_REBOOT -> {
                val data = moshi.fromVs<OphRebootData>(data)
                Log.d("MGWtest", "Test: ${cmd} - ${data!!.option} ")
                hubCommand = HubCommand.RESTART
                onMessageListener.invoke(hubCommand, data.option.toString())
            }

            HubCommand.OPH_MEDIA_V3 -> {
                val data = moshi.fromVs<OphMediaV3Data>(data)
                val records = data!!.records
                val showInfo = b64Decode(data!!.records.first().showInfo!!)
                Log.d("MGWtest", "Test: OPH_MEDIA_V3 showInfo base64 de ${showInfo} ")
                val mediaInfo = moshi.fromVs<MediaInfo>(showInfo)
                Log.d("MGWtest", "Test: OPH_MEDIA_V3 mediaInfo ${mediaInfo} ")
                mediaInfo!!.files.forEach { it ->
                    Log.d("MGWtest", "Test: OPH_MEDIA_V3 MediaFile ${it.url} ")
                }
                val listMediaInfo = mutableListOf<MediaInfo>()
                //
                records.forEach { showInfo ->
                    when (showInfo.action) {
                        //Tạo
                        //Cần làm rõ tạo này thì có phát luôn ko. ở cả mode schedule và live
                        //Sẽ làm theo kiểu cho phát ở action tạo này?
                        //Case: 1 lịch trong ngày có các khung giờ. vậy khi 1 bản tin nữa đc tạo hay cập nhật sẽ thế nào?
                        // thay thế hay nối. nối thì lúc đè frame thì sao
                        1 -> {
                            //Check current schedule week list
//                            val currentScheduleWeekList =
//                                ConfigUtil.getString(appContext, SCHEDULE_WEEK, "")
//                            if (currentScheduleWeekList != "" && currentScheduleWeekList != null) {
//                                val scheduleWeekMap =
//                                    adapterSchedulerList.fromJson(currentScheduleWeekList)
//
//                            } else {
//
//                            }
                            val listMediaShowScheduler =
                                listMediaInfo.filter { it.mode == 1 }.toMutableList()
                            listMediaShowScheduler.forEach { mediaInfo ->
                                val now = Calendar.getInstance()
                                //Tính của các
                                Log.d("MediaInfo", "Lập lịch")
                                val r1 = decodeRepeatDays(mediaInfo.repeat!!, mediaInfo.days!!)
                                val res = repeatDaysToText(r1)
                                Log.d("MediaInfo", "Chạy vào: $res")
                                val idx = todayWeeklyIndex(now)
                                //                    val dayOfMonth = now.get(Calendar.DAY_OF_MONTH) // 1..31
                                //                    val allowed = (r1 as? RepeatDays.Monthly)?.daysOfMonth?.contains(dayOfMonth) == true
                                //val isAllowed = isTodayAllowed(now, r1)
                                val isAllowed = isTodayAllowed(now, r1)
                                Log.d(
                                    "MediaInfo",
                                    "Kiểm tra: nay $idx có phải ngày phát: ${isAllowed}"
                                )
                                if (isAllowed) {
                                    Log.d(
                                        "MediaInfo",
                                        "Lập lịch ${if (mediaInfo.repeat == 0) "tuần" else "tháng"}"
                                    )
                                    val startTime = unixSecondsToCalendar(mediaInfo.start!!)
                                    val expiredTime = unixSecondsToCalendar(mediaInfo.expired!!)
                                    val timeSlot = mediaInfo.ts
                                    val durationSlot = mediaInfo.ds
                                    Log.d(
                                        "MediaInfo",
                                        "Thời gian phát: ${convertCalendarToString(startTime)}"
                                    )
                                    Log.d(
                                        "MediaInfo",
                                        "Thời gian kết thúc: ${
                                            convertCalendarToString(
                                                expiredTime
                                            )
                                        }"
                                    )
                                    val scheduledList = LinkedHashMap<String, List<MediaFile>>()
                                    timeSlot!!.forEachIndexed { index, ts ->
                                        val slotStart = convertSecondsToCalendar(ts.toInt())
                                        val slotEnd =
                                            convertSecondsToCalendar(ts.toInt()).apply {
                                                add(
                                                    Calendar.SECOND,
                                                    durationSlot!![index].toInt()
                                                )
                                            }
                                        Log.d(
                                            "MediaInfo",
                                            "Khung phát: ${
                                                convertCalendarToString(
                                                    slotStart,
                                                    "HH:mm:ss"
                                                )
                                            } - ${
                                                convertCalendarToString(
                                                    slotEnd, "HH:mm:ss"
                                                )
                                            } - Thời lượng: ${durationSlot!![index]}"
                                        )
                                        Log.d("MediaInfo", "---")
                                        val timeFrame = "${slotStart}-${slotEnd}"
                                        scheduledList[timeFrame] = mediaInfo.files
                                    }
                                    val scheduleStr = adapterSchedulerList.toJson(scheduledList)
                                    ConfigUtil.putString(appContext, SCHEDULE_WEEK, scheduleStr)
                                    hubCommand = HubCommand.UPDATE_WEEKLY_SCHEDULER
                                    responseHub = ResponseHub()
                                    responseHub.message = scheduleStr
                                    onMessageListener.invoke(hubCommand, scheduleStr)


                                } else {
                                    Log.d("MediaInfo", "Không có lịch hôm nay")
                                }

                            }
                        }
                        //Sửa
                        2 -> {

                        }
                        //Xóa
                        4 -> {

                        }
                        //Phát
                        8 -> {
                            val mediaInfo = moshi.fromVs<MediaInfo>(b64Decode(showInfo.showInfo!!))
                            listMediaInfo.add(mediaInfo!!)
                            //
                            //case: 2 show, mediainfo 1 là imme, 1 là schedule
                            //case: 3 show, mediainfo 2 là imme, 1 là schedule
                            val listMediaShowImmediate =
                                listMediaInfo.filter { it.mode == 2 }.toMutableList()
                            if (listMediaShowImmediate.isNotEmpty()) {
                                val mediaInfo = findHighestPriorityMediaShow(listMediaShowImmediate)
                                if (mediaInfo != null) {
                                    Log.d("MediaInfo", "Mode: Live ")
                                    val now = Calendar.getInstance()
                                    val startTime = unixSecondsToCalendar(mediaInfo.start!!)
                                    Log.d(
                                        "MediaInfo", "startTime: ${mediaInfo.start} ${
                                            convertCalendarToString(
                                                startTime
                                            )
                                        }"
                                    )
                                    val expiredTime = unixSecondsToCalendar(mediaInfo.expired!!)
                                    Log.d(
                                        "MediaInfo", "expiredTime: ${mediaInfo.expired} ${
                                            convertCalendarToString(
                                                expiredTime
                                            )
                                        }"
                                    )
                                    if (!now.before(expiredTime)) { // now >= expiredTime
                                        Log.d("MediaInfo", "Đã hết giờ phát (expired)")
                                        return@forEach // thoát khỏi function xử lý mediaInfo luôn
                                    }
                                    if (now.before(startTime)) {
                                        Log.d("MediaInfo", "Chưa đến giờ phát (start)")
                                        return@forEach
                                    }
                                    val timeSlot = mediaInfo.ts
                                    val durationSlot = mediaInfo.ds
                                    var shouldPlay = false
                                    //xem lại live có cần phải check ds. ts ko..
                                    if (timeSlot != null && durationSlot != null && durationSlot.size == timeSlot.size) {
                                        timeSlot.forEachIndexed { index, ts ->
                                            if (shouldPlay) return@forEachIndexed
                                            val slotStart = convertSecondsToCalendar(ts.toInt())
                                            Log.d(
                                                "MediaInfo",
                                                "slotStart: ${convertCalendarToString(slotStart)}"
                                            )
                                            val slotEnd =
                                                convertSecondsToCalendar(ts.toInt()).apply {
                                                    add(
                                                        Calendar.SECOND, durationSlot[index].toInt()
                                                    )
                                                }
                                            Log.d(
                                                "MediaInfo",
                                                "slotEnd: ${convertCalendarToString(slotEnd)}"
                                            )

                                            when {
                                                now < slotStart -> {
                                                    Log.d("MediaInfo", "Chưa đến khung giờ phát")
                                                }

                                                now >= slotEnd -> {
                                                    Log.d("MediaInfo", "Đã hết khung phát")
                                                }

                                                else -> { // slotStart <= now < slotEnd, và đã qua start, chưa expired
                                                    Log.d(
                                                        "MediaInfo", "Đang phát trong khung: ${
                                                            convertCalendarToString(
                                                                slotStart, "HH:mm:ss"
                                                            )
                                                        } - ${
                                                            convertCalendarToString(
                                                                slotEnd, "HH:mm:ss"
                                                            )
                                                        } duration=${
                                                            durationSlot.getOrNull(index)
                                                        }"
                                                    )
                                                    // nếu chỉ phát 1 slot thì return/break ở đây
                                                    shouldPlay = true
                                                    try {
                                                        hubCommand = HubCommand.LIVE
                                                        responseHub = ResponseHub()
                                                        responseHub.type = HubCommand.LIVE
                                                        responseHub.level = mediaInfo.prio!!
                                                        responseHub.duration =
                                                            ts.toInt() + durationSlot[index].toInt()
                                                        responseHub.message =
                                                            listMediaFileItemAdapter.toJson(
                                                                mediaInfo.files
                                                            )
                                                        onMessageListener.invoke(hubCommand, "")
                                                    } catch (e: Exception) {
                                                        Log.d(TAG, "Live fail: ${e.message}")
                                                    }
                                                    return@forEachIndexed
                                                }
                                            }
                                        }
                                    } else {
                                        Log.d("MediaInfo", "Giờ phát hoặc thời lượng không hợp lệ")
                                    }
                                }
                            }
                        }
                        //Dừng
                        16 -> {

                        }
                    }
                }
            }

            HubCommand.OPH_MEDIA_DELETE_ALL -> {
                hubCommand = HubCommand.DELETE_ALL_MEDIA
                onMessageListener.invoke(hubCommand, "")
            }
        }

        convertToHubCommand(cmd, message)
    }


    fun unixSecondsToCalendar(
        unixSeconds: Long, tz: TimeZone = TimeZone.getDefault()
    ): Calendar {
        return Calendar.getInstance(tz).apply {
            timeInMillis = unixSeconds * 1000L
        }
    }

    fun findHighestPriorityMediaShow(items: List<MediaInfo>): MediaInfo? {
        // Sử dụng maxByOrNull để tìm item có priority lớn nhất
        // Nếu danh sách trống, nó sẽ trả về null
        return items.maxByOrNull { it.prio!! }
    }


    fun isTodayAllowed(now: Calendar, result: RepeatDays): Boolean = when (result) {
        is RepeatDays.Weekly -> {
            val idx = todayWeeklyIndex(now)
            idx in result.weekdays
        }

        is RepeatDays.Monthly -> {
            val dom = now.get(Calendar.DAY_OF_MONTH)
            dom in result.daysOfMonth
        }
    }

    fun todayWeeklyIndex(now: Calendar): Int {
        val dow = now.get(Calendar.DAY_OF_WEEK) // 1..7 (Sun..Sat)
        return when (dow) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
    }

    fun repeatDaysToText(result: RepeatDays): String = when (result) {
        is RepeatDays.Weekly -> {
            val names = listOf("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "CN")
            result.weekdays.joinToString(", ") { names[it] }
        }

        is RepeatDays.Monthly -> {
            result.daysOfMonth.joinToString(", ") { "Day $it" }
        }
    }

    fun decodeRepeatDays(repeat: Int, daysMask: Int): RepeatDays {
        return when (repeat) {
            0 -> { // weekly: bit 0..6
                val weekdays = (0..6).filter { bit ->
                    (daysMask and (1 shl bit)) != 0
                }
                RepeatDays.Weekly(weekdays)
            }

            1 -> { // monthly: bit 0..30 => day 1..31
                val dom = (1..31).filter { day ->
                    val bit = day - 1
                    (daysMask and (1 shl bit)) != 0
                }
                RepeatDays.Monthly(dom)
            }

            else -> {
                // nếu repeat lạ thì coi như không chọn ngày nào
                RepeatDays.Weekly(emptyList())
            }
        }
    }

    fun isMonthlyDaySelected(days: Int, dayOfMonth: Int): Boolean {
        return (days and (1 shl (dayOfMonth - 1))) != 0
    }

    fun isWeeklyDaySelected(daysMask: Int, weekdayIndex: Int): Boolean {
        if (weekdayIndex !in 0..6) return false
        val mask = 1 shl weekdayIndex
        return (daysMask and mask) != 0
    }

    private fun convertToHubCommand(cmd: String, data: String) {

//        try {
//            val mqttMessage = baseMqttMessageAdapter.fromJson(jsonMessage)
//            var hubCommand = ""
//            when (mqttMessage!!.topic) {
//                HubCommand.DV002 -> {
//                    val body = mqttMessage.payload
//                    if (body != null) {
//                        try {
//                            val payload = schedulePayloadAdapter.fromJsonValue(body)
//                            if (payload == null) return
//                            val mapMonthSchedule = convertWssMessToPlaylist(payload)
//                            val jsonMess = jsonMapAdapter.toJson(mapMonthSchedule)
//                            hubCommand = HubCommand.UPDATE_MONTHLY_WSS
//                            onMessageListener.invoke(hubCommand, jsonMess)
//                            coroutineScope.launch(Dispatchers.Main) {
//                                onMessageListener.invoke(hubCommand, jsonMess)
//                                sendLogSchedule(body)
//                            }
//                        } catch (e: Exception) {
//                            Log.d(TAG, "dv002 fail: ${e.message}")
//                        }
//
//                    }
//                }
//
//                HubCommand.DV003 -> {
//                    val body = mqttMessage.payload
//                    if (body != null) {
//                        try {
//                            val payload = livePayloadAdapter.fromJsonValue(body)
//                            hubCommand = HubCommand.LIVE
//                            responseHub = ResponseHub().apply {
//                                type = HubCommand.LIVE
//                                level = payload?.mucDoUuTien!!.toInt()
//                                duration = payload.thoiLuong.toString()
//                                message = payload.noiDung
//                                userNameControl = ""
//                                name = mediaItemMoshiAdapter.toJson(
//                                    MediaItem(
//                                        projectID = payload.banTinID,
//                                        mediaID = payload.banTinID,
//                                        id = 0,
//                                        sourceID = payload.nguonID,
//                                        sourceName = payload.tenNguon,
//                                        destinationID = payload.danhSachDiaBanNhan?.firstOrNull()?.dichID,
//                                        destinationName = payload.danhSachDiaBanNhan?.firstOrNull()?.tenDich,
//                                        title = payload.tieuDe,
//                                        description = payload.noiDungTomTat,
//                                        duration = payload.thoiLuong,
//                                        body = payload.noiDung,
//                                        sourceReference = payload.nguonID?.split(".")
//                                            ?.firstOrNull(),
//                                        startTime = "00:00:00",
//                                        fieldType = Category(
//                                            id = payload.loaiLinhVuc?.toLong(),
//                                            value = payload.loaiLinhVuc
//                                        ),
//                                        type = "LIVE",
//                                        fixTime = "00:00:00",
//                                        createDate = payload.thoiGianSANXuat,
//                                        contentType = ContentType(
//                                            id = payload.loaiBanTin?.toLong(),
//                                            value = payload.loaiBanTin
//                                        ),
//                                        priority = Priority(
//                                            id = payload.mucDoUuTien.toLong(),
//                                            value = payload.mucDoUuTien
//                                        ),
//                                        author = Author(
//                                            userName = payload.tacGia?.butDanh,
//                                            email = payload.tacGia?.email,
//                                            fullName = payload.tacGia?.tenDayDu
//                                        ),
//                                    )
//                                )
//                            }
//                            Log.d("wss", "convertWssToHubCommad: ${hubCommand}-${jsonMessage}")
//                            coroutineScope.launch(Dispatchers.Main) {
//                                onMessageListener.invoke(hubCommand, jsonMessage)
//                            }
//                        } catch (e: Exception) {
//                            Log.d(TAG, "dv003 fail: ${e.message}")
//                        }
//
//                    }
//
//                }
////            //Hủy lịch
//                HubCommand.DV004 -> {
//                    val body = mqttMessage.payload
//                    if (body != null) {
//                        hubCommand = HubCommand.DELETE_MONTHLY_LIST_WSS
//                        coroutineScope.launch(Dispatchers.Main) {
//                            onMessageListener.invoke(hubCommand, "null")
//                        }
//                        sendCancelScheduleLogSchedule(body)
//                    }
//                }
//                //dieu khien volume, pa
//                HubCommand.DV009 -> {
//                    val body = mqttMessage.payload
//                    if (body != null) {
//                        val payload = controllerPayloadAdapter.fromJsonValue(body)
//                        val maLenh = payload?.dieuKhienThietBi?.maLenh
//                        val giaTri = payload?.dieuKhienThietBi?.thamSo
//
//                        when (maLenh) {
//                            //0 - Điều khiển volume
//                            "0" -> {
//                                hubCommand = HubCommand.SET_VOLUME_DEVICE
//                                responseHub = ResponseHub().apply {
//                                    message = giaTri
//                                }
//                            }
//                            //1 - Điều khiển công suất
//                            "1" -> {
//                                hubCommand = HubCommand.TURN_ON_PA
//                                //update theo 2455, ngược vs bản cũa
//                                //0 - tắt
//                                //1 - bật
//                                if (giaTri == "0") {
//                                    responseHub = ResponseHub().apply {
//                                        message = "0"
//                                    }
//                                } else {
//                                    responseHub = ResponseHub().apply {
//                                        message = "1"
//                                    }
//                                }
//                            }
//                            /*  Dừng phát nội dung
//                            1 - Dừng phát bản tin, chuyển sang bản tin tiếp theo của lịch nếu có
//                            2 - Dừng phát
//                            3 - Phát tiếp nội dụng đã dừng*/
//                            "2" -> {
//                                hubCommand = HubCommand.CONTROL_MEDIA
//                                if (giaTri == "0") {
//                                    responseHub = ResponseHub().apply {
//                                        message = "0"
//                                    }
//                                }
//                                if (giaTri == "1") {
//                                    responseHub = ResponseHub().apply {
//                                        message = "1"
//                                    }
//                                }
//                                if (giaTri == "2") {
//                                    responseHub = ResponseHub().apply {
//                                        message = "2"
//                                    }
//                                }
//                                if (giaTri == "3") {
//                                    responseHub = ResponseHub().apply {
//                                        message = "3"
//                                    }
//                                }
//                            }
//                            //Reset - Tham số (số giây khởi động lại)
//                            "3" -> {
//                                hubCommand = HubCommand.RESTART
//                                responseHub = ResponseHub().apply {
//                                    message = (giaTri?.toIntOrNull() ?: 0).toString()
//                                }
//                            }
//                            //update thiet bi
//                            "4" -> {
//                                hubCommand = HubCommand.UPDATE_VERSION
//                                if (giaTri != "") {
//                                    responseHub = ResponseHub().apply {
//                                        message = giaTri
//                                    }
//                                }
//                            }
//                        }
//                        onMessageListener.invoke(hubCommand, jsonMessage)
//
//                    }
//                }
//
//            }
//        } catch (e: Exception) {
//            Log.d(TAG, "convertToHubCommand: ${e.message}")
//        }

    }

    override fun onConnectSuccess() {
        isConnectSuccess = true
        getMQTTInfoJob?.cancel()
        startPingTimer()
    }

    override fun onConnectFailure(message: String?) {
        isConnectSuccess = false
    }

    override fun onReceiveMessageSuccess(message: String) {
        pingCounter = 0
        // convertToHubCommand(message)
    }

    private fun sendMessage(message: String) {
        if (mqttClient != null) {
            mqttClient?.publishToMQTT("", message)
        }
    }

    private fun sendPing() {
//        pingCounter++
//        var trangThaiHoatDong: String
//        var noiDungPhat: String
//        if (currentMediaItem != null) {
//            noiDungPhat = currentMediaItem!!.pathBackup
//            trangThaiHoatDong = "0"
//        } else {
//            noiDungPhat = ""
//            trangThaiHoatDong = "2"
//        }
//        val payload = StatusPayload(
//            amLuong = SerialPortController.volume,
//            cumLoaId = deviceImei,
//            dichId = dichId,
//            viDo = GlobalProperties.getInst().lat.toDouble(),
//            kinhDo = GlobalProperties.getInst().lng.toDouble(),
//            maNhaCungCap = GlobalProperties.getInst().providerValue,
//            nguonId = nguonId,
//            noiDungPhat = noiDungPhat,
//            phienBanUngDung = versionName,
//            tenDich = tenDich,
//            tenLoaiThietBi = "Cụm loa truyền thanh",
//            tenNguon = tenNguon,
//            tenNhaCungCap = GlobalProperties.getInst().providerValue,
//            tenThietBi = GlobalProperties.getInst().nameDevice,
//            thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
//            trangThaiHoatDong = trangThaiHoatDong,
//            trangThaiKetNoi = NetworkUtils.getNetworkInfo(appContext),
//        )
//        val message = BaseMqttMessage(
//            id = "",
//            topic = HubCommand.DV008,
//            payload = payload
//        )
//        sendMessage(baseMqttMessageAdapter.toJson(message))
    }

//    private fun convertToHubCommand(jsonMessage: String) {
//        try {
//            val mqttMessage = baseMqttMessageAdapter.fromJson(jsonMessage)
//            var hubCommand = ""
//            when (mqttMessage!!.topic) {
//                HubCommand.DV002 -> {
//                    val body = mqttMessage.payload
//                    if (body != null) {
//                        try {
//                            val payload = schedulePayloadAdapter.fromJsonValue(body)
//                            if (payload == null) return
//                            val mapMonthSchedule = convertWssMessToPlaylist(payload)
//                            val jsonMess = jsonMapAdapter.toJson(mapMonthSchedule)
//                            hubCommand = HubCommand.UPDATE_MONTHLY_WSS
//                            onMessageListener.invoke(hubCommand, jsonMess)
//                            coroutineScope.launch(Dispatchers.Main) {
//                                onMessageListener.invoke(hubCommand, jsonMess)
//                                sendLogSchedule(body)
//                            }
//                        } catch (e: Exception) {
//                            Log.d(TAG, "dv002 fail: ${e.message}")
//                        }
//
//                    }
//                }
//
//                HubCommand.DV003 -> {
//                    val body = mqttMessage.payload
//                    if (body != null) {
//                        try {
//                            val payload = livePayloadAdapter.fromJsonValue(body)
//                            hubCommand = HubCommand.LIVE
//                            responseHub = ResponseHub().apply {
//                                type = HubCommand.LIVE
//                                level = payload?.mucDoUuTien!!.toInt()
//                                duration = payload.thoiLuong.toString()
//                                message = payload.noiDung
//                                userNameControl = ""
//                                name = mediaItemMoshiAdapter.toJson(
//                                    MediaItem(
//                                        projectID = payload.banTinID,
//                                        mediaID = payload.banTinID,
//                                        id = 0,
//                                        sourceID = payload.nguonID,
//                                        sourceName = payload.tenNguon,
//                                        destinationID = payload.danhSachDiaBanNhan?.firstOrNull()?.dichID,
//                                        destinationName = payload.danhSachDiaBanNhan?.firstOrNull()?.tenDich,
//                                        title = payload.tieuDe,
//                                        description = payload.noiDungTomTat,
//                                        duration = payload.thoiLuong,
//                                        body = payload.noiDung,
//                                        sourceReference = payload.nguonID?.split(".")
//                                            ?.firstOrNull(),
//                                        startTime = "00:00:00",
//                                        fieldType = Category(
//                                            id = payload.loaiLinhVuc?.toLong(),
//                                            value = payload.loaiLinhVuc
//                                        ),
//                                        type = "LIVE",
//                                        fixTime = "00:00:00",
//                                        createDate = payload.thoiGianSANXuat,
//                                        contentType = ContentType(
//                                            id = payload.loaiBanTin?.toLong(),
//                                            value = payload.loaiBanTin
//                                        ),
//                                        priority = Priority(
//                                            id = payload.mucDoUuTien.toLong(),
//                                            value = payload.mucDoUuTien
//                                        ),
//                                        author = Author(
//                                            userName = payload.tacGia?.butDanh,
//                                            email = payload.tacGia?.email,
//                                            fullName = payload.tacGia?.tenDayDu
//                                        ),
//                                    )
//                                )
//                            }
//                            Log.d("wss", "convertWssToHubCommad: ${hubCommand}-${jsonMessage}")
//                            coroutineScope.launch(Dispatchers.Main) {
//                                onMessageListener.invoke(hubCommand, jsonMessage)
//                            }
//                        } catch (e: Exception) {
//                            Log.d(TAG, "dv003 fail: ${e.message}")
//                        }
//
//                    }
//
//                }
////            //Hủy lịch
//                HubCommand.DV004 -> {
//                    val body = mqttMessage.payload
//                    if (body != null) {
//                        hubCommand = HubCommand.DELETE_MONTHLY_LIST_WSS
//                        coroutineScope.launch(Dispatchers.Main) {
//                            onMessageListener.invoke(hubCommand, "null")
//                        }
//                        sendCancelScheduleLogSchedule(body)
//                    }
//                }
//                //dieu khien volume, pa
//                HubCommand.DV009 -> {
//                    val body = mqttMessage.payload
//                    if (body != null) {
//                        val payload = controllerPayloadAdapter.fromJsonValue(body)
//                        val maLenh = payload?.dieuKhienThietBi?.maLenh
//                        val giaTri = payload?.dieuKhienThietBi?.thamSo
//
//                        when (maLenh) {
//                            //0 - Điều khiển volume
//                            "0" -> {
//                                hubCommand = HubCommand.SET_VOLUME_DEVICE
//                                responseHub = ResponseHub().apply {
//                                    message = giaTri
//                                }
//                            }
//                            //1 - Điều khiển công suất
//                            "1" -> {
//                                hubCommand = HubCommand.TURN_ON_PA
//                                //update theo 2455, ngược vs bản cũa
//                                //0 - tắt
//                                //1 - bật
//                                if (giaTri == "0") {
//                                    responseHub = ResponseHub().apply {
//                                        message = "0"
//                                    }
//                                } else {
//                                    responseHub = ResponseHub().apply {
//                                        message = "1"
//                                    }
//                                }
//                            }
//                            /*  Dừng phát nội dung
//                            1 - Dừng phát bản tin, chuyển sang bản tin tiếp theo của lịch nếu có
//                            2 - Dừng phát
//                            3 - Phát tiếp nội dụng đã dừng*/
//                            "2" -> {
//                                hubCommand = HubCommand.CONTROL_MEDIA
//                                if (giaTri == "0") {
//                                    responseHub = ResponseHub().apply {
//                                        message = "0"
//                                    }
//                                }
//                                if (giaTri == "1") {
//                                    responseHub = ResponseHub().apply {
//                                        message = "1"
//                                    }
//                                }
//                                if (giaTri == "2") {
//                                    responseHub = ResponseHub().apply {
//                                        message = "2"
//                                    }
//                                }
//                                if (giaTri == "3") {
//                                    responseHub = ResponseHub().apply {
//                                        message = "3"
//                                    }
//                                }
//                            }
//                            //Reset - Tham số (số giây khởi động lại)
//                            "3" -> {
//                                hubCommand = HubCommand.RESTART
//                                responseHub = ResponseHub().apply {
//                                    message = (giaTri?.toIntOrNull() ?: 0).toString()
//                                }
//                            }
//                            //update thiet bi
//                            "4" -> {
//                                hubCommand = HubCommand.UPDATE_VERSION
//                                if (giaTri != "") {
//                                    responseHub = ResponseHub().apply {
//                                        message = giaTri
//                                    }
//                                }
//                            }
//                        }
//                        onMessageListener.invoke(hubCommand, jsonMessage)
//
//                    }
//                }
//
//            }
//        } catch (e: Exception) {
//            Log.d(TAG, "convertToHubCommand: ${e.message}")
//        }

//    }

    private fun sendCancelScheduleLogSchedule(body: Any) {
//        try {
//            val receivedPayload = cancelPayloadAdapter.fromJsonValue(body)
//            val payload = LogCancelSchedulePayload(
//                dichID = receivedPayload?.nguonID,
//                tenDich = receivedPayload?.tenNguon,
//                lichPhatID = receivedPayload?.lichPhatID,
//                nguonID = receivedPayload?.nguonID,
//                tenNguon = receivedPayload?.tenNguon,
//                tenLichPhat = receivedPayload?.tenLichPhat,
//                danhSachThietBi = listOf(
//                    ThietBi(
//                        cumLoaId = deviceImei,
//                        maNhaCungCap = GlobalProperties.getInst().providerValue,
//                        tenNhaCungCap = GlobalProperties.getInst().providerValue,
//                        tenThietBi = GlobalProperties.getInst().nameDevice,
//                    )
//                ),
//
//
//                )
//            val baseMqttMessage = BaseMqttMessage(
//                id = "", topic = HubCommand.DV007, payload = payload
//            )
//            sendMessage(baseMqttMessageAdapter.toJson(baseMqttMessage))
//
//        } catch (e: Exception) {
//            Log.d(TAG, "sendLogSchedule: ${e.message}")
//        }


    }

    private fun sendLogSchedule(body: Any) {
//        try {
//            val receivedPayload = schedulePayloadAdapter.fromJsonValue(body)
//            val payload = LogAddSchedulePayload(
//                danhSachBanTin = receivedPayload?.danhSachBanTin,
//                danhSachDiaBan = receivedPayload?.danhSachDiaBanNhan,
//                danhSachThietBi = listOf(
//                    ThietBi(
//                        cumLoaId = deviceImei,
//                        maNhaCungCap = GlobalProperties.getInst().providerValue,
//                        tenNhaCungCap = GlobalProperties.getInst().providerValue,
//                        tenThietBi = GlobalProperties.getInst().nameDevice,
//                    )
//                ),
//                dichId = receivedPayload?.dichID,
//                lichPhatId = receivedPayload?.lichPhatID,
//                nguonId = receivedPayload?.nguonID,
//                tenDich = receivedPayload?.tenDich,
//                tenLichPhat = receivedPayload?.tenLichPhat,
//                tenNguon = receivedPayload?.tenNguon
//            )
//            val baseMqttMessage = BaseMqttMessage(
//                id = "", topic = HubCommand.DV006, payload = payload
//            )
//            sendMessage(baseMqttMessageAdapter.toJson(baseMqttMessage))
//        } catch (e: Exception) {
//            Log.d(TAG, "sendLogSchedule: ${e.message}")
//        }
    }

    fun sendLogMediaSuccess(mediaItem: MediaItem, paState: String) {
//        try {
//            val tacGia = TacGia(
//                tenDayDu = mediaItem.author?.fullName ?: "",
//                butDanh = mediaItem.author?.userName ?: "",
//                email = mediaItem.author?.email ?: ""
//            )
//
//            val payload = LogMediaSuccessPayload(
//                amLuong = SerialPortController.volume,
//                banTinId = mediaItem.projectID,
//                congSuat = paState,
//                cumLoaId = deviceImei,
//                dichId = dichId,
//                loaiBanTin = mediaItem.contentType?.value,
//                loaiLinhVuc = mediaItem.fieldType?.value,
//                maNhaCungCap = GlobalProperties.getInst().providerValue,
//                mucDoUuTien = mediaItem.priority?.id.toString(),
//                nguonId = nguonId,
//                nguonTin = mediaItem.sourceName,
//                tacGia = tacGia,
//                tenDich = tenDich,
//                tenNhaCungCap = GlobalProperties.getInst().providerValue,
//                tenThietBi = GlobalProperties.getInst().nameDevice,
//                thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
//                thoiGianSanXuat = mediaItem.createDate,
//                thoiLuong = mediaItem.duration,
//                tiepAm = mediaItem.isExternalSource,
//                tieuDe = mediaItem.title,
//                noiDung = mediaItem.pathBackup,
//                noiDungTomTat = mediaItem.description,
//            )
//
//            val baseMqttMessage = BaseMqttMessage(
//                id = "", topic = HubCommand.DV005, payload = payload
//            )
//            sendMessage(baseMqttMessageAdapter.toJson(baseMqttMessage))
//        } catch (e: Exception) {
//
//            Log.d(TAG, "sendLogMediaSuccess: ${mediaItem.createDate}")
//            Log.d(TAG, "sendLogMediaSuccess: ${e.message}")
//        }

    }


//    private val listDate = mutableListOf<String>()
//    private fun findDate(danhSachNgayPhat: List<NgayPhat>?) {
//        if (danhSachNgayPhat == null) return
//        listDate.clear()
//        danhSachNgayPhat.forEach {
//            listDate.add(unixTimeStampToDateTime(it.ngayPhat))
//        }
//        listDate.sort()
//    }
//
//    private fun convertWssMessToPlaylist(payload: SchedulePayload): Map<String, String> {
//        listDate.clear()
//        val danhSachBanTin = payload.danhSachBanTin
//        val mapData: MutableMap<String, String> = mutableMapOf()
//        if (danhSachBanTin.isNotEmpty()) {
//            for (item in danhSachBanTin) {
//                findDate(item.danhSachNgayPhat)
//            }
//        }
//
//        listDate.toSet().toMutableList().forEach {
//
//            val jsonSchedule = findScheduleByDate(
//                payload, it
//            )
//            mapData[it] = jsonSchedule
//        }
//
//        return mapData
//    }


//    private fun findScheduleByDate(payload: SchedulePayload, ngayPhat: String): String {
//        val danhSachBanTin = payload.danhSachBanTin
//        val listBanTinPhatTheoNgay = mutableListOf<MediaItem>()
//        val listItemMedia = mutableListOf<itemMedia>()
//        //tim nhung ban tin co ngay la date
//        for (item in danhSachBanTin) {
//            item.danhSachNgayPhat.forEach {
//                if (unixTimeStampToDateTime(it.ngayPhat) == ngayPhat) {
//                    it.thoiDiemPhat.forEach { thoiDiemPhat ->
//                        val time = ThoiDiemPhat(
//                            thoiDiemPhat.thoiGianBatDau, thoiDiemPhat.thoiGianKetThuc
//                        )
//                        val itemMedia = itemMedia(time, item)
//                        listItemMedia.add(itemMedia)
//                    }
//                }
//            }
//        }
//        //chuyển list ra list media
//        if (listItemMedia.isNotEmpty()) {
//            listItemMedia.sortBy { itemMedia -> itemMedia.time.thoiGianBatDau }
//            var count = 1
//            var checkCountStart = 0
//            for (item in listItemMedia) {
//                val tObjStartTime: String = if (checkCountStart > 0) {
//                    convertTimeStampToHHMMSS(item.time.thoiGianBatDau?.minus(0))
//                } else {
//                    convertTimeStampToHHMMSS(item.time.thoiGianBatDau)
//                }
//                checkCountStart++
//                val tObjEndTime: String = convertTimeStampToHHMMSS(
//                    item.time.thoiGianKetThuc
//                )
//                val obj = MediaItem(
//                    id = count,
//                    mediaID = count.toString(),
//                    projectID = item.danhSachBanTin.banTinID,
//                    sourceID = payload.nguonID,
//                    sourceName = payload.tenNguon,
//                    destinationID = payload.dichID,
//                    destinationName = payload.tenDich,
//                    title = item.danhSachBanTin.tieuDe,
//                    description = item.danhSachBanTin.noiDungTomTat,
//                    duration = item.danhSachBanTin.thoiLuong,
//                    contentType = Utils.convertContentType(item.danhSachBanTin.loaiBanTin),
//                    fieldType = Utils.convertFiend(item.danhSachBanTin.loaiLinhVuc),
//                    priority = Utils.convertPriority(item.danhSachBanTin.mucDoUuTien),
//                    category = Utils.convertFiend(item.danhSachBanTin.loaiLinhVuc),
//                    author = convertAuthor(item.danhSachBanTin.tacGia),
//                    body = item.danhSachBanTin.noiDung,
//                    sourceReference = item.danhSachBanTin.tenDiaBanTao,
//                    type = getTypeOfMediaItem(item.danhSachBanTin.loaiBanTin?.toInt()),
//                    createDate = item.danhSachBanTin.thoiGianSanXuat,
//                    metadata = mutableListOf(),
//                    fixTime = tObjEndTime,
//                    startTime = tObjStartTime,
//                    start = tObjStartTime,
//                    end = tObjEndTime,
//                    isExternalSource = item.danhSachBanTin.tiepAm
//                )
//                count++
//                listBanTinPhatTheoNgay.add(obj)
//
//            }
//        }
//        return convertToTimeFrame(listBanTinPhatTheoNgay)
//    }

    private fun convertToTimeFrame(listItem: List<MediaItem>): String {
        val newList = listItem.toMutableList()
        val res = mutableListOf<MediaItem>()
        val listTimeFrame: MutableList<Map<Int, Int>> = mutableListOf()
        val starts: MutableList<String> = mutableListOf()
        var startTime = 0
        var endTime: Int
        for (i in 0..listItem.size - 2) {
            if (i == 0) {
                startTime = 0
            }
            if (listItem[i].end != listItem[i + 1].start) {
                endTime = i
                starts.add(listItem[i].end!!)
                val map = mapOf(startTime to endTime)
                listTimeFrame.add(map)
                startTime = i + 1
            }
            if (i == listItem.size - 2) {
                endTime = i + 1
                val map = mapOf(startTime to endTime)
                listTimeFrame.add(map)

            }
        }
        //Chỉ có 1 khung
        if (starts.isEmpty()) {
            val firstId: Int = listItem.first().id?.minus(1) ?: 0
            Log.d("wssTImeFrame", "convertToTimeFrame: 1 khung")
            val tObjStart = MediaItem(
                id = firstId,
                mediaID = firstId.toString(),
                projectID = firstId.toString(),
                title = "Khung ${listItem.first().start}",
                body = "start",
                startTime = listItem.first().startTime,
                start = listItem.first().startTime,
                fixTime = listItem.first().startTime,
                end = listItem.first().end,
            )
            newList.add(0, tObjStart)
            val lastId: Int = listItem.last().id?.plus(1) ?: 0
            val tObEnd = MediaItem(
                id = lastId,
                mediaID = lastId.toString(),
                projectID = lastId.toString(),
                title = "End Khung ${listItem.first().start}",
                body = "end",
                startTime = listItem.last().startTime,
                fixTime = listItem.last().end,
                end = listItem.last().end,
                start = listItem.last().start
            )
            newList.add(tObEnd)
            return jsonMediaItemAdapter.toJson(newList)
        }
        //Có nhiều khung
        else {
            Log.d("wssTImeFrame", "convertToTimeFrame: Nhiều khung")
            listTimeFrame.forEach {
                val list: MutableList<MediaItem> =
                    listItem.subList(it.keys.first(), it.values.first() + 1).toMutableList()
                val firstId: Int = listItem[it.keys.first()].id?.minus(1) ?: 0
                val tObjStart = MediaItem(
                    id = firstId,
                    mediaID = firstId.toString(),
                    projectID = firstId.toString(),
                    title = "Khung ${listItem[it.keys.first()].start}",
                    body = "start",
                    startTime = listItem[it.keys.first()].startTime,
                    start = listItem[it.keys.first()].startTime,
                    fixTime = listItem[it.keys.first()].startTime,
                    end = listItem[it.keys.first()].end,
                )
                list.add(0, tObjStart)
                val lastId: Int = listItem[it.values.first()].id?.plus(1) ?: 0
                val tObEnd = MediaItem(
                    id = lastId,
                    mediaID = lastId.toString(),
                    projectID = lastId.toString(),
                    title = "End Khung ${listItem[it.values.first()].start}",
                    body = "end",
                    startTime = listItem[it.values.first()].startTime,
                    fixTime = listItem[it.values.first()].end,
                    end = listItem[it.values.first()].end,
                    start = listItem[it.values.first()].start
                )
                list.add(list.size, tObEnd)
                res.addAll(list)
            }
            return jsonMediaItemAdapter.toJson(res)

        }

    }


    private fun startPingTimer() {
        pingHubJob?.cancel()
        pingHubJob = coroutineScope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    sendPing()
                    Log.d(TAG, "ping: $pingCounter")
                    delay(PING_HUB_INTERVAL)
                    checkConnection()
                } catch (_: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "startPingTimer: ${e.message}")
                }
            }
        }
    }

    private fun checkConnection() {
//        if (pingCounter >= 3) {
//            //Try to reconnect to hub
//            hubToken = null
//            isConnectSuccess = false
//            Log.e(TAG, "Losed hub connection. Try to reconnect Websocket")
//            reconnectToServer()
//        }
    }


}


interface HubComm {
    fun forceRestartApp(countHardRestart: Int)
    fun forceRestartDevice()
}

