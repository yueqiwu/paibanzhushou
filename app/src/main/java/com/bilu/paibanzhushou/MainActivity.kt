package com.bilu.paibanzhushou

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bilu.paibanzhushou.data.Migrate
import com.bilu.paibanzhushou.data.Store
import com.bilu.paibanzhushou.data.UsageStats
import com.bilu.paibanzhushou.ui.AppRoot
import com.bilu.paibanzhushou.ui.theme.PaibanzhushouTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        // 旧版唤起的迁移深链：先记下来，Compose 首帧后弹确认框
        Migrate.offerLink(Migrate.linkFromUri(intent?.data))
        UsageStats.ping(this)   // 匿名使用统计：只在后台网页看，界面无任何痕迹
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            PaibanzhushouTheme {
                AppRoot()
            }
        }
    }

    /** singleTask 下再次被深链唤起不会重建 Activity，链接要在这里接 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Migrate.offerLink(Migrate.linkFromUri(intent.data))
    }
}
