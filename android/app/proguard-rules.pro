# LiteRT (TFLite) uses JNI and reflection on its Java API.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
