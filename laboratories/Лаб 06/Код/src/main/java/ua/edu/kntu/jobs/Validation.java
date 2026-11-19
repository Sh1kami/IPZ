package ua.edu.kntu.jobs;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Validation {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");
    private Validation() {}
    public static String text(String value, String field, int min, int max) {
        if (value == null) throw DomainException.validation("Поле «" + field + "» обов'язкове.");
        String normalized = value.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < min || length > max) throw DomainException.validation("Довжина поля «" + field + "»: " + min + "–" + max + " символів.");
        if (normalized.codePoints().anyMatch(cp -> cp == 0 || (Character.isISOControl(cp) && cp != '\n' && cp != '\r' && cp != '\t')))
            throw DomainException.validation("Поле «" + field + "» містить недопустимий керівний символ.");
        return normalized;
    }
    public static String email(String value) {
        String email = text(value, "email", 3, 254).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) throw DomainException.validation("Вкажіть коректну email-адресу.");
        return email;
    }
    public static String password(String value) {
        if (value == null || value.codePointCount(0, value.length()) < 8 || value.codePointCount(0, value.length()) > 128)
            throw DomainException.validation("Пароль повинен містити 8–128 символів.");
        return value;
    }
    public static int salary(int value) {
        if (value < 1 || value > 1_000_000) throw DomainException.validation("Зарплата: ціле число від 1 до 1000000 грн.");
        return value;
    }
    public static long id(String value) {
        try {
            if (value == null || !value.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) { throw DomainException.validation("Ідентифікатор повинен бути додатним цілим числом."); }
    }
}
