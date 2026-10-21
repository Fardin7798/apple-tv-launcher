-keep class com.appletv.launcher.SkyView { <init>(...); }
-keep class com.appletv.launcher.GlassView { <init>(...); }
-keep class com.appletv.launcher.PetalShowerView { <init>(...); }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn **
