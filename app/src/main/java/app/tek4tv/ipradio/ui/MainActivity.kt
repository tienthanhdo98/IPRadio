package app.tek4tv.ipradio.ui


import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
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
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.core.players_helper.IPlayerManagerListener
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import app.tek4tv.ipradio.core.stream.StreamBackManager
import app.tek4tv.ipradio.core.stream.StreamBackManagerVlc
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
import app.tek4tv.ipradio.network.ApiService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.CrashHandler.Companion.restartApp
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.StringValue.Companion.PAStatusOn
import app.tek4tv.ipradio.utils.StringValue.Companion.UART_S1_NAME
import app.tek4tv.ipradio.utils.StringValue.Companion.UART_S4_NAME
import app.tek4tv.ipradio.utils.StringValue.Companion.live1
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.downloadUpdateApk
import app.tek4tv.ipradio.utils.initESimConfig
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.setAirplaneModeEnable
import app.tek4tv.ipradio.utils.setESimEnable
import app.tek4tv.ipradio.utils.setEthernetEnabled
import app.tek4tv.ipradio.utils.setMobileDataEnabled
import app.tek4tv.ipradio.utils.setPreferredMode
import app.tek4tv.ipradio.utils.setWifiDataEnabled
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import app.tek4tv.ipradio.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import startMicToIcecast
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

    private lateinit var airplaneReceiver: AirplaneModeReceiver

    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            Log.d("loghub", "onTimeOver: come")
            serialPortController.turnOffPA()
            serialPortController.writeForceSetVolume(globalProperties.volume)
            hubManager.currentMediaItem = null
        }

        //Phát FM
        override fun onPlayTuner(mediaItem: MediaFile) {
            try {
//                val tunerType = if (mediaItem.getMediaType() == MediaFileType.AM) "0" else "1"
                val pipsData = "${globalProperties.piCode},${globalProperties.psName}"
                val tunerData = "1,${mediaItem.url},${globalProperties.volume}"

                hubManager.currentMediaItem = mediaItem

                if (!serialPortController.isUart1) {
                    lifecycleScope.launch {
                        delay(11000)
                        serialPortController.playTuner(pipsData, tunerData)
                    }
                } else {
                    serialPortController.playTuner(pipsData, tunerData)
                }

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
            if (mediaInfo != null) {
                Log.d("MediaInfo", "onEndForceMedia saved to local history")
                viewModel.insertMediaInfoLiveHistory(mediaInfo)
            }
            ForceMedia.deleteForeMediaPref(applicationContext)
            startPlayingMedia()
        }

        //Lỗi luồng live
        override fun onLiveError(mediaItem: MediaFile?) {
            // hubManager.currentMediaItem = null
            serialPortController.turnOffPA()
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

        override fun onEndMedia() {
            hubManager.currentMediaItem = null
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel.grantPermissionThoughAdb(baseContext)
        //Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

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
                    //lấy lại imei trong trường hợp lỗi khi lấy imei
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

        serialPortController.connectToSerialPort(UART_S1_NAME)

        lifecycleScope.launch {
            delay(10000)
            if (!serialPortController.isUart1) {
                serialPortController.connectToSerialPort(UART_S4_NAME)
                Log.i("PA", "onCreate: s4")
            }
        }

        playerManager = PlayersManager(
            applicationContext, viewModel.mediaRepo
        )
        playerManager.playerManagerListener = playerCallback

        streamBackManager = StreamBackManager(applicationContext)

        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        alarmListener = this

        NetworkUtils.instance.startNetworkListener(this)

        initHubConnect()

        registerObservers()

        setMaxVolume()

        hideSystemUI()

        showMac()

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
        })

        viewModel.clearLocalMediaInfoExpired(applicationContext)

        airplaneReceiver = AirplaneModeReceiver { enabled ->
            if (enabled) {
                println("Airplane mode ON")
            } else {
                println("Airplane mode OFF")
            }
        }
    }

    private fun showMac() {
        val tvMacAddress = findViewById<TextView>(R.id.tvMac)
        val imei = hubManager.deviceImei
        tvMacAddress.text = "$imei - Ver: ${hubManager.versionName}"
        tvMacAddress.setOnClickListener {
            hubManager.handlerMessage(live1)
        }
    }


    override fun onStart() {
        super.onStart()
        viewModel.getPlayerContentLocal()
        val filter = IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(airplaneReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(airplaneReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        playerManager.onActivityStop()
        LocalWebConsole.stop()
        cancelAllAlarm()
        //
        unregisterReceiver(airplaneReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.onActivityDestroy()
    }


    override fun onAlarm(intent: Intent) {
        when (intent.action) {
            MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", listKey!!)
                Log.d("Mediainfo", "onAlarm start $listKey")
                playerManager.setTimeFrame(listKey)
            }

            MediaScheduleReceiver.ACTION_END_PLAYLIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", "end: $listKey")
                Log.d("Mediainfo", "onAlarm end $listKey")
                playerManager.stopPlaylist(listKey!!)
            }

            MediaScheduleReceiver.ACTION_END_FORCE_MEDIA -> {
                playerManager.stopForceMedia()
                Log.d("MediaInfo", "End Live")
                Log.d("alarmx", "stop force")
                Log.d("turnPA", "on alarm end force media")
            }


            MediaScheduleReceiver.ACTION_CHECKHUB_RESET -> {
                lifecycleScope.launch {
                    mediaScheduler.cancelAllScheduledListAlarm()
//                    viewModel.rescheduleMonthlyPlaylist()
//                    hubManager.reconnectToServer()
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
                        playerManager.playForceMedia(
                            forceMediaInfo.endTime,
                            forceMediaInfo,
                            mediaScheduler
                        )
                    }
                    return@collect
                }
                cancelAllAlarm()
                mediaScheduler.scheduleAllAlarms()
                playerManager.getPlay()
            }
        }

    }

    fun startPlayingMedia() {
        viewModel.getPlayerContent()
    }

    private fun initHubConnect() {
        hubManager.connectToServer()
    }

    private fun handleFromCommandServer(command: String?, message: String?) {
        try {
//            if (command == null || command.isEmpty()) {
//                return
//            }
//            if (message == null || message.isEmpty()) {
//                return
//            }

            val responseHub = hubManager.responseHub


            when (command) {

                HubCommand.SAVE_SCHEDULER -> {
                    viewModel.saveSchedulerToLocal(responseHub.listMediaInfo)
                }

                HubCommand.UPDATE_SCHEDULER -> {
                    clearDatastore()
                    playerManager.resetPlayMode()
                    playerManager.stopAllPlayer()
//                    ForceMedia.deleteForeMediaPref(applicationContext)
                    Log.d("sds", "handleFromCommandServer: $message ")
                    viewModel.updateScheduler(responseHub.listMediaInfo)
                }
                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {
                    if (responseHub.listLiveMediaInfo != null) {
                        lifecycleScope.launch {
                            val listNew = viewModel.getOnlyNewMedia(responseHub.listLiveMediaInfo)
                            if (listNew.isNotEmpty()) {
                                onPlayForce(listNew.first())
                            }
                        }
                    }
                }

                HubCommand.UPDATE_SETTING_CONFIG -> {
                    if (responseHub.settingConfig != null) {
                        val data = responseHub.settingConfig
                        if (data?.fmVolume != null || data?.mainVolume != null) {
                            serialPortController.writeForceSetVolume(data.fmVolume.toString())
                            globalProperties.saveVolume(data.fmVolume.toString())
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
                                                "SettingInfo",
                                                "handleFromCommandServer: Esim init"
                                            )
                                            initESimConfig()
                                            delay(2000)
                                            setESimEnable(true)
                                            delay(2000)
                                            setWifiDataEnabled(false)
                                            setEthernetEnabled(false)
                                            setMobileDataEnabled(false)
                                            setAirplaneModeEnable(false)
                                            delay(2000)
                                            setWifiDataEnabled(false)
                                            setEthernetEnabled(false)
                                            setMobileDataEnabled(false)
                                            setAirplaneModeEnable(false)
                                            delay(2000)
                                            setMobileDataEnabled(true)
                                        } else {
                                            Log.d(
                                                "SettingInfo",
                                                "handleFromCommandServer: Sim init"
                                            )
                                            setESimEnable(false)
                                            delay(2000)
                                            setWifiDataEnabled(false)
                                            setEthernetEnabled(false)
                                            setMobileDataEnabled(false)
                                            setAirplaneModeEnable(false)
                                            delay(2000)
                                            setWifiDataEnabled(false)
                                            setEthernetEnabled(false)
                                            setMobileDataEnabled(false)
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
                        hubManager.disconnect()
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
                        Log.d("handleFromCommandServer", "RESTART: ${message}")
                        if (message == "0") {
                            //reset thiết bị
                            serialPortController.hardRestart()
                        } else if (message == "1") {
                            //reset app
                            restartApp(MyApp.instance!!, this@MainActivity)
                        }

                    }

                }

                HubCommand.STREAM_BACK -> {
                    val message = responseHub.message
                    if (message != null) {
//                        streamBackManager.handleStreamBack(
//                            url = message, cmd = responseHub.level, timeout = responseHub.duration
//                        )

                        //vlc

                        startMicToIcecast(this, message)
                    }
                }

                HubCommand.SET_MUTE_DEVICE -> {
                    if (responseHub.message != null) {
                        serialPortController.setMuteDevice(responseHub.message!!)
                    }
                }

//                HubCommand.SET_VOLUME_DEVICE -> {
//                    if (responseHub.message != null && responseHub.message != "") {
//                        Log.d("UpdateVolume", "handleFromCommandServer: ${responseHub.message}")
//                        serialPortController.writeForceSetVolume(responseHub.message!!)
//                        globalProperties.saveVolume(responseHub.message!!)
//                    }
//                }

                HubCommand.UPDATE_VERSION -> {

                    if (responseHub.updateFirmData != null) {
                        val updateFirmData = responseHub.updateFirmData
                        Log.d("update", updateFirmData?.url.toString())
                        Log.d("update", updateFirmData?.option.toString())
                        if (updateFirmData?.option == 0) {
                            downloadUpdateApk(updateFirmData.url, this, serialPortController)
                        } else if (updateFirmData?.option == 1) {
                            globalProperties.clearAllLocalConfigData()
                            downloadUpdateApk(updateFirmData.url, this, serialPortController)
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
        serialPortController.writeForceSetVolume(globalProperties.volume)
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