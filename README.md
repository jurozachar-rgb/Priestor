# Priestor

Jednoduchý zdieľaný to-do list pre Android. Oblasti (Domov, Nákup, Chata, Dovolenka…) s vlastnými sekciami,
úlohy s nepovinným termínom a pripomienkami, archív urobených úloh. Každú oblasť vidia iba ľudia, ktorých do nej pozvete.

**Technológie:** Kotlin, Jetpack Compose (Material 3), Firebase Auth (prihlásenie Google) a Cloud Firestore.
Web `priestor.kvz.sk` (zásady súkromia, pozvánky) je v priečinku `docs/`.

## Ako to funguje

| Časť | Kde |
|---|---|
| Dáta a synchronizácia | `app/src/main/java/sk/kvz/priestor/data/Repo.kt` |
| Prihlásenie | `auth/GoogleAuth.kt` |
| Pripomienky (lokálne v telefóne) | `reminders/Reminders.kt` |
| Obrazovky | `ui/` |
| Bezpečnostné pravidlá databázy | `firebase/firestore.rules` |
| Zostavenie (GitHub Actions) | `.github/workflows/build.yml` |

Štruktúra databázy:

```
spaces/{id}               názov, vlastník, členovia, mená členov, sekcie, farba
spaces/{id}/tasks/{id}    úloha: názov, poznámka, sekcia, termín, hotovo, kto pridal/splnil
invites/{KÓD}             pozvánka do oblasti, platí 7 dní
```

Pripomienky: termín s časom → hodinu vopred a v čase termínu; termín bez času → deň vopred o 18:00 a v deň termínu o 8:00.

## Nastavenie Firebase (raz)

1. [console.firebase.google.com](https://console.firebase.google.com) → **Create project** „priestor“ (Analytics netreba).
2. **Firestore Database → Create database** → región **eur3 (Europe)** → production mode.
3. **Firestore → Rules** → vložiť obsah `firebase/firestore.rules` → **Publish**.
4. **Authentication → Get started → Google** → zapnúť, uložiť.
5. **Project settings → Add app → Android**: package name `sk.kvz.priestor`,
   SHA-1 podpisového kľúča: `A2:80:58:94:37:C3:DC:3C:06:62:51:66:76:7E:62:EB:17:93:DF:A5`
6. `google-services.json` je v `app/`. GitHub secret `SIGNING_PASSWORD` = heslo k podpisovému kľúču.
7. Po každom nahratí kódu sa APK zverejní na
   https://github.com/jurozachar-rgb/Priestor/releases/latest/download/Priestor.apk

Zabezpečenie je popísané v [SECURITY.md](SECURITY.md).

## Pred zverejnením na Google Play

- Balík `.aab` pre Google Play je v artefakte `priestor-play-aab` každého zostavenia.
- Do Firebase pridať SHA-1 kľúča Play App Signing (Play Console → Test and release → App integrity).
- Zapnúť Firebase App Check (Play Integrity) – viď SECURITY.md.
- Doplniť údaje firmy a kontakt v `docs/privacy/index.html` a SHA-256 Play kľúča v `docs/.well-known/assetlinks.json`.
- Zapnúť GitHub Pages (Settings → Pages → branch `main`, priečinok `/docs`) a v DNS domény kvz.sk nastaviť
  CNAME `priestor` → `jurozachar-rgb.github.io`.
