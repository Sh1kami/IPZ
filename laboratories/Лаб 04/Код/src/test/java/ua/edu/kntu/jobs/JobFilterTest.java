package ua.edu.kntu.jobs;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;
import ua.edu.kntu.jobs.Models.Job;
import ua.edu.kntu.jobs.Models.JobStatus;
import static org.junit.Assert.*;

/** Tests the selection predicate; API/repository tests own the ascending-id order oracle. */
public class JobFilterTest {
    @Test
    public void defaultFilterSelectsActiveJobsOfBothWorkModes() {
        JobFilter filter = JobFilter.from(Map.of());
        assertEquals("", filter.query());
        assertEquals("", filter.city());
        assertEquals(0, filter.minSalary());
        assertNull(filter.remote());
        assertEquals("ACTIVE", filter.status());
        assertTrue(filter.matches(job(1, "Java developer", "Acme", "\u041A\u0438\u0457\u0432", 1, true, "Description", JobStatus.ACTIVE)));
        assertTrue(filter.matches(job(2, "Java developer", "Acme", "\u041A\u0438\u0457\u0432", 1, false, "Description", JobStatus.ACTIVE)));
        assertFalse(filter.matches(job(3, "Java developer", "Acme", "\u041A\u0438\u0457\u0432", 1, false, "Description", JobStatus.CLOSED)));
    }

    @Test
    public void querySearchesTitleCompanyAndDescriptionButNotCity() {
        JobFilter filter = new JobFilter(" java ", null, 0, null, "ALL");
        assertTrue(filter.matches(job(1, "JAVA developer", "Acme", "Paris", 1, false, "Description", JobStatus.ACTIVE)));
        assertTrue(filter.matches(job(2, "Backend developer", "Java Company", "Paris", 1, false, "Description", JobStatus.ACTIVE)));
        assertTrue(filter.matches(job(3, "Backend developer", "Acme", "Paris", 1, false, "Build with JaVa", JobStatus.ACTIVE)));
        assertFalse(filter.matches(job(4, "Backend developer", "Acme", "Java City", 1, false, "Description", JobStatus.ACTIVE)));
    }

    @Test
    public void queryAndCityMatchCyrillicWithRootLocaleNormalization() {
        JobFilter filter = JobFilter.from(Map.of("query", "  \u0406\u041D\u0416\u0415\u041D\u0415\u0420  ", "city", " \u043A\u0438\u0457\u0432 "));
        assertEquals("\u0456\u043D\u0436\u0435\u043D\u0435\u0440", filter.query());
        assertEquals("\u043A\u0438\u0457\u0432", filter.city());
        assertTrue(filter.matches(job(1, "\u0406\u043D\u0436\u0435\u043D\u0435\u0440 Java", "Acme", "\u041A\u0418\u0407\u0412", 20_000, false, "Description", JobStatus.ACTIVE)));
        assertFalse(filter.matches(job(2, "\u0406\u043D\u0436\u0435\u043D\u0435\u0440 Java", "Acme", "\u041A\u0438\u0457\u0432\u0441\u044C\u043A\u0438\u0439", 20_000, false, "Description", JobStatus.ACTIVE)));
    }

    @Test
    public void matchingDoesNotDependOnTheProcessDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            JobFilter filter = new JobFilter("INTERN", "IZMIR", 0, null, "ALL");
            assertTrue(filter.matches(job(1, "Intern developer", "Acme", "Izmir", 1, false, "Description", JobStatus.ACTIVE)));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void wildcardAndSqlLikeCharactersAreLiteralSubstrings() {
        String[][] pairs = {
            {"100%", "Developer 100% remote", "Developer 100x remote"},
            {"QA_TEST", "QA_TEST engineer", "QAATEST engineer"},
            {"' OR 1=1--", "Literal ' OR 1=1-- example", "Ordinary vacancy"},
            {"[a-z]", "Literal [a-z] text", "Literal alphabet text"},
            {".*", "Literal .* text", "Literal anything text"},
            {"\\", "Literal \\ text", "Literal slash / text"}
        };
        for (String[] pair : pairs) {
            JobFilter filter = new JobFilter(pair[0], "", 0, null, "ALL");
            assertTrue(pair[0], filter.matches(job(1, pair[1], "Acme", "Kyiv", 1, false, "Description", JobStatus.ACTIVE)));
            assertFalse(pair[0], filter.matches(job(2, pair[2], "Acme", "Kyiv", 1, false, "Description", JobStatus.ACTIVE)));
        }
    }

