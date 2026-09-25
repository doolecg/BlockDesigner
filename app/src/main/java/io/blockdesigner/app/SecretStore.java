package io.blockdesigner.app;

import com.sun.jna.platform.win32.Crypt32Util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Protects API keys at rest with Windows DPAPI, so the settings file is only readable by the same Windows user.
 * On other platforms keys are stored base64-encoded (obfuscated, not encrypted).
 */
public final class SecretStore {
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    private SecretStore() {
    }

    public static String protect(String secret) {
        if (secret == null || secret.isBlank()) return null;
        byte[] raw = secret.strip().getBytes(StandardCharsets.UTF_8);
        byte[] out = WINDOWS ? Crypt32Util.cryptProtectData(raw) : raw;
        return (WINDOWS ? "dpapi:" : "b64:") + Base64.getEncoder().encodeToString(out);
    }

    public static String reveal(String stored) {
        if (stored == null || stored.isBlank()) return "";
        try {
            if (stored.startsWith("dpapi:")) {
                return new String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(stored.substring(6))), StandardCharsets.UTF_8);
            }
            if (stored.startsWith("b64:")) return new String(Base64.getDecoder().decode(stored.substring(4)), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            System.err.println("Could not decrypt a stored key: " + e.getMessage());
        }
        return "";
    }
}
