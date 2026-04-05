package app.tek4tv.ipradio.ui


import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.HubComm
import app.tek4tv.ipradio.core.HubManager
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.core.RestartGuardManager
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.core.SerialPortController.Companion.deviceInfo
import app.tek4tv.ipradio.core.players_helper.IPlayerManagerListener
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.PLAY_MODE_FORCE
import app.tek4tv.ipradio.core.stream.StreamBackManager
import app.tek4tv.ipradio.core.webserver.LocalWebConsole
import app.tek4tv.ipradio.databinding.ActivityMainBinding
import app.tek4tv.ipradio.model.BootstrapResp
import app.tek4tv.ipradio.model.Channel
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.ForceMedia.Companion.getLocalForceMedia
import app.tek4tv.ipradio.model.McuLogData
import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.ServerData
import app.tek4tv.ipradio.model.findIndex
import app.tek4tv.ipradio.model.parseDeviceStatusResponse
import app.tek4tv.ipradio.network.ApiService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.CrashHandler
import app.tek4tv.ipradio.utils.CrashHandler.Companion.restartApp
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.StringValue.Companion.PAStatusOn
import app.tek4tv.ipradio.utils.StringValue.Companion.UART_S1_NAME
import app.tek4tv.ipradio.utils.StringValue.Companion.UART_S4_NAME
import app.tek4tv.ipradio.utils.StringValue.Companion.liveFm
import app.tek4tv.ipradio.utils.StringValue.Companion.liveFmSohoaLoi
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.downloadUpdateApk
import app.tek4tv.ipradio.utils.getEnableMobileData
import app.tek4tv.ipradio.utils.getStateMobileData
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.setAirplaneModeEnable
import app.tek4tv.ipradio.utils.setEthernetEnabled
import app.tek4tv.ipradio.utils.setMobileDataEnabled
import app.tek4tv.ipradio.utils.setPreferredMode
import app.tek4tv.ipradio.utils.setWifiDataEnabled
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import app.tek4tv.ipradio.utils.toCalendar
import app.tek4tv.ipradio.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), AlarmListener {

    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var authService: ApiService

    private lateinit var hubManager: HubManager

    private lateinit var playerManager: PlayersManager

    private lateinit var streamBackManager: StreamBackManager

    private lateinit var serialPortController: SerialPortController

    private lateinit var appStorageManager: AppStorageManager

    private lateinit var mediaScheduler: MediaScheduler

    private val globalProperties = GlobalProperties.getInst()


    private lateinit var binding: ActivityMainBinding
    private lateinit var restartGuardManager: RestartGuardManager

    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            Log.d("loghub", "onTimeOver: come")
            serialPortController.turnOffPA()
            serialPortController.writeForceSetVolume(globalProperties.mainVolume)
            hubManager.currentMediaItem = null
        }

        //Phát FM
        override fun onPlayTuner(mediaItem: MediaFile) {
            try {
                val fmChannel = mediaItem.convertKHzToFm(mediaItem.url)
                if (fmChannel == "") return
//                val tunerType = if (mediaItem.getMediaType() == MediaFileType.AM) "0" else "1"
                val pipsData = "${globalProperties.piCode},${globalProperties.psName}"
                val tunerData = "1,${fmChannel},${globalProperties.fmVolume}"

                hubManager.currentMediaItem = mediaItem

//                if (!serialPortController.isUart1) {
//                    lifecycleScope.launch {
//                        delay(11000)
//                        serialPortController.playTuner(pipsData, tunerData)
//                    }
//                } else {
//                    serialPortController.playTuner(pipsData, tunerData)
//                }
                serialPortController.playTuner(pipsData, tunerData)

                val id = System.currentTimeMillis()
                val logs = listOf(
                    McuLogData(
                        id = id,
                        time = System.currentTimeMillis() / 1000.0,
                        type = 3,
                        detail = """{"m":${mediaItem.id}}"""
                    )
                )
                hubManager.sendLog(logs)
                hubManager.sendStatus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        //Dừng phát khẩn cấp
        override fun onEndForceMedia(mediaInfo: MediaInfo?) {
            if (mediaInfo == null) return
            Log.d("MediaInfo", "onEndForceMedia saved to local history")
            viewModel.insertMediaInfoLiveHistory(mediaInfo)
            ForceMedia.deleteForeMediaPref(applicationContext)
            playerManager.resetPlayMode()
            startPlayingMedia()
        }

        //Lỗi luồng live
        override fun onPlayError(mediaItem: MediaFile?) {
            // hubManager.currentMediaItem = null
//            serialPortController.turnOffPA()
        }


        override fun onPlayMediaSuccess(mediaItem: MediaFile?) {
            if (mediaItem != null && mediaItem.id != 0L) {

                hubManager.currentMediaItem = mediaItem

                serialPortController.turnOnPAVod()

                val id = System.currentTimeMillis()

                val logs = listOf(
                    McuLogData(
                        id = id,
                        time = System.currentTimeMillis() / 1000.0,
                        type = 3,
                        detail = """{"m":${mediaItem.id}}"""
                    )
                )
                hubManager.sendLog(logs)
                hubManager.sendStatus()
            }
        }

        override fun onEndMedia(mediaItem: MediaFile?) {
            if (mediaItem == null) return
            val id = System.currentTimeMillis()

            val logs = listOf(
                McuLogData(
                    id = id,
                    time = System.currentTimeMillis() / 1000.0,
                    type = 31,
                    detail = """{"m":${mediaItem.id}}"""
                )
            )
            hubManager.sendLog(logs)
            hubManager.currentMediaItem = null
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel.grantPermissionThoughAdb(baseContext)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        val hubComm = object : HubComm {
            override fun forceRestartApp(countHardRestart: Int) {
                lifecycleScope.launch {
                    //sau khi 30p reset app thì sẽ reset phần cứng.
                    Log.d("resetApp", "force reset $countHardRestart")
                    if (countHardRestart >= 6) {
                        ConfigUtil.putInt(baseContext, ConfigUtil.CountForHardReset, 0)
                        serialPortController.hardRestart()
                    }
                    ConfigUtil.putInt(
                        baseContext, ConfigUtil.CountForHardReset, countHardRestart + 1
                    )
                    Log.d("turnPA", "force reset")
                    delay(3000)
                    //bật tắt lại mạng di động
                    setMobileDataEnabled(false)
                    setWifiDataEnabled(true)
                    delay(3000)
                    setMobileDataEnabled(true)
                    ConfigUtil.removeSharef(baseContext, Utils.DEVICE_ID)
                    delay(3000)
                    restartApp(MyApp.instance!!, this@MainActivity)
                }
            }

            override fun forceRestartDevice() {
                serialPortController.hardRestart()
            }
        }

        appStorageManager = AppStorageManager(applicationContext)

        hubManager = HubManager(
            authService, baseContext, CoroutineScope(Dispatchers.Main)
        ) { command, message ->
            handleFromCommandServer(command, message)
        }
        serialPortController = SerialPortController(applicationContext, lifecycleScope, hubManager)

        serialPortController.connectToSerialPort(UART_S4_NAME)

//        lifecycleScope.launch {
//            delay(10000)
//            if (!serialPortController.isUart1) {
//                serialPortController.connectToSerialPort(UART_S4_NAME)
//                Log.i("PA", "onCreate: s4")
//            }
//        }

        playerManager = PlayersManager(
            applicationContext, viewModel.mediaRepo
        )
        playerManager.playerManagerListener = playerCallback

        streamBackManager = StreamBackManager(this@MainActivity)

        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        alarmListener = this


        initSettings()

        initHubConnect()

        initWebConsole()

        registerObservers()

        setMaxVolume()

        hideSystemUI()

        showMac()


        NetworkUtils.instance.startNetworkListener(this)
        viewModel.clearLocalMediaInfoExpired(applicationContext)
        restartGuardManager = RestartGuardManager(globalProperties)
        globalProperties.saveAppStartTime(System.currentTimeMillis())

    }

    private fun showMac() {
        val tvMacAddress = findViewById<TextView>(R.id.tvMac)
        val imei = hubManager.deviceImei
        tvMacAddress.text = "$imei - Ver: ${hubManager.versionName}"
        tvMacAddress.setOnClickListener {
            hubManager.handlerMessage(liveFmSohoaLoi)
            //serialPortController.writeSetESimMode("0")
            //serialPortController.turnOnPAVod()
        }
    }


    override fun onStart() {
        super.onStart()
        viewModel.getPlayerContentLocal()
    }

    override fun onDestroy() {
        super.onDestroy()
        hubManager.disconnectAll()
        playerManager.onActivityStop()
        playerManager.onActivityDestroy()
        cancelAllAlarm()
        LocalWebConsole.stop()
    }


    override fun onAlarm(intent: Intent) {
        when (intent.action) {
            MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", listKey!!)
                Log.d("Mediainfo", "onAlarm start $listKey")
                val t = listKey.split("-")
                val start = toCalendar(t[0])
                val resumePoint = playerManager.calculateResumePoint(start, listKey)
                playerManager.setTimeFrame(listKey, resumePoint)
            }

            MediaScheduleReceiver.ACTION_END_PLAYLIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", "end: $listKey")
                Log.d("Mediainfo", "onAlarm end $listKey")
                playerManager.stopPlaylist(listKey!!)
            }

            MediaScheduleReceiver.ACTION_END_FORCE_MEDIA -> {
                Log.d("MediaInfo", "End Live")
                Log.d("alarmx", "stop force")
                Log.d("turnPA", "on alarm end force media")
                playerManager.stopForceMedia()

            }


            MediaScheduleReceiver.ACTION_CHECKHUB_RESET -> {
                lifecycleScope.launch {
                    mediaScheduler.cancelAllAlarm()
                    serialPortController.turnOffPA()
                    delay(120000)
                    Log.e("alarmx", "Hub restart")
                    serialPortController.hardRestart()
                }
            }
        }
    }

