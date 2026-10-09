# Sklep Play – grafiki i teksty karty aplikacji

Strona projektu i polityka prywatności: `site/` → https://krystadev.github.io/ocr-reader-voice/
(polityka: https://krystadev.github.io/ocr-reader-voice/privacy.html).

## Grafiki

| Plik | Wymiary | Wymaganie Google Play |
| --- | --- | --- |
| `icon-512.png` | 512 × 512, PNG | Ikona aplikacji (wymagana) |
| `feature-graphic.png` | 1024 × 500, PNG bez przezroczystości | Grafika promocyjna (wymagana) |
| `screenshots/phone-1..5.png` | 1080 × 1920 (9:16), PNG bez przezroczystości | Zrzuty z telefonu: min. 2, max. 8; 4+ w ≥ 1080 px kwalifikują do promowania |

Zrzuty to odwzorowanie ekranów aplikacji według kodu Compose (te same kolory, wymiary w dp/sp i teksty), z
wyrenderowaną sceną zamiast obrazu z aparatu – budowanie i emulator nie są potrzebne. Źródło: `src/screens.html`.
Po zmianie UI odśwież je:

```sh
cd store/src && npm install && npm install --no-save playwright && npx playwright install chromium && node render.mjs
```

Prawdziwe zrzuty z telefonu też są dobre, ale uwaga: większość telefonów ma ekran 20:9 (np. 1080 × 2400), a Google
Play odrzuca zrzuty, których dłuższy bok jest więcej niż 2 razy dłuższy od krótszego – trzeba je przyciąć do 9:16.

## Teksty karty (pl-PL)

**Nazwa aplikacji** (max 30 znaków):

> Czytnik Głosowy

**Krótki opis** (max 80 znaków):

> Skieruj aparat na tekst – telefon sam przeczyta go na głos, także po przetłumaczeniu.

**Pełny opis** (max 4000 znaków):

> Czytnik Głosowy czyta na głos tekst widoczny w aparacie. Wystarczy uruchomić aplikację i przytrzymać telefon nad
> tekstem – po krótkim sygnale usłyszysz, co jest napisane. Bez robienia zdjęć, zaznaczania i szukania małych
> przycisków.
>
> Dla kogo:
> • osoby słabowidzące – ulotki leków, rachunki, etykiety, drobny druk,
> • osoby niewidome – podpowiedzi głosowe i wibracje pomagają nakierować aparat, pełna obsługa TalkBack,
> • osoby, którym czytanie sprawia trudność – dysleksja, afazja, seniorzy,
> • wszyscy, którzy trafili na tekst w obcym języku – instrukcje, menu, szyldy, komiksy.
>
> Najważniejsze funkcje:
> • Czyta samo – aparat startuje od razu, a tekst jest czytany, gdy tylko kadr się ustabilizuje.
> • Tłumaczenie – tekst w obcym języku może zostać przeczytany po polsku lub w innym wybranym języku.
> • Duże przyciski i wysoki kontrast – żółte i białe na czarnym tle.
> • Dotknięcie ekranu zatrzymuje czytanie albo czyta od razu; „Powtórz” czyta ostatni tekst jeszcze raz.
> • Tempo mowy – wolniej lub szybciej, jednym przyciskiem.
> • Ten sam tekst nie jest czytany w kółko.
> • Latarka – ręcznie albo sama, gdy jest ciemno.
>
> Prywatność: tekst jest rozpoznawany na telefonie. Aplikacja nie wysyła zdjęć ani tekstu na serwery, nie ma kont
> ani reklam. Internet jest potrzebny do jednorazowego pobrania modelu tłumaczenia (ok. 30 MB).

**Kategoria:** Narzędzia (lub Edukacja). **Kontakt:** e-mail (wymagany przez Play Console), strona: adres strony
projektu.

## Formularze w Play Console (podpowiedzi)

- **Polityka prywatności** – adres `…/privacy.html` powyżej.
- **Bezpieczeństwo danych** – twórca nie zbiera danych, ale biblioteka ML Kit wysyła do Google dane diagnostyczne
  (https://developers.google.com/ml-kit/android-data-disclosure, stan na 2026-07-15), więc deklarujemy je jako
  zbierane przez aplikację:
  - *Informacje o aplikacji i wydajności → Dane diagnostyczne* (model urządzenia, wersja systemu, opóźnienia, kody
    błędów) – cel: Analityka; zbieranie obowiązkowe (nie da się wyłączyć).
  - *Identyfikatory urządzenia lub inne* (identyfikatory instalacji, Firebase Installations) – cel: Analityka;
    obowiązkowe.
  - Udostępnianie podmiotom trzecim: **nie**. Szyfrowanie podczas przesyłania: **tak**. Możliwość prośby o usunięcie:
    **nie** (twórca nie ma tych danych).
  - Zdjęcia, tekst, lokalizacja, dane osobowe: **nie są zbierane**.
- **Reklamy** – nie. **Konta użytkowników** – brak (adres do usuwania konta nie jest potrzebny).
- **Ułatwienia dostępu** – aplikacja nie korzysta z API AccessibilityService, więc nie trzeba składać deklaracji.
