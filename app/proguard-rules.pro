# Modely ukladané do Firestore (čítame ich ručne cez mapy, ale pre istotu ponecháme)
-keep class sk.kvz.priestor.data.** { *; }

# Credential Manager / Google ID
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
