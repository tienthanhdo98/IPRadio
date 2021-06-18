package app.tek4tv.ipradio.viewmodels


import android.content.Context
import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.*
import kotlinx.coroutines.*


class MainViewModel @ViewModelInject constructor(
    internal val mediaRepo: MediaRepo
) : ViewModel() {
    val content = MutableLiveData<Pair<List<MediaItem>, List<String>>>()

    private var getContentJob: Job? = null

    private var mediaWatchdog = MediaWatchDog(viewModelScope, mediaRepo)

    var currentMedia : MediaItem? = null

    fun getPlayerContent(listUrl: String, needUpdate: Boolean)
    {
        viewModelScope.launch {
            getContentJob?.join()

            //cancel media check here
            mediaWatchdog.cancelCheck()

            getContentJob = launch {
                val mediaList = mediaRepo.getScenario(needUpdate, listUrl)

                content.value = Pair(mediaList, listOf())

                getContentJob = null
            }
        }
    }

    fun checkPlaylist() {
        mediaWatchdog.checkPlaylist()
    }

    fun downloadMedias() {
        mediaRepo.startDownloadMedia()
    }

    fun updateMonthlyScenario(url : String, forceNewScenario : Boolean = false)
    {
        viewModelScope.launch {
            mediaRepo.updateMonthlyScenario(url, forceNewScenario)
            getPlayerContent("", false)
        }
    }

    fun testDownloadQueue(appContext : Context)
    {
        val folderPath = "${appContext.filesDir.path}/testdownload"
        if(!isFolderExisted(folderPath)) createFolder(folderPath)
        val downloadHelper = DownloadHelper2.getInstance()

        val url1 = listOf(
            "https://aredir.nixcdn.com/Unv_Audio139/KeepYourEyesOnMe-KumiraNASON-5961632_hq.mp3?st=-ED6sFYk5vsiMA2StvdKEg&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui230/AllForYou-SeoInGuk-EunJiAPink_47c7b_hq.mp3?st=CY-H-E5bJC0rPEEqMjb5FA&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui953/BecauseImStupid-SS501-205763_hq.mp3?st=fJ7TDd9be-BKp8-ojS8Afg&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui004/HowAreYouAudition-Jewellery_tuh.mp3?st=DvefVQVxGfNL9YFNg4rvyw&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui227/10Minutes-LeeHyori_449m8_hq.mp3?st=ihf9IcHI-4Y1Zs-VxGUyZA&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui170/EveryDay-Audition_39j85_hq.mp3?st=WI2QmYJhbEMz5XKmqzw4UQ&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio10/Beautiful-Beast_4bqf.mp3?st=7rG42uWboNvYclk7Ap2sVA&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui162/RolyPoly-DangCapNhat_37827_hq.mp3?st=SJip0BXDU0f75N2PN8bD2A&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui154/IntoTheNewWorld-SNSD_357tt_hq.mp3?st=V3ReNN_b-6vcvYJUmuaHXA&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui182/Nobody-WonderGirls_3cu46_hq.mp3?st=JhV4CaaoV7X3e2xSz9dpsQ&e=1621676301",
            "https://aredir.nixcdn.com/YG_Audio1/HaruHaru-BIGBANG-6291516_hq.mp3?st=UyskHwjWW2nJ551FXRaH8g&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui076/Timetolove-T-AraSupernova_e554.mp3?st=jYfvk31OJbbuLPYd1LTbUQ&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio142/DonTNeedYourLove-NCTDREAMHRVY-6001734_hq.mp3?st=WVUs1sKEA8wimPjw8GgZAg&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio139/KeepYourEyesOnMe-KumiraNASON-5961632_hq.mp3?st=-ED6sFYk5vsiMA2StvdKEg&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio138/1SidedLove-KrizKino-5956427_hq.mp3?st=QbFkXo4TNXOYu34NXB-IIw&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio138/WarmWater-JimmyBrown-5953937_hq.mp3?st=umX15ooasrOrumT_mrdhoA&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio38/Beautiful-ParkBoramZico-3626613_hq.mp3?st=JScJq55X4fMUF5QNDXdR8A&e=1621676301",)
        val url2 = listOf(
            "https://aredir.nixcdn.com/Unv_Audio146/Stay-WooHyukJangBabylon-6077031_hq.mp3?st=k9m_ZWTFPmphbxvQnxsEvg&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio42/MyGirl-INFINITEF-3935337_hq.mp3?st=obcRCAPKCTm-Y4rBtVYVNQ&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio134/OurSummer-TOMORROWXTOGETHER-5907749_hq.mp3?st=9RBpepOj46QI41TjZB8BbA&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui991/MagicIsland-TXTTomorrowxTogether-6129047_hq.mp3?st=JkevPHdCC-FzOEQ0LGM20w&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio128/Something-JimmyBrown-5836829_hq.mp3?st=eAWvXW2mpk5oBvZi8UriKA&e=1621676301",
            "http://cdn.skysoundtrack.com/audio/2019/12/25/k6x_Ngon-So-1_GGG_Qp8KDKKddGON.mp3",
            "http://cdn.skysoundtrack.com/youtube/QlkpAExArTc.mp3",
            "https://aredir.nixcdn.com/NhacCuaTui987/BetterThanAnyStarHotelDelLunaOst-RedVelvet-6053124_hq.mp3?st=Fp1a5oXtAEjWe33EQNgDCg&e=1621676301",
            "http://cdn.skysoundtrack.com/youtube/ooToLMyol8g.mp3",
            "http://cdn.skysoundtrack.com/youtube/pcKR0LPwoYs.mp3",
            "http://cdn.skysoundtrack.com/youtube/qdBcC6gd5G4.mp3",
            "http://cdn.skysoundtrack.com/audio/2019/04/11/QC-Gogi-House_Gogi-House_tv3qIQUlFFG0.mp3",
            "http://cdn.skysoundtrack.com/youtube/7gpzx3XpnCY.mp3",
            "https://aredir.nixcdn.com/NhacCuaTui990/BabyOnlyYouTheTaleOfNokduOst-DoyoungNCTUMarkNCTU-6102823_hq.mp3?st=EbsP-hTGadPn6nf-JUxT3A&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui994/SedansoguHowAreUBreadOst-SuhoEXO-6214382_hq.mp3?st=zFqPsGfbV07zO_ATVY6DSw&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio53/HopefullySky-JEONGEUNJIHareem-4425874_hq.mp3?st=zWunebDMkp4VDwNQrOrG4g&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui987/BetterThanAnyStarHotelDelLunaOst-RedVelvet-6053124_hq.mp3?st=Fp1a5oXtAEjWe33EQNgDCg&e=1621676301",
            "http://cdn.skysoundtrack.com/youtube/ooToLMyol8g.mp3",
            "http://cdn.skysoundtrack.com/youtube/pcKR0LPwoYs.mp3",
            "http://cdn.skysoundtrack.com/youtube/qdBcC6gd5G4.mp3"
        )

        val url3 = listOf( "https://aredir.nixcdn.com/NhacCuaTui076/Timetolove-T-AraSupernova_e554.mp3?st=jYfvk31OJbbuLPYd1LTbUQ&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio142/DonTNeedYourLove-NCTDREAMHRVY-6001734_hq.mp3?st=WVUs1sKEA8wimPjw8GgZAg&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio139/KeepYourEyesOnMe-KumiraNASON-5961632_hq.mp3?st=-ED6sFYk5vsiMA2StvdKEg&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio138/1SidedLove-KrizKino-5956427_hq.mp3?st=QbFkXo4TNXOYu34NXB-IIw&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio138/WarmWater-JimmyBrown-5953937_hq.mp3?st=umX15ooasrOrumT_mrdhoA&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio38/Beautiful-ParkBoramZico-3626613_hq.mp3?st=JScJq55X4fMUF5QNDXdR8A&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio11/MyGirl-BTOB-MinHyukLee-PenielDShin_54xc_hq.mp3?st=hWj_-LQJr4L4_1bHRI0KMA&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui991/PoppinStar-TXTTomorrowxTogether-6129049_hq.mp3?st=WnzR6Rkvsrsu8Jwx9fe6HQ&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio151/NoManners-SuperM-6109019_hq.mp3?st=uVHWmOyXXVclPrywVil3jQ&e=1621676301",
            "https://aredir.nixcdn.com/Unv_Audio53/HopefullySky-JEONGEUNJIHareem-4425874_hq.mp3?st=zWunebDMkp4VDwNQrOrG4g&e=1621676301",
            "https://aredir.nixcdn.com/NhacCuaTui987/BetterThanAnyStarHotelDelLunaOst-RedVelvet-6053124_hq.mp3?st=Fp1a5oXtAEjWe33EQNgDCg&e=1621676301",
            "http://cdn.skysoundtrack.com/youtube/ooToLMyol8g.mp3",
            "http://cdn.skysoundtrack.com/youtube/pcKR0LPwoYs.mp3",
            "http://cdn.skysoundtrack.com/youtube/qdBcC6gd5G4.mp3",
            "http://cdn.skysoundtrack.com/audio/2019/04/11/QC-Gogi-House_Gogi-House_tv3qIQUlFFG0.mp3",
            "http://cdn.skysoundtrack.com/youtube/7gpzx3XpnCY.mp3",)

        Log.d("testdownload","Size: ${url1.size+url2.size+url3.size}")
        val coroutineScope = CoroutineScope(Dispatchers.Default)

        val t1 = newSingleThreadContext("aaa")
        val t2 = newSingleThreadContext("bbb")
        val t3 = newSingleThreadContext("ccc")

            val j = coroutineScope.launch {
                val di1= url1.map { url -> DownloadItem().apply {
                    itDownloadUrl = url
                    itStoragePath = folderPath
                    itFileName = getFileNameFromUrl(url)
                } }

                val di2= url2.map { url -> DownloadItem().apply {
                    itDownloadUrl = url
                    itStoragePath = folderPath
                    itFileName = getFileNameFromUrl(url)
                } }

                val di3= url3.map { url -> DownloadItem().apply {
                    itDownloadUrl = url
                    itStoragePath = folderPath
                    itFileName = getFileNameFromUrl(url)
                } }
                launch(t3) { di3.forEach { downloadHelper.addToQueue(it) } }
                delay(3000)
                launch(t1) { di1.forEach { Log.d("aaa",Thread.currentThread().name); downloadHelper.addToQueue(it) } }

                launch(t2) { di2.forEach { downloadHelper.addToQueue(it) } }

                delay(180000)

                launch(t1) { di1.forEach { Log.d("aaa",Thread.currentThread().name); downloadHelper.addToQueue(it) } }
                delay(1000)
                launch(t2) { di2.forEach { downloadHelper.addToQueue(it) } }
                delay(500)
                launch(t3) { di3.forEach { downloadHelper.addToQueue(it) } }
            }
        }
}