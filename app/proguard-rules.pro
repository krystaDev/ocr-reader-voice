# Reguły R8 dla wersji release. Biblioteki (ML Kit, CameraX, Compose, kotlinx.serialization) dostarczają własne
# reguły w swoich AAR/JAR, więc tutaj nic dodatkowego nie jest potrzebne.
#
# Czytelne ślady stosu w raportach awarii z Google Play (plik mapping.txt wgrywa się w Play Console):
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
