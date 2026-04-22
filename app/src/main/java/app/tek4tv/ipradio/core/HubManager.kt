package app.tek4tv.ipradio.core

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.core.mqtt.MQTTManager
import app.tek4tv.ipradio.di.JsonAdapterProvider.fromVs
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
import app.tek4tv.ipradio.model.McuStreamBackData
import app.tek4tv.ipradio.model.McuUpdateFirmData
import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.OphRebootData
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.model.SenMLItem
import app.tek4tv.ipradio.model.ServerData
import app.tek4tv.ipradio.model.ShowInfo
import app.tek4tv.ipradio.model.serialport.parseDeviceStatusResponse
import app.tek4tv.ipradio.network.ApiService
import app.tek4tv.ipradio.utils.DeviceUtils.calculateSpkerr
import app.tek4tv.ipradio.utils.DeviceUtils.getSdFree
import app.tek4tv.ipradio.utils.DeviceUtils.getSdTotal
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.HubCommand.OPU_ACK
import app.tek4tv.ipradio.utils.HubCommand.OPU_GENERIC
import app.tek4tv.ipradio.utils.HubCommand.OPU_LOG
import app.tek4tv.ipradio.utils.HubCommand.OPU_SETTINGS
import app.tek4tv.ipradio.utils.HubCommand.OPU_STATUS
import app.tek4tv.ipradio.utils.HubCommand.OPU_SYNC
import app.tek4tv.ipradio.utils.crypto.b64Decode
import app.tek4tv.ipradio.utils.getImsi
import app.tek4tv.ipradio.utils.getModeMobileData
import app.tek4tv.ipradio.utils.getSimPhoneNumber
import app.tek4tv.ipradio.utils.network.NetworkUtils
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

    private val API_CALL_INTERVAL = 60000L

    private var fastJob: Job? = null
    private var slowJob: Job? = null

    private var getBootstrapInfoJob: Job? = null

    lateinit var hubObverse: IHubObserve

    val deviceImei = "656565656565"
    val verifyCode = "656565656565"


    var pingCounter = 0

    var responseHub = ResponseHub()

    var currentMediaItem: MediaFile? = null
    var currentMediaInfoListId: Set<Long>? = null

    var versionName = "1.0.3"


    var isConnectSuccess = false
    var disconnectionCnt = 0

    private var getMQTTInfoJob: Job? = null

    @Volatile
    var mqttClient: MQTTManager? = null

    private var lastMessageHash: Int = 0

    private var lastMessageTime: Long = 0
    var hasSync = false
    var currentListShowInfoSynced = emptyList<ShowInfo>()

    var globalProperties = GlobalProperties.getInst()
    var bootstrapInfo: BootstrapResp? = null

    private var reconnectAttempts = 0

    @Volatile
    private var hasNotifiedDisconnected = false
    private var reconnectJob: Job? = null

    //Info
    var onMcuInfoUpdated: ((McuInfoData?) -> Unit)? = null
    var currentMcuInfo: McuInfoData? = null

    fun prepareMcuInfo() {
        val info = McuInfoData(
            id = deviceImei,
            vcode = deviceImei,
            fvers = versionName,
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
            gsmImsi = getImsi(appContext),
            gsmPhone = getSimPhoneNumber(appContext)
        )
        updateCurrentMcuInfo(info)
    }

    fun refreshDynamicMcuInfo() {
        val updated = currentMcuInfo?.copy(
            gsmImsi = getImsi(appContext), gsmPhone = getSimPhoneNumber(appContext)
        )
        updateCurrentMcuInfo(updated)
    }

    private fun updateCurrentMcuInfo(newInfo: McuInfoData?) {
        currentMcuInfo = newInfo
        onMcuInfoUpdated?.invoke(currentMcuInfo)
    }

    //In BT Mode
    fun getBootstrapInfo() {
        try {
            bootstrapInfo = globalProperties.bootstrapInfo
            if (bootstrapInfo == null) {
                val call =
                    authService.getBootstrapInfo(externalId = deviceImei, externalKey = verifyCode)
                val response = call.execute()
                if (response.isSuccessful) {
                    Log.i(TAG, "getBootstrapInfo Success")
                    globalProperties.saveConnectionConfig(response.body())
                    bootstrapInfo = response.body()
                } else {
                    bootstrapInfo = null
                    Log.i(TAG, "getBootstrapInfo Success - Data null")
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
                    val mqttSecurity = globalProperties.mqttSecurity
                    val mqttServer = globalProperties.mqttServer
                    val mqttPort = globalProperties.mqttPort
                    val protocol = if (mqttSecurity == 1) "ssl" else "tcp"
                    val serverURI = "$protocol://$mqttServer:$mqttPort"

                    mqttClient = MQTTManager(
                        appContext,
                        this@HubManager,
                        serverURI,
                        bootstrapInfo!!,
                    )
                    connectMqtt()
                }
            }
        }
    }

    fun connectMqtt() {
        mqttClient?.connectToMQTT()
    }


    fun sendLog(logs: List<McuLogData>) {
        val time = System.currentTimeMillis() / 1000.0

        val senMlItem = SenMLItem(
            n = "$deviceImei:s:$OPU_LOG", t = time, vs = logsAdapter.toJson(logs)
        )

        val payload = senmlAdapter.toJson(listOf(senMlItem))
        Log.d("MGWtest", "sendLog: $payload")
        sendMessage(payload)
    }

    fun sendGeneric() {
        val time = System.currentTimeMillis() / 1000.0
        refreshDynamicMcuInfo()
        val info = currentMcuInfo ?: return
        val genericJsonString = moshi.toVs(info) ?: ""
        val senmlItem = SenMLItem(
            n = "$deviceImei:s:${OPU_GENERIC}", t = time, vs = genericJsonString
        )
        val finalPayload = moshi.toVs(listOf(senmlItem)) ?: ""
        Log.d("MGWtest", "sendGeneric: $finalPayload")

        sendMessage(finalPayload)
    }

    fun sendSettings() {
        val time = System.currentTimeMillis() / 1000.0
        val info = McuSettingsData(
            txtype = globalProperties.settingsTxType,
            sim = globalProperties.settingsSimType,
            mobileMode = globalProperties.settingsSimMode,
            ioct = 1,
            classd = 1,
            disabled = globalProperties.settingsDisable,
            mainVolume = globalProperties.mainVolume.toInt(),
            fmVolume = globalProperties.fmVolume.toInt(),
            fmAuto = globalProperties.fmAutoStop,
            fmThreshold = globalProperties.fmThreshold.toInt()
        )
        val settingJsonString = moshi.toVs(info) ?: ""

        val senmlItem = SenMLItem(
            n = "$deviceImei:s:${OPU_SETTINGS}", t = time, vs = settingJsonString
        )
        val finalPayload = moshi.toVs(listOf(senmlItem)) ?: ""
        Log.d("MGWtest", "sendSettings: $finalPayload")
        sendMessage(finalPayload)
    }

    fun sendStatus() {
        val time = System.currentTimeMillis() / 1000.0
        val playlist = if (currentMediaItem != null) {
            currentMediaItem!!.id
        } else {
            0
        }
        val rawDeviceInfo = SerialPortController.deviceInfo
        val deviceStatus = parseDeviceStatusResponse(rawDeviceInfo)
        val runningList = (deviceStatus?.audioStatus ?: emptyList()).take(4).map { it == 1 }
            .let { if (it.size < 4) it + List(4 - it.size) { false } else it }

        val errorList = (deviceStatus?.errorStatus ?: emptyList()).take(4).map { it == 1 }
            .let { if (it.size < 4) it + List(4 - it.size) { false } else it }

        val spkerrValue = calculateSpkerr(
            runningList = runningList, errorList = errorList
        )
        val conn = when (globalProperties.settingsTxType) {
            0 -> {
                0
            }

            1 -> {
                getModeMobileData()
            }

            2 -> {
                2
            }

            else -> {
                0
            }
        }
        val status = McuStatusData(
            conn = conn,
//            ipLan = NetworkUtils.getEthernetLanIp(appContext),
            ipLan = NetworkUtils.getRealEthernetIp(),
            ipWan = NetworkUtils.getMobileLocalIp(appContext),
            csqm = NetworkUtils.getCellularSignalPercent(appContext),
            csqw = NetworkUtils.getWifiSignalPercent(appContext),
            spkErr = spkerrValue,
            fmSta = deviceStatus?.paStatus ?: 0,
            sdFree = getSdFree(),
            sdTotal = getSdTotal(),
            temp = deviceStatus?.temperature ?: 0,
            playlist = playlist!!,
            mids = currentMediaInfoListId,
            fmRssi = globalProperties.fmThreshold.toInt(),
            disconnectionCnt = this.disconnectionCnt,
            rssim = 0,
        )
        val statusJsonString = moshi.toVs(status) ?: ""

        val senMlItem = SenMLItem(
            n = "$deviceImei:s:$OPU_STATUS", t = time, vs = statusJsonString
        )
        val finalPayload = moshi.toVs(listOf(senMlItem)) ?: ""
        Log.d("MGWtest", "sendStatus: $finalPayload")
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

    fun handlerMessage(mess: String) {
        try {
            appContext.saveMessageToFile(mess)
            val appDataList = senmlAdapter.fromJson(mess) ?: return
            if (appDataList.isEmpty()) return
            appDataList.forEach { appdata ->
                val nameParts = appdata.n.split(":")
                if (nameParts.size < 3) return@forEach // Bỏ qua nếu không đúng định dạng "id:dir:opcode"

                val cmd = nameParts[2]
                val data = appdata.vs

                var hubCommand: String
                responseHub = ResponseHub()

                val currentHash = data.hashCode()
                val currentTime = System.currentTimeMillis()
                if (currentHash == lastMessageHash && (currentTime - lastMessageTime) < 2000) {
                    Log.d(TAG, "Bỏ qua bản tin trùng lặp trong thời gian ngắn")
                    return
                }
                lastMessageHash = currentHash
                lastMessageTime = currentTime

                sendAck(cmd.toInt(), appdata.t)

                when (cmd) {
                    HubCommand.OPH_SYNC -> {
                        sendGeneric()
                        sendSettings()
                        sendStatus()
                    }

                    HubCommand.OPH_SETTINGS_V2 -> {
                        val data = moshi.fromVs<McuSettingsData>(data)
                        hubCommand = HubCommand.UPDATE_SETTING_CONFIG
                        responseHub.settingConfig = data
                        onMessageListener.invoke(hubCommand, "")

                    }

                    HubCommand.OPH_SERVER -> {
                        val data = moshi.fromVs<ServerData>(data)
                        hubCommand = HubCommand.UPDATE_SERVER_CONFIG
                        responseHub.serverConfig = data
                        onMessageListener.invoke(hubCommand, "")
                    }

                    HubCommand.OPH_UDF -> {
                        val data = moshi.fromVs<McuUpdateFirmData>(data)
                        hubCommand = HubCommand.UPDATE_VERSION
                        responseHub.updateFirmData = data
                        onMessageListener.invoke(hubCommand, "")
                    }

                    HubCommand.OPH_STREAM_BACK -> {
                        val data = moshi.fromVs<McuStreamBackData>(data)
                        val streamBackData = McuStreamBackData(
                            streamUrl = data?.streamUrl, cmd = data?.cmd, timeout = data?.timeout
                        )
                        hubCommand = HubCommand.STREAM_BACK
                        responseHub.streamBackData = streamBackData
                        onMessageListener.invoke(hubCommand, "")
                    }


                    HubCommand.OPH_SETTINGS -> {

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

                        val listMediaInfo = mutableListOf<MediaInfo>()

                        if (!records.isNullOrEmpty()) {
                            hasSync = currentListShowInfoSynced == records
                            if (hasSync) {
                                Log.d("MGWtest", "handlerMessage: OPH_MEDIA_V3 synced. Not Update")
                                return
                            }
                            currentListShowInfoSynced = records
                            records.forEach { showInfo ->
                                val showInfoStr = b64Decode(showInfo.showInfo!!)
                                val mediaInfo = moshi.fromVs<MediaInfo>(showInfoStr)
                                Log.d(
                                    "MGWtest",
                                    "Test: OPH_MEDIA_V3 showInfo action ${showInfo.action} "
                                )
                                if (mediaInfo != null) {
                                    mediaInfo.action = showInfo.action
                                    when (mediaInfo.action) {
                                        1 -> {
                                            listMediaInfo.add(mediaInfo)
                                        }

                                        4, 8 -> {
                                            hubCommand = HubCommand.DELETE_SCHEDULER
                                            responseHub.listMediaInfo = listOf(mediaInfo)
                                            onMessageListener.invoke(hubCommand, "")
                                        }

                                    }

                                }
                            }
                            if (listMediaInfo.isEmpty()) return
                            val listLiveMediaInfo = listMediaInfo.filter { it.mode == 2 }.toList()
                            val listSchedulerMediaInfo =
                                listMediaInfo.filter { it.mode == 1 }.toList()
                            val listSaveOnlyMediaInfo =
                                listMediaInfo.filter { it.mode == 0 }.toList()

                            if (listLiveMediaInfo.isNotEmpty()) {
                                hubCommand = HubCommand.LIVE
                                responseHub.listLiveMediaInfo = listLiveMediaInfo
                                onMessageListener.invoke(hubCommand, "")
                            }
                            if (listSchedulerMediaInfo.isNotEmpty()) {
                                hubCommand = HubCommand.UPDATE_SCHEDULER
                                responseHub.listMediaInfo = listSchedulerMediaInfo
                                onMessageListener.invoke(hubCommand, "")
                            }
//                            if (listSaveOnlyMediaInfo.isNotEmpty()) {
//                                hubCommand = HubCommand.SAVE_SCHEDULER
//                                responseHub.listSaveOnlyMediaInfo = listSaveOnlyMediaInfo
//                                onMessageListener.invoke(hubCommand, "")
//                            }
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
        hasNotifiedDisconnected = false
        reconnectJob = null
        hubObverse.onHubConnected()
        Log.d("MediaInfo", "Initial connect success")
        reconnectAttempts = 0
        sendGeneric()
        sendSettings()
        sendStatus()
        sendOpuSyncMedia()
        startPingTimer()
    }

    override fun onConnectFailure(message: String?) {

        Log.d("MediaInfo", "Initial connect failed: $message — retry in 15s")
        isConnectSuccess = false
        if (!hasNotifiedDisconnected) {
            hasNotifiedDisconnected = true
            disconnectionCnt++
            hubObverse.onHubDisConnected()
        }

        if (reconnectJob?.isActive == true) return

        reconnectJob = coroutineScope.launch {
            delay(15000)
            mqttClient?.connectToMQTT()
        }
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
                    pingCounter++
                    sendStatus()
                    sendOpuSyncMedia()
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

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        stopAllJob()
        isConnectSuccess = false
        mqttClient?.disconnect()
        mqttClient = null
    }

    fun disconnectAll() {
        reconnectJob?.cancel()
        reconnectJob = null
        stopAllJob()
        isConnectSuccess = false
        mqttClient?.disconnectAll()
        mqttClient = null
    }

}


interface HubComm {
    fun forceRestartApp(countHardRestart: Int)
    fun forceRestartDevice()
}

