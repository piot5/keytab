# KeyTab ProGuard/R8 rules.
# Manifest-referenced classes (KeyTabImeService, MainActivity, FileManagerFragment)
# and layout-referenced views are kept automatically by AGP's default rules.

# Panels/Popup werden zur Laufzeit instanziiert bzw. von Layouts referenziert — sicherheitshalber behalten.
-keep class com.piotv.keytab.ime.** { *; }
-keep class com.piotv.keytab.file.** { *; }

# Robolectric-/Unit-Tests sind im Release nicht enthalten; keine weiteren Regeln nötig.