//    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
//        return true
//    }
//
//    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
//        return true
//    }

    private fun hideSystemUI() {
        val decorView: View = window.decorView
        val uiOptions = decorView.systemUiVisibility
        var newUiOptions = uiOptions
        newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_LOW_PROFILE
        newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_FULLSCREEN
        newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE
        newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        decorView.systemUiVisibility = newUiOptions
    }


    private fun registerObservers() {
        lifecycleScope.launch {
            viewModel.schedulerUpdated.collect {
                hubManager.currentMediaInfoListId = viewModel.getMediaInfoIdList()
                viewModel.downloadMedias()
                Log.d("checkcheck", "scheduler updated")
                if (ForceMedia.hasValidForceMedia(applicationContext)) {
                    val forceMediaInfo = getLocalForceMedia(applicationContext)
                    if (forceMediaInfo != null) {
                        Log.d(
                            "MediaInfo",
                            "handleFromCommandServer: Play Live from local"
                        )
                        playerManager.playForceMedia(
                            forceMediaInfo.endTime, forceMediaInfo, mediaScheduler
                        )
                    }
                    return@collect
                }

                //playerManager.stopAllPlayer()
                if (playerManager.playMode == PLAY_MODE_FORCE) {
                    Log.d(
                        "MediaInfo",
                        "Live is playing, skip cancelAllAlarm and scheduleAllAlarms"
                    )
                    return@collect
                }

                cancelAllAlarm()
                if (globalProperties.settingsDisable == 0) {
                    mediaScheduler.scheduleAllAlarms()
                    playerManager.getPlay()
                }
            }
        }
    }

    fun startPlayingMedia() {
        viewModel.getPlayerContent()
    }

    private fun initSettings() {
        val networkType = globalProperties.settingsTxType
        when (networkType) {
            0 -> {
                Log.d("initSettings", "initSettings: Ethernet")
                setEthernetEnabled(true)
            }

            1 -> {
                Log.d("initSettings", "initSettings: Sim")

                val state = getStateMobileData().trim()
                val isEnable = getEnableMobileData().trim()

                val simStates = state.split(",").map { it.trim() }
                val hasLoadedSim = simStates.any { it.contains("LOADED", ignoreCase = true) }

                Log.d(
                    "initSettings",
                    "initSettings: Sim state=$state, mobileData=${if (isEnable == "1") "enable" else "disable"}"
                )

                lifecycleScope.launch {
                    if (!hasLoadedSim) {
                        Log.d("initSettings", "No loaded SIM, reset radio/network")

                        setEthernetEnabled(false)
                        setWifiDataEnabled(false)
                        setAirplaneModeEnable(true)
                        delay(2000)
                        setAirplaneModeEnable(false)
                        delay(1500)
                        setMobileDataEnabled(true)
                    }

                    if (isEnable == "0") {
                        Log.d("initSettings", "Enable mobile data")
                        setMobileDataEnabled(true)
                    }
                }
            }

            2 -> {
                Log.d("initSettings", "initSettings: Wifi")
                setWifiDataEnabled(true)
            }
        }
    }

    private fun initHubConnect() {
        hubManager.connectToServer()
    }

    private fun initWebConsole() {
        LocalWebConsole.start(onServerUpdate = { serverData ->
            val serverData = ServerData(
                bootstrapServer = serverData.bootstrapServer,
                mqttServer = serverData.mqttServer,
                mqttPort = serverData.mqttPort,
                mqttSecurity = serverData.mqttSecurity
            )
            globalProperties.saveServerConfig(serverData)
            Log.d("webconsole", "Đã cập nhật Server và gửi OPU_SETTINGS")
            lifecycleScope.launch {
                delay(5000)
                Log.e("webconsole", "Rebooting....")
                serialPortController.hardRestart()
            }
        }, onConnectionUpdate = { connData ->
            val bootstrapResp = BootstrapResp(
                id = connData.thingId,
                key = connData.thingKey,
                channels = listOf(Channel(id = connData.channels))
            )
            globalProperties.saveConnectionConfig(bootstrapResp)
            Log.d("webconsole", "Đã nhận ThingID mới: ${connData.thingId}, đang reboot...")
            lifecycleScope.launch {
                delay(5000)
                Log.e("webconsole", "Rebooting....")
                serialPortController.hardRestart()
            }
        }, onFirmwareUpdate = { firmwareData ->
            Log.d("webconsole", "Đã nhận Firmware: $firmwareData")
            val mode = firmwareData.updateMode
            val url = firmwareData.url
            if (mode == 1) {
                globalProperties.clearAllLocalConfigData()
            }
            downloadUpdateApk(
                url, this, serialPortController
            )
        }

        )
    }

    private fun handleFromCommandServer(command: String?, message: String?) {
        try {

            val responseHub = hubManager.responseHub

            when (command) {

                HubCommand.SAVE_SCHEDULER -> {
                    viewModel.saveSchedulerToLocal(responseHub.listMediaInfo)
                }

                HubCommand.DELETE_SCHEDULER -> {
                    val mediaInfo = responseHub.listMediaInfo?.firstOrNull()
                    Log.d(
                        "MediaInfo", "Xóa và dừng phát bản tin: ${mediaInfo?.mid}"
                    )
                    viewModel.deleteLocalScheduler(mediaInfo)
                    viewModel.deleteMediaInfInRepo(mediaInfo)
                    playerManager.stopMediaInfo(mediaInfo)
                }

                HubCommand.UPDATE_SCHEDULER -> {
                    clearDatastore()
                    Log.d(
                        "MediaInfo", "Cập nhật phát bản tin"
                    )
                    //Cập nhật 1 mediainfo, tranh dung phat khi cap nha
                    if (responseHub.listMediaInfo?.size == 1) {
                        viewModel.updateSingleMedia(responseHub.listMediaInfo?.firstOrNull())
                    } else {
                        viewModel.updateScheduler(responseHub.listMediaInfo)
                    }
                    //viewModel.updateScheduler(responseHub.listMediaInfo)
                }
                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {
                    if (responseHub.listLiveMediaInfo != null) {
                        lifecycleScope.launch {
                            val listNew = viewModel.getOnlyNewMedia(responseHub.listLiveMediaInfo)
                            if (listNew.isNotEmpty()) {
                                Log.d(
                                    "MediaInfo",
                                    "handleFromCommandServer: Play Live ${listNew.first().summary}"
                                )
                                onPlayForce(listNew.first())
                            } else {
                                Log.d(
                                    "MediaInfo", "handleFromCommandServer: Live đã phát rồi"
                                )
                            }
                        }
                        //   onPlayForce(responseHub.listLiveMediaInfo!!.first())
                    }
                }

                HubCommand.UPDATE_SETTING_CONFIG -> {
                    if (responseHub.settingConfig != null) {
                        val data = responseHub.settingConfig
                        if (data?.mainVolume != null) {
                            serialPortController.writeForceSetVolume(data.mainVolume.toString())
                            globalProperties.saveMainVolume(data.mainVolume.toString())
                        }
                        if (data?.fmVolume != null) {
                            serialPortController.writeVolumeTuner(data.fmVolume.toString())
                            globalProperties.saveFMVolume(data.fmVolume.toString())
                        }
                        if (data?.txtype != null) {
                            when (data.txtype) {
                                //ETH
                                0 -> {
                                    setWifiDataEnabled(false)
                                    setMobileDataEnabled(false)
                                    setEthernetEnabled(true)
                                }
                                //Mobile
                                1 -> {
                                    lifecycleScope.launch {
                                        val sim = data.sim
                                        if (sim != null) {
                                            Log.d(
                                                "SettingInfo", "handleFromCommandServer: Esim init"
                                            )
                                            //ESimEnable
                                            serialPortController.writeSetESimMode("1")
                                            delay(2000)
                                            setWifiDataEnabled(false)
                                            setEthernetEnabled(false)
                                            setMobileDataEnabled(false)
                                            setAirplaneModeEnable(true)
                                            delay(2000)
                                            setAirplaneModeEnable(false)
                                            delay(2000)
                                            setMobileDataEnabled(true)
                                        } else {
                                            Log.d(
                                                "SettingInfo", "handleFromCommandServer: Sim init"
                                            )
                                            //ESimDisable
                                            serialPortController.writeSetESimMode("0")
                                            if (globalProperties.settingsSimType == 1) {
                                                globalProperties.saveSimType(0)
                                                serialPortController.hardRestart()
                                            }
                                            delay(2000)
                                            setWifiDataEnabled(false)
                                            setEthernetEnabled(false)
                                            setMobileDataEnabled(false)
                                            setAirplaneModeEnable(true)
                                            delay(2000)
                                            setAirplaneModeEnable(false)
                                            delay(2000)
                                            setMobileDataEnabled(true)
                                        }
                                    }
                                    val mode = data.mobileMode
                                    when (mode) {
                                        0 -> {
                                            setPreferredMode(0)
                                        }

                                        2 -> {
                                            setPreferredMode(2)
                                        }

                                        3 -> {
                                            setPreferredMode(3)
                                        }

                                        4 -> {
                                            setPreferredMode(4)
                                        }
                                    }
                                }
                                //Wifi
                                2 -> {
                                    setWifiDataEnabled(true)
                                    setMobileDataEnabled(false)
                                    setEthernetEnabled(false)
                                }

                            }
                        }
                        globalProperties.saveSettingConfig(responseHub.settingConfig)
                    }
                }

                HubCommand.UPDATE_SERVER_CONFIG -> {
                    if (responseHub.serverConfig != null) {
                        hubManager.disconnectAll()
                        globalProperties.saveServerConfig(responseHub.serverConfig)
                        lifecycleScope.launch {
                            delay(5000)
                            serialPortController.hardRestart()
                        }
                    }
                }

                HubCommand.TURN_ON_PA -> {
                    Log.i("TURN_ON_PA", responseHub.message!!)
                    val mess = responseHub.message!!.toInt()
                    if (mess == PAStatusOn.toInt()) {
                        serialPortController.turnOnPAVod()

                    } else {
                        serialPortController.turnOffPA()
                    }
                }

                HubCommand.RESTART -> {
                    lifecycleScope.launch {
                        val msg = message?.trim()
                        val now = System.currentTimeMillis()

                        Log.d("handleFromCommandServer", "RESTART raw=$message trim=$msg")

                        when (msg) {
                            "0" -> {
                                Log.d("handleFromCommandServer", "RESTART device")
                                serialPortController.hardRestart()
                            }

                            "1" -> {
                                if (!restartGuardManager.canHandleRestartApp(now)) {
                                    val appStartTime = globalProperties.getAppStartTime()
                                    val blockedUntil = globalProperties.getRestartBlockedUntil()

                                    when {
                                        now - appStartTime < 15_000L -> {
                                            Log.d(
                                                "handleFromCommandServer",
                                                "Bỏ qua restart app: app mới khởi động ${now - appStartTime} ms"
                                            )
                                        }

                                        now < blockedUntil -> {
                                            Log.d(
                                                "handleFromCommandServer",
                                                "Bỏ qua restart app: đang block đến $blockedUntil"
                                            )
                                        }

                                        else -> {
                                            Log.d(
                                                "handleFromCommandServer",
                                                "Bỏ qua restart app: đang xử lý lệnh restart khác"
                                            )
                                        }
                                    }
                                    return@launch
                                }

                                restartGuardManager.markHandlingRestart(true)

                                try {
                                    restartGuardManager.resetWindowIfExpired(now)

                                    val allowed =
                                        restartGuardManager.registerRestartAndCheckAllowed(
                                            now
                                        )

                                    if (!allowed) {
                                        Log.d(
                                            "handleFromCommandServer",
                                            "RESTART app bị block 5 phút do vượt ngưỡng trong 60 giây"
                                        )
                                        return@launch
                                    }

                                    val currentCount = restartGuardManager.getCurrentRestartCount()

                                    Log.d(
                                        "handleFromCommandServer",
                                        "Cho phép RESTART app lần $currentCount trong cửa sổ 60 giây"
                                    )

                                    delay(1000)

                                    restartApp(MyApp.instance!!, this@MainActivity)

                                } catch (e: Exception) {
                                    Log.e(
                                        "handleFromCommandServer", "Lỗi xử lý RESTART app", e
                                    )
                                } finally {
                                    delay(2000)
                                    restartGuardManager.markHandlingRestart(false)
                                }
                            }

                            else -> {
                                Log.d(
                                    "handleFromCommandServer", "RESTART message không hợp lệ: $msg"
                                )
                            }
                        }
                    }
                }

                HubCommand.STREAM_BACK -> {
                    val streamBackData = responseHub.streamBackData
                    if (streamBackData != null) {
                        val deviceInfoData = parseDeviceStatusResponse(deviceInfo)
                        when (streamBackData.cmd) {
                            0 -> {
                                val index = findIndex(0, deviceInfoData?.audioStatus)
                                Log.e(
                                    "STREAMBACK", "source 1-3 at =${index}"
                                )
                                serialPortController.streamBack(index.toString())
                            }

                            1 -> {
                                val index = findIndex(1, deviceInfoData?.audioStatus)
                                Log.e(
                                    "STREAMBACK", "source 2-4 at =${index}"
                                )
                                serialPortController.streamBack(index.toString())
                            }
                        }
                        streamBackManager.handleStreamBack(streamBackData, {
                            serialPortController.streamBack("0")
                        })
                    }
                }

                HubCommand.SET_MUTE_DEVICE -> {
                    if (responseHub.message != null) {
                        serialPortController.setMuteDevice(responseHub.message!!)
                    }
                }


                HubCommand.UPDATE_VERSION -> {

                    if (responseHub.updateFirmData != null) {
                        val updateFirmData = responseHub.updateFirmData
                        Log.d("update", updateFirmData?.url.toString())
                        Log.d("update", updateFirmData?.option.toString())
                        if (updateFirmData?.option == 0) {
                            downloadUpdateApk(
                                updateFirmData.url, this, serialPortController
                            )
                        } else if (updateFirmData?.option == 1) {
                            globalProperties.clearAllLocalConfigData()
                            downloadUpdateApk(
                                updateFirmData.url, this, serialPortController
                            )
                        }
                    }
                }

                HubCommand.READ_DEVICE_INFO -> {
                    serialPortController.readDeviceInfo()
                }

                HubCommand.DELETE_ALL_MEDIA -> {
                    appStorageManager.deleteAllMedia()
                    viewModel.deleteAllLocalScheduler()
                }
            }

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun onPlayForce(mediaInfo: MediaInfo) {
        //Tránh trường hợp âm lượng bị tắt
        serialPortController.writeForceSetVolume(globalProperties.mainVolume)
        playerManager.onPlayForceCommand(applicationContext, mediaInfo, mediaScheduler)
    }

    private fun cancelAllAlarm() {
        mediaScheduler.cancelAllAlarm()
    }

    private fun setMaxVolume() {
        try {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
        } catch (_: Exception) {
            Log.e("Main", "Error setting max volume")
        }
    }

    private fun clearDatastore() {
        val limit = 524288000
        val totalStorage = appStorageManager.getTotalRomStorage()
        val usedStorage = appStorageManager.getUsedRomStorage()
        if (totalStorage - usedStorage <= limit) {
            Log.d("clearDatastore", "clearDatastore: Must Clear")
            appStorageManager.deleteAllMedia()
        } else {
            Log.d("clearDatastore", "clearDatastore: Dont have to clear")
        }
    }

    companion object {
        var alarmListener: AlarmListener? = null

    }

}