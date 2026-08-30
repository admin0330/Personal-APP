# Emailbox Android 混淆规则
#
# 下面两条是「不加就整个 App 跑不起来」的级别，改动前请先看发布流程里的校验步骤：
# 1. Retrofit 的方法注解（@GET/@POST/@Query/...）一旦被剥离，运行到任何接口都会抛异常。
# 2. kotlinx.serialization 依赖编译期生成的 $$serializer，被移除就无法解析响应。

-keepattributes *Annotation*, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, AnnotationDefault, Signature, InnerClasses, EnclosingMethod

# 保留带 Retrofit 注解的接口方法（Retrofit 用动态代理 + 反射读注解）
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# 整包保留 retrofit2 与 okhttp3：R8 默认会重命名库类并剥离方法上的注解，
# 注解一旦丢失，Retrofit 第一次请求就会抛「HTTP method annotation is required」。
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# 数据模型与序列化器
-keep class com.masteralanlab.emailbox.data.remote.** { *; }
-keep class com.masteralanlab.emailbox.update.UpdateInfo { *; }
-keepclassmembers class com.masteralanlab.emailbox.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.masteralanlab.emailbox.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.masteralanlab.emailbox.data.remote.**$$serializer { *; }

# OkHttp / Retrofit 平台相关反射
-dontwarn retrofit2.**
-dontwarn retrofit2.Platform
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn kotlinx.serialization.**
-dontwarn com.jakewharton.retrofit2.converter.kotlinx.serialization.**
