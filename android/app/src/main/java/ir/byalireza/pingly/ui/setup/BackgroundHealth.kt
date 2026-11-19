package ir.byalireza.pingly.ui.setup

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import ir.byalireza.pingly.service.PinglyService

/** One line of the "keep Pingly awake" checklist. */
data class HealthItem(
    val key: Key,
    val emoji: String,
    val title: String,
    val description: String,
    val ok: Boolean,
    /** Without it partner actions can be missed; others make things nicer or faster. */
    val essential: Boolean,
) {
    enum class Key { Notifications, Battery, FullScreen, Overlay, ExactAlarms, Service, Autostart }
}

/** Inspects every Android setting that affects background delivery and opens the right screen. */
object BackgroundHealth {
    private val aggressiveOems = setOf(
        "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "vivo", "iqoo", "oneplus",
        "samsung", "asus", "meizu", "tecno", "infinix", "itel", "lenovo", "nokia",
    )

    val manufacturer: String get() = Build.MANUFACTURER.lowercase()
    val hasAutostartManager: Boolean get() = manufacturer in aggressiveOems

    fun notificationsAllowed(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun fullScreenAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    fun overlayAllowed(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun exactAlarmsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    fun items(context: Context, serviceShouldRun: Boolean, autostartConfirmed: Boolean): List<HealthItem> = buildList {
        add(
            HealthItem(
                HealthItem.Key.Notifications, "🔔", "اجازه‌ی اعلان",
                "تا پینگ‌ها، پیام‌ها و اعلان همیشگی پینگلی نمایش داده بشن.",
                notificationsAllowed(context), essential = true,
            ),
        )
        add(
            HealthItem(
                HealthItem.Key.Battery, "🔋", "بدون محدودیت باتری",
                "مهم‌ترین مورد! تا اندروید پینگلی رو برای صرفه‌جویی در باتری نخوابونه.",
                batteryUnrestricted(context), essential = true,
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(
                HealthItem(
                    HealthItem.Key.FullScreen, "🖼️", "نمایش تمام‌صفحه",
                    "تا عکس‌ها و پیام‌های عاشقانه حتی روی صفحه‌ی قفل باز بشن.",
                    fullScreenAllowed(context), essential = true,
                ),
            )
        }
        add(
            HealthItem(
                HealthItem.Key.Overlay, "✨", "نمایش روی برنامه‌های دیگه",
                "تا وقتی با یه برنامه‌ی دیگه کار می‌کنی هم پیام عاشقانه فوراً تمام‌صفحه باز بشه.",
                overlayAllowed(context), essential = false,
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                HealthItem(
                    HealthItem.Key.ExactAlarms, "⏰", "هشدار دقیق",
                    "به نگهبان پینگلی اجازه می‌ده اگه سرویس متوقف شد، سریع‌تر دوباره روشنش کنه.",
                    exactAlarmsAllowed(context), essential = false,
                ),
            )
        }
        if (hasAutostartManager) {
            add(
                HealthItem(
                    HealthItem.Key.Autostart, "🚀", "اجرای خودکار (${Build.MANUFACTURER})",
                    "گوشی‌های این برند یه مدیریت‌کننده‌ی اختصاصی دارن؛ پینگلی رو اونجا «مجاز» یا «بدون محدودیت» کن.",
                    autostartConfirmed, essential = false,
                ),
            )
        }
        add(
            HealthItem(
                HealthItem.Key.Service, "💓", "سرویس پس‌زمینه",
                if (serviceShouldRun) "پینگلی باید همیشه روشن باشه تا پارتنرت بهت برسه." else "در حالت مکث خاموشه؛ با «ادامه» دوباره روشن می‌شه.",
                !serviceShouldRun || PinglyService.running, essential = true,
            ),
        )
    }

    private fun packageUri(context: Context) = Uri.parse("package:${context.packageName}")

    @SuppressLint("BatteryLife") // Pingly's core purpose is realtime delivery; the user confirms in a system dialog.
    fun batteryIntent(context: Context) = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))

    fun notificationSettingsIntent(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun fullScreenIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context))
        } else {
            appDetails(context)
        }

    fun overlayIntent(context: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context))

    fun exactAlarmIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context))
        else appDetails(context)

    fun appDetails(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    /** Known OEM "autostart / protected apps" screens, most specific first. */
    fun autostartIntents(context: Context): List<Intent> {
        val components = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
            "com.meizu.safe" to "com.meizu.safe.security.SHOW_APPSEC",
            "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
        )
        return components.map { (pkg, cls) -> Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } +
            appDetails(context)
    }

    /** Tries each intent until one opens. */
    fun openFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // try the next one
            }
        }
        return false
    }
}
