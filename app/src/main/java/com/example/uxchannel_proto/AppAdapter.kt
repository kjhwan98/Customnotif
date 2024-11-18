package com.example.uxchannel_proto

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.uxchannel_proto.NotificationListener.Companion.receivedNotificationApps

class AppAdapter(
    private val appList: MutableList<Pair<String, String>>,  // 앱 이름과 패키지 이름의 Pair 리스트
    private val context: Context,
    var onAppDeleted: (String) -> Unit,
    private val onAppAdded: (String, String) -> Unit  // 콜백을 통해 앱 이름과 패키지 이름 전달
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_ITEM = 0
    private val VIEW_TYPE_ADD_BUTTON = 1

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appNameTextView: TextView = itemView.findViewById(R.id.appNameText)  // 앱 이름을 표시할 TextView
        val appIconImageView: ImageView = itemView.findViewById(R.id.appIcon)  // 앱 아이콘을 표시할 ImageView
        val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
    }

    class AddButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val addButton: Button = itemView.findViewById(R.id.addButton)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_ADD_BUTTON else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_ITEM) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.app_item, parent, false)
            AppViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.add_item, parent, false)
            AddButtonViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AppViewHolder) {
            val (appName, packageName) = appList[position - 1]  // Adjust index for actual app items
            // Set app name and icon as before
            holder.appNameTextView.text = appName
            try {
                val icon = if (packageName.isNotEmpty()) {
                    context.packageManager.getApplicationIcon(packageName)
                } else {
                    ContextCompat.getDrawable(context, R.drawable.ic_word)
                }
                holder.appIconImageView.setImageDrawable(icon)
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                holder.appIconImageView.setImageResource(R.drawable.ic_word)  // Default icon
            }

            // Delete button click listener
            holder.deleteButton.setOnClickListener {
                if (packageName.isEmpty()) {
                    removeKeyword(appName)
                } else {
                    removeApp(packageName)
                }
            }
        } else if (holder is AddButtonViewHolder) {
            holder.addButton.setOnClickListener {
                onAppAdded("", "")
            }
        }
    }

    override fun getItemCount(): Int = appList.size + 1  // 마지막에 "추가" 버튼을 위한 아이템 추가

    // 앱 추가 함수
    fun addApp(app: Pair<String, String>) {
        appList.add(0, app)  // Insert at the top of app list, after the add button
        notifyItemInserted(1)  // Notify insertion at position 1 (after add button)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun removeApp(packageName: String) {
        val index = appList.indexOfFirst { it.second == packageName }
        if (index != -1) {
            appList.removeAt(index)
            notifyDataSetChanged()
            onAppDeleted(packageName)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun removeKeyword(keyword: String) {
        val index = appList.indexOfFirst { it.first == keyword && it.second.isEmpty() }
        if (index != -1) {
            appList.removeAt(index)
            notifyDataSetChanged()
            onAppDeleted(keyword)
        }
    }

    // Retrieve the current app list
    fun getAppList(): List<Pair<String, String>> {
        return appList
    }

    // Update the app list when loading from SharedPreferences
    @SuppressLint("NotifyDataSetChanged")
    fun updateAppList(newAppList: List<Pair<String, String>>) {
        appList.clear()
        appList.addAll(newAppList)
        notifyDataSetChanged()  // Refresh the adapter
    }
}