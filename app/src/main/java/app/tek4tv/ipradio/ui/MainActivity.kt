package app.tek4tv.ipradio.ui


import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.HubComm
import app.tek4tv.ipradio.core.HubManager
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.core.SignalRHubManager
import app.tek4tv.ipradio.core.players_helper.IPlayerManagerListener
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.PLAY_MODE_FORCE
import app.tek4tv.ipradio.databinding.ActivityMainBinding
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.CrashHandler.Companion.restartApp
import app.tek4tv.ipradio.utils.StringValue.Companion.PAStatusOff
import app.tek4tv.ipradio.utils.StringValue.Companion.PAStatusOn
import app.tek4tv.ipradio.utils.StringValue.Companion.UART_S1_NAME
import app.tek4tv.ipradio.utils.StringValue.Companion.UART_S4_NAME
import app.tek4tv.ipradio.utils.media.MediaCapture
import app.tek4tv.ipradio.utils.logger.LogFileUtils
import app.tek4tv.ipradio.utils.logger.LogManager
import app.tek4tv.ipradio.utils.network.CaCertHelper
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.storage.*
import app.tek4tv.ipradio.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds


@AndroidEntryPoint
@RequiresApi(Build.VERSION_CODES.N)
class MainActivity : AppCompatActivity(), AlarmListener {

    private val UPDATE_PERMISSION_REQUEST_CODE = 1

