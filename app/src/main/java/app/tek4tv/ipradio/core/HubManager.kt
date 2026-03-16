package app.tek4tv.ipradio.core

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.core.mqtt.MQTTManager
import app.tek4tv.ipradio.core.stream.IcecastConfig
import app.tek4tv.ipradio.core.stream.IcecastPutClient
import app.tek4tv.ipradio.core.stream.MicAacIcecastStreamer
import app.tek4tv.ipradio.di.JsonAdapterProvider.bootstrapAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.fromVs
import app.tek4tv.ipradio.di.JsonAdapterProvider.listAppDataItemAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.logsAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.senmlAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.showInfoAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.toVs
import app.tek4tv.ipradio.di.MoshiInst.moshi
import app.tek4tv.ipradio.model.BootstrapResp
import app.tek4tv.ipradio.model.McuAckData
import app.tek4tv.ipradio.model.McuInfoData
import app.tek4tv.ipradio.model.McuLogData
import app.tek4tv.ipradio.model.McuSettingsData
import app.tek4tv.ipradio.model.McuStatusData
import app.tek4tv.ipradio.model.McuStreamBackDataData
import app.tek4tv.ipradio.model.McuUpdateFirmData
import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.NetworkConfig
import app.tek4tv.ipradio.model.OphRebootData
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.model.SenMLItem
import app.tek4tv.ipradio.model.ServerData
import app.tek4tv.ipradio.model.ShowInfo
import app.tek4tv.ipradio.network.ApiService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.MQTT_PORT
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.MQTT_SECURITY
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.MQTT_SERVER
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.HubCommand.OPU_ACK
import app.tek4tv.ipradio.utils.HubCommand.OPU_GENERIC
import app.tek4tv.ipradio.utils.HubCommand.OPU_SETTINGS
import app.tek4tv.ipradio.utils.HubCommand.OPU_SYNC
import app.tek4tv.ipradio.utils.crypto.b64Decode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


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

    private var fastJob: Job? = null
    private var slowJob: Job? = null

    private var getBootstrapInfoJob: Job? = null

    val deviceImei = "6565656565652"

    var pingCounter = 0

    var responseHub = ResponseHub()

    var currentMediaItem: MediaFile? = null
    var currentMediaInfoListId: List<Long>? = null

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
                val call = authService.getBootstrapInfo(deviceImei, deviceImei)
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
                getMQTTInfoJob?.cancel()
                getMQTTInfoJob = launch {
                    val mqttSecurity = ConfigUtil.getInt(
                        appContext,
                        MQTT_SECURITY,
                        1
                    )
                    val mqttServer =
                        ConfigUtil.getString(appContext, MQTT_SERVER, "aiot.mobifone.vn")
                    val mqttPort = ConfigUtil.getInt(appContext, MQTT_PORT, 6668)
                    val protocol = if (mqttSecurity == 1) "ssl" else "tcp"
                    val serverURI = "$protocol://$mqttServer:$mqttPort"

                    mqttClient = MQTTManager(
                        appContext,
                        this@HubManager,
                        bootstrapInfo!!.id!!,
                        bootstrapInfo!!.key!!,
                        bootstrapInfo!!.channel!!,
                        serverURI
                    )
                    while (!isConnectSuccess) {
                        mqttClient?.connectToMQTT()
                        delay(15000)
                    }

                }

            }
        }

    }

    fun sendLog(logs: List<McuLogData>) {
        val time = System.currentTimeMillis() / 1000.0

        val senMlItem = SenMLItem(
            n = "$deviceImei:s:5", t = time, vs = logsAdapter.toJson(logs)
        )

        val payload = senmlAdapter.toJson(listOf(senMlItem))
        Log.d("MGWtest", "sendLog: $payload")
        sendMessage(payload)
    }

    fun sendGeneric() {
        val time = System.currentTimeMillis() / 1000.0
        val info = McuInfoData(
            id = deviceImei,
            vcode = deviceImei,
            fvers = "1.0.0",
            osVersion = "5.10",
            osBuilddate = "2026-03-10",
            hwVersion = "3",
            sdCid = "1234567890",
            fm = "SD87.5",
            classdVersion = "2.1",
            classdChipset = "TAS5825",
            gsmModel = "CLM920_AC3",
            gsmImei = "123456789012345",
            gsmRevision = "REV1",
            gsmImsi = "452012345678901",
            gsmPhone = "0987654321"
        )
        val genericJsonString = moshi.toVs(info) ?: ""
        val senmlItem = SenMLItem(
            n = "$deviceImei:s:${OPU_GENERIC}", t = time, vs = genericJsonString
        )
        val finalPayload = moshi.toVs(listOf(senmlItem)) ?: ""
        Log.d("MGWtest", "sendGeneric: $finalPayload")

        sendMessage(finalPayload)
    }

    fun sendAck(opcode: Int, t: Double) {
        val time = System.currentTimeMillis() / 1000.0
        val info = McuAckData(
            opcode = opcode, t = t
        )
        val settingJsonString = moshi.toVs(info) ?: ""

        val senMlItem = SenMLItem(
            n = "$deviceImei:s:$OPU_ACK", t = time, vs = settingJsonString
        )
        val finalPayload = moshi.toVs(listOf(senMlItem)) ?: ""
        Log.d("MGWtest", "sendAck: $finalPayload")
        sendMessage(finalPayload)
    }

    fun sendSettings() {
        val time = System.currentTimeMillis() / 1000.0
        val info = McuSettingsData(
            txtype = 0,
            sim = 0,
            mobileMode = 4,
            ioct = 0,
            classd = 0,
            disabled = 0,
            mainVolume = SerialPortController.volume.toInt(),
            fmVolume = SerialPortController.volume.toInt(),
            fmAuto = 1,
            fmThreshold = 15
        )
        val settingJsonString = moshi.toVs(info) ?: ""

        val senmlItem = SenMLItem(
            n = "$deviceImei:s:${OPU_SETTINGS}", t = time, vs = settingJsonString
        )
        val finalPayload = moshi.toVs(listOf(senmlItem)) ?: ""
        Log.d("MGWtest", "sendSettings: $finalPayload")
        sendMessage(finalPayload)
    }

    fun sendOpuSyncMedia() {
        val time = System.currentTimeMillis() / 1000.0

        // Trường hợp gửi data rỗng như bạn yêu cầu
        // Nếu server yêu cầu {} thì dùng Map hoặc một Data class rỗng
        val syncData = emptyMap<String, Any>()
        val vsString = moshi.toVs(syncData) ?: "{}"

        val senmlItem = SenMLItem(
            n = "$deviceImei:s:$OPU_SYNC", t = time, vs = vsString
        )

        val finalPayload = moshi.toVs(listOf(senmlItem)) ?: ""
        Log.d("MGWtest", "sendOpuSyncMedia: $finalPayload")
        sendMessage(finalPayload)
    }

    fun sendStatus() {
        val time = System.currentTimeMillis() / 1000.0
        val playlist = if (currentMediaItem != null) {
            currentMediaItem!!.id
        } else {
            0
        }
        val status = McuStatusData(
            conn = 0,
            ipLan = "192.168.1.10",
            ipWan = "113.22.1.5",
            csqm = 25,
            csqw = 20,
            spkErr = 0,
            fmSta = 0,
            sdFree = 1024000,
            sdTotal = 2048000,
            temp = 40,
            playlist = playlist!!,
            mids = currentMediaInfoListId,
            fmRssi = 0,
            disconnectionCnt = 0,
            rssim = 0,
        )
        val statusJsonString = moshi.toVs(status) ?: ""

        val senmlItem = SenMLItem(
            n = "$deviceImei:s:3", t = time, vs = statusJsonString
        )
        val finalPayload = moshi.toVs(listOf(senmlItem)) ?: ""
        Log.d("MGWtest", "sendStatus: $finalPayload")
        sendMessage(finalPayload)
    }

    fun handlerMessage(mess: String) {
        try {
            appContext.saveMessageToFile(mess)
            val appDataList = listAppDataItemAdapter.fromJson(mess) ?: return
            if (appDataList.isEmpty()) return
            appDataList.forEach { appdata ->
                val nameParts = appdata.n.split(":")
                if (nameParts.size < 3) return@forEach // Bỏ qua nếu không đúng định dạng "id:dir:opcode"

                val cmd = nameParts[2]
                val data = appdata.vs

                var hubCommand = ""
                responseHub = ResponseHub()

                sendAck(cmd.toInt(), appdata.t)

                when (cmd) {
                    HubCommand.OPH_SYNC -> {
                        sendGeneric()
                        sendSettings()
                        sendStatus()
                    }

                    HubCommand.OPH_STREAM_BACK -> {
                        val data = moshi.fromVs<McuStreamBackDataData>(data)
                        if (data?.streamUrl == null) return
                        val url = data.streamUrl
                        val cmd = data.cmd
                        val timeout = data.timeout
                        hubCommand = HubCommand.STREAM_BACK
                        responseHub.message = url
                        responseHub.duration = timeout ?: 10
                        responseHub.level = cmd ?: 0
                        onMessageListener.invoke(hubCommand, "")
                    }

                    HubCommand.OPH_SETTINGS_V2 -> {
                        val data = moshi.fromVs<McuSettingsData>(data)
                        if (data?.fmVolume != null || data?.mainVolume != null) {
                            hubCommand = HubCommand.SET_VOLUME_DEVICE
                            responseHub.message = data.fmVolume.toString()
                            onMessageListener.invoke(hubCommand, data.fmVolume.toString())
                        }
                        if (data?.txtype != null) {
                            hubCommand = HubCommand.UPDATE_SETTING_CONFIG
                            val networkConfig = NetworkConfig(
                                transport = data.txtype,
                                simType = data.sim,
                                radioMode = data.mobileMode
                            )
                            responseHub.networkConfig = networkConfig
                            onMessageListener.invoke(hubCommand, "")
                        }

                    }

                    HubCommand.OPH_UDF -> {
                        val data = moshi.fromVs<McuUpdateFirmData>(data)
                        hubCommand = HubCommand.UPDATE_VERSION
                        responseHub.message = data?.url
                        onMessageListener.invoke(hubCommand, data?.url)
                    }

                    HubCommand.OPH_SETTINGS -> {

                    }

                    HubCommand.OPH_SERVER -> {
                        val data = moshi.fromVs<ServerData>(data)
                        hubCommand = HubCommand.UPDATE_SERVER_CONFIG
                        responseHub.serverConfig = data
                        onMessageListener.invoke(hubCommand, "")
                    }

                    HubCommand.OPH_REBOOT -> {
                        val data = moshi.fromVs<OphRebootData>(data)
                        Log.d("MGWtest", "Test: $cmd - ${data!!.option} ")
                        hubCommand = HubCommand.RESTART
                        onMessageListener.invoke(hubCommand, data.option.toString())
                    }

                    HubCommand.OPH_MEDIA_SYNC -> {
                        Log.d("MGWtest", "Test: $cmd - OPH_MEDIA_SYNC ")
                    }

                    HubCommand.OPH_MEDIA_V3 -> {
                        //Nếu bị trùng khung giờ thì sao?
                        //Tại sao bản tin live vẫn còn khi sync về
                        val records: List<ShowInfo>? = showInfoAdapter.fromJson(data)
                        if (!records.isNullOrEmpty()) {
                            records.forEach { showInfo ->
                                val showInfoStr = b64Decode(showInfo.showInfo!!)
                                val mediaInfo = moshi.fromVs<MediaInfo>(showInfoStr)
                                Log.d("MGWtest", "Sync: OPH_MEDIA_V3 mediaInfo ${mediaInfo} ")
                                Log.d(
                                    "MGWtest",
                                    "Test: OPH_MEDIA_V3 showInfo action ${showInfo.action} "
                                )
                                mediaInfo!!.files.forEach { it ->
                                    Log.d("MGWtest", "Test: OPH_MEDIA_V3 MediaFile ${it.url} ")
                                }
                                when (showInfo.action) {
                                    //Tạo
                                    1 -> {
                                        when (mediaInfo.mode) {
                                            //Save Only
                                            0 -> {
                                                hubCommand = HubCommand.SAVE_SCHEDULER
                                                responseHub.mediaInfo = mediaInfo
                                                onMessageListener.invoke(hubCommand, "")
                                            }
                                            //Scheduler
                                            1 -> {
                                                hubCommand = HubCommand.UPDATE_SCHEDULER
                                                responseHub.mediaInfo = mediaInfo
                                                onMessageListener.invoke(hubCommand, "")
                                            }
                                            //Live
                                            2 -> {
                                                hubCommand = HubCommand.LIVE
                                                responseHub.mediaInfo = mediaInfo
                                                onMessageListener.invoke(hubCommand, "")
                                            }
                                        }
                                    }
                                    //Sửa
                                    2 -> {

                                    }
                                    //Xóa
                                    4 -> {
                                        hubCommand = HubCommand.DELETE_SCHEDULER
                                        responseHub.mediaInfo = mediaInfo
                                        onMessageListener.invoke(hubCommand, "")
                                    }
                                    //Phát
                                    8 -> {

                                    }
                                    //Dừng
                                    16 -> {

                                    }
                                }
                            }
                        }
                    }

                    HubCommand.OPH_MEDIA_DELETE_ALL -> {
                        hubCommand = HubCommand.DELETE_ALL_MEDIA
                        onMessageListener.invoke(hubCommand, "")
                    }
                }

            }
        } catch (e: Exception) {
            Log.e("MediaInfo", "handlerMessage Error parsing message: ${e.message}")
        }
    }

    override fun onConnectSuccess() {
        isConnectSuccess = true
        getMQTTInfoJob?.cancel()
        sendGeneric()
        sendSettings()
        sendStatus()
        sendGeneric()
        sendOpuSyncMedia()
        startPingTimer()
    }

    override fun onConnectFailure(message: String?) {
        isConnectSuccess = false
    }

    override fun onReceiveMessageSuccess(message: String) {
        pingCounter = 0
        handlerMessage(message)
    }


    private fun sendMessage(message: String) {
        if (mqttClient != null) {
            mqttClient?.publishToMQTT(message)
        }
    }

    private fun startPingTimer() {
        stopAllJob()
        // NHÓM 1: Các gói tin gửi mỗi phút (STATUS & SYNC)
        fastJob = coroutineScope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    delay(60 * 1000L)
                    sendStatus()
                    //1p sync 1 lần thì???
//                    sendOpuSyncMedia()
                } catch (e: Exception) {
                    delay(5000)
                }
            }
        }
        slowJob = coroutineScope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    delay(10 * 60 * 1000L)
                    sendSettings()
                    sendGeneric()
                } catch (e: Exception) {
                    delay(10000)
                }
            }
        }
    }

    private fun stopAllJob() {
        fastJob?.cancel()
        slowJob?.cancel()
    }


    //    private fun checkConnection() {
//        if (pingCounter >= 3) {
//            //Try to reconnect to hub
//            isConnectSuccess = false
//            Log.e(TAG, "Losed hub connection. Try to reconnect Websocket")
//            reconnectToServer()
//        }
//    }


}


interface HubComm {
    fun forceRestartApp(countHardRestart: Int)
    fun forceRestartDevice()
}

