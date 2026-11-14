package ai.hey.phone.feature.pair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairCodeTest {
    @Test fun qrLink() = assertEquals("K7M2QX9P", parsePairCode("https://hey.lastmile.ltd/p/K7M2QX9P"))

    @Test fun customScheme() = assertEquals("K7M2QX9P", parsePairCode("heyparri://p/k7m2qx9p"))

    @Test fun bareTypedCodeIsUpperCased() = assertEquals("K7M2QX9P", parsePairCode("  k7m2qx9p "))

    @Test fun linkWithTrailingParts() = assertEquals("K7M2QX9P", parsePairCode("https://hey.lastmile.ltd/p/K7M2QX9P?x=1"))

    @Test fun notACode() {
        assertNull(parsePairCode(null))
        assertNull(parsePairCode(""))
        assertNull(parsePairCode("hello world"))
        assertNull(parsePairCode("https://example.com/p/K7M2QX9P"))
        assertNull(parsePairCode("https://hey.lastmile.ltd/p/done"))
        assertNull(parsePairCode("done"))
    }
}
