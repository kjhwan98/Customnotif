package com.example.uxchannel_proto

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.*
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.stopForeground
import androidx.core.graphics.drawable.IconCompat
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.uxchannel_proto.NotificationListener.Companion.receivedNotificationApps


class NotificationListener : NotificationListenerService() {
    companion object {
        private const val KEY_FEATURE_ENABLED = "featureEnabled"
        val receivedNotificationApps = mutableSetOf<String>()
    }
   // 각 알림의 데이터를 저장
    private lateinit var deviceId: String // 기기의 고유 ID
    private val notificationChannelId = "NotiServiceChannel" // 알림 채널 ID
    private lateinit var notificationManager: NotificationManager // 알림 관리자
    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper()) // 핸들러 정의
    private var screenOnTime: Long = 0
    private var screenOffTime: Long = 0
    private lateinit var screenOnOffReceiver: BroadcastReceiver
    private var screenOnFlag = false

    private val highImportanceApps = mutableSetOf<String>() // 중요도 상 앱 목록
    private val mediumImportanceApps = mutableSetOf<String>() // 중요도 중 앱 목록
    private val lowImportanceApps = mutableSetOf<String>() // 중요도 하 앱 목록
    private val highImportanceTexts = mutableSetOf<String>()
    private val mediumImportanceTexts = mutableSetOf<String>()
    private val lowImportanceTexts = mutableSetOf<String>()
    private val notificationTitles = mutableMapOf<String, String>() // 패키지와 제목을 저장하는 맵
    private val pendingMediumNotifications = mutableMapOf<String, StatusBarNotification>() // 중요도 중 알림 대기 목록
    private val pendingLowNotifications = mutableMapOf<String, StatusBarNotification>() // 중요도 하 알림 대기 목록

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        try {
            deviceId = generateUniqueDeviceId() // 기기 ID 생성
            Log.d("NotificationListener", "onCreate called")
            loadNotificationTitlesFromSharedPreferences()
            loadReceivedNotificationAppsFromSharedPreferences()


            val importanceUpdateFilter = IntentFilter().apply {
                addAction("com.example.APP_IMPORTANCE_UPDATED")
                addAction("com.example.KEYWORDS_UPDATED") // 추가하여 키워드 업데이트를 감지
            }

            // LocalBroadcastManager를 사용하여 리시버 등록
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(appImportanceUpdateReceiver, importanceUpdateFilter)
            loadImportanceLists() // 중요도 리스트 초기화
            loadImportanceTexts() // 중요도별 텍스트 로드

            val lowImportanceFilter = IntentFilter("com.example.REQUEST_LOW_IMPORTANCE_NOTIFICATIONS")
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(lowImportanceReceiver, lowImportanceFilter)
            Log.d("NotificationListener", "LocalBroadcastManager for low importance notifications registered")

            registerScreenOnOffReceiver()
            createNotificationChannel() // 알림 채널 생성
            startForegroundService() // 포그라운드 서비스 시작
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager // 알림 서비스 접근
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        } catch (e: Exception) {
            Log.e("NotificationListener", "Error during onCreate: ${e.message}")
        }
    }

    private val appImportanceUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.APP_IMPORTANCE_UPDATED" -> {
                    Log.d("NotificationListener", "Received importance update broadcast for apps")
                    loadImportanceLists() // 앱 중요도 업데이트
                }
                "com.example.KEYWORDS_UPDATED" -> {
                    Log.d("NotificationListener", "Received keyword update broadcast")
                    loadImportanceTexts() // 키워드 중요도 업데이트
                }
            }
            Log.d("NotificationListener", "Importance lists and texts reloaded.")
        }
    }

    private val lowImportanceReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.REQUEST_LOW_IMPORTANCE_NOTIFICATIONS") {
                Log.d("NotificationListener", "Received local broadcast for low importance notifications")
                sendRequestedLowImportanceNotifications()  // 대기 중인 중요도 낮은 알림 전송
            }
        }
    }

    private fun loadImportanceLists() {
        val sharedPreferences = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val highSet = sharedPreferences.getStringSet("highImportanceApps", null)
        val mediumSet = sharedPreferences.getStringSet("mediumImportanceApps", null)
        val lowSet = sharedPreferences.getStringSet("lowImportanceApps", null)

        highImportanceApps.clear()
        mediumImportanceApps.clear()
        lowImportanceApps.clear()

        if (highSet != null) {
            highImportanceApps.addAll(highSet.map { it.split(":")[1] })
        }
        if (mediumSet != null) {
            mediumImportanceApps.addAll(mediumSet.map { it.split(":")[1] })
        }
        if (lowSet != null) {
            lowImportanceApps.addAll(lowSet.map { it.split(":")[1] })
        }

        Log.d("NotificationListener", "Loaded High Importance Apps: $highImportanceApps")
        Log.d("NotificationListener", "Loaded Medium Importance Apps: $mediumImportanceApps")
        Log.d("NotificationListener", "Loaded Low Importance Apps: $lowImportanceApps")
    }

    private fun registerScreenOnOffReceiver() {
        screenOnOffReceiver = object : BroadcastReceiver() {
            @RequiresApi(Build.VERSION_CODES.P)
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        screenOnTime = System.currentTimeMillis()
                        handleScreenOn()
                        Log.d("NotificationListener", "Screen ON")

                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOffTime = System.currentTimeMillis()
                        handleScreenOff()
                        Log.d("NotificationListener", "Screen OFF")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenOnOffReceiver, filter)
        Log.d("NotificationListener", "ScreenOnOffReceiver registered")
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channelName = "Notification Stats Service Channel"
        val importance = NotificationManager.IMPORTANCE_MIN
        val channel = NotificationChannel(notificationChannelId, channelName, importance)
        channel.description = "Collecting notification stats"

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel) // 채널 생성
        Log.d("NotificationListener", "Notification channel created")
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification: Notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Notification Listener Service")
            .setContentText("Monitoring and managing notifications.")
            .setSmallIcon(R.mipmap.ic_launcher) // Use an appropriate icon for clarity
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Use a unique notification ID (e.g., 1) to avoid conflicts with other services
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
        Log.d("NotificationListener", "Foreground service started for NotificationListener")
    }

    private fun generateUniqueDeviceId(): String { // 기기의 고유 ID 생성
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        var id = sharedPreferences.getString("deviceId", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("deviceId", id).apply()
        }
        return id
    }

    //타임 스탬프 문자열로 반환26
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault() // 서버의 시간대 설정이 필요하면 이 부분을 조정
        return sdf.format(timestamp)
    }

    private fun anonymizeText(text: String): String {
        // 텍스트 길이가 9자 미만일 경우, 텍스트 전체를 반환
        if (text.length < 9) {
            return text
        }
        // 처음 4자와 마지막 4자는 유지하고, 나머지는 별표(*)로 처리
        val prefix = text.substring(0, 4)
        val suffix = text.substring(text.length - 4)
        val masked = "*".repeat(text.length - 8)
        return "$prefix$masked$suffix"
    }

    private fun loadImportanceTexts() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // Clear all existing keywords to prevent residual data
        highImportanceTexts.clear()
        mediumImportanceTexts.clear()
        lowImportanceTexts.clear()

        highImportanceTexts.addAll(sharedPreferences.getStringSet("highImportanceTexts", emptySet()) ?: emptySet())
        mediumImportanceTexts.addAll(sharedPreferences.getStringSet("mediumImportanceTexts", emptySet()) ?: emptySet())
        lowImportanceTexts.addAll(sharedPreferences.getStringSet("lowImportanceTexts", emptySet()) ?: emptySet())

        Log.d("NotificationListener", "Keywords reloaded - High: $highImportanceTexts, Medium: $mediumImportanceTexts, Low: $lowImportanceTexts")
    }

    private fun saveNotificationTitlesToSharedPreferences() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        // Convert the notificationTitles map to a single string format for storage
        val titleMapString = notificationTitles.entries.joinToString(";") { "${it.key}:${it.value}" }
        editor.putString("notificationTitles", titleMapString)
        editor.apply()

        Log.d("NotificationListener", "Notification titles saved to SharedPreferences.")
    }

    private fun loadNotificationTitlesFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val titleMapString = sharedPreferences.getString("notificationTitles", "")

        // Convert back to map
        notificationTitles.clear()
        titleMapString?.split(";")?.forEach {
            val (packageName, title) = it.split(":")
            notificationTitles[packageName] = title
        }

        Log.d("NotificationListener", "Notification titles loaded from SharedPreferences.")
    }

    @SuppressLint("NewApi")
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "No Title"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "No Text"
        val packageName = sbn.packageName
        val postTime = formatDate(sbn.postTime)
        val key = sbn.key
        val notificationId = sbn.id

        sendDataToFirebase(sbn.id, packageName, title, text, postTime)
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isFeatureEnabled = sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)

        val isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (isGroupSummary) {
            Log.d("NotificationListener", "Group summary notification ignored: Key='$key', Package='$packageName'")
            return // 그룹 요약 알림은 저장하지 않음
        }

        notificationTitles[packageName] = title
        saveNotificationTitlesToSharedPreferences()
        if (receivedNotificationApps.add(packageName)) {
            saveReceivedNotificationAppsToSharedPreferences()
            Log.d("NotificationListener", "New app added and saved: $packageName")
        }

        Log.d("NotificationListener", "Notification posted: Title='$title', Text='$text', Package='$packageName', FeatureEnabled='$isFeatureEnabled'")
        if (title == "No Title" && text == "No Text") {
            Log.d("NotificationListener", "Ignoring placeholder notification: Title='$title', Text='$text', Package='$packageName'")
            return
        }
        // 중요도 리스트 상태를 로그로 출력
        Log.d("NotificationListener", "Loaded High Importance Keywords: $highImportanceTexts")
        Log.d("NotificationListener", "Loaded Medium Importance Keywords: $mediumImportanceTexts")
        Log.d("NotificationListener", "Loaded Low Importance Keywords: $lowImportanceTexts")
        Log.d("NotificationListener", "Loaded High Importance Apps: $highImportanceApps")
        Log.d("NotificationListener", "Loaded Medium Importance Apps: $mediumImportanceApps")
        Log.d("NotificationListener", "Loaded Low Importance Apps: $lowImportanceApps")

        if (isFeatureEnabled && notificationId != 0) {
            when {
                highImportanceTexts.any { title.contains(it, ignoreCase = true) } -> {
                    Log.d("NotificationListener", "High importance keyword detected in title: '$title'. Allowing notification immediately.")
                    super.onNotificationPosted(sbn)
                    return
                }
                mediumImportanceTexts.any { title.contains(it, ignoreCase = true) } && packageName != "com.example.uxchannel_proto" -> {
                    if (!screenOnFlag) {
                        Log.d("NotificationListener", "Medium importance keyword detected, notification canceled: '$title'")
                        cancelNotification(key)
                        pendingMediumNotifications[key] = sbn
                    } else {
                        Log.d("NotificationListener", "Screen is ON, allowing medium importance notification: '$title'")
                        super.onNotificationPosted(sbn)
                    }
                    return
                }
                lowImportanceTexts.any { title.contains(it, ignoreCase = true) } && packageName != "com.example.uxchannel_proto" -> {
                    Log.d("NotificationListener", "Low importance keyword detected, notification canceled: '$title'")
                    cancelNotification(key)
                    pendingLowNotifications[key] = sbn
                    sendLowImportanceNotificationCountBroadcast(pendingLowNotifications.size)
                    return
                }
            }
            when {
                highImportanceTexts.any { text.contains(it, ignoreCase = true) } -> {
                    Log.d("NotificationListener", "High importance keyword detected in title: '$title'. Allowing notification immediately.")
                    super.onNotificationPosted(sbn)
                    return
                }
                mediumImportanceTexts.any { text.contains(it, ignoreCase = true) } && packageName != "com.example.uxchannel_proto" -> {
                    if (!screenOnFlag) {
                        Log.d("NotificationListener", "Medium importance keyword detected, notification canceled: '$title'")
                        cancelNotification(key)
                        pendingMediumNotifications[key] = sbn
                    } else {
                        Log.d("NotificationListener", "Screen is ON, allowing medium importance notification: '$title'")
                        super.onNotificationPosted(sbn)
                    }
                    return
                }
                lowImportanceTexts.any { text.contains(it, ignoreCase = true) } && packageName != "com.example.uxchannel_proto" -> {
                    Log.d("NotificationListener", "Low importance keyword detected, notification canceled: '$title'")
                    cancelNotification(key)
                    pendingLowNotifications[key] = sbn
                    sendLowImportanceNotificationCountBroadcast(pendingLowNotifications.size)
                    return
                }
            }
            Log.d("NotificationListener", "No keyword matched. Checking package importance levels for: '$packageName'")
            when {
                highImportanceApps.contains(packageName) -> {
                    Log.d("NotificationListener", "High importance package detected: $packageName. Allowing notification immediately.")
                    super.onNotificationPosted(sbn)
                }
                mediumImportanceApps.contains(packageName) && packageName != "com.example.uxchannel_proto" -> {
                    if (!screenOnFlag) {
                        Log.d("NotificationListener", "Medium importance package, notification canceled: $packageName")
                        cancelNotification(key)
                        pendingMediumNotifications[key] = sbn
                    } else {
                        Log.d("NotificationListener", "Screen is ON, allowing medium importance package notification: $packageName")
                        super.onNotificationPosted(sbn)
                    }
                }
                lowImportanceApps.contains(packageName) && packageName != "com.example.uxchannel_proto" -> {
                    Log.d("NotificationListener", "Low importance package detected, notification canceled: $packageName")
                    cancelNotification(key)
                    pendingLowNotifications[key] = sbn
                    sendLowImportanceNotificationCountBroadcast(pendingLowNotifications.size)
                }
                else -> {
                    Log.d("NotificationListener", "No matching importance level for package or keyword found. Default behavior for: $packageName")
                    super.onNotificationPosted(sbn)
                }
            }
        } else {
            Log.d("NotificationListener", "Feature disabled or invalid notification ID. Default behavior for notification.")
            super.onNotificationPosted(sbn)
        }
    }

    private fun saveReceivedNotificationAppsToSharedPreferences() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putStringSet("receivedNotificationApps", receivedNotificationApps)
        editor.apply() // 즉시 저장
        Log.d("NotificationListener", "Received notification apps saved to SharedPreferences.")

        // 브로드캐스트 전송
        val intent = Intent("com.example.RECEIVED_NOTIFICATION_APPS_UPDATED")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("NotificationListener", "Broadcast sent for receivedNotificationApps update.")
    }
    private fun loadReceivedNotificationAppsFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val loadedApps = sharedPreferences.getStringSet("receivedNotificationApps", emptySet()) ?: emptySet()

        // 기존 데이터 초기화 후 새 데이터 추가
        receivedNotificationApps.clear()
        receivedNotificationApps.addAll(loadedApps)

        Log.d("MainActivity", "Loaded receivedNotificationApps: $receivedNotificationApps")
    }

    // Firebase에 데이터 전송
    @SuppressLint("HardwareIds")
    private fun sendDataToFirebase(notificationId: Int, packageName: String, title: String?, text: String?, postTime: String) {
        if (packageName != "com.android.systemui") { // systemui 패키지 제외
            val anonymizedText = text?.let { anonymizeText(it) } ?: "No Text"
            val uniqueKey = "$notificationId-$postTime"
            val notificationData = hashMapOf(
                "deviceId" to deviceId,
                "package_name" to packageName,
                "notification_id" to notificationId,
                "post_time" to postTime,
                "title" to title,
                "text" to anonymizedText
            )

            // Firebase Firestore에 데이터 저장
            val database = FirebaseDatabase.getInstance().getReference("notifications")
            database.child(uniqueKey).setValue(notificationData)
                .addOnSuccessListener {
                    Log.d("NotificationListener", "Data successfully written to Realtime Database.")
                }
                .addOnFailureListener { e ->
                    Log.w("NotificationListener", "Error writing document to Realtime Database", e)
                }
        }
    }

    // 알림이 제거될때 호출
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        try {
            val packageName = sbn.packageName
            val notificationId = sbn.id
            val key = sbn.key
            val removalReason = parseRemovalReason(reason) // 제거 이유
            val removalTime = formatDate(System.currentTimeMillis())
            val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: "No Title"
            val text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "No Text"
            Log.d("NotificationListener", "Notification removed: ID=$notificationId, Title='$title', Text='$text', Package='$packageName', Reason='$removalReason', Removal Time='$removalTime'")
            sendRemovalDataToFirebase(notificationId, packageName, title, text, removalReason, removalTime)

        } catch (e: DeadObjectException) {
            Log.e("NotificationListener", "DeadObjectException: ${e.message}")
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error during onNotificationRemoved: ${e.message}")
        }
    }

    // 제거 이유를 문자열로 변환
    private fun parseRemovalReason(reason: Int): String {
        return when (reason) {
            REASON_APP_CANCEL -> "App Specific Cancel"
            REASON_APP_CANCEL_ALL -> "App Cancel All Notifications"
            REASON_ASSISTANT_CANCEL -> "Assistant Cancel"
            REASON_CANCEL -> "Notification Swiped"
            REASON_CANCEL_ALL -> "All Notifications Cleared"
            REASON_CHANNEL_BANNED -> "Channel Banned"
            REASON_CHANNEL_REMOVED -> "Channel Removed"
            REASON_CLEAR_DATA -> "Data Cleared"
            REASON_CLICK -> "Notification Clicked"
            REASON_ERROR -> "Error"
            REASON_GROUP_OPTIMIZATION -> "Group Optimization"
            REASON_GROUP_SUMMARY_CANCELED -> "Group Summary Canceled"
            REASON_LISTENER_CANCEL -> "Listener Cancel"
            REASON_LISTENER_CANCEL_ALL -> "Listener Cancel All"
            REASON_LOCKDOWN -> "Lockdown"
            REASON_PACKAGE_BANNED -> "Package Banned"
            REASON_PACKAGE_CHANGED -> "Package Changed"
            REASON_PACKAGE_SUSPENDED -> "Package Suspended"
            REASON_PROFILE_TURNED_OFF -> "Profile Turned Off"
            REASON_SNOOZED -> "Snoozed"
            REASON_TIMEOUT -> "Timeout"
            REASON_UNAUTOBUNDLED -> "Unautobundled"
            REASON_USER_STOPPED -> "User Stopped"
            else -> "Other"
        }
    }

    @SuppressLint("HardwareIds")
    // 지연된 알림을 다시 보내는 메소드
    private fun sendRemovalDataToFirebase(notificationId: Int, packageName: String, title: String?, text: String?, reason: String, removalTime: String) {
        if (packageName != "com.android.systemui") { // systemui 패키지 제외
            val anonymizedText = text?.let { anonymizeText(it) } ?: "No Text"
            val uniqueKey = "$notificationId-$removalTime"
            val removalData = hashMapOf(
                "deviceId" to deviceId,
                "package_name" to packageName,
                "notification_id" to notificationId,
                "removal_time" to removalTime,
                "title" to title,
                "text" to anonymizedText,
                "removal_reason" to reason
            )

            // Firebase Firestore에 데이터 저장
            val database = FirebaseDatabase.getInstance().getReference("notification_removals")
            database.child(uniqueKey).setValue(removalData)
                .addOnSuccessListener {
                    Log.d("NotificationListener", "Removal data successfully written to Realtime Database.")
                }
                .addOnFailureListener { e ->
                    Log.w("NotificationListener", "Error writing removal data to Realtime Database", e)
                }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendDelayedNotification(sbn: StatusBarNotification) {
        val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE)
            ?: sbn.notification.extras.getString(Notification.EXTRA_TEXT)
            ?: sbn.notification.extras.getString(Notification.EXTRA_BIG_TEXT)
            ?: getAppNameFromPackage(sbn.packageName) // Use app name if title is null
        val uniqueNotificationId = UUID.randomUUID().hashCode()
//        val uniqueNotificationId = title.hashCode()  // TITLE만을 기반으로 고유 ID 생성
        Log.d("NotificationListener", "Attempting to send delayed notification for: $title")
        val channelID = "delayed_channel_id"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelID, "Delayed Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        val originalNotification = sbn.notification
        val extras = originalNotification.extras
        val color = sbn.notification.color
        val smallIcon = originalNotification.smallIcon
        val smallIconCompat = IconCompat.createFromIcon(this, smallIcon)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: "No details available"
        // 알림을 클릭할 때 액션 정의
        val contentIntent = originalNotification.contentIntent
        // 새로운 알림 생성 및 발송
        val newNotification = smallIconCompat?.let {
            NotificationCompat.Builder(this, channelID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(it)
                .setColor(color)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addExtras(Bundle().apply { putBoolean("isDelayedNotification", true) })
                .setExtras(Bundle(extras))
                .build()
        }
        notificationManager.notify(uniqueNotificationId, newNotification)
        Log.d("NotificationListener", "Notification sent for: $title with ID: $uniqueNotificationId")
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val packageManager = this.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // Fallback to package name if app name cannot be retrieved
        }
    }

    private fun handleScreenOff() {
        screenOnFlag = false
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun handleScreenOn() {
        screenOnFlag = true
        // Send notifications from medium importance apps when the screen turns on
        pendingMediumNotifications.values.forEach { sbn ->
            val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE)
            if (!title.isNullOrEmpty()) {
                sendDelayedNotification(sbn)
            }
        }
        pendingMediumNotifications.clear() // Clear the waiting list even if some titles are empty
    }
    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendRequestedLowImportanceNotifications() {

        val count = pendingLowNotifications.size
        sendLowImportanceNotificationCountBroadcast(count)

        Log.d("NotificationListener", "Sending low importance notifications")
        pendingLowNotifications.values.forEach { sbn ->
            var title = sbn.notification.extras.getString(Notification.EXTRA_TITLE)

            if (title.isNullOrEmpty()) {
                // Retrieve the app name using the package manager
                val packageName = sbn.packageName
                val appName = try {
                    val packageManager = applicationContext.packageManager
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                    ).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.e("NotificationListener", "App name not found for package: $packageName", e)
                    packageName // Fallback to package name if app name is not found
                }
                title = appName // Set the app name as the title
            }

            Log.d("NotificationListener", "Attempting to send delayed notification for: $title")
            sendDelayedNotification(sbn) // Send notification with the ensured title
        }
        pendingLowNotifications.clear()
        sendLowImportanceNotificationCountBroadcast(0)
    }

    private fun sendLowImportanceNotificationCountBroadcast(count: Int) {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("lowImportanceCount", count).apply()

        val intent = Intent("UPDATE_LOW_IMPORTANCE_NOTIFICATION_COUNT")
        intent.putExtra("count", count)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun unregisterScreenOnOffReceiver() {
        try {
            if (::screenOnOffReceiver.isInitialized) {
                unregisterReceiver(screenOnOffReceiver)
                Log.d("NotificationListener", "ScreenOnOffReceiver unregistered")
            }
        } catch (e: IllegalArgumentException) {
            Log.e("NotificationListener", "ScreenOnOffReceiver not registered: ${e.message}")
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error during unregisterScreenOnOffReceiver: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Log.d("NotificationListener", "Service destroyed")
            // Remove all callbacks and messages from the handler
            handler.removeCallbacksAndMessages(null)
            // Stop the foreground service
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(appImportanceUpdateReceiver)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(lowImportanceReceiver)

            unregisterScreenOnOffReceiver()
        } catch (e: IllegalArgumentException) {
            Log.e("NotificationListener", "Receiver not registered: ${e.message}")
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error during onDestroy: ${e.message}")
        }
    }
}

