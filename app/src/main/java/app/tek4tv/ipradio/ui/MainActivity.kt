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
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.HubManager
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.core.players_helper.IPlayerManagerListener
import app.tek4tv.ipradio.core.players_helper.PlayersManager
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
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
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

    private lateinit var vlcVideoLayout: VLCVideoLayout
    private val globalProperties = GlobalProperties.getInst()
    private val UART_S1_NAME = "/dev/ttyS1"
    private val UART_S4_NAME = "/dev/ttyS4"


    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            serialPortController.turnOffPA()
            hubManager.currentMediaItem = null
            Log.d("PMLOG", "playerCallback: time over")
        }


        //Dừng phát khẩn cấp
        override fun onEndForceMedia() {
            ForceMedia.deleteForeMediaPref(applicationContext)
            startPlayingMedia()

        }

        //Lỗi luồng live
        override fun onLiveError(mediaItem: MediaItemModel) {
            hubManager.currentMediaItem = mediaItem
            serialPortController.turnOffPA()
        }


        override fun onMediaStopPlaying(mediaItem: MediaItemModel, duration: Long) {
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onMediaStopPlaying ")
        }

        override fun onPlayMediaSuccess(mediaItem: MediaItemModel?) {
            Log.d("onPlayMediaLog", "onPlayMediaSuccess: onPlayMediaSuccess ")

            if (mediaItem != null) {
                hubManager.currentMediaItem = mediaItem
                serialPortController.turnOnPAVod()
                hubManager.sendLogMediaSuccess(mediaItem)
            }
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
            vlcVideoLayout,
            viewModel.mediaRepo
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
            repeat(Int.MAX_VALUE)
            {
                capture()
                delay(300000)

            }
        }
//        lifecycleScope.launch {
//            delay(5000)
//            hubManager.convertToHubCommand(
//                hubManager.loadJSONFromAsset(
//                    baseContext,
//                    "sample.json"
//                )!!
//            )
//        }


    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun capture() {
        try {
            val surfaceView = (vlcVideoLayout.getChildAt(
                0
            ) as FrameLayout).children.filter { it is SurfaceView }
                .first() as SurfaceView

            val bitmap = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888)

            PixelCopy.request(surfaceView, bitmap, {
                saveBitmap(bitmap)
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveBitmap(bitmap: Bitmap) {
        // Create directory for saving
        val captureModel = CaptureScreenModel(
            Imei = hubManager.deviceImei,
            SourceId = GlobalProperties.getInst().destinationId,
            SourceName = GlobalProperties.getInst().destinationName,
            Base64Image = bitmapToBase64(bitmap)
        )
        try {
            lifecycleScope.launch {
                val res = retrofit.postScreenCapture(
                    captureModel,
                    "https://v2-provider-data-warehouse.tek4tv.vn/DigitalDataLog/digital/capture"
                )
                Log.d("captureError", "saveBitmap S: ${res}")
                // Log.d("captureError", "saveBitmap S: ${captureModel.Base64Image}")
            }

        } catch (e: Exception) {
            Log.d("captureError", "saveBitmap ERR: ${e.message}")
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
        viewModel.content.observe(this)
        {
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
        val typeListMediaItem =
            Types.newParameterizedType(List::class.java, MediaItemModel::class.java)
        val jsonListMediaItemAdapter: JsonAdapter<List<MediaItemModel>> = moshi.adapter<List<MediaItemModel>>(typeListMediaItem)
        val retrofit = RetrofitInstance.authService
        val typeMap =
            Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        val jsonMapAdapter: JsonAdapter<Map<String, String>> =
            moshi.adapter(typeMap)
        lateinit var appStorageManager: AppStorageManager
    }
}
