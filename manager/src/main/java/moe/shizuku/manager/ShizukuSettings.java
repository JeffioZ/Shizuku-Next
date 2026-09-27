package moe.shizuku.manager;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import java.lang.annotation.Retention;
import java.util.Locale;
import moe.shizuku.manager.service.WatchdogService;
import moe.shizuku.manager.receiver.BootCompleteReceiver;
import moe.shizuku.manager.utils.Token;
import moe.shizuku.manager.utils.EmptySharedPreferencesImpl;
import moe.shizuku.manager.utils.EnvironmentUtils;
import static java.lang.annotation.RetentionPolicy.SOURCE;

public class ShizukuSettings {

    public static final String NAME = "settings";
    public static class Keys {
        public static final String KEY_START_ON_BOOT = "start_on_boot";
        public static final String KEY_WATCHDOG = "watchdog";
        public static final String KEY_TCP_MODE = "tcp_mode";
        public static final String KEY_TCP_PORT = "tcp_port";
        public static final String KEY_AUTO_DISABLE_USB_DEBUGGING = "auto_disable_usb_debugging";
        public static final String KEY_LANGUAGE = "language";
        public static final String KEY_TRANSLATION = "translation";
        public static final String KEY_TRANSLATION_CONTRIBUTORS = "translation_contributors";
        public static final String KEY_LIGHT_THEME = "light_theme";
        public static final String KEY_NIGHT_MODE = "night_mode";
        public static final String KEY_BLACK_NIGHT_THEME = "black_night_theme";
        public static final String KEY_USE_SYSTEM_COLOR = "use_system_color";
        public static final String KEY_UPDATE_MODE = "update_mode";
        public static final String KEY_HELP = "help";
        public static final String KEY_REPORT_BUG = "report_bug";
        public static final String KEY_LEGACY_PAIRING = "legacy_pairing";
        public static final String KEY_CATEGORY_ADVANCED = "category_advanced";
        public static final String KEY_MANUALLY_STOPPED = "manually_stopped";
        public static final String KEY_LAST_ADB_TRANSPORT = "last_adb_transport";
        public static final String KEY_START_METHOD = "start_method";
        public static final String KEY_RUNNING_START_METHOD = "running_start_method";
        public static final String KEY_WAIT_FOR_WIFI = "wait_for_wifi";
        public static final String KEY_SYSTEM_START_METHOD = "system_start_method";
    }

    public static class UpdateMode {
        public static final int OFF = 0;
        public static final int STABLE = 1;
        public static final int BETA = 2;
    }

    private static SharedPreferences sPreferences;

    public static SharedPreferences getPreferences() {
        return sPreferences;
    }

    @NonNull
    private static Context getSettingsStorageContext(@NonNull Context context) {
        Context storageContext;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            storageContext = context.createDeviceProtectedStorageContext();
        } else {
            storageContext = context;
        }

