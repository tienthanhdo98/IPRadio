package app.tek4tv.ipradio.ui


import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
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
import app.tek4tv.ipradio.databinding.ActivityMainBinding
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.CrashHandler.Companion.restartApp
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.StringValue.Companion.PAStatusOff
import app.tek4tv.ipradio.utils.StringValue.Companion.PAStatusOn
import app.tek4tv.ipradio.utils.StringValue.Companion.UART_S1_NAME
import app.tek4tv.ipradio.utils.StringValue.Companion.UART_S4_NAME
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.downloadUpdateApk
import app.tek4tv.ipradio.utils.media.MediaCapture
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.secToReadable
import app.tek4tv.ipradio.utils.setMobileDataEnabled
import app.tek4tv.ipradio.utils.setWifeDataEnabled
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import app.tek4tv.ipradio.utils.storage.RECORDED_AUDIO_FOLDER_NAME
import app.tek4tv.ipradio.utils.storage.deleteAFileInFolder
import app.tek4tv.ipradio.viewmodels.MainViewModel
import com.squareup.moshi.Moshi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject


@AndroidEntryPoint
@RequiresApi(Build.VERSION_CODES.N)
class MainActivity : StreamBaseFrag(), AlarmListener {

    private val UPDATE_PERMISSION_REQUEST_CODE = 1

