package app.tek4tv.ipradio.ui

import android.Manifest
import android.app.usage.NetworkStatsManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.*
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.network.PlaylistService
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.viewmodels.MainViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val UPDATE_PERMISSION_REQUEST_CODE = 1
    private val PREF_ORIENTATION = "pref_orientation"

    private val permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.REQUEST_INSTALL_PACKAGES,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.READ_SMS,
        Manifest.permission.WRITE_SETTINGS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.SYSTEM_ALERT_WINDOW
    )

    private lateinit var mVideoLayout: VLCVideoLayout

    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var moshi: Moshi

    @Inject
    lateinit var playlistService: PlaylistService

    private lateinit var hubManager: HubManager
    private lateinit var playerManager: PlayerManager
    //private lateinit var audioManager: AudioManager

    private lateinit var serialPortController: SerialPortController

    private lateinit var appStorageManager: AppStorageManager

    private lateinit var preference: SharedPreferences

    private var volume = "100"
    private var version = "1.0.0"

    var locationTracker = LocationTracker()

    private var orientation: Int = 0

    private lateinit var mediaCapture: MediaCapture

    private lateinit var mediaScheduler: MediaScheduler

    private lateinit var rootLayout: FrameLayout

    private val playerCallback = object : PlayerCallback {
        override fun onPlayNewMedia(index: Int, mediaItem: MediaItem) {
            hubManager.playlistIndex = index
            hubManager.currentMediaItem = mediaItem

            if (mediaItem.getMediaType() != MediaType.TUNER
                && serialPortController.playMode != SerialPortController.MODE_VOD_LIVE) {
                serialPortController.invokeDeviceFunc(Define.FUNC_WRITE_PLAY_VOD_LIVE, volume)
                serialPortController.playMode = SerialPortController.MODE_VOD_LIVE
            }
        }

        override fun onTimeOver() {
            serialPortController.apply {
                invokeDeviceFunc(Define.FUNC_WRITE_PLAY_NO_SOURCE, "", true)
                playMode = SerialPortController.MODE_NONE
            }
        }

        override fun onStartTimeFrame(frame: String) {

        }

        override fun onEndTimeFrame(frame: String, isLastTimeFrame: Boolean) {

        }

        override fun onPlayTuner(mediaItem: MediaItem) {
            serialPortController.apply {
                invokeDeviceFunc(Define.FUNC_WRITE_PLAY_TUNER, mediaItem.path)
                playMode = SerialPortController.MODE_TUNER
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        preference = getPreferences(MODE_PRIVATE)

        if (needGrantPermission()) requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)

        hubManager = HubManager(lifecycleScope, this, moshi) { command, message ->
            handleFromCommandServer(command, message)
        }

        serialPortController = SerialPortController(applicationContext, lifecycleScope, hubManager)
        serialPortController.connectToSerialPort()

        rootLayout = findViewById(R.id.root_layout)
        playerManager = PlayerManager(applicationContext, viewModel, rootLayout)
        playerManager.playerCallback = playerCallback

        NetworkUtils.instance.startNetworkListener(this)

        initHubConnect()
        registerObservers()

        appStorageManager = AppStorageManager(applicationContext)

        mediaCapture = MediaCapture(applicationContext, lifecycleScope)

        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)
    }

    /* override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
         super.onActivityResult(requestCode, resultCode, data)
         if (requestCode == 69) {
             Toast.makeText(this, "capture", Toast.LENGTH_LONG).show()
             lifecycleScope.launch {
                 val isPortrait = orientation == 2 || orientation == 3
                 mediaCapture.captureImageMega(playlistService,
                     mediaProjectionManager.getMediaProjection(resultCode, data),
                     resources.displayMetrics.densityDpi, isPortrait)
             }
         }
     }*/

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
        startPlayingMedia()
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
            mediaScheduler.scheduleAllAlarms()
            playerManager.startFirstPlaylist()
        }
    }

    private fun startPlayingMedia() {
        viewModel.getPlayerContent("", "", false)
        viewModel.checkPlaylist()
    }


    private fun initHubConnect() {
        NetworkUtils.instance.mNetworkLive.observe(this) { isConnected ->
            if (isConnected) {
                if (hubManager.hubConnection == null) {
                    hubManager.createNewHubConnection()
                    Log.d("Connected", "Connected")
                }

                locationTracker.getLocation(this@MainActivity)
            } else {
                hubManager.hubConnection = null
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
            volume = responseHub.volume ?: ""

            val isImei = Utils.getDeviceId(applicationContext) == responseHub.imei
            if (isImei) {
                when (command) {
                    HubCommand.GET_LIST -> {
                    }
                    HubCommand.UPDATE_LIST -> {
                        val mess = responseHub.message!!
                        val str = mess.split(',')
                        val listUrl = str[0]
                        val musicUrl = str[1]

                        mediaScheduler.cancelAllAlarm()
                        //viewModel.playlistIndex = 0
                        viewModel.getPlayerContent(listUrl, musicUrl, true)
                    }
                    HubCommand.NEXT -> {
                    }
                    HubCommand.PREVIEW -> {
                    }
                    HubCommand.JUMP -> {
                        /* Log.d(command, message)
                        val id = responseHub.message!!.trim().toInt()
                        playMediaItemByIndex(id)*/
                    }
                    HubCommand.LIVE -> {

                    }
                    HubCommand.UPDATE_STATUS -> {
                        /*serialPortController.apply {
                            writeToDevice(buildReadMessage(Define.FUNC_WRITE_READ_STATUS_PARAM, ""))
                        }*/
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_READ_STATUS_PARAM,
                            "",
                            true
                        )
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
                    HubCommand.STOP -> {
                    }
                    HubCommand.PAUSE -> {
                    }
                    HubCommand.START -> {
                    }
                    HubCommand.RESTART -> {
                        /*serialPortController.apply {
                            writeToDevice(
                                buildReadMessage(
                                    Define.FUNC_WRITE_FORCE_RESTART_DEVICE,
                                    ""
                                )
                            )
                        }*/
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_FORCE_RESTART_DEVICE,
                            "", true
                        )
                    }
                    HubCommand.RELOAD -> {

                    }
                    HubCommand.SWITCH_MODE_FM -> {
                    }
                    HubCommand.SET_MUTE_DEVICE -> {
                        if (responseHub.message != null) {
                            /*serialPortController.apply {
                                writeToDevice(
                                    buildWriteMessage(
                                        Define.FUNC_WRITE_FORCE_SET_MUTE,
                                        responseHub.message!!
                                    )
                                )
                            }*/
                            serialPortController.invokeDeviceFunc(
                                Define.FUNC_WRITE_FORCE_SET_MUTE,
                                responseHub.message!!
                            )
                        }
                    }
                    HubCommand.SET_VOLUME_DEVICE -> {
                        if (responseHub.message != null) {
                            /*serialPortController.apply {
                                writeToDevice(
                                    buildWriteMessage(
                                        Define.FUNC_WRITE_FORCE_SET_VOLUME,
                                        responseHub.message!!
                                    )
                                )
                            }*/
                            serialPortController.invokeDeviceFunc(
                                Define.FUNC_WRITE_FORCE_SET_VOLUME,
                                responseHub.message!!
                            )
                        }
                    }

                    HubCommand.UPDATE_VERSION -> {
                        if (responseHub.message != null) {
                            Log.d("update", responseHub.message!!)
                            downloadUpdateApk(responseHub.message!!, this)
                        }
                    }


                    HubCommand.SET_TIME_OVER -> {
                        /*serialPortController.apply {
                            writeToDevice(buildReadMessage(Define.FUNC_WRITE_PLAY_NO_SOURCE, ""))
                        }*/
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_PLAY_NO_SOURCE,
                            "",
                            true
                        )
                    }

                    HubCommand.SET_TIME_ON -> {
                        /*serialPortController.apply {
                            writeToDevice(buildReadMessage(Define.FUNC_WRITE_PLAY_VOD_LIVE, volume))
                        }*/
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_PLAY_VOD_LIVE,
                            volume,
                            true
                        )
                    }

                    HubCommand.GET_APP_VERSION -> {
                        hubManager.sendMessage(version, Utils.APP_VERSION)
                    }


                    HubCommand.SETUP_PI_PS_NAME -> {
                        /*serialPortController.apply {
                            writeToDevice(
                                buildWriteMessage(
                                    Define.FUNC_WRITE_SETUP_PI_PS_NAME,
                                    responseHub.message ?: ""
                                )
                            )
                        }*/
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_SETUP_PI_PS_NAME,
                            responseHub.message ?: ""
                        )
                    }

                    HubCommand.PLAY_VOD_LIVE_FORCE -> {
                        val hubMessage = responseHub.message ?: ""
                        /*serialPortController.apply {
                            writeToDevice(
                                buildWriteMessage(
                                    Define.FUNC_WRITE_PLAY_VOD_LIVE_FORCE,
                                    hubMessage
                                )
                            )
                        }*/
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_PLAY_VOD_LIVE_FORCE,
                            hubMessage
                        )
                    }

                    HubCommand.PLAY_TUNER_LIVE_FORCE -> {
                        val hubMessage = responseHub.message ?: ""
                        /*serialPortController.apply {
                            writeToDevice(
                                buildWriteMessage(
                                    Define.FUNC_WRITE_PLAY_TUNER_FORCE,
                                    hubMessage
                                )
                            )
                        }*/
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_PLAY_TUNER_FORCE,
                            hubMessage
                        )
                    }

                    HubCommand.READ_DEVICE_INFO -> {
                        /*serialPortController.apply {
                            writeToDevice(buildReadMessage(Define.FUNC_WRITE_READ_DEVICE_INFO, ""))
                        }*/
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_READ_DEVICE_INFO,
                            "",
                            true
                        )
                    }

                    HubCommand.NETWORK_INFO -> {
                        val connectionId = responseHub.message
                        if (connectionId != null) {
                            val networkClass = NetworkUtils.getNetworkClass(applicationContext)
                            val dataUsage = NetworkUtils.getNetworkUsage(applicationContext)

                            val level = when (networkClass) {
                                "WIFI" -> NetworkUtils.getWifiSignalStrength(applicationContext)
                                "---" -> 0
                                else -> NetworkUtils.getCellSignalStrength(applicationContext)
                            }

                            val result = "$networkClass,$dataUsage,$level"

                            hubManager.sendHubDirectMessage(
                                connectionId, Utils.NETWORK_INFO,
                                result
                            )
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
                            val audioList = toJsonList(appStorageManager.getAllMusicPath())
                            hubManager.sendHubDirectMessage(
                                connectionId, Utils.GET_AUDIO_PATH,
                                audioList
                            )
                        }
                    }

                    HubCommand.GET_MEDIA_PATH -> {
                        val connectionId = responseHub.message
                        if (connectionId != null) {
                            Log.d("connectionId", connectionId)
                            val mediaList = toJsonList(appStorageManager.getAllMediaPath())
                            hubManager.sendHubDirectMessage(
                                connectionId, Utils.GET_MEDIA_PATH,
                                mediaList
                            )
                        }
                    }

                    HubCommand.DELETE_ALL_AUDIO -> {
                        appStorageManager.deleteAllMusic()
                    }

                    HubCommand.DELETE_ALL_MEDIA -> {
                        appStorageManager.deleteAllMedia()
                    }

                    HubCommand.CAPTURE_SCREEN -> {
                        val isPortrait = orientation == 2 || orientation == 3
                        mediaCapture.captureSurfaceView(playlistService, false, mVideoLayout)
                        //mediaCapture.captureScreen(playlistService, isPortrait, mVideoLayout)
                        /*  startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),
                              69)*/
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
                        val recordedAudioList = toJsonList(
                            appStorageManager.getAllRecordedAudioPath()
                        )
                        hubManager.sendHubDirectMessage(
                            connectionId, Utils.GET_RECORD,
                            recordedAudioList
                        )
                    }

                    HubCommand.UPLOAD_RECORDED -> {
                        val filePath = responseHub.message!!
                        mediaCapture.updateRecordedAudio(filePath)
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }


    private fun toJsonList(list: List<String>): String {
        val listType = Types.newParameterizedType(List::class.java, String::class.java)
        val adapter = moshi.adapter<List<String>>(listType)
        return adapter.toJson(list)
    }

    private fun cancelAllAlarm() {
        mediaScheduler.cancelAllAlarm()
    }

    companion object {
        const val LOG_TAG = "MainActivity"
    }
}

