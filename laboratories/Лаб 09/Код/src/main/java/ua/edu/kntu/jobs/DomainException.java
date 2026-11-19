package ua.edu.kntu.jobs;

/** Stable application error; HTTP layer serializes the code and message. */
public final class DomainException extends RuntimeException {
    private final int status;
    private final String code;
    public DomainException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static DomainException validation(String message) { return new DomainException(400, "VALIDATION", message); }
    public static DomainException forbidden() { return new DomainException(403, "FORBIDDEN", "Ця дія недоступна для поточного користувача."); }
}
