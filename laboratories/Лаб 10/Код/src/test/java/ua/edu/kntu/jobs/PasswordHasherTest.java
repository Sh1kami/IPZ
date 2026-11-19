package ua.edu.kntu.jobs;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the real PBKDF2 implementation and real secure-random token generation. */
public class PasswordHasherTest {
    private static final String PASSWORD = "  \u0421\u0435\u043A\u0440\u0435\u0442 \uD83E\uDDEA  ";
    private static final PasswordHasher HASHER = new PasswordHasher();
    private static String firstHash;
    private static String secondHash;

    @BeforeClass
    public static void createRealHashesOnce() {
        firstHash = HASHER.hash(PASSWORD);
        secondHash = HASHER.hash(PASSWORD);
    }

    @Test
    public void storedKeyReallyUsesContractAlgorithmIterationsAndBitLength() throws Exception {
        String[] parts = firstHash.split("\\$", -1);
        assertEquals(4, parts.length);
        assertEquals("pbkdf2-sha256", parts[0]);
        assertEquals("600000", parts[1]);
        assertEquals(600_000, PasswordHasher.ITERATIONS);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] actualKey = Base64.getDecoder().decode(parts[3]);
        assertEquals(16, salt.length);
        assertEquals(32, actualKey.length);

        // Independent JCA derivation verifies the work factor, rather than trusting its label.
        PBEKeySpec oracle = new PBEKeySpec(PASSWORD.toCharArray(), salt, 600_000, 256);
        try {
            byte[] expectedKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(oracle).getEncoded();
            assertArrayEquals(expectedKey, actualKey);
        } finally {
            oracle.clearPassword();
        }
    }

    @Test
    public void samePasswordHasDifferentRandomSaltsAndKeysButBothVerify() {
        String[] first = firstHash.split("\\$", -1), second = secondHash.split("\\$", -1);
        assertNotEquals(firstHash, secondHash);
        assertFalse("salt must be freshly random", Arrays.equals(
                Base64.getDecoder().decode(first[2]), Base64.getDecoder().decode(second[2])));
        assertFalse("different salts produce different derived keys", Arrays.equals(
                Base64.getDecoder().decode(first[3]), Base64.getDecoder().decode(second[3])));
        assertTrue(HASHER.verify(PASSWORD, firstHash));
        assertTrue(HASHER.verify(PASSWORD, secondHash));
    }

    @Test
    public void verificationPreservesPasswordWhitespaceCaseAndUnicode() {
        assertFalse(HASHER.verify(PASSWORD.trim(), firstHash));
        assertFalse(HASHER.verify(PASSWORD.replace('\u0421', '\u0441'), firstHash));
        assertFalse(HASHER.verify(PASSWORD.replace("\uD83E\uDDEA", "\uD83D\uDD2C"), firstHash));
        assertFalse(HASHER.verify(null, firstHash));
    }

    @Test
    public void malformedOrUnsupportedStoredHashesReturnFalseWithoutThrowing() {
        String[] parts = firstHash.split("\\$", -1);
        String salt = parts[2], key = parts[3];
        String shortSalt = Base64.getEncoder().encodeToString(new byte[15]);
        String shortKey = Base64.getEncoder().encodeToString(new byte[31]);
        String[] malformed = {
            null, "", "plaintext-password", "pbkdf2-sha256", firstHash + "$extra",
            "pbkdf2-sha1$600000$" + salt + "$" + key,
            "pbkdf2-sha256$not-an-integer$" + salt + "$" + key,
            "pbkdf2-sha256$0$" + salt + "$" + key,
            "pbkdf2-sha256$-1$" + salt + "$" + key,
            "pbkdf2-sha256$600001$" + salt + "$" + key,
            "pbkdf2-sha256$600000$not base64!$" + key,
            "pbkdf2-sha256$600000$" + salt + "$not base64!",
            "pbkdf2-sha256$600000$" + shortSalt + "$" + key,
            "pbkdf2-sha256$600000$" + salt + "$" + shortKey,
            "pbkdf2-sha256$600000$$" + key,
            "pbkdf2-sha256$600000$" + salt + "$"
        };
        for (String stored : malformed) {
            assertFalse("malformed hash must not authenticate: " + stored, HASHER.verify(PASSWORD, stored));
        }
    }

    @Test
    public void wellFormedHashWithOneChangedKeyBitDoesNotAuthenticate() {
        String[] parts = firstHash.split("\\$", -1);
        byte[] tamperedKey = Base64.getDecoder().decode(parts[3]);
        tamperedKey[0] ^= 1;
        String tampered = parts[0] + "$" + parts[1] + "$" + parts[2] + "$"
                + Base64.getEncoder().encodeToString(tamperedKey);
        assertFalse(HASHER.verify(PASSWORD, tampered));
    }

    @Test
    public void tokensHaveThirtyTwoRandomBytesAndAreSafeForCookies() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            String token = (i % 2 == 0 ? HASHER : new PasswordHasher()).newToken();
            assertTrue("unpadded URL-safe token", token.matches("[A-Za-z0-9_-]{43}"));
            assertEquals(32, Base64.getUrlDecoder().decode(token).length);
            assertTrue("fresh token must not repeat", tokens.add(token));
        }
    }

    @Test
    public void tokenDigestMatchesKnownSha256VectorAndIsDeterministic() {
        assertEquals("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=", PasswordHasher.tokenDigest("abc"));
        String firstToken = HASHER.newToken(), secondToken = HASHER.newToken();
        String digest = PasswordHasher.tokenDigest(firstToken);
        assertEquals(digest, PasswordHasher.tokenDigest(firstToken));
        assertEquals(32, Base64.getDecoder().decode(digest).length);
        assertNotEquals(firstToken, digest);
        assertNotEquals(digest, PasswordHasher.tokenDigest(secondToken));
    }
}
