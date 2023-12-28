package app.tek4tv.ipradio.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
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
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.TYPE_SCREEN_KEY
import app.tek4tv.ipradio.utils.Utils.Companion.FULL_SCREEN
import app.tek4tv.ipradio.utils.Utils.Companion.MULTIPLE_AREA
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
import org.videolan.libvlc.util.VLCVideoLayout
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

    private lateinit var vlcVideoLayout: VLCVideoLayout

    private val globalProperties = GlobalProperties.getInst()
    private val UART_S1_NAME = "/dev/ttyS1"
    private val UART_S4_NAME = "/dev/ttyS4"
    private var typeScreen: Long = MULTIPLE_AREA
    private lateinit var tvTopArea: TextView

    //    private lateinit var tvRightArea: TextView
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
            if (globalProperties.logEnabled) {
                logUtils.logStartTimeFrame(frame)
            }
        }

        //Kết thúc khung gian
        override fun onEndTimeFrame(frame: String) {
            if (globalProperties.logEnabled) {
                logUtils.logEndTimeFrame(frame)
            }
            val sdf = SimpleDateFormat("dd/M/yyyy")
            val currentDate = sdf.format(Date())
            val currentDateStr = currentDate.replace("/", "")
            logUtils.sendLogFile("media", currentDateStr)
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
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onMediaStopPlaying ")
        }

        override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onPlayMediaSuccess ")
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
           // logUtils.logMediaStart(mediaItem!!, 0, globalProperties.volume, paState)
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
       // typeScreen = ConfigUtil.getLong(context = baseContext, TYPE_SCREEN_KEY, FULL_SCREEN)
        if(typeScreen == FULL_SCREEN )
        {
            setContentView(R.layout.activity_main)
        }
        else
        {
            setContentView(R.layout.activity_main_multiple_view2)
            initDefaultTopAndLeftArea()
        }
        supportActionBar?.hide()
        //Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

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
        serialPortController.connectToSerialPort("/dev/ttyS4")
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

        hideSystemUI()
        showMac()

        getMonth()
        convertPixelsToDp(20,baseContext)
    }
    fun getMonth()
    {
        playerManager.stopAllPlayer()
        cancelAllAlarm()
        ForceMedia.deleteForeMediaPref(applicationContext)
        viewModel.updateMonthlyScenario("https://iotgateway.tek4tv.vn/api/AsyncDigital/v2/8340a56a-9d80-492b-bb70-d88974a0472b", true)
        ConfigUtil.putString(applicationContext, ConfigUtil.MONTHLY_SCENARIO_URL, "https://iotgateway.tek4tv.vn/api/AsyncDigital/v2/8340a56a-9d80-492b-bb70-d88974a0472b")
    }

