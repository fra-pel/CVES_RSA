package com.uvarara.quiz.security;

import android.util.Base64;

public final class StrObf {
    private StrObf() {}

    public static String d(String b64) {
        return new String(Base64.decode(b64, Base64.DEFAULT));
    }
}