    @Test
    public void everyFilterCombinesWithAndAndTheSalaryThresholdIsInclusive() {
        JobFilter filter = JobFilter.from(Map.of("query", "java", "city", "Kyiv", "minSalary", "20000", "remote", "false", "status", "ACTIVE"));
        List<Job> candidates = List.of(
            job(1, "Java developer", "Acme", "Kyiv", 20_000, false, "Description", JobStatus.ACTIVE),
            job(2, "Python developer", "Acme", "Kyiv", 20_000, false, "Description", JobStatus.ACTIVE),
            job(3, "Java developer", "Acme", "Lviv", 20_000, false, "Description", JobStatus.ACTIVE),
            job(4, "Java developer", "Acme", "Kyiv", 19_999, false, "Description", JobStatus.ACTIVE),
            job(5, "Java developer", "Acme", "Kyiv", 20_000, true, "Description", JobStatus.ACTIVE),
            job(6, "Java developer", "Acme", "Kyiv", 20_000, false, "Description", JobStatus.CLOSED)
        );
        assertEquals(List.of(1L), candidates.stream().filter(filter::matches).map(Job::id).toList());
    }

    @Test
    public void trueAndFalseRemoteFiltersAreDifferentFromAnOmittedFilter() {
        Job remote = job(1, "Java developer", "Acme", "Kyiv", 1, true, "Description", JobStatus.ACTIVE);
        Job onSite = job(2, "Java developer", "Acme", "Kyiv", 1, false, "Description", JobStatus.ACTIVE);
        JobFilter remoteOnly = JobFilter.from(Map.of("remote", "true"));
        JobFilter onSiteOnly = JobFilter.from(Map.of("remote", "false"));
        assertEquals(Boolean.TRUE, remoteOnly.remote());
        assertEquals(Boolean.FALSE, onSiteOnly.remote());
        assertTrue(remoteOnly.matches(remote));
        assertFalse(remoteOnly.matches(onSite));
        assertFalse(onSiteOnly.matches(remote));
        assertTrue(onSiteOnly.matches(onSite));
    }

    @Test
    public void allStatusIncludesBothAndClosedStatusExcludesActive() {
        Job active = job(1, "Java developer", "Acme", "Kyiv", 1, false, "Description", JobStatus.ACTIVE);
        Job closed = job(2, "Java developer", "Acme", "Kyiv", 1, false, "Description", JobStatus.CLOSED);
        JobFilter all = JobFilter.from(Map.of("status", "ALL"));
        JobFilter closedOnly = JobFilter.from(Map.of("status", "CLOSED"));
        assertTrue(all.matches(active));
        assertTrue(all.matches(closed));
        assertFalse(closedOnly.matches(active));
        assertTrue(closedOnly.matches(closed));
    }

    @Test
    public void queryAndCityLimitsCountCodePointsAfterTrim() {
        String point = "\uD83E\uDDEA";
        assertEquals(point.repeat(200), new JobFilter(" " + point.repeat(200) + " ", "", 0, null, "ALL").query());
        assertEquals(point.repeat(80), new JobFilter("", " " + point.repeat(80) + " ", 0, null, "ALL").city());
        validation(() -> new JobFilter(point.repeat(201), "", 0, null, "ALL"));
        validation(() -> new JobFilter("", point.repeat(81), 0, null, "ALL"));
    }

    @Test
    public void minimumSalaryZeroAndMaximumAreAllowed() {
        assertEquals(0, JobFilter.from(Map.of("minSalary", "0")).minSalary());
        assertEquals(1_000_000, JobFilter.from(Map.of("minSalary", "1000000")).minSalary());
        JobFilter maximum = JobFilter.from(Map.of("minSalary", "1000000"));
        assertTrue(maximum.matches(job(1, "Java developer", "Acme", "Kyiv", 1_000_000, false, "Description", JobStatus.ACTIVE)));
        assertFalse(maximum.matches(job(2, "Java developer", "Acme", "Kyiv", 999_999, false, "Description", JobStatus.ACTIVE)));
        validation(() -> new JobFilter("", "", -1, null, "ALL"));
        validation(() -> new JobFilter("", "", 1_000_001, null, "ALL"));
    }

    @Test
    public void malformedSalaryRemoteAndStatusUseStableValidationErrors() {
        for (String value : List.of("-1", "+1", "1.5", "1e2", "", "abc", "1000001", "2147483648")) {
            validation(() -> JobFilter.from(Map.of("minSalary", value)));
        }
        for (String value : List.of("TRUE", "False", "1", "0", "", "yes")) {
            validation(() -> JobFilter.from(Map.of("remote", value)));
        }
        for (String value : List.of("active", "closed", "all", "", "OPEN")) {
            validation(() -> JobFilter.from(Map.of("status", value)));
        }
    }

    private static Job job(long id, String title, String company, String city, int salary,
                           boolean remote, String description, JobStatus status) {
        return new Job(id, 99L, title, company, city, salary, remote, description, status, "2026-10-13T00:00:00Z");
    }

    private static void validation(Runnable action) {
        DomainException error = assertThrows(DomainException.class, action::run);
        assertEquals(400, error.status());
        assertEquals("VALIDATION", error.code());
    }
}
