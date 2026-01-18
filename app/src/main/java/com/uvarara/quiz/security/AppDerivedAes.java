package com.uvarara.quiz.security;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.content.pm.SigningInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import android.util.Base64;

public final class AppDerivedAes {
    private AppDerivedAes() {}

    // Mantieni identico tra app e tool offline
    private static final byte[] SALT = "com.uvarara.quiz/DB/v1".getBytes(StandardCharsets.UTF_8);
    private static final int ITERATIONS = 150_000;
    private static final int KEY_BITS = 256;

    public static SecretKeySpec getAesKey(Context ctx) throws Exception {
        String pkg = ctx.getPackageName();
        byte[] cert = getSigningCertBytes(ctx, pkg);
        byte[] certSha256 = sha256(cert);

        String password = toHex(certSha256) + ":" + pkg;

        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), SALT, ITERATIONS, KEY_BITS);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] key = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    // Cifra una stringa con AES-GCM (IV prepended) e ritorna Base64
    public static String encryptWithAesGcm(Context ctx, String plaintext) throws Exception {
        SecretKeySpec key = getAesKey(ctx);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    // Decifra il blob Base64 (IV+CT) in chiaro
    public static String decryptWithAesGcm(Context ctx, String base64) throws Exception {
        SecretKeySpec key = getAesKey(ctx);
        byte[] in = Base64.decode(base64, Base64.NO_WRAP);
        if (in.length < 13) throw new IllegalArgumentException("Blob troppo corto");
        byte[] iv = new byte[12];
        byte[] ct = new byte[in.length - 12];
        System.arraycopy(in, 0, iv, 0, 12);
        System.arraycopy(in, 12, ct, 0, ct.length);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] pt = c.doFinal(ct);
        return new String(pt, StandardCharsets.UTF_8);
    }

    private static byte[] getSigningCertBytes(Context ctx, String pkg) throws Exception {
        PackageManager pm = ctx.getPackageManager();
        if (Build.VERSION.SDK_INT >= 28) {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo si = pi.signingInfo;
            Signature[] sigs = si.hasMultipleSigners() ? si.getApkContentsSigners()
                                                       : si.getSigningCertificateHistory();
            return sigs[0].toByteArray();
        } else {
            @SuppressWarnings("deprecation")
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            @SuppressWarnings("deprecation")
            Signature[] sigs = pi.signatures;
            return sigs[0].toByteArray();
        }
    }

    private static byte[] sha256(byte[] in) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(in);
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
