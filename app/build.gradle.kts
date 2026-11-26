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
        // 1.0.19 日历行高按屏幕高度自适应（800dp 屏约 63dp/行），把底部空白还给日历
    // 1.0.18 版权与版本合成一行（原三行文字太占高度），其余同 1.0.17
    // 1.0.17 首屏压缩：状态栏留白只留 inset、标题/设置卡收紧、图例改单行横滑、日历行高 70→56dp，开屏即可看完整月历
    // 1.0.16 2×1 小卡改为「4 上 3 下」两行，每格显示日号圆圈 + 班次名
    // 1.0.15 新增 2×1 小号小组件（日号圆圈版），4×2 大卡保持不变
    // 1.0.14 桌面小组件重新设计（2:1 卡片、7 天横排、圆角药丸、今天高亮）
    // 1.0.13 响铃时额外发一条「非常驻」通知，供 ColorOS 手表（OPPO Watch）镜像显示；划掉即关闭闹钟
        // 1.0.20 闹钟设置/权限独立成页（首页顶部闹钟按钮进入）、全页左右滑动切人、锁竖屏、日历行高再按 6.1 英寸校准
    // 1.0.21 日历红框区（标题/图例/月份栏/星期栏）也能左右滑动切人；人员标签跟随滑动进度高亮并自动居中；
    //        切人加淡入位移动画；闹钟铃声改为跳系统设置；新增「勿扰 / 睡眠模式例外」权限项；更新内容完整显示 + 下载源测速
    // 1.0.22 切人动画只保留日历滑入（去掉上两栏淡入）；闹钟铃声恢复 App 内自选；删掉点进去也没开关的
    //        「勿扰 / 睡眠模式例外」权限项，改成文字说明；对比同事排班 / 闹钟设置页补上左右滑入的页面转场
    // 1.0.24 修「首次安装后新增人员就滑不动、重启才恢复」：滑动手势的判断改为每次按下都读实时状态
    // 1.0.25 日期选择从三列滚轮改为日历格子（自绘）
    // 1.0.26 日期 / 月份 / 闹钟时间都换成 Android 系统原生弹窗（DatePickerDialog / TimePickerDialog）
    // 1.0.27 修两个闹钟不跟排班走的问题：单日改成「休息」后旧闹钟撤不掉；闹钟页改完时间直接退出不生效
    // 1.0.28 华为/荣耀后台不响：加电池优化白名单、自启动/后台运行跳转，铃声服务起来失败时退回通知兜底
    // 1.0.29 人员列表左右滑不再切人；切人去掉了 alpha/缩放过渡（荣耀上抖一下）；新增 2×1「今日班次」桌面卡片
    // 1.0.30 2×1 卡片改成「所有人员今天的班次」一人一列；切人时等主层换完再归位（荣耀上标签闪一下）
    // 1.0.31 班次图例可左右滑动（班次多时不再被裁掉），图例区不再触发整页切人
    // 1.0.32 新增匿名使用统计上报（每天首次打开上报一次随机安装标识 + 日期 + 版本号），只在后台网页看，App 内无任何入口
    // 1.0.33 从旧版（uni-app）接收迁移数据：深链 + 剪贴板两条入口；导入前留快照可撤销；
    //        引导只在原生版还没数据时出现，点「稍后」不再每次冷启动重弹
    // 1.0.34 导入旧版数据后闹钟页「为谁写闹钟」会一个都不高亮：绑定人员失效时自动落回当前人员；
    //        开闹钟开关前先校验人员；「悬浮窗」与厂商的「后台弹出界面」拆成两项，后者标为需手动确认
    // 1.0.35 点「自启动」跳不过去时（一加/OPPO 把该页锁给系统「设置」）弹说明写清本机路径，
    //        不再静默跳到应用详情；顺带补上 ColorOS / realme 的自启动组件
    // 1.0.36 修 1.0.35 在正常分辨率上日历只占半屏、下方大片空白：行高上下限误写成 rpx（上限只有 32dp），
    //        改为真实 dp（30–110dp），正常机行高回到约 60dp，5.5 英寸仍一屏
    // 1.0.37 闹钟权限新增「锁屏显示」一项（ColorOS 把它放在隐私 → 全部权限里），
    //        跳不过去时弹说明写清本机路径；顺带把「全屏通知」的说明和它区分开
        versionCode = 137
        versionName = "1.0.37"

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