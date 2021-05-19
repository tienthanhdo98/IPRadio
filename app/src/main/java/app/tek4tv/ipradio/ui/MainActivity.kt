package app.tek4tv.ipradio.ui

import android.Manifest
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
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.core.*
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.viewmodels.MainViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.abs


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

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
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS
    )

    private lateinit var mVideoLayout: VLCVideoLayout

    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var moshi: Moshi

    private lateinit var hubManager: HubManager
    private lateinit var playerManager: PlayerManager
    //private lateinit var audioManager: AudioManager

    private lateinit var serialPortController: SerialPortController

    private lateinit var appStorageManager: AppStorageManager

    private lateinit var preference: SharedPreferences

    private lateinit var logUtils: LogUtils

    private var volume = "80"

    var locationTracker = LocationTracker()

    private lateinit var mediaCapture: MediaCapture

    private lateinit var mediaScheduler: MediaScheduler

    private lateinit var rootLayout: FrameLayout

    private val playerCallback = object : PlayerCallback {
        override fun onPlayNewMedia(index: Int, mediaItem: MediaItem) {
            hubManager.playlistIndex = index
            hubManager.currentMediaItem = mediaItem

            val displayName = mediaItem.name ?: ""//mediaItem.getDisplayName()
            hubManager.curMediaName = displayName

            if (GlobalProperties.logEnabled)
                logUtils.logPlayMedia(displayName)

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
            if (GlobalProperties.logEnabled)
                logUtils.logStartTimeFrame(frame)
        }

        override fun onEndTimeFrame(frame: String) {
            if (GlobalProperties.logEnabled)
                logUtils.logEndTimeFrame(frame)
        }

        override fun onPlayTuner(mediaItem: MediaItem) {
            try {
                val tunerType = if (mediaItem.getMediaType() == MediaType.AM) "0"
                else "1"

                val pipsData = "${GlobalProperties.piCode},${GlobalProperties.psName}"
                val tunerData = "$tunerType,${mediaItem.path},${GlobalProperties.volume}"

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
            mediaScheduler.scheduleAllAlarms()
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

        preference = getPreferences(MODE_PRIVATE)

        if (needGrantPermission()) requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)

        val hubComm = object  : HubComm
        {
            override fun forceRestartApp() {
                CrashHandler.restartApp(MyApp.instance!!, this@MainActivity)
            }

            override fun forceRestartDevice() {
                this@MainActivity.forceRestartDevice()
            }
        }

        hubManager = HubManager(hubComm) { command, message ->
            handleFromCommandServer(command, message)
        }

        serialPortController = SerialPortController(applicationContext, lifecycleScope, hubManager)
        serialPortController.connectToSerialPort()

        rootLayout = findViewById(R.id.root_layout)
        playerManager = PlayerManager(applicationContext, viewModel, rootLayout)
        playerManager.playerCallback = playerCallback

        hubManager.hubEventObserver.add(playerManager)

        NetworkUtils.instance.startNetworkListener(this)

        initHubConnect()
        registerObservers()

        appStorageManager = AppStorageManager(applicationContext)

        mediaCapture = MediaCapture(applicationContext, lifecycleScope)

        mediaScheduler = MediaScheduler(applicationContext, viewModel.mediaRepo)

        logUtils = LogUtils(applicationContext)
    }

    private fun checkTime()
    {
        SNTPClient.getDate(Calendar.getInstance().timeZone, object : SNTPClient.Listener
        {
            override fun onTimeReceived(rawDate: String?) {
                if(rawDate == null) return

                try {
                    val receiveFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                    val receivedDate = receiveFormat.parse(rawDate)

                    if(abs(receivedDate.time - Date().time) > 30000)
                    {

                        Log.d("Time","Sync")

                        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                        setSystemTime(dateFormat.format(receivedDate))

                        cancelAllAlarm()

                        CrashHandler.restartApp(MyApp.instance!!, this@MainActivity)
                    }
                }
                catch (e : Exception)
                {
                    e.printStackTrace()
                }
            }

            override fun onError(ex: java.lang.Exception?) {
                ex?.printStackTrace()
            }
        })
    }

    override fun onStop() {
        super.onStop()
        playerManager.onActivityStop()
        logUtils.onActivityStop()
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

            if (ForceMedia.hasValidForceMedia(applicationContext)) {
                mediaScheduler.scheduleForceMediaAlarm()
                val forceMedia = ForceMedia.getLocalForceMedia(applicationContext)
                if (forceMedia != null)
                    playerManager.playForceMedia(forceMedia)

                return@observe
            }

            //default
            mediaScheduler.scheduleAllAlarms()

            playerManager.startFirstPlaylist()

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
                checkTime()

                hubManager.connectToHub()

                locationTracker.getLocation(this@MainActivity)
            } else {

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
            if (isImei) {
                when (command) {
                    HubCommand.GET_LIST -> {
                    }
                    HubCommand.UPDATE_LIST -> {
                        val mess = responseHub.message!!

                        playerManager.stopAllPlayer()

                        GlobalProperties.saveProperties(responseHub)

                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_FORCE_SET_VOLUME,
                            responseHub.volume
                        )

                        cancelAllAlarm()
                        ForceMedia.deleteForeMediaPref(applicationContext)

                        viewModel.getPlayerContent(mess, true)
                    }
                    HubCommand.NEXT -> {
                    }
                    HubCommand.PREVIEW -> {
                    }
                    HubCommand.JUMP -> {
                    }
                    HubCommand.LIVE -> {
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_FORCE_SET_VOLUME,
                            GlobalProperties.volume
                        )

                        playForceMedia(responseHub)
                    }
                    HubCommand.UPDATE_STATUS -> {
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
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_FORCE_RESTART_DEVICE,
                            "", true
                        )
                    }
                    HubCommand.RELOAD -> {

                    }
                    HubCommand.SWITCH_MODE_FM -> {
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_FORCE_SET_VOLUME,
                            GlobalProperties.volume
                        )

                        playForceMedia(responseHub)
                    }
                    HubCommand.SET_MUTE_DEVICE -> {
                        if (responseHub.message != null) {
                            serialPortController.invokeDeviceFunc(
                                Define.FUNC_WRITE_FORCE_SET_MUTE,
                                responseHub.message!!
                            )
                        }
                    }
                    HubCommand.SET_VOLUME_DEVICE -> {
                        if (responseHub.message != null) {
                            //GlobalProperties.saveVolume(responseHub.message!!)

                            serialPortController.invokeDeviceFunc(
                                Define.FUNC_WRITE_FORCE_SET_VOLUME,
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
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_PLAY_NO_SOURCE,
                            "",
                            true
                        )
                    }

                    HubCommand.SET_TIME_ON -> {
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_PLAY_VOD_LIVE,
                            volume,
                            true
                        )
                    }

                    HubCommand.SETUP_PI_PS_NAME -> {
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_SETUP_PI_PS_NAME,
                            responseHub.message ?: ""
                        )
                    }

                    HubCommand.PLAY_VOD_LIVE_FORCE -> {
                        val hubMessage = responseHub.message ?: ""
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_PLAY_VOD_LIVE_FORCE,
                            hubMessage
                        )
                    }

                    HubCommand.PLAY_TUNER_LIVE_FORCE -> {
                        val hubMessage = responseHub.message ?: ""
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_PLAY_TUNER_FORCE,
                            hubMessage
                        )
                    }

                    HubCommand.READ_DEVICE_INFO -> {
                        serialPortController.invokeDeviceFunc(
                            Define.FUNC_WRITE_READ_DEVICE_INFO,
                            "",
                            true
                        )
                    }

                    HubCommand.NETWORK_INFO -> {
                        val connectionId = responseHub.message
                        if (connectionId != null) {
                            val result = NetworkUtils.getNetworkInfo(applicationContext)

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

                    HubCommand.DELETE_ALL_LOG -> {
                        appStorageManager.deleteAllLog()
                    }

                    HubCommand.GET_LOG_BY_DAY -> {
                        val logName = responseHub.message

                        if (logName != null) {
                            val logPath =
                                "${applicationContext.filesDir.path}/$MEDIA_LOG_FOLDER_NAME/$logName.txt"
                            FtpUtils.uploadLogFile(applicationContext, logPath)
                        }
                    }

                    HubCommand.CONTROL_WIFI_STATE -> {
                        val wifiEnable = responseHub.message!! == "1"
                        NetworkUtils.setWifiEnable(applicationContext, wifiEnable)
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

    fun cancelAllAlarm() {
        if (ForceMedia.hasValidForceMedia(applicationContext))
            mediaScheduler.cancelForceMediaAlarm()
        else {
            mediaScheduler.cancelAllAlarm()
        }
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

                    if (currentForceMedia != null && currentForceMedia.level < level) {
                        Log.d(LOG_TAG, "aaaaaaaaaaaaaaaaaaa")
                        return
                    }
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

    private fun disableSSL() {
        val trustAllCerts: Array<TrustManager> = arrayOf(
            object : X509TrustManager {
                override fun checkClientTrusted(
                    certs: Array<X509Certificate?>?, authType: String?
                ) {
                }

                override fun checkServerTrusted(
                    certs: Array<X509Certificate?>?, authType: String?
                ) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate?>? {
                    return null
                }
            }
        )

        // Install the all-trusting trust manager
        try {
            val sc: SSLContext = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        } catch (e: GeneralSecurityException) {
        }
    }

    fun forceRestartDevice() {
        serialPortController.invokeDeviceFunc(
            Define.FUNC_WRITE_FORCE_RESTART_DEVICE,
            "", true
        )
    }

    companion object {
        const val LOG_TAG = "MainActivity"
    }
}

