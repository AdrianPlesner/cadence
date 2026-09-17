# kotlinx.serialization: keep generated serializers and the Companion.serializer() lookups for the app's own models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dk.azp.cadence.**$$serializer { *; }
-keepclassmembers class dk.azp.cadence.** {
    *** Companion;
}
-keepclasseswithmembers class dk.azp.cadence.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor's engines are looked up reflectively.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
