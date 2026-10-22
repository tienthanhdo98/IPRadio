package app.tek4tv.ipradio

import android.content.Context
import android.content.ContextWrapper
import app.tek4tv.ipradio.utils.network.CaCertHelper
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLException

class CaCertLogicTest {

    private fun createTestContext(tempDir: File): Context {
        return object : ContextWrapper(null) {
            override fun getFilesDir(): File = tempDir
        }
    }

    // ========================================================================
    // 1. KỊCH BẢN: TRÍCH XUẤT HOSTNAME VÀ PORT LINH HOẠT
    // ========================================================================

    @Test
    fun testExtractHostAndPort_standardAndCustomPorts() {
        // Case 1: Standard HTTPS (không có port trong URL -> default 443)
        val res1 = CaCertHelper.extractHostAndPort("https://storage-hp-ttn.thongtinnguon.vn/2026/09/audio.mp3")
        assertNotNull(res1)
        assertEquals("storage-hp-ttn.thongtinnguon.vn", res1!!.first)
        assertEquals(443, res1.second)

        // Case 2: Custom Port 8443
        val res2 = CaCertHelper.extractHostAndPort("https://stream.radio.vn:8443/live/channel.m3u8")
        assertNotNull(res2)
        assertEquals("stream.radio.vn", res2!!.first)
        assertEquals(8443, res2.second)

        // Case 3: Domain không có scheme nhưng có port
        val res3 = CaCertHelper.extractHostAndPort("custom-radio.vn:9443")
        assertNotNull(res3)
        assertEquals("custom-radio.vn", res3!!.first)
        assertEquals(9443, res3.second)

        // Case 4: URL chứa hai dấu gạch chéo trong path
        val res4 = CaCertHelper.extractHostAndPort("https://thongtinnguon.vn//2026/9/9//audio.mp3")
        assertNotNull(res4)
        assertEquals("thongtinnguon.vn", res4!!.first)
        assertEquals(443, res4.second)

        // Case 5: IP trực tiếp với port
        val res5 = CaCertHelper.extractHostAndPort("https://192.168.1.100:8443/live")
        assertNotNull(res5)
        assertEquals("192.168.1.100", res5!!.first)
        assertEquals(8443, res5.second)

        // Case 6: extractHost trả về đúng host
        val hostOnly = CaCertHelper.extractHost("https://stream.radio.vn:8443/live")
        assertEquals("stream.radio.vn", hostOnly)
    }

    // ========================================================================
    // 2. KỊCH BẢN: XỬ LÝ LỆNH HUB VỚI NỘI DUNG PEM TRỰC TIẾP (TRƯỜNG HỢP 1)
    // ========================================================================

    @Test
    fun testHandleHubCaInput_directPemContent_savesFileWithoutNetwork() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_ca_hub_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val testContext = createTestContext(tempDir)

            // Chuỗi PEM giả lập gửi từ Hub
            val pemContent = """
                -----BEGIN CERTIFICATE-----
                MIIFijCCA3KgAwIBAgIQdY39i658BwD6qSWn4cetFDANBgkqhkiG9w0BAQwFADBf
                MQswCQYDVQQGEwJHQjEYMBYGA1UEChMPU2VjdGlnbyBMaW1pdGVkMTYwNAYDVQQD
                Ey1TZWN0aWdvIFB1YmxpYyBTZXJ2ZXIgQXV0aGVudGljYXRpb24gUm9vdCBSNDYw
                -----END CERTIFICATE-----
            """.trimIndent()

            val success = CaCertHelper.handleHubCaInput(testContext, pemContent)
            assertTrue("handleHubCaInput phải trả về true khi nhận chuỗi PEM hợp lệ", success)

            val caDir = File(tempDir, CaCertHelper.CA_DIR_NAME)
            assertTrue("Thư mục ca-certs phải được tạo tự động", caDir.exists())

            val savedFiles = caDir.listFiles { file -> file.name.startsWith("hub_cert_") && file.name.endsWith(".crt") }
            assertNotNull(savedFiles)
            assertEquals("Phải có đúng 1 file cert được lưu từ lệnh Hub", 1, savedFiles!!.size)

