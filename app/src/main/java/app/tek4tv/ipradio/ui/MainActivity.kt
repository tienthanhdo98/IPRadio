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
import app.tek4tv.ipradio.core.stream.StreamBackManager
import app.tek4tv.ipradio.databinding.ActivityMainBinding
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.ForceMedia.Companion.getLocalForceMedia
import app.tek4tv.ipradio.model.McuLogData
import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.network.ApiService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.CrashHandler.Companion.restartApp
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.StringValue.Companion.PAStatusOff
import app.tek4tv.ipradio.utils.StringValue.Companion.PAStatusOn
import app.tek4tv.ipradio.utils.StringValue.Companion.UART_S1_NAME
import app.tek4tv.ipradio.utils.StringValue.Companion.UART_S4_NAME
import app.tek4tv.ipradio.utils.StringValue.Companion.scheduler2
import app.tek4tv.ipradio.utils.StringValue.Companion.scheduler3
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.downloadUpdateApk
import app.tek4tv.ipradio.utils.media.MediaCapture
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.setEthernetEnabled
import app.tek4tv.ipradio.utils.setMobileDataEnabled
import app.tek4tv.ipradio.utils.setPreferredMode
import app.tek4tv.ipradio.utils.setWifiDataEnabled
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import app.tek4tv.ipradio.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
@RequiresApi(Build.VERSION_CODES.N)
class MainActivity : AppCompatActivity(), AlarmListener {

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
    lateinit var authService: ApiService

    private lateinit var hubManager: HubManager

    private lateinit var playerManager: PlayersManager

