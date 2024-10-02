package app.tek4tv.ipradio.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.HubComm
import app.tek4tv.ipradio.core.HubManager
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.core.players_helper.IPlayerManagerListener
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import app.tek4tv.ipradio.di.MoshiInst.moshi
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.network.LogService
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_ANDROID_VOLUME
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_VOLUME
import app.tek4tv.ipradio.utils.ConfigUtil.KEY_LIST_LEFT_PREF
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.TYPE_PRE_SCREEN_KEY
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.TYPE_SCREEN_KEY
import app.tek4tv.ipradio.utils.Utils.Companion.FULL_SCREEN
import app.tek4tv.ipradio.utils.Utils.Companion.MULTIPLE_AREA
import app.tek4tv.ipradio.utils.Utils.Companion.roundToNearestNumber
import app.tek4tv.ipradio.utils.network.FtpUtils
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import app.tek4tv.ipradio.viewmodels.MainViewModel
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.IOException
import java.lang.reflect.Type
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), AlarmListener {

    private val UPDATE_PERMISSION_REQUEST_CODE = 1

    //        Manifest.permission.ACCESS_FINE_LOCATION,
    //        Manifest.permission.ACCESS_COARSE_LOCATION,
    private val permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.REQUEST_INSTALL_PACKAGES,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_PHONE_STATE,
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

    private lateinit var mediaScheduler: MediaScheduler

    private lateinit var vlcVideoLayout: VLCVideoLayout

    private val globalProperties = GlobalProperties.getInst()
    private val UART_S1_NAME = "/dev/ttyS1"
    private val UART_S4_NAME = "/dev/ttyS4"
    private var typeScreen: String = MULTIPLE_AREA
    private lateinit var rootMultiLayout: ConstraintLayout
    private lateinit var ivBackgroundMultiLayout: ImageView
    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            serialPortController.turnOffPA()
            hubManager.curMediaName = ""
            hubManager.currentMediaItem = null
            Log.d("PMLOG", "startFirstTimeFrame: time over")
        }

        //Phát FM
        override fun onPlayTuner(mediaItem: MediaItem) {
            try {
                val tunerType = if (mediaItem.getMediaType() == MediaType.AM) "0" else "1"
                val pipsData = "${globalProperties.piCode},${globalProperties.psName}"
                val tunerData = "$tunerType,${mediaItem.body},${globalProperties.volume}"

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

                hubManager.sendLogMediaSuccess(mediaItem)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        //Dừng phát khẩn cấp
        override fun onEndForceMedia() {
            ForceMedia.deleteForeMediaPref(applicationContext)
            startPlayingMedia()

        }

        //Lỗi luồng live
        override fun onLiveError(mediaItem: MediaItem) {
            hubManager.curMediaName = "Live Error"
            hubManager.currentMediaItem = mediaItem

            serialPortController.turnOffPA()
        }


        override fun onMediaStopPlaying(mediaItem: MediaItem, duration: Long) {
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onMediaStopPlaying ")
        }

        override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onPlayMediaSuccess ")

            if (mediaItem != null) {
                hubManager.playlistIndex = playerManager.playlistIndex
                hubManager.currentMediaItem = mediaItem

                val displayName = mediaItem.title ?: ""
                hubManager.curMediaName = displayName

                if (mediaItem.getMediaType() != MediaType.FM) {
                    if (!serialPortController.isUart1) {
                        lifecycleScope.launch {
                            delay(11000)
                            serialPortController.turnOnPAVod()
                        }
                    } else {
                        serialPortController.turnOnPAVod()
                    }
                }
//                val paState =
//                    if (serialPortController.playMode != SerialPortController.MODE_NONE) "1"
//                    else "0"
                hubManager.sendLogMediaSuccess(mediaItem)
                if (globalProperties.logEnabled) {
                    try {
                        viewModel.currentMedia = mediaItem
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        typeScreen = ConfigUtil.getString(context = baseContext, TYPE_SCREEN_KEY, FULL_SCREEN) ?: "0"
        if (typeScreen == FULL_SCREEN) {
            setContentView(R.layout.activity_main)
        } else {
            setContentView(R.layout.activity_main_multiple_view2)
        }
        supportActionBar?.hide()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        if (needGrantPermission()) requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)


        val hubComm = object : HubComm {
            override fun forceRestartApp(countHardRestart : Int) {
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

        vlcVideoLayout = findViewById(R.id.vlc_video_layout)
        playerManager = PlayersManager(
            applicationContext,
            vlcVideoLayout,
            viewModel.mediaRepo
        )

        playerManager.playerManagerListener = playerCallback

        hubManager.hubEventObserver.add(playerManager)

        NetworkUtils.instance.startNetworkListener(this)

        initHubConnect()
        registerObservers()

        appStorageManager = AppStorageManager(applicationContext)


        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        alarmListener = this

        setMaxVolume()

        viewModel.hideSystemUI(this)

        initDataByScreen()


        checkDeleteFileUpdate()
        playerManager.resumeMedia()

    }
    private  fun checkDeleteFileUpdate()
    {
        val destination = getDestinationApk()
        val file = File(destination)
        if (file.exists()) {
            file.delete()
            Log.d("deletefile", "checkDeleteFileUpdate: delete success")
        }
        else
        {
            Log.d("deletefile", "checkDeleteFileUpdate: file not exists")
        }
    }


    private fun initDataByScreen() {
        if (typeScreen == FULL_SCREEN) {
            val tvMacAddress = findViewById<TextView>(R.id.tvMac)
            val info = "${Utils.getDeviceId(this)} - ${hubManager.versionName}"
            tvMacAddress.text = info
        } else {
            initDefaultTopAndLeftArea()
        }
    }


    private var listLeftMedia: MutableList<MediaItem> = mutableListOf()


    private fun initDefaultTopAndLeftArea() {
        rootMultiLayout = findViewById(R.id.root_multi_layout)
        ivBackgroundMultiLayout = findViewById(R.id.iv_bg_multi_layout)
        val videoViewContainer = findViewById<FrameLayout>(R.id.frame_player_layout)
        videoViewContainer.clipToOutline = true


    }

    override fun onStart() {
        Log.i("ChangeLayout", "Mess: recreate onStart")
        super.onStart()
        startPlayingMedia()
    }


    override fun onStop() {
        Log.e("monthlyinfo", "onStop")
        playerManager.stopAllPlayer()
        cancelAllAlarm()
        Log.i("ChangeLayout", "Mess: recreate stop")
        super.onStop()

    }

    override fun onDestroy() {
        Log.e("monthlyinfo", "onDestroy ")

        playerManager.onActivityDestroy()
//        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
        Log.i("ChangeLayout", "Mess: onDestroy")
        super.onDestroy()

    }

    override fun onAlarm(intent: Intent) {
        when (intent.action) {
            MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                playerManager.isInTimeFrame = true
                Log.d("onPlayMediaLog", "show typescreen ${typeScreen}")
                Log.d("alarmx", "start: $listKey")
                if (listKey != null) {
                    playerManager.setTimeFrame(listKey, null)
                    if (typeScreen == MULTIPLE_AREA) {

                        Log.d("onPlayMediaLog", "startFirstTimeFrame: showLeftArea")

                    }
                }
            }

            MediaScheduleReceiver.ACTION_END_PLAYLIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                playerManager.isInTimeFrame = false
                Log.d("alarmx", "end: $listKey")
                playerManager.stopPlaylist(listKey!!)
                if (typeScreen == MULTIPLE_AREA) {
                    Log.d("onPlayMediaLog", "show left ACTION_END_PLAYLIST ${typeScreen}")
                    Log.d("onPlayMediaLog", "hideTopLeftArea")
                }
            }

            MediaScheduleReceiver.ACTION_END_FORCE_MEDIA -> {
                playerManager.stopForceMedia()
                Log.d("alarmx", "stop force")
            }

            MediaScheduleReceiver.ACTION_RESCHEDULE -> {
                Log.d("alarmx", "reschedule all alarms")
                mediaScheduler.cancelAllScheduledListAlarm()

                viewModel.rescheduleMonthlyPlaylist()
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


    private fun needGrantPermission(): Boolean {
        var needGrantPermission = false

        permissions.forEach {
            if (checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED) {
                needGrantPermission = true
                return@forEach
            }
        }

//        if (!NetworkUtils.checkForNetworkStatPermission(applicationContext)) {
//            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
//            startActivity(intent)
//        }

        return needGrantPermission
    }

    private fun registerObservers() {
        /**
         * khi cần
         * **/
        viewModel.content.observe(this)
        {
            listLeftMedia.clear()
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

            listLeftMedia = playerManager.currentTextPlaylist.toMutableList()

            if (typeScreen == MULTIPLE_AREA) {
                Picasso.get()
                    .load("https://i.ibb.co/KjWTv5F/01.png")

                    .into(ivBackgroundMultiLayout)
//                if (listLeftMedia.isEmpty() && playerManager.isInTimeFrame) {
//                    Picasso.get()
//                        .load("https://brand-pcms.ggg.systems/media/catalog/product/cache/fccf9bc1c56510f6f2e84ded9c30a375/c/o/combo-khong-gioi-han_1_1.jpg")
//                        .fit()
//                        .into(ivBackgroundMultiLayout)
//                } else {
//                    Log.i("monthlyinfo", "Mess: left not show")
//                }

            }

            playerManager.startFirstTimeFrame()


            sendLogDataHub()


            //all load done, check
            viewModel.checkPlaylist()
        }
    }

    val type: Type = Types.newParameterizedType(
        List::class.java,
        MediaItem::class.java
    )

    fun startPlayingMedia() {
        viewModel.getPlayerContentWss()
    }

    private fun initHubConnect() {
        hubManager.connectToServer()
//        locationTracker.getLocation(this@MainActivity)

    }

    private fun handleFromCommandServer(command: String?, message: String?) {
        try {

            val responseHub = hubManager.responseHub

            when (command) {
                HubCommand.ROTATION -> {
                    if (responseHub.message != null) {
                        Log.d("rotation", responseHub.message!!)
                        try {
                            val requireOrientation = responseHub.message!!

                            ConfigUtil.putString(this, "pref_orientation", requireOrientation)
                            lifecycleScope.launch {
                                Toast.makeText(
                                    baseContext, "Màn hình sẽ xoay sau 5s",
                                    Toast.LENGTH_SHORT
                                ).show()
                                delay(5000)
                                CrashHandler.restartApp(MyApp.instance!!, this@MainActivity)
                            }

                        } catch (e: Exception) {
                            Log.e("orientation", "Error parsing orientation")
                        }
                    }
                }
                HubCommand.UPDATE_MONTHLY_WSS -> {

                    clearDatastore()
                    listLeftMedia.clear()

                    playerManager.stopAllPlayer()

                    playerManager.resetPlayMode()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    ForceMedia.clearMonthlyData(applicationContext)
                    Log.d("sds", "handleFromCommandServer: $message ")
                    if (message != null) {
                        viewModel.updateMonthlyScenarioWss(message)
                    }
                    val currentTime = Calendar.getInstance().time.time / 1000
                    ConfigUtil.putString(
                        applicationContext,
                        ConfigUtil.TIME_UPDATE_MONTHLY_SCENARIO,
                        currentTime.toString()
                    )
                    playerManager.stopForceMedia()
                    val preTypeScreen = ConfigUtil.getString(
                        context = baseContext,
                        TYPE_PRE_SCREEN_KEY,
                        "0"
                    )
                    val typeScreen = ConfigUtil.getString(
                        context = baseContext,
                        TYPE_SCREEN_KEY,
                        "0"
                    )
                    Log.d("spssds", "handleFromCommandServer: $preTypeScreen - $typeScreen ")
                    Handler().postDelayed({
                        if (typeScreen != preTypeScreen) {
                            Log.d("spssds", "handleFromCommandServer: diff")
                            ConfigUtil.putString(
                                context = baseContext,
                                TYPE_PRE_SCREEN_KEY,
                                typeScreen
                            )
                            recreate()
                        }
                    }, 1000)


                }
                HubCommand.DELETE_MONTHLY_LIST_WSS -> {
                    Log.d("sds", "handleFromCommandServer: $message ")

                    playerManager.stopAllPlayer()

                    playerManager.resetPlayMode()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    ForceMedia.clearMonthlyData(applicationContext)

                }


                HubCommand.UPDATE_MONTHLY_LIST -> {
                    clearDatastore()
                    val mess = responseHub.message!!
//                    playerManager.stopAllPlayer()
                    playerManager.resetPlayMode()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
//                    viewModel.updateMonthlyScenario(mess, true)
                    ConfigUtil.putString(applicationContext, ConfigUtil.MONTHLY_SCENARIO_URL, mess)
                    // lưu thời gian cập nhật lịch để gửi log
                    val currentTime = Calendar.getInstance().time.time / 1000
                    ConfigUtil.putString(
                        applicationContext,
                        ConfigUtil.TIME_UPDATE_MONTHLY_SCENARIO,
                        currentTime.toString()
                    )
                    playerManager.stopForceMedia()
                    //delete pref list left
                    ConfigUtil.removeSharef(baseContext, KEY_LIST_LEFT_PREF)
                    Handler().postDelayed({
                        recreate()
                    }, 1000)
                }
                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {

//                    ConfigUtil.putString(baseContext, TYPE_SCREEN_KEY, FULL_SCREEN)
//                    ConfigUtil.putString(baseContext, TYPE_PRE_SCREEN_KEY, typeScreen)
//
//                    Handler().postDelayed({
//                        onPlayForce(responseHub)
//                        if(typeScreen != FULL_SCREEN) {
//                            recreate()
//                        }
//                    }, 1000)
                    onPlayForce(responseHub)
                }


                HubCommand.LiveRemoteControl ->{
                    val mess = responseHub.message!!
                    Log.i("LiveRemoteControl", "Mess: $mess ")
                }


                HubCommand.TURN_ON_PA -> {
                    if (responseHub.message == "1") {
                        serialPortController.turnOffPA()
                    } else {
                        serialPortController.turnOnPAVod()
                    }
                }

                HubCommand.CONTROL_MEDIA -> {
                    //0,1,2,3
                    //{"IMEI":"18:93:7F:E5:F5:77","Message":"0"}
                    Log.i("CONTROL_MEDIA", responseHub.message!!)
                    val mess = responseHub.message!!.toInt()
                    when(mess)
                    {
                        //dung phat
                        0 -> {
                            playerManager.stopAllPlayer()
                            playerManager.resetPlayMode()
                        }
                        //dung phat va chuyen sang ban tin tiep
                        1 -> {
                            if(playerManager.isInTimeFrame)
                            {
                                playerManager.playNextMedia()
                            }
                        }
                        //Tạm dừng nội dung đang phát
                        2 ->{
//                            playerManager.pauseMedia()
                        }
                        3 ->{
                            playerManager.resumeMedia()
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
//                                hubManager.sendHubDirectMessage(
//                                    connectionId,
//                                    HubCommand.PLAYLIST_INFO,
//                                    dataFromPlaylist
//                                )
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
                        Log.d("UpdateVolume", "handleFromCommandServer: ${responseHub.message}")
                        if (responseHub.message == "1")//tat loa mute
                        {
                            changeVolume(0)
                            globalProperties.setTempVolume("0")
                            globalProperties.setAndroidPreVolume("0")
                        } else {
                            changeVolume(
                                (ConfigUtil.getString(
                                    baseContext,
                                    DEFAULT_ANDROID_VOLUME,
                                    "10"
                                ) ?: "10").toInt()
                            )
                            globalProperties.setAndroidPreVolume(roundToNearestNumber(responseHub.message!!.toInt()).toString())
                            globalProperties.setTempVolume(
                                (ConfigUtil.getString(
                                    baseContext,
                                    DEFAULT_VOLUME,
                                    "100"
                                ) ?: "100")
                            )
                            Log.d(
                                "UpdateVolume",
                                "handleFromCommandServer: ${globalProperties.androidVolume.toInt()}"
                            )
                        }
                    }
                }
                HubCommand.SET_VOLUME_DEVICE -> {
//                    if (responseHub.message != null) {
//                        serialPortController.writeForceSetVolume(responseHub.message!!)
//                        globalProperties.setTempVolume(responseHub.message!!)
//                    }
                    if (responseHub.message != null) {
                        changeVolume(roundToNearestNumber(responseHub.message!!.toInt()))
                        globalProperties.setAndroidPreVolume(roundToNearestNumber(responseHub.message!!.toInt()).toString())
                        globalProperties.setTempVolume(responseHub.message!!.toString())
                        ConfigUtil.putString(
                            applicationContext,
                            DEFAULT_ANDROID_VOLUME,
                            roundToNearestNumber(responseHub.message!!.toInt()).toString()
                        )
                        ConfigUtil.putString(
                            applicationContext,
                            DEFAULT_VOLUME,
                            responseHub.message!!
                        )

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
//                        hubManager.sendHubDirectMessage(connectionId, Utils.NETWORK_INFO, result)
                    }
                }

                HubCommand.STORAGE_INFO -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        val info = appStorageManager.run {
                            "${getTotalRam()},${getUsedRam()},${getTotalRomStorage()},${getUsedRomStorage()}"
                        }

//                        hubManager.sendHubDirectMessage(connectionId, Utils.STORAGE_INFO, info)
                    }
                }

                HubCommand.GET_MEDIA_PATH -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        Log.d("connectionId", connectionId)
                        val mediaList = Utils.toJsonList(moshi, appStorageManager.getAllMediaPath())
//                        hubManager.sendHubDirectMessage(
//                            connectionId, Utils.GET_MEDIA_PATH, mediaList
//                        )
                    }
                }

                HubCommand.DELETE_ALL_MEDIA -> {
                    appStorageManager.deleteAllMedia()
                }

                HubCommand.GET_RECORD -> {
                    val connectionId = responseHub.message!!
                    val recordedAudioList =
                        Utils.toJsonList(moshi, appStorageManager.getAllRecordedAudioPath())
//                    hubManager.sendHubDirectMessage(
//                        connectionId, Utils.GET_RECORD,
//                        recordedAudioList
//                    )

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

//                        hubManager.sendEncryptedMessage(
//                            connectionId, Utils.ENCRYPTED_STORAGE_INFO, info
//                        )
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
//                    viewModel.getPlayerContent("", false)
                }

                HubCommand.GET_LOG_FILE -> {
                    val data = responseHub.message?.split('-') ?: return
//                    logUtils.sendLogFile(data[0], data[1])
                }
            }
            if (command != HubCommand.PONG && command != HubCommand.UPDATE_STATUS && globalProperties.logEnabled) {
                if (command != null) {
                    logUtils.logHubCommand(command, responseHub.message ?: "")
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }
    private fun changeVolume(volumeValue: Int)
    {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeValue, 0)
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
//        Log.d("UpdateVolume", "set max: ${GlobalProperties.getInst().androidVolume}")
//        changeVolume(GlobalProperties.getInst().androidVolume.toInt())
    }
    private fun clearDatastore()
    {
        //limit free data 1 gb
//        val limit = 1000000000
        //limit free data 500 mb
        val limit = 524288000
        val totalStorage  = appStorageManager.getTotalRomStorage()
        val usedStorage = appStorageManager.getUsedRomStorage()
        if(totalStorage - usedStorage <= limit)
        {
            Log.d("clearDatastore", "clearDatastore: Must Clear")
            appStorageManager.deleteAllMedia()
        }
        else
        {
            Log.d("clearDatastore", "clearDatastore: Dont have to clear")
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

    companion object {
        var alarmListener: AlarmListener? = null
        val typeListMediaItem = Types.newParameterizedType(List::class.java, MediaItem::class.java)
        val jsonListMediaItemAdapter = moshi.adapter<List<MediaItem>>(typeListMediaItem)

        val typeMap =
            Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        val jsonMapAdapter: JsonAdapter<Map<String, String>> = moshi.adapter<Map<String, String>>(typeMap)
    }
}
