package com.aion.hrtest

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.concurrent.futures.await
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.BatchingMode
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.SampleDataPoint
import androidx.health.services.client.unregisterMeasureCallback
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import java.time.Instant

private const val TAG = "AionHr"

/** 수업용 세션은 운동이 아니지만, 화면이 꺼져도 연속 측정을 주는 건 운동 세션뿐이라 일반 운동 유형을 쓴다 */
private val SESSION_TYPE = ExerciseType.WORKOUT

/** 심박수 한 건 */
data class HrSample(
    val bpm: Int,
    /** 잰 시각 (기기 시계 기준 밀리초) */
    val at: Long
)

sealed class HrMessage {
    data class Data(val samples: List<HrSample>) : HrMessage()
    data class AvailabilityChanged(val available: Boolean) : HrMessage()
}

class HealthServicesManager(context: Context) {

    private val client = HealthServices.getClient(context)
    private val measureClient = client.measureClient
    private val exerciseClient = client.exerciseClient

    suspend fun hasHeartRateCapability(): Boolean {
        val capabilities = measureClient.getCapabilitiesAsync().await()
        return DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure
    }

    /** 운동 세션으로 심박을 받을 수 있고, 화면 꺼짐 중 5초 배치를 지원하는지 */
    suspend fun exerciseSupport(): Pair<Boolean, Boolean> {
        val caps = exerciseClient.getCapabilitiesAsync().await()
        val hr = caps.typeToCapabilities[SESSION_TYPE]
            ?.supportedDataTypes?.contains(DataType.HEART_RATE_BPM) == true
        val fiveSec = BatchingMode.HEART_RATE_5_SECONDS in caps.supportedBatchingModeOverrides
        return hr to fiveSec
    }

    /**
     * 화면이 꺼져도 이어지는 심박 측정 (포그라운드 서비스에서 사용).
     * MeasureClient는 화면이 꺼지면 60초씩 묶어서 주기 때문에, 운동 세션 + 5초 배치를 쓴다.
     */
    fun exerciseHeartRateFlow(fiveSecondBatching: Boolean): Flow<HrMessage> = callbackFlow {
        val callback = object : ExerciseUpdateCallback {
            override fun onRegistered() = Unit

            override fun onRegistrationFailed(throwable: Throwable) {
                Log.w(TAG, "운동 세션 콜백 등록 실패: ${throwable.message}")
                close(throwable)
            }

            override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                val points = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
                Log.d(TAG, "onDataReceived: ${points.size}개, t=${System.currentTimeMillis()}")
                val samples = points.map { it.toSample() }
                if (samples.isNotEmpty()) trySendBlocking(HrMessage.Data(samples))
            }

            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

            override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
                if (availability is DataTypeAvailability) {
                    Log.d(TAG, "availability=$availability")
                    trySendBlocking(HrMessage.AvailabilityChanged(availability == DataTypeAvailability.AVAILABLE))
                }
            }
        }

        exerciseClient.setUpdateCallback(callback)
        val config = ExerciseConfig.builder(SESSION_TYPE)
            .setDataTypes(setOf(DataType.HEART_RATE_BPM))
            .setIsAutoPauseAndResumeEnabled(false)
            .setIsGpsEnabled(false)
            .apply { if (fiveSecondBatching) setBatchingModeOverrides(setOf(BatchingMode.HEART_RATE_5_SECONDS)) }
            .build()
        exerciseClient.startExerciseAsync(config).await()
        Log.d(TAG, "운동 세션 시작 (화면 꺼짐 5초 배치=$fiveSecondBatching)")

        awaitClose {
            Log.d(TAG, "운동 세션 종료")
            runCatching {
                runBlocking {
                    exerciseClient.endExerciseAsync().await()
                    exerciseClient.clearUpdateCallbackAsync(callback).await()
                }
            }
        }
    }

    /** 화면이 켜져 있을 때만 믿을 수 있는 측정. 운동 세션을 못 쓰는 기기에서 대신 쓴다 */
    fun heartRateFlow(): Flow<HrMessage> = callbackFlow {
        val callback = object : MeasureCallback {

            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability
            ) {
                if (availability is DataTypeAvailability) {
                    val ok = availability == DataTypeAvailability.AVAILABLE
                    Log.d(TAG, "availability=$availability")
                    trySendBlocking(HrMessage.AvailabilityChanged(ok))
                }
            }

            override fun onDataReceived(data: DataPointContainer) {
                val points = data.getData(DataType.HEART_RATE_BPM)

                // 콜백이 몇 초마다 오는지, 한 번에 몇 개씩 오는지 확인
                Log.d(TAG, "onDataReceived: ${points.size}개, t=${System.currentTimeMillis()}")

                val samples = points.map { it.toSample() }
                if (samples.isNotEmpty()) {
                    trySendBlocking(HrMessage.Data(samples))
                }
            }
        }

        Log.d(TAG, "콜백 등록")
        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)

        awaitClose {
            Log.d(TAG, "콜백 해제")
            runCatching {
                runBlocking {
                    measureClient.unregisterMeasureCallback(
                        DataType.HEART_RATE_BPM, callback
                    )
                }
            }
        }
    }
}

/** 묶여서 와도 각 값의 실제 측정 시각을 쓴다 (부팅 후 경과 시간 → 벽시계 시각) */
private fun SampleDataPoint<Double>.toSample(): HrSample {
    val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
    return HrSample(bpm = value.toInt(), at = getTimeInstant(bootInstant).toEpochMilli())
}
