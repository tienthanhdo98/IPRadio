package app.tek4tv.ipradio.core

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.di.RetrofitInstance
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.ui.MainActivity.Companion.jsonMapAdapter
import app.tek4tv.ipradio.ui.MainActivity.Companion.retrofit
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.Utils.Companion.convertAuthor
import app.tek4tv.ipradio.utils.Utils.Companion.getDeviceInfo
import app.tek4tv.ipradio.utils.Utils.Companion.getTypeOfMediaItem
import app.tek4tv.ipradio.utils.network.NetworkUtils
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import kotlinx.coroutines.*
import java.lang.reflect.ParameterizedType
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


class HubManager(
    private val hubComm: HubComm,
    var onMessageListener: (command: String?, message: String?) -> Unit,
) : IConnectionServer {
    private val TAG = "HubManager"

    private val CONNECT_COUNTER_LIMIT = 5
    private val PING_HUB_INTERVAL = 60000L
    private val moshi = RetrofitInstance.moshi


    private var pingHubJob: Job? = null
    private var getTokenJob: Job? = null

    private var coroutineScope = CoroutineScope(Dispatchers.Main)

    val appContext: Context = MyApp.instance!!.applicationContext

    private val deviceImei = Utils.getDeviceId(appContext)

    private var typeMediaItem: ParameterizedType = Types.newParameterizedType(
        MutableList::class.java,
        MediaItemModel::class.java
    )
    private var jsonMediaItemAdapter: JsonAdapter<List<MediaItemModel>> = moshi.adapter(typeMediaItem)


    var pingCounter = 0


    var responseHub = ResponseHub()

    //player info
    var playlistIndex = 0
    var currentMediaItem: MediaItemModel? = null
    var curMediaName = ""

    var versionName = ""

    private val notifyDisconnected = AtomicBoolean(false)


    private var hubToken: String? = null

    private val wssStatusAdapter: JsonAdapter<WssStatusModel> =
        moshi.adapter(WssStatusModel::class.java)
    private val wssLiveAdapter: JsonAdapter<ReceivedMessageModel> =
        moshi.adapter(ReceivedMessageModel::class.java)
    private val wssPayloadScheduleAdapter: JsonAdapter<ReceivedMessageScheduleModel> =
        moshi.adapter(ReceivedMessageScheduleModel::class.java)
    private val wssPayloadLiveAdapter: JsonAdapter<ReceivedMessageLiveModel> =
        moshi.adapter(ReceivedMessageLiveModel::class.java)
    private val wssPayloadRemovePlaylistAdapter: JsonAdapter<ReceivedMessageRemovePlaylistModel> =
        moshi.adapter(ReceivedMessageRemovePlaylistModel::class.java)
    private val wssPayloadControlDeviceAdapter: JsonAdapter<ReceivedMessageControlDeviceModel> =
        moshi.adapter(ReceivedMessageControlDeviceModel::class.java)

    private val mediaItemMoshiAdapter: JsonAdapter<MediaItemModel> =
        moshi.adapter(MediaItemModel::class.java)


    //mqtt
    lateinit var listMQTTInfo : List<NoiDungMQTT>
    val isMQTT = true
    var mqttClient: MQTTManager? = null

    init {
        versionName = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
    }

    private fun getHubToken() {
        try {
            val body = mapOf(
                "Username" to "tek4tv_quangngai",
                "Password" to "4bfb2c4a-e049-4d21-86e0-4ffdf854b8a1",
            )
            val url = ConfigUtil.getString(
                context = appContext,
                ConfigUtil.URL_TOKEN,
                "https://gateway-thongtinnguon.tek4tv.vn/api/v2/oauth/token"
            )
            val call = retrofit.getToken(body, url!!)
            val response = call.execute()
            if (response.isSuccessful) {
                if ((response.body()?.trangThaiGUI ?: 1) != 1) {
                    hubToken = response.body()!!.noiDung.token
                    Log.i(TAG, "getHubToken ${hubToken}")
                }
            } else {
                Log.i(TAG, "getHubToken fail")
                hubToken = null
                pingCounter ++
            }
        } catch (e: Exception) {
            Log.i(TAG, "getHubToken error: ${e.message}")
            hubToken = null
            pingCounter ++
        }
        Log.i(TAG, "getHubToken ping: $pingCounter")
    }
    private fun getMqttInfo()
    {
        try {
            val body = MQTTInfoModel(deviceImei)
            val url = "https://gateway-thongtinnguon.tek4tv.vn/v2/mqtt-digital/digital/mqtt/dangkythietbi"

            val call = retrofit.getMQTTInfo(body,"Bearer $hubToken", url)
            val response = call.execute()
            if (response.isSuccessful) {
                listMQTTInfo = response.body()!!.noiDung
                Log.i(TAG, "MqttInfo: ${listMQTTInfo.size}")
                if (isMQTT) {
                    mqttClient =
                        MQTTManager(hubToken, this, GlobalProperties.getInst().destinationId,listMQTTInfo)
                    mqttClient?.connectToMQTT(appContext)
                }
            } else {
                listMQTTInfo = listOf()
                Log.i(TAG, "MqttInfo null")
            }
        } catch (e: Exception) {
            Log.i(TAG, "MqttInfo fail: ${e.message}")
        }
    }

    fun connectToServer() {
        //reset sau 5 phút khi không lấy được token
        if (pingCounter >= 60) {
            val countForHardRestart = ConfigUtil.getInt(appContext, ConfigUtil.CountForHardReset, 0)

            hubComm.forceRestartApp(countForHardRestart)
        }
        getTokenJob?.cancel()
        getTokenJob = CoroutineScope(Dispatchers.Default).launch {
            while (hubToken == null) {
                getHubToken()
                delay(5000)
                connectToServer()
            }
        }
        if (hubToken == null) return
        getTokenJob?.cancel()
        getMqttInfo()
    }

    override fun onConnectSuccess() {

        startPingTimer()
    }

    override fun onConnectFailure(message: String?) {
        startPingTimer()
    }

    override fun onReceiveMessageSuccess(message: String) {
        pingCounter = 0
        Log.d(TAG, "onReceiveMessageSuccess: ${pingCounter}")
        convertToHubCommand(message)

    }

    override fun onSendMessageSuccess(responseMessage: String) {

    }

    override fun onSendMessageFailure(responseMessage: String) {

    }

    override fun onDisconnectFailure(message: String?) {
    }


    fun closeConnect()
    {
        if (isMQTT) {
            mqttClient?.disconnectMqtt()
        }
    }


    private fun sendMessage(message: String) {
        if (isMQTT) {
            mqttClient?.publishToMQTT(message)
        }
    }

     private fun sendPing() {
         pingCounter++
         var noiDungPhat: List<String>
         noiDungPhat = if (currentMediaItem != null) {
             listOf(currentMediaItem!!.pathBackup)
         } else {
             listOf()
         }

         val request = WssStatusModel(
             id = "", topic = HubCommand.DV016, payload = PayloadStatus(
                 dichID = GlobalProperties.getInst().dichId,
                 tenDich = GlobalProperties.getInst().tenDich,
                 nguonID = GlobalProperties.getInst().destinationId,
                 tenNguon = GlobalProperties.getInst().destinationName,
                 bangDienTuID = deviceImei,
                 tenThietBI = GlobalProperties.getInst().nameDevice,
                 tenLoaiThietBI = "Bảng điện tử công cộng",
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                trangThaiKetNoi = NetworkUtils.getNetworkInfo(appContext),
                trangThai = "1",
                amLuong = getDeviceInfo(
                    StringValue.deviceInfoVolume,
                    SerialPortController.deviceInfo
                ),
                viDo = GlobalProperties.getInst().lat,
                kinhDo = GlobalProperties.getInst().lng,
                thongTinThietBI = ThongTinThietBI(
                    congSuat = getDeviceInfo(
                        StringValue.deviceInfoPA,
                        SerialPortController.deviceInfo
                    ),
                    nhietDo = getDeviceInfo(
                        StringValue.deviceInfoTemperature,
                        SerialPortController.deviceInfo
                    ),
                    dungLuongSuDung = "1024000",
                    duLieuKhac = ""
                ),
                thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                //conver sttring to list String
                noiDungPhat = noiDungPhat ,
                phienBanUngDung = versionName,
            )
        )
        Log.d(TAG, "sendPing: ${request.payload}")
        sendMessage(wssStatusAdapter.toJson(request))
    }

    private fun convertToHubCommand(jsonMessage: String) {
        val wssData = wssLiveAdapter.fromJson(jsonMessage)
        var hubCommand = ""

        when (wssData!!.topic) {
            HubCommand.DV010 -> {

                val body = wssPayloadScheduleAdapter.fromJson(jsonMessage)
                if (body != null) {
                    val mapMonthSchedule = convertWssMessToPlaylist(body.payload!!)
                    val jsonMess = jsonMapAdapter.toJson(mapMonthSchedule)
                    hubCommand = HubCommand.UPDATE_MONTHLY_WSS
                    val typeScreen = body.payload.phanVungManHinh
                    ConfigUtil.putString(
                        context = appContext,
                        GlobalProperties.TYPE_SCREEN_KEY,
                        typeScreen
                    )
                    onMessageListener.invoke(hubCommand, jsonMess)
                    sendLogUpdatePlaylist(body.payload)



                }
            }
            //Phát trực tiếp
            HubCommand.DV011 -> {
                val body = wssPayloadLiveAdapter.fromJson(jsonMessage)
                hubCommand = HubCommand.LIVE
                try {
                    val bodyLive = body!!.payload
                    val banTinPhat = bodyLive!!.danhSachBanTin!!.firstOrNull()
                    if (banTinPhat != null) {
                        responseHub = ResponseHub().apply {
                            type = HubCommand.LIVE
                            level = banTinPhat.mucDoUuTien!!.toInt()
                            duration = banTinPhat.thoiLuong!!
                            message = banTinPhat.noiDung
                            userNameControl = ""
                            name = mediaItemMoshiAdapter.toJson(
                                MediaItemModel(
                                    projectID = banTinPhat.banTinCongCongID,
                                    mediaID = banTinPhat.banTinCongCongID,
                                    id = 0,
                                    sourceID = bodyLive.nguonID,
                                    sourceName = bodyLive.tenNguon,
                                    destinationID = bodyLive.danhSachDiaBanNhan!!.firstOrNull()?.dichID,
                                    destinationName = bodyLive.danhSachDiaBanNhan.firstOrNull()?.tenDich,
                                    title = banTinPhat.tieuDe,
                                    description = banTinPhat.noiDungTomTat,
                                    duration = bodyLive.danhSachBanTin!!.firstOrNull()?.thoiLuong,
                                    body = bodyLive.danhSachBanTin.firstOrNull()?.noiDung,
                                    sourceReference = bodyLive.nguonID,
                                    startTime = "00:00:00",
                                    fieldType = Category(
                                        id = banTinPhat.loaiLinhVuc?.toLong(),
                                        value = banTinPhat.loaiLinhVuc
                                    ),
                                    type = "LIVE",
                                    fixTime = "00:00:00",
                                    createDate = banTinPhat.thoiGianSANXuat.toString(),
                                    contentType = ContentType(
                                        id = banTinPhat.loaiBanTin?.toLong(),
                                        value = banTinPhat.loaiBanTin
                                    ),
                                    priority = Priority(
                                        id = banTinPhat.mucDoUuTien.toLong(),
                                        value = banTinPhat.mucDoUuTien
                                    ),
                                    author = Author(
                                        userName = banTinPhat.tacGia?.butDanh,
                                        fullName = banTinPhat.tacGia?.tenDayDu,
                                        email = banTinPhat.tacGia?.email
                                    ),
                                    broadcastRegion = BroadcastRegion(
                                        banTinPhat.vungPhat,
                                        banTinPhat.vungPhat!!
                                    )
                                )
                            )
                        }
                        Log.d("wssLive", "convertWssToHubCommad: ${hubCommand}-${jsonMessage}")
                        Log.d("wssLive", "convertWssToHubCommad: ${responseHub}")

                        onMessageListener.invoke(hubCommand, jsonMessage)
                    }

                }catch (e : Exception)
                {
                    Log.d("wss", "convertWssToHubCommad: fail ${e.message}")
                }


            }
//            //Hủy lịch
            HubCommand.DV012 -> {
                val body = wssPayloadRemovePlaylistAdapter.fromJson(jsonMessage)
                if (body != null) {
                    hubCommand = HubCommand.DELETE_MONTHLY_LIST_WSS

                    onMessageListener.invoke(hubCommand, "null")

                    sendLogRemovePlaylist(body.payload!!)
                }
            }
            //dieu khien volume, pa
            HubCommand.DV017 -> {
                val body = wssPayloadControlDeviceAdapter.fromJson(jsonMessage)
                if (body != null) {

                    val maLenh = body.payload!!.dieuKhienThietBI?.maLenh
                    val giaTri = body.payload.dieuKhienThietBI?.thamSo

                    when (maLenh) {
                        //0 - Điều khiển volume
                        "0" -> {
                            hubCommand = HubCommand.SET_VOLUME_DEVICE
                            responseHub = ResponseHub().apply {
                                message = giaTri
                            }
                        }
                        //1 - Điều khiển công suất
                        "1" -> {
                            hubCommand = HubCommand.TURN_ON_PA
                            //0 - bật
                            //1 - tắt
                            if (giaTri == "0") {
                                responseHub = ResponseHub().apply {
                                    message = "0"
                                }
                            } else {
                                responseHub = ResponseHub().apply {
                                    message = "1"
                                }
                            }
                        }
                        /*  Dừng phát nội dung
                        1 - Dừng phát bản tin, chuyển sang bản tin tiếp theo của lịch nếu có
                        2 - Dừng phát
                        3 - Phát tiếp nội dụng đã dừng*/
                        "2" -> {

                        }
                        //Reset - Tham số (số giây khởi động lại)
                        "3" -> {
                            hubCommand =    HubCommand.RESTART
                        }
                        //update thiet bi
                        "4" -> {
                            hubCommand = HubCommand.UPDATE_VERSION
                            if(giaTri != "")
                            {
                                responseHub = ResponseHub().apply {
                                    message = giaTri
                                }
                            }
                        }

                    }

                    onMessageListener.invoke(hubCommand, jsonMessage)
                    Log.d("wss", "Control ${hubCommand}- ${responseHub}")

                }
            }

        }
    }

    fun sendLogMediaSuccess(mediaItem: MediaItemModel) {

        val tacGia = TacGia(
            tenDayDu = mediaItem.author?.fullName ?: "",
            butDanh = mediaItem.author?.userName ?: "",
            email = mediaItem.author?.email ?: "",
            tenDangNhap =   ""
        )

        val request = WssStatusModel(
            id = "", topic = HubCommand.DV013, payload = WssMediaLogModel(
                nguonID = GlobalProperties.getInst().destinationId,
                tenNguon = GlobalProperties.getInst().destinationName,
                dichID = GlobalProperties.getInst().dichId,
                tenDich = GlobalProperties.getInst().tenDich,
                bangDienTuID = deviceImei,
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                banTinCongCongID = mediaItem.projectID!!,
                loaiBanTin = mediaItem.contentType?.value!!,
                mucDoUuTien = mediaItem.priority?.id.toString(),
                noiDung = listOf(mediaItem.pathBackup),
                noiDungTomTat = mediaItem.description!!,
                tacGia = tacGia,
                tenThietBI = GlobalProperties.getInst().nameDevice,
                thoiLuong = mediaItem.duration!!,
                tieuDe = mediaItem.title!!,
                loaiLinhVuc = mediaItem.fieldType!!.value!!,
                vungPhat = mediaItem.broadcastRegion!!.value,
                thongTinChiTietBanTin = mutableListOf(
                    ThongTinChiTietBanTinTa("", "")
                ),
                nguonTin = mediaItem.sourceName!!,
                thoiGianSANXuat = mediaItem.createDate?.toLong() ?: 0
            )
        )
        sendMessage(wssStatusAdapter.toJson(request))
    }

    private fun sendLogUpdatePlaylist( body: ReceivedPayloadSchedule) {
        val request = WssStatusModel(
            id = "", topic = HubCommand.DV014, payload = PayloadUpdatePlaylist(
                nguonID = GlobalProperties.getInst().destinationId,
                tenNguon = GlobalProperties.getInst().destinationName,
                dichID = GlobalProperties.getInst().dichId,
                tenDich = GlobalProperties.getInst().tenDich,
                danhSachDiaBan = body.danhSachDiaBanNhan,
                danhSachThietBI = mutableListOf(
                    DanhSachThietBi(
                        bangDienTuID = deviceImei,
                        tenThietBI = GlobalProperties.getInst().nameDevice,
                        maNhaCungCap = GlobalProperties.getInst().providerValue,
                        tenNhaCungCap = GlobalProperties.getInst().providerValue,
                    )
                ),
                phanVungManHinh = body.phanVungManHinh,
                lichPhatID = body.lichPhatID,
                tenLichPhat = body.tenLichPhat,
                danhSachBanTin = body.danhSachBanTin,


                )
        )
        Log.d("WebsocketLog", "sendLogSchedule: $request")
        sendMessage(wssStatusAdapter.toJson(request))
    }


    private fun sendLogRemovePlaylist(body: PayloadRemovePlaylist) {
        val request = WssStatusModel(
            id = "", topic = HubCommand.DV015, payload = PayloadSendLogRemovePlaylist(
                nguonID = body.nguonID,
                tenNguon = body.tenNguon,
                dichID = body.danhSachDiaBanNhan?.firstOrNull()?.dichID,
                tenDich =  body.danhSachDiaBanNhan?.firstOrNull()?.dichID,
                lichPhatID = body.lichPhatID,
                tenLichPhat = body.tenLichPhat,
                danhSachThietBI = mutableListOf(
                    DanhSachThietBi(
                        bangDienTuID = deviceImei,
                        tenThietBI = GlobalProperties.getInst().nameDevice,
                        maNhaCungCap = GlobalProperties.getInst().providerValue,
                        tenNhaCungCap = GlobalProperties.getInst().providerValue,
                    )
                ),


            )
        )
        Log.d("WebsocketLog", "sendLogRemovePlaylist: $request")
        sendMessage(wssStatusAdapter.toJson(request))
    }

    private val listDate = mutableListOf<String>()
    private fun findDate(danhSachNgayPhat: List<DanhSachNgayPhat>) {
        danhSachNgayPhat.forEach {
            listDate.add(unixTimeStampToDateTime(it.ngayPhat))
        }
        listDate.sort()
    }

    private fun convertWssMessToPlaylist(payload: ReceivedPayloadSchedule): Map<String, String> {
        listDate.clear()
        val danhSachBanTin = payload.danhSachBanTin
        val mapData: MutableMap<String, String> = mutableMapOf()
        if (danhSachBanTin.isNotEmpty()) {
            for (item in danhSachBanTin) {
                findDate(item.danhSachNgayPhat!!)
            }
        }

        listDate.toSet().forEach {

            val jsonSchedule = findScheduleByDate(
                payload,
                it
            )
            mapData[it] =
                jsonSchedule
        }

        mapData.forEach{
            Log.d("wssplaylist", "Convert Map: ${it.key}")
            Log.d("wssplaylist", "Convert Map: ${it.value}")
        }

        return mapData
    }

    private fun findScheduleByDate(payload: ReceivedPayloadSchedule, ngayPhat: String): String {
        val listBanTinPhatTheoNgay = mutableListOf<MediaItemModel>()
        val listItemMedia = mutableListOf<itemMedia>()
        //tim nhung ban tin co ngay la date
        for (item in payload.danhSachBanTin) {
            item.danhSachNgayPhat!!.forEach {
                if (unixTimeStampToDateTime(it.ngayPhat) == ngayPhat) {
                    it.thoiDiemPhat.forEach { thoiDiemPhat ->
                        val time = ThongTinChiTietBanTin(
                            thoiDiemPhat.thoiGianBatDau,
                            thoiDiemPhat.thoiGianKetThuc
                        )
                        val itemMedia = itemMedia(time, item)
                        listItemMedia.add(itemMedia)
                    }
                }
            }
        }
        listItemMedia.forEach {
            Log.d("playtesst", "findScheduleByDate: ${it.danhSachBanTin.tieuDe}")
        }
        //chuyển list ra list media
        if (listItemMedia.isNotEmpty()) {
            listItemMedia.sortBy { itemMedia -> itemMedia.time.thoiGianBatDau }
            var count = 1
            var checkCountStart = 0
            for (item in listItemMedia) {
                val tObjStartTime: String = if (checkCountStart > 0) {
                    convertTimeStampToHHMMSS(item.time.thoiGianBatDau - 0)
                } else {
                    convertTimeStampToHHMMSS(item.time.thoiGianBatDau)
                }
                checkCountStart++
                val tObjEndTime: String = convertTimeStampToHHMMSS(
                    item.time.thoiGianKetThuc
                )
                val obj = MediaItemModel(
                    id = count,
                    mediaID = count.toString(),
                    projectID = item.danhSachBanTin.banTinCongCongID,
                    sourceID = payload.nguonID,
                    sourceName = payload.tenNguon,
                    destinationID = payload.danhSachDiaBanNhan.firstOrNull()!!.dichID,
                    destinationName = payload.danhSachDiaBanNhan.firstOrNull()!!.tenDich,
                    title = item.danhSachBanTin.tieuDe,
                    description = item.danhSachBanTin.noiDungTomTat,
                    duration = item.danhSachBanTin.thoiLuong,
                    contentType = Utils.convertContentType(item.danhSachBanTin.loaiBanTin!!),
                    fieldType = Utils.convertFiend(item.danhSachBanTin.loaiLinhVuc!!),
                    priority = Utils.convertPriority(item.danhSachBanTin.mucDoUuTien!!),
                    category = Utils.convertFiend(item.danhSachBanTin.loaiLinhVuc!!),
                    author = convertAuthor(item.danhSachBanTin.tacGia!!),
                    body = item.danhSachBanTin.noiDung,
                    sourceReference = item.danhSachBanTin.tenDiaBanTAO,
                    type = getTypeOfMediaItem(item.danhSachBanTin.loaiBanTin!!.toInt()),
                    createDate = item.danhSachBanTin.thoiGianSANXuat.toString(),
                    metadata = mutableListOf(),
                    tag = mutableListOf(),
                    fixTime = tObjEndTime,
                    startTime = tObjStartTime,
                    start = tObjStartTime,
                    end = tObjEndTime,
                    broadcastRegion = BroadcastRegion(
                        id = item.danhSachBanTin.vungPhat,
                        value = item.danhSachBanTin.vungPhat!!
                    )
                )
                count++
                listBanTinPhatTheoNgay.add(obj)

            }
        }
        return convertToTimeFrame(listBanTinPhatTheoNgay)
    }

    private fun convertToTimeFrame(listItem: List<MediaItemModel>): String {
        val newList = listItem.toMutableList()
        val listTimeFrame: MutableList<Map<Int, Int>> = mutableListOf()
        val starts: MutableList<String> = mutableListOf()
        var startTime = 0
        var endTime: Int
        for (i in 0..listItem.size - 2) {
            Log.d("wssTImeFrame", "convertToTimeFrame: end ${listItem[i].end}")
            Log.d("wssTImeFrame", "convertToTimeFrame: start ${listItem[i + 1].start}")
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

        val firstId: Int = listItem.first().id?.minus(1) ?: 0
        Log.d("wssTImeFrame", "convertToTimeFrame: 1 khung")
        val tObjStart = MediaItemModel(
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
            val tObEnd = MediaItemModel(
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
            Log.d(
                "wssTImeFrame",
                "convertToTimeFrame: startTimes ${jsonMediaItemAdapter.toJson(newList)}"
            )
            return jsonMediaItemAdapter.toJson(newList)


    }


    private fun startPingTimer() {
        pingHubJob?.cancel()
        pingHubJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                sendPing()
                Log.d(TAG, "ping: $pingCounter")
                delay(PING_HUB_INTERVAL)
                checkConnection()
            }
        }
    }

    private fun checkConnection() {
        if (pingCounter >= CONNECT_COUNTER_LIMIT) {
            val countForHardRestart = ConfigUtil.getInt(appContext, ConfigUtil.CountForHardReset, 0)

            hubComm.forceRestartApp(countForHardRestart)
        }
        if (pingCounter >= 3) {
            Log.e(TAG, "Losed hub connection")
            if (notifyDisconnected.compareAndSet(false, true)) {
                hubToken = null
                Log.e(TAG, "Hub Disconnected")
            }
            //Try to reconnect to hub
            Log.e(TAG, "Try to reconnect Websocket")
            connectToServer()
        }
    }



}


interface HubComm {
    fun forceRestartApp(countHardRestart : Int)
    fun forceRestartDevice()
}


