package app.tek4tv.ipradio.ui

import android.content.Intent
import android.media.AudioManager
import android.os.*
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.tek4tv.digitalsignage.utils.MediaCapture
import app.tek4tv.ipradio.MyApp.Companion.appStorageManager
import app.tek4tv.ipradio.MyApp.Companion.hubManager
import app.tek4tv.ipradio.MyApp.Companion.playerManager
import app.tek4tv.ipradio.MyApp.Companion.serialPortController
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.HubComm
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.core.players_helper.IPlayerManagerListener
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import app.tek4tv.ipradio.di.MoshiInst.adapterTacGia
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.model.local.MediaLogDao
import app.tek4tv.ipradio.model.local.MediaLogModel
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.network.IHubObserve
import app.tek4tv.ipradio.network.LogService
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_ANDROID_VOLUME
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_VOLUME
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.TYPE_PRE_SCREEN_KEY
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.TYPE_SCREEN_KEY
import app.tek4tv.ipradio.utils.TimeUtils.getCurrentTime
import app.tek4tv.ipradio.utils.Utils.Companion.FULL_SCREEN
import app.tek4tv.ipradio.utils.Utils.Companion.MULTIPLE_AREA
import app.tek4tv.ipradio.utils.Utils.Companion.getLocalForceMedia
import app.tek4tv.ipradio.utils.Utils.Companion.roundToNearestNumber
import app.tek4tv.ipradio.utils.network.FtpUtils
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.storage.*
import app.tek4tv.ipradio.viewmodels.MainViewModel
import com.squareup.moshi.Moshi
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.IOException
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), AlarmListener, IHubObserve {

    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var moshi: Moshi

    @Inject
    lateinit var authService: AuthService

    @Inject
    lateinit var logService: LogService

    @Inject
    lateinit var mediaLogDao: MediaLogDao


    private lateinit var logUtils: LogUtils

    private lateinit var mediaCapture: MediaCapture

    private lateinit var mediaScheduler: MediaScheduler

    private lateinit var vlcVideoLayout: VLCVideoLayout
    private lateinit var imgHolder: ImageView

    private val globalProperties = GlobalProperties.getInst()
    private var typeScreen: String = FULL_SCREEN
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AutoScrollAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null

    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            serialPortController.turnOffPA()
            hubManager.currentMediaItem = null
            vlcVideoLayout.visibility = View.INVISIBLE
            imgHolder.visibility = View.VISIBLE
            val url = ConfigUtil.getString(applicationContext, ConfigUtil.PHOTO_HOLDER_URL, "")
            initPhotoHolder(url)
            hideTopLeftArea()
            Log.d("PMLOG", "startFirstTimeFrame: time over")
        }

        //Dừng phát khẩn cấp
        override fun onEndForceMedia() {
            hubManager.currentMediaItem = null
            ForceMedia.deleteForeMediaPref(applicationContext)
            val oldTypeScreen = ConfigUtil.getString(
                context = baseContext, TYPE_PRE_SCREEN_KEY, FULL_SCREEN
            ) ?: FULL_SCREEN
            ConfigUtil.putString(baseContext, TYPE_SCREEN_KEY, oldTypeScreen)
            recreate()

        }

        //Lỗi luồng live
        override fun onLiveError(mediaItem: MediaItem) {
            hubManager.currentMediaItem = mediaItem
            serialPortController.turnOffPA()
        }


        override fun onMediaStopPlaying(mediaItem: MediaItem, duration: Long) {

        }

        override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onPlayMediaSuccess ")
            if (mediaItem?.contentType?.value == "2") {
                vlcVideoLayout.visibility = View.INVISIBLE
                imgHolder.visibility = View.VISIBLE
            } else {
                vlcVideoLayout.visibility = View.VISIBLE
                imgHolder.visibility = View.INVISIBLE
            }
            if (mediaItem != null) {
                hubManager.playlistIndex = playerManager.playlistIndex
                hubManager.currentMediaItem = mediaItem

                serialPortController.turnOnPAVod()

                val paState =
                    if (serialPortController.playMode != SerialPortController.MODE_NONE) StringValue.PAStatusOff
                    else StringValue.PAStatusOn

                hubManager.sendLogMediaSuccess(mediaItem)
                if (!hubManager.isConnectSuccess) {
                    lifecycleScope.launch {
                        val tacGia = TacGia(
                            tenDayDu = mediaItem.author?.fullName ?: "",
                            butDanh = mediaItem.author?.userName ?: "",
                            email = mediaItem.author?.email ?: "",
                            tenDangNhap = mediaItem.author?.userName ?: ""
                        )
                        val mediaLog = MediaLogModel(
                            amLuong = Utils.getDeviceInfo(
                                StringValue.deviceInfoVolume, SerialPortController.deviceInfo
                            ),
                            thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                            thoiGianSanXuat = mediaItem.createDate?.toLong(),
                            congSuat = paState,
                            banTinID = mediaItem.projectID,
                            loaiBanTin = mediaItem.contentType?.value,
                            mucDoUuTien = mediaItem.priority?.id.toString(),
                            noiDung = mediaItem.pathBackup,
                            noiDungTomTat = mediaItem.description,
                            tacGia = adapterTacGia.toJson(tacGia),
                            thoiLuong = mediaItem.duration,
                            tieuDe = mediaItem.title,
                            loaiLinhVuc = mediaItem.fieldType?.value,
                            vungPhat = mediaItem.broadcastRegion!!.value,
                            nguonTin = mediaItem.sourceName
                        )
                        mediaLogDao.insertLog(mediaLog)
                    }
                }
            }

        }

        override fun onLeftMediaPlaying(listMediaItem: List<MediaItem>) {
            if (listMediaItem.isNotEmpty()) {
                hubManager.sendLogMediaSuccess(listMediaItem.first())
                if (typeScreen == MULTIPLE_AREA) {
                    showLeftArea(listMediaItem)
                }
            }
        }

        override fun onEndLeftMediaPlaying() {

            if (typeScreen == MULTIPLE_AREA) {
                hideTopLeftArea()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        typeScreen =
            ConfigUtil.getString(context = baseContext, TYPE_SCREEN_KEY, FULL_SCREEN) ?: FULL_SCREEN
        if (typeScreen == FULL_SCREEN) {
            setContentView(R.layout.activity_main)
        } else if (typeScreen == MULTIPLE_AREA) {
            setContentView(R.layout.activity_main_multiple_view2)
        }
        supportActionBar?.hide()
        // Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

//        if (needGrantPermission()) requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)
        viewModel.grantPermissionThoughAdb(baseContext)

        val hubComm = object : HubComm {

            override fun forceRestartDevice() {
                Toast.makeText(baseContext, resources.getString(R.string.reset), Toast.LENGTH_SHORT)
                    .show()
                lifecycleScope.launch {
                    delay(5000)
                    serialPortController.hardRestart()
                }

            }
        }

        logUtils = LogUtils.getInst()
        logUtils.logService = logService

        mediaCapture = MediaCapture(applicationContext, lifecycleScope)

        hubManager.hubComm = hubComm
        hubManager.setConnectionListener(this)

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

        vlcVideoLayout = findViewById(R.id.vlc_video_layout)
        imgHolder = findViewById(R.id.img_holder)
        playerManager = PlayersManager(
            applicationContext, vlcVideoLayout, imgHolder, viewModel.mediaRepo
        )

        playerManager.playerManagerListener = playerCallback


        NetworkUtils.instance.startNetworkListener(this)

        initHubConnect()
        registerObservers()

        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        alarmListener = this

        setAndroidVolume()

        viewModel.hideSystemUI(this)

        initDataByScreen()

        viewModel.checkDeleteFileUpdate()

        //  doSwap("/data/data/app.tek4tv.ipradio/files/tt2.mp3",SwapType.BackToFront,SwapEncryptDecryptMode.Decrypt)

    }


    private fun initDataByScreen() {
        val url = ConfigUtil.getString(applicationContext, ConfigUtil.PHOTO_HOLDER_URL, "")
        initPhotoHolder(url)
        if (typeScreen == FULL_SCREEN) {
            val tvMacAddress = findViewById<TextView>(R.id.tvMac)
            val tvCheckInternet = findViewById<TextView>(R.id.tvCheckInternet)
            val info = "${Utils.getDeviceId(this)} - ${hubManager.versionName}"
            //val info = "${Utils.getDeviceId(this).takeLast(5)} - ${hubManager.versionName}"
            tvMacAddress.text = info
            lifecycleScope.launch {
                while (true) {
                    if (hubManager.pingCounter <= 1) {
                        tvCheckInternet.setTextColor(getColor(R.color.white))
                        tvCheckInternet.setBackgroundColor(getColor(R.color.green_online))
                        tvCheckInternet.text = resources.getString(R.string.online)
                    } else {
                        tvCheckInternet.setTextColor(getColor(R.color.white))
                        tvCheckInternet.setBackgroundColor(getColor(R.color.red_offline))
                        tvCheckInternet.text = resources.getString(R.string.offine)
                    }
                    delay(10000)
                }
            }
            lifecycleScope.launch {
                tvMacAddress.text = info
                delay(15000)
                tvMacAddress.visibility = View.GONE
            }

        } else if (typeScreen == MULTIPLE_AREA) {
            initDefaultTopAndLeftArea()
        }
    }

    private fun initPhotoHolder(url: String?) {
        if (url != "") {
            val fileName = getFileNameFromUrl(url!!)
            if (isFileExisted(viewModel.mediaRepo.mediaStoragePath, fileName)) {
                val urlLocal = "file://${viewModel.mediaRepo.mediaStoragePath}/$fileName"
                Picasso.get().load(urlLocal).into(imgHolder)
                Log.d("PhotoHolder", "initPhotoHolder: Local")
            } else {
                Picasso.get().load(url).into(imgHolder)
                Log.d("PhotoHolder", "initPhotoHolder: Online")
            }
        } else {
            imgHolder.setImageResource(R.drawable.quangngai)
        }
    }

    private fun startAutoScroll() {

        val speed = 1 // Tốc độ cuộn (số pixel mỗi lần chạy)
        val interval: Long = 50 // Khoảng thời gian giữa mỗi lần cuộn (ms)

        scrollRunnable = object : Runnable {
            override fun run() {
                recyclerView.scrollBy(0, speed) // Cuộn theo trục Y (dọc)
                handler.postDelayed(this, interval)
            }
        }
        handler.postDelayed(scrollRunnable!!, interval)
    }

    private fun showLeftArea(listMedia: List<MediaItem>) {
        adapter = AutoScrollAdapter(listMedia, resources)
        recyclerView.adapter = adapter
        recyclerView.visibility = View.VISIBLE
        startAutoScroll()
    }

    private fun hideTopLeftArea() {
        if (typeScreen != FULL_SCREEN) {
            recyclerView.visibility = View.INVISIBLE
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun initDefaultTopAndLeftArea() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
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
                Log.d("onPlayMediaLog", "show typescreen $typeScreen")
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
//                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
//                Log.d("alarmx", "end: $listKey")
//                playerManager.stopPlaylist(listKey!!)
//                if (typeScreen == MULTIPLE_AREA) {
//                    Log.d("alarmx", "end left")
//                    playerManager.stopLeftPlaylist()
//                }
            }

            MediaScheduleReceiver.ACTION_END_FORCE_MEDIA -> {
                playerManager.stopForceMedia()
                Log.d("alarmx", "stop force")
            }

            MediaScheduleReceiver.ACTION_CHECKHUB_RESET -> {
                lifecycleScope.launch {
                    Log.d("alarmx", "reschedule all alarms")
                    mediaScheduler.cancelAllScheduledListAlarm()
                    viewModel.rescheduleMonthlyPlaylist()
                    hubManager.reconnectToServer()
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

    private fun registerObservers() {
        /**
         * khi cần
         * **/
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


            sendLogDataHub()

            //all load done, check
            viewModel.checkPlaylist()
        }
    }


    private fun startPlayingMedia() {
        viewModel.getPlayerContentWss()
    }

    private fun initHubConnect() {
        hubManager.connectToServer()
    }

    private fun handleFromCommandServer(command: String?, message: String?) {
        try {

            val responseHub = hubManager.responseHub

            when (command) {

                HubCommand.UPDATE_MONTHLY_WSS -> {
                    viewModel.clearDatastore(appStorageManager)
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
                    Handler(Looper.getMainLooper()).postDelayed({
                        recreate()
                    }, 1000)
                }

                HubCommand.DELETE_MONTHLY_LIST_WSS -> {
                    Log.d("sds", "handleFromCommandServer: $message ")
                    playerManager.stopAllPlayer()
                    playerManager.resetPlayMode()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    ForceMedia.clearMonthlyData(applicationContext)
                    hubManager.currentMediaItem = null
                }
                HubCommand.UPDATE_PHOTO_HOLDER -> {
                    val mess = responseHub.message!!
                    ConfigUtil.putString(applicationContext, ConfigUtil.PHOTO_HOLDER_URL, mess)
                    viewModel.mediaRepo.downloadImage(mess)
                    initPhotoHolder(mess)
                }

                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {
                    ConfigUtil.removeSharef(context = baseContext, "FORCE_MEDIA")
                    val newTypeScreen = responseHub.type
                    if (newTypeScreen != typeScreen) {
                        ConfigUtil.putString(baseContext, TYPE_SCREEN_KEY, newTypeScreen)
                        ConfigUtil.putString(baseContext, TYPE_PRE_SCREEN_KEY, typeScreen)

                        //onPlayForce(responseHub)
                        Handler(Looper.getMainLooper()).postDelayed({
                            onPlayForce(responseHub, true)
                            recreate()
                        }, 1000)
                    } else {
                        onPlayForce(responseHub, false)
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
                        0 -> {
                            cancelAllAlarm()
                            playerManager.stopAndEndTimeFrame(typeScreen)
                            playerManager.resetPlayMode()
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

                HubCommand.PLAYLIST_INFO -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val path = "${filesDir.path}/$PLAYLIST_FILE_NAME"
                            val dataFromPlaylist: String = readFile(path)
                            Log.d("readplaylist", "value2 $dataFromPlaylist ")
                            val connectionId = responseHub.message
                            Log.d("PLAYLIST_INFO", "Connectid $connectionId ")
                        } catch (e: IOException) {
                            Log.e("readplaylist", "error reading playlist!!!")
                        }
                    }
                    sendLogDataHub(true)
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

                HubCommand.STORAGE_INFO -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        val info = appStorageManager.run {
                            "${getTotalRam()},${getUsedRam()},${getTotalRomStorage()},${getUsedRomStorage()}"
                        }
//                        hubManager.sendHubDirectMessage(connectionId, Utils.STORAGE_INFO, info)
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
//                    hubManager.sendHubDirectMessage(
//                        connectionId, Utils.GET_RECORD,
//                        recordedAudioList
//                    )

                }

                HubCommand.UPLOAD_RECORDED -> {
                    val filePath = responseHub.message!!
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
            if (command != HubCommand.PONG && command != HubCommand.UPDATE_STATUS) {
                if (command != null) {
                    // logUtils.logHubCommand(command, responseHub.message ?: "")
                }
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
        /* if (ForceMedia.hasValidForceMedia(applicationContext)) mediaScheduler.cancelForceMediaAlarm()
         else mediaScheduler.cancelAllScheduledListAlarm()*/
        mediaScheduler.cancelAllScheduledListAlarm()
    }

    private fun setAndroidVolume() {
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
                ) ?: "0"
                val idPlaylist =
                    ConfigUtil.getString(baseContext, ConfigUtil.MONTHLY_SCENARIO_URL, "") ?: ""
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
        lifecycleScope.launch {
            val listMediaLog = mediaLogDao.getUnsentMediaLogs()
            listMediaLog.forEach {
                hubManager.sendUnsentLogMediaSuccess(it)
                mediaLogDao.deleteMediaItemById(it.id)
            }
        }
        Log.d("HubObverse", "onHubConnected")
    }

    override fun onHubDisConnected() {
        Log.d("HubObverse", "onHubDisConnected")
    }
}
