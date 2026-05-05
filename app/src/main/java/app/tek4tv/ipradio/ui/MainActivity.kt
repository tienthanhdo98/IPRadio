package app.tek4tv.ipradio.ui


import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.HubManager
import app.tek4tv.ipradio.core.IHubObserve
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.core.RestartGuardManager
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.core.SerialPortController.Companion.deviceInfo
import app.tek4tv.ipradio.core.players_helper.IPlayerManagerListener
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.PLAY_MODE_FORCE
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.PLAY_MODE_TEST
import app.tek4tv.ipradio.core.stream.StreamBackManager
import app.tek4tv.ipradio.core.webserver.LocalWebConsole
import app.tek4tv.ipradio.databinding.ActivityMainBinding
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.ForceMedia.Companion.getLocalForceMedia
import app.tek4tv.ipradio.model.McuSettingsData
import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.ServerData
import app.tek4tv.ipradio.model.serialport.findIndex
import app.tek4tv.ipradio.model.serialport.parseDeviceStatusResponse
import app.tek4tv.ipradio.model.webconsole.ClassDUiModel
import app.tek4tv.ipradio.model.webconsole.StatusPageData
import app.tek4tv.ipradio.model.webconsole.ConnectionSettings
import app.tek4tv.ipradio.model.webconsole.DeviceBasicInfo
import app.tek4tv.ipradio.network.ApiService
import app.tek4tv.ipradio.utils.CrashHandler.Companion.restartApp
import app.tek4tv.ipradio.utils.DeviceUtils
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.StringValue.Companion.PAStatusOn
import app.tek4tv.ipradio.utils.StringValue.Companion.UART_S4_NAME
import app.tek4tv.ipradio.utils.downloadUpdateApk
import app.tek4tv.ipradio.utils.getStateMobileData
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.network.connectWifi
import app.tek4tv.ipradio.utils.setAirplaneModeEnable
import app.tek4tv.ipradio.utils.setEthernetEnabled
import app.tek4tv.ipradio.utils.setMobileDataEnabled
import app.tek4tv.ipradio.utils.setPreferredMode
import app.tek4tv.ipradio.utils.setWifiDataEnabled
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import app.tek4tv.ipradio.utils.su.NetworkShellUtils
import app.tek4tv.ipradio.utils.unixSecondsToCalendar
import app.tek4tv.ipradio.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), AlarmListener, IHubObserve {

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

    private var isSelectingNetwork = false
    private var networkReselectJob: Job? = null
    private var ipCheckJob: Job? = null
    private var networkPeriodicJob: Job? = null

    private val networkCallback = object : NetworkUtils.ConnectionCallback {
        override fun onChange(netWorkState: Boolean) {
            if (isSelectingNetwork) return

            Log.d("initSettings", "Network changed: $netWorkState")

            networkReselectJob?.cancel()
            networkReselectJob = lifecycleScope.launch {
                delay(1500)

                val stillConnected = NetworkUtils.instance.isNetworkConnected

                if (netWorkState) {
                    if (!isSelectingNetwork) initSettings()
                    return@launch
                }

                if (!stillConnected) {
                    if (!isSelectingNetwork) initSettings()
                } else {
                    Log.d("initSettings", "Bỏ qua reselect vì vẫn còn mạng")
                }
            }
        }
    }

    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            Log.d("MediaInfo", "onTimeOver: come")
            serialPortController.turnOffPA()
            //serialPortController.writeForceSetVolume(globalProperties.mainVolume)
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

                serialPortController.playTuner(pipsData, tunerData)

                viewModel.addLog(
                    type = 3, detail = """{"m":${mediaItem.id}}""", hubManager = hubManager
                )
                hubManager.sendStatus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onForceMediaExpired(mediaInfo: MediaInfo?) {
            if (mediaInfo == null) return
            Log.d("MediaInfo", "onForceMediaExpired clear data")
            viewModel.insertMediaInfoLiveHistory(mediaInfo)
            ForceMedia.deleteForeMediaPref(applicationContext)
            playerManager.resetPlayMode()
        }

        //Dừng phát khẩn cấp
        override fun onEndForceMedia(mediaInfo: MediaInfo?) {
            if (mediaInfo == null) return
            Log.d("MediaInfo", "onEndForceMedia saved to local history")
            viewModel.insertMediaInfoLiveHistory(mediaInfo)
            ForceMedia.deleteForeMediaPref(applicationContext)
            playerManager.resetPlayMode()
            serialPortController.turnOffPA()
            startPlayingMedia()
        }


        //Lỗi luồng live
        override fun onPlayError(mediaItem: MediaFile?) {
            // hubManager.currentMediaItem = null
//            serialPortController.turnOffPA()
            if (playerManager.playMode == PLAY_MODE_TEST) {
                LocalWebConsole.updateMediaStatusPlaying(0)
            }
        }


        override fun onPlayMediaSuccess(mediaItem: MediaFile?) {
            if (mediaItem == null) return
            if (playerManager.playMode == PLAY_MODE_TEST) {
                LocalWebConsole.updateMediaStatusPlaying(1)
            }
            hubManager.currentMediaItem = mediaItem

            serialPortController.turnOnPAVod()

            viewModel.addLog(
                type = 3, detail = """{"m":${mediaItem.id}}""", hubManager = hubManager
            )
            hubManager.sendStatus()

        }

        override fun onEndMedia(mediaItem: MediaFile?) {
            if (mediaItem == null) return
            viewModel.addLog(
                type = 31, detail = """{"m":${mediaItem.id}}""", hubManager = hubManager
            )
            hubManager.currentMediaItem = null
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel.grantPermissionThoughAdb(baseContext)
        //Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        appStorageManager = AppStorageManager(applicationContext)

        hubManager = HubManager(
            authService, baseContext, CoroutineScope(Dispatchers.Main)
        ) { command, message ->
            handleFromCommandServer(command, message)
        }
        hubManager.hubObverse = this
        hubManager.prepareMcuInfo()
        hubManager.onMcuInfoUpdated = { info ->
            LocalWebConsole.updateMcuInfoData(info)
        }
        serialPortController = SerialPortController(applicationContext, lifecycleScope, hubManager)

        serialPortController.connectToSerialPort(UART_S4_NAME)

        initSerialConfig()

        streamBackManager = StreamBackManager(this@MainActivity)

        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        playerManager = PlayersManager(
            applicationContext, viewModel.mediaRepo, mediaScheduler
        )
        playerManager.playerManagerListener = playerCallback

        alarmListener = this


        initHubConnect()

        initWebConsole()

        registerObservers()

        setMaxVolume()

        hideSystemUI()

        showMac()

        NetworkUtils.instance.startNetworkListener(this)
        NetworkUtils.instance.addCallback(networkCallback)
        viewModel.clearLocalMediaInfoExpired(applicationContext)
        initSettings()
        startIpCheckTimer()
        startNetworkPeriodicCheck()
        restartGuardManager = RestartGuardManager(globalProperties)
        globalProperties.saveAppStartTime(System.currentTimeMillis())
        viewModel.addLog(
            type = 8,
            detail = """{"u":${SystemClock.elapsedRealtime() / 1000}}""",
            hubManager = hubManager
        )
        Log.d("MQTTLOG", "onCreate instance=${this.hashCode()} taskId=$taskId intent=$intent")

    }

    private fun startNetworkPeriodicCheck() {
        networkPeriodicJob?.cancel()
        networkPeriodicJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(120_000) // Kiểm tra mỗi 2 phút
                if (isSelectingNetwork) continue

                try {
                    val isConnected = NetworkUtils.isNetworkConnected(this@MainActivity)
                    val prefType = globalProperties.settingsTxType

                    var isPrefConnected = false
                    when (prefType) {
                        0 -> {
                            val ip =
                                NetworkUtils.getRealEthernetIp() ?: NetworkUtils.getEthernetLanIp(
                                    this@MainActivity
                                )
                            isPrefConnected = !ip.isNullOrBlank()
                        }

                        1 -> {
                            val ip = NetworkUtils.getMobileLocalIp(this@MainActivity)
                            isPrefConnected = !ip.isNullOrBlank()
                        }

                        2 -> {
                            val cm =
                                getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                            val activeNet = cm.activeNetworkInfo
                            isPrefConnected =
                                activeNet != null && activeNet.type == android.net.ConnectivityManager.TYPE_WIFI && activeNet.isConnected
                        }
                    }

                    if (!isConnected || !isPrefConnected) {
                        Log.d(
                            "initSettings",
                            "Periodic check: not connected to preferred network (pref=$prefType, connected=$isConnected), running initSettings()"
                        )
                        launch(Dispatchers.Main) {
                            initSettings()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("initSettings", "Error in periodic network check", e)
                }
            }
        }
    }

    private fun startIpCheckTimer() {
        ipCheckJob?.cancel()
        ipCheckJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(10000) // Kiểm tra mỗi 10 giây
                try {
                    // Chỉ áp dụng IP tĩnh cho Ethernet (txType != 2) hoặc khi có cấu hình localIp hợp lệ
                    val targetIp = globalProperties.localIp
                    if (targetIp.isNotBlank() && targetIp != "0.0.0.0") {
                        val currentIp = NetworkUtils.getRealEthernetIp()
                            ?: NetworkUtils.getEthernetLanIp(this@MainActivity)
                        if (currentIp != null && currentIp.isNotBlank() && currentIp != targetIp) {
                            Log.d(
                                "IpCheck",
                                "IP changed from $targetIp to $currentIp, re-applying static IP"
                            )
                            val gateway = if (globalProperties.settingsTxType == 0) globalProperties.gateway else ""
                            NetworkShellUtils.setUniversalStaticIp(
                                targetIp,
                                globalProperties.subnet,
                                gateway,
                                "8.8.8.8",
                                "1.1.1.1"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("IpCheck", "Error checking IP", e)
                }
            }
        }
    }

    private fun showMac() {
        val tvMacAddress = findViewById<TextView>(R.id.tvMac)
        val imei = hubManager.deviceImei
        tvMacAddress.text = "$imei - Ver: ${hubManager.versionName}"
        tvMacAddress.setOnClickListener {
            lifecycleScope.launch() {
                Log.d("showWifi", "getAvailableWifiNetworks")
                val listName = NetworkUtils.getAvailableWifiNetworks(this@MainActivity)
                delay(1500)
                Log.d("showWifi", "showWifi get: ${listName.size}")
                listName.forEach {
                    Log.d("showWifi", "showWifi: $it")
                }
            }
//            changeVolume(
//                roundToNearestNumber(64)
//            )
        }
    }


    override fun onStart() {
        super.onStart()
        viewModel.getPlayerContentLocal()
    }

    override fun onDestroy() {
        super.onDestroy()
        ipCheckJob?.cancel()
        networkPeriodicJob?.cancel()
        NetworkUtils.instance.removeCallback(networkCallback)
        NetworkUtils.instance.stopNetworkListener(this)
        hubManager.disconnectAll()
        playerManager.onActivityDestroy()
        cancelAllAlarm()
        LocalWebConsole.stop()
    }


    override fun onAlarm(intent: Intent) {
        when (intent.action) {
            MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST -> {
                val listKey =
                    intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY) ?: return
                Log.d("alarmx", listKey)
                Log.d("Mediainfo", "onAlarm start $listKey")
                playerManager.onScheduleStart(listKey)
            }

            MediaScheduleReceiver.ACTION_END_PLAYLIST -> {
                val listKey =
                    intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY) ?: return
                Log.d("alarmx", "end: $listKey")
                Log.d("Mediainfo", "onAlarm end $listKey")
                playerManager.markPlaylistEnd(listKey)
            }

            MediaScheduleReceiver.ACTION_END_FORCE_MEDIA -> {
                Log.d("MediaInfo", "Force End Live")
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

//                LocalWebConsole.updateMediaList(
//                    viewModel.getScheduleList()
//                )
                viewModel.updateWebsocketListLocal()

                hubManager.currentMediaInfoListId = viewModel.getMediaInfoIdList()
                viewModel.downloadMedias()
                if (globalProperties.settingsDisable == 1) {
                    Log.d("checkcheck", "scheduler updated")
                    if (ForceMedia.hasValidForceMedia(applicationContext)) {
                        val forceMediaInfo = getLocalForceMedia(applicationContext)
                        if (forceMediaInfo != null) {
                            Log.d(
                                "MediaInfo", "handleFromCommandServer: Play Live from local"
                            )
                            playerManager.playForceMedia(
                                forceMediaInfo, mediaScheduler
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
        if (isSelectingNetwork) return
        val networkType = globalProperties.settingsTxType
        Log.d("initSettings", "Conn: $networkType")
        isSelectingNetwork = true
        lifecycleScope.launch {
            try {
                val connected = NetworkUtils.isNetworkConnected(this@MainActivity)
                if (connected) {
                    Log.d(
                        "initSettings",
                        "Đã có mạng sẵn, vẫn kiểm tra theo ưu tiên cấu hình $networkType"
                    )
                }

                val success = when (networkType) {
                    0 -> {
                        Log.d("initSettings", "Preferred network: Ethernet")
                        tryUseEthernet() || tryUseSim() || tryUseWifi()
                    }

                    1 -> {
                        Log.d("initSettings", "Preferred network: Sim")
                        tryUseSim() || tryUseEthernet() || tryUseWifi()
                    }

                    2 -> {
                        Log.d("initSettings", "Preferred network: Wifi")
                        tryUseWifi() || tryUseEthernet() || tryUseSim()
                    }

                    else -> {
                        Log.d("initSettings", "Unknown networkType, fallback all")
                        tryUseEthernet() || tryUseSim() || tryUseWifi()
                    }
                }

                Log.d("initSettings", "initSettings result: success=$success")

                if (!success) {
                    Log.d("initSettings", "Không tìm được loại mạng nào usable")
                }

            } finally {
                delay(1000)
                isSelectingNetwork = false
            }
        }
    }

    private suspend fun tryUseEthernet(): Boolean {
        Log.d("initSettings", "tryUseEthernet")

        val targetIp = globalProperties.localIp
        if (targetIp.isNotBlank() && targetIp != "0.0.0.0") {
            val currentIp =
                NetworkUtils.getRealEthernetIp() ?: NetworkUtils.getEthernetLanIp(this@MainActivity)
            if (currentIp != targetIp) {
                Log.d(
                    "initSettings",
                    "tryUseEthernet: IP changed from $currentIp to $targetIp, applying static IP..."
                )
                val gateway = if (globalProperties.settingsTxType == 0) globalProperties.gateway else ""
                NetworkShellUtils.setUniversalStaticIp(
                    targetIp,
                    globalProperties.subnet,
                    gateway,
                    "8.8.8.8",
                    "1.1.1.1"
                )
                delay(2500) // Đợi card mạng khởi động lại
            } else {
                Log.d("initSettings", "tryUseEthernet: IP is already $targetIp, skipping reset")
            }
        }

        // Clear any previous process binding so Ethernet becomes default again
        NetworkUtils.clearProcessNetworkBinding(this@MainActivity)

//        EthernetStaticIpUtils.forceStaticIp(
//            globalProperties.localIp, globalProperties.gateway, "8.8.8.8", "1.1.1.1"
//        )
        setEthernetEnabled(true)
        setWifiDataEnabled(false)
        setMobileDataEnabled(false)

        repeat(5) {
            delay(2000)
            val ethIp = NetworkUtils.getEthernetLanIp(this@MainActivity)
            val isValidated = NetworkUtils.instance.getCurrentConnectedState(this@MainActivity)
            val isWebConsoleActive = LocalWebConsole.isWebConsoleActive()

            Log.d(
                "initSettings",
                "tryUseEthernet: ethIp=$ethIp, validated=$isValidated, webConsoleActive=$isWebConsoleActive"
            )

            if (ethIp != null && (isValidated || isWebConsoleActive)) {
                return true
            }
        }

        Log.d("initSettings", "tryUseEthernet: ethernet không có mạng (internet)")
        return false
    }

    private suspend fun tryUseSim(): Boolean {
        Log.d("initSettings", "tryUseSim")

        var state = getStateMobileData().trim()
        var simStates = state.split(",").map { it.trim() }
        var hasLoadedSim = simStates.any { it.contains("LOADED", ignoreCase = true) }

        if (!hasLoadedSim) {
            Log.d("initSettings", "tryUseSim: no loaded SIM, reset radio")

            setWifiDataEnabled(false)
            setMobileDataEnabled(false)
            setAirplaneModeEnable(true)
            delay(1500)
            setAirplaneModeEnable(false)
            delay(1500)
            setMobileDataEnabled(true)

            delay(2000)

            state = getStateMobileData().trim()
            simStates = state.split(",").map { it.trim() }
            hasLoadedSim = simStates.any { it.contains("LOADED", ignoreCase = true) }
            if (!hasLoadedSim) {
                Log.d("initSettings", "tryUseSim: SIM vẫn chưa LOADED")
                return false
            }
        } else {
            setWifiDataEnabled(false)
            setMobileDataEnabled(false)
            setAirplaneModeEnable(true)
            delay(1500)
            setAirplaneModeEnable(false)
            delay(1500)
            setMobileDataEnabled(true)
            delay(2000)
        }

        repeat(5) {
            delay(1000)
            val mobileIp = NetworkUtils.getMobileLocalIp(this@MainActivity)
            Log.d("initSettings", "tryUseSim: mobileIp=$mobileIp")
            delay(1000)
//            if (mobileIp != null) {
//                setEthernetEnabled(false)
//                return true
//            }
            if (mobileIp == null) {
                setMobileDataEnabled(true)
            }
            if (mobileIp != null) {
                // Delete default route from eth0 to ensure internet traffic goes through SIM
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    app.tek4tv.ipradio.utils.su.NetworkShellUtils.executeSuCommands(arrayOf("ip route del default dev eth0"))
                }
                val bound = NetworkUtils.bindProcessToTransport(this@MainActivity, android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                Log.d("initSettings", "tryUseSim: process bound to cellular = $bound")
                return true
            }
        }

        Log.d("initSettings", "tryUseSim: mobile chưa lên")
        return false
    }

    private suspend fun tryUseWifi(): Boolean {
        Log.d("initSettings", "tryUseWifi")
        setWifiDataEnabled(true)
        setMobileDataEnabled(false)
        delay(2000)

        val ssid = globalProperties.wifiSsid
        val pass = globalProperties.wifiPassword

        if (ssid.isNotBlank()) {
            Log.d("initSettings", "tryUseWifi: connecting to SSID=$ssid")
            Log.d("initSettings", "tryUseWifi: connecting to SSID=$pass")
            delay(30000)
            connectWifi(ssid, pass)
            delay(30000) // chờ kết nối WiFi
        } else {
            Log.d("initSettings", "tryUseWifi: no SSID configured, chờ auto-reconnect")
            delay(3000)
        }

        val connected = NetworkUtils.isNetworkConnected(this@MainActivity)
        Log.d("initSettings", "tryUseWifi: connected=$connected")
        if (connected) {
            // Delete default route from eth0 to ensure internet traffic goes through WiFi
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                app.tek4tv.ipradio.utils.su.NetworkShellUtils.executeSuCommands(arrayOf("ip route del default dev eth0"))
            }
            val bound = NetworkUtils.bindProcessToTransport(this@MainActivity, android.net.NetworkCapabilities.TRANSPORT_WIFI)
            Log.d("initSettings", "tryUseWifi: process bound to wifi = $bound")
        }
        return connected
    }

    private fun initHubConnect() {
        Log.d("MQTTLOG", "onCreate instance=${this.hashCode()}")
        Log.d("MQTTLOG", "connectToServer hub=${this.hashCode()}")
        hubManager.connectToServer()
    }

    private fun initWebConsole() {
        //Info connection page
        LocalWebConsole.updateBasicInfo(
            DeviceBasicInfo(
                mgwId = hubManager.deviceImei,
                verifyCode = hubManager.verifyCode,
                firmwareVersion = hubManager.versionName
            )
        )
        LocalWebConsole.updateConnectionSettings(
            ConnectionSettings(
                localIp = globalProperties.localIp,
                subnet = globalProperties.subnet,
                gateway = globalProperties.gateway,
                mqttSecurity = globalProperties.mqttSecurity.toString(),
                mqttServer = globalProperties.mqttServer,
                mqttPort = globalProperties.mqttPort.toString(),
                networkType = globalProperties.settingsTxType.toString(),
                simSelection = globalProperties.settingsSimType.toString(),
                mobileDataMode = globalProperties.settingsSimMode.toString(),
                activation = globalProperties.settingsDisable.toString(),
                normalVolume = globalProperties.mainVolume,
                fmAutoStop = globalProperties.fmAutoStop.toString(),
                fmAutoThreshold = globalProperties.fmThreshold,
                wifiSsid = globalProperties.wifiSsid,
                wifiPassword = globalProperties.wifiPassword
            )
        )
        //Status page
        //ClassD
        val rawDeviceInfo = deviceInfo
        val deviceStatus = parseDeviceStatusResponse(rawDeviceInfo)

        val adcThresholds = (deviceStatus?.adcThresholds ?: emptyList())
        val classD = ClassDUiModel(
            threshold = globalProperties.classdThreshold,
            szdLeft = globalProperties.classdSzdLeft,
            szdRight = globalProperties.classdSzdRight,
            muteLeft = globalProperties.muteSpeakerLeft,
            muteRight = globalProperties.muteSpeakerRight,
//            speaker1Offset = adcThresholds.getOrElse(0) { globalProperties.adcThresholdCh1 },
//            speaker2Offset = adcThresholds.getOrElse(1) { globalProperties.adcThresholdCh2 },
//            speaker3Offset = adcThresholds.getOrElse(2) { globalProperties.adcThresholdCh3 },
//            speaker4Offset = adcThresholds.getOrElse(3) { globalProperties.adcThresholdCh4 })
            speaker1Offset = adcThresholds.getOrElse(3) { globalProperties.adcThresholdCh4 },
            speaker2Offset = adcThresholds.getOrElse(2) { globalProperties.adcThresholdCh3 },
            speaker3Offset = adcThresholds.getOrElse(1) { globalProperties.adcThresholdCh2 },
            speaker4Offset = adcThresholds.getOrElse(0) { globalProperties.adcThresholdCh1 })
        LocalWebConsole.updateClassD(
            classD
        )

        //About page
        LocalWebConsole.updateMcuInfoData(hubManager.currentMcuInfo)

        LocalWebConsole.start(
            context = applicationContext,
            globalProperties = globalProperties,
            onConnectionUpdate = { connData ->
                Log.d("webconsole", "Đã nhận cấu hình mới: $connData")

                //Mqtt setting
                val mqttServer = connData.mqttServer
                val mqttPort = connData.mqttPort
                val mqttSecurity = connData.mqttSecurity
                val serverData = ServerData(
                    mqttServer = mqttServer,
                    mqttPort = mqttPort?.toInt(),
                    mqttSecurity = mqttSecurity?.toInt()
                )
                globalProperties.saveServerConfig(serverData)
                //Network settings
                val settingConfig = McuSettingsData(
                    txtype = connData.networkType?.toInt(),
                    sim = connData.simSelection?.toInt(),
                    mobileMode = connData.mobileDataMode?.toInt(),
                    disabled = connData.activation?.toInt(),
                    mainVolume = connData.normalVolume?.toInt(),
                    fmVolume = connData.fmVolume?.toInt(),
                    fmAuto = connData.fmAutoStop?.toInt(),
                    fmThreshold = connData.fmAutoThreshold?.toInt()
                )
                globalProperties.saveSettingConfig(settingConfig)
                globalProperties.saveNetworkIpConfig(
                    connData.localIp, connData.subnet, connData.gateway
                )
                globalProperties.saveWifiConfig(
                    connData.wifiSsid ?: "", connData.wifiPassword ?: ""
                )

                val networkType = connData.networkType ?: globalProperties.settingsTxType.toString()

                if (networkType == "2") {
                    // WiFi mode: connect đến WiFi đã cấu hình
                    val ssid = globalProperties.wifiSsid
                    val pass = globalProperties.wifiPassword
                    if (ssid.isNotBlank()) {
                        Log.d("webconsole", "Connecting to WiFi: $ssid")
                        lifecycleScope.launch(Dispatchers.IO) {
                            connectWifi(ssid, pass)
                        }
                    } else {
                        Log.w("webconsole", "WiFi SSID trống, bỏ qua connect")
                    }
                } else {
                    // Ethernet / Mobile: apply static IP rồi reboot
                    lifecycleScope.launch(Dispatchers.IO) {
                        val gateway = if (globalProperties.settingsTxType == 0) globalProperties.gateway else ""
                        NetworkShellUtils.setUniversalStaticIp(
                            globalProperties.localIp,
                            globalProperties.subnet,
                            gateway,
                            "8.8.8.8",
                            "1.1.1.1"
                        )
                    }
                }

                lifecycleScope.launch {
                    delay(5000)
                    Log.e("webconsole", "Rebooting....")
                    serialPortController.hardRestart()
                }
            },
            onSettingsUpdate = { connData ->
                Log.d("webconsole", "Đã nhận cấu hình Settings mới: $connData")

                val settingConfig = McuSettingsData(
                    txtype = connData.networkType?.toInt(),
                    sim = connData.simSelection?.toInt(),
                    mobileMode = connData.mobileDataMode?.toInt(),
                    disabled = connData.activation?.toInt(),
                    mainVolume = connData.normalVolume?.toInt(),
                    fmVolume = connData.fmVolume?.toInt(),
                    fmAuto = connData.fmAutoStop?.toInt(),
                    fmThreshold = connData.fmAutoThreshold?.toInt()
                )
                globalProperties.saveSettingConfig(settingConfig)
                globalProperties.saveWifiConfig(
                    connData.wifiSsid ?: "", connData.wifiPassword ?: ""
                )

                // apply volume
                val mainVolume = connData.normalVolume?.toIntOrNull()
                if (mainVolume != null) {
                    globalProperties.saveMainVolume(mainVolume.toString())
                    changeVolume(mainVolume)
                }

                // apply fm volume
                val fmVolume = connData.fmVolume?.toIntOrNull()
                if (fmVolume != null) {
                    serialPortController.writeVolumeTuner(fmVolume.toString())
                }

                val networkType = globalProperties.settingsTxType.toString()
                if (networkType == "2") {
                    // Nếu đang dùng WiFi và đổi Settings (có thể là SSID/Pass) thì kết nối lại
                    val ssid = globalProperties.wifiSsid
                    val pass = globalProperties.wifiPassword
                    if (ssid.isNotBlank()) {
                        Log.d("webconsole", "Connecting to WiFi from Settings: $ssid")
                        lifecycleScope.launch(Dispatchers.IO) {
                            connectWifi(ssid, pass)
                        }
                    }
                }
            },
            onMediaAction = { action ->
                when (action.action) {
                    "play" -> {
                        val mid = action.mid ?: return@start
                        val mediaInfo = viewModel.getScheduleList().firstOrNull { it.mid == mid }
                            ?: return@start
                        Log.d("webconsole", "Play $mid")
                        lifecycleScope.launch(Dispatchers.Main) {
                            playerManager.playMediaInfoTest(mediaInfo)
                        }
                    }

                    "stop" -> {
                        val mid = action.mid ?: return@start
                        lifecycleScope.launch(Dispatchers.Main) {
                            val mediaInfo =
                                viewModel.getScheduleList().firstOrNull { it.mid == mid }
                            if (playerManager.playMode == PlayersManager.PLAY_MODE_TEST && playerManager.currentPlayingMediaInfo?.mid == mid) {
                                playerManager.stopMediaInfoTest()
                            } else {
                                playerManager.stopMediaInfo(mediaInfo)
                            }
                        }
                        Log.d("webconsole", "Stop $mid")
                    }

                    "delete" -> {
                        val mid = action.mid ?: return@start
                        val mediaInfo = viewModel.getScheduleList().firstOrNull { it.mid == mid }
                        if (mediaInfo != null) {
                            viewModel.deleteLocalScheduler(mediaInfo)
                            viewModel.deleteMediaInfInRepo(mediaInfo)
                            if (playerManager.playMode == PlayersManager.PLAY_MODE_TEST && playerManager.currentPlayingMediaInfo?.mid == mediaInfo.mid) {
                                playerManager.stopMediaInfoTest()
                            } else {
                                playerManager.stopMediaInfo(mediaInfo)
                            }
                        }
                        Log.d("webconsole", "Delete $mid")
                    }

                    "delete_all" -> {
                        appStorageManager.deleteAllMedia()
                        viewModel.deleteAllLocalScheduler()
                        lifecycleScope.launch(Dispatchers.Main) {
                            if (playerManager.playMode == PlayersManager.PLAY_MODE_TEST) {
                                playerManager.stopMediaInfoTest()
                            }
                            playerManager.stopAllPlayer("Delete all")
                            playerManager.resetPlayMode()
                        }
                        Log.d("webconsole", "Delete all")
                    }

                    "next" -> {
                        lifecycleScope.launch(Dispatchers.Main) {
                            playerManager.playNextManually()
                        }
                    }
                }
            },
            onGetStatusData = {
                val appUptime = System.currentTimeMillis() - globalProperties.getAppStartTime()
                val systemUptime = SystemClock.elapsedRealtime()
                val storageTotalMiB = DeviceUtils.getSdTotal() / 1024.0
                val storageFreeMiB = DeviceUtils.getSdFree() / 1024.0

                val currentConn = when (globalProperties.settingsTxType) {
                    0 -> "ETH"
                    1 -> "MOBILE"
                    2 -> "WIFI"
                    else -> "UNKNOWN"
                }

                val rawDeviceInfo = deviceInfo
                val deviceStatus = parseDeviceStatusResponse(rawDeviceInfo)

                val runningList = (deviceStatus?.audioStatus ?: emptyList()).take(4).map { it == 1 }
                    .let { if (it.size < 4) it + List(4 - it.size) { false } else it }
                val errorList = (deviceStatus?.errorStatus ?: emptyList()).take(4).map { it == 0 }
                    .let { if (it.size < 4) it + List(4 - it.size) { false } else it }

                val isFmPlaying = hubManager.currentMediaItem?.type == 2

                StatusPageData(
                    appUptime = appUptime,
                    systemUptime = systemUptime,
                    storageTotalMiB = storageTotalMiB,
                    storageFreeMiB = storageFreeMiB,
                    connectionType = currentConn,
                    disconnectionCnt = hubManager.disconnectionCnt,
                    isServerConnected = hubManager.isConnectSuccess,
                    isFmPlaying = isFmPlaying,
                    fmRssi = if (isFmPlaying) globalProperties.fmThreshold.toInt() else 0,
                    isStreamBack = streamBackManager.isStreamBacking(),
                    spk1Run = runningList.getOrElse(0) { false },
                    spk1Err = errorList.getOrElse(0) { false },
                    spk2Run = runningList.getOrElse(1) { false },
                    spk2Err = errorList.getOrElse(1) { false },
                    spk3Run = runningList.getOrElse(2) { false },
                    spk3Err = errorList.getOrElse(2) { false },
                    spk4Run = runningList.getOrElse(3) { false },
                    spk4Err = errorList.getOrElse(3) { false },
                    serverMacAddress = globalProperties.mqttServer
                )
            },
            onResetConnection = {
                lifecycleScope.launch(Dispatchers.IO) {
                    val gateway = if (globalProperties.settingsTxType == 0) globalProperties.gateway else ""
                    NetworkShellUtils.setUniversalStaticIp(
                        globalProperties.localIp,
                        globalProperties.subnet,
                        gateway,
                        "8.8.8.8",
                        "1.1.1.1"
                    )
//                    EthernetStaticIpUtils.forceStaticIp(
//                        globalProperties.localIp, globalProperties.gateway, "8.8.8.8", "1.1.1.1"
//                    )
                }
                lifecycleScope.launch {
                    delay(5000)
                    Log.e("webconsole", "Rebooting after reset connection....")
                    serialPortController.hardRestart()
                }
            },
            onResetSettings = {
                lifecycleScope.launch {
                    delay(5000)
                    Log.e("webconsole", "Rebooting after reset settings....")
                    serialPortController.hardRestart()
                }
            },
            onRebootMira = {
                lifecycleScope.launch(Dispatchers.IO) {
                    delay(5000)
                    Log.e("webconsole", "Rebooting mira....")
                    serialPortController.hardRestart()
                }
            },
            onRestartApp = {
                lifecycleScope.launch(Dispatchers.Main) {
                    delay(5000)
                    Log.e("webconsole", "Restarting app....")
                    restartApp(MyApp.instance!!, this@MainActivity)
                }
            },
            onFactoryReset = {
                lifecycleScope.launch(Dispatchers.IO) {
                    globalProperties.clearAllLocalConfigData()
                    delay(5000)
                    Log.e("webconsole", "Factory reset and Rebooting....")
                    serialPortController.hardRestart()
                }
            },
            onGetPlayerStatus = {
                val isFmPlaying = hubManager.currentMediaItem?.type == 2
                val fmRssi = if (isFmPlaying) globalProperties.fmThreshold.toIntOrNull() ?: 0 else 0
                playerManager.getPlayerStatusData(fmRssi = fmRssi)
            },
            onClassDUpdate = { classD ->
                Log.d("webconsole", "onClassDUpdate")
                val errors = mutableListOf<String>()
                globalProperties.apply {
                    saveClassDThreshold(classD.threshold)
                    saveClassDSzdLeft(classD.szdLeft)
                    saveClassDSzdRight(classD.szdRight)

                    if (!updateSpeakerMute(
                            classD.monitorChannel,
                            classD.muteLeft,
                            classD.muteRight
                        )
                    ) {
                        errors.add("Gửi lệnh mute (left, right) thất bại")
                    }
                    saveAdcThresholds(
                        classD.speaker1Offset,
                        classD.speaker2Offset,
                        classD.speaker3Offset,
                        classD.speaker4Offset
                    )
                    if (!updateADC(
                            classD.speaker1Offset,
                            classD.speaker2Offset,
                            classD.speaker3Offset,
                            classD.speaker4Offset
                        )
                    ) {
                        errors.add("Gửi lệnh cập nhật ADC thất bại")
                    }
                }
                errors
            })
    }

    private fun handleFromCommandServer(command: String?, message: String?) {
        try {

            val responseHub = hubManager.responseHub
            Log.d(
                "MediaInfo", "handleFromCommandServer $command"
            )
            when (command) {

                HubCommand.SAVE_SCHEDULER -> {
                    viewModel.saveSchedulerToLocal(responseHub.listMediaInfo)
                }

                HubCommand.DELETE_SCHEDULER -> {
                    val mediaInfo = responseHub.listMediaInfo?.firstOrNull()
                    Log.d(
                        "MediaInfo", "Xóa và dừng phát bản tin: ${mediaInfo?.mid}"
                    )
                    viewModel.addLog(
                        type = 4, detail = """{"m":${mediaInfo?.mid}}""", hubManager = hubManager
                    )
                    viewModel.deleteLocalScheduler(mediaInfo)
                    viewModel.deleteMediaInfInRepo(mediaInfo)
                    playerManager.stopMediaInfo(mediaInfo)
                    viewModel.updateWebsocketListLocal()
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
                                val now = Calendar.getInstance()
                                var playableMedia: MediaInfo? = null

                                listNew.forEach { media ->
                                    val expiredTime = unixSecondsToCalendar(media.expired ?: 0L)
                                    if (!now.before(expiredTime)) {
                                        // Bản tin đã hết hạn -> đẩy thẳng vào history tránh phát nhầm
                                        Log.d(
                                            "MediaInfo",
                                            "Sync Live quá hạn -> đẩy vào history: ${media.mid}"
                                        )
                                        viewModel.insertMediaInfoLiveHistory(media)
                                    } else if (playableMedia == null) {
                                        // Lấy bản tin Live hợp lệ đầu tiên
                                        playableMedia = media
                                    } else {
                                        // Có nhiều Live hợp lệ -> giữ nguyên pending (vẫn "new"), 
                                        // lần sync hoặc getPlay sau sẽ phát khi Live đầu hết hạn.
                                        Log.d(
                                            "MediaInfo",
                                            "Pending Live dư thừa chưa hết hạn: ${media.mid}"
                                        )
                                    }
                                }

                                val finalPlayable = playableMedia
                                if (finalPlayable != null) {
                                    Log.d(
                                        "MediaInfo",
                                        "handleFromCommandServer: Play Live ${finalPlayable.summary}"
                                    )
                                    onPlayForce(finalPlayable)
                                } else {
                                    Log.d(
                                        "MediaInfo",
                                        "handleFromCommandServer: Tất cả Live mới đều đã hết hạn."
                                    )
                                }
                            } else {
                                Log.d(
                                    "MediaInfo", "handleFromCommandServer: Live đã phát rồi"
                                )
                            }
                        }
                    }
                }

                HubCommand.UPDATE_SETTING_CONFIG -> {
                    if (responseHub.settingConfig != null) {
                        val data = responseHub.settingConfig
                        if (data?.mainVolume != null) {
                            globalProperties.saveMainVolume(data.mainVolume.toString())
                            changeVolume(data.mainVolume)
                        }
                        if (data?.fmVolume != null) {
                            serialPortController.writeVolumeTuner(data.fmVolume.toString())
                            globalProperties.saveFMVolume(data.fmVolume.toString())
                        }
                        if (data?.txtype != null) {
                            when (data.txtype) {
                                //ETH
                                0 -> {
                                    lifecycleScope.launch {
//                                        EthernetStaticIpUtils.forceStaticIp(
//                                            globalProperties.localIp,
//                                            globalProperties.gateway,
//                                            "8.8.8.8",
//                                            "1.1.1.1"
//                                        )
                                        val gateway = if (globalProperties.settingsTxType == 0) globalProperties.gateway else ""
                                        NetworkShellUtils.setUniversalStaticIp(
                                            globalProperties.localIp,
                                            globalProperties.subnet,
                                            gateway,
                                            "8.8.8.8",
                                            "1.1.1.1"
                                        )
                                        setEthernetEnabled(true)
                                        setWifiDataEnabled(false)
                                        setMobileDataEnabled(false)
                                        globalProperties.saveSettingConfig(responseHub.settingConfig)
                                    }
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
                                        } else {
                                            Log.d(
                                                "SettingInfo", "handleFromCommandServer: Sim init"
                                            )
                                            //ESimDisable
                                            serialPortController.writeSetESimMode("0")
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
                                        globalProperties.saveSettingConfig(responseHub.settingConfig)
                                        Log.d(
                                            "initSettings",
                                            "mobile: ${responseHub.settingConfig?.txtype}"
                                        )
                                        delay(3000)
                                        serialPortController.hardRestart()
                                    }
                                }
                                //Wifi
                                2 -> {
                                    lifecycleScope.launch {
                                        setWifiDataEnabled(true)
                                        setMobileDataEnabled(false)
//                                        setEthernetEnabled(false)
                                        globalProperties.saveSettingConfig(responseHub.settingConfig)
                                        delay(3000)
                                        serialPortController.hardRestart()
                                    }
                                }
                            }
                            // Sync networkType mới vào webconsole để trang /config-settings hiển thị đúng
                            LocalWebConsole.updateConnectionSettings(
                                ConnectionSettings(
                                    localIp = globalProperties.localIp,
                                    subnet = globalProperties.subnet,
                                    gateway = globalProperties.gateway,
                                    mqttSecurity = globalProperties.mqttSecurity.toString(),
                                    mqttServer = globalProperties.mqttServer,
                                    mqttPort = globalProperties.mqttPort.toString(),
                                    networkType = data.txtype.toString(),
                                    simSelection = (data.sim
                                        ?: globalProperties.settingsSimType).toString(),
                                    mobileDataMode = (data.mobileMode
                                        ?: globalProperties.settingsSimMode).toString(),
                                    activation = globalProperties.settingsDisable.toString(),
                                    normalVolume = globalProperties.mainVolume,
                                    fmVolume = globalProperties.fmVolume,
                                    fmAutoStop = globalProperties.fmAutoStop.toString(),
                                    fmAutoThreshold = globalProperties.fmThreshold,
                                    wifiSsid = globalProperties.wifiSsid,
                                    wifiPassword = globalProperties.wifiPassword
                                )
                            )
                        }

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

                        Log.d("handleFromCommandServer", "RESTART raw=$message trim=$msg")

                        when (msg) {
                            "0" -> {
                                handleRestartWithGuard("RESTART device") {
                                    serialPortController.hardRestart()
                                }
                            }

                            "1" -> {
                                handleRestartWithGuard("RESTART app") {
                                    restartApp(MyApp.instance!!, this@MainActivity)
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
                        streamBackManager.handleStreamBack(streamBackData) {
                            serialPortController.streamBack("0")
                        }
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
                        val url = updateFirmData?.url.orEmpty()

                        Log.d("update", url)
                        Log.d("update", updateFirmData?.option.toString())

                        if (url.contains("tek4tv", ignoreCase = true)) {
                            viewModel.addLog(
                                type = 23,
                                detail = """{"u":${SystemClock.elapsedRealtime() / 1000}}""",
                                hubManager = hubManager
                            )

                            if (updateFirmData?.option == 0) {
                                downloadUpdateApk(
                                    url, this, serialPortController
                                )
                            } else if (updateFirmData?.option == 1) {
                                globalProperties.clearAllLocalConfigData()
                                downloadUpdateApk(
                                    url, this, serialPortController
                                )
                            }
                        } else {
                            Log.d("update", "Url không đúng")
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

    fun initSerialConfig() {
        serialPortController.writeADC("${globalProperties.adcThresholdCh1}-${globalProperties.adcThresholdCh2}-${globalProperties.adcThresholdCh3}-${globalProperties.adcThresholdCh4}")
        serialPortController.writeForceSetVolume(globalProperties.serialVolume)
    }

    private suspend fun handleRestartWithGuard(
        actionName: String, action: suspend () -> Unit
    ) {
        val now = System.currentTimeMillis()

        if (!restartGuardManager.canHandleRestartApp(now)) {
            val appStartTime = globalProperties.getAppStartTime()
            val blockedUntil = globalProperties.getRestartBlockedUntil()

            when {
                now - appStartTime < 15_000L -> {
                    Log.d(
                        "handleFromCommandServer",
                        "Bỏ qua $actionName: app mới khởi động ${now - appStartTime} ms"
                    )
                }

                now < blockedUntil -> {
                    Log.d(
                        "handleFromCommandServer",
                        "Bỏ qua $actionName: đang block đến $blockedUntil"
                    )
                }

                else -> {
                    Log.d(
                        "handleFromCommandServer",
                        "Bỏ qua $actionName: đang xử lý lệnh restart khác"
                    )
                }
            }
            return
        }

        restartGuardManager.markHandlingRestart(true)

        try {
            restartGuardManager.resetWindowIfExpired(now)

            val allowed = restartGuardManager.registerRestartAndCheckAllowed(now)

            if (!allowed) {
                Log.d(
                    "handleFromCommandServer",
                    "$actionName bị block 5 phút do vượt ngưỡng trong 60 giây"
                )
                return
            }

            val currentCount = restartGuardManager.getCurrentRestartCount()

            Log.d(
                "handleFromCommandServer",
                "Cho phép $actionName lần $currentCount trong cửa sổ 60 giây"
            )

            delay(1000)
            action()
        } catch (e: Exception) {
            Log.e("handleFromCommandServer", "Lỗi xử lý $actionName", e)
        } finally {
            delay(2000)
            restartGuardManager.markHandlingRestart(false)
        }
    }

    private fun onPlayForce(mediaInfo: MediaInfo) {
        //Tránh trường hợp âm lượng bị tắt
        //serialPortController.writeForceSetVolume(globalProperties.mainVolume)
        playerManager.onPlayForceCommand(applicationContext, mediaInfo, mediaScheduler)
    }

    private fun cancelAllAlarm() {
        mediaScheduler.cancelAllAlarm()
    }


    suspend fun updateADC(offset1: Int, offset2: Int, offset3: Int, offset4: Int): Boolean {
        val oldCh1 = globalProperties.adcThresholdCh1
        val oldCh2 = globalProperties.adcThresholdCh2
        val oldCh3 = globalProperties.adcThresholdCh3
        val oldCh4 = globalProperties.adcThresholdCh4

        if (oldCh1 == offset1 && oldCh2 == offset2 && oldCh3 == offset3 && oldCh4 == offset4) {
            return true
        }

        val data = "${offset1}-${offset2}-${offset3}-${offset4}"
        val success = serialPortController.writeADCAwait(data)
        if (success) {
            GlobalProperties.getInst().saveAdcThresholds(
                ch1 = offset1, ch2 = offset2, ch3 = offset3, ch4 = offset4
            )
        }
        return success
    }

    suspend fun updateSpeakerMute(
        monitorChannel: String,
        muteLeftInput: Int,
        muteRightInput: Int
    ): Boolean {
        // Đọc giá trị phần cứng hiện tại đã lưu
        val oldLeftMute = globalProperties.muteSpeakerLeft == 0
        val oldRightMute = globalProperties.muteSpeakerRight == 0

        var newLeftMute = oldLeftMute
        var newRightMute = oldRightMute

        if (muteLeftInput == 0 && muteRightInput == 0) {
            // Mute kênh được chọn
            if (monitorChannel == "CH 1-2") {
                newLeftMute = true
            } else if (monitorChannel == "CH 2-4") {
                newRightMute = true
            }
        } else if (muteLeftInput == 1 && muteRightInput == 1) {
            // Unmute kênh được chọn
            if (monitorChannel == "CH 1-2") {
                newLeftMute = false
            } else if (monitorChannel == "CH 2-4") {
                newRightMute = false
            }
        }

        if (newLeftMute == oldLeftMute && newRightMute == oldRightMute) {
            return true // Không thay đổi gì, báo thành công luôn
        }

        val success = serialPortController.writeMuteSpeakerAwait(
            leftMute = newLeftMute, rightMute = newRightMute
        )

        if (success) {
            // Lưu lại trạng thái phần cứng mới nhất nếu lệnh gửi thành công
            globalProperties.saveMuteSpeaker(if (newLeftMute) 0 else 1, if (newRightMute) 0 else 1)
        }

        return success
    }

    /**
     * Đặt âm lượng Android tương đối theo thang 0–100 từ server.
     * Quy đổi: androidVol = round(serverValue / 100.0 * maxStreamVolume)
     * Ví dụ: server gửi 95, maxVol=15 → round(95/100*15) = round(14.25) = 14
     */
    private fun changeVolume(serverVolume: Int) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val clamped = serverVolume.coerceIn(0, 100)
        val androidVol = (clamped / 100.0 * maxVol).toInt().coerceIn(0, maxVol)
        Log.d("Volume", "changeVolume: server=$serverVolume → android=$androidVol (max=$maxVol)")
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, androidVol, 0)
    }

    private fun setMaxVolume() {
        try {
            changeVolume(globalProperties.mainVolume.toInt())
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

    override fun onHubConnected() {
        viewModel.addLog(
            type = 9,
            detail = """{"u":${SystemClock.elapsedRealtime() / 1000}}""",
            hubManager = hubManager
        )
    }

    override fun onHubDisConnected() {
        viewModel.addLog(
            type = 10,
            detail = """{"u":${SystemClock.elapsedRealtime() / 1000}}""",
            hubManager = hubManager
        )
    }

    companion object {
        var alarmListener: AlarmListener? = null

    }

}