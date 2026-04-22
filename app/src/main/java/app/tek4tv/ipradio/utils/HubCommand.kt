package app.tek4tv.ipradio.utils

object HubCommand {

    const val TURN_ON_PA = "TURN_ON_PA"

    const val LIVE = "LIVE"

    const val RESTART = "RESTART"

    const val SET_MUTE_DEVICE = "SET_MUTE_DEVICE"

    const val UPDATE_VERSION = "UPDATE_VERSION"

    const val DELETE_ALL_MEDIA = "DELETE_ALL_MEDIA"

    const val READ_DEVICE_INFO = "READ_DEVICE_INFO"


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
    const val OPH_SETTINGS_V2 = "40"
    const val OPH_MEDIA_SYNC = "41"

    //OPU = Operation from Unit → lệnh từ thiết bị (Unit) lên server -> MQTT topic: channels/{channel_id}/messages/mirav3/s
    const val OPU_GENERIC = "1"
    const val OPU_SETTINGS = "2"
    const val OPU_STATUS = "3"
    const val OPU_LOG = "5"
    const val OPU_SYNC = "6"
    const val OPU_CHECK_BALANCE = "7"
    const val OPU_CHARGE_MONEY = "8"
    const val OPU_ACK = "15"

    //Command
    const val UPDATE_SCHEDULER = "UPDATE_SCHEDULER"
    const val SAVE_SCHEDULER = "SAVE_SCHEDULER"
    const val DELETE_SCHEDULER = "DELETE_SCHEDULER"
    const val STREAM_BACK = "STREAM_BACK"
    const val UPDATE_SETTING_CONFIG = "UPDATE_SETTING"
    const val UPDATE_SERVER_CONFIG = "UPDATE_SERVER_CONFIG"


}