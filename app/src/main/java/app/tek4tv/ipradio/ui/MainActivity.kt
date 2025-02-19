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
import android.widget.FrameLayout
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
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.PLAY_MODE_FORCE
import app.tek4tv.ipradio.databinding.ActivityMainBinding
import app.tek4tv.ipradio.libs.RtspOnlyAudioTek
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.network.LogService
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.CrashHandler.Companion.restartApp
import app.tek4tv.ipradio.utils.media.MediaCapture
import app.tek4tv.ipradio.utils.network.FtpUtils
import app.tek4tv.ipradio.utils.network.LocationTracker
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.storage.*
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
import org.videolan.libvlc.Media
import java.io.File
import java.io.IOException
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : StreamBaseFrag(), AlarmListener  {
    private val permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.REQUEST_INSTALL_PACKAGES,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.RECORD_AUDIO
    )
    private val UPDATE_PERMISSION_REQUEST_CODE = 1


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

    private lateinit var mediaCapture: MediaCapture

    private lateinit var mediaScheduler: MediaScheduler

    private lateinit var rootLayout: FrameLayout

    private val globalProperties = GlobalProperties.getInst()
    private val UART_S1_NAME = "/dev/ttyS1"
    private val UART_S4_NAME = "/dev/ttyS4"
    private lateinit var binding: ActivityMainBinding

    private var user_name_stream = ""
    private var password_stream = ""

    //    var minuteToTurnOnPA = 0

    lateinit var micStreamRtsp: RtspOnlyAudioTek

    var media: Media? = null


    var urlStream = ""
    var protocol_stream = ""
    var bitrate_stream = ""
    var samplerate = ""


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
//                Log.d("loghub", "onTimeOver: come - turn of pa at second time")
//                serialPortController.turnOffPA()
//                Log.d("turnPA", "onTimeOver: come - turn of pa at second time")
//            }, 10000)
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
            ForceMedia.deleteForeMediaPref(applicationContext)
