-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# pdfbox-android: JPEG 2000 decoding is an optional add-on (com.gemalto.jp2) we don't ship.
-dontwarn com.gemalto.jp2.**

# JavaMail discovers the IMAPS provider from META-INF resources at runtime.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
