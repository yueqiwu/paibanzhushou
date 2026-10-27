package com.bilu.paibanzhushou.wx

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram

/**
 * 无界面的「拉起小程序」入口。
 *
 * 三种触发方式（都不需要改动首页 UI）：
 *  1. 代码：`WxMiniProgram.launch(context)` 或 `WxMiniProgram.launchAndToast(context)`
 *  2. Deeplink / 其他应用 / 网页：
 *     `paibanzhushou://miniprogram?path=pages/index/index&type=0`
 *  3. adb 调试：
 *     `adb shell am start -a android.intent.action.VIEW -d "paibanzhushou://miniprogram?path=pages/index/index"`
 *
 * 参数（query string 或 intent extra 都支持）：
 *  - path      小程序页面路径，可带 query；缺省用 strings.xml 的 wx_miniprogram_path
 *  - userName  小程序原始 ID；缺省用 strings.xml 的 wx_miniprogram_id
 *  - type      0 正式版（默认）/ 1 开发版 / 2 体验版
 */
class WxLaunchMiniProgramActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data: Uri? = intent?.data
        val path = data?.getQueryParameter("path") ?: intent?.getStringExtra(EXTRA_PATH)
        val userName = data?.getQueryParameter("userName") ?: intent?.getStringExtra(EXTRA_USER_NAME)
        val type = (data?.getQueryParameter("type") ?: intent?.getStringExtra(EXTRA_TYPE))
            ?.toIntOrNull() ?: WXLaunchMiniProgram.Req.MINIPTOGRAM_TYPE_RELEASE

        val mode = data?.getQueryParameter("mode") ?: intent?.getStringExtra(EXTRA_MODE)

        Log.i(TAG, "launch mini program: mode=" + mode + ", path=" + path + ", userName=" + userName + ", type=" + type)
        if (mode == "sdk") {
            // 强制走官方 SDK（会校验开放平台登记的包名 + 签名）
            val uid = userName?.takeIf { it.isNotBlank() } ?: getString(com.bilu.paibanzhushou.R.string.wx_miniprogram_id).trim()
            val uPath = path?.takeIf { it.isNotBlank() } ?: getString(com.bilu.paibanzhushou.R.string.wx_miniprogram_path).trim()
            val r = WxMiniProgram.launch(this, userName = uid, path = uPath, type = type)
            android.widget.Toast.makeText(this, "SDK: " + WxMiniProgram.run { r.toMessage() }, android.widget.Toast.LENGTH_SHORT).show()
        } else if (mode == "scheme") {
            val r = WxMiniProgram.launchByScheme(this)
            android.widget.Toast.makeText(this, "Scheme: " + WxMiniProgram.run { r.toMessage() }, android.widget.Toast.LENGTH_SHORT).show()
        } else {
            WxMiniProgram.launchAndToast(this, userName = userName, path = path)
        }
        finish()
    }

    companion object {
        private const val TAG = "WxLaunchMiniProgram"
        const val EXTRA_PATH = "wx_path"
        const val EXTRA_USER_NAME = "wx_user_name"
        const val EXTRA_TYPE = "wx_type"
        const val EXTRA_MODE = "wx_mode"
    }
}
