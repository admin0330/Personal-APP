package com.masteralanlab.emailbox.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.masteralanlab.emailbox.R
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class IconInspectTest {

    private fun drawableToBitmap(drawable: Drawable, width: Int = 300, height: Int = 300): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun saveBitmap(bitmap: Bitmap, dir: File, filename: String) {
        val file = File(dir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        println("Saved ${file.absolutePath} (${file.length()} bytes)")
    }

    @Test
    fun dumpPackageManagerIcons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        val pkg = context.packageName
        val outDir = context.getExternalFilesDir(null) ?: context.filesDir

        println("Dumping icons for pkg: $pkg to ${outDir.absolutePath}")

        // 1. Application icon
        val appIcon = pm.getApplicationIcon(pkg)
        println("appIcon type: ${appIcon::class.java.name}")
        if (appIcon is AdaptiveIconDrawable) {
            println("appIcon background: ${appIcon.background::class.java.name}")
            println("appIcon foreground: ${appIcon.foreground::class.java.name}")
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                println("appIcon monochrome: ${appIcon.monochrome?.javaClass?.name}")
            }
        }
        saveBitmap(drawableToBitmap(appIcon), outDir, "inspect_app_icon.png")

        // 2. Activity Alias icon
        val aliasCmp = ComponentName(pkg, "com.masteralanlab.emailbox.MainActivityAlias")
        val aliasIcon = pm.getActivityIcon(aliasCmp)
        println("aliasIcon type: ${aliasIcon::class.java.name}")
        saveBitmap(drawableToBitmap(aliasIcon), outDir, "inspect_alias_icon.png")

        // 3. MainActivity icon
        val mainCmp = ComponentName(pkg, "com.masteralanlab.emailbox.MainActivity")
        val mainIcon = pm.getActivityIcon(mainCmp)
        println("mainIcon type: ${mainIcon::class.java.name}")
        saveBitmap(drawableToBitmap(mainIcon), outDir, "inspect_main_icon.png")

        // 4. Resources ic_launcher
        val resLauncher = context.resources.getDrawable(R.mipmap.ic_launcher, null)
        println("resLauncher type: ${resLauncher::class.java.name}")
        saveBitmap(drawableToBitmap(resLauncher), outDir, "inspect_res_launcher.png")

        // 5. Resources ic_launcher_round
        val resRound = context.resources.getDrawable(R.mipmap.ic_launcher_round, null)
        println("resRound type: ${resRound::class.java.name}")
        saveBitmap(drawableToBitmap(resRound), outDir, "inspect_res_round.png")
    }
}
