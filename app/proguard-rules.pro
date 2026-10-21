# CloudburstMC Protocol & Netty rules
-keep class org.cloudburstmc.protocol.** { *; }
-dontwarn org.cloudburstmc.protocol.**

-keep class io.netty.** { *; }
-dontwarn io.netty.**

# Gson rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.aayaan.agclient.config.** { *; }
-keep class com.aayaan.agclient.gui.models.** { *; }

# Coroutines rules
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
