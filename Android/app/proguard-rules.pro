# ──────────────────────────────────────────────
# kotlinx.serialization (Puzzle / 랭킹 / 문의 / 통계 모델 JSON 디코딩)
# ──────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.**

# 생성된 직렬화기($$serializer) 유지
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.jiny.catchtherule.**$$serializer { *; }

# @Serializable 클래스의 Companion / serializer() 접근자 유지
-keepclassmembers class com.jiny.catchtherule.** {
    *** Companion;
}
-keepclasseswithmembers class com.jiny.catchtherule.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable 모델 클래스와 필드 보존(난독화로 JSON 매핑 깨짐 방지)
-keep @kotlinx.serialization.Serializable class com.jiny.catchtherule.** { *; }

# ──────────────────────────────────────────────
# Jetpack Compose / Kotlin — AGP 기본 R8 구성으로 충분(추가 규칙 불필요)
# ──────────────────────────────────────────────
