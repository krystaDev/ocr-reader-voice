# Czytnik Głosowy

Aplikacja Android, która czyta na głos tekst widoczny w aparacie – opcjonalnie po przetłumaczeniu.

- Wymagania produktu: [docs/PRD-MVP.md](docs/PRD-MVP.md)
- Specyfikacja techniczna: [docs/TECH-SPEC.md](docs/TECH-SPEC.md)
- Instalacja wersji testowej: [docs/INSTALACJA.md](docs/INSTALACJA.md)
- Zestaw testowy i nagrania: [testdata/README.md](testdata/README.md), szablon testu: [docs/tests/](docs/tests/)

## Budowanie

| Co | Polecenie | Wymaga Android SDK |
| --- | --- | --- |
| Testy logiki (moduł `core`, czysty Kotlin/JVM) | `./gradlew -p core test` | nie |
| Debug APK | `./gradlew :app:assembleDebug` | tak |
| Release z R8 podpisany kluczem debug (test na telefonie) | `./gradlew :app:installR8test` | tak |
| Release AAB do Google Play (wymaga własnego klucza podpisu) | `./gradlew :app:bundleRelease -PversionCode=<n>` | tak |

Każdy push buduje w GitHub Actions debug APK (zakładka **Actions** → przebieg → artefakt
`czytnik-glosowy-debug-<commit>`). APK są podpisane wspólnym kluczem debug z `app/debug.keystore`, więc nowa wersja
instaluje się na poprzednią bez odinstalowania.
