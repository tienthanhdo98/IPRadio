package app.tek4tv.ipradio.utils.network

import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import android.net.wifi.WifiConfiguration
import android.util.Log
import app.tek4tv.ipradio.MyApp


fun connectWifi(ssid: String, key: String) {
    val wifiConfig = WifiConfiguration()
    wifiConfig.SSID = String.format("\"%s\"", ssid)
    wifiConfig.preSharedKey = String.format("\"%s\"", key)

    val wifiManager =
        MyApp.instance!!.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager?

    val netId = wifiManager!!.addNetwork(wifiConfig)
    wifiManager.disconnect()
    wifiManager.enableNetwork(netId, true)
    val a = wifiManager.reconnect()
    Log.d("aaa",a.toString())
}
