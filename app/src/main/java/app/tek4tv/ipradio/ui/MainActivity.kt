package app.tek4tv.ipradio.ui


import android.Manifest
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
import androidx.lifecycle.lifecycleScope
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.HubComm
import app.tek4tv.ipradio.core.HubManager
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.core.players_helper.IPlayerManagerListener
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import app.tek4tv.ipradio.utils.network.CaCertHelper
import app.tek4tv.ipradio.databinding.ActivityMainBinding
import app.tek4tv.ipradio.libs.RtspOnlyAudioTek
import app.tek4tv.ipradio.model.DataValueModel
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.model.StreamingModel
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.network.LogService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.CrashHandler
import app.tek4tv.ipradio.utils.CrashHandler.Companion.restartApp
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.LogUtils
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.checkDeleteFileUpdate
import app.tek4tv.ipradio.utils.downloadUpdateApk
import app.tek4tv.ipradio.utils.getCurrentTime
import app.tek4tv.ipradio.utils.network.LocationTracker
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.secToReadable
import app.tek4tv.ipradio.utils.setMobileDataEnabled
import app.tek4tv.ipradio.utils.setWifeDataEnabled
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import app.tek4tv.ipradio.utils.storage.PLAYLIST_FILE_NAME
import app.tek4tv.ipradio.utils.storage.RECORDED_AUDIO_FOLDER_NAME
import app.tek4tv.ipradio.utils.storage.deleteAFileInFolder
import app.tek4tv.ipradio.utils.storage.isFileExisted
import app.tek4tv.ipradio.utils.storage.readFile
import app.tek4tv.ipradio.viewmodels.MainViewModel
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.TransportEnum
import com.pedro.encoder.input.audio.MicrophoneMode
import com.pedro.rtsp.rtsp.Protocol
import com.squareup.moshi.Moshi
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Calendar
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : StreamBaseFrag(), AlarmListener {
    private val permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.REQUEST_INSTALL_PACKAGES,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    private val PERMISSION_REQUEST_CODE = 1


    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var moshi: Moshi

    @Inject
    lateinit var authService: AuthService

    @Inject
    lateinit var logService: LogService

    private lateinit var hubManager: HubManager
    private lateinit var playerManager: PlayersManager

    private lateinit var serialPortController: SerialPortController

    private lateinit var appStorageManager: AppStorageManager

    private lateinit var logUtils: LogUtils

    private var volume = "80"

    var locationTracker = LocationTracker()


    private lateinit var mediaScheduler: MediaScheduler


    private val globalProperties = GlobalProperties.getInst()

    private val UART_S1_NAME = "/dev/ttyS1"
    private val UART_S4_NAME = "/dev/ttyS4"
    private lateinit var binding: ActivityMainBinding

    private var userNameStream = ""
    private var passwordStream = ""

    private lateinit var micStreamRtsp: RtspOnlyAudioTek

    private var urlStream = ""
    private var protocolStream = ""
    private var bitrateStream = ""
    private var samplerate = ""


    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            Log.d("loghub", "onTimeOver: come")
            serialPortController.turnOffPA()
            serialPortController.writeForceSetVolume(globalProperties.volume)
            hubManager.curMediaName = ""
            hubManager.currentMediaItem = null
            //chi dung cho ban nam dinh
//            Handler(Looper.getMainLooper()).postDelayed({
//                serialPortController.turnOffPA()
//            }, 1500)
//            }, 1500)
        }

        //Phát FM
        override fun onPlayTuner(mediaItem: MediaItem) {
            try {
                val tunerType = if (mediaItem.getMediaType() == MediaType.AM) "0" else "1"
                val pipsData = "${globalProperties.piCode},${globalProperties.psName}"
                val tunerData = "$tunerType,${mediaItem.body},${globalProperties.volume}"
                val paState =
                    if (serialPortController.playMode != SerialPortController.MODE_NONE) "1"
                    else "0"
                hubManager.currentMediaItem = mediaItem

                val displayName = mediaItem.title ?: ""
                hubManager.curMediaName = displayName

                if (!serialPortController.isUart1) {
                    lifecycleScope.launch {
                        delay(11000)
                        serialPortController.playTuner(pipsData, tunerData)
                    }
                } else {
                    serialPortController.playTuner(pipsData, tunerData)
                }

                logUtils.logMediaStart(mediaItem, 0, globalProperties.volume, paState)
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
            hubManager.curMediaName = "Live Error"
            hubManager.currentMediaItem = mediaItem
            serialPortController.turnOffPA()
            Log.d("turnPA", "live error")
        }

        override fun onMediaStopPlaying(mediaItem: MediaItem, duration: Long) {
        }

        override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
            hubManager.playlistIndex = playerManager.playlistIndex
            hubManager.currentMediaItem = mediaItem

            val displayName = mediaItem?.title ?: ""
            hubManager.curMediaName = displayName

            serialPortController.turnOnPAVod()

            val paState = if (serialPortController.playMode != SerialPortController.MODE_NONE) "1"
            else "0"
            if (mediaItem != null) {
                logUtils.logMediaStart(mediaItem, 0, globalProperties.volume, paState)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel.grantPermissionThoughAdb(baseContext)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        //if (needGrantPermission()) requestPermissions(permissions, PERMISSION_REQUEST_CODE)


//        if (ConfigUtil.isFirstRun(baseContext)) {
//            ConfigUtil.clearAppPreferences(baseContext)
//        }

        val hubComm = object : HubComm {
            override fun forceRestartApp(countHardRestart: Int) {
                lifecycleScope.launch {
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
                    setMobileDataEnabled(false)
                    setWifeDataEnabled(true)
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

        logUtils = LogUtils.getInst()
        logUtils.logService = logService


        hubManager = HubManager(baseContext, hubComm, authService) { command, message ->
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
        hubManager.onMessageListener = { command, message ->
            handleFromCommandServer(command, message)
        }
        var lastHubScheduleSyncTime = 0L
        hubManager.onHubConnectedListener = {
            val now = System.currentTimeMillis()
            if (now - lastHubScheduleSyncTime >= 5 * 60 * 1000L) {
                lastHubScheduleSyncTime = now
                lifecycleScope.launch(Dispatchers.Main) {
                    val isScheduleEmpty = viewModel.content.value.isNullOrEmpty()
                    val isNotStarted = playerManager.isIdle()
                    if (isNotStarted && isScheduleEmpty && !playerManager.isPlaying() && !ForceMedia.hasValidForceMedia(
                            applicationContext
                        )
                    ) {
                        Log.i(
                            "MainActivity",
                            "Hub connected & schedule is empty -> fetching schedule now"
                        )
                        startPlayingMedia()
                    }
                }
            }
        }

        playerManager = PlayersManager(
            applicationContext, viewModel.mediaRepo
        )
        playerManager.playerManagerListener = playerCallback
        serialPortController.isPlayingProvider = { playerManager.isPlaying() }

        NetworkUtils.instance.startNetworkListener(this)
        NetworkUtils.instance.addCallback(object : NetworkUtils.ConnectionCallback {
            override fun onChange(netWorkState: Boolean) {
                if (netWorkState) {
                    Log.i("MainActivity", "Network connected -> reconnect Hub & sync playback/PA")
                    lifecycleScope.launch(Dispatchers.Main) {
                        hubManager.connectToHub()
                        val isScheduleEmpty = viewModel.content.value.isNullOrEmpty()
                        val isNotStarted = playerManager.isIdle()

                        if (isNotStarted && isScheduleEmpty && !playerManager.isPlaying()) {
                            startPlayingMedia()
                        } else {
                            serialPortController.syncPAState()
                        }
                    }
                }
            }
        })

        initHubConnect()

        registerObservers()

        appStorageManager = AppStorageManager(applicationContext)


        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        alarmListener = this

        setMaxVolume()

        hideSystemUI()
        showMac()

        if (isEncoder) {
            micStreamRtsp = RtspOnlyAudioTek(this)
            getSteamInfo()
            lifecycleScope.launch(Dispatchers.Main) {
                while (true) {
                    if (hubManager.pingCounter <= 5) {
                        if (!isStreaming) {
                            delay(10000)
                            if (urlStream != "") {
                                startStream()
                            }
                        }
                        Log.i("pingping:", "micStream.isStreaming:  $isStreaming")
                    } else {
                        stopStream()
                    }
                    delay(5000)
                }
            }
        }

        checkDeleteFileUpdate()

    }


    private fun getSteamInfo() {
        urlStream = ConfigUtil.getString(
            this, ConfigUtil.URL_STREAM, ""
        )!!
        userNameStream = ConfigUtil.getString(
            this, ConfigUtil.USER_NAME_STREAM, ""
        )!!
        passwordStream = ConfigUtil.getString(
            this, ConfigUtil.PASSWORD_STREAM, ""
        )!!
        protocolStream = ConfigUtil.getString(
            this, ConfigUtil.PROTOCOL_STREAM, "TCP"
        )!!
        bitrateStream = ConfigUtil.getString(
            this, ConfigUtil.BITRATE_STREAM, "64000"
        )!!
        samplerate = ConfigUtil.getString(
            this, ConfigUtil.SAMPLERATE_STREAM, "44100"
        )!!
        streamingModel = StreamingModel(
            streamUrl = urlStream,
            protocolStream = protocolStream,
            bitrateStream = bitrateStream,
            samplerate = samplerate
        )
    }

    override fun startStopWatch() {
        var secondPassed = 0L
        lifecycleScope.launch {
            while (isStreaming) {
                delay(1000)
                secondPassed++
                binding.txtTime.text = secToReadable(secondPassed)
            }
            binding.txtTime.text = getString(R.string.count_time_default)
        }
    }

    override fun startStream() {
        binding.apply {
            btnRecord.setImageResource(R.drawable.ic_stop_stream_voice)
            txtStatus.text = getString(R.string.txt_end_encoder)
        }
        micStreamRtsp.setAuthorization(userNameStream, passwordStream)
        micStreamRtsp.setMicrophoneMode(MicrophoneMode.SYNC)
        if (protocolStream == "TCP") {
            micStreamRtsp.setProtocol(Protocol.TCP)
        } else {
            micStreamRtsp.setProtocol(Protocol.UDP)
        }
        if (micStreamRtsp.prepareAudio(
                bitrateStream.toInt(), samplerate.toInt(), true, false, false
            )
        ) {
            micStreamRtsp.startStream(urlStream)
            isStreaming = true
        } else {
            Log.d("RTMP", "startStream: micStream not prepare success")
        }
    }


    override fun stopStream() {
        if (isEncoder) {
            isStreaming = false
            micStreamRtsp.stopStream()
            binding.apply {
                btnRecord.setImageResource(R.drawable.ic_start_stream_voice)
                txtStatus.text = getString(R.string.txt_start_encoder)
            }
        }
    }

    private fun sendLogDataHub(isSendPlaylistData: Boolean = false) {
        if (NetworkUtils.isNetworkConnected(baseContext)) {
            var logDeviceData: DataValueModel
            val strTimeFrame = mutableListOf<String>()
            lifecycleScope.launch {
                while (true) {
                    strTimeFrame.clear()
                    val imei = Utils.getDeviceId(baseContext)
                    val timeUpdate = ConfigUtil.getString(
                        baseContext, ConfigUtil.TIME_UPDATE_MONTHLY_SCENARIO, "0"
                    )!!
                    val idPlaylist =
                        ConfigUtil.getString(baseContext, ConfigUtil.MONTHLY_SCENARIO_URL, "")!!
                    val scheduledList = viewModel.mediaRepo.timeDividers
                    for (i in scheduledList.keys.indices) {
                        val period = scheduledList.keys.elementAt(i)
                        strTimeFrame.add(period)
                    }
                    if (isSendPlaylistData) {
                        val fileStoragePath = "${baseContext.filesDir.path}/$PLAYLIST_FILE_NAME"
                        var strPlaylist = ""
                        if (isFileExisted(baseContext.filesDir.path, PLAYLIST_FILE_NAME)) {
                            strPlaylist = readFile(fileStoragePath)
                        }
                        logDeviceData = DataValueModel(
                            idPlaylist, timeUpdate, getCurrentTime(), strTimeFrame, strPlaylist
                        )
                    } else {
                        logDeviceData =
                            DataValueModel(idPlaylist, timeUpdate, getCurrentTime(), strTimeFrame)
                    }
                    logUtils.logDataHub(imei, logDeviceData)
                    delay(300000)
                }
            }
        }
    }

    private fun showMac() {
        val tvMacAddress = findViewById<TextView>(R.id.tvMac)
        val imei = hubManager.deviceImei
        tvMacAddress.text = buildString {
            append(imei)
            append(" - Ver: ")
            append(hubManager.versionName)
        }
    }


    override fun onStop() {
        super.onStop()
        serialPortController.turnOffPA()
        playerManager.onActivityStop()
        stopStream()
        cancelAllAlarm()
    }

    override fun onDestroy() {
        super.onDestroy()
        serialPortController.turnOffPA()
        playerManager.onActivityDestroy()
        stopStream()
        cancelAllAlarm()
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
                    mediaScheduler.cancelAllAlarm()
                    val monthlyScenarioUrl = ConfigUtil.getString(
                        applicationContext, ConfigUtil.MONTHLY_SCENARIO_URL, ""
                    ) ?: ""
                    viewModel.updateMonthlyScenario(monthlyScenarioUrl)
                    serialPortController.turnOffPA()
                    delay(120000)
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
                    applicationContext, forceMedia, mediaScheduler
                )
                return@observe
            }
            //default
            mediaScheduler.scheduleAllAlarms()

            playerManager.startFirstTimeFrame()

            sendLogDataHub()

            //all load done, check
            viewModel.checkPlaylist(appStorageManager)
        }
    }

    fun startPlayingMedia() {
        Log.d("checkcheck", "startPlayingMedia: ")
        viewModel.getPlayerContent()
    }

    private fun initHubConnect() {
        hubManager.connectToHub()
        locationTracker.getLocation(this@MainActivity)
    }

    private fun handleFromCommandServer(command: String?, message: String?) {
        try {
            if (command.isNullOrEmpty()) {
                return
            }
            if (message.isNullOrEmpty()) {
                return
            }

            val responseHub = hubManager.responseHub


            when (command) {
                HubCommand.UPDATE_MONTHLY_LIST -> {
                    val mess = responseHub.message!!
                    serialPortController.turnOffPA()
                    //  //add at 4.1.3d
//                    playerManager.stopTimeFrame() //dừng khung thời gian đã setup trước
                    playerManager.stopAllPlayer() //dừng trạng thái tất cả player hiện tại
                    playerManager.resetPlayMode() //reset lại trạng thái phát (player mode, FM, Live)
                    cancelAllAlarm() //xóa tất hẹn giờ
                    ForceMedia.deleteForeMediaPref(applicationContext) //xóa bản tin phát trực tiếp
                    viewModel.stopDownloadMedia()
                    viewModel.updateMonthlyScenario(mess) //cập nhật lịch mới
                    ConfigUtil.putString(
                        applicationContext, ConfigUtil.MONTHLY_SCENARIO_URL, mess
                    ) //lưu dữ link vào local storage cho việc lặp lịch vào hôm sau
                    // lưu thời gian cập nhật lịch để gửi log
                    val currentTime = Calendar.getInstance().time.time / 1000
                    ConfigUtil.putString(
                        applicationContext,
                        ConfigUtil.TIME_UPDATE_MONTHLY_SCENARIO,
                        currentTime.toString()
                    )
                    //add at 4.1.3b
                    playerManager.stopForceMedia() //stop force media
                }
                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {
                    onPlayForce(responseHub)
                }

                HubCommand.UPDATE_CONFIG -> {
                    //{"IMEI":"18:93:7F:E5:F5:77","Message":"https://hub.gateway.httt.tek4tv.vn/hubServer,https://api.gateway.httt.tek4tv.vn/api/auth/login"}
                    val mess = responseHub.message!!
                    Log.d("message handle", mess)
                    val listValue = mess.split(",")
                    val urlHub = listValue[0].trim()
                    val urlToken = listValue[1].trim()
                    if (listValue.size >= 3 && listValue[2] != "null") {
                        ConfigUtil.putString(
                            applicationContext, ConfigUtil.PROVIDER, listValue[2].trim()
                        )
                    }
                    if (listValue.size >= 4 && listValue[3] != "null") {
                        ConfigUtil.putString(
                            applicationContext, ConfigUtil.URL_STREAM, listValue[3].trim()
                        )
                    }
                    if (listValue.size >= 5 && listValue[4] != "null") {
                        ConfigUtil.putString(
                            applicationContext, ConfigUtil.USER_NAME_STREAM, listValue[4].trim()
                        )
                    }
                    if (listValue.size >= 6 && listValue[5] != "null") {
                        ConfigUtil.putString(
                            applicationContext, ConfigUtil.PASSWORD_STREAM, listValue[5].trim()
                        )
                    }
                    if (listValue.size >= 7 && listValue[6] != "null") {
                        ConfigUtil.putString(
                            applicationContext, ConfigUtil.PROTOCOL_STREAM, listValue[6].trim()
                        )
                    }
                    if (listValue.size >= 8 && listValue[7] != "null") {
                        ConfigUtil.putString(
                            applicationContext, ConfigUtil.BITRATE_STREAM, listValue[7].trim()
                        )
                    }
                    if (listValue.size >= 9 && listValue[8] != "null") {
                        ConfigUtil.putString(
                            applicationContext, ConfigUtil.SAMPLERATE_STREAM, listValue[8].trim()
                        )
                    }
                    if (listValue.size >= 10 && listValue[9] != "null") {
                        ConfigUtil.putString(
                            applicationContext, ConfigUtil.KEY_EXTEND_FILE, listValue[9].trim()
                        )
                    }

                    if (urlHub.isNotEmpty() && urlHub != "null") {
                        ConfigUtil.putString(
                            applicationContext, ConfigUtil.URL_HUB, urlHub
                        )
                    }
                    if (urlToken.isNotEmpty() && urlToken != "null") {
                        ConfigUtil.putString(
                            applicationContext, ConfigUtil.URL_TOKEN, urlToken
                        )
                    }

                    val hubConnection =
                        HubConnectionBuilder.create(urlHub)
                            .setHttpClientBuilderCallback { builder ->
                                CaCertHelper.buildConfiguredOkHttpClient(
                                    builder,
                                    applicationContext
                                )
                            }
                            .withAccessTokenProvider(Single.defer {
                                val token = hubManager.getHubToken(urlToken)
                                if (token != null) {
                                    Single.just(token)
                                } else Single.error(Exception("Cannot get hub token"))
                            }).withTransport(TransportEnum.WEBSOCKETS).build()
                    lifecycleScope.launch {
                        val id = hubManager.openConnection2(hubConnection)
                        try {
                            hubConnection.stop().blockingAwait()
                        } catch (e: Exception) {
                            Log.e(
                                "UPDATE_CONFIG",
                                "Error stopping test hub connection: ${e.message}"
                            )
                        }
                        if (id != null) {
                            Log.i(
                                "UPDATE_CONFIG",
                                "Connect test success with id=$id, restarting..."
                            )
                            delay(2000)
                            serialPortController.hardRestart()
                            delay(3000)
                            restartApp(MyApp.instance!!, this@MainActivity)
                        } else {
                            Log.e(
                                "UPDATE_CONFIG",
                                "Connect test failed (id is null), restarting to apply config..."
                            )
                            delay(2000)
                            serialPortController.hardRestart()
                            delay(3000)
                            restartApp(MyApp.instance!!, this@MainActivity)
                        }
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
                            lat = it.toFloat()
                        }
                        listInfoConfig.getOrNull(2)?.takeIf { it != "null" }?.let {
                            lng = it.toFloat()
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
                        hubManager.providerValue = it
                    }

                    listInfoConfig.getOrNull(7)?.takeIf { it != "null" }?.let {
                        ConfigUtil.putInt(
                            applicationContext, ConfigUtil.Minute_TURN_ON_PA, it.toInt()
                        )
                    }

                    listInfoConfig.getOrNull(8)?.takeIf { it != "null" }?.let {
                        ConfigUtil.putInt(applicationContext, ConfigUtil.ServerTimeout, it.toInt())
                    }

                    listInfoConfig.getOrNull(9)?.takeIf { it != "null" }?.let {
                        ConfigUtil.putInt(
                            applicationContext, ConfigUtil.KeepAliveInterval, it.toInt()
                        )
                    }

                    globalProperties.saveProperties(newResponseHub)
                }

                HubCommand.TURN_ON_PA -> {
                    val mess = responseHub.message
                    try {
                        when (val value = mess?.toIntOrNull()) {
                            0 -> serialPortController.turnOnPAVod()
                            1 -> serialPortController.turnOffPA()
                            null -> {
                                // mess không phải là số
                                Log.e("HubCommand", "Message is not a valid number: $mess")
                            }

                            else -> {
                                // mess là số nhưng không phải 0 hoặc 1
                                Log.w("HubCommand", "Unexpected PA control value: $value")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("HubCommand", "Error parsing PA command: ${e.message}")
                    }
                }

                HubCommand.CONTROL_MEDIA -> {
                    try {
                        //0,1,2,3
                        //{"IMEI":"18:93:7F:E5:F5:77","Message":"0"}
                        val mess = responseHub.message!!.toInt()
//                    if (playerManager.playMode != PLAY_MODE_FORCE) {
//                        when (mess) {
//                            //dung phat tat ca
//                            0 -> {
////                                playerManager.stopAllPlayer()
////                                playerManager.resetPlayMode()
//                                cancelAllAlarm()
//                                playerManager.stopAndEndTimeFrame()
//                                playerManager.resetPlayMode()
//                                lifecycleScope.launch {
//                                    viewModel.mediaRepo.clearPlaylist()
//                                }
//                            }
//                            //Tam dung
//                            2 -> {
//                                playerManager.pausePlayer()
//                            }
//                            //chuyen sang ban tin tiep
//                            1 -> {
//                                Log.d(
//                                    "CONTROL_MEDIA", "isInTimeFrame: ${playerManager.isTimeOver}"
//                                )
//                                if (!playerManager.isTimeOver) {
//                                    playerManager.playNextMedia()
//                                }
//                            }
//                            //Phát tiếp nội dung dừng trước đó
//                            3 -> {
//                                if (!playerManager.isTimeOver) {
//                                      playerManager.resume()
//                                }
//                            }
//                        }
//                    }
                        //bacninh
                        when (mess) {
                            //dung phat tat ca
                            0 -> {
//                                playerManager.stopAllPlayer()
//                                playerManager.resetPlayMode()
                                cancelAllAlarm()
                                playerManager.stopAndEndTimeFrame()
                                playerManager.resetPlayMode()
                                lifecycleScope.launch {
                                    viewModel.mediaRepo.clearPlaylist()
                                    viewModel.mediaRepo.clearMonthlyLocalData()
                                }
                            }
                            //Tam dung
                            2 -> {
                                playerManager.pausePlayer()
                            }
                            //chuyen sang ban tin tiep
                            1 -> {
                                Log.d(
                                    "CONTROL_MEDIA", "isInTimeFrame: ${playerManager.isTimeOver}"
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
                    } catch (e: Exception) {
                        Log.e("HubCommand", "Error processing CONTROL_MEDIA: ${e.message}")
                    }
                }

                HubCommand.PLAYLIST_INFO -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val path = "${filesDir.path}/$PLAYLIST_FILE_NAME"
                            val dataFromPlaylist: String = readFile(path)
                            Log.d("readplaylist", "value2 $dataFromPlaylist ")
                            val connectionId = responseHub.message
                            Log.d("PLAYLIST_INFO", "Connectid $connectionId ")
                            if (connectionId != null) {
                                hubManager.sendHubDirectMessage(
                                    connectionId, HubCommand.PLAYLIST_INFO, dataFromPlaylist
                                )
                            }
                        } catch (_: IOException) {
                            Log.e("readplaylist", "error reading playlist!!!")
                        }
                    }
                    sendLogDataHub(true)
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
                    serialPortController.setTimeOn(volume)
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
                        hubManager.sendHubDirectMessage(connectionId, Utils.NETWORK_INFO, result)
                    }
                }


                HubCommand.STORAGE_INFO -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        val info = appStorageManager.run {
                            "${getTotalRam()},${getUsedRam()},${getTotalRomStorage()},${getUsedRomStorage()}"
                        }

                        hubManager.sendHubDirectMessage(connectionId, Utils.STORAGE_INFO, info)
                    }
                }

                HubCommand.GET_MEDIA_PATH -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        Log.d("connectionId", connectionId)
                        val mediaList = Utils.toJsonList(moshi, appStorageManager.getAllMediaPath())
                        hubManager.sendHubDirectMessage(
                            connectionId, Utils.GET_MEDIA_PATH, mediaList
                        )
                    }
                }

                HubCommand.DELETE_ALL_MEDIA -> {
                    appStorageManager.deleteAllMedia()
                }

                HubCommand.RECORD -> {
                    //mess chua user control
//                    val shouldStart = responseHub.start!! == "1"
//
//                    if (shouldStart) {
//                        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
//
//                        mediaCapture.startCaptureAudio()
//                    } else {
//                        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
//                    }
//                    val duration = responseHub.message
//                    val shouldStart = responseHub.start!! == "1"
//
//                    if (shouldStart) {
//                        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
//                        mediaCapture.startCaptureAudio()
//                        Log.i("kakaka", "record.... ")
//                    } else {
//                        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
//                    }
//                    Timer().schedule(object : TimerTask() {
//                        override fun run() {
//                            mediaCapture.stopAudioCapture()
//                            Log.i("kakaka", "stop record.... ")
//                        }
//                    }, duration!!.toLong() * 60 * 1000)
                }

                HubCommand.GET_RECORD -> {
                    val connectionId = responseHub.message!!
                    val recordedAudioList =
                        Utils.toJsonList(moshi, appStorageManager.getAllRecordedAudioPath())
                    hubManager.sendHubDirectMessage(
                        connectionId, Utils.GET_RECORD, recordedAudioList
                    )
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

                HubCommand.GET_ENCRYPTED_STORAGE_INFO -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        val info = appStorageManager.run {
                            "${getTotalRomStorage()},${getUsedRomStorage()}"
                        }

                        hubManager.sendEncryptedMessage(
                            connectionId, Utils.ENCRYPTED_STORAGE_INFO, info
                        )
                    }
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

//            if (command != HubCommand.PONG && command != HubCommand.UPDATE_STATUS) {
//                logUtils.logHubCommand(command, responseHub.message ?: "")
//            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun onPlayForce(responseHub: ResponseHub) {
        //Tránh trường hợp âm lượng bị tắt
        serialPortController.writeForceSetVolume(globalProperties.volume)
        playerManager.onPlayForceCommand(applicationContext, responseHub, mediaScheduler)
    }

    private fun cancelAllAlarm() {/* if (ForceMedia.hasValidForceMedia(applicationContext)) mediaScheduler.cancelForceMediaAlarm()
         else mediaScheduler.cancelAllScheduledListAlarm()*/
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


    companion object {
        var alarmListener: AlarmListener? = null
        var isEncoder = false
        var streamingModel: StreamingModel? = null
    }
}