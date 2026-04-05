//package app.tek4tv.ipradio.repo
//
//import app.tek4tv.ipradio.model.local.log.MgwLogDao
//import app.tek4tv.ipradio.model.local.log.MgwLogEntity
//import kotlinx.coroutines.sync.Mutex
//import kotlinx.coroutines.sync.withLock
//import javax.inject.Inject
//import javax.inject.Singleton
//
//@Singleton
//class LogRepository @Inject constructor(
//    private val dao: MgwLogDao,
//    private val mqttClient: MqttPublisher,
//    private val deviceId: String,
//    private val gson: Gson = Gson()
//) {
//    private val flushMutex = Mutex()
//
//    suspend fun addLog(
//        logId: Long,
//        time: Long,
//        type: Int,
//        detail: String
//    ) {
//        dao.insert(
//            MgwLogEntity(
//                logId = logId,
//                time = time,
//                type = type,
//                detail = detail
//            )
//        )
//    }
//
//    suspend fun flushLogs() {
//        flushMutex.withLock {
//            while (true) {
//                val batch = dao.getPendingLogs(limit = 10)
//                if (batch.isEmpty()) break
//
//                val ids = batch.map { it.localId }
//                dao.markSending(ids)
//
//                val success = try {
//                    val payloadLogs = batch.map {
//                        MgwLogPayload(
//                            id = it.logId,
//                            time = it.time,
//                            type = it.type,
//                            detail = it.detail
//                        )
//                    }
//
//                    val senml = listOf(
//                        SenmlPacket(
//                            n = "$deviceId:s:5",
//                            t = System.currentTimeMillis() / 1000,
//                            vs = gson.toJson(payloadLogs)
//                        )
//                    )
//
//                    mqttClient.publish(
//                        topic = "channels/{channel_id}/messages/mirav3/s",
//                        payload = gson.toJson(senml)
//                    )
//                } catch (e: Exception) {
//                    false
//                }
//
//                if (success) {
//                    dao.markSent(ids)
//                } else {
//                    dao.unmarkSending(ids)
//                    break
//                }
//            }
//        }
//    }
//}