# ProGuard 기본 규칙 및 사용자 정의 규칙 포함
-keep class com.example.uxchannel_proto.** { *; }

# NotificationListener 보호 (정확히 포함된 모든 멤버 보호)
-keep class com.example.uxchannel_proto.NotificationListener { *; }

# BroadcastReceiver 보호
-keep class com.example.uxchannel_proto.BootShutdownReceiver { *; }

# Service 클래스 전체 보호
-keep class com.example.uxchannel_proto.UsageStatsService { *; }

# MyApp (Custom Application 클래스) 보호
-keep class com.example.uxchannel_proto.MyApp { *; }

# 모든 Activity 보호
-keep class com.example.uxchannel_proto.*Activity { *; }

# SharedPreferences 키를 리플렉션으로 접근하는 경우 보호
-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
}

# Firebase 관련 규칙 (반드시 포함)
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class com.google.** { *; }
-keepnames class com.google.** { *; }
-keepclassmembers class com.google.** { *; }
-dontwarn com.google.**