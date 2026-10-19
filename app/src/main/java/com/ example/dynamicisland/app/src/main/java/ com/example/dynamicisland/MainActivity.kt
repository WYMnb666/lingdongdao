package com.example.dynamicisland

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 200, 60, 60)
            setBackgroundColor(Color.BLACK)
        }

        val hint = TextView(this).apply {
            text = "开启权限后启动灵动岛"
            textSize = 18f
            setTextColor(Color.WHITE)
        }
        root.addView(hint)

        root.addView(btn("1. 授予悬浮窗权限") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        })

        root.addView(btn("2. 授予通知使用权") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        root.addView(btn("3. 启动灵动岛") {
            if (!Settings.canDrawOverlays(this)) {
                toast("请先授予悬浮窗权限")
                return@btn
            }
            if (!isNotifListenerEnabled()) {
                toast("请先授予通知使用权")
                return@btn
            }
            startForegroundService(Intent(this, IslandService::class.java))
            toast("灵动岛已启动，去酷狗播放音乐试试")
        })

        setContentView(root)
    }

    private fun btn(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            setText(text)
            setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 24
            layoutParams = lp
        }

    private fun isNotifListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
