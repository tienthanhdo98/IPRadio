package app.tek4tv.ipradio.model

import java.util.*

data class FbMediaReport(
    var name : String = "",
    var privateKey : Int,
    var start : Long = Date().time
)
