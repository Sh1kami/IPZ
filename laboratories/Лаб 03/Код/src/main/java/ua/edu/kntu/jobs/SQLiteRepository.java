package ua.edu.kntu.jobs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ua.edu.kntu.jobs.Models.*;

/** Actual SQLite persistence; no database mock and no external database server. */
public final class SQLiteRepository implements AutoCloseable {
    private final Path database;
    public SQLiteRepository(Path database) {
        this.database = database.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.database.getParent());
            Class.forName("org.sqlite.JDBC");
            initialize();
        } catch (IOException | ClassNotFoundException e) { throw new IllegalStateException("Cannot open SQLite", e); }
    }
    public Path databasePath() { return database; }
    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }
    private void initialize() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, role TEXT NOT NULL CHECK(role IN ('SEEKER','EMPLOYER')), password_hash TEXT NOT NULL, created_at TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS sessions (token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id), expires_at INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS resumes (user_id INTEGER PRIMARY KEY REFERENCES users(id), title TEXT NOT NULL, text TEXT NOT NULL, skills TEXT NOT NULL, updated_at TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS jobs (id INTEGER PRIMARY KEY AUTOINCREMENT, owner_id INTEGER NOT NULL REFERENCES users(id), title TEXT NOT NULL, company TEXT NOT NULL, city TEXT NOT NULL, salary INTEGER NOT NULL CHECK(salary BETWEEN 1 AND 1000000), remote INTEGER NOT NULL CHECK(remote IN (0,1)), description TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('ACTIVE','CLOSED')), created_at TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS applications (id INTEGER PRIMARY KEY AUTOINCREMENT, job_id INTEGER NOT NULL REFERENCES jobs(id), seeker_id INTEGER NOT NULL REFERENCES users(id), cover_letter TEXT NOT NULL, resume_title TEXT NOT NULL, resume_text TEXT NOT NULL, resume_skills TEXT NOT NULL, created_at TEXT NOT NULL, UNIQUE(job_id,seeker_id))");
            s.execute("CREATE INDEX IF NOT EXISTS applications_seeker ON applications(seeker_id)");
        } catch (SQLException e) { throw databaseError(e); }
    }
    public User insertUser(String name, String email, Role role, String passwordHash, String createdAt) {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("INSERT INTO users(name,email,role,password_hash,created_at) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, name); p.setString(2, email); p.setString(3, role.name()); p.setString(4, passwordHash); p.setString(5, createdAt); p.executeUpdate();
            return new User(key(p), name, email, role, createdAt);
        } catch (SQLException e) {
            if (e.getErrorCode() == 19 && e.getMessage().contains("users.email")) throw new DomainException(409, "EMAIL_EXISTS", "Ця email-адреса вже зареєстрована.");
            throw databaseError(e);
        }
    }
    public Optional<StoredUser> userByEmail(String email) {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("SELECT * FROM users WHERE email=?")) {
            p.setString(1, email); try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(new StoredUser(user(r), r.getString("password_hash"))) : Optional.empty(); }
        } catch (SQLException e) { throw databaseError(e); }
    }
    public void insertSession(String tokenHash, long userId, long expiresAt) {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,?)")) {
            p.setString(1, tokenHash); p.setLong(2, userId); p.setLong(3, expiresAt); p.executeUpdate();
        } catch (SQLException e) { throw databaseError(e); }
    }
    public Optional<User> sessionUser(String tokenHash, long now) {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("SELECT u.* FROM users u JOIN sessions s ON s.user_id=u.id WHERE s.token_hash=? AND s.expires_at>?")) {
            p.setString(1, tokenHash); p.setLong(2, now); try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(user(r)) : Optional.empty(); }
        } catch (SQLException e) { throw databaseError(e); }
    }
    public void deleteSession(String tokenHash) {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("DELETE FROM sessions WHERE token_hash=?")) { p.setString(1, tokenHash); p.executeUpdate(); }
        catch (SQLException e) { throw databaseError(e); }
    }
    public Resume saveResume(Resume resume) {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("INSERT INTO resumes(user_id,title,text,skills,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET title=excluded.title,text=excluded.text,skills=excluded.skills,updated_at=excluded.updated_at")) {
            p.setLong(1, resume.userId()); p.setString(2, resume.title()); p.setString(3, resume.text()); p.setString(4, resume.skills()); p.setString(5, resume.updatedAt()); p.executeUpdate(); return resume;
        } catch (SQLException e) { throw databaseError(e); }
    }
    public Optional<Resume> resume(long userId) {
        try (Connection c = open()) { return resume(c, userId); }
        catch (SQLException e) { throw databaseError(e); }
    }
    private Optional<Resume> resume(Connection c, long userId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM resumes WHERE user_id=?")) {
            p.setLong(1, userId); try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(new Resume(userId, r.getString("title"), r.getString("text"), r.getString("skills"), r.getString("updated_at"))) : Optional.empty(); }
        }
    }
    public Job insertJob(long ownerId, String title, String company, String city, int salary, boolean remote, String description, String createdAt) {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("INSERT INTO jobs(owner_id,title,company,city,salary,remote,description,status,created_at) VALUES(?,?,?,?,?,?,?,'ACTIVE',?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setLong(1, ownerId); p.setString(2, title); p.setString(3, company); p.setString(4, city); p.setInt(5, salary); p.setBoolean(6, remote); p.setString(7, description); p.setString(8, createdAt); p.executeUpdate();
            return new Job(key(p), ownerId, title, company, city, salary, remote, description, JobStatus.ACTIVE, createdAt);
        } catch (SQLException e) { throw databaseError(e); }
    }
    public List<Job> jobs() {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("SELECT * FROM jobs ORDER BY id")) {
            List<Job> list = new ArrayList<>(); try (ResultSet r = p.executeQuery()) { while (r.next()) list.add(job(r)); } return list;
        } catch (SQLException e) { throw databaseError(e); }
    }
    public Optional<Job> job(long id) {
        try (Connection c = open()) { return job(c, id); }
        catch (SQLException e) { throw databaseError(e); }
    }
    private Optional<Job> job(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM jobs WHERE id=?")) { p.setLong(1, id); try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(job(r)) : Optional.empty(); } }
    }
    public synchronized Job changeJobStatus(long id, long ownerId, JobStatus status) {
        try (Connection c = open()) {
            Job old = job(c, id).orElseThrow(() -> new DomainException(404, "JOB_NOT_FOUND", "Вакансію не знайдено."));
            if (old.ownerId() != ownerId) throw DomainException.forbidden();
            try (PreparedStatement p = c.prepareStatement("UPDATE jobs SET status=? WHERE id=? AND owner_id=?")) { p.setString(1, status.name()); p.setLong(2, id); p.setLong(3, ownerId); p.executeUpdate(); }
            return new Job(old.id(), old.ownerId(), old.title(), old.company(), old.city(), old.salary(), old.remote(), old.description(), status, old.createdAt());
        } catch (SQLException e) { throw databaseError(e); }
    }
    /** Status, resume snapshot and unique insertion are checked inside one real transaction. */
    public synchronized Application insertApplication(long jobId, long seekerId, String coverLetter, String createdAt) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                Job job = job(c, jobId).orElseThrow(() -> new DomainException(404, "JOB_NOT_FOUND", "Вакансію не знайдено."));
                if (job.status() != JobStatus.ACTIVE) throw new DomainException(409, "JOB_CLOSED", "Цю вакансію закрито.");
                Resume resume = resume(c, seekerId).orElseThrow(() -> new DomainException(409, "RESUME_REQUIRED", "Спочатку збережіть своє резюме."));
                try (PreparedStatement p = c.prepareStatement("INSERT INTO applications(job_id,seeker_id,cover_letter,resume_title,resume_text,resume_skills,created_at) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    p.setLong(1, jobId); p.setLong(2, seekerId); p.setString(3, coverLetter); p.setString(4, resume.title()); p.setString(5, resume.text()); p.setString(6, resume.skills()); p.setString(7, createdAt); p.executeUpdate();
                    Application app = new Application(key(p), jobId, seekerId, coverLetter, resume.title(), resume.text(), resume.skills(), createdAt, job.title(), job.company());
                    c.commit(); return app;
                }
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                if (e instanceof SQLException sql && sql.getErrorCode() == 19 && sql.getMessage().contains("applications.job_id"))
                    throw new DomainException(409, "DUPLICATE_APPLICATION", "Ви вже надіслали відгук на цю вакансію.");
                throw e;
            }
        } catch (SQLException e) { throw databaseError(e); }
    }
    public List<Application> applications(long seekerId) {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("SELECT a.*,j.title AS job_title,j.company AS company FROM applications a JOIN jobs j ON j.id=a.job_id WHERE a.seeker_id=? ORDER BY a.id")) {
            p.setLong(1, seekerId); List<Application> list = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) { while (r.next()) list.add(new Application(r.getLong("id"), r.getLong("job_id"), r.getLong("seeker_id"), r.getString("cover_letter"), r.getString("resume_title"), r.getString("resume_text"), r.getString("resume_skills"), r.getString("created_at"), r.getString("job_title"), r.getString("company"))); } return list;
        } catch (SQLException e) { throw databaseError(e); }
    }
    private User user(ResultSet r) throws SQLException { return new User(r.getLong("id"), r.getString("name"), r.getString("email"), Role.valueOf(r.getString("role")), r.getString("created_at")); }
    private Job job(ResultSet r) throws SQLException { return new Job(r.getLong("id"), r.getLong("owner_id"), r.getString("title"), r.getString("company"), r.getString("city"), r.getInt("salary"), r.getBoolean("remote"), r.getString("description"), JobStatus.valueOf(r.getString("status")), r.getString("created_at")); }
    private long key(PreparedStatement p) throws SQLException { try (ResultSet r = p.getGeneratedKeys()) { if (!r.next()) throw new SQLException("Missing generated key"); return r.getLong(1); } }
    private IllegalStateException databaseError(SQLException e) { return new IllegalStateException("SQLite operation failed", e); }
    @Override public void close() { /* Each operation owns and closes its connection. */ }
}
