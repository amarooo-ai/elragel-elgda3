package com.example.hourclerk

import android.Manifest
import android.animation.ObjectAnimator
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

// ───────────── الشاشة الرئيسية: بتفتحلك الأذونات واحد ورا التاني ─────────────
class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = 20f; gravity = Gravity.CENTER }
        val test = Button(this).apply {
            text = "جرّب الموظف (30 ثانية)"
            setOnClickListener {
                if (Settings.canDrawOverlays(context))
                    startForegroundService(Intent(context, ClerkService::class.java).putExtra("wait", 30000L))
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48); addView(status); addView(test)
        })
    }

    override fun onResume() {
        super.onResume()
        val pkg = Uri.parse("package:$packageName")
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        if (!Settings.canDrawOverlays(this)) {
            status.text = "فعّل «العرض فوق التطبيقات الأخرى»"
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)); return
        }
        val am = getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            status.text = "فعّل «المنبهات والتذكيرات»"
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)); return
        }
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("askedNotif", false) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("askedNotif", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1); return
        }
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName) && !prefs.getBoolean("askedBattery", false)) {
            prefs.edit().putBoolean("askedBattery", true).apply()
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)); return
        }
        HourReceiver.schedule(this)
        status.text = "جاهز ✅\nالموظف هيجيب كرسي في الدقيقة 50 من كل ساعة"
    }
}

// ───────────── المنبه: الساعة :50 من كل ساعة ─────────────
class HourReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        schedule(c)
        if (i.action == Intent.ACTION_BOOT_COMPLETED) return
        if (Settings.canDrawOverlays(c)) c.startForegroundService(Intent(c, ClerkService::class.java))
    }

    companion object {
        fun schedule(c: Context) {
            val am = c.getSystemService(AlarmManager::class.java)
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return
            val t = Calendar.getInstance().apply {
                set(Calendar.MINUTE, 50); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.HOUR_OF_DAY, 1)
            }.timeInMillis
            val pi = PendingIntent.getBroadcast(
                c, 0, Intent(c, HourReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }
}

// ───────────── الموظف: بيجيب كرسي، يقعد، يبص في ساعته، وعلى الساعة يظبطها ─────────────
class ClerkService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var box: View? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (box != null) return START_NOT_STICKY
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("clerk", "الموظف", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "clerk")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("الموظف مستني الساعة").build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }

        val wait = i?.getLongExtra("wait", -1L) ?: -1L
        val toHour = if (wait > 0) wait else Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis - System.currentTimeMillis()
        show(maxOf(toHour, 5000L))
        return START_NOT_STICKY
    }

    private fun show(toHour: Long) {
        wm = getSystemService(WindowManager::class.java)
        val bubble = TextView(this).apply {
            text = "⌚ 🤨"; textSize = 16f; setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE); setPadding(24, 12, 24, 12); visibility = View.INVISIBLE
        }
        val chair = TextView(this).apply { text = "🪑"; textSize = 56f }
        val guy = TextView(this).apply { text = "🧑‍💼"; textSize = 56f; translationX = -130f }
        val frame = FrameLayout(this).apply {
            addView(chair, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.RIGHT))
            addView(guy, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.RIGHT))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_HORIZONTAL; translationX = -700f
            addView(bubble); addView(frame)
        }
        box = root
        val lp = WindowManager.LayoutParams(
            WRAP_CONTENT, WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.LEFT; y = 40 }
        wm?.addView(root, lp)

        // 1) يدخل شايل الكرسي
        root.animate().translationX(0f).setDuration(1000).start()
        // 2) يقعد عليه
        h.postDelayed({ guy.animate().translationX(0f).translationY(-12f).setDuration(500).start() }, 1500)
        // 3) كل شوية يبص في ساعة إيده
        val glance = object : Runnable {
            override fun run() {
                bubble.visibility = View.VISIBLE
                guy.animate().rotation(-10f).setDuration(300).start()
                h.postDelayed({
                    bubble.visibility = View.INVISIBLE
                    guy.animate().rotation(0f).setDuration(300).start()
                }, 2000)
                h.postDelayed(this, 6000)
            }
        }
        h.postDelayed(glance, 3500)
        // 4) على الساعة: يقوم، يظبطها، يقول «أي خدمة؟»، ويمشي
        h.postDelayed({
            h.removeCallbacksAndMessages(null)
            bubble.text = "أي خدمة؟"; bubble.visibility = View.VISIBLE
            guy.animate().translationX(-130f).translationY(0f).rotation(0f).setDuration(500).start()
            ObjectAnimator.ofFloat(guy, "rotation", -12f, 12f).apply {
                duration = 300; repeatCount = 8; repeatMode = ObjectAnimator.REVERSE; startDelay = 600; start()
            }
            h.postDelayed({
                root.animate().translationX(-700f).setDuration(600).withEndAction { stopSelf() }.start()
            }, 3800)
        }, toHour)
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        box?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        box = null
        super.onDestroy()
    }
}
