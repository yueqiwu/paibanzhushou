import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 正式包签名：keystore.properties 不入库（.gitignore 已忽略），内含 storeFile / 密码 / alias
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        FileInputStream(keystorePropsFile).use { load(it) }
    }
}

android {
    namespace = "com.bilu.paibanzhushou"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.bilu.paibanzhushou"
        minSdk = 24
        targetSdk = 37
        // 1.0.3/102 对齐源项目；1.0.4 日历不限年份；1.0.5 改名「班表小历」；
        // 1.0.6 修节假日数据源：某年接口返回空对象时不再吞掉备源（2027 年假/班标记）；
        // 1.0.7 修班次闹钟的响铃时间选择：Dialog 里的滚轮一拖就被判成点到外面而关掉，改为应用内浮层
        // 1.0.8 到点改成真正的全屏响铃页（循环闹铃音 + 震动 + 关闭/稍后提醒），并加常驻前台服务
        // 1.0.9 修后台/锁屏不响：响铃改由前台服务直接放音（全屏页会被 BAL 拦掉），并检测「全屏通知」权限
        // 1.0.10 响铃时按电源键（屏幕状态切换）即停铃；通知动作改走 Service 并补 Wearable 动作，手表也能关
        // 1.0.11 设置页加「闹钟权限」清单：逐项显示通知/精确闹钟/后台弹出界面/全屏通知是否开启，缺的可一键跳系统设置
        // 1.0.12 新增「闹钟铃声」：可选系统默认 / 静音（只震动）/ 任意系统闹钟铃声，带试听
        // 1.0.17 首屏压缩：状态栏留白只留 inset、标题/设置卡收紧、图例改单行横滑、日历行高 70→56dp，开屏即可看完整月历
    // 1.0.16 2×1 小卡改为「4 上 3 下」两行，每格显示日号圆圈 + 班次名
    // 1.0.15 新增 2×1 小号小组件（日号圆圈版），4×2 大卡保持不变
    // 1.0.14 桌面小组件重新设计（2:1 卡片、7 天横排、圆角药丸、今天高亮）
    // 1.0.13 响铃时额外发一条「非常驻」通知，供 ColorOS 手表（OPPO Watch）镜像显示；划掉即关闭闹钟
        versionCode = 117
        versionName = "1.0.17"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // debug 也用正式签名库：微信开放平台是按「包名 + 签名」校验调用方的，
        // 用 debug.keystore 会被微信拒绝（应用包名签名信息校验不通过）。
        debug {
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            // 关闭 R8 优化：AGP 的 packageScope 优化会在 1.0.4 上触发
            // kotlin.collections 的 IllegalAccessError（Class kotlin.collections.f extended
            // by class jg is inaccessible），正式包以稳定为先，保持不混淆。
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // 微信开放 SDK：仅用于「App 拉起微信小程序」（WXLaunchMiniProgram）
    implementation("com.tencent.mm.opensdk:wechat-sdk-android:6.8.40")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}