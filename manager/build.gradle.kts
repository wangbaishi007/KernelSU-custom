plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
}

val androidMinSdkVersion by extra(31)
val androidTargetSdkVersion by extra(37)
val androidCompileSdkVersion by extra(37)
val androidCompileSdkVersionMinor by extra(0)
val androidBuildToolsVersion by extra("37.0.0")
val androidCompileNdkVersion: String by extra(libs.versions.ndk.get())
val androidSourceCompatibility by extra(JavaVersion.VERSION_21)
val androidTargetCompatibility by extra(JavaVersion.VERSION_21)

val MANAGER_VERSION_NAME_FALLBACK = "0.0.0-local"

val managerVersionCode by extra(getVersionCode())
val managerVersionName by extra(getVersionName())

fun getGitCommitCount(): Int {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("git", "rev-list", "--count", "HEAD"))
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        val exit = process.waitFor()
        if (exit != 0) {
            0
        } else {
            output.toIntOrNull() ?: 0
        }
    } catch (_: Exception) {
        0
    }
}

fun getGitDescribe(): String {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("git", "describe", "--tags", "--always"))
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        val exit = process.waitFor()
        if (exit != 0 || output.isEmpty()) {
            MANAGER_VERSION_NAME_FALLBACK
        } else {
            output
        }
    } catch (_: Exception) {
        MANAGER_VERSION_NAME_FALLBACK
    }
}

fun getVersionCode(): Int {
    val commitCount = getGitCommitCount()
    return 30000 + commitCount
}

fun getVersionName(): String {
    return getGitDescribe()
}
