package app.tek4tv.ipradio.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.size
import androidx.lifecycle.lifecycleScope
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.HubComm
import app.tek4tv.ipradio.core.HubManager
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.core.players_helper.IPlayerManagerListener
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.model.ResponseHub
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
import app.tek4tv.ipradio.utils.media.MediaCapture
import app.tek4tv.ipradio.utils.network.FtpUtils
import app.tek4tv.ipradio.utils.network.LocationTracker
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import app.tek4tv.ipradio.viewmodels.MainViewModel
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.TransportEnum
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.IOException
import java.lang.reflect.Type
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

    private lateinit var vlcVideoLayout: VLCVideoLayout

    private val globalProperties = GlobalProperties.getInst()
    private val UART_S1_NAME = "/dev/ttyS1"
    private val UART_S4_NAME = "/dev/ttyS4"
    private var typeScreen: Int = FULL_SCREEN
    private lateinit var tvTopArea: TextView
    private lateinit var tvTitleRight: TextView
    private lateinit var scrollLeftArea: ScrollView
    private lateinit var layoutTvLeft: LinearLayout
    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            serialPortController.turnOffPA()
            hubManager.curMediaName = ""
            hubManager.currentMediaItem = null
            Log.d("PMLOG", "startFirstTimeFrame: time over")
        }

        //Bắt đầu khung thời gian
        override fun onStartTimeFrame(frame: String) {
//            if (globalProperties.logEnabled) {
//                logUtils.logStartTimeFrame(frame)
//            }
            Log.d("onPlayMediaLog", "startFirstTimeFrame: onStartTimeFrame")
            if (typeScreen == MULTIPLE_AREA) {
                showLeftArea()
            }
        }

        //Kết thúc khung gian
        override fun onEndTimeFrame(frame: String) {
//            if (globalProperties.logEnabled) {
//                logUtils.logEndTimeFrame(frame)
//            }
            if (typeScreen == MULTIPLE_AREA) {
                hideTopLeftArea(true)
            }
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onMediaEndReach ")
            val sdf = SimpleDateFormat("dd/M/yyyy")
            val currentDate = sdf.format(Date())
            val currentDateStr = currentDate.replace("/", "")
//            logUtils.sendLogFile("media", currentDateStr)
            Log.d("screeninfo", "showMac: nEndTimeFrame")
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

                logUtils.logMediaStart(mediaItem, 0, globalProperties.volume, paState,typeScreen)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        //Dừng phát khẩn cấp
        override fun onEndForceMedia() {
            ForceMedia.deleteForeMediaPref(applicationContext)
            val oldTypeScreen = ConfigUtil.getInt(
                            context = baseContext,
                            TYPE_PRE_SCREEN_KEY,
                            FULL_SCREEN
                        )
            ConfigUtil.putInt(baseContext, TYPE_SCREEN_KEY, oldTypeScreen)

            startPlayingMedia()
            if(oldTypeScreen != typeScreen)
            {
                recreate()
            }

//            logUtils.logMediaEnd(mediaItem!!)

        }

        //Lỗi luồng live
        override fun onLiveError(mediaItem: MediaItem) {
            hubManager.curMediaName = "Live Error"
            hubManager.currentMediaItem = mediaItem

            serialPortController.turnOffPA()
        }

        override fun onMediaEndReach(mediaItem: MediaItem) {
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onMediaEndReach ")
        }

        override fun onMediaStopPlaying(mediaItem: MediaItem, duration: Long) {
//            logUtils.logMediaEnd(mediaItem)

            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onMediaStopPlaying ")
        }

        override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onPlayMediaSuccess ")

            if (mediaItem != null) {
                hubManager.playlistIndex = playerManager.playlistIndex
                hubManager.currentMediaItem = mediaItem

                val displayName = mediaItem?.title ?: ""
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
                val paState =
                    if (serialPortController.playMode != SerialPortController.MODE_NONE) "1"
                    else "0"
                logUtils.logMediaStart(mediaItem, 0, globalProperties.volume, paState,typeScreen)
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
        typeScreen = ConfigUtil.getInt(context = baseContext, TYPE_SCREEN_KEY, FULL_SCREEN)
        if (typeScreen != FULL_SCREEN) {
            setContentView(R.layout.activity_main)
        } else {
            setContentView(R.layout.activity_main_multiple_view2)
        }
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

        mediaCapture = MediaCapture(applicationContext, lifecycleScope)

        hubManager = HubManager(hubComm, authService, mediaCapture) { command, message ->
            handleFromCommandServer(command, message)
        }
        hubManager.logService = logService

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
//
//        initDataByScreen()


    }



    private fun initDataByScreen() {
        if (typeScreen == FULL_SCREEN) {
            val tvMacAdress = findViewById<TextView>(R.id.tvMac)
            val imei = Utils.getDeviceId(this)
            tvMacAdress.text = "$imei - Ver: ${hubManager.versionName}"
        } else {
            initDefaultTopAndLeftArea()
        }
    }


    private fun startAutoScroll() {
        val runnable: Runnable = object : Runnable {
            override fun run() {
                // Scroll the ScrollView vertically
                scrollLeftArea.scrollBy(0, scrollSpeed)

                // Check if reached the bottom
                if (scrollLeftArea.scrollY >= scrollLeftArea.getChildAt(0)
                        .height - scrollLeftArea.height
                ) {
                    // val typeface = resources.getFont(R.font.verdanabold)
                    listLeftMedia.forEach {
                        val textView = TextView(baseContext)
                        textView.text = it.body
                        textView.setTextColor(Color.parseColor("#F2FB0A"))
                        textView.textSize = tvTitleRight.textSize / resources.displayMetrics.density
//                        textView.typeface = typeface
                        listLeftTextView.add(textView)
                        layoutTvLeft.addView(textView)
                    }
                    //if(layoutTvLeft.size >= listLeftMedia.size*3)
                    if (layoutTvLeft.size >= 30) {
                        layoutTvLeft.removeAllViews()
                        handler.removeCallbacksAndMessages(null)
                        showLeftArea()
                    }
                }

                handler.postDelayed(this, 50) // Adjust the delay as needed
            }
        }
        handler.postDelayed(runnable, 2000) // Delay before starting the auto-scroll
    }

    private fun showLeftArea() {
        if (listLeftMedia.isEmpty()) return
        Log.d("monthlyinfo", "show left")
        //   val typeface = resources.getFont(R.font.verdanabold)
        listLeftMedia.forEach {
            val textView = TextView(baseContext)
            textView.text = it.body
            textView.setTextColor(Color.parseColor("#F2FB0A"))
            textView.textSize = tvTitleRight.textSize / resources.displayMetrics.density
            //   textView.typeface = typeface
            listLeftTextView.add(textView)
            layoutTvLeft.addView(textView)
        }

        scrollLeftArea.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                startAutoScroll()
                scrollLeftArea.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }


    private fun hideTopLeftArea(isStillTimeFrame : Boolean) {
        if(typeScreen != FULL_SCREEN ) {
            layoutTvLeft.removeAllViews()
            handler.removeCallbacksAndMessages(null)
            if (!isStillTimeFrame) {
                listLeftMedia.clear()
                ConfigUtil.removeSharef(baseContext, KEY_LIST_LEFT_PREF)
                Log.e("monthlyinfo", "Hub hide left")
            }
        }
    }

    private val scrollSpeed = 1
    private val handler: Handler = Handler()
    private var listLeftMedia: MutableList<MediaItem> = mutableListOf()
    private var listLeftTextView = mutableListOf<TextView>()

    private fun initDefaultTopAndLeftArea() {
        scrollLeftArea = findViewById(R.id.scrollView)
        tvTopArea = findViewById(R.id.tvTopArea)
        tvTitleRight = findViewById<TextView>(R.id.tvTitleRight)
        tvTopArea.isSelected = true
        layoutTvLeft = findViewById(R.id.layoutTvLeft)
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

        handler.removeCallbacksAndMessages(null)
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
                Log.d("alarmx", "start: $listKey")
                if (listKey != null) {
                    playerManager.setTimeFrame(listKey, null)
                }
            }

            MediaScheduleReceiver.ACTION_END_PLAYLIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                playerManager.isInTimeFrame = false
                Log.d("alarmx", "end: $listKey")
                playerManager.stopPlaylist(listKey!!)
                mediaScheduler.scheduleShowListLeft()
                mediaScheduler.scheduleEndLeftArea()
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
            viewModel.downloadMedias()
            //Check xem có đang phát khẩn cấp không
            //Nếu có thì phát media khẩn cấp
            //Media khẩn cấp lưu share pref
            if (ForceMedia.hasValidForceMedia(applicationContext)) {
//                mediaScheduler.scheduleForceMediaAlarm()
                val forceMedia = ForceMedia.getLocalForceMedia(applicationContext)
                Log.i("Changelayout", "Mess: local ${forceMedia?.mediaItem}")
                if (forceMedia != null)
                    playerManager.playForceMedia(
                        forceMedia,
                        mediaScheduler,
                        true
                    )
                return@observe
            }
            //default
            mediaScheduler.scheduleAllAlarms()
            //check hub
            mediaScheduler.setCheckHubAndResetAlarms()

            playerManager.startFirstTimeFrame()

            if (typeScreen == MULTIPLE_AREA) {
                listLeftMedia = viewModel.mediaRepo.leftScheduledList
                if (listLeftMedia.isEmpty()) {
                    val mediaJsonAdapter: JsonAdapter<List<MediaItem>> =
                        moshi.adapter(type)
                    val strList = ConfigUtil.getString(baseContext, KEY_LIST_LEFT_PREF, "") ?: ""
                    if (strList != "") {
                        listLeftMedia = mediaJsonAdapter.fromJson(strList)
                            ?.filter { it.body != null && it.body != "start" && it.body != "end" } as MutableList<MediaItem>
                        Log.i("monthlyinfo", "Mess: local ")
                    }
                } else {
                    Log.i("monthlyinfo", "Mess: online ")
                }

            }

            //all load done, check
            viewModel.checkPlaylist()
        }
    }

    val type: Type = Types.newParameterizedType(
        List::class.java,
        MediaItem::class.java
    )

    fun startPlayingMedia() {
        viewModel.getPlayerContent("", false)
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
                    val mess = responseHub.message!!
                    playerManager.stopForceMedia()
                    playerManager.stopTimeFrame()
                    //add at 3.0.5
                    playerManager.stopAllPlayer()
                    playerManager.resetPlayMode()

                    //lưu thông số vol, pi,...
                    globalProperties.saveProperties(responseHub)

                    serialPortController.writeForceSetVolume(responseHub.volume)

                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)

                    viewModel.getPlayerContent(mess, true)
                }

                HubCommand.UPDATE_MONTHLY_LIST -> {
                    clearDatastore()
                    val mess = responseHub.message!!
                    playerManager.stopTimeFrame()
//                    playerManager.stopAllPlayer()
                    playerManager.resetPlayMode()
                    hideTopLeftArea(false)
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    viewModel.updateMonthlyScenario(mess, true)
                    ConfigUtil.putString(applicationContext, ConfigUtil.MONTHLY_SCENARIO_URL, mess)
                    playerManager.stopForceMedia()
                    //delete pref list left
                    ConfigUtil.removeSharef(baseContext, KEY_LIST_LEFT_PREF)
                    Handler().postDelayed({
                        recreate()
                    }, 1000)
                }
                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {

                    ConfigUtil.putInt(baseContext, TYPE_SCREEN_KEY, (responseHub.mode ?: "0").toInt())
                    ConfigUtil.putInt(baseContext, TYPE_PRE_SCREEN_KEY, typeScreen)

                    Handler().postDelayed({
                        onPlayForce(responseHub)
                        recreate()
                    }, 1000)
                }


                HubCommand.UPDATE_DESTINATION -> {
                    val mess = responseHub.message!!
                    Log.i("config_config", "updateconfig $mess")

                    val listInfoConfig = mess.split(",")
                    val resposnHub = ResponseHub().apply {
                        if (listInfoConfig.isNotEmpty() && listInfoConfig[0] != "null") {
                            volume = listInfoConfig[0]
                        }
                        if (listInfoConfig.size >= 2 && listInfoConfig[1] != "null") {
                            lat = listInfoConfig[1].toFloat()
                        }
                        if(listInfoConfig.size >= 3 && listInfoConfig[2] != "null") {
                            lng = listInfoConfig[2].toFloat()
                        }
                        if(listInfoConfig.size >= 4 && listInfoConfig[3] != "null") {
                            destinationId = listInfoConfig[3]
                        }
                        if(listInfoConfig.size >= 5  && listInfoConfig[4] != "null") {
                            destinationName = listInfoConfig[4]
                        }
                        if(listInfoConfig.size >= 6 && listInfoConfig[5] != "null") {
                            deviceName = listInfoConfig[5]
                        }
                    }
                    if(listInfoConfig.size >= 7 && listInfoConfig[6] != "null" )
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


                HubCommand.LiveRemoteControl ->{
                    val mess = responseHub.message!!
                    Log.i("LiveRemoteControl", "Mess: $mess ")
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
                            delay(10000)
                            CrashHandler.restartApp(MyApp.instance!!, this@MainActivity)

                        } else {
                            Log.i("config_config", "updateconfig fail: reseting")
                            delay(2000)
                            serialPortController.hardRestart()
                            CrashHandler.restartApp(MyApp.instance!!, this@MainActivity)
                        }

                    }

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
                            playerManager.pauseMedia()
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
//                    logUtils.sendLogFile(data[0], data[1])
                }
            }
            if (command != HubCommand.PONG && command != HubCommand.UPDATE_STATUS && globalProperties.logEnabled) {
                logUtils.logHubCommand(command, responseHub.message ?: "")
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

    companion object {
        var alarmListener: AlarmListener? = null
    }
}