        storageContext = new ContextWrapper(storageContext) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                try {
                    return super.getSharedPreferences(name, mode);
                } catch (IllegalStateException e) {
                    // SharedPreferences in credential encrypted storage are not available until after user is unlocked
                    return new EmptySharedPreferencesImpl();
                }
            }
        };

        return storageContext;
    }

    public static void initialize(Context context) {
        if (sPreferences == null) {
            sPreferences = getSettingsStorageContext(context)
                .getSharedPreferences(NAME, Context.MODE_PRIVATE);
        }
    }

    /**
     * Which method a start uses. This is what the Start button, start on boot,
     * the watchdog and the start intents all follow, so "start" behaves the same
     * everywhere instead of guessing from whichever method happened to work last.
     */
    @IntDef({
        StartMethod.WIRELESS,
        StartMethod.USB,
        StartMethod.SYSTEM,
        StartMethod.ROOT,
    })
    @Retention(SOURCE)
    public @interface StartMethod {
        int WIRELESS = 0;
        int USB = 1;
        int SYSTEM = 2;
        int ROOT = 3;
    }

    @StartMethod
    public static int getStartMethod() {
        int fallback = getLastLaunchMode() == LaunchMethod.ROOT
                ? StartMethod.ROOT
                : StartMethod.WIRELESS;
        return getPreferences().getInt(Keys.KEY_START_METHOD, fallback);
    }

    public static void setStartMethod(@StartMethod int method) {
        getPreferences().edit().putInt(Keys.KEY_START_METHOD, method).apply();
    }

    private static final int START_METHOD_UNRECORDED = -1;

    /**
     * How the server that is running now was started. Kept separately from
     * [getStartMethod] (which is what the next start will use), so the UI can show both
     * and they can't be mistaken for each other. [START_METHOD_UNRECORDED] means no
     * start of ours launched it — e.g. it was started by another tool.
     */
    public static int getRunningStartMethod() {
        return getPreferences().getInt(Keys.KEY_RUNNING_START_METHOD, START_METHOD_UNRECORDED);
    }

    public static void setRunningStartMethod(@StartMethod int method) {
        getPreferences().edit().putInt(Keys.KEY_RUNNING_START_METHOD, method).apply();
    }

    @IntDef({
        LaunchMethod.UNKNOWN,
        LaunchMethod.ROOT,
        LaunchMethod.ADB,
    })
    @Retention(SOURCE)
    public @interface LaunchMethod {
        int UNKNOWN = -1;
        int ROOT = 0;
        int ADB = 1;
    }

    /** Which method was observed to work last — informational (status card, transport). */
    @LaunchMethod
    public static int getLastLaunchMode() {
        return getPreferences().getInt("mode", LaunchMethod.UNKNOWN);
    }

    public static void setLastLaunchMode(@LaunchMethod int method) {
        getPreferences().edit().putInt("mode", method).apply();
    }

    public static boolean getAutoDisableUsbDebugging() {
        return getPreferences().getBoolean(Keys.KEY_AUTO_DISABLE_USB_DEBUGGING, false);
    }
    
    public static String getLastPromptedVersion() {
        return getPreferences().getString("lastPromptedVersion", "");
    }

    public static void setLastPromptedVersion(String version) {
        getPreferences().edit().putString("lastPromptedVersion", version).apply();
    }

    public static String getAuthToken() {
        String authToken = getPreferences().getString("auth_token", null);
        if (authToken == null || authToken.isEmpty()) {
            authToken = generateAuthToken();
        }
        return authToken;
    }

    public static String generateAuthToken() {
        String token = Token.generateToken();
        getPreferences().edit().putString("auth_token", token).apply();
        return token;
    }

    public static boolean getStartOnBoot(Context context) {
        ComponentName bootCompleteReceiver = new ComponentName(context.getPackageName(), BootCompleteReceiver.class.getName());
        int state = context.getPackageManager().getComponentEnabledSetting(bootCompleteReceiver);
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    public static void setStartOnBoot(Context context, boolean enable) {
        ComponentName bootCompleteReceiver = new ComponentName(context.getPackageName(), BootCompleteReceiver.class.getName());
        context.getPackageManager().setComponentEnabledSetting(
            bootCompleteReceiver,
            enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        );
        getPreferences().edit().putBoolean(Keys.KEY_START_ON_BOOT, enable).apply();
    }
    
    public static boolean getWatchdog() {
        return getPreferences().getBoolean(Keys.KEY_WATCHDOG, false);
    }

    public static boolean isWatchdogRunning() {
        return WatchdogService.isRunning();
    }

    /**
     * True while the last stop was requested by the user (as opposed to a crash or
     * the system killing the server). Suppresses the watchdog's proactive
     * "server is dead, restart it" check. Cleared whenever a start is requested
     * from any entry point and whenever the server is confirmed RUNNING.
     */
    public static boolean getManuallyStopped() {
        return getPreferences().getBoolean(Keys.KEY_MANUALLY_STOPPED, false);
    }

    public static void setManuallyStopped(boolean stopped) {
        getPreferences().edit().putBoolean(Keys.KEY_MANUALLY_STOPPED, stopped).apply();
    }

    public static final int ADB_TRANSPORT_UNKNOWN = 0;
    // Started over wireless debugging (TLS)
    public static final int ADB_TRANSPORT_TLS = 1;
    // Started over the classic adb TCP port (USB debugging)
    public static final int ADB_TRANSPORT_TCP = 2;

    public static int getLastAdbTransport() {
        return getPreferences().getInt(Keys.KEY_LAST_ADB_TRANSPORT, ADB_TRANSPORT_UNKNOWN);
    }

    public static void setLastAdbTransport(int transport) {
        getPreferences().edit().putInt(Keys.KEY_LAST_ADB_TRANSPORT, transport).apply();
    }

    /**
     * When enabled, unattended background restarts wait for an unmetered Wi-Fi
     * connection before attempting discovery. User-initiated starts never wait.
     */
    public static boolean getWaitForWifi() {
        return getPreferences().getBoolean(Keys.KEY_WAIT_FOR_WIFI, true);
    }

    public static void setWaitForWifi(boolean enable) {
        getPreferences().edit().putBoolean(Keys.KEY_WAIT_FOR_WIFI, enable).apply();
    }

    /**
     * Which method the "Start (system)" card uses to launch Shizuku under the
     * system UID: the built-in device exploit, or an external/custom launch.
     */
    public static final String SYSTEM_START_EXPLOIT = "exploit";
    public static final String SYSTEM_START_CUSTOM = "custom";

    public static String getSystemStartMethod() {
        return getPreferences().getString(Keys.KEY_SYSTEM_START_METHOD, SYSTEM_START_CUSTOM);
    }

    public static void setSystemStartMethod(String method) {
        getPreferences().edit().putString(Keys.KEY_SYSTEM_START_METHOD, method).apply();
    }

    public static void setWatchdog(Context context, boolean enable) {
        if (enable) {
            WatchdogService.start(context);
        } else {
            WatchdogService.stop(context);
        }
        getPreferences().edit().putBoolean(Keys.KEY_WATCHDOG, enable).apply();
        return;
    }

    public static boolean getTcpMode() {
        return getPreferences().getBoolean(Keys.KEY_TCP_MODE, true);
    }

    public static void setTcpMode(boolean enable) {
        getPreferences().edit().putBoolean(Keys.KEY_TCP_MODE, enable).apply();
    }

    public static int getTcpPort() {
        try {
            return Integer.parseInt(getPreferences().getString(Keys.KEY_TCP_PORT, "5555"));
        } catch (NumberFormatException e) {
            return 5555;
        }
    }

    public static void setTcpPort(@Nullable Integer port) {
        if (port != null) {
            getPreferences().edit().putString(Keys.KEY_TCP_PORT, Integer.toString(port)).apply();
        } else {
            getPreferences().edit().remove(Keys.KEY_TCP_PORT).apply();
        }
        
    }

    public static boolean getLegacyPairing() {
        return getPreferences().getBoolean(Keys.KEY_LEGACY_PAIRING, false);
    }

    @AppCompatDelegate.NightMode
    public static int getNightMode() {
        int defValue = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (EnvironmentUtils.isWatch()) {
            defValue = AppCompatDelegate.MODE_NIGHT_YES;
        }
        return getPreferences().getInt(Keys.KEY_NIGHT_MODE, defValue);
    }

    public static Locale getLocale() {
        String tag = getPreferences().getString(Keys.KEY_LANGUAGE, null);
        if (TextUtils.isEmpty(tag) || "SYSTEM".equals(tag)) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(tag);
    }

    public static int getUpdateMode() {
        return getPreferences().getInt(Keys.KEY_UPDATE_MODE, UpdateMode.STABLE);
    }
}
