package ai.hey.phone.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkRouteTest {
    @Test fun friendAppLinkOpensAcceptRoute() = assertEquals("friend/K7M2QX", deepLinkRoute("https://hey.lastmile.ltd/f/k7m2qx"))

    @Test fun friendCustomSchemeOpensAcceptRoute() = assertEquals("friend/K7M2QX", deepLinkRoute("heyparri://f/K7M2QX"))

    @Test fun friendLinkWithQuery() = assertEquals("friend/K7M2QX", deepLinkRoute("https://hey.lastmile.ltd/f/K7M2QX?utm=x"))

    @Test fun pairLinksStillOpenJoin() {
        assertEquals("join/K7M2QX9P", deepLinkRoute("https://hey.lastmile.ltd/p/K7M2QX9P"))
        assertEquals("join/K7M2QX9P", deepLinkRoute("heyparri://p/k7m2qx9p"))
    }

    @Test fun notALink() {
        assertNull(deepLinkRoute(null))
        assertNull(deepLinkRoute("https://hey.lastmile.ltd/p/done"))
        assertNull(deepLinkRoute("https://example.com/f/K7M2QX"))
        assertNull(deepLinkRoute("https://hey.lastmile.ltd/f/"))
        assertNull(deepLinkRoute("heyparri://x/K7M2QX"))
    }
}
