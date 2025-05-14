package app.tek4tv.ipradio.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.tek4tv.digitalsignage.utils.MediaCapture
import app.tek4tv.ipradio.MyApp.Companion.appStorageManager
import app.tek4tv.ipradio.MyApp.Companion.hubManager
import app.tek4tv.ipradio.MyApp.Companion.serialPortController
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.HubComm
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.core.players_helper.IPlayerManagerListener
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import app.tek4tv.ipradio.di.MoshiInst.adapterTacGia
import app.tek4tv.ipradio.di.MoshiInst.moshi
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
import app.tek4tv.ipradio.utils.Utils.Companion.FULL_SCREEN
import app.tek4tv.ipradio.utils.Utils.Companion.MULTIPLE_AREA
import app.tek4tv.ipradio.utils.Utils.Companion.roundToNearestNumber
import app.tek4tv.ipradio.utils.network.FtpUtils
import app.tek4tv.ipradio.utils.network.LocationTracker
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.storage.*
import app.tek4tv.ipradio.viewmodels.MainViewModel
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Type
import java.util.*
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

    @Inject
    lateinit var mediaLogDao: MediaLogDao

    private lateinit var playerManager: PlayersManager


    private lateinit var logUtils: LogUtils

    var locationTracker = LocationTracker()

    private lateinit var mediaCapture: MediaCapture

    private lateinit var mediaScheduler: MediaScheduler

    private lateinit var vlcVideoLayout: VLCVideoLayout
    private lateinit var imgHolder: ImageView

    private val globalProperties = GlobalProperties.getInst()
    private var typeScreen: String = FULL_SCREEN
    private lateinit var tvTopArea: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AutoScrollAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null

    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            serialPortController.turnOffPA()
            hubManager.curMediaName = ""
            hubManager.currentMediaItem = null
            vlcVideoLayout.visibility = View.INVISIBLE
            imgHolder.visibility = View.VISIBLE
            Log.d("PMLOG", "startFirstTimeFrame: time over")
        }

        //Dừng phát khẩn cấp
        override fun onEndForceMedia() {
            hubManager.curMediaName = ""
            hubManager.currentMediaItem = null
            ForceMedia.deleteForeMediaPref(applicationContext)
            val oldTypeScreen = ConfigUtil.getString(
                context = baseContext,
                TYPE_PRE_SCREEN_KEY, FULL_SCREEN
            ) ?: FULL_SCREEN
            ConfigUtil.putString(baseContext, TYPE_SCREEN_KEY, oldTypeScreen)
//            startPlayingMedia()
            Handler().postDelayed({
                recreate()

            }, 1000)
        }

        //Lỗi luồng live
        override fun onLiveError(mediaItem: MediaItem) {
            hubManager.curMediaName = "Live Error"
            hubManager.currentMediaItem = mediaItem
            serialPortController.turnOffPA()
        }


        override fun onMediaStopPlaying(mediaItem: MediaItem, duration: Long) {
//            logUtils.logMediaEnd(mediaItem)
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onMediaStopPlaying ")
        }

        override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onPlayMediaSuccess ")
            imgHolder.visibility = View.GONE
            vlcVideoLayout.visibility = View.VISIBLE
            if (mediaItem != null) {
                hubManager.playlistIndex = playerManager.playlistIndex
                hubManager.currentMediaItem = mediaItem

                val displayName = mediaItem.title ?: ""
                hubManager.curMediaName = displayName

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
                                StringValue.deviceInfoVolume,
                                SerialPortController.deviceInfo
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

            if (typeScreen == MULTIPLE_AREA) {
                showLeftArea(listMediaItem)
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
        grantPermissionThoughAdb(baseContext)

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
                        "MainActivityMess",
                        "Received Message: Command=$command, Message=$message"
                    )
                    handleFromCommandServer(command, message)
                }
            }
        }

        vlcVideoLayout = findViewById(R.id.vlc_video_layout)
        imgHolder = findViewById(R.id.img_holder)
        playerManager = PlayersManager(
            applicationContext,
            vlcVideoLayout,
            viewModel.mediaRepo
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

        checkDeleteFileUpdate()
//        lifecycleScope.launch{
//            delay(10000)
//            playerManager.resumeMedia()
//        }


        //  doSwap("/data/data/app.tek4tv.ipradio/files/tt2.mp3",SwapType.BackToFront,SwapEncryptDecryptMode.Decrypt)

    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun capture() {
        try {
            val surfaceView = (vlcVideoLayout.getChildAt(
                0
            ) as FrameLayout).children.filter { it is SurfaceView }
                .first() as SurfaceView

            val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)

            PixelCopy.request(surfaceView, bitmap, {
                saveBitmap(bitmap)
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveBitmap(bitmap: Bitmap) {
        // Create directory for saving
        val path: File =
            File(Environment.getExternalStorageDirectory().toString() + "/Screenshots/")
        if (!path.exists()) {
            path.mkdirs()
        }

        // Create the file for saving
        val imageFile = File(path, "screenshot_" + System.currentTimeMillis() + ".png")
        try {
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) // Compress to PNG
                Toast.makeText(this, "Screenshot saved: " + imageFile.path, Toast.LENGTH_SHORT)
                    .show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save screenshot", Toast.LENGTH_SHORT).show()
        }
    }


    fun isPermissionGranted(permission: String): Boolean {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isUsageStatsPermissionGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun grantPermissionThoughAdb(context: Context) {
        val packageName = "app.tek4tv.digital"

        if (!isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Log.d("PermissionCheck", "Granting READ_EXTERNAL_STORAGE...")
            execSu("pm grant $packageName android.permission.READ_EXTERNAL_STORAGE")
        } else {
            Log.d("PermissionCheck", "READ_EXTERNAL_STORAGE already granted.")
        }

        if (!isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Log.d("PermissionCheck", "Granting WRITE_EXTERNAL_STORAGE...")
            execSu("pm grant $packageName android.permission.WRITE_EXTERNAL_STORAGE")
        } else {
            Log.d("PermissionCheck", "WRITE_EXTERNAL_STORAGE already granted.")
        }

        if (!isUsageStatsPermissionGranted(context)) {
            Log.d("PermissionCheck", "Granting GET_USAGE_STATS via appops...")
            execSu("appops set $packageName GET_USAGE_STATS allow")
        } else {
            Log.d("PermissionCheck", "GET_USAGE_STATS already allowed.")
        }

        if (!isPermissionGranted(Manifest.permission.READ_PHONE_STATE)) {
            Log.d("PermissionCheck", "Granting READ_PHONE_STATE...")
            execSu("pm grant $packageName android.permission.READ_PHONE_STATE")
        } else {
            Log.d("PermissionCheck", "READ_PHONE_STATE already granted.")
        }

        if (!isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
            Log.d("PermissionCheck", "Granting RECORD_AUDIO...")
            execSu("pm grant $packageName android.permission.RECORD_AUDIO")
        } else {
            Log.d("PermissionCheck", "RECORD_AUDIO already granted.")
        }
    }

    private fun checkDeleteFileUpdate() {
        val destination = getDestinationApk()
        val file = File(destination)
        if (file.exists()) {
            file.delete()
            Log.d("deletefile", "checkDeleteFileUpdate: delete success")
        } else {
            Log.d("deletefile", "checkDeleteFileUpdate: file not exists")
        }
    }

    lateinit var tvDownload: TextView
    private fun initDataByScreen() {
        val url = ConfigUtil.getString(applicationContext, ConfigUtil.PHOTO_HOLDER_URL, "")
        initPhotoHolder(url)
        if (typeScreen == FULL_SCREEN) {
            val tvMacAddress = findViewById<TextView>(R.id.tvMac)
            tvDownload = findViewById(R.id.tvDownload)
            val tvPlaying = findViewById<TextView>(R.id.tvPlaying)
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
//            lifecycleScope.launch {
//                tvMacAddress.text = info
//                delay(15000)
//                tvMacAddress.visibility = View.GONE
//            }
            lifecycleScope.launch {
                while (true) {
                    tvPlaying.text = "Now playing: ${hubManager.currentMediaItem?.body}"
                    delay(2000)
                }
            }
            lifecycleScope.launch {
                while (true) {
                    val value = viewModel.mediaRepo.currentMapDownload
                    val str = value.entries.joinToString("\n") { "${it.key} - ${it.value}" }
                    tvDownload.text = str
                    delay(2000)
                }
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
                Log.d("onPlayMediaLog", "show typescreen ${typeScreen}")
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
                if (typeScreen == MULTIPLE_AREA) {
                    Log.d("alarmx", "end left")
                    playerManager.stopLeftPlaylist()
                }
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
    }

    private fun handleFromCommandServer(command: String?, message: String?) {
        try {

            val responseHub = hubManager.responseHub

            when (command) {

                HubCommand.UPDATE_MONTHLY_WSS -> {
                    clearDatastore()
                    viewModel.mediaRepo.currentMapDownload.clear()
//                    tvDownload.text = ""
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
                    Handler().postDelayed({
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
                    hubManager.curMediaName = ""
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
                    ConfigUtil.putString(baseContext, TYPE_SCREEN_KEY, FULL_SCREEN)
                    ConfigUtil.putString(baseContext, TYPE_PRE_SCREEN_KEY, typeScreen)

                    Handler().postDelayed({
                        onPlayForce(responseHub)
                        recreate()
                    }, 1000)
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
                    when (mess) {
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


                HubCommand.UPDATE_STATUS -> {
                    serialPortController.updateStatus()
                }

                HubCommand.GET_LOCATION -> {
                    val connectionId = responseHub.message
                    if (locationTracker.mlocation != null && connectionId != null) {

                        val result =
                            "${locationTracker.mlocation!!.latitude},${locationTracker.mlocation!!.longitude}"

//                        hubManager.sendHubDirectMessage(
//                            connectionId, Utils.DEVICE_LOCATION,
//                            result
//                        )
                    } else Log.d("location", "location not found")
                }

                HubCommand.RESTART -> {
                    Toast.makeText(
                        baseContext,
                        resources.getString(R.string.reset),
                        Toast.LENGTH_SHORT
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
                                    baseContext,
                                    DEFAULT_ANDROID_VOLUME, "10"
                                ) ?: "10").toInt()
                            )
                            globalProperties.setAndroidPreVolume(roundToNearestNumber(responseHub.message!!.toInt()).toString())
                            globalProperties.saveVolume(
                                (ConfigUtil.getString(
                                    baseContext,
                                    DEFAULT_VOLUME, "100"
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
                    logUtils.logHubCommand(command, responseHub.message ?: "")
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

    private fun onPlayForce(responseHub: ResponseHub) {
        //Tránh trường hợp âm lượng bị tắt
        serialPortController.writeForceSetVolume(globalProperties.volume)
        playerManager.onPlayForceCommand(applicationContext, responseHub, mediaScheduler)
    }


    private fun cancelAllAlarm() {
        /* if (ForceMedia.hasValidForceMedia(applicationContext)) mediaScheduler.cancelForceMediaAlarm()
         else mediaScheduler.cancelAllScheduledListAlarm()*/
        mediaScheduler.cancelAllScheduledListAlarm()
    }

    private fun setAndroidVolume() {
        changeVolume(GlobalProperties.getInst().androidVolume.toInt())
    }

    private fun clearDatastore() {
        //Luon de trong 500mb
        //Xoa file khong co trong lich khong du se dung download
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
                        ConfigUtil.TIME_UPDATE_MONTHLY_SCENARIO, "0"
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
        val jsonMapAdapter: JsonAdapter<Map<String, String>> =
            moshi.adapter(typeMap)
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
