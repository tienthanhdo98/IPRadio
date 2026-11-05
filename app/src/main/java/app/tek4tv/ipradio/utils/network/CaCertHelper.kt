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
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }
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
     * Khß╗ƒi tß║ío v├á trß║ú vß╗ü th╞░ mß╗Ñc chß╗⌐a c├íc CA cert bß╗ò sung cho LibVLC
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
                Log.d(TAG, "─É├ú khß╗ƒi tß║ío file CA cert mß║╖c ─æß╗ïnh tß║íi: ${certFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Lß╗ùi ghi file CA cert mß║╖c ─æß╗ïnh: ${e.message}", e)
            }
        }
        return caDir
    }

    /**
     * Tr├¡ch xuß║Ñt link HTTPS tß╗½ MediaItem (body hoß║╖c pathBackup)
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
     * Tr├¡ch xuß║Ñt linh hoß║ít cß║ú Hostname v├á Port tß╗½ link URL.
     * Tß╗▒ ─æß╗Öng nhß║¡n diß╗çn port t├╣y chß╗ënh (v├¡ dß╗Ñ: https://stream.radio.vn:8443/live -> Host: stream.radio.vn, Port: 8443).
     * Nß║┐u URL kh├┤ng chß╗ë ─æß╗ïnh port th├¼ mß║╖c ─æß╗ïnh d├╣ng defaultPort (443).
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
     * Tr├¡ch xuß║Ñt hostname tß╗½ link URL
     */
    fun extractHost(urlStr: String): String? {
        return extractHostAndPort(urlStr)?.first
    }

    /**
     * Tß╗▒ ─æß╗Öng kß║┐t nß╗æi tß╗¢i domain lß║Ñy chuß╗ùi SSL Certificate v├á l╞░u file .crt v├áo th╞░ mß╗Ñc ca-certs
     */
    fun fetchAndSaveCertFromUrl(context: Context, urlStr: String, force: Boolean = false): Boolean {
        val hostPort = extractHostAndPort(urlStr)
        if (hostPort == null) {
            Log.w(TAG, "Kh├┤ng thß╗â tr├¡ch xuß║Ñt host v├á port tß╗½ URL: $urlStr")
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
            Log.d(TAG, "Chß╗⌐ng chß╗ë cho $hostKey ─æ├ú sß║╡n s├áng tß║íi: ${certFile.name}")
            return true
        }

        Log.i(TAG, "Bß║»t ─æß║ºu tß╗▒ ─æß╗Öng lß║Ñy SSL certificate tß╗½ domain: $hostKey...")

        var certs: List<Certificate>? = null

        // 1. Thß╗¡ lß║Ñy chß╗⌐ng chß╗ë qua OkHttp (─æ├ú bypass SSL)
        try {
            val client = getUnsafeOkHttpClient()
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
                certs = response.handshake()?.peerCertificates()
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "OkHttp HEAD kh├┤ng lß║Ñy ─æ╞░ß╗úc cert ($hostKey): ${e.message}, chuyß╗ân sang SSLSocket..."
            )
        }

        // 2. Dß╗▒ ph├▓ng: D├╣ng SSLSocket trß╗▒c tiß║┐p vß╗¢i port t╞░╞íng ß╗⌐ng v├á SNI
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

                // Cß║Ñu h├¼nh SNI (Server Name Indication) ─æß╗â server kh├┤ng trß║ú sai chß╗⌐ng chß╗ë
                try {
                    val sslParameters = socket.sslParameters
                    sslParameters.serverNames = listOf(javax.net.ssl.SNIHostName(host))
                    socket.sslParameters = sslParameters
                } catch (_: Throwable) {
                    // Dß╗▒ ph├▓ng cho phi├¬n bß║ún Android c┼⌐ kh├┤ng hß╗ù trß╗ú SNIHostName
                }

                socket.use { s ->
                    s.startHandshake()
                    certs = s.session.peerCertificates?.toList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "SSLSocket thß║Ñt bß║íi khi kß║┐t nß╗æi tß╗¢i $hostKey: ${e.message}")
            }
        }

        val certList = certs
        if (certList.isNullOrEmpty()) {
            Log.e(TAG, "Kh├┤ng lß║Ñy ─æ╞░ß╗úc chß╗⌐ng chß╗ë n├áo tß╗½ $hostKey")
            return false
        }

        // 3. Format chß╗⌐ng chß╗ë sang dß║íng PEM v├á l╞░u v├áo ca-certs
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
                "===> TH├ÇNH C├öNG: ─É├ú tß╗▒ ─æß╗Öng l╞░u ${certList.size} chß╗⌐ng chß╗ë tß╗½ $hostKey v├áo ${certFile.absolutePath}"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Lß╗ùi khi ghi file cert cho $hostKey: ${e.message}", e)
            false
        }

    }

    /**
     * Bß║Ñt ─æß╗ông bß╗Ö: Tß║úi v├á l╞░u chß╗⌐ng chß╗ë trong coroutine
     */
    fun fetchAndSaveCertFromUrlAsync(context: Context, urlStr: String, force: Boolean = false) {
        coroutineScope.launch {
            fetchAndSaveCertFromUrl(context, urlStr, force)
        }
    }

    /**
     * Kiß╗âm tra v├á nß║íp tr╞░ß╗¢c chß╗⌐ng chß╗ë cho URL nß║┐u ch╞░a c├│
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
     * Tß║úi v├á l╞░u chß╗⌐ng chß╗ë cho danh s├ích domain hoß║╖c chuß╗ùi domain nhß║¡n tß╗½ lß╗çnh Hub (hß╗ù trß╗ú ph├ón t├ích bß║▒ng dß║Ñu phß║⌐y/khoß║úng trß║»ng)
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
                    Log.i(TAG, "Hub Command: ─Éang tß║úi SSL CA cert cho: $targetKey")
                    val success = fetchAndSaveCertFromUrl(context, item, force)
                    resultMap[targetKey] = success
                    Log.i(
                        TAG,
                        "Hub Command: Kß║┐t quß║ú tß║úi SSL CA cert cho $targetKey: ${if (success) "Th├ánh c├┤ng" else "Thß║Ñt bß║íi"}"
                    )
                }
            }
            onComplete?.invoke(resultMap)
        }
    }

    /**
     * B├│c t├ích chß╗ë c├íc khß╗æi X.509 Certificate (-----BEGIN CERTIFICATE----- ... -----END CERTIFICATE-----),
     * tß╗▒ ─æß╗Öng loß║íi bß╗Å mß╗ìi khß╗æi kh├┤ng phß║úi chß╗⌐ng chß╗ë nh╞░ PRIVATE KEY, RSA PRIVATE KEY, header hoß║╖c ch├║ th├¡ch thß╗½a.
     * Tr├ính lß╗ùi java.security.cert.CertificateParsingException: signed fields invalid tr├¬n Java/Android.
     */
    fun extractCertificatesOnly(rawPem: String): String {
        val pattern = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----")
        val matches = pattern.findAll(rawPem).map { it.value.trim() }.toList()
        return if (matches.isNotEmpty()) matches.joinToString("\n\n") else ""
    }

    /**
     * Xß╗¡ l├╜ ─æa n─âng input tß╗½ lß╗çnh UPDATE_CA cß╗ºa Hub:
     * 1. Nß║┐u l├á chuß╗ùi nß╗Öi dung chß╗⌐ng chß╗ë PEM (chß╗⌐a "-----BEGIN CERTIFICATE-----"): Tß╗▒ ─æß╗Öng lß╗ìc bß╗Å Private Key (nß║┐u c├│), l╞░u c├íc Certificate v├áo file .crt
     * 2. Nß║┐u l├á ─æ╞░ß╗¥ng link tß║úi file chß╗⌐ng chß╗ë (.crt, .pem, .cer): Tß║úi trß╗▒c tiß║┐p file vß╗ü, lß╗ìc bß╗Å Private Key v├á l╞░u v├áo ca-certs
     * 3. Nß║┐u l├á danh s├ích domain: Kß║┐t nß╗æi tß╗¢i tß╗½ng domain ─æß╗â b├│c t├ích chß╗⌐ng chß╗ë
     */
    fun handleHubCaInput(context: Context, input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false

        val caDir = setupCaDir(context)

        // 1. Tr╞░ß╗¥ng hß╗úp gß╗¡i trß╗▒c tiß║┐p nß╗Öi dung chß╗⌐ng chß╗ë PEM
        if (trimmed.contains("-----BEGIN CERTIFICATE-----", ignoreCase = true)) {
            return try {
                val cleanCerts = extractCertificatesOnly(trimmed)
                if (cleanCerts.isEmpty()) {
                    Log.w(TAG, "Hub Command: Kh├┤ng t├¼m thß║Ñy khß╗æi CERTIFICATE hß╗úp lß╗ç trong nß╗Öi dung")
                    return false
                }
                val certHash = cleanCerts.hashCode().let { if (it < 0) -it else it }
                val fileName = "hub_cert_$certHash.crt"
                val certFile = File(caDir, fileName)
                certFile.writeText(cleanCerts)
                Log.i(
                    TAG,
                    "Hub Command: ─É├ú lß╗ìc sß║ích v├á l╞░u trß╗▒c tiß║┐p nß╗Öi dung CA cert v├áo ${certFile.absolutePath}"
                )
                true
            } catch (e: Exception) {
                Log.e(TAG, "Hub Command: Lß╗ùi ghi nß╗Öi dung CA cert: ${e.message}", e)
                false
            }
        }

        // 2. Tr╞░ß╗¥ng hß╗úp gß╗¡i link tß║úi file chß╗⌐ng chß╗ë (.crt, .pem, .cer)
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
                Log.i(TAG, "Hub Command: ─Éang tß║úi file CA cert trß╗▒c tiß║┐p tß╗½ link: $trimmed...")
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder().url(trimmed).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body() != null) {
                        val bytes = response.body()!!.bytes()
                        val text = String(bytes, Charsets.UTF_8)
                        if (text.contains("-----BEGIN CERTIFICATE-----", ignoreCase = true)) {
                            // Tß╗▒ ─æß╗Öng lß╗ìc bß╗Å Private Key nß║┐u file c├│ chß╗⌐a, chß╗ë l╞░u c├íc khß╗æi Certificate
                            val cleanCerts = extractCertificatesOnly(text)
                            certFile.writeText(cleanCerts)
                            Log.i(
                                TAG,
                                "Hub Command: Tß║úi, lß╗ìc sß║ích cert v├á l╞░u th├ánh c├┤ng v├áo ${certFile.absolutePath}"
                            )
                        } else {
                            // File nhß╗ï ph├ón DER (.cer / .crt nhß╗ï ph├ón)
                            certFile.writeBytes(bytes)
                            Log.i(
                                TAG,
                                "Hub Command: Tß║úi v├á l╞░u file binary cert th├ánh c├┤ng v├áo ${certFile.absolutePath} (${bytes.size} bytes)"
                            )
                        }
                        true
                    } else {
                        Log.e(
                            TAG,
                            "Hub Command: Tß║úi file cert thß║Ñt bß║íi, HTTP code: ${response.code()}"
                        )
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Hub Command: Lß╗ùi khi tß║úi file cert tß╗½ URL: ${e.message}", e)
                false
            }
        }

        // 3. Tr╞░ß╗¥ng hß╗úp gß╗¡i danh s├ích domain / host:port
        val domains = trimmed.split(",", ";", " ", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var anySuccess = false
        for (item in domains) {
            val hostPort = extractHostAndPort(item)
            val targetKey = if (hostPort != null) "${hostPort.first}:${hostPort.second}" else item
            if (targetKey.isNotEmpty()) {
                Log.i(TAG, "Hub Command: ─Éang tß║úi SSL CA cert cho: $targetKey")
                val success = fetchAndSaveCertFromUrl(context, item, force = true)
                if (success) anySuccess = true
                Log.i(
                    TAG,
                    "Hub Command: Kß║┐t quß║ú tß║úi SSL CA cert cho $targetKey: ${if (success) "Th├ánh c├┤ng" else "Thß║Ñt bß║íi"}"
                )
            }
        }
        return anySuccess
    }

    /**
     * Bß║Ñt ─æß╗ông bß╗Ö: Xß╗¡ l├╜ input tß╗½ Hub trong CoroutineScope
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

        // Nß║íp chß╗⌐ng chß╗ë CA mß║╖c ─æß╗ïnh (Sectigo Root R46 + OV R36)
        try {
            ByteArrayInputStream(DEFAULT_CERTIFICATE_CONTENT.toByteArray(Charsets.UTF_8)).use { stream ->
                val certs = certFactory.generateCertificates(stream)
                certs.forEachIndexed { index, cert ->
                    keyStore.setCertificateEntry("default_ca_$index", cert)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lß╗ùi nß║íp DEFAULT_CERTIFICATE_CONTENT v├áo KeyStore: ${e.message}", e)
        }

        // Nß║íp th├¬m c├íc file .crt trong th╞░ mß╗Ñc ca-certs (nß║┐u c├│)
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
                                "Kh├┤ng thß╗â nß║íp cert tß╗½ file ${certFile.name}: ${e.message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Kh├┤ng thß╗â qu├⌐t th╞░ mß╗Ñc ca-certs: ${e.message}")
            }
        }

        val customTmf =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }
        return customTmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
     * Tß║ío TrustManager x├íc thß╗▒c chß╗⌐ng chß╗ë theo hß╗ç thß╗æng + c├íc chß╗⌐ng chß╗ë CA ─æ├ú cß║Ñu h├¼nh trong ß╗⌐ng dß╗Ñng.
     * TUYß╗åT ─Éß╗ÉI KH├öNG Bß╗Ä QUA SSL: Vß║½n kiß╗âm tra ─æß║ºy ─æß╗º t├¡nh hß╗úp lß╗ç, chß╗» k├╜ v├á hß║ín cß╗ºa chß╗⌐ng chß╗ë.
     * Tß╗▒ ─æß╗Öng reload lß║íi danh s├ích cert nß║┐u ban ─æß║ºu ch╞░a nhß║¡n diß╗çn ─æ╞░ß╗úc.
     */
    fun createConfiguredTrustManager(context: Context? = null): X509TrustManager {
        // 1. Lß║Ñy TrustManager mß║╖c ─æß╗ïnh cß╗ºa hß╗ç thß╗æng
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
                Log.d(TAG, "─É├ú reload danh s├ích CA cert v├áo TrustManager")
            } catch (e: Exception) {
                Log.e(TAG, "Lß╗ùi reload CustomTrustManager: ${e.message}", e)
            }
        }

        // Kß║┐t hß╗úp cß║ú hai: kiß╗âm tra hß╗ç thß╗æng tr╞░ß╗¢c, nß║┐u kh├┤ng t├¼m thß║Ñy Trust anchor th├¼ d├╣ng tiß║┐p custom KeyStore
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
                        // Thß╗¡ reload lß║íi th╞░ mß╗Ñc ca-certs nß║┐u vß╗½a c├│ cert mß╗¢i tß║úi vß╗ü
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
     * Cß║Ñu h├¼nh HttpsURLConnection to├án cß╗Ñc nß║íp chß╗⌐ng chß╗ë CA tß╗½ hß╗ç thß╗æng + ca-certs ─æ├ú cß║Ñu h├¼nh.
     * TUYß╗åT ─Éß╗ÉI KH├öNG BYPASS SSL: Kh├┤ng can thiß╗çp HostnameVerifier (vß║½n kiß╗âm tra t├¬n miß╗ün nghi├¬m ngß║╖t).
     * Phß╗Ñc vß╗Ñ cho PRDownloader v├á c├íc kß║┐t nß╗æi URL tß║úi file trong ß╗⌐ng dß╗Ñng.
     */
    fun setupGlobalConfiguredSsl(context: Context) {
        try {
            val trustManager = createConfiguredTrustManager(context)
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            }
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            Log.i(TAG, "─É├ú thiß║┐t lß║¡p HttpsURLConnection to├án cß╗Ñc vß╗¢i chß╗⌐ng chß╗ë CA an to├án")
        } catch (e: Exception) {
            Log.e(TAG, "Lß╗ùi khi cß║Ñu h├¼nh SSL to├án cß╗Ñc cho HttpsURLConnection: ${e.message}", e)
        }
    }

    /**
     * OkHttp Interceptor tß╗▒ ─æß╗Öng bß║»t lß╗ùi SSL khi gß╗ìi API, tß╗▒ ─æß╗Öng k├⌐o CA cert tß╗½ server v├á retry lß║íi request.
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
                    val urlStr = request.url().toString()
                    val host = request.url().host()
                    Log.w(
                        TAG,
                        "SslRetryInterceptor: Lß╗ùi SSL khi gß╗ìi API ($host): ${e.message}. Tß╗▒ ─æß╗Öng tß║úi CA cert..."
                    )
                    val success = fetchAndSaveCertFromUrl(targetContext, urlStr, force = true)
                    if (success) {
                        Log.i(
                            TAG,
                            "SslRetryInterceptor: ─É├ú tß║úi CA cert cho $host th├ánh c├┤ng. ─Éang thß╗¡ lß║íi request..."
                        )
                        return chain.proceed(request)
                    }
                }
                throw e
            }
        }
    }

    /**
     * Tß║ío OkHttpClient x├íc thß╗▒c SSL an to├án bß║▒ng hß╗ç thß╗æng kß║┐t hß╗úp CA cert ─æ├ú cß║Ñu h├¼nh v├á tß╗▒ ─æß╗Öng retry khi gß║╖p lß╗ùi SSL
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
            Log.e(TAG, "Lß╗ùi khß╗ƒi tß║ío OkHttpClient vß╗¢i CA cert: ${e.message}", e)
            builder
                .connectTimeout(40, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .writeTimeout(40, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
