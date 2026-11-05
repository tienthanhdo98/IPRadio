package app.tek4tv.ipradio.ui


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.MyApp.Companion.hubManager
import app.tek4tv.ipradio.MyApp.Companion.serialPortController
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.HubComm
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.core.constants.AppConstants.FULL_SCREEN
import app.tek4tv.ipradio.core.constants.AppConstants.MULTIPLE_AREA
import app.tek4tv.ipradio.core.players_helper.IPlayerManagerListener
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.PLAY_MODE_FORCE
import app.tek4tv.ipradio.core.scheduler.AlarmListener
import app.tek4tv.ipradio.core.scheduler.MediaScheduleReceiver
import app.tek4tv.ipradio.core.scheduler.MediaScheduler
import app.tek4tv.ipradio.model.DataValueModel
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.ForceMedia.Companion.getLocalForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.network.IHubObserve
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_ANDROID_VOLUME
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_VOLUME
import app.tek4tv.ipradio.utils.CrashHandler
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.TYPE_PRE_SCREEN_KEY
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.TYPE_SCREEN_KEY
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.LogUtils
import app.tek4tv.ipradio.utils.PLAYLIST_FILE_NAME
import app.tek4tv.ipradio.utils.RECORDED_AUDIO_FOLDER_NAME
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.Utils.Companion.roundToNearestNumber
import app.tek4tv.ipradio.utils.deleteAFileInFolder
import app.tek4tv.ipradio.utils.downloadUpdateApk
import app.tek4tv.ipradio.utils.getCurrentTime
import app.tek4tv.ipradio.utils.getFileNameFromUrl
import app.tek4tv.ipradio.utils.isFileExisted
import app.tek4tv.ipradio.utils.media.MediaCapture
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.readFile
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import app.tek4tv.ipradio.utils.toCalendar
import app.tek4tv.ipradio.viewmodels.MainViewModel
import com.google.android.material.card.MaterialCardView
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.TransportEnum
import com.squareup.moshi.Moshi
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Single
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), AlarmListener, IHubObserve {

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
        Manifest.permission.RECORD_AUDIO,
    )

    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var moshi: Moshi


    @Inject
    lateinit var logUtils: LogUtils


    private lateinit var playerManager: PlayersManager


    private lateinit var appStorageManager: AppStorageManager


    private lateinit var mediaCapture: MediaCapture

    private lateinit var mediaScheduler: MediaScheduler

    private val globalProperties = GlobalProperties.getInst()

    private var typeScreen: Int = FULL_SCREEN
    private lateinit var exoVideoLayout: PlayerView
    private lateinit var imgHolder: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AutoScrollAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null

    private var lastScheduleRetryTime: Long = 0L

    private val networkCallback = object : NetworkUtils.ConnectionCallback {
        override fun onChange(netWorkState: Boolean) {
            Log.d("MainActivity", "Network state changed: $netWorkState")
            if (netWorkState) {
                hubManager.connectToHub()

                val now = System.currentTimeMillis()
                if (now - lastScheduleRetryTime > 5 * 60 * 1000L) {
                    val currentContent = viewModel.content.value
                    val isScheduleEmpty = currentContent == null || currentContent.first.isEmpty()
                    if (isScheduleEmpty && playerManager.isIdle()) {
                        lastScheduleRetryTime = now
                        Log.i("MainActivity", "Network back and schedule is empty -> fetching schedule")
                        startPlayingMedia()
                    }
                }
            }
        }
    }

    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            serialPortController.turnOffPA()
            hubManager.curMediaName = ""
            hubManager.currentMediaItem = null
            val scheduledList = viewModel.mediaRepo.scheduledList
            Log.d("PlayersManager", "startFirstTimeFrame: isHaveSchedule ${scheduledList.size}")

            val now = Calendar.getInstance().timeInMillis

            val isHaveSchedule = scheduledList.keys.any { period ->
                val t = period.split("-")
                val start = toCalendar(t[0])
                val end = toCalendar(t[1])
                now in start.timeInMillis..end.timeInMillis
            }

            Log.d("PlayersManager", "startFirstTimeFrame: isHaveSchedule $isHaveSchedule")

            if (!isHaveSchedule) {
                exoVideoLayout.visibility = View.INVISIBLE
                imgHolder.visibility = View.VISIBLE
                val url = ConfigUtil.getString(applicationContext, ConfigUtil.PHOTO_HOLDER_URL, "")
                initPhotoHolder(url)
            }

            Log.d("PlayersManager", "onTimeOver")
        }

        //Dừng phát khẩn cấp
        override fun onEndForceMedia() {
            serialPortController.turnOffPA()
            hubManager.currentMediaItem = null
            ForceMedia.deleteForeMediaPref(applicationContext)
            Log.d("LiveLog", "onEndForceMedia current $typeScreen")
            val oldTypeScreen = ConfigUtil.getInt(
                context = baseContext, TYPE_PRE_SCREEN_KEY, FULL_SCREEN
            )
            Log.d("LiveLog", "onEndForceMedia old $oldTypeScreen")
            ConfigUtil.putInt(baseContext, TYPE_SCREEN_KEY, oldTypeScreen)
            Log.d("LiveLog", "onEndForceMedia")
            if (oldTypeScreen != typeScreen) {
                recreate()
            } else {
                mediaScheduler.scheduleAllAlarms()
                playerManager.startFirstTimeFrame()
            }
        }

        override fun onMediaError(error: String, mediaItem: MediaItem) {
            exoVideoLayout.visibility = View.INVISIBLE
            imgHolder.visibility = View.VISIBLE
        }

        override fun onMediaEndReach(mediaItem: MediaItem) {
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onMediaEndReach ")
            hubManager.curMediaName = ""
            hubManager.currentMediaItem = null
        }

        override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
            if (mediaItem == null) return
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: ${mediaItem.title} ")
            exoVideoLayout.visibility = View.VISIBLE
            imgHolder.visibility = View.GONE

            hubManager.playlistIndex = playerManager.playlistIndex
            hubManager.currentMediaItem = mediaItem
            hubManager.curMediaName = mediaItem.title ?: ""
            hubManager.logUtils = logUtils
            serialPortController.turnOnPAVod()

            val paState = if (serialPortController.playMode != SerialPortController.MODE_NONE) "1"
            else "0"

            logUtils.logMediaStart(
                mediaItem, globalProperties.volume, paState, typeScreen
            )
        }

        override fun onLeftMediaPlaying(listMediaItem: List<MediaItem>) {
            if (typeScreen == FULL_SCREEN) return
            if (listMediaItem.isNotEmpty()) {
                val paState =
                    if (serialPortController.playMode != SerialPortController.MODE_NONE) "1"
                    else "0"
                logUtils.logMediaStart(
                    listMediaItem.first(), globalProperties.volume, paState, typeScreen
                )
                showLeftArea(listMediaItem)
            }
        }

        override fun onEndLeftMediaPlaying() {
            hideTopLeftArea()
        }
    }

    //need for android 11, it couldn`t auto open after reboot
    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 5926)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        typeScreen = ConfigUtil.getInt(context = baseContext, TYPE_SCREEN_KEY, FULL_SCREEN)
        if (typeScreen == FULL_SCREEN) {
            setContentView(R.layout.activity_main)
        } else if (typeScreen == MULTIPLE_AREA) {
            setContentView(R.layout.activity_main_multiple_view2)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                Toast.makeText(baseContext, resources.getString(R.string.reset), Toast.LENGTH_SHORT)
                    .show()
                lifecycleScope.launch {
                    delay(5000)
                    serialPortController.hardRestart()
                }
            }
        }

        mediaCapture = MediaCapture(applicationContext, lifecycleScope)

        hubManager.hubComm = hubComm
        hubManager.hubObverse = this
        hubManager.mediaCapture = mediaCapture

        lifecycleScope.launch {
            hubManager.messageStateFlow.collectLatest { messagePair ->
                messagePair?.let { (command, message) ->
                    Log.d(
                        "MainActivityMess", "Received Message: Command=$command, Message=$message"
                    )
                    handleFromCommandServer(command, message)
                }
            }
        }
        exoVideoLayout = findViewById(R.id.exo_player)

        imgHolder = findViewById(R.id.img_holder)
        playerManager = PlayersManager(
            applicationContext, imgHolder, viewModel.mediaRepo, exoVideoLayout
        )

        playerManager.playerManagerListener = playerCallback

        NetworkUtils.instance.startNetworkListener(this)
        serialPortController.isPlayingProvider = { playerManager.isPlaying() }
        NetworkUtils.instance.addCallback(networkCallback)

        hubManager.onHubConnectedListener = {
            val now = System.currentTimeMillis()
            if (now - lastScheduleRetryTime > 5 * 60 * 1000L) {
                val currentContent = viewModel.content.value
                val isScheduleEmpty = currentContent == null || currentContent.first.isEmpty()
                if (isScheduleEmpty && playerManager.isIdle()) {
                    lastScheduleRetryTime = now
                    Log.i("MainActivity", "Hub reconnected and schedule is empty -> fetching schedule")
                    startPlayingMedia()
                }
            }
        }
        alarmListener = this

        appStorageManager = AppStorageManager(applicationContext)

        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        setMaxVolume()

        viewModel.hideSystemUI(this)

        sendLogDataHub()

        initDataByScreen()

        registerObservers()

    }


    private fun initDataByScreen() {
        val url = ConfigUtil.getString(applicationContext, ConfigUtil.PHOTO_HOLDER_URL, "")
        initPhotoHolder(url)
        if (typeScreen == FULL_SCREEN) {
            val tvMacAddress = findViewById<TextView>(R.id.tvMac)
            val cardStatus = findViewById<MaterialCardView>(R.id.card_status)
            val tvCheckInternet = findViewById<TextView>(R.id.tvCheckInternet)
            val info = Utils.getDeviceId(this).takeLast(5)
            tvMacAddress.text = info

            lifecycleScope.launch {
                while (isActive) {
                    if (hubManager.isConnectSuccess) {
                        tvCheckInternet.setTextColor(getColor(R.color.white))
                        tvCheckInternet.setBackgroundColor(getColor(R.color.green_online))
                        tvCheckInternet.text = resources.getString(R.string.online)
                        cardStatus.fadeOut()
                        tvMacAddress.fadeOut()
                    } else {
                        tvCheckInternet.setTextColor(getColor(R.color.white))
                        tvCheckInternet.setBackgroundColor(getColor(R.color.red_offline))
                        tvCheckInternet.text = resources.getString(R.string.offine)
                        cardStatus.fadeIn()
                        tvMacAddress.fadeIn()
                    }
                    delay(15000)
                }
            }
        } else if (typeScreen == MULTIPLE_AREA) {
            initDefaultTopAndLeftArea()
        }
    }

    private fun initPhotoHolder(url: String?) {
        val dm = Resources.getSystem().displayMetrics
        val maxW = dm.widthPixels
        val maxH = dm.heightPixels
        if (url != "") {
            val fileName = getFileNameFromUrl(url!!)
            if (isFileExisted(viewModel.mediaRepo.mediaStoragePath, fileName)) {
                val urlLocal = "file://${viewModel.mediaRepo.mediaStoragePath}/$fileName"
                Picasso.get().load(urlLocal)
                    .resize(maxW, maxH)      // giới hạn theo kích thước màn hình
                    .onlyScaleDown() // chỉ thu nhỏ, không phóng to
                    .config(Bitmap.Config.RGB_565)// giảm bộ nhớ (từ 4B/px xuống 2B/px), chấp nhận giảm chất lượng
                    .into(imgHolder)
                Log.d("PhotoHolder", "initPhotoHolder: Local")
            } else {
                Picasso.get().load(url)
                    .resize(maxW, maxH)      // giới hạn theo kích thước màn hình
                    .onlyScaleDown() // chỉ thu nhỏ, không phóng to
                    .config(Bitmap.Config.RGB_565)// giảm bộ nhớ (từ 4B/px xuống 2B/px), chấp nhận giảm chất lượng
                    .into(imgHolder)
                Log.d("PhotoHolder", "initPhotoHolder: Online")
            }
        } else {
            imgHolder.setImageResource(R.drawable.img_bg)
        }
    }

    private fun startAutoScroll() {
        scrollRunnable?.let { handler.removeCallbacks(it) }

        val speed = 1
        val interval: Long = 50

        scrollRunnable = object : Runnable {
            override fun run() {
                recyclerView.scrollBy(0, speed)
                handler.postDelayed(this, interval)
            }
        }
        handler.postDelayed(scrollRunnable!!, interval)
    }

    private fun showLeftArea(listMedia: List<MediaItem>) {
        adapter = AutoScrollAdapter(listMedia, resources)
        recyclerView.adapter = adapter
        if (adapter.itemCount > 0) {
            recyclerView.visibility = View.VISIBLE
            startAutoScroll()
        } else {
            recyclerView.visibility = View.INVISIBLE
            scrollRunnable?.let { handler.removeCallbacks(it) }
        }
    }


    private fun initDefaultTopAndLeftArea() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    }

    private fun hideTopLeftArea() {
        if (typeScreen == FULL_SCREEN) return
        recyclerView.visibility = View.INVISIBLE
        handler.removeCallbacksAndMessages(null)
    }


    override fun onStart() {
        super.onStart()
        startPlayingMedia()
    }

    override fun onDestroy() {
        Log.e("monthlyinfo", "onDestroy ")
        serialPortController.turnOffPA()
        NetworkUtils.instance.removeCallback(networkCallback)
        hubManager.onHubConnectedListener = null
        serialPortController.isPlayingProvider = null

        handler.removeCallbacksAndMessages(null)
        playerManager.onActivityDestroy()
        Log.i("ChangeLayout", "Mess: onDestroy")
        super.onDestroy()

    }

    override fun onAlarm(intent: Intent) {
        when (intent.action) {
            MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", "start: $listKey")
                if (listKey != null) {
                    playerManager.setTimeFrame(listKey, null)
                    if (typeScreen == MULTIPLE_AREA) {
                        Log.d("alarmx", "start left: $listKey")
                        playerManager.startLeftTimeFrame(listKey)
                    }
                }
            }

            MediaScheduleReceiver.ACTION_END_PLAYLIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", "end: $listKey")

                playerManager.stopPlaylist(listKey!!)
            }

            MediaScheduleReceiver.ACTION_END_FORCE_MEDIA -> {
                playerManager.stopForceMedia()
                Log.d("alarmx", "stop force")
            }

            MediaScheduleReceiver.ACTION_CHECKHUB_RESET -> {
                lifecycleScope.launch {
                    Log.d("alarmx", "reschedule all alarms")
                    mediaScheduler.cancelAllScheduledListAlarm()
                    ConfigUtil.getString(
                        applicationContext, ConfigUtil.MONTHLY_SCENARIO_URL, ""
                    )?.takeIf { it.isNotBlank() }?.let { url ->
                        lifecycleScope.launch {
                            repeat(10) { // Try 10 times before giving up
                                if (viewModel.mediaRepo.isRescheduleSuccess) return@launch
                                viewModel.updateMonthlyScenarioAndGetContent(url)
                                delay(15000)
                            }
                            Log.e("alarmx", "Failed to reschedule after 10 attempts")
                        }
                    } ?: Log.e("alarmx", "MONTHLY_SCENARIO_URL is empty or null")
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
            viewModel.downloadMedias()
            //Check xem có đang phát khẩn cấp không
            //Nếu có thì phát media khẩn cấp
            //Media khẩn cấp lưu share pref
            if (ForceMedia.hasValidForceMedia(applicationContext)) {
                val listForceMedia = getLocalForceMedia(applicationContext)
                Log.i("Changelayout", "Mess: local ${listForceMedia?.size}")
                if (listForceMedia != null) playerManager.playForceMedia(
                    listForceMedia, mediaScheduler
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


        private var lastStartPlayingMediaTime = 0L
    private val START_PLAYING_MEDIA_DEBOUNCE_MS = 3000L

    private fun startPlayingMedia() {
        val now = System.currentTimeMillis()
        if (now - lastStartPlayingMediaTime < START_PLAYING_MEDIA_DEBOUNCE_MS) {
            Log.d("startPlayingMedia", "startPlayingMedia bá»‹ bá» qua do debounce (< ${START_PLAYING_MEDIA_DEBOUNCE_MS}ms)")
            return
        }
        lastStartPlayingMediaTime = now
        viewModel.getPlayerContent()
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
                    viewModel.clearDatastore(appStorageManager)
                    val mess = responseHub.message!!
                    playerManager.stopTimeFrame()
                    playerManager.stopAllPlayer()
                    playerManager.resetPlayMode()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    viewModel.updateMonthlyScenario(mess)
                    ConfigUtil.putString(applicationContext, ConfigUtil.MONTHLY_SCENARIO_URL, mess)
                    playerManager.stopForceMedia()
                    Handler(Looper.getMainLooper()).postDelayed({
                        recreate()
                    }, 1000)
                }
                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {
                    playerManager.stopAllPlayer()
                    ConfigUtil.removeSharef(context = baseContext, "FORCE_MEDIA")
                    val newTypeScreen = responseHub.mode!!.toInt()
                    if (newTypeScreen != typeScreen) {
                        ConfigUtil.putInt(baseContext, TYPE_SCREEN_KEY, newTypeScreen)
                        ConfigUtil.putInt(baseContext, TYPE_PRE_SCREEN_KEY, typeScreen)

                        //onPlayForce(responseHub)
                        Handler(Looper.getMainLooper()).postDelayed({
                            onPlayForce(responseHub, true)
                            recreate()
                        }, 1000)
                    } else {
                        onPlayForce(responseHub, false)
                    }

                }

                HubCommand.UPDATE_PHOTO_HOLDER -> {
                    val mess = responseHub.message!!
                    ConfigUtil.putString(applicationContext, ConfigUtil.PHOTO_HOLDER_URL, mess)
                    viewModel.mediaRepo.downloadImage(mess)
                    initPhotoHolder(mess)
                }

                HubCommand.UPDATE_DESTINATION -> {
                    val mess = responseHub.message!!

                    val listInfoConfig = mess.split(",")
                    val resposnHub = ResponseHub().apply {
                        if (listInfoConfig.isNotEmpty() && listInfoConfig[0] != "null") {
                            volume = listInfoConfig[0]
                        }
                        if (listInfoConfig.size >= 2 && listInfoConfig[1] != "null") {
                            lat = listInfoConfig[1].toFloat()
                        }
                        if (listInfoConfig.size >= 3 && listInfoConfig[2] != "null") {
                            lng = listInfoConfig[2].toFloat()
                        }
                        if (listInfoConfig.size >= 4 && listInfoConfig[3] != "null") {
                            destinationId = listInfoConfig[3]
                        }
                        if (listInfoConfig.size >= 5 && listInfoConfig[4] != "null") {
                            destinationName = listInfoConfig[4]
                        }
                        if (listInfoConfig.size >= 6 && listInfoConfig[5] != "null") {
                            deviceName = listInfoConfig[5]
                        }
                    }
                    if (listInfoConfig.size >= 7 && listInfoConfig[6] != "null") {
                        ConfigUtil.putStringEncrypt(
                            applicationContext, ConfigUtil.PROVIDER, listInfoConfig[6]
                        )
                        hubManager.providerValue = listInfoConfig[6]
                    }
                    globalProperties.saveProperties(resposnHub)
                }

                HubCommand.UPDATE_CONFIG -> {
                    //{"IMEI":"18:93:7F:E5:F5:77","Message":"https://hub.gateway.httt.tek4tv.vn/hubServer,https://api.gateway.httt.tek4tv.vn/api/auth/login"}
                    Log.i("UPDATE_CONFIG be", responseHub.message!!)
                    val mess = responseHub.message!!
                    val listValue = mess.split(",")
                    val urlHub = listValue[0]
                    val urlToken = listValue[1]
                    if (listValue.size >= 3) {
                        ConfigUtil.putStringEncrypt(
                            applicationContext, ConfigUtil.PROVIDER, listValue[2]
                        )
                    }
                    val hubConnection =
                        HubConnectionBuilder.create(urlHub)
                            .setHttpClientBuilderCallback { builder ->
                                app.tek4tv.ipradio.utils.network.CaCertHelper.buildConfiguredOkHttpClient(builder, applicationContext)
                            }
                            .withAccessTokenProvider(Single.defer {
                                val token = hubManager.getHubToken(urlToken)
                                if (token != null) {
                                    Single.just(token)
                                } else Single.error(Exception())
                            }).withTransport(TransportEnum.WEBSOCKETS).build()
                    lifecycleScope.launch {
                        val id = hubManager.openConnection2(hubConnection)
                        if (id != null) {
                            ConfigUtil.putStringEncrypt(
                                applicationContext, ConfigUtil.URL_HUB, urlHub
                            )
                            ConfigUtil.putStringEncrypt(
                                applicationContext, ConfigUtil.URL_TOKEN, urlToken
                            )
                            Log.i("config_config", "updateconfig success: reseting")
                            delay(60000)
                            Toast.makeText(
                                baseContext, resources.getString(R.string.reset), Toast.LENGTH_SHORT
                            ).show()
                            delay(10000)
                            serialPortController.hardRestart()
                            CrashHandler.restartApp(MyApp.instance!!, this@MainActivity)

                        } else {
                            Log.i("config_config", "updateconfig fail: reseting")
                            delay(2000)
                            Toast.makeText(
                                baseContext, resources.getString(R.string.reset), Toast.LENGTH_SHORT
                            ).show()
                            delay(5000)
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
                    when (responseHub.message!!.toInt()) {
                        //dung phat
                        0 -> {
                            lifecycleScope.launch {
                                playerManager.resetPlayMode()
                                cancelAllAlarm()
                                ForceMedia.deleteForeMediaPref(applicationContext)
                                viewModel.mediaRepo.clearPlaylist()
                                playerManager.stopAndEndTimeFrame()
                            }
                        }
                        //dung phat va chuyen sang ban tin tiep
                        1 -> {
                            if (!playerManager.isTimeOver) {
                                playerManager.playNextMedia()
                            }
                        }
                        //Tạm dừng nội dung đang phát
                        2 -> {
                            playerManager.pauseMedia()
                        }

                        3 -> {
                            if (!playerManager.isTimeOver) {
                                playerManager.resumeMedia()
                            }
                        }
                    }
                }

                HubCommand.UPDATE_STATUS -> {
                    serialPortController.updateStatus()
                }


                HubCommand.RESTART -> {
                    Toast.makeText(
                        baseContext, resources.getString(R.string.reset), Toast.LENGTH_SHORT
                    ).show()
                    lifecycleScope.launch {
                        delay(5000)
                        serialPortController.hardRestart()
                    }
                }

                HubCommand.SET_MUTE_DEVICE -> {
                    if (responseHub.message != null) {
                        Log.d("UpdateVolume", "handleFromCommandServer: ${responseHub.message}")
                        if (responseHub.message == "1")//tat loa mute
                        {
                            changeVolume(0)
                            globalProperties.saveVolume("0")
                            globalProperties.setAndroidPreVolume("0")
                        } else {
                            changeVolume(
                                (ConfigUtil.getString(
                                    baseContext, DEFAULT_ANDROID_VOLUME, "10"
                                ) ?: "10").toInt()
                            )
                            globalProperties.setAndroidPreVolume(roundToNearestNumber(responseHub.message!!.toInt()).toString())
                            globalProperties.saveVolume(
                                (ConfigUtil.getString(
                                    baseContext, DEFAULT_VOLUME, "100"
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
                    if (responseHub.message != null) {
                        changeVolume(roundToNearestNumber(responseHub.message!!.toInt()))
                        globalProperties.setAndroidPreVolume(roundToNearestNumber(responseHub.message!!.toInt()).toString())
                        globalProperties.saveVolume(responseHub.message!!.toString())
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

                HubCommand.DELETE_ALL_MEDIA -> {
                    appStorageManager.deleteAllMedia()
                }

                HubCommand.RECORD -> {
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
                        connectionId, Utils.GET_RECORD, recordedAudioList
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


                HubCommand.CONTROL_WIFI_STATE -> {
                    val wifiEnable = responseHub.message!! == "1"
                    NetworkUtils.setWifiEnable(applicationContext, wifiEnable)
                }


                HubCommand.SET_ENCRYPTED_VOLUME_DEVICE -> {
                    if (responseHub.message != null) {
                        serialPortController.writeForceSetVolume(responseHub.message!!)
                    }
                }

            }
            if (command != HubCommand.PONG && command != HubCommand.UPDATE_STATUS) {
                logUtils.logHubCommand(command, responseHub.message ?: "")
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun changeVolume(volumeValue: Int) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeValue, 0)
    }

    private fun onPlayForce(responseHub: ResponseHub, isLayoutChange: Boolean) {
        //Tránh trường hợp âm lượng bị tắt
        serialPortController.writeForceSetVolume(globalProperties.volume)
        playerManager.onPlayForceCommand(
            applicationContext, responseHub, mediaScheduler, isLayoutChange
        )
    }

    private fun cancelAllAlarm() {
        mediaScheduler.cancelAllScheduledListAlarm()
    }

    private fun setMaxVolume() {
        Log.d("UpdateVolume", "set max: ${GlobalProperties.getInst().androidVolume}")
        changeVolume(GlobalProperties.getInst().androidVolume.toInt())
    }


    private fun sendLogDataHub(isSendPlaylistData: Boolean = false) {
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

    companion object {
        var alarmListener: AlarmListener? = null
    }

    override fun onHubConnected() {
        Log.d("HubObverse", "onHubConnected")
        //
        if (playerManager.playMode == PLAY_MODE_FORCE) {
            if (ForceMedia.hasValidForceMedia(applicationContext)) {
                val listForceMedia = getLocalForceMedia(applicationContext)
                Log.i("Changelayout", "Mess: local ${listForceMedia?.size}")
                if (listForceMedia != null) playerManager.playForceMedia(
                    listForceMedia, mediaScheduler
                )
            }
        } else {
            playerManager.startFirstTimeFrame()
        }
    }

    fun View.fadeIn(duration: Long = 500, end: (() -> Unit)? = null) {
        if (visibility != View.VISIBLE) {
            alpha = 0f
            visibility = View.VISIBLE
        }
        animate().alpha(1f).setDuration(duration).withEndAction { end?.invoke() }.start()
    }

    fun View.fadeOut(duration: Long = 500, end: (() -> Unit)? = null) {
        animate().alpha(0f).setDuration(duration).withEndAction {
            visibility = View.INVISIBLE // hoặc GONE nếu muốn bỏ chiếm chỗ
            end?.invoke()
        }.start()
    }

    override fun onHubDisConnected() {
        Log.d("HubObverse", "onHubDisConnected")
    }

}
