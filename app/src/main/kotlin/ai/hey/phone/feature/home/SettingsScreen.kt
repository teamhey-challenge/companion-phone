package ai.hey.phone.feature.home

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.R
import ai.hey.phone.notify.Notifier
import ai.hey.phone.ui.ConfirmDialog
import ai.hey.phone.ui.GlassButton
import ai.hey.phone.ui.HeyButton
import ai.hey.phone.ui.HeyButtonVariant
import ai.hey.phone.ui.HeyDivider
import ai.hey.phone.ui.HeyGap
import ai.hey.phone.ui.HeyNotice
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextField
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.PageScaffold
import ai.hey.phone.ui.ToggleRow
import ai.hey.phone.ui.VStack
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/** Tab 설정: 이름, 알림 허용, 이 폰에서 나가기. Stateless except for the open confirmation. */
@Composable
fun SettingsScreen(
    name: String,
    loaded: Boolean,
    saving: Boolean,
    notice: SettingsNotice?,
    notificationsOn: Boolean,
    onName: (String) -> Unit,
    onSaveName: () -> Unit,
    onAllowNotifications: () -> Unit,
    onLogout: () -> Unit,
    /** Account without a car: opens the QR scanner to bind a car to this Google account. Null hides the button. */
    onConnectCar: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    initialConfirm: Boolean = false,
    /** Phone-only account: leaving is final (no pairing to come back through), so the dialog says so. */
    phoneOnly: Boolean = false,
) {
    var confirm by remember { mutableStateOf(initialConfirm) }
    Box(modifier) {
        PageScaffold(insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) {
            VStack(HeyGap.G24, Modifier.padding(top = HeyGap.G32.value, bottom = HeyGap.G24.value)) {
                HeyText(stringResource(R.string.settings_title), variant = HeyTextVariant.Title)
                notice?.let {
                    HeyNotice(
                        stringResource(if (it == SettingsNotice.NameSaved) R.string.settings_name_saved else R.string.settings_err),
                        danger = it == SettingsNotice.Failed,
                    )
                }
                if (!loaded) HeyText(stringResource(R.string.settings_loading), tone = HeyTone.Muted)

                VStack(HeyGap.G12) {
                    HeyText(stringResource(R.string.settings_name_title), variant = HeyTextVariant.Heading)
                    HeyTextField(name, onName, stringResource(R.string.settings_name_label), capitalize = false)
                    GlassButton(stringResource(R.string.settings_name_save), onSaveName, enabled = !saving && name.isNotBlank())
                }
                HeyDivider()
                VStack(HeyGap.G12) {
                    HeyText(stringResource(R.string.settings_notif_title), variant = HeyTextVariant.Heading)
                    HeyText(stringResource(if (notificationsOn) R.string.settings_notif_on else R.string.settings_notif_off), tone = HeyTone.Muted)
                    if (!notificationsOn) GlassButton(stringResource(R.string.settings_notif_allow), onAllowNotifications)
                }
                if (onConnectCar != null) {
                    HeyDivider()
                    GlassButton(stringResource(R.string.login_qr), onConnectCar)
                }
                HeyDivider()
                HeyButton(stringResource(R.string.settings_logout), { confirm = true }, variant = HeyButtonVariant.Danger)
            }
        }
        if (confirm) ConfirmDialog(
            title = stringResource(R.string.settings_logout_title),
            message = stringResource(if (phoneOnly) R.string.settings_logout_message_phone_only else R.string.settings_logout_message),
            confirmText = stringResource(R.string.settings_logout_confirm),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { confirm = false; onLogout() },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
fun SettingsRoute(onUnauthorized: () -> Unit, onLogout: () -> Unit, onConnectCar: () -> Unit) {
    val context = LocalContext.current
    val app = HeyPhoneApp.from(context)
    val scope = rememberCoroutineScope()
    var notificationsOn by remember { mutableStateOf(Notifier.allowed(context)) }
    val model = remember {
        SettingsModel(
            app.api, scope, onUnauthorized,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsOn = Notifier.allowed(context) }
    LaunchedEffect(Unit) { model.load() }

    SettingsScreen(
        name = model.name,
        loaded = model.loaded,
        saving = model.saving,
        notice = model.notice,
        notificationsOn = notificationsOn,
        onName = { model.name = it },
        onSaveName = model::saveName,
        onAllowNotifications = {
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        onLogout = onLogout,
        onConnectCar = if (app.prefs.phoneOnly) onConnectCar else null,
        phoneOnly = app.prefs.phoneOnly,
    )
}
