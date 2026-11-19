package ua.edu.kntu.jobs;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import ua.edu.kntu.jobs.Models.Job;

/** Pure domain search. Unicode case normalization does not depend on SQLite ASCII LIKE. */
public record JobFilter(String query, String city, int minSalary, Boolean remote, String status) {
    public JobFilter {
        query = Validation.text(query == null ? "" : query, "query", 0, 200).toLowerCase(Locale.ROOT);
        city = Validation.text(city == null ? "" : city, "city", 0, 80).toLowerCase(Locale.ROOT);
        if (minSalary < 0 || minSalary > 1_000_000) throw DomainException.validation("minSalary: 0–1000000.");
        if (status == null) status = "ACTIVE";
        if (!List.of("ACTIVE", "CLOSED", "ALL").contains(status)) throw DomainException.validation("status: ACTIVE, CLOSED або ALL.");
    }
    public static JobFilter from(Map<String, String> params) {
        int salary = 0;
        if (params.containsKey("minSalary")) {
            String value = params.get("minSalary");
            try { if (!value.matches("[0-9]+")) throw new NumberFormatException(); salary = Integer.parseInt(value); }
            catch (NumberFormatException e) { throw DomainException.validation("minSalary повинен бути цілим невід'ємним числом."); }
        }
        Boolean remote = null;
        if (params.containsKey("remote")) {
            String value = params.get("remote");
            if (!value.equals("true") && !value.equals("false")) throw DomainException.validation("remote: true або false.");
            remote = Boolean.valueOf(value);
        }
        return new JobFilter(params.get("query"), params.get("city"), salary, remote, params.get("status"));
    }
    public boolean matches(Job job) {
        return (status.equals("ALL") || job.status().name().equals(status))
            && (query.isEmpty() || job.title().toLowerCase(Locale.ROOT).contains(query)
                || job.company().toLowerCase(Locale.ROOT).contains(query) || job.description().toLowerCase(Locale.ROOT).contains(query))
            && (city.isEmpty() || job.city().toLowerCase(Locale.ROOT).equals(city))
            && job.salary() >= minSalary && (remote == null || job.remote() == remote);
    }
}
