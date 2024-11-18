package com.example.uxchannel_proto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi

// 기기의 부팅완료와 종료이벤트를 수신하는 기능
class BootShutdownReceiver : BroadcastReceiver() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        // 인텐트의 액션에 따라 분기 처리
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // 부팅 완료 > UsageStatsService 시작
                Intent(context, UsageStatsService::class.java).also {
                    // 포그라운드 서비스로 시작
                    context.startForegroundService(it)
                }
            }
            Intent.ACTION_SHUTDOWN -> {
                // 기기가 종료될 때 실행할 코드
            }
        }
    }
}