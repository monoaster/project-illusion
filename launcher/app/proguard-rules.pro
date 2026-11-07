# Keep names readable in crash logs; the size win comes from dropping unused code, not renaming.
-dontobfuscate
# The Home page calls these by name through the WebView bridge (HomeActivity.Bridge, DialService.Bridge).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
