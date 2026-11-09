# JavaMail (android-mail + gimap) loads providers and handlers reflectively.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.activation.** { *; }
-keep class myjava.awt.datatransfer.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**

# SQLCipher native bindings.
-keep class net.zetetic.database.** { *; }

# PdfBox-Android uses reflection for fonts and filters.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter
-dontwarn com.gemalto.jp2.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** { *** Companion; *; }
