package com.example.uxchannel_proto

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import android.Manifest
import android.content.Context.USAGE_STATS_SERVICE
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat

class UsageStatsService : Service() {
    // 변수 선언
    private var updateRunnable: Runnable? = null
    private var collectionStartTime: Long = 0
    private var isCharging: Boolean = false
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private lateinit var realtimeDatabase: FirebaseDatabase
    private lateinit var deviceId: String
    private var handler = Handler(Looper.getMainLooper())
    private var isCollectingStats: Boolean = true
    private val notificationChannelId = "UsageStatsServiceChannel"
    private lateinit var audioManager: AudioManager
    private val dataTransferReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            collectAndSendUsageStats()
        }
    }

    // 배터리 충전 상태 변경 감지를 위한 BroadcastReceiver
    private val chargingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isCharging = when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> true
                Intent.ACTION_POWER_DISCONNECTED -> false
                else -> isCharging
            }
            // 충전 상태 변경 이벤트 발송
            val chargingIntent = Intent("com.example.app.CHARGING_STATE_CHANGED")
            chargingIntent.putExtra("isCharging", isCharging)
            sendBroadcast(chargingIntent)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null // 바인드 서비스가 아니므로 널 반환
    }
    // 서비스 상태를 반환하는 함수
    private fun broadcastServiceStatus(isRunning: Boolean) {
        val intent = Intent("com.example.app.SERVICE_STATUS")
        intent.putExtra("isServiceRunning", isRunning)
        sendBroadcast(intent)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        isCollectingStats = true

        realtimeDatabase = FirebaseDatabase.getInstance() // 파이어베이스 인스턴스 초기화
        broadcastServiceStatus(true) // 서비스가 실행중임을 알림
        collectionStartTime = System.currentTimeMillis()
        deviceId = generateUniqueDeviceId() // 고유 디바이스 ID생성
        createNotificationChannel() // 알림 채널 생성
        startForegroundService() // 포그라운드 서비스 시작

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        handler = Handler(Looper.getMainLooper())
        scheduleUsageStatsUpdates()
        val dataTransferFilter = IntentFilter("com.example.app.TRANSFER_DATA")
        val chargingFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val notificationPolicyFilter = IntentFilter(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
        // RECEIVER_NOT_EXPORTED 플래그를 사용하기 위한 API 수준의 조건부 확인
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0 // API 33 미만인 경우 플래그 매개변수의 기본값 0
        }
        // BroadcastReceiver 등록
        registerReceiver(dataTransferReceiver, dataTransferFilter, receiverFlags)
        registerReceiver(chargingStateReceiver, chargingFilter, receiverFlags)
        registerReceiver(notificationPolicyReceiver, notificationPolicyFilter, receiverFlags)
    }

    // 수집 진행 상황을 발송하는 함수
    private fun sendCollectionProgress() {
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - collectionStartTime // 경과 시간 계산
        val progressIntent = Intent("com.example.app.COLLECTION_PROGRESS")
        progressIntent.putExtra("elapsedTime", elapsedTime) // 경과 시간 데이터를 인텐트에 추가
        sendBroadcast(progressIntent)
    }

    // 알림 채널 생성
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                notificationChannelId,
                "Usage Stats Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel) // 알림 채널 등록
        }
    }

    // 알림 정책 변경을 감지
    private val notificationPolicyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED) {
                // 알림 접근 권한이 변경되었을 때 서비스 재시작
                restartForegroundService()
            }
        }
    }

    // 포그라운드 서비스 재시작
    private fun restartForegroundService() {
        stopSelf() // 현재 서비스 중지
        val serviceIntent = Intent(this, UsageStatsService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    // 포그라운드 서비스를 시작하는 함수
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification: Notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Usage Stats Service")
            .setContentText("Collecting and analyzing usage stats.")
            .setSmallIcon(R.mipmap.ic_launcher) // Use a relevant icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Use a different unique notification ID (e.g., 2) for this service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(2, notification)
        }
        Log.d("UsageStatsService", "Foreground service started for UsageStatsService")
    }

    // 고유 디바이스 ID 생성
    private fun generateUniqueDeviceId(): String {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        var id = sharedPreferences.getString("deviceId", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("deviceId", id).apply()
        }
        return id
    }

    // 알림이 활성화되어 있는지 확인
    private fun areNotificationsEnabled(): Boolean {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(notificationChannelId)
            if (channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return NotificationManagerCompat.from(this).areNotificationsEnabled() // 알림 활성화 상태 반환
    }

    // 사용 통계 업데이트 스케줄링
    private fun scheduleUsageStatsUpdates() {
        updateRunnable = Runnable {
            if (isCollectingStats) {
                collectAndSendUsageStats() // 사용 통계 수집 및 전송
                sendCollectionProgress() // 수집 진행 상황을 알림

                if (!areNotificationsEnabled()) {
                    restartForegroundService() // 알림이 비활성화 되어 있으면 포그라운드 서비스 재시작
                }
            }
            handler.postDelayed(updateRunnable as Runnable, 1000 * 60 * 30) // 30분 마다 반복
        }
        handler.postDelayed(updateRunnable as Runnable, 1000 * 60 * 30) // 초기 실행 예약
    }

    // 마지막 데이터 전송 타임스탬프를 가져옴
    private fun getLastSentTimestamp(): Long {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getLong("lastSentTimestamp", 0)
    }
    // 마지막 데이터 전송 타임스탬프를 업데이트
    private fun updateLastSentTimestamp(timestamp: Long) {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putLong("lastSentTimestamp", timestamp)
            apply()
        }
    }

    private fun getRingerMode(): String {
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "Normal"
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Unknown"
        }
    }

    // 사용 통계 및 환경 데이터 수집 및 전송
    private fun collectAndSendUsageStats() {
        // 사용 패턴과 환경데이터를 수집하여 파이어베이스에 전송
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val anonymizeData = sharedPreferences.getBoolean("anonymizeData", false)
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val lastSentTimestamp = getLastSentTimestamp()
        val ringerMode = getRingerMode()
        val startTime =
            if (lastSentTimestamp == 0L) endTime - 1000 * 60 * 60 * 24 else lastSentTimestamp

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val eventTypeMap = mapOf(
            //1 to "ACTIVITY_RESUMED",
            //11 to "STANDBY_BUCKET_CHANGED",
            //21 to "CONTINUING_FOREGROUND_SERVICE",
            //2 to "ACTIVITY_PAUSED",
            12 to "NOTIFICATION_INTERRUPTION", // 수집
            //22 to "ROLLOVER_FOREGROUND_SERVICE",
            //3 to "END_OF_DAY",
            //13 to "SLICE_PINNED_PRIV",
            //23 to "ACTIVITY_STOPPED",
            //4 to "CONTINUE_PREVIOUS_DAY",
            //14 to "SLICE_PINNED",
            //24 to "ACTIVITY_DESTROYED",
            //5 to "CONFIGURATION_CHANGE",
            15 to "SCREEN_INTERACTIVE", // 수집
            //25 to "FLUSH_TO_DISK",
            //6 to "SYSTEM_INTERACTION",
            16 to "SCREEN_NON_INTERACTIVE", // 수집
            26 to "DEVICE_SHUTDOWN", // 수집
            7 to "USER_INTERACTION", // 수집
            //17 to "KEYGUARD_SHOWN",
            27 to "DEVICE_STARTUP", // 수집
            //8 to "SHORTCUT_INVOCATION",
            //18 to "KEYGUARD_HIDDEN",
            //28 to "USER_UNLOCKED",
            //9 to "CHOOSER_ACTION",
            //19 to "FOREGROUND_SERVICE_START",
            //29 to "LOCUS_ID_SET",
            10 to "NOTIFICATION_SEEN", // 수집
            //20 to "FOREGROUND_SERVICE_STOP",
            //30 to "",
            //31 to "APP_COMPONENT_USED",
            )
        val allowedEventTypes = eventTypeMap.keys
        var lastEventTime = 0L

        while (usageEvents.hasNextEvent()) {
            val event = UsageEvents.Event()
            usageEvents.getNextEvent(event)
            if (event.timeStamp > lastSentTimestamp && event.eventType in allowedEventTypes) {
                val formattedTime = formatter.format(Date(event.timeStamp))
                lastEventTime = event.timeStamp
                val packageName = if (anonymizeData) "Anonymous" else event.packageName
                val className = if (anonymizeData) "Anonymous" else event.className
                val eventTypeString = eventTypeMap[event.eventType] ?: "Unknown"
                val eventDetails = mutableMapOf<String, Any>(
                    "deviceId" to deviceId,
                    "package_name" to packageName,
                    "timestamp" to formattedTime,
                    "eventType" to eventTypeString,
                    "className" to className,
                    "ringerMode" to ringerMode,
                    "isCharging" to isCharging
                )
                realtimeDatabase.reference.child("usage_events").push().setValue(eventDetails)
                    .addOnSuccessListener {
                        Log.d("UsageStatsService", "Usage event sent successfully.")
                    }
                    .addOnFailureListener { exception ->
                        Log.e(
                            "UsageStatsService",
                            "Error sending usage event: ${exception.message}"
                        )
                    }
            }
        }
        if (lastEventTime > 0) {
            updateLastSentTimestamp(lastEventTime)
        }
    }

    override fun onDestroy() {
        // 서비스 종료시 지원 해제 및 정리 작업
        super.onDestroy()
        broadcastServiceStatus(false)
        unregisterReceiver(dataTransferReceiver)
        unregisterReceiver(chargingStateReceiver)
        unregisterReceiver(notificationPolicyReceiver)
        handler.removeCallbacksAndMessages(null)
    }
}
