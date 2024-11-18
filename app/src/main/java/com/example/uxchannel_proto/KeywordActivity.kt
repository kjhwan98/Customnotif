package com.example.uxchannel_proto

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class KeywordActivity : AppCompatActivity() {
    private lateinit var keywordListView: ListView
    private val receivedKeywords = mutableSetOf<String>() // 기존 키워드 목록
    private lateinit var deviceIdTextView: TextView
    private lateinit var realtimeDatabase: FirebaseDatabase
    private lateinit var deviceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keyword)

        deviceIdTextView = findViewById(R.id.tvDeviceId)
        realtimeDatabase = FirebaseDatabase.getInstance().apply {
            setPersistenceEnabled(true) // 오프라인 시 데이터 로컬 저장 활성화
        }
        // Retrieve the device ID from shared preferences and display it
        val sharedPreferences = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        deviceId = sharedPreferences.getString("deviceId", null) ?: "UnknownDeviceId"

        displayDeviceId(deviceId)

        keywordListView = findViewById(R.id.keywordListView)
        loadKeywordsFromSharedPreferences() // 저장된 키워드 로드

        findViewById<ImageButton>(R.id.addTextButton).setOnClickListener {
            showAddTextDialog()
        }

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish() // Close the activity and go back
        }

        updateKeywordListView()
    }

    @SuppressLint("SetTextI18n")
    private fun displayDeviceId(deviceId: String) {
        deviceIdTextView.text = "Device ID: $deviceId"
    }

    private fun showAddTextDialog() {
        val input = EditText(this).apply {
            hint = "추가할 키워드를 입력하세요"
        }

        AlertDialog.Builder(this)
            .setTitle("키워드 추가")
            .setMessage("알림에 사용할 키워드를 입력하세요.")
            .setView(input)
            .setPositiveButton("확인") { _, _ ->
                val keyword = input.text.toString().trim()
                if (keyword.isNotEmpty()) {
                    receivedKeywords.add(keyword)
                    saveKeywordsToSharedPreferences() // SharedPreferences에 저장
                    saveKeywordsToFirebase(keyword) // Firebase에 저장
                    updateKeywordListView()
                    Toast.makeText(this, "키워드가 추가되었습니다: $keyword", Toast.LENGTH_SHORT).show()

                    // 키워드가 추가된 후 Broadcast 전송
                    val intent = Intent("com.example.KEYWORDS_UPDATED")
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun updateKeywordListView() {
        val keywordList = receivedKeywords.toList()

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, keywordList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.BLACK) // 텍스트 색을 검정으로 설정
                return view
            }
        }

        keywordListView.adapter = adapter

        keywordListView.setOnItemClickListener { _, _, position, _ ->
            val keywordToRemove = keywordList[position]
            AlertDialog.Builder(this)
                .setTitle("키워드 삭제")
                .setMessage("정말로 키워드 \"$keywordToRemove\" 를 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    receivedKeywords.remove(keywordToRemove)
                    saveKeywordsToSharedPreferences()
                    removeKeywordFromFirebase(keywordToRemove) // Firebase에서 삭제 시간 업데이트
                    updateKeywordListView()
                    Toast.makeText(this, "키워드가 삭제되었습니다: $keywordToRemove", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun saveKeywordsToSharedPreferences() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putStringSet("savedKeywords", receivedKeywords) // Corrected key to "savedKeywords"
        editor.apply()

        // Send broadcast to notify MainActivity of the keyword update
        val intent = Intent("com.example.KEYWORDS_UPDATED")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun loadKeywordsFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        receivedKeywords.clear()
        receivedKeywords.addAll(sharedPreferences.getStringSet("savedKeywords", emptySet()) ?: emptySet()) // Corrected key to "savedKeywords"
    }

    private fun saveKeywordsToFirebase(keyword: String) {
        val keywordRef = realtimeDatabase.reference.child("users")
            .child(deviceId)
            .child("keywords")
            .push()

        val keywordData = mapOf(
            "deviceId" to deviceId, // deviceId 추가
            "keyword" to keyword,
            "addedAt" to System.currentTimeMillis(), // 추가 시간
            "deletedAt" to null // 삭제 시간은 없음(null)
        )

        keywordRef.setValue(keywordData).addOnSuccessListener {
            Log.d("KeywordActivity", "키워드가 Firebase에 성공적으로 추가되었습니다.")
        }.addOnFailureListener { exception ->
            Log.e("KeywordActivity", "Firebase에 키워드 추가 중 오류 발생: ${exception.message}")
        }
    }

    private fun removeKeywordFromFirebase(keyword: String) {
        val keywordRef = realtimeDatabase.reference.child("users")
            .child(deviceId)
            .child("keywords")

        keywordRef.orderByChild("keyword").equalTo(keyword).addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (dataSnapshot in snapshot.children) {
                    // 삭제 시간 업데이트
                    dataSnapshot.ref.child("deletedAt").setValue(System.currentTimeMillis())
                        .addOnSuccessListener {
                            Log.d("KeywordActivity", "Firebase에 키워드 삭제 시간이 성공적으로 업데이트되었습니다.")
                        }
                        .addOnFailureListener { exception ->
                            Log.e("KeywordActivity", "Firebase에 삭제 시간 업데이트 중 오류 발생: ${exception.message}")
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("KeywordActivity", "Firebase에서 키워드 쿼리 중 오류 발생: ${error.message}")
            }
        })
    }


}