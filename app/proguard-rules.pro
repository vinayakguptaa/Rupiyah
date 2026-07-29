-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# JavaMail discovers the IMAPS provider from META-INF resources at runtime.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
