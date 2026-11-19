package ua.edu.kntu.jobs;

import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

/** Boundary oracles from API-contract.json; no database or HTTP is required. */
public class ValidationTest {
    private static final String SUPPLEMENTARY = "\uD83E\uDDEA";

    @Test
    public void textBoundsCountUnicodeCodePointsAfterTrim() {
        Object[][] limits = {
            {"name", 2, 80}, {"resumeTitle", 5, 120}, {"resumeText", 20, 10000},
            {"resumeSkills", 0, 1000}, {"jobTitle", 5, 120}, {"company", 2, 120},
            {"city", 2, 80}, {"jobDescription", 20, 10000}, {"coverLetter", 0, 2000}
        };
        for (Object[] limit : limits) {
            String field = (String) limit[0];
            int min = (Integer) limit[1], max = (Integer) limit[2];
            String minimum = SUPPLEMENTARY.repeat(min), maximum = SUPPLEMENTARY.repeat(max);
            assertEquals(field + " lower boundary", minimum,
                    Validation.text(" \t" + minimum + "\r\n ", field, min, max));
            assertEquals(field + " upper boundary", maximum,
                    Validation.text("  " + maximum + "  ", field, min, max));
            if (min > 0) {
                validation(() -> Validation.text(SUPPLEMENTARY.repeat(min - 1), field, min, max));
            }
            validation(() -> Validation.text(SUPPLEMENTARY.repeat(max + 1), field, min, max));
        }
    }

    @Test
    public void requiredTextRejectsNullAndTrimmedBlank() {
        validation(() -> Validation.text(null, "name", 2, 80));
        validation(() -> Validation.text(" \t\r\n ", "name", 2, 80));
        assertEquals("", Validation.text(" \t\r\n ", "skills", 0, 1000));
    }

    @Test
    public void printableInputRemainsLiteralRatherThanBecomingExecutableMarkup() {
        String literal = "<img src=x onerror=\"alert('XSS')\"> ' OR 1=1-- 100%_";
        assertEquals(literal, Validation.text("  " + literal + "  ", "text", 0, 120));
    }

    @Test
    public void passwordCountsUnicodeCodePointsAndPreservesWhitespace() {
        assertEquals(" secret ", Validation.password(" secret "));
        assertEquals("        ", Validation.password("        "));
        assertEquals(SUPPLEMENTARY.repeat(8), Validation.password(SUPPLEMENTARY.repeat(8)));
        assertEquals(SUPPLEMENTARY.repeat(128), Validation.password(SUPPLEMENTARY.repeat(128)));
        validation(() -> Validation.password(SUPPLEMENTARY.repeat(7)));
        validation(() -> Validation.password(SUPPLEMENTARY.repeat(129)));
    }

    @Test
    public void passwordAsciiBoundsAreInclusiveAndMissingPasswordIsInvalid() {
        assertEquals("a".repeat(8), Validation.password("a".repeat(8)));
        assertEquals("a".repeat(128), Validation.password("a".repeat(128)));
        validation(() -> Validation.password(null));
        validation(() -> Validation.password(""));
        validation(() -> Validation.password("a".repeat(7)));
        validation(() -> Validation.password("a".repeat(129)));
    }

    @Test
    public void emailTrimsAndUsesRootLocaleEvenWhenDefaultLocaleIsTurkish() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("ivy+test@example.test", Validation.email(" \tIVY+TEST@EXAMPLE.TEST \n"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void emailAcceptsContractPatternAndExactLengthLimit() {
        assertEquals("o'neil+tag@example.test", Validation.email("O'Neil+Tag@EXAMPLE.TEST"));
        assertEquals("a@sub-domain.example", Validation.email("a@sub-domain.example"));
        String longest = "a".repeat(250) + "@b.c";
        assertEquals(254, longest.length());
        assertEquals(longest, Validation.email(" " + longest + " "));
        validation(() -> Validation.email("a".repeat(251) + "@b.c"));
    }

    @Test
    public void emailRejectsAddressesOutsideTheExplicitContractPattern() {
        String[] malformed = {null, "", " ", "missing-at.example", "a@localhost", "@b.c",
            "a@@b.c", "a b@b.c", "a@-b.c", "a@b-.c", "a@b..c", "a@b.c.", "\u0410@b.c"};
        for (String email : malformed) validation(() -> Validation.email(email));
    }

    @Test
    public void salaryAcceptsBothInclusiveBoundsAndRejectsOutsideValues() {
        assertEquals(1, Validation.salary(1));
        assertEquals(1_000_000, Validation.salary(1_000_000));
        for (int salary : new int[]{Integer.MIN_VALUE, -1, 0, 1_000_001, Integer.MAX_VALUE}) {
            validation(() -> Validation.salary(salary));
        }
    }

    @Test
    public void idAcceptsPositiveIntegersIncludingTheStorageLimit() {
        assertEquals(1L, Validation.id("1"));
        assertEquals(42L, Validation.id("00042"));
        assertEquals(Long.MAX_VALUE, Validation.id(Long.toString(Long.MAX_VALUE)));
    }

    @Test
    public void malformedOrNonPositiveIdUsesTheValidationError() {
        String[] malformed = {null, "", "0", "000", "-1", "+1", "1.0", "1e2", "abc",
            " 1", "1 ", "\u0661", "9223372036854775808"};
        for (String id : malformed) validation(() -> Validation.id(id));
    }

    private static void validation(Runnable action) {
        DomainException error = assertThrows(DomainException.class, action::run);
        assertEquals(400, error.status());
        assertEquals("VALIDATION", error.code());
        assertNotNull(error.getMessage());
        assertFalse(error.getMessage().isBlank());
    }
}
