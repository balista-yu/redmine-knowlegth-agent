// =============================================================================
// settings.gradle.kts: Gradle プロジェクト構成 + Java toolchain auto-provisioning
// =============================================================================
// foojay-resolver-convention: Java toolchain (jvmToolchain(25)) のバイナリを
//   Foojay Disco API 経由で自動ダウンロードする。ローカルに JDK 25 が無くても
//   gradle が必要な版をキャッシュしてビルドに使う。
// =============================================================================
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "redmine-knowledge-agent"
