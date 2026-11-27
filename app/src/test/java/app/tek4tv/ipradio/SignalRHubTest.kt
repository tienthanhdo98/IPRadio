package app.tek4tv.ipradio

import app.tek4tv.ipradio.core.SignalRHubManager
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.DirectMessage
import app.tek4tv.ipradio.model.HeartbeatRequest
import app.tek4tv.ipradio.model.MetadataModel
import app.tek4tv.ipradio.model.PingHubRequest
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.model.SignalRTokenDto
import app.tek4tv.ipradio.model.Video
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.crypto.xorString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalRHubTest {

    private val moshi = MoshiInst.moshi

    @Test
    fun testDefaultSignalRUrls() {
        assertEquals(
            "https://hubip-thongtinnguon.tek4tv.vn/hubServer",
            SignalRHubManager.DEFAULT_SIGNALR_HUB_URL
        )
        assertEquals(
            "https://apigatewayhttt.tek4tv.vn/api/Auth/Login",
            SignalRHubManager.DEFAULT_SIGNALR_TOKEN_URL
        )
        assertEquals("UPDATE_CA_CERT", HubCommand.UPDATE_CA_CERT)
    }

    @Test
    fun testSignalRTokenDtoDeserialization() {
        val json = """
            {
              "Token": "eyJhbGciOiJIUzI1NiJ9.test.sig",
              "Message": "Success",
              "ExpirationTime": 1795500000
            }
        """.trimIndent()
        val adapter = moshi.adapter(SignalRTokenDto::class.java)
        val dto = adapter.fromJson(json)
        assertNotNull(dto)
        assertEquals("eyJhbGciOiJIUzI1NiJ9.test.sig", dto!!.token)
        assertEquals("Success", dto.message)
        assertEquals(1795500000L, dto.expirationTime)
    }

    @Test
    fun testPingHubRequestAndHeartbeatSerialization() {
        val pingAdapter = moshi.adapter(PingHubRequest::class.java)
        val videoAdapter = moshi.adapter(Video::class.java)
        val hbAdapter = moshi.adapter(HeartbeatRequest::class.java)

        val video = Video(index = "1", time = "0", mediaName = "Ban tin", path = "/data/a.mp3", level = 1)
        val ping = PingHubRequest().apply {
            imei = "AA:BB:CC:DD:EE:FF"
            status = "START"
            connectionId = "conn-123"
            this.video = videoAdapter.toJson(video)
            logType = SignalRHubManager.DEFAULT_SIGNALR_HUB_URL
            appName = "Ipradio"
            metadata = moshi.adapter(List::class.java).toJson(
                listOf(MetadataModel("PLAYLISTINFO", "MQTT_WSS,1732500000"))
            )
        }
        val pingJson = pingAdapter.toJson(ping)
        assertTrue(pingJson.contains("\"IMEI\":\"AA:BB:CC:DD:EE:FF\""))
        assertTrue(pingJson.contains("\"AppName\":\"Ipradio\""))
        assertTrue(pingJson.contains("hubip-thongtinnguon.tek4tv.vn"))

        val hb = HeartbeatRequest().apply {
            key = "AA:BB:CC:DD:EE:FF"
            mess = ""
        }
        val hbJson = hbAdapter.toJson(hb)
        assertTrue(hbJson.contains("\"key\":\"AA:BB:CC:DD:EE:FF\""))
    }

    @Test
    fun testDirectMessageAndResponseHubSerialization() {
        val dmAdapter = moshi.adapter(DirectMessage::class.java)
        val dm = DirectMessage("AA:BB:CC:DD:EE:FF", "NET_OK")
        val dmJson = dmAdapter.toJson(dm)
        assertTrue(dmJson.contains("\"IMEI\":\"AA:BB:CC:DD:EE:FF\""))
        assertTrue(dmJson.contains("\"Message\":\"NET_OK\""))

        val respAdapter = moshi.adapter(ResponseHub::class.java)
        val respJson = """
            {
              "IMEI": "AA:BB:CC:DD:EE:FF",
              "Message": "80",
              "DestinationID": "H01.108",
              "DestinationName": "Xa Demo",
              "DeviceName": "Cum loa 01"
            }
        """.trimIndent()
        val parsed = respAdapter.fromJson(respJson)
        assertNotNull(parsed)
        assertEquals("80", parsed!!.message)
        assertEquals("H01.108", parsed.destinationId)
        assertEquals("Xa Demo", parsed.destinationName)
        assertEquals("Cum loa 01", parsed.deviceName)
    }

    @Test
    fun testXorTokenFieldKeysMatch422b() {
        val pkg = "app.tek4tv.ipradio"
        val u = byteArrayOf(58, 26, 1, 19, 28, 17, 4, 75)
        val p = byteArrayOf(63, 8, 23, 18, 5, 31, 27, 74)
        assertEquals("Username", String(xorString(u, pkg.reversed())))
        assertEquals("Password", String(xorString(p, pkg.reversed())))
    }
}
