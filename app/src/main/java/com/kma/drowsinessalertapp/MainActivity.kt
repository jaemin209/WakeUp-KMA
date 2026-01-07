package com.kma.drowsinessalertapp

// ⭐ 필수 Import 목록 (누락 시 모든 Unresolved Reference 발생)

// Wear OS 통신 및 코루틴 관련 import

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


// ⭐ 클래스 상속: 'AppCompatActivity'를 상속해야 합니다.
class MainActivity : AppCompatActivity() {

    private val PATH_VIBRATE = "/vibrate_command"

    // ⭐ onCreate 함수 오버라이드: 'override' 키워드와 함수 시그니처가 정확해야 합니다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // XML 레이아웃 로드
        setContentView(R.layout.activity_main)

        // 1. XML의 버튼을 Kotlin 객체로 가져오기
        val button: Button = findViewById(R.id.sendVibrationButton)

        // 2. 버튼 클릭 시 진동 명령 전송 함수 호출
        button.setOnClickListener {
            sendVibrationCommand()
        }
    }

    // ⭐ 워치로 진동 명령을 전송하는 함수 정의
    private fun sendVibrationCommand() {
        lifecycleScope.launch {
            try {
                val nodeClient = Wearable.getNodeClient(this@MainActivity)
                val messageClient = Wearable.getMessageClient(this@MainActivity)

                val nodes = nodeClient.connectedNodes.await()

                if (nodes.isEmpty()) {
                    Log.e("Mobile", "연결된 Wear OS 기기가 없습니다.")
                    return@launch
                }

                for (node in nodes) {
                    messageClient.sendMessage(
                        node.id,
                        PATH_VIBRATE,
                        ByteArray(0)
                    ).addOnSuccessListener {
                        Log.i("Mobile", "진동 명령 전송 성공: ${node.displayName}")
                    }.addOnFailureListener { e ->
                        Log.e("Mobile", "진동 명령 전송 실패: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("Mobile", "워치 통신 중 오류 발생", e)
            }
        }
    }
}

// ⭐ Compose 관련 함수 (Greeting, GreetingPreview 등)는 모두 제거되었습니다.