# Retrofit / Gson model reflection
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.rudradigital.rudrastaylink.data.** { *; }
-keep interface retrofit2.** { *; }
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
