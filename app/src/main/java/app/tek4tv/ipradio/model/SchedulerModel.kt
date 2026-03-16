package app.tek4tv.ipradio.model

enum class RepeatType {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}

data class SchedulerModel(
    val id: Long,
    val prior: Int,
    val name: String,
    val mode: Int,
    val repeat: Int,
    val weeklyDays: Set<Int> = emptySet(),
    val monthlyDays: Set<Int> = emptySet(),
    val startTime: Long,
    val endTime: Long,
    val mediaList: List<MediaFile>,
    val timeFrame: Set<String> = emptySet()
)

data class TimeFrame(
    val startFrame: Int,
    val endFrame: Int
)
