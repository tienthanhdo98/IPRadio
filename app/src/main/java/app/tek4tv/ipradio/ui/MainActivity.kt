package app.tek4tv.ipradio.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.*
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.network.LogService
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.media.MediaCapture
import app.tek4tv.ipradio.utils.network.FtpUtils
import app.tek4tv.ipradio.utils.network.LocationTracker
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import app.tek4tv.ipradio.viewmodels.MainViewModel
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.TransportEnum
import com.squareup.moshi.Moshi
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), AlarmListener {

    private val UPDATE_PERMISSION_REQUEST_CODE = 1

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

    private val globalProperties = GlobalProperties.getInst()
    private val UART_S1_NAME = "/dev/ttyS1"
    private val UART_S4_NAME = "/dev/ttyS4"
    private lateinit var webview: WebView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        if (needGrantPermission()) requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)

        val hubComm = object : HubComm {
            override fun forceRestartApp() {
                lifecycleScope.launch {
                    serialPortController.turnOffPA()
                    delay(3000)
                    CrashHandler.restartApp(MyApp.instance!!, this@MainActivity)
                }

            }

            override fun forceRestartDevice() {
                serialPortController.hardRestart()
            }
        }

        logUtils = LogUtils.getInst()
        logUtils.logService = logService
        //logUtils.logStartApp()
        mediaCapture = MediaCapture(applicationContext, lifecycleScope)

        hubManager = HubManager(hubComm, authService, mediaCapture) { command, message ->
            handleFromCommandServer(command, message)
        }
        hubManager.logService = logService

        initWebview()

        serialPortController = SerialPortController(applicationContext, lifecycleScope, hubManager)
        // serialPortController.connectToSerialPort("/dev/ttyS4")
        serialPortController.connectToSerialPort(UART_S1_NAME)
        lifecycleScope.launch {
            delay(10000)
            if (!serialPortController.isUart1) {
                serialPortController.connectToSerialPort(UART_S4_NAME)
                Log.i("PA", "onCreate: s4")
            }
        }


        NetworkUtils.instance.startNetworkListener(this)

        initHubConnect()
        registerObservers()
        playWebview()


        //appStorageManager = AppStorageManager(applicationContext)


        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        alarmListener = this

        setMaxVolume()

        hideSystemUI()
        showMac()


    }

    private fun initWebview() {
        webview = findViewById(R.id.webview)
        webview.settings.apply {
            allowContentAccess = true
            allowFileAccess = true
            domStorageEnabled = true
            javaScriptEnabled = true
            loadsImagesAutomatically = true
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            /*loadWithOverviewMode = true
            useWideViewPort = true*/
        }
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit" +
                "/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3"
        webview.settings.setUserAgentString(userAgent)

        webview.webChromeClient = WebChromeClient()
        webview.addJavascriptInterface(WebViewJsInterface(webview), "MainActivity")

//        webview.webViewClient = object : WebViewClient() {
//            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
//                return false
//            }
//        }
    }


    private fun showMac() {
        val tvMacAdress = findViewById<TextView>(R.id.tvMacAddress)

        val imei = Utils.getDeviceId(this)
        Log.i("tvMacAdress", "showMac: $imei")
        tvMacAdress.text = "$imei - Ver: ${hubManager.versionName}"
    }




    override fun onAlarm(intent: Intent) {
        when (intent.action) {
            MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", listKey)
                playerManager.setTimeFrame(listKey, null)

            }

            MediaScheduleReceiver.ACTION_END_PLAYLIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", "end: $listKey")

                playerManager.stopPlaylist(listKey!!)
            }

            MediaScheduleReceiver.ACTION_END_MEDIA -> {
                val mediaIndex = intent.getIntExtra(MediaScheduleReceiver.SCHEDULED_MEDIA_INDEX, 0)
                Log.d("alarmx", "end media: $mediaIndex")

                playerManager.endScheduledMediaByIndex(mediaIndex)
            }

            MediaScheduleReceiver.ACTION_END_FORCE_MEDIA -> {
                playerManager.stopForceMedia()
                Log.d("alarmx", "stop force")
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
                val isConnect = ConfigUtil.getString(this, "checkhub", "0")
                if (isConnect == "0") {
                    lifecycleScope.launch {
                        serialPortController.turnOffPA()
                        delay(3000)
                        Log.e("alarmx", "Hub restart")
                        serialPortController.hardRestart()
                    }


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

    private fun playWebview() {

        //webview.loadUrl("http://digitalweb.httt.vtcplay.vn/")
        webview.loadUrl("https://webdigital.httt.vtcplay.vn/")

//        Handler().postDelayed({
//            webview.loadUrl("javascript:getCommandDivice('Template','2')")
//            Log.d("WebviewHandle", "sendCommand: sendLive ")
//            //doSomethingHere()
//        }, 7000)

//        Handler().postDelayed({
//            webview.loadUrl("javascript:getCommandDivice('Template','1')")
//            Log.d("WebviewHandle", "sendCommand: sendLive ")
//            //doSomethingHere()
//        }, 15000)

        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)

// Get an array of MediaCodecInfo objects representing the available codecs

// Get an array of MediaCodecInfo objects representing the available codecs
        val codecInfos = codecList.codecInfos

// Loop through the codecs and print their names and supported types

// Loop through the codecs and print their names and supported types
        for (codecInfo in codecInfos) {
            if (!codecInfo.isEncoder) { // Only check decoder codecs
                val codecName = codecInfo.name
                val types = codecInfo.supportedTypes
                // Print the codec name and supported types
                Log.d("Codec", "Codec: " + codecName + ", types: " + Arrays.toString(types))
            }
        }

    }

    private fun registerObservers() {
        viewModel.content.observe(this)
        {
            playWebview()
            viewModel.downloadMedias()


            //default
            mediaScheduler.scheduleAllAlarms()
            //check hub
            mediaScheduler.setCheckHubAndResetAlarms()


        }
    }


    private fun initHubConnect() {
        hubManager.connectToHub()
        locationTracker.getLocation(this@MainActivity)

    }

    private fun handleFromCommandServer(command: String?, message: String?) {
        try {
            if (command == null || command.isEmpty()) {
                return
            }
            if (message == null || message.isEmpty()) {
                return
            }

            val responseHub = hubManager.responseHub

            val isImei = Utils.getDeviceId(applicationContext) == responseHub.imei

            if (!isImei) return
            // Log.d("PLAYLIST_INFO", "command $command ")
            when (command) {
                HubCommand.UPDATE_LIST -> {
//                    val mess = responseHub.message!!
//                    lifecycleScope.launch {
//
//                        viewModel.checkTimeAndLevel(mess).collect {
//
//                            Log.i("timeandlevel", "handleFromCommandServer: $it")
//                        }
//                    }
//
//                    //add at 3.0.5
//                    playerManager.stopAllPlayer()
//                    playerManager.resetPlayMode()
//
//                    //lưu thông số vol, pi,...
//                    globalProperties.saveProperties(responseHub)
//
//                    serialPortController.writeForceSetVolume(responseHub.volume)
//
//                    cancelAllAlarm()
//                    ForceMedia.deleteForeMediaPref(applicationContext)
//
//                    viewModel.getPlayerContent(mess, true)


                        webview.loadUrl("javascript:getCommandDivice('Template','2')")
                        Log.d("WebviewHandle", "sendCommand: sendLive ")




                }

                HubCommand.UPDATE_MONTHLY_LIST -> {
//                    val mess = responseHub.message!!
//                    playerManager.stopAllPlayer()
//                    cancelAllAlarm()
//                    ForceMedia.deleteForeMediaPref(applicationContext)
//                    viewModel.updateMonthlyScenario(mess, true)
//                    ConfigUtil.putString(applicationContext, ConfigUtil.MONTHLY_SCENARIO_URL, mess)
//
//                    Log.i("monthlyinfo", "Mess: $mess ")
                    webview.loadUrl("javascript:getCommandDivice('Template','1')")
                    Log.d("WebviewHandle", "sendCommand: sendLive ")
                }
                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {
                    //onPlayForce(responseHub)
                }
                HubCommand.LiveRemoteControl ->{
                    val mess = responseHub.message!!
                    Log.i("LiveRemoteControl", "Mess: $mess ")
                }
                HubCommand.UPDATE_CONFIG -> {
                    //{"IMEI":"18:93:7F:E5:F5:77","Message":"https://hub.gateway.httt.tek4tv.vn/hubServer,https://api.gateway.httt.tek4tv.vn/api/auth/login"}
                    Log.i("UPDATE_CONFIG be", responseHub.message!!)
                    val mess = responseHub.message!!
                    val urlHub = mess.split(",")[0]
                    val urlToken = mess.split(",")[1]
                    Log.i("UPDATE_CONFIG hub", urlHub)
                    Log.i("UPDATE_CONFIG token", urlToken)
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
                            ConfigUtil.putStringEncrypt(
                                applicationContext,
                                ConfigUtil.URL_HUB,
                                urlHub
                            )
                            ConfigUtil.putStringEncrypt(
                                applicationContext,
                                ConfigUtil.URL_TOKEN,
                                urlToken
                            )
                            Log.i("config_config", "updateconfig success: reseting")
                            delay(60000)
                            serialPortController.hardRestart()

                        } else {
                            Log.i("config_config", "updateconfig fail: reseting")
                            delay(2000)
                            serialPortController.hardRestart()
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
                }
                HubCommand.FM -> {
                    //onPlayForce(responseHub)
                }

                HubCommand.UPDATE_STATUS -> {
                    serialPortController.updateStatus()
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
//                    serialPortController.hardRestart()
                    webview.loadUrl("http://digitalweb.httt.vtcplay.vn/")
                }
                HubCommand.SWITCH_MODE_FM -> {
                  //  onPlayForce(responseHub)
                }
                HubCommand.SET_MUTE_DEVICE -> {
                    if (responseHub.message != null) {
                        serialPortController.setMuteDevice(responseHub.message!!)
                    }
                }
                HubCommand.SET_VOLUME_DEVICE -> {
                    if (responseHub.message != null) {
                        serialPortController.writeForceSetVolume(responseHub.message!!)
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

                HubCommand.GET_LOG_FILE -> {
                    val data = responseHub.message?.split('-') ?: return
                    logUtils.sendLogFile(data[0], data[1])
                }

            }

            if (command != HubCommand.PONG && command != HubCommand.UPDATE_STATUS && globalProperties.logEnabled) {
                logUtils.logHubCommand(command, responseHub.message ?: "")
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }


    private fun cancelAllAlarm() {
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

    companion object {
        var alarmListener: AlarmListener? = null
    }
}