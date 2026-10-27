package com.bilu.paibanzhushou.wx

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.bilu.paibanzhushou.R
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram
import com.tencent.mm.opensdk.openapi.WXAPIFactory

/**
 * 微信「App 拉起小程序」封装。
 *
 * 对应微信官方文档：非多端应用 → 集成微信 SDK 后调用 `WXLaunchMiniProgram`。
 * 需要的三样东西都放在 res/values/strings.xml（改完重新打包即可，不用动代码）：
 *  - wx_app_id           微信开放平台「移动应用」的 AppID（wx 开头，注意不是小程序 AppID）
 *  - wx_miniprogram_id   目标小程序的原始 ID（gh_ 开头）
 *  - wx_miniprogram_path 目标页面路径，留空表示小程序首页
 *
 * 注意：微信开放平台上该移动应用登记的「包名 + 签名」必须与本 App 一致
 * （包名 com.bilu.paibanzhushou，签名 MD5 CD38223BF24A7D7124B5F5CFEB4AE77C），
 * 否则微信会直接拒绝请求。
 */
object WxMiniProgram {

    private const val TAG = "WxMiniProgram"

    /** 微信包名，用于 Android 11+ 的包可见性判断（manifest 里已声明 <queries>）。 */
    const val WX_PACKAGE = "com.tencent.mm"

    /** 拉起结果，调用方据此决定提示文案。 */
    sealed class Result {
        /** 请求已交给微信，正在跳转（返回值来自 WXApi.sendReq）。 */
        data object Launched : Result()

        /** 还没把真实 AppID 填进 strings.xml（仍是占位值）。 */
        data object NotConfigured : Result()

        /** 手机上没装微信。 */
        data object WeChatNotInstalled : Result()

        /** 微信拒收该请求，通常是 AppID / 包名 / 签名与开放平台登记的不一致。 */
        class Rejected(private val message: String) : Result() {
            override fun toString(): String = message
        }
    }

    /** 当前配置是否已填真实值（占位值是 wx0000… / gh_0000…）。 */
    fun isConfigured(context: Context): Boolean {
        val appId = context.getString(R.string.wx_app_id).trim()
        val userName = context.getString(R.string.wx_miniprogram_id).trim()
        return appId.startsWith("wx") && appId != "wx0000000000000000" &&
            userName.startsWith("gh_") && userName != "gh_000000000000"
    }

