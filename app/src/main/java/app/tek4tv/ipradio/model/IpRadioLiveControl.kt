package app.tek4tv.ipradio.model

data class IpRadioLiveControl(
    var id: String,
    var privateKey: String,
    var imes: List<String>,
    var sourceID: String,
    var sourceName: String,
    var destinationID: String,
    var destinationName: String,
    var priority: Item,
    var category:Item,
    var contentType: Item,
    var author: Author,
    var title: String,
    var body: String,
    var startTime: Long,
    var createDate: Long,
    var checkSum: Long,
    var checkSize: Long,
)

data class Item(
    var id: Int,
    var value: String
)