//    private fun convertPixelsToDp(px: Int, context: Context): Int {
//        return px / (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT).toInt()
//    }

    fun convertPixelsToDp(px: Int, context: Context): Float {
        val h = Resources.getSystem().displayMetrics.heightPixels
        val w = Resources.getSystem().displayMetrics.widthPixels
        Log.d("convertPixelsToDp", "convertPixelsToDp: ${w}-${h}")
        val density = context.resources.displayMetrics.density
        Log.d("convertPixelsToDp", "convertPixelsToDp: ${density}")
        return (px / density)
    }

    private fun showMac() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels
        Log.d("screeninfo", "showMac: $height")
        Log.d("screeninfo", "showMac: $width")

        if(typeScreen != FULL_SCREEN) {

        }

        else{
            val tvMacAdress = findViewById<TextView>(R.id.tvMac)
            val imei = Utils.getDeviceId(this)
            tvMacAdress.text = "$imei - Ver: ${hubManager.versionName}"
            tvMacAdress.setOnClickListener{

                Log.d("dpiPoint", "showMac: click")
                if (typeScreen != FULL_SCREEN) {
                    ConfigUtil.putLong(baseContext, TYPE_SCREEN_KEY, FULL_SCREEN)
                } else {
                    ConfigUtil.putLong(baseContext, TYPE_SCREEN_KEY, MULTIPLE_AREA)
                }
                recreate()
            }
        }
    }


    private fun showLeftArea() {
        if (listLeftMedia.isEmpty()) return
        Log.d("RightTextLog", "onAnimationStart: showLeftArea ")
        //val typeface = resources.getFont(R.font.verdanabold)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        listLeftMedia.forEach {
            val textView = TextView(this)
            textView.text = it.body
            textView.setTextColor(Color.parseColor("#F2FB0A"))
//            textView.textSize = convertPixelsToDp(36, baseContext)
           // textView.typeface = typeface;
//            layoutParams.setMargins(
//                0,
//                convertPixelsToDp(20, baseContext).toInt(),
//                0,
//                convertPixelsToDp(100, baseContext).toInt()
//            ) // Adjust the bottom margin as needed
            textView.layoutParams = layoutParams
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

    private fun startAutoScroll() {
        val runnable: Runnable = object : Runnable {
            override fun run() {
                // Scroll the ScrollView vertically
               // scrollLeftArea.scrollBy(0, scrollSpeed)

                // Check if reached the bottom
                if (scrollLeftArea.scrollY >= scrollLeftArea.getChildAt(0)
                        .height - scrollLeftArea.height
                ) {
//                    layoutTvLeft.removeViews(0,listLeftTextView.size-1)
                    listLeftTextView.forEach {
                        layoutTvLeft.removeView(it)
                    }
                    listLeftTextView.clear()
                    listLeftMedia.forEach {
                        val textView = TextView(baseContext)
                        textView.text = it.body
                        textView.setTextColor(Color.parseColor("#F2FB0A"))
//                        textView.textSize = convertPixelsToDp(36, baseContext)
                        // textView.typeface = typeface;
                        val layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
//                        layoutParams.setMargins(
//                            0,
//                            convertPixelsToDp(20, baseContext).toInt(),
//                            0,
//                            convertPixelsToDp(100, baseContext).toInt()
//                        ) // Adjust the bottom margin as needed
                        textView.layoutParams = layoutParams
                        listLeftTextView.add(textView)
                        layoutTvLeft.addView(textView)
                    }
                    Log.d("PLaasdas", "run: ${layoutTvLeft.size}")
                }

                // Repeat the scrolling
                handler.postDelayed(this, 50) // Adjust the delay as needed
            }
        }
        handler.postDelayed(runnable, 2000) // Delay before starting the auto-scroll
    }

    private fun hideTopLeftArea() {
        listLeftMedia.clear()
        handler.removeCallbacksAndMessages(null)
        Log.e("AreaLog", "Hub hide left")
    }

    private val scrollSpeed = 2 // Adjus
    private val handler: Handler = Handler()
    private var listLeftMedia = mutableListOf<MediaItem>()
    private var listLeftTextView = mutableListOf<TextView>()


    private fun initDefaultTopAndLeftArea() {
        scrollLeftArea = findViewById(R.id.scrollView)
        tvTopArea = findViewById(R.id.tvTopArea)
        val tvTitleRight = findViewById<TextView>(R.id.tvTitleRight)
//        tvTitleRight.textSize = convertPixelsToDp(36, baseContext)
//        tvTopArea.textSize = convertPixelsToDp(48, baseContext)
//        tvTopArea.textSize = convertPixelsToDp(48, baseContext)
        tvTopArea.isSelected = true
        layoutTvLeft = findViewById(R.id.layoutTvLeft)

    }



    override fun onStop() {
        super.onStop()
        playerManager.onActivityStop()
        cancelAllAlarm()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        playerManager.onActivityDestroy()
        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()

    }

    override fun onAlarm(intent: Intent) {
        when (intent.action) {
            MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                playerManager.isInTimeFrame = true
                Log.d("alarmx", "start: $listKey")
                playerManager.setTimeFrame(listKey, null)
            }

            MediaScheduleReceiver.ACTION_END_PLAYLIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                playerManager.isInTimeFrame = false
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
            MediaScheduleReceiver.SCHEDULED_END_LEFT_AREA -> {
                hideTopLeftArea()
            }
        }
    }

//    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
//        return false
//    }
//
//    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
//        return false
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
            // startActivity(intent)
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

            if (typeScreen == MULTIPLE_AREA) {
                if (playerManager.isInTimeFrame) {
                    listLeftMedia =
                        viewModel.mediaRepo.leftScheduledList.filter { it.body != null && it.body != "start" && it.body != "end" } as MutableList<MediaItem>
                        if(listLeftMedia.isNotEmpty()) {
                            showLeftArea()
                        }
//                    val listMediaTop =
//                        viewModel.mediaRepo.topScheduledList.filter { it.body != null && it.body != "start" && it.body != "end" }
//                    if (listMediaTop.isNotEmpty()) {
//                        var text = ""
//                        listMediaTop.forEach {
//                            text += "${it.body}\n"
//                        }
//                        listMediaTop.first().body = text
//                        showTopArea(listMediaTop.first())
//                    }

                }
            }


            //all load done, check
            viewModel.checkPlaylist()
        }
    }

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
                    val mess = responseHub.message!!
                    playerManager.stopTimeFrame()
                    playerManager.stopAllPlayer()
                    playerManager.resetPlayMode()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    viewModel.updateMonthlyScenario(mess, true)
                    ConfigUtil.putString(applicationContext, ConfigUtil.MONTHLY_SCENARIO_URL, mess)
                    playerManager.stopForceMedia()
                    Log.i("monthlyinfo", "Mess: $mess ")
                }
                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {
                    onPlayForce(responseHub)
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

                HubCommand.TURN_ON_PA -> {
                    serialPortController.turnOnPAVod()
                }
                HubCommand.TURN_OFF_PA -> {
                    serialPortController.turnOffPA()
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