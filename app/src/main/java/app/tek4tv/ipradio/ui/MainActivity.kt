package app.tek4tv.ipradio.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.*
import app.tek4tv.ipradio.core.players_helper.ExoMediaPlayer
import app.tek4tv.ipradio.core.players_helper.IPlayerEventListener
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import app.tek4tv.ipradio.core.players_helper.VlcMediaPlayer
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.viewmodels.MainViewModel
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.abs


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
        Manifest.permission.WRITE_SETTINGS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.RECORD_AUDIO
    )

    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var moshi: Moshi

    private lateinit var hubManager: HubManager
    private lateinit var playerManager: PlayersManager

    private lateinit var serialPortController: SerialPortController

    private lateinit var appStorageManager: AppStorageManager

    private lateinit var logUtils: LogUtils

    private var volume = "80"

    var locationTracker = LocationTracker()

    private lateinit var mediaCapture: MediaCapture

    private lateinit var mediaScheduler: MediaScheduler

    private lateinit var rootLayout: FrameLayout

    private val globalProperties = GlobalProperties.getInst()

    private val playerCallback = object : PlayerCallback {
        override fun onPlayNewMedia(index: Int, mediaItem: MediaItem) {
            hubManager.playlistIndex = index
            hubManager.currentMediaItem = mediaItem

            val displayName = mediaItem.name ?: ""
            hubManager.curMediaName = displayName

            if (globalProperties.logEnabled) {
                val paState =
                    if (serialPortController.playMode != SerialPortController.MODE_NONE) "1"
                    else "0"

                try {
                    if (viewModel.currentMedia == null) {
                        logUtils.logPlayMedia(mediaItem, globalProperties.volume, paState)
                    } else {
                        if (viewModel.currentMedia!!.id != mediaItem.id)
                            logUtils.logPlayMedia(mediaItem, globalProperties.volume, paState)
                    }

                    viewModel.currentMedia = mediaItem
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (mediaItem.getMediaType() != MediaType.FM
                && serialPortController.playMode != SerialPortController.MODE_VOD_LIVE
                && !playerManager.isLiveStreamError
            ) {
                serialPortController.turnOnPAVod()
            }
        }

        override fun onTimeOver() {
            serialPortController.turnOffPA()

            hubManager.curMediaName = ""
            hubManager.currentMediaItem = null
        }

        override fun onStartTimeFrame(frame: String) {
            if (globalProperties.logEnabled) {
                logUtils.logStartTimeFrame(frame)
                logUtils.logPerformance(applicationContext, appStorageManager)
            }
        }

        override fun onEndTimeFrame(frame: String) {
            if (globalProperties.logEnabled) {
                logUtils.logEndTimeFrame(frame)
                logUtils.logPerformance(applicationContext, appStorageManager)
            }
        }

        override fun onPlayTuner(mediaItem: MediaItem) {
            try {
                val tunerType = if (mediaItem.getMediaType() == MediaType.AM) "0"
                else "1"

                val pipsData = "${globalProperties.piCode},${globalProperties.psName}"
                val tunerData = "$tunerType,${mediaItem.path},${globalProperties.volume}"

                serialPortController.invokeDeviceFunc(
                    Define.FUNC_WRITE_SETUP_PI_PS_NAME,
                    pipsData,
                    true
                )

                serialPortController.apply {
                    invokeDeviceFunc(Define.FUNC_WRITE_PLAY_TUNER, tunerData)
                    playMode = SerialPortController.MODE_TUNER
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onEndForceMedia() {
            ForceMedia.deleteForeMediaPref(applicationContext)
            startPlayingMedia()
        }

        override fun rescheduleMedias() {
            mediaScheduler.cancelAllAlarm()
            val monthlyScenarioUrl = ConfigUtil.getString(
                applicationContext,
                ConfigUtil.MONTHLY_SCENARIO_URL, ""
            ) ?: return

            viewModel.updateMonthlyScenario(monthlyScenarioUrl)
        }

        override fun onLiveError() {
            hubManager.curMediaName = ""
            hubManager.currentMediaItem = null

            serialPortController.turnOffPA()
        }

        override fun requestTurnOnPa() {
            serialPortController.turnOnPAVod()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        disableSSL()

        if (needGrantPermission()) requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)

        val hubComm = object : HubComm {
            override fun forceRestartApp() {
                CrashHandler.restartApp(MyApp.instance!!, this@MainActivity)
            }

            override fun forceRestartDevice() {
                serialPortController.hardRestart()
            }
        }

        logUtils = LogUtils.getInst()
        logUtils.logStartApp()

        hubManager = HubManager(hubComm) { command, message ->
            handleFromCommandServer(command, message)
        }

        serialPortController = SerialPortController(applicationContext, lifecycleScope, hubManager)
        serialPortController.connectToSerialPort()

        rootLayout = findViewById(R.id.root_layout)
        playerManager = PlayersManager(applicationContext, rootLayout, viewModel.mediaRepo) //PlayerManager(applicationContext, viewModel, rootLayout)
        playerManager.playerCallback = playerCallback

        hubManager.hubEventObserver.add(playerManager)

        NetworkUtils.instance.startNetworkListener(this)

        initHubConnect()
        registerObservers()

        appStorageManager = AppStorageManager(applicationContext)

        mediaCapture = MediaCapture(applicationContext, lifecycleScope)

        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        alarmListener = this
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

    override fun onStart() {
        super.onStart()
        //testVlc()
        startPlayingMedia()
    }

    fun testVlc() {
        val vlcPlayer = ExoMediaPlayer(applicationContext, object : IPlayerEventListener {
            override fun requestTurnOnPaLive() {
                Log.d("ExoMediaPlayer", "requestTurnOnPaLive1")
            }

            override fun onPlayerError(e: Exception?, isLiveError: Boolean) {
                Log.d("ExoMediaPlayer", "onPlayerError")
            }

            override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
                Log.d("ExoMediaPlayer", "onPlayMediaSuccess")
            }

            override fun onMediaEndReached(mediaItem: MediaItem?) {
                Log.d("ExoMediaPlayer", "onMediaEndReached")
            }
        })

        val mediaItem = MediaItem(
            path = "https://ll4mfr7mnaliv.vcdn.cloud/laichau/radio/playlist.m3u8",
            type = "LIVE",
            fixTime = "16:39:00",
            duration = "01:00:00"
        )
        vlcPlayer.playMedia(mediaItem)
    }

    override fun onAlarm(intent: Intent) {
        when (intent.action) {
            MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", listKey)

                playerManager.setTimeFrame(listKey, null)
            }

            MediaScheduleReceiver.ACTION_END_PLAYLIST -> {
                val listKey = intent.getStringExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY)
                Log.d("alarmx", "end: $listKey")

                playerManager.stopPlaylist(listKey)
            }

            /*MediaScheduleReceiver.ACTION_PLAY_MEDIA -> {
                val mediaIndex = intent.getIntExtra(MediaScheduleReceiver.SCHEDULED_MEDIA_INDEX, 0)
                Log.d("alarmx", "start media: $mediaIndex")

                playerManager.playScheduledMediaByIndex(mediaIndex)
            }*/

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
                mediaScheduler.cancelAllAlarm()
                //mediaScheduler.scheduleAllAlarms(

                val monthlyScenarioUrl = ConfigUtil.getString(
                    applicationContext,
                    ConfigUtil.MONTHLY_SCENARIO_URL, ""
                ) ?: return

                viewModel.updateMonthlyScenario(monthlyScenarioUrl)
            }
        }
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
        viewModel.content.observe(this)
        {
            viewModel.downloadMedias()

            if (ForceMedia.hasValidForceMedia(applicationContext)) {
                mediaScheduler.scheduleForceMediaAlarm()
                val forceMedia = ForceMedia.getLocalForceMedia(applicationContext)
                if (forceMedia != null)
                    playerManager.playForceMedia(forceMedia)

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
        viewModel.getPlayerContent("", false)
    }

    private fun initHubConnect() {
        NetworkUtils.instance.mNetworkLive.observe(this) { isConnected ->
            if (isConnected) {
                //checkTime()
                hubManager.connectToHub()
                locationTracker.getLocation(this@MainActivity)
            }
        }
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

            when (command) {
                HubCommand.UPDATE_LIST -> {
                    val mess = responseHub.message!!

                    playerManager.stopAllPlayer()

                    globalProperties.saveProperties(responseHub)

                    serialPortController.writeForceSetVolume(responseHub.volume)

                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)

                    viewModel.getPlayerContent(mess, true)
                }

                HubCommand.UPDATE_MONTHLY_LIST -> {
                    val mess = responseHub.message!!

                    playerManager.stopAllPlayer()
                    cancelAllAlarm()
                    ForceMedia.deleteForeMediaPref(applicationContext)
                    viewModel.updateMonthlyScenario(mess, true)
                    ConfigUtil.putString(applicationContext, ConfigUtil.MONTHLY_SCENARIO_URL, mess)
                }
                HubCommand.LIVE -> {
                    serialPortController.writeForceSetVolume(globalProperties.volume)
                    playForceMedia(responseHub)
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
                    serialPortController.writeForceSetVolume(globalProperties.volume)
                    playForceMedia(responseHub)
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

                HubCommand.GET_AUDIO_PATH -> {
                    val connectionId = responseHub.message
                    if (connectionId != null) {
                        val audioList = Utils.toJsonList(moshi, appStorageManager.getAllMusicPath())
                        hubManager.sendHubDirectMessage(
                            connectionId, Utils.GET_AUDIO_PATH, audioList
                        )
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

                HubCommand.DELETE_ALL_AUDIO -> {
                    appStorageManager.deleteAllMusic()
                }

                HubCommand.DELETE_ALL_MEDIA -> {
                    appStorageManager.deleteAllMedia()
                }

                HubCommand.RECORD -> {
                    val shouldStart = responseHub.start!! == "1"

                    if (shouldStart) {
                        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()

                        mediaCapture.startCaptureAudio()
                    } else {
                        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
                    }
                }

                HubCommand.GET_RECORD -> {
                    val connectionId = responseHub.message!!
                    val recordedAudioList = Utils.toJsonList(moshi, appStorageManager.getAllRecordedAudioPath())
                    hubManager.sendHubDirectMessage(
                        connectionId, Utils.GET_RECORD,
                        recordedAudioList
                    )
                }

                HubCommand.UPLOAD_RECORDED -> {
                    val filePath = responseHub.message!!
                    mediaCapture.updateRecordedAudio(filePath)
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
            }

            if (command != HubCommand.PONG && command != HubCommand.UPDATE_STATUS && globalProperties.logEnabled) {
                logUtils.logHubCommand(command, responseHub.message ?: "")
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelAllAlarm() {
        if (ForceMedia.hasValidForceMedia(applicationContext)) mediaScheduler.cancelForceMediaAlarm()
        else mediaScheduler.cancelAllAlarm()
    }

    private fun playForceMedia(responseHub: ResponseHub) {
        try {
            val duration = responseHub.duration
            val type = responseHub.type
            val level = responseHub.level
            val mediaName = responseHub.name

            //if we are playing a force media that has a higher level, skip playing this
            if (playerManager.currentPlaylistKey == PlayerManager.PLAY_MODE_FORCE) {
                val value = ConfigUtil.getString(applicationContext, ConfigUtil.FORCE_MEDIA, "")
                if (value != null && value != "") {
                    val currentForceMedia = ForceMedia.jsonAdapter.fromJson(value)

                    if (currentForceMedia != null && currentForceMedia.level < level) return
                }
            }

            val forceMedia = ForceMedia(responseHub.message!!, duration, type, level).apply {
                val mediaEndTime = Calendar.getInstance().apply {
                    add(Calendar.MINUTE, duration)
                }
                endTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(mediaEndTime.time)
                fixTime = SimpleDateFormat("HH:mm:ss").format(Date())
                name = mediaName
            }
            playForceMedia(forceMedia)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playForceMedia(forceMedia: ForceMedia) {
        try {
            cancelAllAlarm()

            forceMedia.saveItemToPref(applicationContext)

            mediaScheduler.scheduleForceMediaAlarm()

            playerManager.playForceMedia(forceMedia)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val LOG_TAG = "MainActivity"
        var alarmListener: AlarmListener? = null
    }
}

