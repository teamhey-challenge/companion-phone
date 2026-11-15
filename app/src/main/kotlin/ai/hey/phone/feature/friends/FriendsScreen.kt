package ai.hey.phone.feature.friends

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.R
import ai.hey.phone.data.Friend
import ai.hey.phone.data.Invite
import ai.hey.phone.data.PushFrame
import ai.hey.phone.feature.pair.ScanRoute
import ai.hey.phone.ui.CharacterHero
import ai.hey.phone.ui.ConfirmDialog
import ai.hey.phone.ui.GlassButton
import ai.hey.phone.ui.HeyButton
import ai.hey.phone.ui.HeyButtonVariant
import ai.hey.phone.ui.HeyCard
import ai.hey.phone.ui.HeyDivider
import ai.hey.phone.ui.HeyGap
import ai.hey.phone.ui.HeyNotice
import ai.hey.phone.ui.HeyText
import ai.hey.phone.ui.HeyTextField
import ai.hey.phone.ui.HeyTextVariant
import ai.hey.phone.ui.HeyTone
import ai.hey.phone.ui.PageScaffold
import ai.hey.phone.ui.QrImage
import ai.hey.phone.ui.ToggleRow
import ai.hey.phone.ui.VStack
import ai.hey.phone.ui.HeyIconButton
import ai.hey.phone.ui.HeyIcons
import ai.hey.phone.ui.HStack
import ai.hey.phone.ui.LinkRow
import androidx.activity.compose.BackHandler
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private sealed interface Confirm {
    data class Unfriend(val friend: Friend) : Confirm
    data class Report(val id: String) : Confirm
}

/** The sub-pages of the 친구 tab (studio PLEOS-196): list, my code, add, compose (+ sent), inbox. */
enum class FriendsView { List, Code, Add, Compose, Sent, Inbox }

/**
 * Tab 친구. Stateless except for the open confirmation and the message draft: code to share (QR + link + share sheet),
 * add by code / scan, the friend list (도착 알리기, 메시지, 친구 끊기) and the inbox with a report button per message.
 */