    fun isWeChatInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(WX_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * 拉起小程序。
     *
     * @param userName 小程序原始 ID，默认取 strings.xml 的 wx_miniprogram_id
     * @param path     页面路径（可带 query），默认取 wx_miniprogram_path
     * @param type     0 正式版 / 1 开发版 / 2 体验版，默认正式版
     */
    fun launch(
        context: Context,
        userName: String = context.getString(R.string.wx_miniprogram_id).trim(),
        path: String = context.getString(R.string.wx_miniprogram_path).trim(),
        type: Int = WXLaunchMiniProgram.Req.MINIPTOGRAM_TYPE_RELEASE,
    ): Result {
        if (!isConfigured(context)) {
            Log.w(TAG, "wx_app_id / wx_miniprogram_id 仍是占位值，未发起跳转")
            return Result.NotConfigured
        }
        if (!isWeChatInstalled(context)) return Result.WeChatNotInstalled

        val appId = context.getString(R.string.wx_app_id).trim()
        val api = WXAPIFactory.createWXAPI(context.applicationContext, appId, true)
        // 先注册一次，确保微信侧拿到本应用的 appId（部分机型/版本不注册会直接返回 has_no_permission）
        api.registerApp(appId)

        val req = WXLaunchMiniProgram.Req().apply {
            this.userName = userName
            this.path = path
            this.miniprogramType = type
        }
        val ok = api.sendReq(req)
        if (!ok) {
            Log.e(TAG, "sendReq 返回 false：AppID=$appId, userName=$userName")
            return Result.Rejected("微信拒绝了跳转请求，请检查开放平台的 AppID / 包名 / 签名")
        }
        return Result.Launched
    }

    /**
     * 用微信 URL Scheme 直接拉起小程序，不经过 SDK、也不校验调用方签名。
     *
     * 格式（参考跨时空 App 的做法）：
     * `weixin://app/{AppID}/jumpWxa/?userName={小程序原始ID}&path={页面路径}`
     * 例如：
     * `weixin://app/wxcac48d6e659ad7d7/jumpWxa/?userName=gh_4c40d7a119a2&path=page/me/pages/signIn/signIn.html`
     *
     * 适合「移动应用还没在开放平台登记 / 签名与登记不一致」时兜底。
     */
    fun launchByScheme(
        context: Context,
        appId: String = context.getString(R.string.wx_app_id).trim(),
        userName: String = context.getString(R.string.wx_miniprogram_id).trim(),
        path: String = context.getString(R.string.wx_miniprogram_path).trim(),
    ): Result {
        if (!isConfigured(context)) return Result.NotConfigured
        if (!isWeChatInstalled(context)) return Result.WeChatNotInstalled

        val url = buildString {
            append("weixin://app/").append(appId).append("/jumpWxa/?userName=").append(userName)
            if (path.isNotBlank()) append("&path=").append(path)
        }
        Log.i(TAG, "launchByScheme: " + url)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.Launched
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "没有 Activity 能处理 " + url, e)
            Result.WeChatNotInstalled
        } catch (t: Throwable) {
            Log.e(TAG, "scheme 跳转失败", t)
            Result.Rejected(t.message ?: "scheme 跳转失败")
        }
    }

    /**
     * 用「明文 URL Scheme」拉起小程序：不经过 SDK、不走开放平台、也不需要账号认证或关联。
     *
     * 格式（微信官方文档「获取 URL Scheme → 明文 URL Scheme」）：
     * `weixin://dl/business/?appid={小程序AppID}&path={页面路径}&query={参数}&env_version=release`
     *
     * 前提（小程序后台一次性开关，代码替代不了）：
     * 小程序后台 → 左下角账号 → 账号设置 → 基本设置 → 隐私与安全 → 「明文Scheme拉起此小程序」声明。
     * 另外 path 必须是**已发布版本**里真实存在的页面，且 path 里不能带 query（参数放 query）。
     *
     * 微信客户端自己注册了 weixin:// 的 Activity，本 App 用 ACTION_VIEW 打开即可，
     * 这条路不校验调用方签名，所以个人主体（未认证）账号也能用。
     */
    fun launchByPlainScheme(
        context: Context,
        appId: String = context.getString(R.string.wx_miniprogram_appid).trim(),
        path: String = context.getString(R.string.wx_miniprogram_path).trim(),
        query: String = "",
        envVersion: String = "release",
    ): Result {
        if (!appId.startsWith("wx") || appId == "wx0000000000000000") {
            Log.w(TAG, "wx_miniprogram_appid 尚未填写，跳过明文 scheme")
            return Result.NotConfigured
        }
        if (!isWeChatInstalled(context)) return Result.WeChatNotInstalled

        val pagePath = path.substringBefore('?').trim()
        val queryFromPath = path.substringAfter('?', "").trim()
        val mergedQuery = listOf(queryFromPath, query.trim())
            .filter { it.isNotBlank() }
            .joinToString("&")

        // path 按原样拼接（官方示例里就是裸路径，做百分号编码反而可能对不上）；
        // query 必须 url_encode，否则 & 会被当成参数分隔符。
        val url = buildString {
            append("weixin://dl/business/?appid=").append(appId)
            if (pagePath.isNotBlank()) append("&path=").append(pagePath)
            if (mergedQuery.isNotBlank()) append("&query=").append(Uri.encode(mergedQuery))
            append("&env_version=").append(envVersion)
        }
        Log.i(TAG, "launchByPlainScheme: " + url)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.Launched
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "没有 Activity 能处理 " + url, e)
            Result.WeChatNotInstalled
        } catch (t: Throwable) {
            Log.e(TAG, "明文 scheme 跳转失败", t)
            Result.Rejected(t.message ?: "明文 scheme 跳转失败")
        }
    }

    /** 优先用明文 scheme（不校验签名）→ 再试 jumpWxa scheme → 最后退回 SDK。 */
    fun launchBestEffort(context: Context, userName: String? = null, path: String? = null): Result {
        val plain = launchByPlainScheme(
            context,
            path = path?.takeIf { it.isNotBlank() } ?: context.getString(R.string.wx_miniprogram_path).trim(),
        )
        if (plain is Result.Launched) return plain
        val scheme = launchByScheme(
            context,
            userName = userName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.wx_miniprogram_id).trim(),
            path = path?.takeIf { it.isNotBlank() } ?: context.getString(R.string.wx_miniprogram_path).trim(),
        )
        if (scheme is Result.Launched) return scheme
        if (scheme is Result.WeChatNotInstalled || scheme is Result.NotConfigured) return scheme
        return launch(
            context,
            userName = userName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.wx_miniprogram_id).trim(),
            path = path?.takeIf { it.isNotBlank() } ?: context.getString(R.string.wx_miniprogram_path).trim(),
        )
    }

    /** 把结果转成一句可以直接 Toast 的文案。 */
    fun Result.toMessage(): String = when (this) {
        Result.Launched -> "正在打开小程序…"
        Result.NotConfigured -> "还没配置微信 AppID / 小程序 ID"
        Result.WeChatNotInstalled -> "未安装微信"
        is Result.Rejected -> toString()
    }

    /** 拉起并直接把结果 Toast 出来，供无界面入口复用。 */
    fun launchAndToast(context: Context, userName: String? = null, path: String? = null) {
        val result = try {
            launchBestEffort(context, userName = userName, path = path)
        } catch (t: Throwable) {
            Log.e(TAG, "拉起小程序异常", t)
            Toast.makeText(context, "拉起小程序失败：" + t.message, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, result.toMessage(), Toast.LENGTH_SHORT).show()
    }
}
