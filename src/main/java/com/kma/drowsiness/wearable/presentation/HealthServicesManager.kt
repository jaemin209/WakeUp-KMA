package com.kma.drowsiness.wearable.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesClient
import androidx.health.services.client.data.*
import androidx.health.services.client.ExerciseUpdateCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sqrt

class HealthServicesManager(private val context: Context) : SensorEventListener {

    // --- [설정값] ---
    // 테스트를 위해 10초(10000)로 설정. 나중에 3분(180000)으로 변경하세요.
    private val DROWSINESS_TIME_LIMIT = 10000L
    private val MOVEMENT_THRESHOLD = 0.3f
    // ----------------

    private val healthClient: HealthServicesClient = HealthServices.getClient(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _heartRate = MutableStateFlow(0f)
    val heartRate: StateFlow<Float> = _heartRate.asStateFlow()

    private val _isDrowsy = MutableStateFlow(false)
    val isDrowsy: StateFlow<Boolean> = _isDrowsy.asStateFlow()

    private val _motionIndex = MutableStateFlow(0f)
    val motionIndex: StateFlow<Float> = _motionIndex.asStateFlow()

    private var lastMovementTime: Long = System.currentTimeMillis()
    private var lastMagnitude: Float = 9.8f

    private fun isPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun startMonitoring() {
        Log.e("HSM_DEBUG", "1. startMonitoring 진입 성공!")

        if (!isPermissionsGranted()) {
            Log.e("HSM_DEBUG", "권한 부족으로 리턴")
            return
        }

        // 시간 초기화
        lastMovementTime = System.currentTimeMillis()
        _isDrowsy.value = false

        // ⭐️ 순서 변경: 가속도 센서를 먼저 켭니다 (얘는 에뮬레이터에서도 무조건 됨)
        startAccelerometer()
        Log.e("HSM_DEBUG", "2. 가속도 센서 켜짐 (움직임 감지 시작)")

        // ⭐️ 심박수 센서 시작 (여기서 멈춰도 가속도 센서는 돌아갑니다)
        startHeartRate()
    }

    suspend fun stopMonitoring() {
        try {
            // 타임아웃 적용 (멈춤 방지)
            withTimeoutOrNull(2000) {
                healthClient.exerciseClient.endExerciseAsync().await()
            }
            sensorManager.unregisterListener(this)
            _isDrowsy.value = false
            Log.i("HSM", "모니터링 중지됨")
        } catch (e: Exception) {
            Log.e("HSM", "중지 실패", e)
        }
    }

    private suspend fun startHeartRate() {
        Log.e("HSM_DEBUG", "3. 심박수 설정 시작")
        val config = ExerciseConfig.builder(ExerciseType.WALKING)
            .setDataTypes(setOf(DataType.HEART_RATE_BPM))
            .setIsAutoPauseAndResumeEnabled(false)
            .build()

        val callback = object : ExerciseUpdateCallback {
            override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                val latestHr = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
                    .lastOrNull()?.value?.toFloat()
                if (latestHr != null && latestHr > 0) {
                    _heartRate.value = latestHr
                    Log.d("HSM", "심박수 수신: $latestHr")
                }
            }
            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
            override fun onRegistered() { Log.d("HSM", "심박수 리스너 등록됨") }
            override fun onRegistrationFailed(throwable: Throwable) {}
            override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
        }

        // 기존 운동 종료 (타임아웃 적용: 3초 안에 응답 없으면 무시하고 넘어감)
        Log.e("HSM_DEBUG", "4. 기존 운동 종료 시도...")
        try {
            withTimeoutOrNull(3000) {
                healthClient.exerciseClient.endExerciseAsync().await()
            }
            Log.e("HSM_DEBUG", "5. 기존 운동 종료 완료 (또는 없음)")
        } catch (e: Exception) {
            Log.e("HSM_DEBUG", "기존 운동 종료 에러 (무시): ${e.message}")
        }

        // 새 운동 시작 (타임아웃 적용)
        Log.e("HSM_DEBUG", "6. 새 운동 시작 요청...")
        try {
            withTimeoutOrNull(5000) {
                healthClient.exerciseClient.startExerciseAsync(config).await()
            }
            // 콜백은 await 필요 없음
            healthClient.exerciseClient.setUpdateCallback(callback)
            Log.e("HSM_DEBUG", "7. 심박수 모니터링 시작 성공!")
        } catch (e: Exception) {
            Log.e("HSM_DEBUG", "심박수 시작 실패 (에뮬레이터 문제 가능성): ${e.message}")
        }
    }

    private fun startAccelerometer() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val currentMagnitude = sqrt(x*x + y*y + z*z)
            _motionIndex.value = currentMagnitude // UI 표시용

            val delta = abs(currentMagnitude - lastMagnitude)

            if (delta > MOVEMENT_THRESHOLD) {
                lastMovementTime = System.currentTimeMillis()
                if (_isDrowsy.value) {
                    _isDrowsy.value = false
                    Log.i("HSM", "움직임 감지! 졸음 해제")
                }
            }

            val timeDiff = System.currentTimeMillis() - lastMovementTime
            if (timeDiff > DROWSINESS_TIME_LIMIT) {
                if (!_isDrowsy.value) {
                    _isDrowsy.value = true
                    Log.w("HSM", "⚠️ 졸음 경고 발령! (시간 초과)")
                }
            }
            lastMagnitude = currentMagnitude
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}