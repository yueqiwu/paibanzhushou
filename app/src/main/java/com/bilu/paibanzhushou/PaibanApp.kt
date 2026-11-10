package com.bilu.paibanzhushou

import android.app.Application
import com.bilu.paibanzhushou.data.Store

/**
 * 只做一件事：把本地存储初始化提前到进程启动时。
 * 闹钟/重启补写这类广播会拉起没有界面的进程，[com.bilu.paibanzhushou.MainActivity] 不会执行，
 * 之前把 Store.init 放在 Activity 里会导致广播里读不到任何设置。
 */
class PaibanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
