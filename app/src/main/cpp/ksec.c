#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <stdbool.h>
#include <unistd.h>
#include <stdio.h>   // <-- definisce FILE, fopen, fclose ecc.
#include <stdlib.h>  // (opzionale, ma utile per free, malloc...)

// --- util ---

static inline uint64_t rotl64(uint64_t x, int k) { return (x << k) | (x >> (64 - k)); }
static inline uint8_t rotl8(uint8_t x, int k) { return (uint8_t)((x << k) | (x >> (8 - k))); }

// FNV-1a 32-bit (integrity)
static uint32_t fnv1a32(const uint8_t* data, size_t len) {
    uint32_t h = 2166136261u;
    for (size_t i = 0; i < len; ++i) {
        h ^= data[i];
        h *= 16777619u;
    }
    return h;
}

// xorshift128+ (PRNG)
typedef struct { uint64_t s[2]; } xs128p_t;
static uint64_t xs128p_next(xs128p_t* x) {
    uint64_t s1 = x->s[0];
    const uint64_t s0 = x->s[1];
    x->s[0] = s0;
    s1 ^= s1 << 23;
    x->s[1] = s1 ^ s0 ^ (s1 >> 17) ^ (s0 >> 26);
    return x->s[1] + s0;
}

// Keystream byte-by-byte
static void ks_bytes(xs128p_t* rng, uint8_t* out, size_t n) {
    uint64_t w = 0; int cnt = 0;
    for (size_t i = 0; i < n; ++i) {
        if (cnt == 0) { w = xs128p_next(rng); cnt = 8; }
        out[i] = (uint8_t)w;
        w >>= 8; cnt--;
    }
}

// Simple anti-debug: /proc/self/status TracerPid
static bool anti_debug() {
    FILE* f = fopen("/proc/self/status", "r");
    if (!f) return false;
    char line[128];
    bool traced = false;
    while (fgets(line, sizeof(line), f)) {
        if (strncmp(line, "TracerPid:", 10) == 0) {
            int v = 0; sscanf(line + 10, "%d", &v);
            traced = (v != 0);
            break;
        }
    }
    fclose(f);
    return traced;
}

// Secure zero (avoid optimization)
static void secure_bzero(void* p, size_t n) {
    volatile uint8_t* v = (volatile uint8_t*)p;
    while (n--) *v++ = 0;
}

// --- dati offuscati (DA SOSTITUIRE con lo script) ---

// Semi obfuscation constants (fissi a codice)
static const uint64_t K1 = 0xC3A5C85C97CB3127ULL; // "random pepper" 1
static const uint64_t K2 = 0xB492B66FBE98F273ULL; // "random pepper" 2

// Seed offuscati (placeholder)
static const uint64_t SEED_A_OBF = 0xF6F6269533916DF6ULL; // DA SOSTITUIRE
static const uint64_t SEED_B_OBF = 0x1C1C3796A36F1D85ULL; // DA SOSTITUIRE

// Permutazione codificata: PCODE[i] = perm[i] ^ f(i)
static const uint16_t PCODE[] = {
        0xBEE9, 0x596B, 0x71E7, 0x095B, 0x20F6, 0xC98A, 0xD186, 0xBEBB};

// BLOB permutato e mascherato (bytes ofuscati)
static const uint8_t BLOB[] = {
        // DA SOSTITUIRE: valori generati dallo script
        0x60, 0x0B, 0x9C, 0xE6, 0x1D, 0xFD, 0xD3, 0xFB};

// Lunghezza reale della passphrase (derivata da BLOB)
static const size_t BLOB_LEN = sizeof(BLOB);

// Checksum atteso (FNV-1a) offuscato
static const uint32_t FNV_EXPECT_OBF = 0x0E990072u; // DA SOSTITUIRE

// --- decodifica support ---

// Inverse f(i) usiamo la stessa f, perché XOR -> f è involutiva in questo uso
static inline uint16_t f_i(uint16_t i) {
// mix non lineare su 16 bit
uint32_t x = i;
x = (x * 2654435761u) ^ 0xBEEF;
x ^= (x >> 16);
return (uint16_t)(x & 0xFFFFu);
}

static uint64_t unmask_seed(uint64_t obf) {
    // schema: seed = rotl64((obf ^ K1), 17) ^ K2
    return rotl64((obf ^ K1), 17) ^ K2;
}

static uint32_t unmask_fnv(uint32_t obf) {
    // semplice srotolamento: rotazione + XOR con costante
    uint32_t x = (obf ^ 0xA5A5A5A5u);
    return (x << 7) | (x >> (32 - 7));
}

// --- JNI ---

JNIEXPORT jbyteArray JNICALL
Java_com_uvarara_quiz_adapter_DBHelper_getDbKeyNative(JNIEnv* env, jclass clazz) {
    // Anti-debug: ritorno chiave-esca se necessario
    if (anti_debug()) {
        const char* decoy = "uvarara-decoy";
        jbyteArray out = (*env)->NewByteArray(env, (jsize)strlen(decoy));
        (*env)->SetByteArrayRegion(env, out, 0, (jsize)strlen(decoy), (const jbyte*)decoy);
        return out;
    }

    // Ricostruisci seed
    uint64_t sA = unmask_seed(SEED_A_OBF);
    uint64_t sB = unmask_seed(SEED_B_OBF);
    if ((sA | sB) == 0) { // fallback paranoico
        sA = 0x9E3779B97F4A7C15ULL; sB = 0xD1B54A32D192ED03ULL;
    }

    xs128p_t rng = { .s = { sA, sB } };

    // Decodifica permutazione
    size_t n = BLOB_LEN;
    uint16_t* perm = (uint16_t*)malloc(n * sizeof(uint16_t));
    uint8_t* ks = (uint8_t*)malloc(n);
    uint8_t* key = (uint8_t*)malloc(n);
    if (!perm || !ks || !key) {
        if (perm) free(perm); if (ks) free(ks); if (key) free(key);
        return NULL;
    }

    for (size_t i = 0; i < n; ++i) {
        uint16_t code = PCODE[i];
        perm[i] = (uint16_t)(code ^ f_i((uint16_t)i));
    }

    // Keystream
    ks_bytes(&rng, ks, n);

    // Ricostruzione in ordine originale: key[i] = BLOB[perm[i]] ^ ks[i] ^ rotl8(i, 1)
    for (size_t i = 0; i < n; ++i) {
        uint16_t j = perm[i] % n;
        key[i] = (uint8_t)(BLOB[j] ^ ks[i] ^ rotl8((uint8_t)i, 1));
    }

    // Integrity check
    uint32_t expect = unmask_fnv(FNV_EXPECT_OBF);
    uint32_t got = fnv1a32(key, n);
    if (got != expect) {
        // Wipe & decoy
        secure_bzero(key, n);
        const char* decoy = "uvarara-decoy";
        jbyteArray out = (*env)->NewByteArray(env, (jsize)strlen(decoy));
        (*env)->SetByteArrayRegion(env, out, 0, (jsize)strlen(decoy), (const jbyte*)decoy);
        secure_bzero(perm, n * sizeof(uint16_t));
        secure_bzero(ks, n);
        free(perm); free(ks); free(key);
        return out;
    }

    // Ritorno chiave reale
    jbyteArray out = (*env)->NewByteArray(env, (jsize)n);
    (*env)->SetByteArrayRegion(env, out, 0, (jsize)n, (const jbyte*)key);

    // Pulizia memoria
    secure_bzero(perm, n * sizeof(uint16_t));
    secure_bzero(ks, n);
    secure_bzero(key, n);
    free(perm); free(ks); free(key);
    return out;
}
