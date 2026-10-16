# Zabezpečenie aplikácie Priestor

## Prístup k dátam
- **Prihlásenie iba cez Google účet** (Firebase Authentication). Aplikácia nikdy nevidí heslo.
- **Pravidlá databázy** (`firebase/firestore.rules`) sa vyhodnocujú na serveri Google, nie v aplikácii,
  takže ich nemožno obísť upravenou aplikáciou ani priamym volaním API:
  - oblasť a jej úlohy vidí a mení iba člen oblasti,
  - každý zápis má pevne určené polia, typy a maximálne dĺžky,
  - autor úlohy sa nedá sfalšovať ani zmeniť,
  - člen mení iba svoje meno, vlastník môže odoberať členov, mazať oblasť môže iba vlastník,
  - do oblasti sa dá pridať iba sám seba a iba s platnou pozvánkou,
  - pozvánka má 8-znakový náhodný kód (~10^12 kombinácií) a platí najviac 7 dní,
  - všetko ostatné je zakázané.
- Pravidlá sú pokryté automatickými testmi (`firebase/test`), ktoré bežia v emulátore pri každej zmene.

## Prenos a uloženie
- Komunikácia výhradne cez HTTPS/TLS (nešifrovaná komunikácia je v aplikácii zakázaná).
- Dáta sú vo Firestore v regióne EÚ, šifrované v pokoji (Google).
- Lokálna kópia v telefóne je v súkromnom priestore aplikácie a pri odhlásení či zmazaní účtu sa vymaže.
- Zálohovanie dát aplikácie do cloudu a prenos na iné zariadenie sú vypnuté.
- Notifikácie na zamknutej obrazovke nezobrazujú názov úlohy.

## Zostavenie aplikácie
- Zverejňovaná verzia je ostrá (release): nie je ladiaca, kód je zmenšený a zatemnený (R8), ladiace logy sú odstránené.
- Testovacie verzie (inštalované mimo Google Play) sú podpísané testovacím kľúčom z repozitára.
  Riziko: niekto by mohol podvrhnúť aktualizáciu – iba ak by ste APK inštalovali z iného zdroja
  než z odkazu na GitHub releases. Na Google Play sa tento kľúč nepoužije.
- Pre Google Play je pripravený vlastný kľúč (RSA 4096), v repozitári iba zašifrovaný (AES-256,
  PBKDF2 600 000 iterácií). Použije sa po nastavení GitHub secret `SIGNING_PASSWORD`.
- Súbor `app/google-services.json` nie je tajný – je súčasťou každej nainštalovanej aplikácie.
  Ochranu dát zabezpečujú pravidlá databázy.

## Pred zverejnením na Google Play
- Zapnúť **Firebase App Check** s Play Integrity a vynútiť ho pre Firestore aj Authentication
  (overí, že požiadavky prichádzajú iba z originálnej aplikácie z Google Play).
- Zapnúť **Play App Signing** a do Firebase pridať SHA-1 kľúča z Play Console.
- Z Firebase odstrániť SHA-1 testovacieho (debug) kľúča.
