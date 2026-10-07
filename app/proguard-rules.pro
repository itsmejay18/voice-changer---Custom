# VICE CHANGER - R8 rules
# The app has no reflection-based lookups and no native code. Keep the entry point
# and the Compose runtime contract safe if minification is ever switched on.
-keep class com.vicechanger.app.MainActivity { *; }
-keepclassmembers class com.vicechanger.app.settings.SettingsManager { *; }
-dontwarn org.jetbrains.annotations.**