    private val permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.REQUEST_INSTALL_PACKAGES,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.RECORD_AUDIO,
    )

    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var moshi: Moshi

    @Inject
    lateinit var authService: AuthService

    private lateinit var hubManager: HubManager

    private lateinit var playerManager: PlayersManager

    private lateinit var serialPortController: SerialPortController

    private lateinit var appStorageManager: AppStorageManager

    private lateinit var mediaCapture: MediaCapture

    private lateinit var mediaScheduler: MediaScheduler

    private lateinit var rootLayout: FrameLayout

    private val globalProperties = GlobalProperties.getInst()

    private lateinit var binding: ActivityMainBinding


    // event media
    private val playerCallback = object : IPlayerManagerListener {
        override fun onTimeOver() {
            Log.d("loghub", "onTimeOver: come")
            serialPortController.turnOffPA()
            serialPortController.writeForceSetVolume(globalProperties.volume)
            hubManager.currentMediaItem = null
        }

        //Phát FM
        override fun onPlayTuner(mediaItem: MediaItem) {
            try {
                val tunerType = if (mediaItem.getMediaType() == MediaType.AM) "0" else "1"
                val pipsData = "${globalProperties.piCode},${globalProperties.psName}"
                val tunerData = "$tunerType,${mediaItem.body},${globalProperties.volume}"
                val paState =
                    if (serialPortController.playMode != SerialPortController.MODE_NONE) PAStatusOff
                    else PAStatusOn
                hubManager.currentMediaItem = mediaItem

                if (!serialPortController.isUart1) {
                    lifecycleScope.launch {
                        delay(11000)
                        serialPortController.playTuner(pipsData, tunerData)
                    }
                } else {
                    serialPortController.playTuner(pipsData, tunerData)
                }

                hubManager.sendLogMediaSuccess(mediaItem, paState)
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
            hubManager.currentMediaItem = mediaItem
            serialPortController.turnOffPA()
            Log.d("turnPA", "live error")
        }

        override fun onMediaStopPlaying(mediaItem: MediaItem, duration: Long) {
            hubManager.currentMediaItem = null
        }


        override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
            if (mediaItem != null) {

                hubManager.currentMediaItem = mediaItem

                if (mediaItem.getMediaType() != MediaType.FM) {
                    serialPortController.turnOnPAVod()
                }
                val paState =
                    if (serialPortController.playMode != SerialPortController.MODE_NONE) PAStatusOff
                    else PAStatusOn

                hubManager.sendLogMediaSuccess(mediaItem, paState)

                viewModel.currentMedia = mediaItem

            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        if (needGrantPermission()) requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)

        val hubComm = object : HubComm {
            override fun forceRestartApp(countHardRestart: Int) {
                lifecycleScope.launch {
                    //sau khi 30p reset app thì sẽ reset phần cứng.
                    Log.d("resetApp", "force reset $countHardRestart")
                    if (countHardRestart >= 6) {
                        ConfigUtil.putInt(baseContext, ConfigUtil.CountForHardReset, 0)
                        serialPortController.hardRestart()
                    }
                    ConfigUtil.putInt(
                        baseContext, ConfigUtil.CountForHardReset, countHardRestart + 1
                    )
                    Log.d("turnPA", "force reset")
                    delay(3000)
                    //bật tắt lại mạng di động
                    setMobileDataEnabled(false)
                    setWifeDataEnabled(true)
                    delay(3000)
                    setMobileDataEnabled(true)
                    //lấy lại imei trong trường hợp lỗi khi lấy imei
                    ConfigUtil.removeSharef(baseContext, Utils.DEVICE_ID)
                    delay(3000)
                    restartApp(MyApp.instance!!, this@MainActivity)
                }
            }

            override fun forceRestartDevice() {
                serialPortController.hardRestart()
            }

        }

        appStorageManager = AppStorageManager(applicationContext)
        mediaCapture = MediaCapture(applicationContext, lifecycleScope)

        hubManager = HubManager(
            authService, baseContext, CoroutineScope(Dispatchers.Main)
        ) { command, message ->
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

        rootLayout = findViewById(R.id.root_layout)
        playerManager = PlayersManager(
            applicationContext, viewModel.mediaRepo
        )
        playerManager.playerManagerListener = playerCallback

        NetworkUtils.instance.startNetworkListener(this)

        initHubConnect()
        registerObservers()

        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        alarmListener = this

        setMaxVolume()

        hideSystemUI()

        showMac()


    }

    override fun startStopWatch() {
        var secondPassed = 0L
        lifecycleScope.launch {
            while (isStreaming) {
                delay(1000)
                secondPassed++
                binding.txtTime.text = secToReadable(secondPassed)
            }
            binding.txtTime.text = "00:00"
        }
    }


    private fun showMac() {
        val tvMacAdress = findViewById<TextView>(R.id.tvMac)
        val imei = hubManager.deviceImei
        tvMacAdress.text = "$imei - Ver: ${hubManager.versionName}"
    }


    override fun onStop() {
        super.onStop()
        playerManager.onActivityStop()
        stopStream()
//        serialPortController.disConnect()
        cancelAllAlarm()

    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.onActivityDestroy()
        stopStream()
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

            MediaScheduleReceiver.ACTION_END_FORCE_MEDIA -> {
                playerManager.stopForceMedia()
                Log.d("alarmx", "stop force")
                Log.d("turnPA", "on alarm end force media")
            }


            MediaScheduleReceiver.ACTION_CHECKHUB_RESET -> {
                lifecycleScope.launch {
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
        viewModel.content.observe(this) {
            Log.d("checkcheck", "registerObservers content")
            cancelAllAlarm()
            viewModel.downloadMedias()
            //Check xem có đang phát khẩn cấp không
            //Nếu có thì phát media khẩn cấp
            //Media khẩn cấp lưu share pref
            if (ForceMedia.hasValidForceMedia(applicationContext)) {
                mediaScheduler.scheduleForceMediaAlarm()
                val forceMedia = ForceMedia.getLocalForceMedia(applicationContext)
                if (forceMedia != null) playerManager.playForceMedia(
                    applicationContext,
                    forceMedia,
                    mediaScheduler
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

    fun startPlayingMedia() {
        viewModel.getPlayerContentWss()
    }

    private fun initHubConnect() {
        hubManager.connectToServer()
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


            when (command) {
                HubCommand.UPDATE_MONTHLY_WSS -> {
                    clearDatastore()
                    playerManager.resetPlayMode()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    ForceMedia.clearMonthlyData(applicationContext)
                    Log.d("sds", "handleFromCommandServer: $message ")
                    viewModel.updateMonthlyScenarioWss(message)
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
                    CoroutineScope(Dispatchers.Main).launch {
                        playerManager.stopAllPlayer()
                    }
                    hubManager.currentMediaItem = null

                    playerManager.resetPlayMode()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    ForceMedia.clearMonthlyData(applicationContext)
                }

                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {
                    lifecycleScope.launch(Dispatchers.Main) {
                        onPlayForce(responseHub)
                    }
                }

                HubCommand.TURN_ON_PA -> {
                    Log.i("TURN_ON_PA", responseHub.message!!)
                    val mess = responseHub.message!!.toInt()
                    if (mess == PAStatusOn.toInt()) {
                        serialPortController.turnOnPAVod()

                    } else {
                        serialPortController.turnOffPA()

                    }
                }

                HubCommand.CONTROL_MEDIA -> {
                    //Vi thread se an theo websocket nen phai quay lai main de dieu khien player
                    //0,1,2,3
                    //{"IMEI":"18:93:7F:E5:F5:77","Message":"0"}
                    val mess = responseHub.message!!.toInt()
                    if (playerManager.playMode != PLAY_MODE_FORCE) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            when (mess) {
                                //dung phat
                                0 -> {
                                    cancelAllAlarm()
                                    playerManager.stopAndEndTimeFrame()

                                    playerManager.resetPlayMode()
                                    lifecycleScope.launch {
                                        viewModel.mediaRepo.clearPlaylist()
                                    }

                                }

                                2 -> {

                                    playerManager.pausePlayer()


                                }
                                //dung phat va chuyen sang ban tin tiep
                                1 -> {
                                    Log.d(
                                        "CONTROL_MEDIA",
                                        "isInTimeFrame: ${playerManager.isTimeOver}"
                                    )
                                    if (!playerManager.isTimeOver) {
                                        playerManager.playNextMedia()
                                    }
                                }

                                //Phát tiếp nội dung dừng trước đó
                                3 -> {
                                    if (!playerManager.isTimeOver) {
                                        playerManager.resume()
                                    }
                                }
                            }
                        }
                    }

                }

                HubCommand.FM -> {
                    onPlayForce(responseHub)
                }

                HubCommand.RESTART -> {
                    lifecycleScope.launch {
                        val seconds = responseHub.message?.trim()?.toLongOrNull() ?: 0L
                        delay(seconds * 1000L)
                        Log.d("RESTART", "handleFromCommandServer: reset")
                        serialPortController.hardRestart()
                    }

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
                        globalProperties.saveVolume(responseHub.message!!)
                    }
                }

                HubCommand.UPDATE_VERSION -> {
                    Log.d("update", responseHub.message!!)
                    downloadUpdateApk(responseHub.message!!, this, serialPortController)
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


                HubCommand.SET_ENCRYPTED_VOLUME_DEVICE -> {
                    if (responseHub.message != null) {
                        serialPortController.writeForceSetVolume(responseHub.message!!)
                    }
                }

                HubCommand.STOP_FORCE -> {
                    playerManager.stopAllPlayer()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    viewModel.getPlayerContentWss()
                }


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
        mediaScheduler.cancelAllAlarm()
    }

    private fun setMaxVolume() {
        try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
        } catch (_: Exception) {
            Log.e("Main", "Error setting max volume")
        }
    }

    private fun clearDatastore() {
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

    companion object {
        var alarmListener: AlarmListener? = null

    }

}