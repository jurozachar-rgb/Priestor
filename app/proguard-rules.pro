# Modely ukladané do Firestore (čítame ich ručne cez mapy, ale pre istotu ponecháme)
-keep class sk.kvz.priestor.data.** { *; }

# Credential Manager / Google ID
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Vynechať ladiace logy z ostrej verzie
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