@Composable
fun FriendsScreen(
    friends: FriendsUi,
    inbox: List<InboxRow>?,
    invite: Invite?,
    notice: FriendsNotice?,
    busy: Boolean,
    onMakeInvite: () -> Unit,
    onShare: (Invite) -> Unit,
    onAdd: (String) -> Unit,
    onScan: () -> Unit,
    onRetry: () -> Unit,
    onShareArrival: (String, Boolean) -> Unit,
    onSend: (String, String) -> Unit,
    onUnfriend: (String) -> Unit,
    onReport: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialWriteTo: String? = null,
    arrived: String? = null,
    initialView: FriendsView = FriendsView.List,
) {
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    var writeTo by rememberSaveable { mutableStateOf(initialWriteTo) }
    var draft by rememberSaveable { mutableStateOf("") }
    var view by rememberSaveable { mutableStateOf(if (initialWriteTo != null) FriendsView.Compose else initialView) }
    val back = { view = FriendsView.List }
    if (view != FriendsView.List) BackHandler(onBack = back)
    Box(modifier) {
        PageScaffold(insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) {
            VStack(HeyGap.G24, Modifier.padding(top = HeyGap.G16.value, bottom = HeyGap.G24.value)) {
                HStack(HeyGap.G8, Modifier.fillMaxWidth()) {
                    if (view != FriendsView.List) HeyIconButton(HeyIcons.ArrowLeft, stringResource(R.string.friends_back), back)
                    HeyText(
                        stringResource(
                            when (view) {
                                FriendsView.List -> R.string.friends_title
                                FriendsView.Code -> R.string.friends_invite_title
                                FriendsView.Add -> R.string.friends_add_title
                                FriendsView.Compose, FriendsView.Sent -> R.string.friends_compose_title
                                FriendsView.Inbox -> R.string.friends_inbox_title
                            },
                        ),
                        Modifier.weight(1f), variant = HeyTextVariant.Title,
                    )
                    if (view == FriendsView.List) HeyIconButton(HeyIcons.Plus, stringResource(R.string.friends_add_title), { view = FriendsView.Add })
                }
                notice?.let { HeyNotice(it.text(), danger = it.danger()) }
                arrived?.let { HeyNotice(it) }

                when (view) {
                    FriendsView.List -> {
                        LinkRow(stringResource(R.string.friends_invite_title), { view = FriendsView.Code })
                        LinkRow(stringResource(R.string.friends_inbox_title), { view = FriendsView.Inbox })
                        HeyDivider()
                        HeyText(stringResource(R.string.friends_list_title), variant = HeyTextVariant.Heading)
                        when (friends) {
                            FriendsUi.Loading -> HeyText(stringResource(R.string.friends_loading), tone = HeyTone.Muted)
                            FriendsUi.Failed -> VStack(HeyGap.G8) {
                                HeyText(stringResource(R.string.friends_load_failed), tone = HeyTone.Muted)
                                HeyButton(stringResource(R.string.retry), onRetry)
                            }
                            is FriendsUi.Items -> if (friends.friends.isEmpty()) {
                                VStack(HeyGap.G16) {
                                    CharacterHero()
                                    HeyText(stringResource(R.string.friends_empty), Modifier.fillMaxWidth(), tone = HeyTone.Muted, align = TextAlign.Center)
                                    GlassButton(stringResource(R.string.friends_add_title), { view = FriendsView.Add }, Modifier.fillMaxWidth())
                                }
                            } else VStack(HeyGap.G16) {
                                friends.friends.forEachIndexed { i, f ->
                                    HeyCard(tone = i + 1) {
                                        HeyText(f.name, variant = HeyTextVariant.Heading)
                                        ToggleRow(
                                            label = stringResource(R.string.friends_arrival),
                                            checked = f.shareArrival,
                                            onCheckedChange = { onShareArrival(f.id, it) },
                                            description = stringResource(R.string.friends_arrival_note),
                                        )
                                        HeyButton(stringResource(R.string.friends_write), { writeTo = f.id; draft = ""; view = FriendsView.Compose }, variant = HeyButtonVariant.Text)
                                        HeyButton(stringResource(R.string.friends_unfriend), { confirm = Confirm.Unfriend(f) }, variant = HeyButtonVariant.Danger)
                                    }
                                }
                            }
                        }
                    }

                    FriendsView.Code -> HeyCard(tone = 0) {
                        if (invite == null) {
                            HeyButton(stringResource(R.string.friends_invite_make), onMakeInvite, enabled = !busy)
                        } else {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrImage(invite.url, invite.code, size = 200.dp) }
                            HeyText(invite.code, Modifier.fillMaxWidth(), variant = HeyTextVariant.Title, align = TextAlign.Center)
                            HeyText(stringResource(R.string.friends_invite_expires), Modifier.fillMaxWidth(), variant = HeyTextVariant.Caption, tone = HeyTone.Muted, align = TextAlign.Center)
                            GlassButton(stringResource(R.string.friends_invite_share), { onShare(invite) }, Modifier.fillMaxWidth())
                        }
                    }

                    FriendsView.Add -> VStack(HeyGap.G16) {
                        HeyText(stringResource(R.string.friends_add_intro), tone = HeyTone.Muted)
                        HeyTextField(code, { code = it }, stringResource(R.string.friends_add_label))
                        GlassButton(stringResource(R.string.friends_add_go), { onAdd(code); code = ""; view = FriendsView.List }, Modifier.fillMaxWidth(), enabled = !busy && code.isNotBlank())
                        HeyButton(stringResource(R.string.friends_scan), onScan, variant = HeyButtonVariant.Text)
                        LinkRow(stringResource(R.string.friends_share_link), { view = FriendsView.Code })
                    }

                    FriendsView.Compose -> {
                        val who = (friends as? FriendsUi.Items)?.friends?.firstOrNull { it.id == writeTo }
                        VStack(HeyGap.G16) {
                            HeyText(if (who != null) stringResource(R.string.friends_compose_to, who.name) else "", tone = HeyTone.Muted)
                            HeyTextField(
                                draft, { if (it.length <= FriendsModel.MAX_MESSAGE) draft = it }, stringResource(R.string.friends_write_label),
                                capitalize = false, multiline = true, counter = "${draft.length}/${FriendsModel.MAX_MESSAGE}",
                            )
                            GlassButton(
                                stringResource(R.string.friends_write_send),
                                { writeTo?.let { onSend(it, draft) }; draft = ""; view = FriendsView.Sent },
                                Modifier.fillMaxWidth(), enabled = !busy && draft.isNotBlank() && writeTo != null,
                            )
                        }
                    }

                    FriendsView.Sent -> VStack(HeyGap.G16, Modifier.fillMaxWidth(), horizontal = Alignment.CenterHorizontally) {
                        CharacterHero()
                        HeyText(stringResource(R.string.friends_sent_title), variant = HeyTextVariant.Heading, align = TextAlign.Center)
                        HeyText(stringResource(R.string.friends_sent_body), Modifier.fillMaxWidth(), tone = HeyTone.Muted, align = TextAlign.Center)
                        GlassButton(stringResource(R.string.friends_to_list), { view = FriendsView.List }, Modifier.fillMaxWidth())
                    }

                    FriendsView.Inbox -> when {
                        inbox == null -> HeyText(stringResource(R.string.friends_loading), tone = HeyTone.Muted)
                        inbox.isEmpty() -> VStack(HeyGap.G16) {
                            CharacterHero()
                            HeyText(stringResource(R.string.friends_inbox_empty), Modifier.fillMaxWidth(), tone = HeyTone.Muted, align = TextAlign.Center)
                        }
                        else -> VStack(HeyGap.G12) {
                            inbox.forEachIndexed { i, m ->
                                HeyCard(tone = i) {
                                    HeyText(m.fromName, variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                                    HeyText(m.text)
                                    HStack(HeyGap.G8) {
                                        if (m.friendId != null) HeyButton(stringResource(R.string.friends_reply), { writeTo = m.friendId; draft = ""; view = FriendsView.Compose }, variant = HeyButtonVariant.Text)
                                        HeyButton(stringResource(R.string.friends_report), { confirm = Confirm.Report(m.id) }, variant = HeyButtonVariant.Text)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        when (val c = confirm) {
            is Confirm.Unfriend -> ConfirmDialog(
                title = stringResource(R.string.friends_unfriend_title),
                message = stringResource(R.string.friends_unfriend_message, c.friend.name),
                confirmText = stringResource(R.string.friends_unfriend_confirm),
                dismissText = stringResource(R.string.cancel),
                onConfirm = { onUnfriend(c.friend.id); confirm = null },
                onDismiss = { confirm = null },
            )
            is Confirm.Report -> ConfirmDialog(
                title = stringResource(R.string.friends_report_title),
                message = stringResource(R.string.friends_report_message),
                confirmText = stringResource(R.string.friends_report_confirm),
                dismissText = stringResource(R.string.cancel),
                onConfirm = { onReport(c.id); confirm = null },
                onDismiss = { confirm = null },
            )
            null -> Unit
        }
    }
}

private fun FriendsNotice.danger() = !(this is FriendsNotice.Added || this == FriendsNotice.Sent || this == FriendsNotice.Reported)

@Composable
private fun FriendsNotice.text(): String = when (this) {
    is FriendsNotice.Added -> stringResource(R.string.friends_added, withParticleWaGwa(name))
    FriendsNotice.Sent -> stringResource(R.string.friends_sent)
    FriendsNotice.Reported -> stringResource(R.string.friends_reported)
    FriendsNotice.AddFailed -> stringResource(R.string.friends_err_add)
    FriendsNotice.LimitReached -> stringResource(R.string.friends_err_limit)
    FriendsNotice.OwnCode -> stringResource(R.string.friends_err_self)
    FriendsNotice.AlreadyFriends -> stringResource(R.string.friends_err_already)
    FriendsNotice.CodeExpired -> stringResource(R.string.friends_err_expired)
    FriendsNotice.SendFailed -> stringResource(R.string.friends_err_generic)
    FriendsNotice.Blocked -> stringResource(R.string.friends_err_blocked)
    FriendsNotice.Generic -> stringResource(R.string.friends_err_generic)
}

/** The share-sheet text: the link and the code, so it still works when the link is not opened by the app. */
fun inviteShareText(template: String, invite: Invite) = template.replace("%1\$s", invite.url).replace("%2\$s", invite.code)

@Composable
fun FriendsRoute(onUnauthorized: () -> Unit, acceptCode: String? = null) {
    val context = LocalContext.current
    val app = HeyPhoneApp.from(context)
    val scope = rememberCoroutineScope()
    val model = remember { FriendsModel(app.api, scope, onUnauthorized) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    val shareTemplate = stringResource(R.string.friends_share_text)
    val chooser = stringResource(R.string.friends_share_chooser)

    LaunchedEffect(Unit) { model.load() }
    // A friend link accepts its invite once, right after the list loads (add() reloads the list itself).
    var accepted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(acceptCode) { if (acceptCode != null && !accepted) { accepted = true; model.add(acceptCode) } }
    // A friend message or arrival while this tab is open refreshes the inbox at once.
    LaunchedEffect(Unit) {
        app.events.collect {
            if (it is PushFrame.Message) model.loadInbox()
            if (it is PushFrame.Arrived) model.onArrived(it.fromName)
        }
    }

    if (scanning) {
        ScanRoute(
            onCode = { c -> scanning = false; model.add(c) },
            onBack = { scanning = false },
            parse = ::parseFriendCode,
            title = stringResource(R.string.friends_scan_title),
            hint = stringResource(R.string.friends_scan_hint),
            wrongText = stringResource(R.string.friends_scan_wrong),
            pasteLabel = stringResource(R.string.friends_add_label),
        )
        return
    }
    FriendsScreen(
        friends = model.friends,
        inbox = model.inbox,
        invite = model.invite,
        notice = model.notice,
        arrived = model.arrived,
        busy = model.busy,
        onMakeInvite = model::makeInvite,
        onShare = { inv ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, inviteShareText(shareTemplate, inv))
            context.startActivity(Intent.createChooser(send, chooser))
        },
        onAdd = model::add,
        onScan = { scanning = true },
        onRetry = model::load,
        onShareArrival = model::setShareArrival,
        onSend = model::send,
        onUnfriend = model::unfriend,
        onReport = model::report,
    )
}
