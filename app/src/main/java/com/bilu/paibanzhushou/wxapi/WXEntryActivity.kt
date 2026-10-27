package com.bilu.paibanzhushou.wxapi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.tencent.mm.opensdk.constants.ConstantsAPI
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import com.tencent.mm.opensdk.openapi.WXAPIFactory

/**
 * 微信回调页。类名与路径必须严格是 `<applicationId>.wxapi.WXEntryActivity`，
 * 否则微信无法回调（拉起的响应、以及未来接入登录 / 分享 / 支付都要靠它）。
 *
 * 这里只处理「拉起小程序」的响应，并把小程序通过
 * `<button open-type="launchApp" app-parameter="...">` 回传的 extMsg 打日志。
 */
class WXEntryActivity : Activity(), IWXAPIEventHandler {

    private lateinit var api: IWXAPI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = WXAPIFactory.createWXAPI(this, getString(com.bilu.paibanzhushou.R.string.wx_app_id).trim(), false)
        // 兼容微信通过 onNewIntent 派发的情况
        try {
            api.handleIntent(intent, this)
        } catch (t: Throwable) {
            Log.e(TAG, "handleIntent 失败", t)
        }
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        try {
            api.handleIntent(intent, this)
        } catch (t: Throwable) {
            Log.e(TAG, "onNewIntent handleIntent 失败", t)
        }
    }

    override fun onReq(req: BaseReq?) {
        Log.i(TAG, "onReq: " + req?.type)
    }

    override fun onResp(resp: BaseResp?) {
        if (resp == null) return
        Log.i(TAG, "onResp: type=" + resp.type + ", errCode=" + resp.errCode + ", errStr=" + resp.errStr)
        if (resp.type == ConstantsAPI.COMMAND_LAUNCH_WX_MINIPROGRAM) {
            val launchResp = resp as? WXLaunchMiniProgram.Resp
            val extra = launchResp?.extMsg
            Log.i(TAG, "小程序回传参数 extMsg=" + extra)
            if (resp.errCode == BaseResp.ErrCode.ERR_OK) {
                if (!extra.isNullOrBlank()) {
                    Toast.makeText(this, "小程序回传：" + extra, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "小程序返回失败：" + resp.errCode, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val TAG = "WXEntryActivity"
    }
}
