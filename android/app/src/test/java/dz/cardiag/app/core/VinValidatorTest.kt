package dz.cardiag.app.core

import org.junit.Assert.*
import org.junit.Test

class VinValidatorTest {

    // --- normalize ---

    @Test fun normalizeUppercases() = assertEquals("ABCDEFGHJKLMNPRST", VinValidator.normalize("abcdefghjklmnprst"))
    @Test fun normalizeTrimsWhitespace() = assertEquals("ABCDEFGHJKLMNPRST", VinValidator.normalize("  ABCDEFGHJKLMNPRST  "))
    @Test fun normalizeRemovesSpaces() = assertEquals("ABCDEFGHJKLMNPRST", VinValidator.normalize("ABC DEF GHJ KLM NPR ST"))
    @Test fun normalizePreservesDigits() = assertEquals("1A2B3C4D5E6F7G8H9", VinValidator.normalize("1a2b3c4d5e6f7g8h9"))
    @Test fun normalizePreservesValidLetters() = assertEquals("ABCDEFGHJKLMNPRSTUVWXYZ0123456789", VinValidator.normalize("ABCDEFGHJKLMNPRSTUVWXYZ0123456789"))

    // --- isValid ---

    @Test fun acceptsValid17CharVin() = assertTrue(VinValidator.isValid("VF1AAAA1A2B345678"))
    @Test fun acceptsValidAlgerianVinPrefix() = assertTrue(VinValidator.isValid("WVWZZZ3CZWE123456"))
    @Test fun acceptsAllDigits() = assertTrue(VinValidator.isValid("00000000000000000"))

    @Test fun rejectsTooShort() = assertFalse(VinValidator.isValid("VF1AAAA1A2B34567"))
    @Test fun rejectsTooLong() = assertFalse(VinValidator.isValid("VF1AAAA1A2B3456789"))
    @Test fun rejectsEmpty() = assertFalse(VinValidator.isValid(""))
    @Test fun rejectsBlank() = assertFalse(VinValidator.isValid("   "))

    @Test fun rejectsForbiddenLetterI() = assertFalse(VinValidator.isValid("VF1AAAA1A2B3456I8"))
    @Test fun rejectsForbiddenLetterO() = assertFalse(VinValidator.isValid("VF1AAAA1A2B345O78"))
    @Test fun rejectsForbiddenLetterQ() = assertFalse(VinValidator.isValid("VF1AAAA1A2B34Q678"))
    @Test fun rejectsLowercaseForbiddenLetters() = assertFalse(VinValidator.isValid("vfiAAAA1A2B345678"))

    @Test fun acceptsLowercaseViaNormalize() = assertTrue(VinValidator.isValid("vf1aaaa1a2b345678"))
    @Test fun rejectsSpecialCharacters() = assertFalse(VinValidator.isValid("VF1AAAA1A2B34567!"))
    @Test fun rejectsSpacesInMiddle() = assertFalse(VinValidator.isValid("VF1AAAA  A2B345678"))

    @Test fun isValidCallsNormalizeFirst() {
        assertTrue(VinValidator.isValid("  vf1aaaa1a2b345678  "))
    }
}
