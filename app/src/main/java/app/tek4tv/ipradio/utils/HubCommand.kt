package app.tek4tv.ipradio.utils

object HubCommand {
    const val RECORD = "RECORD"
    const val UPLOAD_RECORDED = "UPLOAD_RECORDED"
    const val DELETE_RECORD = "DELETE_RECORD"
    const val GET_RECORD = "GET_RECORD"
    const val LIVE = "LIVE"
    const val UPDATE_STATUS = "UPDATE_STATUS"
    const val SET_VOLUME_DEVICE = "SET_VOLUME_DEVICE"
    const val RESTART = "RESTART"
    const val SET_MUTE_DEVICE = "SET_MUTE_DEVICE"
    const val UPDATE_VERSION = "UPDATE_VERSION"
    const val PONG = "PONG"
    const val SET_TIME_OVER = "SET_TIME_OVER"
    const val SETUP_PI_PS_NAME = "SETUP_PI_PS_NAME"
    const val NETWORK_INFO = "NETWORK_INFO"
    const val PLAYLIST_INFO = "PLAYLIST_INFO"
    const val TURN_ON_PA = "TURN_ON_PA"
    const val CONTROL_MEDIA = "CONTROL_MEDIA"
    const val STORAGE_INFO = "STORAGE_INFO"
    const val DELETE_ALL_MEDIA = "DELETE_ALL_MEDIA"

    const val PLAY_VOD_LIVE_FORCE = "PLAY_VOD_LIVE_FORCE"
    const val PLAY_TUNER_LIVE_FORCE = "PLAY_TUNER_LIVE_FORCE"
    const val READ_DEVICE_INFO = "READ_DEVICE_INFO"


    const val DELETE_ALL_LOG = "DELETE_ALL_LOG"
    const val GET_LOG_BY_DAY = "GET_LOG_BY_DAY"


    const val GET_ENCRYPTED_STORAGE_INFO = "GET_ENCRYPTED_STORAGE_INFO"
    const val SET_ENCRYPTED_VOLUME_DEVICE = "SET_ENCRYPTED_VOLUME_DEVICE"

    const val STOP_FORCE = "STOP_FORCE"

    const val GET_LOG_FILE = "GET_LOG_FILE"


    const val UPDATE_MONTHLY_WSS = "UPDATE_MONTHLY_WSS"
    const val ROTATION = "ROTATION"
    const val DELETE_MONTHLY_LIST_WSS = "DELETE_MONTHLY_LIST_WSS"
    const val UPDATE_PHOTO_HOLDER = "UPDATE_PHOTO_HOLDER"

    //API
    const val UPDATE_CONFIG = "UPDATE_CONFIG"
    const val UPDATE_DESTINATION = "UPDATE_DESTINATION"
    const val UPDATE_MONTHLY_LIST = "UPDATE_MONTHLY_LIST"


    //wss cap nhật lịch
    const val DV010 = "dv010"

    //wss live
    const val DV011 = "dv011"

    //wss hủy lịch phát
    const val DV012 = "dv012"

    //wss gửi log bản tin phát
    const val DV013 = "dv013"

    //wss gửi log lịch phát
    const val DV014 = "dv014"

    //wss nhat ky huy lich phat
    const val DV015 = "dv015"

    //Dịch vụ gửi trạng thái thiết bị
    const val DV016 = "dv016"

    //Dịch vụ điều khiển thiết bị
    const val DV017 = "dv017"

}