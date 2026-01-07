package com.kma.drowsiness.wearable.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.kma.drowsiness.wearable.presentation.theme.DrowsinessAlertAppTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

//감시자
class RealSensorManager(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val heartRateSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val scope = CoroutineScope(Dispatchers.Default)
    private var timerJob: Job? = null

    val heartRate = MutableStateFlow(0f)
    val motionIndex = MutableStateFlow(0f)
    val isDrowsy = MutableStateFlow(false)

    private var lastMovementTime: Long = System.currentTimeMillis()
    private var lastMag

    nitude: Float = 9.8f


    private val TIME_LIMIT = 3 * 60 * 1000L
    //민감도 설정하는 부분
    private val MOVEMENT_THRESHOLD = 0.5f

    fun start() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        if (heartRateSensor != null) {
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        lastMovementTime = System.currentTimeMillis()
        isDrowsy.value = false
        startWatchdogTimer()
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        timerJob?.cancel()
    }

    private fun startWatchdogTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val elapsed = System.currentTimeMillis() - lastMovementTime

                if (elapsed > TIME_LIMIT) {
                    if (!isDrowsy.value) {
                        isDrowsy.value = true
                    }
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
            heartRate.value = event.values[0]
        }
        else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val currentMagnitude = sqrt(x*x + y*y + z*z)
            motionIndex.value = currentMagnitude

            val delta = abs(currentMagnitude - lastMagnitude)

            if (delta > MOVEMENT_THRESHOLD) {
                lastMovementTime = System.currentTimeMillis()
                if (isDrowsy.value) isDrowsy.value = false
            }
            lastMagnitude = currentMagnitude
        }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

// === [2. 메인 액티비티] ===
class MainActivity : ComponentActivity() {
    private lateinit var sensorManager: RealSensorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager = RealSensorManager(this)

        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            WearApp(this, sensorManager)
        }
    }

    fun triggerVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.stop()
    }
}

@Composable
fun WearApp(activity: MainActivity, manager: RealSensorManager) {
    DrowsinessAlertAppTheme {
        val heartRate by manager.heartRate.collectAsState()
        val motion by manager.motionIndex.collectAsState()
        val isDrowsy by manager.isDrowsy.collectAsState()

        var bgColor by remember { mutableStateOf(Color.Black) }

        LaunchedEffect(isDrowsy) {
            if (isDrowsy) {
                while (true) {
                    bgColor = Color.Red
                    activity.triggerVibration()
                    delay(500)
                    bgColor = Color.Black
                    delay(500)
                }
            } else {
                bgColor = Color.Black
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            var hasPerms by remember { mutableStateOf(false) }

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                if (perms.values.all { it }) {
                    hasPerms = true
                    manager.start()
                }
            }

            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {
                    hasPerms = true
                    manager.start()
                } else {
                    launcher.launch(arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.VIBRATE))
                }
            }

            if (hasPerms) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isDrowsy) {
                        // 졸음 감지
                        Text("CRITICAL ALERT", fontSize = 12.sp, color = Color.Yellow, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("졸음 징후 감지됨", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("정신 차리세요", fontSize = 14.sp, color = Color.White)
                    } else {
                        // 정상
                        Text("DRIVER GUARD", fontSize = 10.sp, color = Color.LightGray)
                        Text("System Active", fontSize = 8.sp, color = Color.Gray)

                        Spacer(modifier = Modifier.height(15.dp))

                        // 심박수 표시
                        Text("${heartRate.toInt()}", fontSize = 40.sp, color = MaterialTheme.colors.primary, fontWeight = FontWeight.Bold)
                        Text("BPM (Real-time)", fontSize = 10.sp, color = MaterialTheme.colors.primary)

                        Spacer(modifier = Modifier.height(15.dp))

                        //
                        Text("생체 신호 분석 중...", fontSize = 10.sp, color = Color.Green)
                        if (motion > 0) {
                            Text("모션 센서: 정상 작동", fontSize = 10.sp, color = Color.Gray)
                        } else {
                            Text("모션 센서: 대기 중", fontSize = 10.sp, color = Color.DarkGray)
                        }
                    }
                }
            } else {
                Button(onClick = { launcher.launch(arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION)) }) {
                    Text("시스템 권한 승인")
                }
            }
        }
    }
}