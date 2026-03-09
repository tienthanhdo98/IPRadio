package app.tek4tv.ipradio.utils

object HubCommand {
    const val RECORD = "RECORD"
    const val UPLOAD_RECORDED = "UPLOAD_RECORDED"
    const val DELETE_RECORD = "DELETE_RECORD"
    const val GET_RECORD = "GET_RECORD"
    const val CAPTURE_SCREEN = "CAPTURE_SCREEN"
    const val GET_LIST = "GET_LIST"
    const val UPDATE_LIST = "UPDATE_LIST"
    const val UPDATE_MONTHLY_WSS = "UPDATE_MONTHLY_WSS"
    const val DELETE_MONTHLY_LIST_WSS = "DELETE_MONTHLY_LIST_WSS"
    const val UPDATE_MONTHLY_LIST = "UPDATE_MONTHLY_LIST"
    const val NEXT = "NEXT"
    const val PREVIEW = "PREVIEW"
    const val TURN_ON_PA = "TURN_ON_PA"
    const val TURN_OFF_PA = "TURN_OFF_PA"
    const val CONTROL_MEDIA = "CONTROL_MEDIA"
    const val JUMP = "JUMP"
    const val LIVE = "LIVE"
    const val UPDATE_CONFIG = "UPDATE_CONFIG"
    const val UPDATE_DESTINATION = "UPDATE_DESTINATION"
    const val FM = "FM"
    const val UPDATE_STATUS = "UPDATE_STATUS"
    const val SET_VOLUME_DEVICE = "SET_VOLUME_DEVICE"
    const val STOP = "STOP"
    const val PAUSE = "PAUSE"
    const val START = "START"
    const val RESTART = "RESTART"
    const val RELOAD = "RELOAD"
    const val SET_MUTE_DEVICE = "SET_MUTE_DEVICE"
    const val SWITCH_MODE_FM = "SWITCH_MODE_FM"
    const val GET_LOCATION = "GET_LOCATION"
    const val UPDATE_VERSION = "UPDATE_VERSION"
    const val PONG = "PONG"
    const val SET_TIME_OVER = "SET_TIME_OVER"
    const val SET_TIME_ON = "SET_TIME_ON"
    const val GET_APP_VERSION = "GET_APP_VERSION"
    const val SETUP_PI_PS_NAME = "SETUP_PI_PS_NAME"
    const val NETWORK_INFO = "NETWORK_INFO"
    const val PLAYLIST_INFO = "PLAYLIST_INFO"
    const val STORAGE_INFO = "STORAGE_INFO"
    const val GET_AUDIO_PATH = "GET_AUDIO_PATH"
    const val GET_MEDIA_PATH = "GET_MEDIA_PATH"
    const val DELETE_ALL_AUDIO = "DELETE_ALL_AUDIO"
    const val DELETE_ALL_MEDIA = "DELETE_ALL_MEDIA"

    const val PLAY_VOD_LIVE_FORCE = "PLAY_VOD_LIVE_FORCE"
    const val PLAY_TUNER_LIVE_FORCE = "PLAY_TUNER_LIVE_FORCE"
    const val READ_DEVICE_INFO = "READ_DEVICE_INFO"


    const val DELETE_ALL_LOG = "DELETE_ALL_LOG"
    const val GET_LOG_BY_DAY = "GET_LOG_BY_DAY"


    const val CONTROL_WIFI_STATE = "CONTROL_WIFI_STATE"

    const val GET_ENCRYPTED_STORAGE_INFO = "GET_ENCRYPTED_STORAGE_INFO"
    const val SET_ENCRYPTED_VOLUME_DEVICE = "SET_ENCRYPTED_VOLUME_DEVICE"

    const val STOP_FORCE = "STOP_FORCE"

    const val JOIN_GROUP = "JoinGroup"

    const val GET_LOG_FILE = "GET_LOG_FILE"


    //
    //OPH = Operation from Host → lệnh từ server (Host) xuống thiết bị -> MQTT topic: channels/{channel_id}/messages/mirav3/d
    const val OPH_SYNC = "1"
    const val OPH_SETTINGS = "2"
    const val OPH_SERVER = "3"
    const val OPH_UDF = "4"
    const val OPH_REBOOT = "5"
    const val OPH_TAILSCALE = "7"
    const val OPH_UDF_GSM = "8"
    const val OPH_MEDIA_V3 = "16"
    const val OPH_MEDIA_DELETE_ALL = "17"
    const val OPH_STREAM_BACK = "18"
    const val OPH_SERVER_HTTTN = "21"
    const val OPH_CHECK_BALANCE = "32"
    const val OPH_CHARGE_MONEY = "33"
    const val OPH_SETTINGS_V2 = "OPH_SETTINGS_V2"
    const val OPH_MEDIA_SYNC = "OPH_MEDIA_SYNC"

    //OPU = Operation from Unit → lệnh từ thiết bị (Unit) lên server -> MQTT topic: channels/{channel_id}/messages/mirav3/s
    const val OPU_GENERIC = "OPU_GENERIC"
    const val OPU_SETTINGS = "OPU_SETTINGS"
    const val OPU_STATUS = "OPU_STATUS"
    const val OPU_LOG = "OPU_LOG"
    const val OPU_SYNC = "OPU_SYNC"
    const val OPU_CHECK_BALANCE = "OPU_CHECK_BALANCE"
    const val OPU_CHARGE_MONEY = "OPU_CHARGE_MONEY"
    const val OPU_ACK = "OPU_ACK"

    //MGW_CT
    const val OPH_CT_READ = "OPH_CT_READ"
    const val OPH_CT_CONFIG = "OPH_CT_CONFIG"
    const val OPH_CT_CONTROL = "OPH_CT_CONTROL"
    const val OPH_CT_GET_LOG = "OPH_CT_GET_LOG"
    const val OPU_CT_STATUS = "OPU_CT_STATUS"
    const val OPU_CT_CONFIG = "OPU_CT_CONFIG"
    const val OPU_CT_LOG = "OPU_CT_LOG"
    const val OPU_CT_ACK = "OPU_CT_ACK"

    //
    const val UPDATE_WEEKLY_SCHEDULER = "UPDATE_WEEKLY_SCHEDULER"


}