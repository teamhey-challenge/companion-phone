package ai.hey.phone.feature.friends

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.ApiException
import ai.hey.phone.data.Friend
import ai.hey.phone.data.FriendMessage
import ai.hey.phone.data.Invite
import ai.hey.phone.data.guardedCall
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val BARE_FRIEND_CODE = Regex("^[A-Za-z0-9]{4,12}$")
private val LINK_FRIEND_CODE = Regex("""/f/([A-Za-z0-9]{4,12})(?:[/?#].*)?$""")

/** The friend code in scanned or typed text: a bare 6-char code or `${PASSENGER_WEB_URL}/f/{code}`. Pair links (`/p/`) are not friend codes. */
fun parseFriendCode(text: String?): String? {
    val t = text?.trim().orEmpty()
    if (t.isEmpty()) return null
    val code = if (BARE_FRIEND_CODE.matches(t)) t else LINK_FRIEND_CODE.find(t)?.groupValues?.get(1)
    return code?.uppercase()
}

sealed interface FriendsUi {
    data object Loading : FriendsUi
    data object Failed : FriendsUi
    data class Items(val friends: List<Friend>) : FriendsUi
}

/** The one-line result under the heading. [Added] carries the new friend's name for "…와 친구가 됐어!". */
sealed interface FriendsNotice {
    data class Added(val name: String) : FriendsNotice
    data object Sent : FriendsNotice
    data object Reported : FriendsNotice
    data object AddFailed : FriendsNotice
    /** 409 `limit` (50 friends, D-53). */
    data object LimitReached : FriendsNotice
    /** 400 `self`: "내 코드예요". */
    data object OwnCode : FriendsNotice
    /** 409 `already_friends`. */
    data object AlreadyFriends : FriendsNotice
    /** 410 `expired`: the 24 hour code ran out. */
    data object CodeExpired : FriendsNotice
    data object SendFailed : FriendsNotice
    data object Blocked : FriendsNotice
    data object Generic : FriendsNotice
}

/** Server answer of `POST /v1/friends/invites/{code}/accept` -> notice (6.5). */
fun friendAcceptNotice(e: ApiException): FriendsNotice = when {
    e.reason == "self" -> FriendsNotice.OwnCode
    e.reason == "already_friends" -> FriendsNotice.AlreadyFriends
    e.reason == "limit" || e.code == 409 -> FriendsNotice.LimitReached
    e.code == 410 || e.reason == "expired" -> FriendsNotice.CodeExpired
    e.code in 400..499 -> FriendsNotice.AddFailed
    else -> FriendsNotice.Generic
}

/** Inbox row: someone else's message with the report button. */
data class InboxRow(val id: String, val fromName: String, val text: String, val at: String, val friendId: String? = null)

fun List<FriendMessage>.toInbox(): List<InboxRow> =
    filter { it.direction == "in" }.sortedByDescending { it.at }.map { InboxRow(it.id, it.friendName, it.text, it.at, it.friendId) }

private fun String.endsWithBatchim(): Boolean {
    val last = last()
    return last in '가'..'힣' && (last.code - 0xAC00) % 28 != 0
}

/** "<이름>가 도착했대" with the right subject particle; never a place (D-52). */
fun arrivedText(name: String): String {
    val n = name.ifBlank { "친구" }
    return n + (if (n.endsWithBatchim()) "이가" else "가") + " 도착했대"
}

/** "<이름>과/와" for "…와 친구가 됐어!". */
fun withParticleWaGwa(name: String): String {
    val n = name.ifBlank { "친구" }
    return n + (if (n.endsWithBatchim()) "과" else "와")
}

/**
 * State and actions of the 친구 tab: list, invite, accept, per-friend 도착 알리기, send, unfriend, inbox + report. A 401/403 calls
 * [onUnauthorized] once. A report hides the message at once (the server hides it too); a failed report brings it back.
 */
class FriendsModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onUnauthorized: () -> Unit,
) {
    var friends: FriendsUi by mutableStateOf(FriendsUi.Loading)
        private set
    var inbox: List<InboxRow>? by mutableStateOf(null)
        private set
    var invite: Invite? by mutableStateOf(null)
        private set
    var notice: FriendsNotice? by mutableStateOf(null)
        private set
    var busy by mutableStateOf(false)
        private set
    /** The last friend arrival seen while the tab is open ("<이름>가 도착했대"); shown as a notice. */
    var arrived: String? by mutableStateOf(null)
        private set

    fun onArrived(name: String) { arrived = arrivedText(name) }

    private var out = false

    private fun fail(n: FriendsNotice = FriendsNotice.Generic): (Exception) -> Unit = { e ->
        notice = if (e is ApiException && e.code == 422) FriendsNotice.Blocked else n
    }

    private fun unauthorized() { if (!out) { out = true; onUnauthorized() } }

    fun clearNotice() { notice = null }

    fun load() {
        scope.launch {
            guardedCall(::unauthorized, { if (friends !is FriendsUi.Items) friends = FriendsUi.Failed }) {
                friends = FriendsUi.Items(api.friends())
            }
        }
        loadInbox()
    }

    fun loadInbox() {
        scope.launch {
            // Inbox failure keeps the old list; the friends zone shows its own error.
            guardedCall(::unauthorized, {}) { inbox = api.messages().toInbox() }
        }
    }

    fun makeInvite() {
        if (busy) return
        busy = true
        scope.launch {
            guardedCall(::unauthorized, fail()) { invite = api.createInvite() }
            busy = false
        }
    }

    fun add(codeOrLink: String) {
        val code = parseFriendCode(codeOrLink)
        if (code == null) { notice = FriendsNotice.AddFailed; return }
        if (busy) return
        busy = true
        notice = null
        scope.launch {
            guardedCall(::unauthorized, { e ->
                notice = when {
                    e is ApiException -> friendAcceptNotice(e)
                    else -> FriendsNotice.Generic
                }
            }) {
                val friend = api.acceptInvite(code)
                friends = FriendsUi.Items(api.friends())
                notice = FriendsNotice.Added(friend.name)
            }
            busy = false
        }
    }

    fun setShareArrival(id: String, on: Boolean) {
        val cur = friends as? FriendsUi.Items ?: return
        val before = cur.friends
        friends = FriendsUi.Items(before.map { if (it.id == id) it.copy(shareArrival = on) else it })
        scope.launch {
            guardedCall(::unauthorized, { friends = FriendsUi.Items(before); notice = FriendsNotice.Generic }) { api.setShareArrival(id, on) }
        }
    }

    fun send(id: String, text: String) {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_MESSAGE || busy) return
        busy = true
        notice = null
        scope.launch {
            guardedCall(::unauthorized, fail(FriendsNotice.SendFailed)) {
                api.sendMessage(id, t)
                notice = FriendsNotice.Sent
            }
            busy = false
        }
    }

    fun unfriend(id: String) {
        val cur = friends as? FriendsUi.Items ?: return
        scope.launch {
            guardedCall(::unauthorized, fail()) {
                api.unfriend(id)
                friends = FriendsUi.Items(cur.friends.filter { it.id != id })
            }
        }
    }

    fun report(id: String) {
        val before = inbox ?: return
        inbox = before.filter { it.id != id }
        scope.launch {
            guardedCall(::unauthorized, { inbox = before; notice = FriendsNotice.Generic }) {
                api.reportMessage(id)
                notice = FriendsNotice.Reported
            }
        }
    }

    companion object {
        const val MAX_MESSAGE = 120
    }
}
