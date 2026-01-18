package com.uvarara.quiz.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class KeyManager {
    // === AES CONFIG (TUA LOGICA ATTUALE) ===
    private static final String AES_KEY_ALIAS = "DBKeyAlias";
    private static final String PREF_NAME = "secure_prefs";
    private static final String ENCRYPTED_KEY_PREF = "encrypted_db_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private final Context context;

    public KeyManager(Context context) {
        this.context = context;
    }

    // ====== METODI AES (come avevi) ======
    public void generateKeyIfNeeded() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (!keyStore.containsAlias(AES_KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            keyGenerator.init(new KeyGenParameterSpec.Builder(
                    AES_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            keyGenerator.generateKey();
        }
    }

    public void encryptAndStoreDbKey(String dbKey) throws Exception {
        SecretKey secretKey = getSecretKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(dbKey.getBytes(StandardCharsets.UTF_8));

        byte[] combined = ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv)
                .put(encrypted)
                .array();

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(ENCRYPTED_KEY_PREF, Base64.encodeToString(combined, Base64.DEFAULT)).apply();
    }

    public String decryptDbKey() throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String base64 = prefs.getString(ENCRYPTED_KEY_PREF, null);
        if (base64 == null) throw new IllegalStateException("Chiave non trovata");

        byte[] combined = Base64.decode(base64, Base64.DEFAULT);
        ByteBuffer buffer = ByteBuffer.wrap(combined);
        byte[] iv = new byte[12];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);

        SecretKey secretKey = getSecretKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        byte[] decrypted = cipher.doFinal(encrypted);

        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return ((KeyStore.SecretKeyEntry) keyStore.getEntry(AES_KEY_ALIAS, null)).getSecretKey();
    }

    // ====== NUOVI METODI RSA ======
    public static void ensureRSAKeyExists(String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(alias)) return;

        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE);

        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .build();

        kpg.initialize(spec);
        kpg.generateKeyPair();
    }

    public static String decryptRSA(byte[] encrypted, String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, null);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decoded = cipher.doFinal(encrypted);

        return new String(decoded, StandardCharsets.UTF_8);
    }

    public static String getPublicKeyPem(String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        Certificate cert = ks.getCertificate(alias);
        if (cert == null) throw new IllegalStateException("Chiave pubblica non trovata per alias: " + alias);
        PublicKey pub = cert.getPublicKey();
        String base64 = Base64.encodeToString(pub.getEncoded(), Base64.NO_WRAP);
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }
}
