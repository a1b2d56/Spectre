package com.spectre.app.core.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.spectre.app.MainActivity

class GeneratorTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (isSecure) {
            unlockAndRun {
                startMainActivity()
            }
        } else {
            startMainActivity()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("open_page", "generator")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getActivity(
                this,
                4321,
                intent,
                flags,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
