package app.tek4tv.ipradio.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.HubManager.HubManager
import app.tek4tv.ipradio.core.HubManager.IHubObserve
import app.tek4tv.ipradio.core.SchedulerManager.AlarmListener
import app.tek4tv.ipradio.core.SchedulerManager.MediaScheduleReceiver
import app.tek4tv.ipradio.core.SchedulerManager.MediaScheduler
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.core.players_helper.IPlayerManagerListener
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.PLAY_MODE_FORCE
import app.tek4tv.ipradio.di.NetworkStatus
import app.tek4tv.ipradio.di.RetrofitInstance
import app.tek4tv.ipradio.di.RetrofitInstance.moshi
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_ANDROID_VOLUME
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_VOLUME
import app.tek4tv.ipradio.utils.Utils.Companion.bitmapToBase64
import app.tek4tv.ipradio.utils.Utils.Companion.roundToNearestNumber
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.storage.*
import app.tek4tv.ipradio.viewmodels.MainViewModel
import com.squareup.picasso.Picasso
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.*


class MainActivity : AppCompatActivity(), AlarmListener {

    private val UPDATE_PERMISSION_REQUEST_CODE = 1

    private val permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.REQUEST_INSTALL_PACKAGES,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.RECORD_AUDIO
    )

    private val viewModel by viewModels<MainViewModel>()

    private lateinit var hubManager: HubManager

    private lateinit var playerManager: PlayersManager

    private lateinit var serialPortController: SerialPortController

    private lateinit var logUtils: LogUtils

    private lateinit var mediaScheduler: MediaScheduler


    private lateinit var exoVideoLayout: PlayerView
    private lateinit var imgHolder: ImageView
    private val globalProperties = GlobalProperties.getInst()
    private val UART_S1_NAME = "/dev/ttyS1"
    private val UART_S4_NAME = "/dev/ttyS4"
    var tokenDigital = ""

    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            serialPortController.turnOffPA()
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
            ForceMedia.deleteForeMediaPref(applicationContext)
            startPlayingMedia()
        }

        //Lỗi luồng live
        override fun onMediaError(error: String, mediaItem: MediaItemModel) {
            lifecycleScope.launch {
                val status = logUtils.logException(
                    data = error, imei = hubManager.deviceImei, token = tokenDigital
                )
                if (status == NetworkStatus.UnAuthorization) {
                    tokenDigital = ""
                    getTokenDigital()
                    if (tokenDigital != "") {
                        logUtils.logException(
                            data = error, imei = hubManager.deviceImei, token = tokenDigital
                        )
                    }
                }
            }
            exoVideoLayout.visibility = View.INVISIBLE
            imgHolder.visibility = View.VISIBLE
        }

        override fun onMediaStopPlaying(mediaItem: MediaItemModel, duration: Long) {
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onMediaStopPlaying ")
        }

        override fun onPlayMediaSuccess(mediaItem: MediaItemModel?) {
            if (mediaItem == null) return
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: ${mediaItem.title} ")
            if (mediaItem.contentType?.id == 2) {
                exoVideoLayout.visibility = View.INVISIBLE
                imgHolder.visibility = View.VISIBLE
            } else {
                exoVideoLayout.visibility = View.VISIBLE
                imgHolder.visibility = View.INVISIBLE
            }


            hubManager.currentMediaItem = mediaItem
            serialPortController.turnOnPAVod()
            hubManager.sendLogMediaSuccess(mediaItem)

        }
    }

    private val hubCallBack = object : IHubObserve {
        override fun onHubConnected() {
            Log.d("HubObverse", "onHubConnected")
            //
            if (playerManager.playMode == PLAY_MODE_FORCE) {
                if (ForceMedia.hasValidForceMedia(applicationContext)) {
                    val forceMedia = ForceMedia.getLocalForceMedia(applicationContext)
                    if (forceMedia != null) {
                        mediaScheduler.scheduleForceMediaAlarm(forceMedia)
                        playerManager.playForceMedia(applicationContext, forceMedia, mediaScheduler)
                    }
                }
            } else {
                playerManager.startFirstTimeFrame()
            }

        }

        override fun onHubDisConnected() {
            playerManager.stopAllPlayer()
            Log.d("HubObverse", "onHubDisConnected")

        }

    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        supportActionBar?.hide()
        viewModel.hideSystemUI(this)
        //Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        if (needGrantPermission()) requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)


        logUtils = LogUtils.getInst()


        hubManager = HubManager { command, message ->
            handleFromCommandServer(command, message)
        }
        hubManager.hubObverse = hubCallBack

        serialPortController = SerialPortController(applicationContext, lifecycleScope, hubManager)
        serialPortController.connectToSerialPort(UART_S1_NAME)

        lifecycleScope.launch {
            delay(10000)
            if (!serialPortController.isUart1) {
                serialPortController.connectToSerialPort(UART_S4_NAME)
                Log.i("PA", "onCreate: s4")
            }
        }

        exoVideoLayout = findViewById(R.id.exo_player)

        imgHolder = findViewById(R.id.img_holder)
        initPhotoHolder("")
        playerManager = PlayersManager(
            viewModel.mediaRepo, exoVideoLayout, imgHolder,
        )

        playerManager.playerManagerListener = playerCallback



        initHubConnect()
        registerObservers()

        appStorageManager = AppStorageManager(applicationContext)


        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        alarmListener = this

        setMaxVolume()

        initDataByScreen()

        checkDeleteFileUpdate()

        lifecycleScope.launch {
            getTokenDigital()
            repeat(Int.MAX_VALUE) {

                if (tokenDigital != "") {
                    capture()
                    delay(300000)
                }

            }
        }

    }

    private suspend fun getTokenDigital() {
        val body = mapOf(
            "Username" to "digitalsignage",
            "Password" to "c6033006-575e-4739-8da9-a191a7d0768d",
        )
        val res = retrofit.getDigitalToken(
            "https://gateway-digitalsignage.tek4tv.vn/api/v2/oauth/sso/token", body
        )
        if (res.code() == 200 && res.isSuccessful) {
            tokenDigital = "Bearer ${res.body()?.noiDung?.token}"
        }
    }

    @OptIn(UnstableApi::class)
    @RequiresApi(Build.VERSION_CODES.N)
    fun capture(
    ) {
        // Lấy đúng video surface của PlayerView
        val surfaceView = (exoVideoLayout.videoSurfaceView as? SurfaceView) ?: return

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                // chỉ chụp khi surface đã valid
                doPixelCopy(surfaceView)
                surfaceView.holder.removeCallback(this)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder, format: Int, width: Int, height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })

        // Nếu đã valid thì chụp luôn
        if (surfaceView.holder.surface?.isValid == true) {
            doPixelCopy(surfaceView)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun doPixelCopy(
        surfaceView: SurfaceView,
    ) {
        val w = surfaceView.width.coerceAtLeast(1)
        val h = surfaceView.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        PixelCopy.request(surfaceView, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                saveBitmap(bitmap)
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun saveBitmap(
        bitmap: Bitmap,
    ) {
        // Create directory for saving
        val captureModel = CaptureScreenModel(
            Imei = hubManager.deviceImei,
            SourceId = GlobalProperties.getInst().destinationId,
            SourceName = GlobalProperties.getInst().destinationName,
            Base64Image = bitmapToBase64(bitmap)
        )
        try {
            lifecycleScope.launch {
                val url = "https://gateway-digitalsignage.tek4tv.vn/api/v2/digital/capture"
                val res = retrofit.postScreenCapture(
                    url,
                    tokenDigital,
                    captureModel,
                )
                if (res.isSuccessful) {
                    Log.d("captureInfo", "saveBitmap S: ${res}")
                }
                if (res.code() == 401) {
                    tokenDigital = ""
                    getTokenDigital()
                    if (tokenDigital != "") {
                        retrofit.postScreenCapture(
                            url,
                            tokenDigital,
                            captureModel,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("captureInfo", "saveBitmap ERR: ${e.message}")
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

    @RequiresApi(Build.VERSION_CODES.N)
    private fun initDataByScreen() {
        checkInternetAndTime()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun checkInternetAndTime() {
        val tvCheckInternet = findViewById<TextView>(R.id.tvCheckInternet)
        val tvMacAddress = findViewById<TextView>(R.id.tvMac)
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
        Log.d("alarmx", "Cancel cancelAllScheduledListAlarm onStop")
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
                Log.d("alarmx", "start: $listKey")
                if (listKey != null) {
                    playerManager.setTimeFrame(listKey)
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
                    viewModel.rescheduleMonthlyPlaylist()
                    hubManager.reconnectToServer()
                    serialPortController.turnOffPA()
                    delay(120000)
                    Log.e("alarmx", "Hub restart")
                    serialPortController.hardRestart()
                }

            }
            MediaScheduleReceiver.ACTION_START_DEFAULT_SCENARIO -> {
                Log.e("alarmx", "ACTION_START_DEFAULT_SCENARIO")
//                playerManager.startFirstTimeFrame()
                playerManager.playDefault()
            }
            MediaScheduleReceiver.ACTION_END_DEFAULT_SCENARIO -> {
                Log.e("alarmx", "ACTION_END_DEFAULT_SCENARIO")
                playerManager.stopAllPlayer()
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
        viewModel.content.observe(this) {
            viewModel.downloadMedias()
            //Check xem có đang phát khẩn cấp không
            //Nếu có thì phát media khẩn cấp
            //Media khẩn cấp lưu share pref
            if (ForceMedia.hasValidForceMedia(applicationContext)) {
                val forceMedia = ForceMedia.getLocalForceMedia(applicationContext)
                if (forceMedia != null) {
                    mediaScheduler.scheduleForceMediaAlarm(forceMedia)
                    playerManager.playForceMedia(applicationContext, forceMedia, mediaScheduler)
                    return@observe
                }
            }
            //default
            mediaScheduler.scheduleAllAlarms()

            playerManager.startFirstTimeFrame()

            sendLogDataHub()

            //all load done, check
            viewModel.checkPlaylist()
        }
    }

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
                HubCommand.ROTATION -> {
                    if (responseHub.message != null) {
                        Log.d("rotation", responseHub.message!!)
                        try {
                            val requireOrientation = responseHub.message!!

                            ConfigUtil.putString(this, "pref_orientation", requireOrientation)
                            lifecycleScope.launch {
                                Toast.makeText(
                                    baseContext, "Màn hình sẽ xoay sau 5s", Toast.LENGTH_SHORT
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
                    playerManager.stopAllPlayer()
                    playerManager.resetPlayMode()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    ForceMedia.clearMonthlyData(applicationContext)
                    Log.d("sds", "UPDATE_MONTHLY_WSS mess: $message ")
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

                }

                HubCommand.DELETE_MONTHLY_LIST_WSS -> {
                    Log.d("sds", "handleFromCommandServer: $message ")

                    playerManager.stopAllPlayer()

                    playerManager.resetPlayMode()
                    cancelAllAlarm()
                    Log.d("alarmx", "Cancel cancelAllScheduledListAlarm DELETE_MONTHLY_LIST_WSS")
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    ForceMedia.clearMonthlyData(applicationContext)

                }
                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {
                    onPlayForce(responseHub)
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
                            playerManager.stopAllPlayer()
                            playerManager.resetPlayMode()
                        }
                        //dung phat va chuyen sang ban tin tiep
                        1 -> {
                            playerManager.playNextMedia()
                        }
                        //Tạm dừng nội dung đang phát
                        2 -> {
//                            playerManager.pauseMedia()
                        }

                        3 -> {
                            playerManager.resumeMedia()
                        }
                    }
                }

                HubCommand.RESTART -> {
                    serialPortController.hardRestart()
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
                                    baseContext, DEFAULT_ANDROID_VOLUME, "10"
                                ) ?: "10").toInt()
                            )
                            globalProperties.setAndroidPreVolume(roundToNearestNumber(responseHub.message!!.toInt()).toString())
                            globalProperties.setTempVolume(
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
                            applicationContext, DEFAULT_VOLUME, responseHub.message!!
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

                HubCommand.READ_DEVICE_INFO -> {
                    serialPortController.readDeviceInfo()
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
                    Log.d("alarmx", "Cancel cancelAllScheduledListAlarm STOP_FORCE")
                    ForceMedia.deleteForeMediaPref(applicationContext)
//                    viewModel.getPlayerContent("", false)
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
        mediaScheduler.cancelAllScheduledListAlarm()
    }

    private fun setMaxVolume() {
//        Log.d("UpdateVolume", "set max: ${GlobalProperties.getInst().androidVolume}")
//        changeVolume(GlobalProperties.getInst().androidVolume.toInt())
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
        val retrofit = RetrofitInstance.authService
        lateinit var appStorageManager: AppStorageManager
    }
}
