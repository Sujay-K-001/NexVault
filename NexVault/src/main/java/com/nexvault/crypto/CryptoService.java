package com.nexvault.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * All cryptography lives here: PBKDF2WithHmacSHA256 key derivation and AES-256-GCM encryption.
 * Encrypted blobs are laid out as {@code IV (12 bytes) || ciphertext || GCM tag (16 bytes)}.
 */
public final class CryptoService {
    public static final String KDF = "PBKDF2WithHmacSHA256";
    public static final int DEFAULT_ITERATIONS = 200_000;
    public static final int SALT_BYTES = 32;
    public static final int KEY_BYTES = 32; // AES-256
    public static final int IV_BYTES = 12;
    public static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();

    public byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        random.nextBytes(b);
        return b;
    }

    public SecureRandom random() { return random; }

    /** Derives a 256-bit key. The PBEKeySpec's internal password copy is cleared afterwards. */
    public byte[] deriveKey(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance(KDF).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Key derivation unavailable: " + e.getMessage(), e);
        } finally {
            spec.clearPassword();
        }
    }

    public byte[] encrypt(byte[] key, byte[] plaintext, byte[] aad) {
        try {
            byte[] iv = randomBytes(IV_BYTES);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            if (aad != null) c.updateAAD(aad);
            byte[] ct = c.doFinal(plaintext);
            byte[] out = new byte[IV_BYTES + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(ct, 0, out, IV_BYTES, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /** @throws GeneralSecurityException if the key is wrong or the data/AAD was tampered with. */
    public byte[] decrypt(byte[] key, byte[] blob, byte[] aad) throws GeneralSecurityException {
        if (blob == null || blob.length < IV_BYTES + TAG_BITS / 8) throw new GeneralSecurityException("Blob too short");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(blob, 0, IV_BYTES)));
        if (aad != null) c.updateAAD(aad);
        return c.doFinal(blob, IV_BYTES, blob.length - IV_BYTES);
    }

    public byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return m.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean constantTimeEquals(byte[] a, byte[] b) { return MessageDigest.isEqual(a, b); }

    public static void wipe(byte[] a) { if (a != null) Arrays.fill(a, (byte) 0); }
    public static void wipe(char[] a) { if (a != null) Arrays.fill(a, '\0'); }
}
