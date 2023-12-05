package app.tek4tv.ipradio.ui

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
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
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.PLAY_MODE_FORCE
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.network.LogService
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.CrashHandler.Companion.restartApp
import app.tek4tv.ipradio.utils.Utils.Companion.convertFileToBase64
import app.tek4tv.ipradio.utils.Utils.Companion.formatDuration
import app.tek4tv.ipradio.utils.Utils.Companion.sendSms
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
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
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

    private lateinit var rootLayout: FrameLayout

    private val globalProperties = GlobalProperties.getInst()
    private val UART_S1_NAME = "/dev/ttyS1"
    private val UART_S4_NAME = "/dev/ttyS4"

    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            Log.d("loghub", "onTimeOver: come")
            serialPortController.turnOffPA()
            serialPortController.writeForceSetVolume(globalProperties.volume)
            hubManager.curMediaName = ""
            hubManager.currentMediaItem = null
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
                if (!serialPortController.isUart1) {
                    lifecycleScope.launch {
                        delay(11000)
                        serialPortController.turnOnPAVod()
                    }
                } else {
                    serialPortController.turnOnPAVod()
                }
            }
            val paState =
                if (serialPortController.playMode != SerialPortController.MODE_NONE) "1"
                else "0"
            logUtils.logMediaStart(mediaItem!!, 0, globalProperties.volume, paState)
            if (globalProperties.logEnabled) {
                try {
                    viewModel.currentMedia = mediaItem
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

       // Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        if (needGrantPermission()) requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)

        val hubComm = object : HubComm {
            override fun forceRestartApp() {
                lifecycleScope.launch {
                    serialPortController.turnOffPA()
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
        //logUtils.logStartApp()
        mediaCapture = MediaCapture(applicationContext, lifecycleScope)

        hubManager = HubManager(hubComm, authService, mediaCapture) { command, message ->
            handleFromCommandServer(command, message)
        }
        hubManager.logService = logService

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
//        sendSms("191","KTTK",baseContext)
        hideSystemUI()
        showMac()
        lifecycleScope.launch {
            while (true) {
                Log.d("PingCounter check", "onCreate: ping count ${hubManager.pingCounter}")
                if (hubManager.pingCounter >= hubManager.CONNECT_COUNTER_LIMIT) {
                    Log.d("PingCounter check", "onCreate: ping count reset")
                    serialPortController.hardRestart();
                }
                delay(5000)
            }
        }
//        var textDe = "APxss/POTv3TP4AolIddVbeDEi0p/tO0LvwufzbWggFan+exmwlGrsm1xk56niS2n8Fn2oV9FdW0zM1LErMywnqY6+cVPJmexrkOD9yoWwR6tPGKndHP9se2bcXPQoplsD+JMmVvQONwweRNmp214p77gcQswvJc2VX6mP/Vtz28QWl265ZCXx30fwotIpJAt/UlQZNgvxRNne12TC7kPG/Bp+pew8Fgz2cNc1YMgb3A17dm3zVUM2ojOK3lA7en5AUTUlGPdO/XEDzXHyEPvH3Xr4gZNMf7VlziSyQRuhVjeOJgAdJMVYL9AMRDczbNFDky3zFGpF+Pxuka9zZD1SLyPR3YS+b/jimbhdk9jz8ste1EzW/nTMYNxhuCRuHbrGW4+b4GCHlJZKDsDHa7/T6IHITrkxvjmqL69algz38yvAF2YpRTuVau9Lm2R1fNcdliSjLUu7IZja/oGt8m8rA45HwGXqU1eff9+d6+Nxi42TOQlOGcSTLidtnZddZj"
//        val data = decryptData(
//            textDe,
//            "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"
//        )
//        Log.d("giai ma", "onCreate: $data");
        //recored()
        //convert()
        //readSms(baseContext)
    }
    fun readSms(context: Context): String? {
        val contentResolver: ContentResolver = context.contentResolver
        val uri: Uri = Uri.parse("content://sms/inbox")
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, "date DESC LIMIT 1")

        cursor?.use { c ->
            if (c.moveToFirst()) {
                val body: String = c.getString(c.getColumnIndexOrThrow("body"))
                val address: String = c.getString(c.getColumnIndexOrThrow("address"))

                Log.d("SmsReader", "Address: $address, Body: $body")
                return body
            }
        }
        return null
    }
    private fun showMac() {

        val tvMacAdress = findViewById<TextView>(R.id.tvMac)
        val imei = Utils.getDeviceId(this)
        tvMacAdress.text = "$imei - Ver: ${hubManager.versionName}"
        tvMacAdress.setOnClickListener{
            sendSms("191","KTTK",baseContext)
            Log.d("SmsReceiver", "Click")
        }
    }
    fun sendSMS()
    {  Log.d("SmsReceiver", "Click")
        sendSms("191","KTTK",baseContext)
        Log.d("SmsReceiver", "Click")
    }


    override fun onStop() {
        super.onStop()
        playerManager.onActivityStop()
        cancelAllAlarm()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.onActivityDestroy()
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

                viewModel.updateMonthlyScenario(monthlyScenarioUrl,false)
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
        viewModel.content.observe(this)
        {
            Log.d("MainLog", "registerObservers: ${it}")
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

            //check hub
            mediaScheduler.setCheckHubAndResetAlarms()

            playerManager.startFirstTimeFrame()

            //all load done, check
            viewModel.checkPlaylist()
        }
    }

    fun startPlayingMedia() {
        viewModel.getPlayerContent("", false)
    }

    private fun initHubConnect() {
        hubManager.connectToHub()
       // locationTracker.getLocation(this@MainActivity)
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
                    val mess = responseHub.message!!
                    //add at 4.1.3a
                    playerManager.stopForceMedia()
                    //add at 4.1.3d
                    playerManager.stopTimeFrame()
                    //add at 3.0.5
                    playerManager.stopAllPlayer()
                    playerManager.resetPlayMode()
                    //lưu thông số vol, pi,...
                    globalProperties.saveProperties(responseHub)
                    if(responseHub.volume != ""){
                        serialPortController.writeForceSetVolume(responseHub.volume)
                        globalProperties.setTempVolume(responseHub.volume)
                    }
                    else
                    {
                        serialPortController.writeForceSetVolume("100")
                    }
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)

                    viewModel.getPlayerContent(mess, true)
                }

                HubCommand.UPDATE_MONTHLY_LIST -> {
                    val mess = responseHub.message!!
                    //add at 4.1.3b
                    playerManager.stopForceMedia()
                    //  //add at 4.1.3d
                    playerManager.stopTimeFrame()
                    playerManager.stopAllPlayer()
                    playerManager.resetPlayMode()

                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    viewModel.updateMonthlyScenario(mess, false)
                    ConfigUtil.putString(applicationContext, ConfigUtil.MONTHLY_SCENARIO_URL, mess)


                }

                HubCommand.UPDATE_DESTINATION -> {
                    val mess = responseHub.message!!
                    Log.i("config_config", "updateconfig $mess")
                    val listInfoConfig = mess.split(",")
                    val resposnHub = ResponseHub().apply {
                        if(listInfoConfig.isNotEmpty()) {
                            volume = listInfoConfig[0]
                        }
                        if(listInfoConfig.size >= 2) {
                            lat = listInfoConfig[1].toFloat()
                        }
                        if(listInfoConfig.size >= 3) {
                            lng = listInfoConfig[2].toFloat()
                        }
                        if(listInfoConfig.size >= 4) {
                            destinationId = listInfoConfig[3]
                        }
                        if(listInfoConfig.size >= 5) {
                            destinationName = listInfoConfig[4]
                        }
                        if(listInfoConfig.size >= 6) {
                            deviceName = listInfoConfig[5]
                        }
                    }
                    if(listInfoConfig.size >= 7 )
                    {
                        ConfigUtil.putStringEncrypt(
                            applicationContext,
                            ConfigUtil.PROVIDER,
                            listInfoConfig[6]
                        )
                        hubManager.providerValue = listInfoConfig[6]
                    }
                    globalProperties.saveProperties(resposnHub)
                    Log.i("config_config", "updateconfig ${resposnHub.lat}")
                    Log.i("config_config", "updateconfig ${resposnHub.lng}")
                    Log.i("config_config", "updateconfig success")

                }


                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {
                    onPlayForce(responseHub)
                }
                HubCommand.UPDATE_CONFIG -> {
                    //{"IMEI":"18:93:7F:E5:F5:77","Message":"https://hub.gateway.httt.tek4tv.vn/hubServer,https://api.gateway.httt.tek4tv.vn/api/auth/login"}
                    Log.i("UPDATE_CONFIG be", responseHub.message!!)
                    val mess = responseHub.message!!
                    val listValue = mess.split(",")
                    val urlHub = listValue[0]
                    val urlToken = listValue[1]
                    if(listValue.size >= 3 )
                    {
                        ConfigUtil.putStringEncrypt(
                            applicationContext,
                            ConfigUtil.PROVIDER,
                            listValue[2]
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
                HubCommand.TURN_ON_PA -> {
                    Log.i("CONTROL_MEDIA", responseHub.message!!)
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
                    Log.i("CONTROL_MEDIA", responseHub.message!!)
                    val mess = responseHub.message!!.toInt()
                    if(playerManager.playMode != PLAY_MODE_FORCE ) {
                        when (mess) {
                            //dung phat
                            0 -> {
                                playerManager.stopAllPlayer()
                                playerManager.resetPlayMode()
                            }
                            //dung phat va chuyen sang ban tin tiep
                            1 -> {
                                Log.d("CONTROL_MEDIA", "isInTimeFrame: ${playerManager.isInTimeFrame}")
                                if (playerManager.isInTimeFrame) {
                                    playerManager.playNextMedia()
                                }
                            }
                            //Tạm dừng nội dung đang phát
                            2 -> {
                                playerManager.pauseMedia()
                            }
                            //Phát tiếp nội dung dừng trước đó
                            3 -> {
                                if (playerManager.played) {
                                    playerManager.resumeMedia()
                                } else {
                                    playerManager.startFirstTimeFrame()
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
                    val message = responseHub.message
                    val listMess = message?.split(",")
                    val duration = listMess?.firstOrNull()
                    val userId = listMess?.lastOrNull()
                    if(duration!!.isNotEmpty() && userId!!.isNotEmpty()) {
                        val fileName = "RecordLog.aac"
                        var dataBase64 = ""
                        Log.i("RecoredLog", "record....${mediaCapture.isRecordingAudio} ")
                        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
                        mediaCapture.startCaptureAudio(fileName)
                        val path = "${filesDir.path}/recorded/$fileName"
                        Timer().schedule(object : TimerTask() {
                            override fun run() {
                                mediaCapture.stopAudioCapture()
                                Log.i("RecoredLog", "stop record.... ")
                                lifecycleScope.launch {
                                    delay(2000)
                                    dataBase64 = convertFileToBase64(path) ?: ""
                                    val recordLog = RecordLogModel(
                                        data = dataBase64,
                                        duration = formatDuration((duration.toLong())),
                                        userID = userId ?: "",
                                        extension = "aac",
                                    )
                                    try {
                                        logService.postRecordLog(recordLog)
                                        Log.d(
                                            "RecoredLog",
                                            "${recordLog}"
                                        )
                                    } catch (e: Exception) {
                                        Log.d("RecoredLog", "Error send log media ${e.message}")
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }, duration!!.toLong() * 1000)
                    }
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
//        try {
//            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
//            val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
//            audio.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
//        } catch (e: Exception) {
//            Log.e("Main", "Error setting max volume")
//        }
    }

    companion object {
        var alarmListener: AlarmListener? = null
    }
}