    private val permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.REQUEST_INSTALL_PACKAGES,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.RECORD_AUDIO,
    )

    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var authService: AuthService

    private lateinit var hubManager: HubManager

    private lateinit var signalRHubManager: SignalRHubManager

    private lateinit var playerManager: PlayersManager

    private lateinit var serialPortController: SerialPortController

    private lateinit var appStorageManager: AppStorageManager

    private lateinit var mediaCapture: MediaCapture

    private lateinit var mediaScheduler: MediaScheduler

    private lateinit var logManager: LogManager


    private val globalProperties = GlobalProperties.getInst()


    private lateinit var binding: ActivityMainBinding


    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            Log.d("loghub", "onTimeOver: come")
            serialPortController.turnOffPA()
            serialPortController.writeForceSetVolume(globalProperties.volume)
            hubManager.currentMediaItem = null
            signalRHubManager.currentMediaItem = null
            signalRHubManager.curMediaName = ""
        }

        //Phát FM
        override fun onPlayTuner(mediaItem: MediaItem) {
            try {
                val tunerType = if (mediaItem.getMediaType() == MediaType.AM) "0" else "1"
                val pipsData = "${globalProperties.piCode},${globalProperties.psName}"
                val tunerData = "$tunerType,${mediaItem.body},${globalProperties.volume}"
                val paState =
                    if (serialPortController.playMode != SerialPortController.MODE_NONE) PAStatusOff
                    else PAStatusOn
                hubManager.currentMediaItem = mediaItem
                signalRHubManager.currentMediaItem = mediaItem
                signalRHubManager.playlistIndex = playerManager.playlistIndex
                signalRHubManager.curMediaName = mediaItem.title ?: ""

                if (!serialPortController.isUart1) {
                    lifecycleScope.launch {
                        delay(11000.milliseconds)
                        serialPortController.playTuner(pipsData, tunerData)
                    }
                } else {
                    serialPortController.playTuner(pipsData, tunerData)
                }

                hubManager.sendLogMediaSuccess(mediaItem, paState)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        //Dừng phát khẩn cấp
        override fun onEndForceMedia() {
            serialPortController.turnOffPA()
            ForceMedia.deleteForeMediaPref(applicationContext)
            startPlayingMedia()
        }

        //Lỗi luồng live
        override fun onLiveError(mediaItem: MediaItem) {
            hubManager.currentMediaItem = mediaItem
            signalRHubManager.currentMediaItem = mediaItem
            serialPortController.turnOffPA()
            Log.d("turnPA", "live error")
        }

        override fun onMediaStopPlaying(mediaItem: MediaItem, duration: Long) {
            hubManager.currentMediaItem = null
            signalRHubManager.currentMediaItem = null
            signalRHubManager.curMediaName = ""
        }


        override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
            if (mediaItem != null) {

                hubManager.currentMediaItem = mediaItem
                signalRHubManager.currentMediaItem = mediaItem
                signalRHubManager.playlistIndex = playerManager.playlistIndex
                signalRHubManager.curMediaName = mediaItem.title ?: ""

                if (mediaItem.getMediaType() != MediaType.FM) {
                    serialPortController.turnOnPAVod()
                }
                val paState =
                    if (serialPortController.playMode != SerialPortController.MODE_NONE) PAStatusOff
                    else PAStatusOn

                hubManager.sendLogMediaSuccess(mediaItem, paState)

                viewModel.currentMedia = mediaItem


            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        if (needGrantPermission()) requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)

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
                    delay(3000.milliseconds)
                    //bật tắt lại mạng di động
                    setMobileDataEnabled(false)
                    setWifeDataEnabled(true)
                    delay(3000.milliseconds)
                    setMobileDataEnabled(true)
                    //lấy lại imei trong trường hợp lỗi khi lấy imei
                    ConfigUtil.removeSharef(baseContext, Utils.DEVICE_ID)
                    delay(3000.milliseconds)
                    restartApp(MyApp.instance!!, this@MainActivity)
                }
            }

            override fun forceRestartDevice() {
                serialPortController.hardRestart()
            }

        }

        appStorageManager = AppStorageManager(applicationContext)
        mediaCapture = MediaCapture(applicationContext, lifecycleScope)
        LogFileUtils.initFolders()

        hubManager = HubManager(
            authService, baseContext, CoroutineScope(Dispatchers.Main), hubComm,
        ) { command, message ->
            handleFromCommandServer(command, message, hubManager.responseHub)
        }
        logManager = LogManager(applicationContext) {
            "${hubManager.dichId},${hubManager.tenDich}"
        }
        signalRHubManager = SignalRHubManager(
            baseContext, authService
        ) { command, message, responseHub ->
            handleFromSignalRMonitor(command, message, responseHub)
        }.apply {
            versionName = hubManager.versionName
            destinationProvider = { Pair(hubManager.dichId, hubManager.tenDich) }
        }
        serialPortController = SerialPortController(applicationContext, lifecycleScope, hubManager)

        serialPortController.connectToSerialPort(UART_S1_NAME)
        lifecycleScope.launch {
            delay(10000.milliseconds)
            if (!serialPortController.isUart1) {
                serialPortController.connectToSerialPort(UART_S4_NAME)
                Log.i("PA", "onCreate: s4")
            }
        }

        playerManager = PlayersManager(
            applicationContext, viewModel.mediaRepo
        ) //PlayerManager(applicationContext, viewModel, rootLayout)
        playerManager.playerManagerListener = playerCallback
        serialPortController.isPlayingProvider = { playerManager.isPlaying() }

        NetworkUtils.instance.startNetworkListener(this)
        NetworkUtils.instance.addCallback(object : NetworkUtils.ConnectionCallback {
            override fun onChange(netWorkState: Boolean) {
                if (netWorkState) {
                    Log.i("MainActivity", "Network connected -> reconnect SignalR Hub & sync")
                    lifecycleScope.launch(Dispatchers.Main) {
                        signalRHubManager.connectToHub()
                        if (!hubManager.isConnectSuccess) {
                            hubManager.connectToServer()
                        }
                    }
                }
            }
        })

        initHubConnect()
        registerObservers()

        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        alarmListener = this

        setMaxVolume()

        hideSystemUI()

        showMac()

        checkDeleteFileUpdate()
        viewModel.startPeriodicScheduleCheckLoop()
        startPeriodicLogUploadLoop()

    }

    private fun startPeriodicLogUploadLoop() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                if (NetworkUtils.isNetworkConnected(applicationContext)) {
                    logManager.uploadAllPendingLogs()
                }
                delay(60 * 60 * 1000L)
            }
        }
    }


    private fun showMac() {
        val tvMacAddress = findViewById<TextView>(R.id.tvMac)
        val imei = Utils.getDeviceId(this)
        tvMacAddress.text = "$imei - Ver: ${hubManager.versionName}"
    }


    override fun onStop() {
        super.onStop()
        serialPortController.turnOffPA()
        playerManager.onActivityStop()
//        serialPortController.disConnect()
        cancelAllAlarm()

    }

    override fun onDestroy() {
        super.onDestroy()
        serialPortController.turnOffPA()
        playerManager.onActivityDestroy()
        signalRHubManager.disconnect()
        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()

    }

    override fun onStart() {
        super.onStart()
        startPlayingMedia()
    }

    override fun onAlarm(intent: Intent) {
        when (intent.action) {
            MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", listKey!!)
                playerManager.setTimeFrame(listKey, null)
            }

            MediaScheduleReceiver.ACTION_END_PLAYLIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", "end: $listKey")
                playerManager.stopPlaylist(listKey!!)
            }

            MediaScheduleReceiver.ACTION_END_FORCE_MEDIA -> {
                playerManager.stopForceMedia()
                Log.d("alarmx", "stop force")
                Log.d("turnPA", "on alarm end force media")
            }


            MediaScheduleReceiver.ACTION_CHECKHUB_RESET -> {
                lifecycleScope.launch {
                    mediaScheduler.cancelAllScheduledListAlarm()
                    viewModel.rescheduleMonthlyPlaylist()
                    logManager.uploadAllPendingLogs()
                    hubManager.reconnectToServer()
                    signalRHubManager.connectToHub()
                    serialPortController.turnOffPA()
                    delay(120000.milliseconds)
                    Log.e("alarmx", "Hub restart")
                    serialPortController.hardRestart()
                }
            }


        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        return true
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

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

    private fun needGrantPermission(): Boolean {
        var needGrantPermission = false

        permissions.forEach {
            if (checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED) {
                needGrantPermission = true
                return@forEach
            }
        }

        if (!NetworkUtils.checkForNetworkStatPermission(applicationContext)) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
        }

        return needGrantPermission
    }

    private fun registerObservers() {
        /**
         * khi cần
         * **/
        viewModel.content.observe(this) {
            Log.d("checkcheck", "registerObservers content")
            cancelAllAlarm()
            viewModel.downloadMedias()
            //Check xem có đang phát khẩn cấp không
            //Nếu có thì phát media khẩn cấp
            //Media khẩn cấp lưu share pref
            if (ForceMedia.hasValidForceMedia(applicationContext)) {
                mediaScheduler.scheduleForceMediaAlarm()
                val forceMedia = ForceMedia.getLocalForceMedia(applicationContext)
                if (forceMedia != null) playerManager.playForceMedia(
                    applicationContext,
                    forceMedia,
                    mediaScheduler
                )
                return@observe
            }
            //default
            mediaScheduler.scheduleAllAlarms()

            playerManager.startFirstTimeFrame()
            //all load done, check
            viewModel.checkPlaylist()


        }
    }

    fun startPlayingMedia() {
        viewModel.getPlayerContentWss()
    }

    private fun initHubConnect() {
        hubManager.connectToServer()
        signalRHubManager.connectToHub()
    }

    private fun handleFromCommandServer(
        command: String?,
        message: String?,
        responseHub: ResponseHub = hubManager.responseHub
    ) {
        try {
            if (command == null || command.isEmpty()) {
                return
            }
            if (message == null || message.isEmpty()) {
                return
            }


            when (command) {
                HubCommand.UPDATE_MONTHLY_WSS -> {
                    clearDatastore()
                    playerManager.resetPlayMode()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    ForceMedia.clearMonthlyData(applicationContext)
                    Log.d("sds", "handleFromCommandServer: $message ")
                    viewModel.updateMonthlyScenarioWss(message)
                    val currentTime = Calendar.getInstance().time.time / 1000
                    ConfigUtil.putString(
                        applicationContext,
                        ConfigUtil.TIME_UPDATE_MONTHLY_SCENARIO,
                        currentTime.toString()
                    )
                    playerManager.stopForceMedia()
                }

                HubCommand.DELETE_MONTHLY_LIST_WSS -> {
                    Log.d("sds", "handleFromCommandServer: $message ")
                    CoroutineScope(Dispatchers.Main).launch {
                        playerManager.stopAllPlayer()
                    }
                    hubManager.currentMediaItem = null
                    signalRHubManager.currentMediaItem = null
                    signalRHubManager.curMediaName = ""

                    playerManager.resetPlayMode()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    ForceMedia.clearMonthlyData(applicationContext)
                }

                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {
                    lifecycleScope.launch(Dispatchers.Main) {
                        onPlayForce(responseHub)
                    }
                }

                HubCommand.UPDATE_DESTINATION -> {
                    val mess = responseHub.message ?: return
                    Log.i("config_config", "updateconfig $mess")
                    val listInfoConfig = mess.split(",")

                    val newResponseHub = ResponseHub().apply {
                        listInfoConfig.getOrNull(0)?.takeIf { it != "null" }?.let {
                            volume = it
                            serialPortController.writeForceSetVolume(it)
                            globalProperties.setTempVolume(it)
                        }
                        listInfoConfig.getOrNull(1)?.takeIf { it != "null" }?.let {
                            lat = it.toFloatOrNull()
                        }
                        listInfoConfig.getOrNull(2)?.takeIf { it != "null" }?.let {
                            lng = it.toFloatOrNull()
                        }
                        listInfoConfig.getOrNull(3)?.takeIf { it != "null" }?.let {
                            destinationId = it
                        }
                        listInfoConfig.getOrNull(4)?.takeIf { it != "null" }?.let {
                            destinationName = it
                        }
                        listInfoConfig.getOrNull(5)?.takeIf { it != "null" }?.let {
                            deviceName = it
                        }
                    }

                    listInfoConfig.getOrNull(6)?.takeIf { it != "null" }?.let {
                        ConfigUtil.putString(applicationContext, ConfigUtil.PROVIDER, it)
                        signalRHubManager.providerValue = it
                    }
                    listInfoConfig.getOrNull(7)?.takeIf { it != "null" }?.toIntOrNull()?.let {
                        ConfigUtil.putInt(applicationContext, ConfigUtil.Minute_TURN_ON_PA, it)
                    }
                    listInfoConfig.getOrNull(8)?.takeIf { it != "null" }?.toIntOrNull()?.let {
                        ConfigUtil.putInt(applicationContext, ConfigUtil.ServerTimeout, it)
                    }
                    listInfoConfig.getOrNull(9)?.takeIf { it != "null" }?.toIntOrNull()?.let {
                        ConfigUtil.putInt(applicationContext, ConfigUtil.KeepAliveInterval, it)
                    }

                    globalProperties.saveProperties(newResponseHub)
                }

                HubCommand.TURN_ON_PA -> {
                    val mess = responseHub.message
                    try {
                        when (val value = mess?.toIntOrNull()) {
                            0 -> serialPortController.turnOnPAVod()
                            1 -> serialPortController.turnOffPA()
                            null -> Log.e("HubCommand", "Message is not a valid number: $mess")
                            else -> Log.w("HubCommand", "Unexpected PA control value: $value")
                        }
                    } catch (e: Exception) {
                        Log.e("HubCommand", "Error parsing PA command: ${e.message}")
                    }
                }

                HubCommand.CONTROL_MEDIA -> {
                    //Vi thread se an theo websocket nen phai quay lai main de dieu khien player
                    //0,1,2,3
                    //{"IMEI":"18:93:7F:E5:F5:77","Message":"0"}
                    val mess = responseHub.message!!.toInt()
                    if (playerManager.playMode != PLAY_MODE_FORCE) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            when (mess) {
                                //dung phat
                                0 -> {
                                    cancelAllAlarm()
                                    playerManager.stopAndEndTimeFrame()

                                    playerManager.resetPlayMode()
                                    lifecycleScope.launch {
                                        viewModel.mediaRepo.clearPlaylist()
                                    }

                                }

                                2 -> {

                                    playerManager.pausePlayer()


                                }
                                //dung phat va chuyen sang ban tin tiep
                                1 -> {
                                    Log.d(
                                        "CONTROL_MEDIA",
                                        "isInTimeFrame: ${playerManager.isTimeOver}"
                                    )
                                    if (!playerManager.isTimeOver) {
                                        playerManager.playNextMedia()
                                    }
                                }

                                //Phát tiếp nội dung dừng trước đó
                                3 -> {
                                    if (!playerManager.isTimeOver) {
                                        playerManager.resume()
                                    }
                                }
                            }
                        }
                    }

                }

                HubCommand.PLAYLIST_INFO -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val path = "${filesDir.path}/$PLAYLIST_FILE_NAME"
                            val dataFromPlaylist: String = readFile(path)
                            val connectionId = responseHub.message
                            if (connectionId != null) {
                                signalRHubManager.sendHubDirectMessage(
                                    connectionId, HubCommand.PLAYLIST_INFO, dataFromPlaylist
                                )
                            }
                        } catch (_: IOException) {
                            Log.e("readplaylist", "error reading playlist!!!")
                        }
                    }
                }

                HubCommand.FM -> {
                    onPlayForce(responseHub)
                }

                HubCommand.UPDATE_STATUS -> {
                    serialPortController.updateStatus()
                }

                HubCommand.RESTART -> {
                    serialPortController.hardRestart()
                }

                HubCommand.SWITCH_MODE_FM -> {
                    onPlayForce(responseHub)
                }

                HubCommand.SET_MUTE_DEVICE -> {
                    if (responseHub.message != null) {
                        serialPortController.setMuteDevice(responseHub.message!!)
                    }
                }

                HubCommand.SET_VOLUME_DEVICE -> {
                    if (responseHub.message != null) {
                        Log.d("UpdateVolume", "handleFromCommandServer: ${responseHub.message}")
                        serialPortController.writeForceSetVolume(responseHub.message!!)
                        globalProperties.saveVolume(responseHub.message!!)
                        globalProperties.setTempVolume(responseHub.message!!)
                    }
                }

                HubCommand.UPDATE_VERSION -> {
                    if (responseHub.message != null) {
                        Log.d("update", responseHub.message!!)
                        downloadUpdateApk(responseHub.message!!, this, serialPortController)
                    }
                }

                HubCommand.SET_TIME_OVER -> {
                    serialPortController.setTimeOver()
                }

                HubCommand.SET_TIME_ON -> {
                    serialPortController.setTimeOn(globalProperties.volume)
                }

                HubCommand.SETUP_PI_PS_NAME -> {
                    serialPortController.setUpPiPsName(responseHub.message ?: "")
                }

                HubCommand.PLAY_VOD_LIVE_FORCE -> {
                    serialPortController.writePlayVodLiveForce(responseHub.message ?: "")
                }

                HubCommand.PLAY_TUNER_LIVE_FORCE -> {
                    serialPortController.playTunerLiveForce(responseHub.message ?: "")
                }

                HubCommand.READ_DEVICE_INFO -> {
                    serialPortController.readDeviceInfo()
                }

                HubCommand.NETWORK_INFO -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        val result = NetworkUtils.getNetworkInfo(applicationContext)
                        signalRHubManager.sendHubDirectMessage(
                            connectionId, Utils.NETWORK_INFO, result
                        )
                    }
                }

                HubCommand.STORAGE_INFO -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        val info = appStorageManager.run {
                            "${getTotalRam()},${getUsedRam()},${getTotalRomStorage()},${getUsedRomStorage()}"
                        }
                        signalRHubManager.sendHubDirectMessage(
                            connectionId, Utils.STORAGE_INFO, info
                        )
                    }
                }

                HubCommand.GET_MEDIA_PATH -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        val mediaList = Utils.toJsonList(
                            MoshiInst.moshi, appStorageManager.getAllMediaPath()
                        )
                        signalRHubManager.sendHubDirectMessage(
                            connectionId, Utils.GET_MEDIA_PATH, mediaList
                        )
                    }
                }

                HubCommand.DELETE_ALL_MEDIA -> {
                    appStorageManager.deleteAllMedia()
                }

                HubCommand.RECORD -> {
                    val duration = responseHub.message
                    val shouldStart = responseHub.start == "1"

                    if (shouldStart) {
                        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
                        mediaCapture.startCaptureAudio()
                        Log.i("kakaka", "record.... ")
                    } else {
                        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
                    }
                    duration?.toLongOrNull()?.let { mins ->
                        Timer().schedule(object : TimerTask() {
                            override fun run() {
                                if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
                                Log.i("kakaka", "stop record.... ")
                            }
                        }, mins * 60 * 1000)
                    }
                }

                HubCommand.GET_RECORD -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        val recordedAudioList = Utils.toJsonList(
                            MoshiInst.moshi, appStorageManager.getAllRecordedAudioPath()
                        )
                        signalRHubManager.sendHubDirectMessage(
                            connectionId, Utils.GET_RECORD, recordedAudioList
                        )
                    }
                }

                HubCommand.UPLOAD_DEVICE_LOGGER -> {
                    logManager.uploadAllPendingLogs()
                }

                HubCommand.DELETE_RECORD -> {
                    val fileDirPath = this.filesDir.path
                    val recordPath = "$fileDirPath${File.separator}$RECORDED_AUDIO_FOLDER_NAME"
                    val itemDelete = responseHub.message
                    if (itemDelete != "" && itemDelete != null) {
                        deleteAFileInFolder(recordPath, itemDelete)
                    } else {

                        appStorageManager.deleteAllRecord()
                    }
                }

                HubCommand.DELETE_ALL_LOG -> {
                    appStorageManager.deleteAllLog()
                }

                HubCommand.CONTROL_WIFI_STATE -> {
                    val wifiEnable = responseHub.message!! == "1"
                    NetworkUtils.setWifiEnable(applicationContext, wifiEnable)
                }

                HubCommand.SET_ENCRYPTED_VOLUME_DEVICE -> {
                    if (responseHub.message != null) {
                        serialPortController.writeForceSetVolume(responseHub.message!!)
                    }
                }

                HubCommand.STOP_FORCE -> {
                    serialPortController.turnOffPA()
                    playerManager.stopAllPlayer()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    startPlayingMedia()
                }

                HubCommand.UPDATE_CA_CERT -> {
                    val hubInput = responseHub.message ?: message
                    if (hubInput.isNotBlank()) {
                        Log.i("HubCommand", "Nhận lệnh nạp CA cert từ Hub: $hubInput")
                        CaCertHelper.handleHubCaInputAsync(
                            applicationContext,
                            hubInput
                        )
                    } else {
                        Log.w("HubCommand", "Lệnh UPDATE_CA nhận message rỗng")
                    }
                }


            }

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Xử lý riêng các lệnh monitor thông tin & log từ SignalR Hub.
     * Tuyệt đối không thực thi các lệnh điều khiển phát, PA, cấu hình hay restart thiết bị.
     */
    private fun handleFromSignalRMonitor(
        command: String?,
        message: String?,
        responseHub: ResponseHub
    ) {
        try {
            if (command.isNullOrEmpty() || message.isNullOrEmpty()) return

            when (command) {
                HubCommand.UPDATE_STATUS -> {
                    serialPortController.updateStatus()
                }

                HubCommand.READ_DEVICE_INFO -> {
                    serialPortController.readDeviceInfo()
                }

                HubCommand.PLAYLIST_INFO -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val path = "${filesDir.path}/$PLAYLIST_FILE_NAME"
                            val dataFromPlaylist: String = readFile(path)
                            val connectionId = responseHub.message
                            if (connectionId != null) {
                                signalRHubManager.sendHubDirectMessage(
                                    connectionId, HubCommand.PLAYLIST_INFO, dataFromPlaylist
                                )
                            }
                        } catch (_: IOException) {
                            Log.e("readplaylist", "error reading playlist!!!")
                        }
                    }
                }

                HubCommand.NETWORK_INFO -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        val result = NetworkUtils.getNetworkInfo(applicationContext)
                        signalRHubManager.sendHubDirectMessage(
                            connectionId, Utils.NETWORK_INFO, result
                        )
                    }
                }

                HubCommand.STORAGE_INFO -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        val info = appStorageManager.run {
                            "${getTotalRam()},${getUsedRam()},${getTotalRomStorage()},${getUsedRomStorage()}"
                        }
                        signalRHubManager.sendHubDirectMessage(
                            connectionId, Utils.STORAGE_INFO, info
                        )
                    }
                }

                HubCommand.GET_MEDIA_PATH -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        val mediaList = Utils.toJsonList(
                            MoshiInst.moshi, appStorageManager.getAllMediaPath()
                        )
                        signalRHubManager.sendHubDirectMessage(
                            connectionId, Utils.GET_MEDIA_PATH, mediaList
                        )
                    }
                }

                HubCommand.GET_RECORD -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        val recordedAudioList = Utils.toJsonList(
                            MoshiInst.moshi, appStorageManager.getAllRecordedAudioPath()
                        )
                        signalRHubManager.sendHubDirectMessage(
                            connectionId, Utils.GET_RECORD, recordedAudioList
                        )
                    }
                }

                HubCommand.UPLOAD_DEVICE_LOGGER -> {
                    logManager.uploadAllPendingLogs()
                }

                else -> {
                    Log.d("SignalRMonitor", "Ignored non-monitor command from SignalR: $command")
                }
            }
        } catch (e: Exception) {
            Log.e("SignalRMonitor", "Error handling SignalR monitor command: ${e.message}", e)
        }
    }

    private fun onPlayForce(responseHub: ResponseHub) {
        //Tránh trường hợp âm lượng bị tắt
        serialPortController.writeForceSetVolume(globalProperties.volume)
        playerManager.onPlayForceCommand(applicationContext, responseHub, mediaScheduler)
    }

    private fun cancelAllAlarm() {
        mediaScheduler.cancelAllAlarm()
    }

    private fun setMaxVolume() {
        try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
            Log.d("clearDatastore", "clearDatastore: Dung lượng trống thấp -> Dọn dẹp file cũ")
            appStorageManager.deleteAllRecord()
            appStorageManager.deleteAllMusic()

            // Bảo vệ các file media của lịch hôm nay, chỉ xóa các file media cũ không còn trong lịch
            val currentMediaFiles = viewModel.mediaRepo.broadcastList.mapNotNull { it.body }.toSet()
            appStorageManager.getAllMediaPath().forEach { filePath ->
                if (!currentMediaFiles.contains(filePath)) {
                    try {
                        val file = File(filePath)
                        if (file.exists()) {
                            file.delete()
                            Log.d("clearDatastore", "Đã xóa file media cũ không dùng: $filePath")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } else {
            Log.d("clearDatastore", "clearDatastore: Dont have to clear")
        }
    }

    companion object {
        var alarmListener: AlarmListener? = null
    }


}