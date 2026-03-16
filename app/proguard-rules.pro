-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

# KEEP Kotlin Serialization
-keep class kotlinx.serialization.** { *; }

# Keep all @Serializable classes
-keepnames @kotlinx.serialization.Serializable class *

# Keep their members
-keepclassmembers @kotlinx.serialization.Serializable class * {
    <init>(...);
}

# Keep generated serializer classes
-keep class **$$serializer { *; }

# Keep Navigation Compose serializers
-keep class * implements kotlinx.serialization.KSerializer { *; }