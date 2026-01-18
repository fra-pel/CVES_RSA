package com.uvarara.quiz;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import com.uvarara.quiz.security.KeyManager;
public class KeyExportActivity extends Activity {

    private static final String KEY_ALIAS = "DbKeyAlias"; // stesso alias usato nel DBHelper

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Crea la coppia RSA se non esiste già
            KeyManager.ensureRSAKeyExists(KEY_ALIAS);

            // Recupera la chiave pubblica in formato PEM
            String pem = KeyManager.getPublicKeyPem(KEY_ALIAS);

            // Stampa nei logcat
            Log.d("RSA_PUBLIC_KEY", "\n" + pem);

        } catch (Exception e) {
            Log.e("RSA_PUBLIC_KEY", "Errore durante l'estrazione della chiave", e);
        }

        // Chiudi subito l'Activity
        finish();
    }
}
