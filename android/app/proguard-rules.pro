# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepnames class * {
#    public protected *;
#}

# Keep Room entities, DAOs and database (only needed if minify is enabled).
-keep class com.example.data.local.** { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.Database class *
-dontwarn androidx.room.**

# --- PDFBox (PDF engine) ---
# JPX/JPEG-2000 images inside a PDF need an optional codec that is not on the
# classpath. PDFBox degrades gracefully: those images are skipped, everything
# else on the page (all the vector text, which is what these papers are made of)
# still renders and still searches. Without this rule R8 fails the build.
-dontwarn com.gemalto.jp2.**

# PDFBox resolves font files and rendering classes at runtime.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**
