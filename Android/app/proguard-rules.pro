# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.jiny.catchtherule.** {
    *** Companion;
}
-keepclasseswithmembers class com.jiny.catchtherule.** {
    kotlinx.serialization.KSerializer serializer(...);
}
