package ua.edu.kntu.jobs;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import ua.edu.kntu.jobs.Models.*;

public final class JobPortalService {
    private static final String DUMMY_HASH = new PasswordHasher().hash("synthetic-dummy-password");
    private final SQLiteRepository repository;
    private final PasswordHasher hasher;
    private final Clock clock;
    public JobPortalService(SQLiteRepository repository) { this(repository, new PasswordHasher(), Clock.systemUTC()); }
    public JobPortalService(SQLiteRepository repository, PasswordHasher hasher, Clock clock) { this.repository = repository; this.hasher = hasher; this.clock = clock; }
    public User register(String name, String email, String password, String role) {
        String validName = Validation.text(name, "name", 2, 80), validEmail = Validation.email(email), validPassword = Validation.password(password);
        Role validRole = role(role);
        return repository.insertUser(validName, validEmail, validRole, hasher.hash(validPassword), clock.instant().toString());
    }
    public Login login(String email, String password) {
        String validEmail = Validation.email(email), validPassword = Validation.password(password);
        StoredUser stored = repository.userByEmail(validEmail).orElse(null);
        // A missing user still incurs the password KDF, with a process-local dummy hash.
        boolean matches = hasher.verify(validPassword, stored == null ? DUMMY_HASH : stored.passwordHash());
        if (stored == null || !matches) throw new DomainException(401, "INVALID_CREDENTIALS", "Неправильна email-адреса або пароль.");
        String token = hasher.newToken(); repository.insertSession(PasswordHasher.tokenDigest(token), stored.user().id(), clock.millis() + Duration.ofHours(8).toMillis());
        return new Login(stored.user(), token);
    }
    public User authenticate(String token) {
        if (token == null || token.isBlank()) throw unauthorized();
        return repository.sessionUser(PasswordHasher.tokenDigest(token), clock.millis()).orElseThrow(this::unauthorized);
    }
    public void logout(String token) { authenticate(token); repository.deleteSession(PasswordHasher.tokenDigest(token)); }
    public Resume saveResume(User user, String title, String text, String skills) {
        requireRole(user, Role.SEEKER);
        Resume resume = new Resume(user.id(), Validation.text(title, "title", 5, 120), Validation.text(text, "text", 20, 10000), Validation.text(skills == null ? "" : skills, "skills", 0, 1000), clock.instant().toString());
        return repository.saveResume(resume);
    }
    public Resume resume(User user) { requireRole(user, Role.SEEKER); return repository.resume(user.id()).orElseThrow(() -> new DomainException(404, "RESUME_NOT_FOUND", "Резюме ще не створено.")); }
    public Job createJob(User user, String title, String company, String city, int salary, boolean remote, String description) {
        requireRole(user, Role.EMPLOYER);
        return repository.insertJob(user.id(), Validation.text(title, "title", 5, 120), Validation.text(company, "company", 2, 120), Validation.text(city, "city", 2, 80), Validation.salary(salary), remote, Validation.text(description, "description", 20, 10000), clock.instant().toString());
    }
    public List<Job> jobs(JobFilter filter) { return repository.jobs().stream().filter(filter::matches).toList(); }
    public Job job(long id) { return repository.job(id).orElseThrow(() -> new DomainException(404, "JOB_NOT_FOUND", "Вакансію не знайдено.")); }
    public Job changeJobStatus(User user, long id, String status) {
        requireRole(user, Role.EMPLOYER);
        JobStatus valid;
        try { valid = JobStatus.valueOf(status == null ? "" : status); } catch (IllegalArgumentException e) { throw DomainException.validation("status: ACTIVE або CLOSED."); }
        return repository.changeJobStatus(id, user.id(), valid);
    }
    public Application apply(User user, long id, String coverLetter) {
        requireRole(user, Role.SEEKER);
        return repository.insertApplication(id, user.id(), Validation.text(coverLetter == null ? "" : coverLetter, "coverLetter", 0, 2000), clock.instant().toString());
    }
    public List<Application> applications(User user) { requireRole(user, Role.SEEKER); return repository.applications(user.id()); }
    public static void requireRole(User user, Role role) { if (user == null || user.role() != role) throw DomainException.forbidden(); }
    private Role role(String role) { try { return Role.valueOf(role == null ? "" : role); } catch (IllegalArgumentException e) { throw DomainException.validation("role: SEEKER або EMPLOYER."); } }
    private DomainException unauthorized() { return new DomainException(401, "UNAUTHORIZED", "Потрібно увійти до облікового запису."); }
    public void seedDemo() {
        if (!repository.jobs().isEmpty()) return;
        User employer = repository.userByEmail("employer@example.test").map(StoredUser::user).orElseGet(() -> register("Навчальний роботодавець", "employer@example.test", "DemoPass26!", "EMPLOYER"));
        createJob(employer, "Java Developer", "Example Engineering", "Київ", 40000, false, "Навчальна вакансія: розробка Java застосунків і робота з SQL.");
        createJob(employer, "Python Developer", "Example Analytics", "Львів", 50000, true, "Навчальна вакансія: автоматизація обробки даних мовою Python.");
        createJob(employer, "Junior Java Developer", "Example Academy", "Київ", 30000, true, "Навчальна вакансія: початкова розробка Java та написання тестів.");
        Job closed = createJob(employer, "Java Developer Closed", "Example Archive", "Київ", 60000, false, "Навчальна закрита вакансія для перевірки відмови у відгуку.");
        changeJobStatus(employer, closed.id(), "CLOSED");
    }
}
