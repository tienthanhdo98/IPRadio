package app.tek4tv.ipradio.model.webconsole

data class ClassDUiModel(
    val monitorChannel: String = "CH 2-4",
    val szdLeft: Int = 0,
    val szdRight: Int = 0,
    val muteLeft: Int = 0,
    val muteRight: Int = 0,
    val threshold: Int = 5,
    val speaker1Offset: Int = 25,
    val speaker2Offset: Int = 20,
    val speaker3Offset: Int = 20,
    val speaker4Offset: Int = 20,
)