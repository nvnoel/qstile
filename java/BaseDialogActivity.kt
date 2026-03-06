package com.aeldy24.restile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

abstract class BaseDialogActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

  private val SHIZUKU_CODE = 1234

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setupEdgeToEdge()
  }

  private fun setupEdgeToEdge() {
    val win = window
    // true -> biarkan Android men-center dialog di dalam safe area (menghindari condong ke bawah di landscape)
    WindowCompat.setDecorFitsSystemWindows(win, true)
    win.statusBarColor = Color.TRANSPARENT
    win.navigationBarColor = Color.TRANSPARENT
    WindowCompat.getInsetsController(win, win.decorView).systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
  }

  protected fun hasWriteSecureSettings(): Boolean {
    return checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
  }

  protected fun showPermissionDialog() {
    var hasPermission = false
    try {
      if (!Shizuku.pingBinder()) {
        MaterialAlertDialogBuilder(this)
          .setTitle("Dibutuhkan Shizuku")
          .setMessage("Aplikasi ini membutuhkan Shizuku untuk berjalan pertama kali penginstalan.")
          .setPositiveButton("Tutup", android.content.DialogInterface.OnClickListener { _, _ -> finish() })
          .setOnDismissListener(android.content.DialogInterface.OnDismissListener { finish() })
          .show()
        return
      }
      hasPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
      MaterialAlertDialogBuilder(this)
        .setTitle("Dibutuhkan Shizuku")
        .setMessage("Aplikasi ini membutuhkan Shizuku untuk berjalan pertama kali penginstalan.")
        .setPositiveButton("Tutup", android.content.DialogInterface.OnClickListener { _, _ -> finish() })
        .setOnDismissListener(android.content.DialogInterface.OnDismissListener { finish() })
        .show()
      return
    }

    if (!hasPermission) {
      MaterialAlertDialogBuilder(this)
        .setTitle("Dibutuhkan Shizuku")
        .setMessage("Aplikasi ini membutuhkan Shizuku untuk berjalan pertama kali penginstalan.")
        .setPositiveButton("Izinkan", android.content.DialogInterface.OnClickListener { _, _ ->
          Shizuku.addRequestPermissionResultListener(this)
          Shizuku.requestPermission(SHIZUKU_CODE)
        })
        .setNegativeButton("Tutup", android.content.DialogInterface.OnClickListener { _, _ -> finish() })
        .setOnDismissListener(android.content.DialogInterface.OnDismissListener { finish() })
        .show()
      return
    }

    try {
      val pmBinder = SystemServiceHelper.getSystemService("package")
      val shizukuBinder = ShizukuBinderWrapper(pmBinder)
      val ipmClass = Class.forName("android.content.pm.IPackageManager\$Stub")
      val ipm = ipmClass.getDeclaredMethod("asInterface", android.os.IBinder::class.java).invoke(null, shizukuBinder)

      val grantMethod = ipm.javaClass.getMethod(
        "grantRuntimePermission", String::class.java, String::class.java, Int::class.javaPrimitiveType
      )
      grantMethod.invoke(ipm, "com.aeldy24.restile", "android.permission.WRITE_SECURE_SETTINGS", 0) // 0 is user id

      // Jika dipanggil kembali ke sini namun sudah grant, tutup dan anggap sukses
      finish()
    } catch (e: Throwable) {
      MaterialAlertDialogBuilder(this)
        .setTitle("Gagal")
        .setMessage("Gagal memberikan izin via Shizuku: ${e.message}")
        .setPositiveButton("Tutup", android.content.DialogInterface.OnClickListener { _, _ -> finish() })
        .setOnDismissListener(android.content.DialogInterface.OnDismissListener { finish() })
        .show()
    }
  }

  override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
    if (requestCode == SHIZUKU_CODE) {
      Shizuku.removeRequestPermissionResultListener(this)
      if (grantResult == PackageManager.PERMISSION_GRANTED) {
        try {
          val pmBinder = SystemServiceHelper.getSystemService("package")
          val shizukuBinder = ShizukuBinderWrapper(pmBinder)
          val ipmClass = Class.forName("android.content.pm.IPackageManager\$Stub")
          val ipm = ipmClass.getDeclaredMethod("asInterface", android.os.IBinder::class.java).invoke(null, shizukuBinder)
          val grantMethod = ipm.javaClass.getMethod(
            "grantRuntimePermission", String::class.java, String::class.java, Int::class.javaPrimitiveType
          )
          grantMethod.invoke(ipm, "com.aeldy24.restile", "android.permission.WRITE_SECURE_SETTINGS", 0)

          Toast.makeText(this, "Izin diberikan, fitur terbuka.", Toast.LENGTH_SHORT).show()
          recreate() // Reload activity untuk melanjutkan flow normal
        } catch (e: Throwable) {
          Toast.makeText(this, "Gagal memberikan izin via Shizuku: ${e.message}", Toast.LENGTH_LONG).show()
          finish()
        }
      } else {
        finish()
      }
    }
  }

  protected fun android.view.Window.applyDialogWindowStyle() {
    setBackgroundDrawableResource(android.R.color.transparent)
    val density  = context.resources.displayMetrics.density
    val maxWidth = minOf(
      (DIALOG_MAX_DP * density).toInt(),
      context.resources.displayMetrics.widthPixels
    )
    setLayout(maxWidth, WindowManager.LayoutParams.WRAP_CONTENT)
    setGravity(Gravity.CENTER)
    attributes = attributes.apply {
      dimAmount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.2f else 0.6f
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurBehindRadius = BLUR_RADIUS
    }
    addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }
  }

  protected fun vibrateClick() = VibrationHelper.click(this)
  protected fun vibrateTick()  = VibrationHelper.tick(this)

  companion object {
    private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
    private const val DIALOG_MAX_DP = 320
    private const val BLUR_RADIUS   = 40
  }
}
