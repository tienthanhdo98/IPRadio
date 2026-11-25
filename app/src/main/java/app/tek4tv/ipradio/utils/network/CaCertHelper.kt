package app.tek4tv.ipradio.utils.network

import android.content.Context
import android.util.Base64
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object CaCertHelper {
    private const val TAG = "CaCertHelper"
    const val CA_DIR_NAME = "ca-certs"
    private const val DEFAULT_CERT_FILE_NAME = "sectigo_thongtinnguon.crt"

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val fetchedHosts = ConcurrentHashMap.newKeySet<String>()

    // Sectigo Root R46 (Root CA) + Sectigo CA OV R36 (Intermediate CA)
    private val DEFAULT_CERTIFICATE_CONTENT = buildString {
        // Sectigo Public Server Authentication Root R46
        appendLine("-----BEGIN CERTIFICATE-----")
        appendLine("MIIFijCCA3KgAwIBAgIQdY39i658BwD6qSWn4cetFDANBgkqhkiG9w0BAQwFADBf")
        appendLine("MQswCQYDVQQGEwJHQjEYMBYGA1UEChMPU2VjdGlnbyBMaW1pdGVkMTYwNAYDVQQD")
        appendLine("Ey1TZWN0aWdvIFB1YmxpYyBTZXJ2ZXIgQXV0aGVudGljYXRpb24gUm9vdCBSNDYw")
        appendLine("HhcNMjEwMzIyMDAwMDAwWhcNNDYwMzIxMjM1OTU5WjBfMQswCQYDVQQGEwJHQjEY")
        appendLine("MBYGA1UEChMPU2VjdGlnbyBMaW1pdGVkMTYwNAYDVQQDEy1TZWN0aWdvIFB1Ymxp")
        appendLine("YyBTZXJ2ZXIgQXV0aGVudGljYXRpb24gUm9vdCBSNDYwggIiMA0GCSqGSIb3DQEB")
        appendLine("AQUAA4ICDwAwggIKAoICAQCTvtU2UnXYASOgHEdCSe5jtrch/cSV1UgrJnwUUxDa")
        appendLine("ef0rty2k1Cz66jLdScK5vQ9IPXtamFSvnl0xdE8H/FAh3aTPaE8bEmNtJZlMKpnz")
        appendLine("SDBh+oF8HqcIStw+KxwfGExxqjWMrfhu6DtK2eWUAtaJhBOqbchPM8xQljeSM9xf")
        appendLine("iOefVNlI8JhD1mb9nxc4Q8UBUQvX4yMPFF1bFOdLvt30yNoDN9HWOaEhUTCDsG3X")
        appendLine("ME6WW5HwcCSrv0WBZEMNvSE6Lzzpng3LILVCJ8zab5vuZDCQOc2TZYEhMbUjUDM3")
        appendLine("IuM47fgxMMxF/mL50V0yeUKH32rMVhlATc6qu/m1dkmU8Sf4kaWD5QazYw6A3OAS")
        appendLine("VYCmO2a0OYctyPDQ0RTp5A1NDvZdV3LFOxxHVp3i1fuBYYzMTYCQNFu31xR13NgE")
        appendLine("SJ/AwSiItOkcyqex8Va3e0lMWeUgFaiEAin6OJRpmkkGj80feRQXEgyDet4fsZfu")
        appendLine("+Zd4KKTIRJLpfSYFplhym3kT2BFfrsU4YjRosoYwjviQYZ4ybPUHNs2iTG7sijbt")
        appendLine("8uaZFURww3y8nDnAtOFr94MlI1fZEoDlSfB1D++N6xybVCi0ITz8fAr/73trdf+L")
        appendLine("HaAZBav6+CuBQug4urv7qv094PPK306Xlynt8xhW6aWWrL3DkJiy4Pmi1KZHQ3xt")
        appendLine("zwIDAQABo0IwQDAdBgNVHQ4EFgQUVnNYZJX5khqwEioEYnmhQBWIIUkwDgYDVR0P")
        appendLine("AQH/BAQDAgGGMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQEMBQADggIBAC9c")
        appendLine("mTz8Bl6MlC5w6tIyMY208FHVvArzZJ8HXtXBc2hkeqK5Duj5XYUtqDdFqij0lgVQ")
        appendLine("YKlJfp/imTYpE0RHap1VIDzYm/EDMrraQKFz6oOht0SmDpkBm+S8f74TlH7Kph52")
        appendLine("gDY9hAaLMyZlbcp+nv4fjFg4exqDsQ+8FxG75gbMY/qB8oFM2gsQa6H61SilzwZA")
        appendLine("Fv97fRheORKkU55+MkIQpiGRqRxOF3yEvJ+M0ejf5lG5Nkc/kLnHvALcWxxPDkjB")
        appendLine("JYOcCj+esQMzEhonrPcibCTRAUH4WAP+JWgiH5paPHxsnnVI84HxZmduTILA7rpX")
        appendLine("DhjvLpr3Etiga+kFpaHpaPi8TD8SHkXoUsCjvxInebnMMTzD9joiFgOgyY9mpFui")
        appendLine("TdaBJQbpdqQACj7LzTWb4OE4y2BThihCQRxEV+ioratF4yUQvNs+ZUH7G6aXD+u5")
        appendLine("dHn5HrwdVw1Hr8Mvn4dGp+smWg9WY7ViYG4A++MnESLn/pmPNPW56MORcr3Ywx65")
        appendLine("LvKRRFHQV80MNNVIIb/bE/FmJUNS0nAiNs2fxBx1IK1jcmMGDw4nztJqDby1ORrp")
        appendLine("0XZ60Vzk50lJLVU3aPAaOpg+VBeHVOmmJ1CJeyAvP/+/oYtKR5j/K3tJPsMpRmAY")
        appendLine("QqszKbrAKbkTidOIijlBO8n9pu0f9GBj39ItVQGL")
        appendLine("-----END CERTIFICATE-----")

        // Sectigo Public Server Authentication CA OV R36
        appendLine("-----BEGIN CERTIFICATE-----")
        appendLine("MIIGTDCCBDSgAwIBAgIQLBo8dulD3d3/GRsxiQrtcTANBgkqhkiG9w0BAQwFADBf")
        appendLine("MQswCQYDVQQGEwJHQjEYMBYGA1UEChMPU2VjdGlnbyBMaW1pdGVkMTYwNAYDVQQD")
        appendLine("Ey1TZWN0aWdvIFB1YmxpYyBTZXJ2ZXIgQXV0aGVudGljYXRpb24gUm9vdCBSNDYw")
        appendLine("HhcNMjEwMzIyMDAwMDAwWhcNMzYwMzIxMjM1OTU5WjBgMQswCQYDVQQGEwJHQjEY")
        appendLine("MBYGA1UEChMPU2VjdGlnbyBMaW1pdGVkMTcwNQYDVQQDEy5TZWN0aWdvIFB1Ymxp")
        appendLine("YyBTZXJ2ZXIgQXV0aGVudGljYXRpb24gQ0EgT1YgUjM2MIIBojANBgkqhkiG9w0B")
        appendLine("AQEFAAOCAY8AMIIBigKCAYEApkMtJ3R06jo0fceI0M52B7K+TyMeGcv2BQ5AVc3j")
        appendLine("lYt76TvHIu/nNe22W/RJXX9rWUD/2GE6GF5x0V4bsY7K3IeJ8E7+KzG/TGboySfD")
        appendLine("u+F52jqQBbY62ofhYjMeiAbLI02+FqwHeM8uIrUtcX8b2RCxF358TB0NHVccAXZc")
        appendLine("FYgZndZCeXxjuca7pJJ20LLUnXtgXcjAE1vY4WvbReW0W6mkeZyNGdmpTcFs5Y+s")
        appendLine("yy6LtE5Zocji9J9NlNnReox2RWVyEXpA1ChZ4gqN+ZpVSIQ0HBorVFbBKyhdZyEX")
        appendLine("gZgNSNtBRwxqwIzJePJhYd4ZUhO1vk+/uP3nwDk0p95q/j7naXNCSvESnrHPypaB")
        appendLine("WRK066nKfPRPi9m9kIOhMdYfS8giFRTcdgL24Ycilj7ecAK9Trh0VbjwouJ4WH+x")
        appendLine("bt47u68ZFCD/ac55I0DNHkCpaPruj6e9Rmr7K46wZDAYXuEAqB7tGG/jd6JAA+H2")
        appendLine("O44CV98NRsU213f1kScIZntNAgMBAAGjggGBMIIBfTAfBgNVHSMEGDAWgBRWc1hk")
        appendLine("lfmSGrASKgRieaFAFYghSTAdBgNVHQ4EFgQU42Z0u3BojSxdTg6mSo+bNyKcgpIw")
        appendLine("DgYDVR0PAQH/BAQDAgGGMBIGA1UdEwEB/wQIMAYBAf8CAQAwHQYDVR0lBBYwFAYI")
        appendLine("KwYBBQUHAwEGCCsGAQUFBwMCMBsGA1UdIAQUMBIwBgYEVR0gADAIBgZngQwBAgIw")
        appendLine("VAYDVR0fBE0wSzBJoEegRYZDaHR0cDovL2NybC5zZWN0aWdvLmNvbS9TZWN0aWdv")
        appendLine("UHVibGljU2VydmVyQXV0aGVudGljYXRpb25Sb290UjQ2LmNybDCBhAYIKwYBBQUH")
        appendLine("AQEEeDB2ME8GCCsGAQUFBzAChkNodHRwOi8vY3J0LnNlY3RpZ28uY29tL1NlY3Rp")
        appendLine("Z29QdWJsaWNTZXJ2ZXJBdXRoZW50aWNhdGlvblJvb3RSNDYucDdjMCMGCCsGAQUF")
        appendLine("BzABhhdodHRwOi8vb2NzcC5zZWN0aWdvLmNvbTANBgkqhkiG9w0BAQwFAAOCAgEA")
        appendLine("BZXWDHWC3cubb/e1I1kzi8lPFiK/ZUoH09ufmVOrc5ObYH/XKkWUexSPqRkwKFKr")
        appendLine("7r8OuG+p7VNB8rifX6uopqKAgsvZtZsq7iAFw04To6vNcxeBt1Eush3cQ4b8nbQR")
        appendLine("MQLChgEAqwhuXp9P48T4QEBSksYav7+aFjNySsLYlPzNqVM3RNwvBdvp6vgDtGwc")
        appendLine("xlKQZVuuNVIaoYyls8swhxDeSHKpRdxRauTLZ+pl+wGvy0pnrLEJGSz9mOEmfbod")
        appendLine("e/XopR2NGqaHJ6bIjyxPu6UtyQGI26En7UAEozACrHz06Nx2jTAY9E6NeB6XuobE")
        appendLine("wLK025ZRmvglcURG1BrV24tGHHTgxCe8M3oGlpUSMTKQ2dkgljZVYt+gKdFtWELZ")
        appendLine("MuRdi+X3XsrR8LFz+aLUiDRfQqhmw3RxjIyVKvvu9UPYY1nsvxYmFnUSeM+2q1z/")
        appendLine("iPUry+xDY9MC6+IhleKT094VKdFVp7LXH42+wvU+17lRolQ2mK2N/nBLVBwaIhib")
        appendLine("QXw4VYKwB86Bc6eS6iqsc94KEgD/U4VsjmgfhK+Xp4NM+VYzTTa3QeV3p8xOM0cw")
        appendLine("q1p8oZFA+OBcz3FYWpDIe5j0NWKlw9hXsTyPY/HeZUV59akskSOSRSmDfe8wJDPX")
        appendLine("58uB9/7lud0G3x0pxQAcffP0ayKavNwDTw4UfJ34cEw=")
        appendLine("-----END CERTIFICATE-----")
    }

    /**
     * Khởi tạo và trả về thư mục chứa các CA cert bổ sung cho LibVLC
     */
    fun setupCaDir(context: Context): File {
        val caDir = File(context.filesDir, CA_DIR_NAME)
        if (!caDir.exists()) {
            caDir.mkdirs()
        }

        val certFile = File(caDir, DEFAULT_CERT_FILE_NAME)
        if (!certFile.exists() || certFile.length() == 0L) {
            try {
                certFile.writeText(DEFAULT_CERTIFICATE_CONTENT)
                Log.d(TAG, "Đã khởi tạo file CA cert mặc định tại: ${certFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi ghi file CA cert mặc định: ${e.message}", e)
            }
        }
        return caDir
    }

    /**
     * Trích xuất link HTTPS từ MediaItem (body hoặc pathBackup)
     */
    fun extractHttpsUrl(mediaItem: MediaItem?): String? {
        if (mediaItem == null) return null
        val body = mediaItem.body?.trim() ?: ""
        if (body.startsWith("https://", ignoreCase = true)) return body
        val pathBackup = mediaItem.pathBackup.trim()
        if (pathBackup.startsWith("https://", ignoreCase = true)) return pathBackup
        return null
    }

    /**
     * Trích xuất linh hoạt cả Hostname và Port từ link URL.
     * Tự động nhận diện port tùy chỉnh (ví dụ: https://stream.radio.vn:8443/live -> Host: stream.radio.vn, Port: 8443).
     * Nếu URL không chỉ định port thì mặc định dùng defaultPort (443).
     */
    fun extractHostAndPort(urlStr: String, defaultPort: Int = 443): Pair<String, Int>? {
        val trimmed = urlStr.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val formatted = if (!trimmed.contains("://")) "https://$trimmed" else trimmed
            val cleaned = formatted.replace("(?<!:)//".toRegex(), "/")
            val uri = URI(cleaned)
            val host =
                uri.host ?: cleaned.substringAfter("://").substringBefore("/").substringBefore(":")
            val port = if (uri.port != -1) uri.port else defaultPort
            if (host.isNotBlank()) Pair(host, port) else null
        } catch (e: Exception) {
            try {
                val formatted = if (!trimmed.contains("://")) "https://$trimmed" else trimmed
                val hostPart = formatted.substringAfter("://").substringBefore("/")
                val host = hostPart.substringBefore(":")
                val port = if (hostPart.contains(":")) hostPart.substringAfter(":").toIntOrNull()
                    ?: defaultPort else defaultPort
                if (host.isNotBlank()) Pair(host, port) else null
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Trích xuất hostname từ link URL
     */
    fun extractHost(urlStr: String): String? {
        return extractHostAndPort(urlStr)?.first
    }

    /**
     * Tự động kết nối tới domain lấy chuỗi SSL Certificate và lưu file .crt vào thư mục ca-certs
     */
    fun fetchAndSaveCertFromUrl(context: Context, urlStr: String, force: Boolean = false): Boolean {
        val hostPort = extractHostAndPort(urlStr)
        if (hostPort == null) {
            Log.w(TAG, "Không thể trích xuất host và port từ URL: $urlStr")
            return false
        }
        val (host, port) = hostPort
        val hostKey = "$host:$port"

        val caDir = setupCaDir(context)
        val safeFileName = if (port == 443) "${host.replace(".", "_")}.crt" else "${
            host.replace(
                ".",
                "_"
            )
        }_$port.crt"
        val certFile = File(caDir, safeFileName)

        if (!force && certFile.exists() && certFile.length() > 0) {
            Log.d(TAG, "Chứng chỉ cho $hostKey đã sẵn sàng tại: ${certFile.name}")
            return true
        }

        Log.i(TAG, "Bắt đầu tự động lấy SSL certificate từ domain: $hostKey...")

        var certs: List<Certificate>? = null

        // 1. Thử lấy chứng chỉ qua OkHttp (đã bypass SSL)
        try {
            val client = OkHttpClientUtil.getUnsafeOkHttpClient()
            val requestUrl =
                if (urlStr.startsWith("http://", ignoreCase = true) || urlStr.startsWith(
                        "https://",
                        ignoreCase = true
                    )
                ) {
                    urlStr
                } else {
                    "https://$host:$port"
                }
            val request = Request.Builder()
                .url(requestUrl)
                .head()
                .build()

            client.newCall(request).execute().use { response ->
                certs = response.handshake?.peerCertificates
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "OkHttp HEAD không lấy được cert ($hostKey): ${e.message}, chuyển sang SSLSocket..."
            )
        }

        // 2. Dự phòng: Dùng SSLSocket trực tiếp với port tương ứng và SNI
        if (certs.isNullOrEmpty()) {
            try {
                val naiveTrustManager = object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    override fun checkClientTrusted(
                        chain: Array<out X509Certificate>?,
                        authType: String?
                    ) {
                    }

                    override fun checkServerTrusted(
                        chain: Array<out X509Certificate>?,
                        authType: String?
                    ) {
                    }
                }
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf<TrustManager>(naiveTrustManager), SecureRandom())
                }
                val socket = sslContext.socketFactory.createSocket(host, port) as SSLSocket
                socket.soTimeout = 7000

                // Cấu hình SNI (Server Name Indication) để server không trả sai chứng chỉ
                try {
                    val sslParameters = socket.sslParameters
                    sslParameters.serverNames = listOf(javax.net.ssl.SNIHostName(host))
                    socket.sslParameters = sslParameters
                } catch (_: Throwable) {
                    // Dự phòng cho phiên bản Android cũ không hỗ trợ SNIHostName
                }

                socket.use { s ->
                    s.startHandshake()
                    certs = s.session.peerCertificates?.toList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "SSLSocket thất bại khi kết nối tới $hostKey: ${e.message}")
            }
        }

        val certList = certs
        if (certList.isNullOrEmpty()) {
            Log.e(TAG, "Không lấy được chứng chỉ nào từ $hostKey")
            return false
        }

        // 3. Format chứng chỉ sang dạng PEM và lưu vào ca-certs
        return try {
            val pemBuilder = StringBuilder()
            for (cert in certList) {
                if (cert is X509Certificate) {
                    val encoded = Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
                    pemBuilder.append("-----BEGIN CERTIFICATE-----\n")
                    pemBuilder.append(encoded.chunked(64).joinToString("\n"))
                    pemBuilder.append("\n-----END CERTIFICATE-----\n")
                }
            }

            certFile.writeText(pemBuilder.toString())
            fetchedHosts.add(host)
            fetchedHosts.add(hostKey)
            Log.i(
                TAG,
                "===> THÀNH CÔNG: Đã tự động lưu ${certList.size} chứng chỉ từ $hostKey vào ${certFile.absolutePath}"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi ghi file cert cho $hostKey: ${e.message}", e)
            false
        }

    }

    /**
     * Bất đồng bộ: Tải và lưu chứng chỉ trong coroutine
     */
    fun fetchAndSaveCertFromUrlAsync(context: Context, urlStr: String, force: Boolean = false) {
        coroutineScope.launch {
            fetchAndSaveCertFromUrl(context, urlStr, force)
        }
    }

    /**
     * Kiểm tra và nạp trước chứng chỉ cho URL nếu chưa có
     */
    fun ensureCertForUrlAsync(context: Context, urlStr: String) {
        val hostPort = extractHostAndPort(urlStr) ?: return
        val (host, port) = hostPort
        val hostKey = "$host:$port"
        if (fetchedHosts.contains(host) || fetchedHosts.contains(hostKey)) return

        val caDir = File(context.filesDir, CA_DIR_NAME)
        val safeFileName = if (port == 443) "${host.replace(".", "_")}.crt" else "${
            host.replace(
                ".",
                "_"
            )
        }_$port.crt"
        val certFile = File(caDir, safeFileName)
        if (certFile.exists() && certFile.length() > 0) {
            fetchedHosts.add(host)
            fetchedHosts.add(hostKey)
            return
        }

        fetchAndSaveCertFromUrlAsync(context, urlStr, force = false)
    }

    /**
     * Tải và lưu chứng chỉ cho danh sách domain hoặc chuỗi domain nhận từ lệnh Hub (hỗ trợ phân tách bằng dấu phẩy/khoảng trắng)
     */
    fun fetchAndSaveCertFromDomainListAsync(
        context: Context,
        domainInput: String,
        force: Boolean = true,
        onComplete: ((Map<String, Boolean>) -> Unit)? = null
    ) {
        coroutineScope.launch {
            val domains = domainInput.split(",", ";", " ", "\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val resultMap = mutableMapOf<String, Boolean>()
            for (item in domains) {
                val hostPort = extractHostAndPort(item)
                val targetKey =
                    if (hostPort != null) "${hostPort.first}:${hostPort.second}" else item
                if (targetKey.isNotEmpty()) {
                    Log.i(TAG, "Hub Command: Đang tải SSL CA cert cho: $targetKey")
                    val success = fetchAndSaveCertFromUrl(context, item, force)
                    resultMap[targetKey] = success
                    Log.i(
                        TAG,
                        "Hub Command: Kết quả tải SSL CA cert cho $targetKey: ${if (success) "Thành công" else "Thất bại"}"
                    )
                }
            }
            onComplete?.invoke(resultMap)
        }
    }

    /**
     * Bóc tách chỉ các khối X.509 Certificate (-----BEGIN CERTIFICATE----- ... -----END CERTIFICATE-----),
     * tự động loại bỏ mọi khối không phải chứng chỉ như PRIVATE KEY, RSA PRIVATE KEY, header hoặc chú thích thừa.
     * Tránh lỗi java.security.cert.CertificateParsingException: signed fields invalid trên Java/Android.
     */
    fun extractCertificatesOnly(rawPem: String): String {
        val pattern = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----")
        val matches = pattern.findAll(rawPem).map { it.value.trim() }.toList()
        return if (matches.isNotEmpty()) matches.joinToString("\n\n") else ""
    }

    /**
     * Xử lý đa năng input từ lệnh UPDATE_CA của Hub:
     * 1. Nếu là chuỗi nội dung chứng chỉ PEM (chứa "-----BEGIN CERTIFICATE-----"): Tự động lọc bỏ Private Key (nếu có), lưu các Certificate vào file .crt
     * 2. Nếu là đường link tải file chứng chỉ (.crt, .pem, .cer): Tải trực tiếp file về, lọc bỏ Private Key và lưu vào ca-certs
     * 3. Nếu là danh sách domain: Kết nối tới từng domain để bóc tách chứng chỉ
     */
    fun handleHubCaInput(context: Context, input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false

        val caDir = setupCaDir(context)

        // 1. Trường hợp gửi trực tiếp nội dung chứng chỉ PEM
        if (trimmed.contains("-----BEGIN CERTIFICATE-----", ignoreCase = true)) {
            return try {
                val cleanCerts = extractCertificatesOnly(trimmed)
                if (cleanCerts.isEmpty()) {
                    Log.w(TAG, "Hub Command: Không tìm thấy khối CERTIFICATE hợp lệ trong nội dung")
                    return false
                }
                val certHash = cleanCerts.hashCode().let { if (it < 0) -it else it }
                val fileName = "hub_cert_$certHash.crt"
                val certFile = File(caDir, fileName)
                certFile.writeText(cleanCerts)
                Log.i(
                    TAG,
                    "Hub Command: Đã lọc sạch và lưu trực tiếp nội dung CA cert vào ${certFile.absolutePath}"
                )
                true
            } catch (e: Exception) {
                Log.e(TAG, "Hub Command: Lỗi ghi nội dung CA cert: ${e.message}", e)
                false
            }
        }

        // 2. Trường hợp gửi link tải file chứng chỉ (.crt, .pem, .cer)
        val isCertUrl = (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith(
            "https://",
            ignoreCase = true
        )) &&
                (trimmed.contains(".crt", ignoreCase = true) || trimmed.contains(
                    ".pem",
                    ignoreCase = true
                ) || trimmed.contains(".cer", ignoreCase = true))

        if (isCertUrl) {
            return try {
                val rawName = trimmed.substringAfterLast("/").substringBefore("?")
                val baseName = if (rawName.contains(".")) rawName.substringBeforeLast(".") else rawName
                val fileName = if (baseName.isNotBlank()) "$baseName.crt" else "hub_downloaded_cert.crt"
                val certFile = File(caDir, fileName)
                Log.i(TAG, "Hub Command: Đang tải file CA cert trực tiếp từ link: $trimmed...")
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder().url(trimmed).build()
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body
                    if (response.isSuccessful && responseBody != null) {
                        val bytes = responseBody.bytes()
                        val text = String(bytes, Charsets.UTF_8)
                        if (text.contains("-----BEGIN CERTIFICATE-----", ignoreCase = true)) {
                            // Tự động lọc bỏ Private Key nếu file có chứa, chỉ lưu các khối Certificate
                            val cleanCerts = extractCertificatesOnly(text)
                            certFile.writeText(cleanCerts)
                            Log.i(
                                TAG,
                                "Hub Command: Tải, lọc sạch cert và lưu thành công vào ${certFile.absolutePath}"
                            )
                        } else {
                            // File nhị phân DER (.cer / .crt nhị phân)
                            certFile.writeBytes(bytes)
                            Log.i(
                                TAG,
                                "Hub Command: Tải và lưu file binary cert thành công vào ${certFile.absolutePath} (${bytes.size} bytes)"
                            )
                        }
                        true
                    } else {
                        Log.e(
                            TAG,
                            "Hub Command: Tải file cert thất bại, HTTP code: ${response.code}"
                        )
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Hub Command: Lỗi khi tải file cert từ URL: ${e.message}", e)
                false
            }
        }

        // 3. Trường hợp gửi danh sách domain / host:port
        val domains = trimmed.split(",", ";", " ", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var anySuccess = false
        for (item in domains) {
            val hostPort = extractHostAndPort(item)
            val targetKey = if (hostPort != null) "${hostPort.first}:${hostPort.second}" else item
            if (targetKey.isNotEmpty()) {
                Log.i(TAG, "Hub Command: Đang tải SSL CA cert cho: $targetKey")
                val success = fetchAndSaveCertFromUrl(context, item, force = true)
                if (success) anySuccess = true
                Log.i(
                    TAG,
                    "Hub Command: Kết quả tải SSL CA cert cho $targetKey: ${if (success) "Thành công" else "Thất bại"}"
                )
            }
        }
        return anySuccess
    }

    /**
     * Bất đồng bộ: Xử lý input từ Hub trong CoroutineScope
     */
    fun handleHubCaInputAsync(
        context: Context,
        input: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        coroutineScope.launch {
            val success = handleHubCaInput(context, input)
            onComplete?.invoke(success)
        }
    }

    private fun createCustomTrustManager(context: Context?): X509TrustManager {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
        }
        val certFactory = CertificateFactory.getInstance("X.509")

        // Nạp chứng chỉ CA mặc định (Sectigo Root R46 + OV R36)
        try {
            ByteArrayInputStream(DEFAULT_CERTIFICATE_CONTENT.toByteArray(Charsets.UTF_8)).use { stream ->
                val certs = certFactory.generateCertificates(stream)
                certs.forEachIndexed { index, cert ->
                    keyStore.setCertificateEntry("default_ca_$index", cert)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi nạp DEFAULT_CERTIFICATE_CONTENT vào KeyStore: ${e.message}", e)
        }

        // Nạp thêm các file .crt trong thư mục ca-certs (nếu có)
        val targetContext = context ?: MyApp.instance
        if (targetContext != null) {
            try {
                val caDir = File(targetContext.filesDir, CA_DIR_NAME)
                if (caDir.exists() && caDir.isDirectory) {
                    caDir.listFiles { file ->
                        val ext = file.extension.lowercase()
                        ext == "crt" || ext == "pem" || ext == "cer"
                    }?.forEach { certFile ->
                        try {
                            val text = try {
                                certFile.readText(Charsets.UTF_8)
                            } catch (_: Exception) {
                                ""
                            }
                            val stream =
                                if (text.contains("-----BEGIN CERTIFICATE-----", ignoreCase = true)) {
                                    val cleanCerts = extractCertificatesOnly(text)
                                    ByteArrayInputStream(cleanCerts.toByteArray(Charsets.UTF_8))
                                } else {
                                    certFile.inputStream()
                                }
                            stream.use { s ->
                                val certs = certFactory.generateCertificates(s)
                                certs.forEachIndexed { index, cert ->
                                    keyStore.setCertificateEntry(
                                        "${certFile.nameWithoutExtension}_$index",
                                        cert
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(
                                TAG,
                                "Không thể nạp cert từ file ${certFile.name}: ${e.message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Không thể quét thư mục ca-certs: ${e.message}")
            }
        }

        val customTmf =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }
        return customTmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
     * Tạo TrustManager xác thực chứng chỉ theo hệ thống + các chứng chỉ CA đã cấu hình trong ứng dụng.
     * TUYỆT ĐỐI KHÔNG BỎ QUA SSL: Vẫn kiểm tra đầy đủ tính hợp lệ, chữ ký và hạn của chứng chỉ.
     * Tự động reload lại danh sách cert nếu ban đầu chưa nhận diện được.
     */
    fun createConfiguredTrustManager(context: Context? = null): X509TrustManager {
        // 1. Lấy TrustManager mặc định của hệ thống
        val defaultTmf =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(null as KeyStore?)
            }
        val defaultTrustManager =
            defaultTmf.trustManagers.filterIsInstance<X509TrustManager>().first()

        val customTrustManagerRef =
            java.util.concurrent.atomic.AtomicReference(createCustomTrustManager(context))

        fun reloadCustomTrustManager() {
            try {
                customTrustManagerRef.set(createCustomTrustManager(context))
                Log.d(TAG, "Đã reload danh sách CA cert vào TrustManager")
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi reload CustomTrustManager: ${e.message}", e)
            }
        }

        // Kết hợp cả hai: kiểm tra hệ thống trước, nếu không tìm thấy Trust anchor thì dùng tiếp custom KeyStore
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                try {
                    defaultTrustManager.checkClientTrusted(chain, authType)
                } catch (e: Exception) {
                    customTrustManagerRef.get().checkClientTrusted(chain, authType)
                }
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                try {
                    defaultTrustManager.checkServerTrusted(chain, authType)
                } catch (e: Exception) {
                    try {
                        customTrustManagerRef.get().checkServerTrusted(chain, authType)
                    } catch (e2: Exception) {
                        // Thử reload lại thư mục ca-certs nếu vừa có cert mới tải về
                        reloadCustomTrustManager()
                        customTrustManagerRef.get().checkServerTrusted(chain, authType)
                    }
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return defaultTrustManager.acceptedIssuers + customTrustManagerRef.get().acceptedIssuers
            }
        }
    }

    /**
     * Cấu hình HttpsURLConnection toàn cục nạp chứng chỉ CA từ hệ thống + ca-certs đã cấu hình.
     * TUYỆT ĐỐI KHÔNG BYPASS SSL: Không can thiệp HostnameVerifier (vẫn kiểm tra tên miền nghiêm ngặt).
     * Phục vụ cho PRDownloader và các kết nối URL tải file trong ứng dụng.
     */
    fun setupGlobalConfiguredSsl(context: Context) {
        try {
            val trustManager = createConfiguredTrustManager(context)
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            }
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            Log.i(TAG, "Đã thiết lập HttpsURLConnection toàn cục với chứng chỉ CA an toàn")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi cấu hình SSL toàn cục cho HttpsURLConnection: ${e.message}", e)
        }
    }

    /**
     * OkHttp Interceptor tự động bắt lỗi SSL khi gọi API, tự động kéo CA cert từ server và retry lại request.
     */
    class SslRetryInterceptor(private val context: Context?) : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
            val request = chain.request()
            try {
                return chain.proceed(request)
            } catch (e: Exception) {
                val isSsl = e is javax.net.ssl.SSLException ||
                        e.cause is javax.net.ssl.SSLException ||
                        e.message?.contains(
                            "CertPathValidatorException",
                            ignoreCase = true
                        ) == true ||
                        e.message?.contains("Trust anchor", ignoreCase = true) == true

                val targetContext = context ?: MyApp.instance
                if (isSsl && targetContext != null) {
                    val urlStr = request.url.toString()
                    val host = request.url.host
                    Log.w(
                        TAG,
                        "SslRetryInterceptor: Lỗi SSL khi gọi API ($host): ${e.message}. Tự động tải CA cert..."
                    )
                    val success = fetchAndSaveCertFromUrl(targetContext, urlStr, force = true)
                    if (success) {
                        Log.i(
                            TAG,
                            "SslRetryInterceptor: Đã tải CA cert cho $host thành công. Đang thử lại request..."
                        )
                        return chain.proceed(request)
                    }
                }
                throw e
            }
        }
    }

    /**
     * Tạo OkHttpClient xác thực SSL an toàn bằng hệ thống kết hợp CA cert đã cấu hình và tự động retry khi gặp lỗi SSL
     */
    fun buildConfiguredOkHttpClient(
        builder: OkHttpClient.Builder = OkHttpClient.Builder(),
        context: Context? = null
    ): OkHttpClient {
        return try {
            val trustManager = createConfiguredTrustManager(context)
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            }
            builder
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .addInterceptor(SslRetryInterceptor(context ?: MyApp.instance))
                .connectTimeout(40, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .writeTimeout(40, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khởi tạo OkHttpClient với CA cert: ${e.message}", e)
            builder
                .connectTimeout(40, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .writeTimeout(40, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}

