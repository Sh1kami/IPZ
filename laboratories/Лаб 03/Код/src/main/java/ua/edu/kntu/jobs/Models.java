package ua.edu.kntu.jobs;

public final class Models {
    private Models() {}
    public enum Role { SEEKER, EMPLOYER }
    public enum JobStatus { ACTIVE, CLOSED }
    public record User(long id, String name, String email, Role role, String createdAt) {}
    public record Resume(long userId, String title, String text, String skills, String updatedAt) {}
    public record Job(long id, long ownerId, String title, String company, String city, int salary,
                      boolean remote, String description, JobStatus status, String createdAt) {}
    public record Application(long id, long jobId, long seekerId, String coverLetter,
                              String resumeTitle, String resumeText, String resumeSkills,
                              String createdAt, String jobTitle, String company) {}
    public record StoredUser(User user, String passwordHash) {}
    public record Login(User user, String token) {}
}
