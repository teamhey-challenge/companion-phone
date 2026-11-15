package ai.hey.phone.feature.friends

import ai.hey.phone.data.ApiException
import org.junit.Assert.assertEquals
import org.junit.Test

class FriendNoticeTest {
    @Test fun ownCode() = assertEquals(FriendsNotice.OwnCode, friendAcceptNotice(ApiException(400, "self")))
    @Test fun alreadyFriends() = assertEquals(FriendsNotice.AlreadyFriends, friendAcceptNotice(ApiException(409, "already_friends")))
    @Test fun limit() = assertEquals(FriendsNotice.LimitReached, friendAcceptNotice(ApiException(409, "limit")))
    @Test fun expired() = assertEquals(FriendsNotice.CodeExpired, friendAcceptNotice(ApiException(410, "expired")))
    @Test fun unknownCode() = assertEquals(FriendsNotice.AddFailed, friendAcceptNotice(ApiException(404, "unknown")))
}
