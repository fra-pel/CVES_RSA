########################################
# 1. Librerie crittografiche e Keystore
########################################
-keep class net.sqlcipher.** { *; }
-keep class androidx.security.crypto.** { *; }
-keep class javax.crypto.** { *; }
-keep class android.security.keystore.** { *; }

########################################
# 2. Classi/metodi usati via reflection
########################################
-keepclassmembers class * {

}
-keepattributes Signature, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations
-keepattributes SourceFile, LineNumberTable

########################################
# 3. String Obfuscator
########################################
-keep class com.uvarara.quiz.security.StrObf { *; }

########################################
# 4. Ottimizzazione aggressiva
########################################
-optimizationpasses 5
-overloadaggressively
-useuniqueclassmembernames
