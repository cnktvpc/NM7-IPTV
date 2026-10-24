package vn.phuong.iptvplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Small encrypted store for the opaque session token returned by NM7's TikTok auth Worker. */
public final class TikTokSessionStore {
    private static final String PREFS = "nm7_tiktok_session";
    private static final String ALIAS = "nm7_tiktok_session_aes_v1";
    private static final String IV = "iv";
    private static final String VALUE = "value";

    private TikTokSessionStore() { }

    public static synchronized void save(Context context, String token) throws Exception {
        if (token == null || token.isEmpty()) throw new IllegalArgumentException("empty token");
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit()
                .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(VALUE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply();
    }

    public static synchronized String read(Context context) {
        try {
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String ivValue = preferences.getString(IV, null);
            String encryptedValue = preferences.getString(VALUE, null);
            if (ivValue == null || encryptedValue == null) return null;
            byte[] iv = Base64.decode(ivValue, Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(encryptedValue, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception error) {
            clear(context);
            return null;
        }
    }

    public static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(ALIAS)) {
            java.security.Key key = keyStore.getKey(ALIAS, null);
            if (key instanceof SecretKey) return (SecretKey) key;
            keyStore.deleteEntry(ALIAS);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
