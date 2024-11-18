package com.example.uxchannel_proto

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.uxchannel_proto.NotificationListener.Companion.receivedNotificationApps
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {
    // 앱에서 사용하는 각종 권환 및 서비스에 대한 요청 코드와 알림 채널 ID 정의
    companion object {
        const val PREFS_NAME = "AppPrefs"
        const val KEY_SERVICE_ON_TIME = "serviceOnTime"
        const val KEY_SERVICE_OFF_TIME = "serviceOffTime"
        private const val KEY_LAST_TIMESTAMP = "lastTimestamp"
        private const val KEY_FEATURE_ENABLED = "featureEnabled"
    }
    private lateinit var realtimeDatabase: FirebaseDatabase
    private lateinit var deviceId: String
    private lateinit var handler: Handler

    // 토글 버튼 정의(서비스 시작/중지)
    private lateinit var btnToggleFeature: Button

    // 앱 추가/삭제를 위한 RecyclerView 어댑터 정의
    private lateinit var highImportanceAdapter: AppAdapter
    private lateinit var mediumImportanceAdapter: AppAdapter
    private lateinit var lowImportanceAdapter: AppAdapter
    private val highImportanceTexts = mutableSetOf<String>()
    private val mediumImportanceTexts = mutableSetOf<String>()
    private val lowImportanceTexts = mutableSetOf<String>()
    private val addedApps = mutableSetOf<String>()
    private var receivedNotificationApps = mutableSetOf<String>() // 기존 알림을 받은 앱 저장

    private val receivedNotificationAppsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.RECEIVED_NOTIFICATION_APPS_UPDATED") {
                loadReceivedNotificationAppsFromSharedPreferences()
                Log.d("MainActivity", "Received notification apps updated and reloaded.")

                // Get the latest low importance count from SharedPreferences
                val lowImportanceCount = getCurrentLowImportanceNotificationCount()
                // Update the button with the latest count
                updateLowImportanceNotificationCount(lowImportanceCount)
            }
        }
    }

    // 데이터 전송 상태를 받는 BroadcastReceiver를 정의
    private val transferDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isSuccess = intent.getBooleanExtra("TransferStatus", false)
            if (isSuccess) {
                Toast.makeText(context, "Data Transfer Successful", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Data Transfer Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val lowImportanceCountReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("NotificationListener", "BroadcastReceiver triggered")
            if (intent?.action == "UPDATE_LOW_IMPORTANCE_NOTIFICATION_COUNT") {
                val lowImportanceCount = intent.getIntExtra("count", 0)
                Log.d("NotificationListener", "Received low importance notification count: $lowImportanceCount")
                updateLowImportanceNotificationCount(lowImportanceCount)
            }
        }
    }

    private val keywordUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadKeywordsFromSharedPreferences()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateLowImportanceNotificationCount(count: Int) {
        Log.d("MainActivity", "Updating count in UI: $count")
        val requestLowImportanceButton: Button = findViewById(R.id.request_low_importance_button)
        requestLowImportanceButton.isEnabled = count > 0
        requestLowImportanceButton.backgroundTintList = ColorStateList.valueOf(
            if (count > 0) Color.parseColor("#82DE94") else Color.GRAY
        )
        requestLowImportanceButton.text = if (count > 0) "$count 개 알림 받기" else "대기 중인 알람이 없습니다"
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "InlinedApi", "MissingInflatedId")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        realtimeDatabase = FirebaseDatabase.getInstance().apply {
            setPersistenceEnabled(true) // 오프라인 시 데이터 로컬 저장 활성화
        }
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        deviceId = sharedPreferences.getString("deviceId", "UnknownDeviceId") ?: "UnknownDeviceId"

        // 위치, 활동 인식, 알림, 배터리 최적화 권한을 요청하는 메소드 호출
        requestNotificationPermission()
        requestBatteryOptimizationPermission()
        checkNotificationListenerPermission()
        setupRecyclerViews()
        loadAppListsFromSharedPreferences()
        loadReceivedNotificationAppsFromSharedPreferences()
        loadKeywordsFromSharedPreferences()

        // 키워드 추가 버튼 이벤트 설정
        findViewById<ImageButton>(R.id.openKeywordButton).setOnClickListener {
            val intent = Intent(this, KeywordActivity::class.java)
            startActivity(intent)
        }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(receivedNotificationAppsUpdateReceiver, IntentFilter("com.example.RECEIVED_NOTIFICATION_APPS_UPDATED"))
        // 키워드 업데이트 수신기 등록
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(keywordUpdateReceiver, IntentFilter("com.example.KEYWORDS_UPDATED"))
        // BroadcastReceiver 등록
        val lowImportanceCountFilter = IntentFilter("UPDATE_LOW_IMPORTANCE_NOTIFICATION_COUNT")
        LocalBroadcastManager.getInstance(this).registerReceiver(lowImportanceCountReceiver, lowImportanceCountFilter)

        // 중요도 낮은 앱 알림 요청
        val requestLowImportanceButton: Button = findViewById(R.id.request_low_importance_button)
        requestLowImportanceButton.setOnClickListener {
            val pendingNotificationCount = getCurrentLowImportanceNotificationCount()
            logNotificationRequestButtonPress(pendingNotificationCount) // 로그 추가
            Log.d(
                "NotificationListener",
                "Sending local broadcast for low importance notifications"
            )

            // LocalBroadcastManager를 사용하여 로컬 브로드캐스트 전송
            val lowImportanceIntent = Intent("com.example.REQUEST_LOW_IMPORTANCE_NOTIFICATIONS")
            LocalBroadcastManager.getInstance(this).sendBroadcast(lowImportanceIntent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, UsageStatsService::class.java))
        } else {
            startService(Intent(this, UsageStatsService::class.java))
        }

        if (!hasUsageStatsPermission()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else {
            startService(Intent(this, UsageStatsService::class.java))
        }

        val transferDataFilter = IntentFilter("com.example.app.TRANSFER_DATA")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerReceiver(
                transferDataReceiver,
                transferDataFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(transferDataReceiver, transferDataFilter)
        }
        handler = Handler(Looper.getMainLooper())

        // 기능 토글 버튼의 클릭 이벤트 처리
        btnToggleFeature = findViewById(R.id.toggleFeatureButton)
        btnToggleFeature.setOnClickListener {
            toggleFeature()
        }
        // Initialize RecyclerView and TextView references for tabs
        val highImportanceText: TextView = findViewById(R.id.highImportanceText)
        val mediumImportanceText: TextView = findViewById(R.id.mediumImportanceText)
        val lowImportanceText: TextView = findViewById(R.id.lowImportanceText)
        val recyclerViewHigh: RecyclerView = findViewById(R.id.recyclerView_high)
        val recyclerViewMedium: RecyclerView = findViewById(R.id.recyclerView_medium)
        val recyclerViewLow: RecyclerView = findViewById(R.id.recyclerView_low)

        // Set up click listeners to switch between RecyclerViews based on selected tab
        highImportanceText.setOnClickListener { showRecyclerView(recyclerViewHigh, highImportanceText, mediumImportanceText, lowImportanceText) }
        mediumImportanceText.setOnClickListener { showRecyclerView(recyclerViewMedium, highImportanceText, mediumImportanceText, lowImportanceText) }
        lowImportanceText.setOnClickListener { showRecyclerView(recyclerViewLow, highImportanceText, mediumImportanceText, lowImportanceText) }

        // Initial setup: Show high importance RecyclerView by default
        showRecyclerView(recyclerViewHigh, highImportanceText, mediumImportanceText, lowImportanceText)
        startUsageStatsService()
        updateLowImportanceNotificationCount(0) // Assuming 0 notifications initially
    }

    private fun showRecyclerView(
        selectedRecyclerView: RecyclerView,
        highImportanceText: TextView,
        mediumImportanceText: TextView,
        lowImportanceText: TextView
    ) {
        // 각 RecyclerView의 가시성을 설정
        findViewById<RecyclerView>(R.id.recyclerView_high).visibility = if (selectedRecyclerView == findViewById(R.id.recyclerView_high)) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.recyclerView_medium).visibility = if (selectedRecyclerView == findViewById(R.id.recyclerView_medium)) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.recyclerView_low).visibility = if (selectedRecyclerView == findViewById(R.id.recyclerView_low)) View.VISIBLE else View.GONE

        // 모든 탭의 선택 상태를 초기화
        highImportanceText.isSelected = false
        mediumImportanceText.isSelected = false
        lowImportanceText.isSelected = false

        // 선택된 탭의 상태를 활성화
        when (selectedRecyclerView) {
            findViewById<RecyclerView>(R.id.recyclerView_high) -> highImportanceText.isSelected = true
            findViewById<RecyclerView>(R.id.recyclerView_medium) -> mediumImportanceText.isSelected = true
            findViewById<RecyclerView>(R.id.recyclerView_low) -> lowImportanceText.isSelected = true
        }
    }


    private fun updateButtonColor(isFeatureEnabled: Boolean) {
        btnToggleFeature.setBackgroundColor(
            if (isFeatureEnabled) Color.parseColor("#82DE94") else Color.GRAY
        )
        btnToggleFeature.text = if (isFeatureEnabled) "Service On" else "Service Off"
    }

    // 기능 토글 버튼의 상태를 업데이트
    private fun toggleFeature() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val currentTimestamp = System.currentTimeMillis()
        val lastTimestamp = sharedPreferences.getLong(KEY_LAST_TIMESTAMP, currentTimestamp)
        val elapsedTime = currentTimestamp - lastTimestamp

        val isFeatureEnabled = sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)

        if (isFeatureEnabled) {
            val currentOnTime = sharedPreferences.getLong(KEY_SERVICE_ON_TIME, 0)
            editor.putLong(KEY_SERVICE_ON_TIME, currentOnTime + elapsedTime)
            Toast.makeText(this, "기능이 비활성화되었습니다.", Toast.LENGTH_SHORT).show()

            // 기능 비활성화 시 storedNotifications를 비우지 않음
        } else {
            val currentOffTime = sharedPreferences.getLong(KEY_SERVICE_OFF_TIME, 0)
            editor.putLong(KEY_SERVICE_OFF_TIME, currentOffTime + elapsedTime)
            Toast.makeText(this, "기능이 활성화되었습니다.", Toast.LENGTH_SHORT).show()
        }

        editor.putBoolean(KEY_FEATURE_ENABLED, !isFeatureEnabled)
        editor.putLong(KEY_LAST_TIMESTAMP, currentTimestamp)

        if (editor.commit()) {
            updateButtonColor(!isFeatureEnabled)
        } else {
            Toast.makeText(this, "설정 저장 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkNotificationListenerPermission() {
        // 알림 접근 권한이 부여되었는지 확인, 부여되지 않았다면 사용자에게 설정 변경을 요청
        if (!permissionGranted()) {
            AlertDialog.Builder(this)
                .setTitle("알림 서비스 허용")
                .setMessage("앱의 기능을 완전히 사용하기 위해서는 알림 허용이 필요합니다.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun permissionGranted(): Boolean {
        // 알림 접근 권한이 부여되었는지 확인
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationPermission() {
        // 배터리 최적화 무시 권한 요청
        val packageName = packageName
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent()
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestNotificationPermission() {
        // 알림 허용 여부 확인
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("알림 권한 허용")
                .setMessage("앱의 기능을 완전히 사용하기 위해서는 알림 허용이 필요합니다.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    // 사용자가 설정으로 이동하길 원할 경우, 앱의 알림 설정 화면으로 이동
                    val intent = Intent().apply {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            putExtra(Settings.EXTRA_CHANNEL_ID, applicationInfo.uid)
                        }
                    }
                    startActivity(intent)
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    // 앱이 다시 활성화될 때 호출
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onResume() {
        super.onResume()
        startUsageStatsService()
        loadAppListsFromSharedPreferences()
        loadReceivedNotificationAppsFromSharedPreferences()

        val lowImportanceCount = getCurrentLowImportanceNotificationCount()
        updateLowImportanceNotificationCount(lowImportanceCount)

    }

    private fun getCurrentLowImportanceNotificationCount(): Int {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("lowImportanceCount", 0)
    }

    private fun checkServiceEnabled(): Boolean {
        // 서비스 활성화 여부를 SharedPreferences에서 가져옴
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)
    }

    override fun onPause() {
        super.onPause()
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isFeatureEnabled = sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)
        updateButtonColor(isFeatureEnabled)
        saveAppListsToSharedPreferences()
        saveReceivedNotificationAppsToSharedPreferences()
    }

    override fun onStop() {
        super.onStop()
        saveAppListsToSharedPreferences()
        saveReceivedNotificationAppsToSharedPreferences()// Also save when the app is stopped
    }

    private fun startUsageStatsService() {
        val serviceIntent = Intent(this, UsageStatsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    // 사용 통계 권한이 있는지 확인
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = applicationContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        appOps?.let {
            // API 레벨 29 이상에서는 unsafeCheckOpNoThrow를 사용
            val mode = it.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }
        return false
    }

    // 중요도별 앱 목록 저장 및 방송 전송
    private fun saveAppListsToSharedPreferences() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val highImportanceApps = highImportanceAdapter.getAppList().map { "${it.first}:${it.second}" }.toSet()
        val mediumImportanceApps = mediumImportanceAdapter.getAppList().map { "${it.first}:${it.second}" }.toSet()
        val lowImportanceApps = lowImportanceAdapter.getAppList().map { "${it.first}:${it.second}" }.toSet()
        Log.d("MainActivity", "Saving highImportanceApps: $highImportanceApps")
        Log.d("MainActivity", "Saving mediumImportanceApps: $mediumImportanceApps")
        Log.d("MainActivity", "Saving lowImportanceApps: $lowImportanceApps")
        Log.d("MainActivity", "Saving receivedNotificationApps: $receivedNotificationApps")
        editor.putStringSet("highImportanceApps", highImportanceApps)
        editor.putStringSet("mediumImportanceApps", mediumImportanceApps)
        editor.putStringSet("lowImportanceApps", lowImportanceApps)

        if (editor.commit()) {
            Log.d("MainActivity", "App lists and receivedNotificationApps saved successfully.")
            val intent = Intent("com.example.APP_IMPORTANCE_UPDATED")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        } else{
            Log.e("MainActivity", "Failed to save app lists and receivedNotificationApps.")
        }
    }

    private fun loadAppListsFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val highImportanceApps = sharedPreferences.getStringSet("highImportanceApps", emptySet())?.map {
            val parts = it.split(":")
            parts[0] to parts[1]  // Convert back to Pair(appName, packageName)
        }?.toMutableList() ?: mutableListOf()

        val mediumImportanceApps = sharedPreferences.getStringSet("mediumImportanceApps", emptySet())?.map {
            val parts = it.split(":")
            parts[0] to parts[1]
        }?.toMutableList() ?: mutableListOf()

        val lowImportanceApps = sharedPreferences.getStringSet("lowImportanceApps", emptySet())?.map {
            val parts = it.split(":")
            parts[0] to parts[1]
        }?.toMutableList() ?: mutableListOf()

        Log.d("MainActivity", "Loaded highImportanceApps: $highImportanceApps")
        Log.d("MainActivity", "Loaded mediumImportanceApps: $mediumImportanceApps")
        Log.d("MainActivity", "Loaded lowImportanceApps: $lowImportanceApps")

        highImportanceAdapter.updateAppList(highImportanceApps)
        mediumImportanceAdapter.updateAppList(mediumImportanceApps)
        lowImportanceAdapter.updateAppList(lowImportanceApps)
    }


    private fun setupRecyclerViews() {
        Log.d("MainActivity", "Setting up RecyclerViews for importance levels")

        // High importance apps and keywords
        setupRecyclerView(R.id.recyclerView_high, mutableListOf()) { keywordOrPackageName ->
            Log.d("MainActivity", "High Importance - Item: '$keywordOrPackageName'") // Debugging each item
            if (keywordOrPackageName.isNotEmpty() && highImportanceTexts.contains(keywordOrPackageName)) { // Keyword removal
                Log.d("MainActivity", "MainActivity - Removing keyword from High Importance: '$keywordOrPackageName'")
                highImportanceAdapter.removeKeyword(keywordOrPackageName) // 키워드 삭제
                highImportanceTexts.remove(keywordOrPackageName) // 리스트에서도 삭제
                saveKeywordsToSharedPreferences()
                Log.d("MainActivity", "Keywords after removal from High Importance: $highImportanceTexts")
                Toast.makeText(this, "Keyword '$keywordOrPackageName' removed from high importance.", Toast.LENGTH_SHORT).show()
            } else { // App removal
                Log.d("MainActivity", "MainActivity - Removing app package from High Importance: '$keywordOrPackageName'")
                highImportanceAdapter.removeApp(keywordOrPackageName)
                val updatedHighImportanceList = highImportanceAdapter.getAppList().filterNot { it.second == keywordOrPackageName }
                highImportanceAdapter.updateAppList(updatedHighImportanceList)
                saveAppListsToSharedPreferences()
                Log.d("MainActivity", "High Importance Apps after removal: ${highImportanceAdapter.getAppList()}")
                Toast.makeText(this, "App '$keywordOrPackageName' removed from high importance.", Toast.LENGTH_SHORT).show()
            }
        }

        // Medium importance apps and keywords
        setupRecyclerView(R.id.recyclerView_medium, mutableListOf()) { keywordOrPackageName ->
            if (keywordOrPackageName.isNotEmpty() && mediumImportanceTexts.contains(keywordOrPackageName)) { // Keyword removal
                Log.d("MainActivity", "MainActivity - Removing keyword from Medium Importance: '$keywordOrPackageName'")
                mediumImportanceAdapter.removeKeyword(keywordOrPackageName)
                mediumImportanceTexts.remove(keywordOrPackageName)
                saveKeywordsToSharedPreferences()
                Log.d("MainActivity", "Keywords after removal from Medium Importance: $mediumImportanceTexts")
                Toast.makeText(this, "Keyword '$keywordOrPackageName' removed from medium importance.", Toast.LENGTH_SHORT).show()
            } else { // App removal
                Log.d("MainActivity", "MainActivity - Removing app package from Medium Importance: '$keywordOrPackageName'")
                mediumImportanceAdapter.removeApp(keywordOrPackageName)
                val updatedMediumImportanceList = mediumImportanceAdapter.getAppList().filterNot { it.second == keywordOrPackageName }
                mediumImportanceAdapter.updateAppList(updatedMediumImportanceList)
                saveAppListsToSharedPreferences()
                Log.d("MainActivity", "Medium Importance Apps after removal: ${mediumImportanceAdapter.getAppList()}")
                Toast.makeText(this, "App '$keywordOrPackageName' removed from medium importance.", Toast.LENGTH_SHORT).show()
            }
        }

        // Low importance apps and keywords
        setupRecyclerView(R.id.recyclerView_low, mutableListOf()) { keywordOrPackageName ->
            if (keywordOrPackageName.isNotEmpty() && lowImportanceTexts.contains(keywordOrPackageName)) { // Keyword removal
                Log.d("MainActivity", "MainActivity - Removing keyword from Low Importance: '$keywordOrPackageName'")
                lowImportanceAdapter.removeKeyword(keywordOrPackageName)
                lowImportanceTexts.remove(keywordOrPackageName)
                saveKeywordsToSharedPreferences()
                Log.d("MainActivity", "Keywords after removal from Low Importance: $lowImportanceTexts")
                Toast.makeText(this, "Keyword '$keywordOrPackageName' removed from low importance.", Toast.LENGTH_SHORT).show()
            } else { // App removal
                Log.d("MainActivity", "MainActivity - Removing app package from Low Importance: '$keywordOrPackageName'")
                lowImportanceAdapter.removeApp(keywordOrPackageName)
                val updatedLowImportanceList = lowImportanceAdapter.getAppList().filterNot { it.second == keywordOrPackageName }
                lowImportanceAdapter.updateAppList(updatedLowImportanceList)
                saveAppListsToSharedPreferences()
                Log.d("MainActivity", "Low Importance Apps after removal: ${lowImportanceAdapter.getAppList()}")
                Toast.makeText(this, "App '$keywordOrPackageName' removed from low importance.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getImportanceLevelByRecyclerViewId(recyclerViewId: Int): String {
        return when (recyclerViewId) {
            R.id.recyclerView_high -> "high"
            R.id.recyclerView_medium -> "medium"
            R.id.recyclerView_low -> "low"
            else -> "unknown"
        }
    }

    private fun setupRecyclerView(
        recyclerViewId: Int,
        appList: MutableList<Pair<String, String>>,
        onAppDeleted: (String) -> Unit
    ) {
        val recyclerView = findViewById<RecyclerView>(recyclerViewId)
        recyclerView.layoutManager = LinearLayoutManager(this)

        lateinit var adapter: AppAdapter
        adapter = AppAdapter(appList, this, onAppDeleted) { appName, packageName ->
            showAppPickerDialog { selectedName, selectedPackage ->
                if (selectedPackage.isEmpty()) {
                    if (isKeywordInAnotherImportanceLevel(selectedName)) {
                        Toast.makeText(this, "키워드가 이미 다른 레벨에 추가되었습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        removeKeywordFromAllLevels(selectedName) // Ensure previous level removal
                        adapter.addApp(selectedName to "")
                        saveKeywordToLevel(selectedName, recyclerViewId)
                        saveKeywordsToSharedPreferences()
                        logImportanceChange("keyword", selectedName, getImportanceLevelByRecyclerViewId(recyclerViewId), "added") // 로그 추가
                    }
                } else {
                    if (isAppInAnotherImportanceLevel(selectedPackage)) {
                        Toast.makeText(this, "앱이 이미 다른 레벨에 추가되었습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        adapter.addApp(selectedName to selectedPackage)
                        addedApps.add(selectedPackage)
                        receivedNotificationApps.remove(selectedPackage)
                        saveAppListsToSharedPreferences()
                        saveReceivedNotificationAppsToSharedPreferences()
                        logImportanceChange("app", selectedName, getImportanceLevelByRecyclerViewId(recyclerViewId), "added") // 로그 추가
                    }
                }
            }
        }

        recyclerView.adapter = adapter
        when (recyclerViewId) {
            R.id.recyclerView_high -> highImportanceAdapter = adapter
            R.id.recyclerView_medium -> mediumImportanceAdapter = adapter
            R.id.recyclerView_low -> lowImportanceAdapter = adapter
        }

        // 앱 삭제 시 처리
        adapter.onAppDeleted = { packageName ->
            Log.d("MainActivity", "onAppDeleted callback triggered with package: $packageName")

            // packageName이 키워드 리스트에 있는지 확인
            if (highImportanceTexts.contains(packageName) || mediumImportanceTexts.contains(packageName) || lowImportanceTexts.contains(packageName)) {
                Log.d("MainActivity", "Removing keyword from specific level: $packageName")
                removeKeywordFromLevel(packageName, recyclerViewId)
                saveKeywordsToSharedPreferences()
                logImportanceChange("keyword", packageName, getImportanceLevelByRecyclerViewId(recyclerViewId), "removed") // 로그 추가
            } else {
                // 일반 앱 삭제 처리
                addedApps.remove(packageName)
                receivedNotificationApps.add(packageName)
                saveReceivedNotificationAppsToSharedPreferences()
                logImportanceChange("app", packageName, getImportanceLevelByRecyclerViewId(recyclerViewId), "removed") // 로그 추가
            }
            saveAppListsToSharedPreferences()
        }
    }

    private fun removeKeywordFromAllLevels(keyword: String) {
        highImportanceTexts.remove(keyword)
        mediumImportanceTexts.remove(keyword)
        lowImportanceTexts.remove(keyword)
        saveKeywordsToSharedPreferences()
    }

    // 키워드를 각 중요도별로 저장하는 함수
    private fun saveKeywordToLevel(keyword: String, recyclerViewId: Int) {
        when (recyclerViewId) {
            R.id.recyclerView_high -> highImportanceTexts.add(keyword)
            R.id.recyclerView_medium -> mediumImportanceTexts.add(keyword)
            R.id.recyclerView_low -> lowImportanceTexts.add(keyword)
        }
    }

    // 키워드를 각 중요도별에서 제거하는 함수
    private fun removeKeywordFromLevel(keyword: String, recyclerViewId: Int) {
        Log.d("MainActivity", "removeKeywordFromLevel called with keyword: '$keyword' and recyclerViewId: $recyclerViewId")
        when (recyclerViewId) {
            R.id.recyclerView_high -> {
                highImportanceTexts.remove(keyword) // 정확히 해당 키워드만 삭제
                Log.d("MainActivity", "Keyword '$keyword' removed from High Importance Texts")
            }
            R.id.recyclerView_medium -> {
                mediumImportanceTexts.remove(keyword)
                Log.d("MainActivity", "Keyword '$keyword' removed from Medium Importance Texts")
            }
            R.id.recyclerView_low -> {
                lowImportanceTexts.remove(keyword)
                Log.d("MainActivity", "Keyword '$keyword' removed from Low Importance Texts")
            }
        }
    }

    // Check if a keyword is in another importance level
    private fun isKeywordInAnotherImportanceLevel(keyword: String): Boolean {
        return highImportanceAdapter.getAppList().any { it.first == keyword && it.second.isEmpty() } ||
                mediumImportanceAdapter.getAppList().any { it.first == keyword && it.second.isEmpty() } ||
                lowImportanceAdapter.getAppList().any { it.first == keyword && it.second.isEmpty() }
    }

    private fun isAppInAnotherImportanceLevel(packageName: String): Boolean {
        return highImportanceAdapter.getAppList().any { it.second == packageName } ||
                mediumImportanceAdapter.getAppList().any { it.second == packageName } ||
                lowImportanceAdapter.getAppList().any { it.second == packageName }
    }

    // 사용자가 키워드를 중요도에 추가할 때 해당 키워드를 SharedPreferences에 저장하는 함수
    private fun saveKeywordsToSharedPreferences() {
        Log.d("MainActivity", "Saving keywords to SharedPreferences. High: $highImportanceTexts, Medium: $mediumImportanceTexts, Low: $lowImportanceTexts")
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putStringSet("highImportanceTexts", highImportanceTexts)
        editor.putStringSet("mediumImportanceTexts", mediumImportanceTexts)
        editor.putStringSet("lowImportanceTexts", lowImportanceTexts)
        editor.apply()
        Log.d("MainActivity", "Keywords saved successfully.")

        // 키워드 변경 사항을 브로드캐스트
        val intent = Intent("com.example.KEYWORDS_UPDATED")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun loadKeywordsFromSharedPreferences() {
        Log.d("MainActivity", "Loading keywords from SharedPreferences.")
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        highImportanceTexts.clear()
        mediumImportanceTexts.clear()
        lowImportanceTexts.clear()
        val highTexts = sharedPreferences.getStringSet("highImportanceTexts", null)
        val mediumTexts = sharedPreferences.getStringSet("mediumImportanceTexts", null)
        val lowTexts = sharedPreferences.getStringSet("lowImportanceTexts", null)
        if (highTexts != null) highImportanceTexts.addAll(highTexts)
        if (mediumTexts != null) mediumImportanceTexts.addAll(mediumTexts)
        if (lowTexts != null) lowImportanceTexts.addAll(lowTexts)
        Log.d("MainActivity", "Loaded keywords: High=$highImportanceTexts, Medium=$mediumImportanceTexts, Low=$lowImportanceTexts")
    }

    private fun loadNotificationTitles(): MutableMap<String, String> {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val titleMapString = sharedPreferences.getString("notificationTitles", "")

        val notificationTitles = mutableMapOf<String, String>()
        titleMapString?.split(";")?.forEach {
            val parts = it.split(":")
            if (parts.size == 2) { // Ensure there are exactly two parts
                val (packageName, title) = parts
                notificationTitles[packageName] = title
            } else {
                Log.w("MainActivity", "Skipping malformed entry in notificationTitles: $it")
            }
        }

        return notificationTitles
    }

    // 앱 선택 다이얼로그
    @SuppressLint("QueryPermissionsNeeded")
    private fun showAppPickerDialog(onAppSelected: (String, String) -> Unit) {
        val packageManager = packageManager
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        Log.d("MainActivity", "receivedNotificationApps: $receivedNotificationApps")

        // Retrieve keywords from SharedPreferences
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedKeywords =
            sharedPreferences.getStringSet("savedKeywords", emptySet())?.toList() ?: emptyList()

        // Filter out keywords already assigned to an importance level
        val assignedKeywords = mutableSetOf<String>().apply {
            addAll(highImportanceAdapter.getAppList().filter { it.second.isEmpty() }
                .map { it.first })
            addAll(mediumImportanceAdapter.getAppList().filter { it.second.isEmpty() }
                .map { it.first })
            addAll(lowImportanceAdapter.getAppList().filter { it.second.isEmpty() }
                .map { it.first })
        }
        val unassignedKeywords = savedKeywords.filterNot { assignedKeywords.contains(it) }

        // Verify receivedNotificationApps is populated
        if (receivedNotificationApps.isEmpty()) {
            Log.w("MainActivity", "No received notifications found in receivedNotificationApps")
        }
        val notificationTitles = loadNotificationTitles()
        // Get only the apps that have sent notifications

        val notificationSendingApps = apps.filter { appInfo ->
            val isInReceived = receivedNotificationApps.contains(appInfo.packageName)
            val isExcluded = appInfo.packageName == "com.example.uxchannel_proto"
            isInReceived && !isExcluded
        }.map { appInfo ->
            val appName = appInfo.loadLabel(packageManager).toString()
            val packageName = appInfo.packageName
            val title = notificationTitles[packageName]
            val displayName = if (title != null && title != "No Title") "$appName - $title" else appName
            displayName to packageName
        }.sortedBy { it.first } //앱 이름순 으로 보여주기

        // Combine unassigned apps and keywords for display
        val combinedList = notificationSendingApps.map { it.first } + unassignedKeywords
        val combinedPackages =
            notificationSendingApps.map { it.second } + List(unassignedKeywords.size) { "" }

        if (combinedList.isEmpty()) {
            // Show an AlertDialog with a message indicating no items are available
            AlertDialog.Builder(this)
                .setTitle("앱 및 키워드 선택")
                .setMessage("아직 새로운 알림이 없습니다. 새로운 알림이 오면 설정해주세요.")
                .setPositiveButton("확인", null)
                .show()
            return
        }

        val styledList = combinedList.mapIndexed { index, item ->
            val spannable = SpannableString(item)
            val parts = item.split(" - ")

            val color = if (combinedPackages[index].isEmpty()) {
                ContextCompat.getColor(this, R.color.sky_blue) // Sky blue for keywords
            } else {
                ContextCompat.getColor(this, R.color.white) // White for apps
            }
            spannable.setSpan(
                ForegroundColorSpan(color),
                0,
                parts[0].length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // Set different sizes for the app name and the title (if available)
            if (parts.size > 1) {
                spannable.setSpan(
                    RelativeSizeSpan(0.7f), // Smaller size for the title part
                    parts[0].length + 3, // Start after " - "
                    item.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            spannable
        }

        // Use ArrayAdapter to display styled items
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, styledList)

        AlertDialog.Builder(this)
            .setTitle("앱 및 키워드 선택")
            .setAdapter(adapter) { _, which ->
                val selectedName = combinedList[which]
                val selectedPackage = combinedPackages[which]

                // If it's an app, extract only the app name without the title part
                val appName =
                    if (selectedPackage.isNotEmpty()) selectedName.split(" - ")[0] else selectedName

                // Pass the app name or keyword to the callback
                onAppSelected(appName, selectedPackage)
            }
            .show()
    }

    // 앱이 알림을 보낸 앱 목록을 SharedPreferences에 저장
    private fun saveReceivedNotificationAppsToSharedPreferences() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        editor.putStringSet("receivedNotificationApps", receivedNotificationApps)
        Log.d("MainActivity", "Saving receivedNotificationApps: $receivedNotificationApps")

        if (editor.commit()) {
            Log.d("MainActivity", "Received notification apps saved successfully.")
        } else {
            Log.e("MainActivity", "Failed to save received notification apps.")
        }
    }

    // 앱 시작 시 SharedPreferences에서 알림을 보낸 앱 목록을 불러옴
    private fun loadReceivedNotificationAppsFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        receivedNotificationApps = sharedPreferences.getStringSet("receivedNotificationApps", emptySet())?.toMutableSet()
            ?: mutableSetOf()

        Log.d("MainActivity", "Loaded receivedNotificationApps: $receivedNotificationApps")

        filterOutExistingImportanceApps()
    }

    private fun filterOutExistingImportanceApps() {
        val allImportanceApps = mutableSetOf<String>().apply {
            addAll(highImportanceAdapter.getAppList().map { it.second })
            addAll(mediumImportanceAdapter.getAppList().map { it.second })
            addAll(lowImportanceAdapter.getAppList().map { it.second })
        }
        receivedNotificationApps.removeAll(allImportanceApps)
        Log.d("MainActivity", "Filtered received notification apps after load: $receivedNotificationApps")
    }

    // 앱 또는 키워드가 중요도에 추가될 때 기록
    private fun logImportanceChange(itemType: String, itemName: String, importanceLevel: String, action: String) {
        val timestamp = getCurrentFormattedTimestamp()
        val logData = mapOf(
            "deviceId" to deviceId,
            "itemType" to itemType, // "app" 또는 "keyword"
            "itemName" to itemName,
            "importanceLevel" to importanceLevel, // 예: "high", "medium", "low"
            "action" to action, // "added" 또는 "removed"
            "timestamp" to timestamp
        )
        realtimeDatabase.reference.child("logs").push().setValue(logData)
    }

    // 알림 요청 버튼이 눌렸을 때 기록
    private fun logNotificationRequestButtonPress(pendingNotificationCount: Int) {
        val timestamp = getCurrentFormattedTimestamp()
        val logData = mapOf(
            "deviceId" to deviceId,
            "action" to "notification_request_button_pressed",
            "pendingNotificationCount" to pendingNotificationCount,
            "timestamp" to timestamp
        )
        realtimeDatabase.reference.child("logs").push().setValue(logData)
    }

    private fun getCurrentFormattedTimestamp(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) // 원하는 포맷
        return dateFormat.format(Date())
    }

    // 액티비티가 파괴될 때 BroadcastReceiver를 등록 해제
    override fun onDestroy() {
        super.onDestroy()
        saveAppListsToSharedPreferences()
        saveReceivedNotificationAppsToSharedPreferences()
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(lowImportanceCountReceiver)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(keywordUpdateReceiver)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receivedNotificationAppsUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("MainActivity", "Low importance count receiver was not registered.")
        }
        try {
            unregisterReceiver(transferDataReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("MainActivity", "Transfer data receiver was not registered.")
        }
        stopService(Intent(this, NotificationListener::class.java))
        Log.d("MainActivity", "All receivers unregistered, NotificationListener stopped.")
    }
}