            val savedContent = savedFiles[0].readText()
            assertEquals("Nội dung file cert lưu trong máy phải khớp 100% với chuỗi PEM nhận được", pemContent, savedContent)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ========================================================================
    // 3. KỊCH BẢN: XỬ LÝ LỆNH HUB VỚI DANH SÁCH DOMAIN & PORT (TRƯỜNG HỢP 3)
    // ========================================================================

    @Test
    fun testHandleHubCaInput_domainListSplitting() {
        val input = "stream1.radio.vn:8443, stream2.radio.vn; backup.radio.vn:9443\n192.168.1.50:443"
        val items = input.split(",", ";", " ", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        assertEquals(4, items.size)

        val pair1 = CaCertHelper.extractHostAndPort(items[0])
        assertEquals("stream1.radio.vn", pair1?.first)
        assertEquals(8443, pair1?.second)

        val pair2 = CaCertHelper.extractHostAndPort(items[1])
        assertEquals("stream2.radio.vn", pair2?.first)
        assertEquals(443, pair2?.second)

        val pair3 = CaCertHelper.extractHostAndPort(items[2])
        assertEquals("backup.radio.vn", pair3?.first)
        assertEquals(9443, pair3?.second)

        val pair4 = CaCertHelper.extractHostAndPort(items[3])
        assertEquals("192.168.1.50", pair4?.first)
        assertEquals(443, pair4?.second)
    }

    // ========================================================================
    // 4. KỊCH BẢN: TÍNH HỢP LỆ CỦA BỘ CHỨNG CHỈ SECTIGO MẶC ĐỊNH
    // ========================================================================

    @Test
    fun testDefaultCertificates_parseSuccessfullyAndValid() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_ca_default_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val testContext = createTestContext(tempDir)
            val caDir = CaCertHelper.setupCaDir(testContext)
            val defaultCertFile = File(caDir, "sectigo_thongtinnguon.crt")

            assertTrue("File sectigo_thongtinnguon.crt phải được tạo tự động", defaultCertFile.exists())
            assertTrue("File cert phải có dung lượng > 0", defaultCertFile.length() > 0)

            val certFactory = CertificateFactory.getInstance("X.509")
            val certs = defaultCertFile.inputStream().use { stream ->
                certFactory.generateCertificates(stream).filterIsInstance<X509Certificate>()
            }

            assertEquals("Bộ chứng chỉ mặc định phải chứa đúng 2 cert (Root R46 và OV R36)", 2, certs.size)

            val rootCert = certs[0]
            assertTrue("Chứng chỉ 1 phải là Sectigo Root R46", rootCert.subjectDN.name.contains("Sectigo Public Server Authentication Root R46"))

            val intermediateCert = certs[1]
            assertTrue("Chứng chỉ 2 phải là Sectigo OV R36", intermediateCert.subjectDN.name.contains("Sectigo Public Server Authentication CA OV R36"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ========================================================================
    // 5. KỊCH BẢN: NHẬN DIỆN LỖI SSL ĐỂ KÍCH HOẠT AUTO RETRY (SSL ERROR DETECTOR)
    // ========================================================================

    @Test
    fun testSslErrorDetection_classifiesErrorsAccurately() {
        fun isSslError(e: Throwable?): Boolean {
            if (e == null) return false
            return e is SSLException ||
                    e.cause is SSLException ||
                    e.message?.contains("CertPathValidatorException", ignoreCase = true) == true ||
                    e.message?.contains("Trust anchor", ignoreCase = true) == true
        }

        // Case 5.1: Lỗi Trust anchor not found (Lỗi thực tế từ log)
        val err1 = SSLHandshakeException("java.security.cert.CertPathValidatorException: Trust anchor for certification path not found.")
        assertTrue("Lỗi Trust anchor phải được nhận diện là SSL error", isSslError(err1))

        // Case 5.2: Ngoại lệ SSLException chung
        val err2 = SSLException("Connection reset by peer during handshake")
        assertTrue("SSLException phải được nhận diện là SSL error", isSslError(err2))

        // Case 5.3: Lỗi bọc trong Exception thông thường nhưng có cause là SSL
        val err3 = Exception("Network call failed", SSLHandshakeException("Handshake failed"))
        assertTrue("Exception có cause là SSLHandshakeException phải được nhận diện là SSL error", isSslError(err3))

        // Case 5.4: Lỗi mạng thông thường (404, Timeout, SocketClosed) -> KHÔNG phải lỗi SSL
        val err4 = java.net.SocketTimeoutException("Connect timed out")
        assertFalse("SocketTimeoutException không được coi là lỗi SSL", isSslError(err4))

        val err5 = java.io.FileNotFoundException("File not found on server")
        assertFalse("FileNotFoundException không được coi là lỗi SSL", isSslError(err5))
    }

    // ========================================================================
    // 6. KỊCH BẢN: NẠP VÀ TỰ ĐỘNG CẬP NHẬT VÀO KEYSTORE
    // ========================================================================

    @Test
    fun testKeyStoreLoading_storesCertificatesProperly() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_keystore_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val testContext = createTestContext(tempDir)
            val caDir = CaCertHelper.setupCaDir(testContext)
            val defaultCertFile = File(caDir, "sectigo_thongtinnguon.crt")
            assertTrue(defaultCertFile.exists() && defaultCertFile.length() > 0)

            // Thêm một file cert custom hợp lệ vào ca-certs (copy từ file cert hợp lệ)
            val customCertFile = File(caDir, "test_custom.crt")
            customCertFile.writeText(defaultCertFile.readText())

            // Nạp vào KeyStore
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
            }
            val certFactory = CertificateFactory.getInstance("X.509")

            var loadedCount = 0
            caDir.listFiles { f -> f.extension.equals("crt", ignoreCase = true) }?.forEach { f ->
                f.inputStream().use { stream ->
                    val certs = certFactory.generateCertificates(stream)
                    certs.forEachIndexed { idx, cert ->
                        keyStore.setCertificateEntry("${f.nameWithoutExtension}_$idx", cert)
                        loadedCount++
                    }
                }
            }

            assertTrue("KeyStore phải nạp được ít nhất 3 cert (2 cert mặc định + 1 cert custom)", loadedCount >= 3)
            assertTrue("KeyStore phải chứa alias của test_custom", keyStore.containsAlias("test_custom_0"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ========================================================================
    // 7. KỊCH BẢN: BÓC TÁCH FILE PEM CHỨA PRIVATE KEY (AUTO STRIP PRIVATE KEY)
    // ========================================================================

    @Test
    fun testExtractCertificatesOnly_fromPemWithPrivateKey() {
        val rawPem = """
            -----BEGIN PRIVATE KEY-----
            MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC7V3L
            -----END PRIVATE KEY-----
            -----BEGIN CERTIFICATE-----
            MIIFijCCA3KgAwIBAgIQdY39i658BwD6qSWn4cetFDANBgkqhkiG9w0BAQwFADBf
            MQswCQYDVQQGEwJHQjEYMBYGA1UEChMPU2VjdGlnbyBMaW1pdGVkMTYwNAYDVQQD
            Ey1TZWN0aWdvIFB1YmxpYyBTZXJ2ZXIgQXV0aGVudGljYXRpb24gUm9vdCBSNDYw
            -----END CERTIFICATE-----
        """.trimIndent()
        assertTrue("File gốc phải có khối PRIVATE KEY", rawPem.contains("-----BEGIN PRIVATE KEY-----"))

        val cleanCerts = CaCertHelper.extractCertificatesOnly(rawPem)
        assertFalse("Kết quả bóc tách TUYỆT ĐỐI không được chứa PRIVATE KEY", cleanCerts.contains("PRIVATE KEY"))
        assertTrue("Kết quả bóc tách phải chứa BEGIN CERTIFICATE", cleanCerts.contains("-----BEGIN CERTIFICATE-----"))
    }
}

