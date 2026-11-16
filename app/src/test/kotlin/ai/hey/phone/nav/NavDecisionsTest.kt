package ai.hey.phone.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavDecisionsTest {
    @Test fun `each session state has exactly one screen`() {
        assertEquals("start", routeFor(Session.SignedOut))
        assertEquals("consent", routeFor(Session.NeedsConsent))
        assertEquals("connect/none", routeFor(Session.CarLinkRequired()))
        assertEquals("connect/AB12", routeFor(Session.CarLinkRequired("AB12")))
        assertEquals("home/today", routeFor(Session.Linked()))
        assertEquals("friend/FR99", routeFor(Session.Linked("FR99")))
    }

    @Test fun `cold start by token`() {
        assertEquals(Routes.ENTRY, coldStartRoute(hasToken = true))
        assertEquals(Routes.START, coldStartRoute(hasToken = false))
    }

    @Test fun `after login the unanswered consent comes first`() {
        assertEquals(Routes.CONSENT, afterLoginRoute(needsConsent = true))
        assertEquals(Routes.ENTRY, afterLoginRoute(needsConsent = false))
    }

    @Test fun `a friend invite waits for the car and wins over today`() {
        assertEquals("home/today", todayRoute(null))
        assertEquals("friend/X1Y2", todayRoute("X1Y2"))
    }

    @Test fun `a car-link loss always lands on the scanner, a 401 on start`() {
        assertEquals("connect/none", routeFor(Session.CarLinkRequired(pendingPairCode = null)))
        assertEquals("start", routeFor(Session.SignedOut))
    }

    @Test fun `a pair link keeps its code and goes through login when signed out, to entry when signed in`() {
        assertEquals(DeepLinkPlan(pairCode = "ABCD", route = Routes.START), deepLinkPlan("connect/ABCD", hasToken = false))
        assertEquals(DeepLinkPlan(pairCode = "ABCD", route = Routes.ENTRY), deepLinkPlan("connect/ABCD", hasToken = true))
    }

    @Test fun `a friend link keeps its code the same way`() {
        assertEquals(DeepLinkPlan(friendCode = "FFFF", route = Routes.START), deepLinkPlan("friend/FFFF", hasToken = false))
        assertEquals(DeepLinkPlan(friendCode = "FFFF", route = Routes.ENTRY), deepLinkPlan("friend/FFFF", hasToken = true))
    }

    @Test fun `any other route is opened as it is and stores no code`() {
        val plan = deepLinkPlan("home/today", hasToken = true)
        assertEquals("home/today", plan.route)
        assertNull(plan.pairCode); assertNull(plan.friendCode)
    }

    @Test fun `the link parser and the plan agree on both link kinds`() {
        assertEquals("connect/ABCD", deepLinkRoute("https://hey.lastmile.ltd/p/abcd"))
        assertEquals("ABCD", deepLinkPlan(deepLinkRoute("heyparri://p/abcd")!!, hasToken = true).pairCode)
        assertEquals("FFFF", deepLinkPlan(deepLinkRoute("heyparri://f/ffff")!!, hasToken = true).friendCode)
    }
}
