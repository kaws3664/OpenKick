-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-keep interface com.gallbladderz.openkick.core.network.api.** { *; }

-keepattributes *Annotation*
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    static kotlinx.serialization.KSerializer serializer(...);
}


-dontwarn org.koin.**

# kotlinx.serialization 官方推荐规则（R8 下必需，否则启动闪退）
-keepclassmembers class com.gallbladderz.openkick.** {
    *** Companion;
}
-keepclasseswithmembers class com.gallbladderz.openkick.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gallbladderz.openkick.**$$serializer { *; }
-keepclassmembers class com.gallbladderz.openkick.** {
    *** Companion;
}

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