//            logUtils.logMediaEnd(mediaItem!!)
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
            logUtils.logMediaEnd(mediaItem)
        }

        override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
            hubManager.playlistIndex = playerManager.playlistIndex
            hubManager.currentMediaItem = mediaItem

            val displayName = mediaItem?.title ?: ""
            hubManager.curMediaName = displayName

            if (mediaItem?.getMediaType() != MediaType.FM) {
                serialPortController.turnOnPAVod()
                Log.i("onPlayMediaSuccess", "log media: success ${mediaItem?.title}")
            }
            val paState =
                if (serialPortController.playMode != SerialPortController.MODE_NONE) "1"
                else "0"
            if(mediaItem != null) {
                logUtils.logMediaStart(mediaItem, 0, globalProperties.volume, paState)
            }
            if (globalProperties.logEnabled) {
                try {
                    viewModel.currentMedia = mediaItem
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

       Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

//        if (needGrantPermission()) {
//            grantPermissionThoughAdb()
//        }
        if (needGrantPermission()) requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)

        val hubComm = object : HubComm {
            override fun forceRestartApp(countHardRestart : Int) {
                lifecycleScope.launch {
                    Log.d("resetApp", "force reset $countHardRestart")
                    if(countHardRestart >= 6)
                    {
                        ConfigUtil.putInt(baseContext, ConfigUtil.CountForHardReset, 0)
                        serialPortController.hardRestart()
                    }
                    ConfigUtil.putInt(baseContext, ConfigUtil.CountForHardReset, countHardRestart + 1)
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

        mediaCapture = MediaCapture(applicationContext, lifecycleScope)

        hubManager = HubManager(hubComm, authService) { command, message ->
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
        hubManager.onMessageListener =
            { command, message ->
                handleFromCommandServer(command, message)
            }

        rootLayout = findViewById(R.id.root_layout)
        playerManager = PlayersManager(
            applicationContext,
            viewModel.mediaRepo
        ) //PlayerManager(applicationContext, viewModel, rootLayout)
        playerManager.playerManagerListener = playerCallback

        hubManager.hubEventObserver.add(playerManager)

        NetworkUtils.instance.startNetworkListener(this)

        initHubConnect()
        registerObservers()

        appStorageManager = AppStorageManager(applicationContext)


        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        alarmListener = this

        setMaxVolume()

        hideSystemUI()
        showMac()

        if(isEncoder) {
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
        // lay thong so cua thiet bi
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
               serialPortController.updateStatus()
                delay(5000)
            }
        }
        checkDeleteFileUpdate()

        //doSwap("/data/data/app.tek4tv.ipradio/files/tt3.mp3",SwapType.FrontToBack,SwapEncryptDecryptMode.Decrypt)
    }

    private  fun checkDeleteFileUpdate()
    {
        try {
            val destination = getDestinationApk()
            val file = File(destination)
            if (file.exists()) {
                file.delete()
                Log.d("deletefile", "checkDeleteFileUpdate: delete success")
            } else {
                Log.d("deletefile", "checkDeleteFileUpdate: file not exists")
            }
        }catch (e : Exception){
            Log.d("deletefile", "checkDeleteFileUpdate: delete fail ${e.message}")
        }
    }

    private fun getSteamInfo() {
        urlStream = ConfigUtil.getString(
            this,
            ConfigUtil.URL_STREAM,
            ""
        )!!
        user_name_stream = ConfigUtil.getString(
            this,
            ConfigUtil.USER_NAME_STREAM,
            ""
        )!!
        password_stream = ConfigUtil.getString(
            this,
            ConfigUtil.PASSWORD_STREAM,
            ""
        )!!
        protocol_stream = ConfigUtil.getString(
            this,
            ConfigUtil.PROTOCOL_STREAM,
            "TCP"
        )!!
        bitrate_stream = ConfigUtil.getString(
            this,
            ConfigUtil.BITRATE_STREAM,
            "64000"
        )!!
        samplerate = ConfigUtil.getString(
            this,
            ConfigUtil.SAMPLERATE_STREAM,
            "44100"
        )!!
        streamingModel = StreamingModel(
            streamUrl = urlStream,
            protocolStream = protocol_stream,
            bitrateStream = bitrate_stream,
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
            binding.txtTime.text = "00:00"
        }
    }

    override fun startStream() {
        binding.apply {
            btnRecord.setImageResource(R.drawable.ic_stop_stream_voice)
            txtStatus.text = "End"
        }
        micStreamRtsp.setAuthorization(user_name_stream, password_stream)
        micStreamRtsp.setMicrophoneMode(MicrophoneMode.SYNC)
        if(protocol_stream == "TCP")
        {
            micStreamRtsp.setProtocol(Protocol.TCP)
        }
        else
        {
            micStreamRtsp.setProtocol(Protocol.UDP)
        }
        if (micStreamRtsp.prepareAudio(bitrate_stream.toInt(), samplerate.toInt(), true, false, false)) {
            micStreamRtsp.startStream(urlStream)
            isStreaming = true
//             checkStreamIsAvailableWithVlc()
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
                txtStatus.text = "Start"
            }
        }
    }

    private fun sendLogDataHub(isSendPlaylistData: Boolean = false) {
        var logDeviceData: DataValueModel
        val strTimeFrame = mutableListOf<String>()
        lifecycleScope.launch {
            while (true) {
                strTimeFrame.clear()
                val imei = Utils.getDeviceId(baseContext)
                val timeUpdate =
                    ConfigUtil.getString(
                        baseContext,
                        ConfigUtil.TIME_UPDATE_MONTHLY_SCENARIO,
                        "0"
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
                        idPlaylist,
                        timeUpdate,
                        getCurrentTime(),
                        strTimeFrame,
                        strPlaylist
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

    private fun showMac() {
        val tvMacAdress = findViewById<TextView>(R.id.tvMac)
        val imei = Utils.getDeviceId(this)
        tvMacAdress.text = "$imei - Ver: ${hubManager.versionName}"

    }


    override fun onStop() {
        super.onStop()
        playerManager.onActivityStop()
        stopStream()
        cancelAllAlarm()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.onActivityDestroy()
        stopStream()
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

            MediaScheduleReceiver.ACTION_RESCHEDULE -> {
                Log.d("alarmx", "reschedule all alarms")
                mediaScheduler.cancelAllScheduledListAlarm()

                val monthlyScenarioUrl = ConfigUtil.getString(
                    applicationContext,
                    ConfigUtil.MONTHLY_SCENARIO_URL, ""
                ) ?: return

                viewModel.updateMonthlyScenario(monthlyScenarioUrl)
            }

            MediaScheduleReceiver.ACTION_CHECKHUB_RESET -> {
                    lifecycleScope.launch {
                        serialPortController.turnOffPA()
                        Log.d("turnPA", "check hub reset")
                        delay(3000)
                        Log.e("alarmx", "Hub restart")
                        serialPortController.hardRestart()
                }
            }
            MediaScheduleReceiver.ACTION_TURN_ON_PA -> {
//                var found = false
//                val scheduledList = viewModel.mediaRepo.scheduledList
//                val now = Calendar.getInstance().timeInMillis
//                for (i in scheduledList.keys.indices) {
//                    val period = scheduledList.keys.elementAt(i)
//                    val t = period.split("-")
//                    val start = toCalendar(t[0]).timeInMillis - (minuteToTurnOnPA)*60*1000
//                    val end = toCalendar(t[1]).timeInMillis
//
//                    if (now in start..end) {
//                        found = true
//                        break
//                    }
//                }
//
//                if (found) {
//                    Log.i("alarm", "Turn on Pa")
//                    serialPortController.turnOnPAVod()
//                }

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
//    private fun grantPermissionThoughAdb() {
//        execSu("pm grant app.tek4tv.ipradio android.permission.READ_EXTERNAL_STORAGE")
//        execSu("pm grant app.tek4tv.ipradio android.permission.WRITE_EXTERNAL_STORAGE")
//        execSu("pm grant app.tek4tv.ipradio android.permission.PACKAGE_USAGE_STATS")
//        execSu("pm grant app.tek4tv.ipradio android.permission.READ_PHONE_STATE")
//    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun registerObservers() {
        /**
         * khi cần
         * **/
        viewModel.content.observe(this)
        {
            Log.d("checkcheck", "registerObservers content")
            cancelAllAlarm()
            viewModel.downloadMedias()
            //Check xem có đang phát khẩn cấp không
            //Nếu có thì phát media khẩn cấp
            //Media khẩn cấp lưu share pref
            if (ForceMedia.hasValidForceMedia(applicationContext)) {
                mediaScheduler.scheduleForceMediaAlarm()
                val forceMedia = ForceMedia.getLocalForceMedia(applicationContext)
                if (forceMedia != null)
                    playerManager.playForceMedia(applicationContext, forceMedia, mediaScheduler)
                return@observe
            }
            //default
            mediaScheduler.scheduleAllAlarms()

            playerManager.startFirstTimeFrame()
            sendLogDataHub()
            //all load done, check
//            viewModel.checkPlaylist()
        }
    }

    fun startPlayingMedia() {
        Log.d("checkcheck", "startPlayingMedia: ")
        viewModel.getPlayerContent("", false)
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

            val isImei = Utils.getDeviceId(applicationContext) == responseHub.imei

            if (!isImei) return
            // Log.d("PLAYLIST_INFO", "command $command ")
            when (command) {
                HubCommand.UPDATE_DESTINATION -> {
                    val mess = responseHub.message ?: return
                    Log.i("config_config", "updateconfig $mess")

                    val listInfoConfig = mess.split(",")

                    val newResponseHub = ResponseHub().apply {
                        listInfoConfig.getOrNull(0)?.takeIf { it != "null" }?.let {
                            volume = it
                            serialPortController.writeForceSetVolume(mess)
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
                        globalProperties.providerValue = it
                    }

                    listInfoConfig.getOrNull(7)?.takeIf { it != "null" }?.let {
                        ConfigUtil.putInt(applicationContext, ConfigUtil.Minute_TURN_ON_PA, it.toInt())
                    }

                    listInfoConfig.getOrNull(8)?.takeIf { it != "null" }?.let {
                        ConfigUtil.putInt(applicationContext, ConfigUtil.ServerTimeout, it.toInt())
                    }

                    listInfoConfig.getOrNull(9)?.takeIf { it != "null" }?.let {
                        ConfigUtil.putInt(applicationContext, ConfigUtil.KeepAliveInterval, it.toInt())
                    }

                    globalProperties.saveProperties(newResponseHub)
                }
                HubCommand.UPDATE_MONTHLY_LIST -> {
                    clearDatastore()
                    val mess = responseHub.message!!
                    //  //add at 4.1.3d
//                    playerManager.stopTimeFrame() //dừng khung thời gian đã setup trước
                    playerManager.stopAllPlayer() //dừng trạng thái tất cả player hiện tại
                    playerManager.resetPlayMode() //reset lại trạng thái phát (player mode, FM, Live)
                    cancelAllAlarm() //xóa tất hẹn giờ
                    ForceMedia.deleteForeMediaPref(applicationContext) //xóa bản tin phát trực tiếp
                    viewModel.updateMonthlyScenario(mess) //cập nhật lịch mới
                    ConfigUtil.putString(
                        applicationContext,
                        ConfigUtil.MONTHLY_SCENARIO_URL,
                        mess
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
                    Log.d("message handle", mess ?: "")
                    val listValue = mess.split(",")
                    val urlHub = listValue[0]
                    val urlToken = listValue[1]
                    if (listValue.size >= 3 && listValue[2] != "null") {
                        ConfigUtil.putString(
                            applicationContext,
                            ConfigUtil.PROVIDER,
                            listValue[2]
                        )
                    }
                    if (listValue.size >= 4 && listValue[3] != "null") {
                        ConfigUtil.putString(
                            applicationContext,
                            ConfigUtil.URL_STREAM,
                            listValue[3]
                        )
                    }
                    if (listValue.size >= 5 && listValue[4] != "null")  {
                        ConfigUtil.putString(
                            applicationContext,
                            ConfigUtil.USER_NAME_STREAM,
                            listValue[4]
                        )
                    }
                    if (listValue.size >= 6 && listValue[5] != "null") {
                        ConfigUtil.putString(
                            applicationContext,
                            ConfigUtil.PASSWORD_STREAM,
                            listValue[5]
                        )
                    }
                    if (listValue.size >= 7 && listValue[6] != "null")  {
                        ConfigUtil.putString(
                            applicationContext,
                            ConfigUtil.PROTOCOL_STREAM,
                            listValue[6]
                        )
                    }
                    if (listValue.size >= 8 && listValue[7] != "null") {
                        ConfigUtil.putString(
                            applicationContext,
                            ConfigUtil.BITRATE_STREAM,
                            listValue[7]
                        )
                    }
                    if (listValue.size >= 9 && listValue[8] != "null") {
                        ConfigUtil.putString(
                            applicationContext,
                            ConfigUtil.SAMPLERATE_STREAM,
                            listValue[8]
                        )
                    }
                    if (listValue.size >= 10 && listValue[9] != "null") {
                        ConfigUtil.putString(
                            applicationContext,
                            ConfigUtil.KEY_EXTEND_FILE,
                            listValue[9]
                        )
                    }

                    val hubConnection = HubConnectionBuilder.create(urlHub)
                        .withAccessTokenProvider(Single.defer {
                            val token = hubManager.getHubToken(urlToken)
                            if (token != null) {
                                Single.just(token)
                            } else
                                Single.error(Exception())
                        })
                        .withTransport(TransportEnum.WEBSOCKETS)
                        .build()
                    lifecycleScope.launch {
                        val id = hubManager.openConnection2(hubConnection)
                        if (id != null) {
                            ConfigUtil.putString(
                                applicationContext,
                                ConfigUtil.URL_HUB,
                                urlHub
                            )
                            ConfigUtil.putString(
                                applicationContext,
                                ConfigUtil.URL_TOKEN,
                                urlToken
                            )
                            delay(60000)
                            serialPortController.hardRestart()

                        } else {
                            delay(2000)
                            serialPortController.hardRestart()
                        }

                    }

                }
                HubCommand.TURN_ON_PA -> {
                    val mess = responseHub.message!!.toInt()
                    if(mess == 0)
                    {
                        serialPortController.turnOnPAVod()

                    }
                    else
                    {
                        serialPortController.turnOffPA()

                    }
                }
                HubCommand.CONTROL_MEDIA -> {
                    //0,1,2,3
                    //{"IMEI":"18:93:7F:E5:F5:77","Message":"0"}
                    val mess = responseHub.message!!.toInt()
                    if(playerManager.playMode != PLAY_MODE_FORCE ) {
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
                                }
                            }
                            //Tam dung
                            2 -> {
                                  playerManager.pausePlayer()
//                                playerManager.stopAllPlayer()
//                                playerManager.resetPlayMode()
                            }
                            //chuyen sang ban tin tiep
                            1 -> {
                                Log.d("CONTROL_MEDIA", "isInTimeFrame: ${playerManager.isTimeOver}")
                                if (!playerManager.isTimeOver) {
                                    playerManager.playNextMedia()
                                }
                            }
                            //Phát tiếp nội dung dừng trước đó
                            3 -> {
                                  playerManager.resume()
//                                if (!playerManager.isTimeOver) {
//                                    if (playerManager.played) {
//                                        playerManager.resumeMedia()
//                                    } else {
//                                        playerManager.startFirstTimeFrame()
//                                    }
//                                }
                            }
                        }
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
                                    connectionId,
                                    HubCommand.PLAYLIST_INFO,
                                    dataFromPlaylist
                                )
                            }
                        } catch (e: IOException) {
                            Log.e("readplaylist", "error reading playlist!!!")
                        }
                    }
                    sendLogDataHub(true)
                }
                HubCommand.FM -> {
                    onPlayForce(responseHub)
                }

                HubCommand.UPDATE_STATUS -> {
                    // serialPortController.updateStatus()
                }
                HubCommand.GET_LOCATION -> {
                    val connectionId = responseHub.message
                    if (locationTracker.mlocation != null && connectionId != null) {

                        val result =
                            "${locationTracker.mlocation!!.latitude},${locationTracker.mlocation!!.longitude}"

                        hubManager.sendHubDirectMessage(
                            connectionId, Utils.DEVICE_LOCATION,
                            result
                        )
                    } else Log.d("location", "location not found")
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
                    val duration = responseHub.message
                    val shouldStart = responseHub.start!! == "1"

                    if (shouldStart) {
                        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
                        mediaCapture.startCaptureAudio()
                        Log.i("kakaka", "record.... ")
                    } else {
                        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
                    }
                    Timer().schedule(object : TimerTask() {
                        override fun run() {
                            mediaCapture.stopAudioCapture()
                            Log.i("kakaka", "stop record.... ")
                        }
                    }, duration!!.toLong() * 60 * 1000)
                }

                HubCommand.GET_RECORD -> {
                    val connectionId = responseHub.message!!
                    val recordedAudioList =
                        Utils.toJsonList(moshi, appStorageManager.getAllRecordedAudioPath())
                    hubManager.sendHubDirectMessage(
                        connectionId, Utils.GET_RECORD,
                        recordedAudioList
                    )

                }

                HubCommand.UPLOAD_RECORDED -> {
                    val filePath = responseHub.message!!
                    mediaCapture.updateRecordedAudio(filePath)
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

                HubCommand.GET_LOG_BY_DAY -> {
                    val logName = responseHub.message

                    if (logName != null) {
                        val logPath =
                            "${applicationContext.filesDir.path}/$LOG_FOLDER_NAME/$logName.txt"
                        FtpUtils.uploadLogFile(applicationContext, logPath)
                    }
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
                    playerManager.stopAllPlayer()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    viewModel.getPlayerContent("", false)
                }

            }

            if (command != HubCommand.PONG && command != HubCommand.UPDATE_STATUS && globalProperties.logEnabled) {
                logUtils.logHubCommand(command, responseHub.message ?: "")
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun onPlayForce(responseHub: ResponseHub) {
        //Tránh trường hợp âm lượng bị tắt
        serialPortController.writeForceSetVolume(globalProperties.volume)
        playerManager.onPlayForceCommand(applicationContext, responseHub, mediaScheduler)
    }

    private fun cancelAllAlarm() {
        /* if (ForceMedia.hasValidForceMedia(applicationContext)) mediaScheduler.cancelForceMediaAlarm()
         else mediaScheduler.cancelAllScheduledListAlarm()*/
        mediaScheduler.cancelAllAlarm()
    }

    private fun setMaxVolume() {
        try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
        } catch (e: Exception) {
            Log.e("Main", "Error setting max volume")
        }
    }
    private fun clearDatastore() {
        //limit free data 1 gb
//        val limit = 1000000000
        //limit free data 500 mb
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
        var isEncoder = false
        var streamingModel : StreamingModel? = null
    }
}