    private lateinit var streamBackManager: StreamBackManager

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
        override fun onPlayTuner(mediaItem: MediaFile) {
            try {
//                val tunerType = if (mediaItem.getMediaType() == MediaFileType.AM) "0" else "1"
                val pipsData = "${globalProperties.piCode},${globalProperties.psName}"
                val tunerData = "1,${mediaItem.url},${globalProperties.volume}"
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

                val id = System.currentTimeMillis()
                val logs = listOf(
                    McuLogData(
                        id = id,
                        time = System.currentTimeMillis() / 1000.0,
                        type = 3,
                        detail = """{"m":${mediaItem.id}}"""
                    )
                )
                hubManager.sendLog(logs)
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
        override fun onLiveError(mediaItem: MediaFile?) {
//            hubManager.currentMediaItem = mediaItem
//            serialPortController.turnOffPA()
//            Log.d("turnPA", "live error")
        }


        override fun onPlayMediaSuccess(mediaItem: MediaFile?) {
            if (mediaItem != null) {

                hubManager.currentMediaItem = mediaItem

                val paState =
                    if (serialPortController.playMode != SerialPortController.MODE_NONE) PAStatusOff
                    else PAStatusOn

                //hubManager.sendLogMediaSuccess(mediaItem, paState)
                val id = System.currentTimeMillis()
                val logs = listOf(
                    McuLogData(
                        id = id,
                        time = System.currentTimeMillis() / 1000.0,
                        type = 3,
                        detail = """{"m":${mediaItem.id}}"""
                    )
                )
                hubManager.sendLog(logs)

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
                    setWifiDataEnabled(true)
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

        streamBackManager = StreamBackManager(applicationContext)

        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        alarmListener = this

        NetworkUtils.instance.startNetworkListener(this)

        initHubConnect()

        registerObservers()


        setMaxVolume()

        hideSystemUI()

        showMac()

    }

    private fun showMac() {
        val tvMacAdress = findViewById<TextView>(R.id.tvMac)
        val imei = hubManager.deviceImei
        tvMacAdress.text = "$imei - Ver: ${hubManager.versionName}"
        tvMacAdress.setOnClickListener {
            hubManager.handlerMessage(scheduler2)
            lifecycleScope.launch {
                delay(5000)
                hubManager.handlerMessage(scheduler3)
            }

        }
    }


    override fun onStart() {
        super.onStart()
        viewModel.getPlayerContentLocal()
//        viewModel.getPlayerContent()
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


    override fun onAlarm(intent: Intent) {
        when (intent.action) {
            MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", listKey!!)
                Log.d("Mediainfo", "onAlarm start $listKey")
                playerManager.setTimeFrame(listKey)
            }

            MediaScheduleReceiver.ACTION_END_PLAYLIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", "end: $listKey")
                Log.d("Mediainfo", "onAlarm end $listKey")
                playerManager.stopPlaylist(listKey!!)
            }

            MediaScheduleReceiver.ACTION_END_FORCE_MEDIA -> {
                playerManager.stopForceMedia()
                Log.d("MediaInfo", "End Live")
                Log.d("alarmx", "stop force")
                Log.d("turnPA", "on alarm end force media")
            }


            MediaScheduleReceiver.ACTION_CHECKHUB_RESET -> {
                lifecycleScope.launch {
                    mediaScheduler.cancelAllScheduledListAlarm()
//                    viewModel.rescheduleMonthlyPlaylist()
//                    hubManager.reconnectToServer()
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
        lifecycleScope.launch {
            viewModel.schedulerUpdated.collect {
                hubManager.currentMediaInfoListId = viewModel.getMediaInfoIdList()
                viewModel.downloadMedias()
                Log.d("checkcheck", "scheduler updated")
                if (ForceMedia.hasValidForceMedia(applicationContext)) {
                    val forceMediaInfo = getLocalForceMedia(applicationContext)
                    if (forceMediaInfo != null) {
                        playerManager.onPlayForceCommand(
                            applicationContext, forceMediaInfo, mediaScheduler
                        )
                    }
                    return@collect
                }
                cancelAllAlarm()
                mediaScheduler.scheduleAllAlarms()
                playerManager.getPlay()
            }
        }

    }

    fun startPlayingMedia() {
        viewModel.getPlayerContent()
    }

    private fun initHubConnect() {
        hubManager.connectToServer()
    }

    private fun handleFromCommandServer(command: String?, message: String?) {
        try {
//            if (command == null || command.isEmpty()) {
//                return
//            }
//            if (message == null || message.isEmpty()) {
//                return
//            }

            val responseHub = hubManager.responseHub


            when (command) {

                HubCommand.UPDATE_SCHEDULER -> {
                    clearDatastore()
                    playerManager.resetPlayMode()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    Log.d("sds", "handleFromCommandServer: $message ")
                    viewModel.updateScheduler(responseHub.listMediaInfo)
                }


                HubCommand.UPDATE_SETTING_CONFIG -> {
                    if (responseHub.networkConfig != null) {
                        val networkConfig = responseHub.networkConfig
                        when (networkConfig?.transport) {
                            //ETH
                            0 -> {
                                setWifiDataEnabled(false)
                                setMobileDataEnabled(false)
                                setEthernetEnabled(true)
                            }
                            //Mobile
                            1 -> {
                                setWifiDataEnabled(false)
                                setMobileDataEnabled(true)
                                setEthernetEnabled(false)
                                val sim = networkConfig.simType
                                when (sim) {
                                    0 -> {

                                    }

                                    1 -> {

                                    }
                                }
                                val mode = networkConfig.radioMode
                                when (mode) {
                                    0 -> {
                                        setPreferredMode(0)
                                    }

                                    2 -> {
                                        setPreferredMode(2)
                                    }

                                    3 -> {
                                        setPreferredMode(3)
                                    }

                                    4 -> {
                                        setPreferredMode(4)
                                    }
                                }
                            }
                            //Wifi
                            2 -> {
                                setWifiDataEnabled(true)
                                setMobileDataEnabled(false)
                                setEthernetEnabled(false)
                            }

                        }
                    }
                }

                HubCommand.UPDATE_SERVER_CONFIG -> {
                    if (responseHub.serverConfig != null) {
                        globalProperties.saveServerConfig(responseHub)
                    }
                }

                //Phát khẩn cấp luồng live
                HubCommand.LIVE -> {
                    if (responseHub.mediaInfo != null) {
                        onPlayForce(responseHub.mediaInfo!!)
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

                HubCommand.RESTART -> {
                    lifecycleScope.launch {
                        Log.d("handleFromCommandServer", "RESTART: ${message}")
                        if (message == "0") {
                            //reset thiết bị
                            serialPortController.hardRestart()
                        } else if (message == "1") {
                            //reset app
                            restartApp(MyApp.instance!!, this@MainActivity)
                        }

                    }

                }

                HubCommand.STREAM_BACK -> {
                    val message = responseHub.message
                    if (message != null) {
                        streamBackManager.handleStreamBack(
                            url = message, cmd = responseHub.level, timeout = responseHub.duration
                        )
                    }
                }

                HubCommand.SET_MUTE_DEVICE -> {
                    if (responseHub.message != null) {
                        serialPortController.setMuteDevice(responseHub.message!!)
                    }
                }

                HubCommand.SET_VOLUME_DEVICE -> {
                    if (responseHub.message != null && responseHub.message != "") {
                        Log.d("UpdateVolume", "handleFromCommandServer: ${responseHub.message}")
                        serialPortController.writeForceSetVolume(responseHub.message!!)
                        globalProperties.saveVolume(responseHub.message!!)
                    }
                }

                HubCommand.UPDATE_VERSION -> {
                    Log.d("update", responseHub.message!!)
                    downloadUpdateApk(responseHub.message!!, this, serialPortController)
                }

                HubCommand.READ_DEVICE_INFO -> {
                    serialPortController.readDeviceInfo()
                }

                HubCommand.DELETE_ALL_MEDIA -> {
                    appStorageManager.deleteAllMedia()
                    viewModel.deleteAllLocalScheduler()
                }
            }

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun onPlayForce(mediaInfo: MediaInfo) {
        //Tránh trường hợp âm lượng bị tắt
        serialPortController.writeForceSetVolume(globalProperties.volume)
        playerManager.onPlayForceCommand(applicationContext, mediaInfo, mediaScheduler)
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