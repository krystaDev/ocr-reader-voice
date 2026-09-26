# Zestaw testowy

Opis zestawu z docs/TECH-SPEC.md, rozdz. 6.3. **Zdjęć ani nagrań stron komiksów Marvela nie trzymamy w repozytorium**
(decyzja z 2026-09-26) – leżą w prywatnym katalogu poza repo.

## 1. Druk – 30 zdjęć (miara PRD: ≥ 95% poprawnych znaków)

Katalog `testdata/print/` (można trzymać w repo, jeśli zdjęcia są własne i bez danych osobowych):

| Plik | Zawartość |
| --- | --- |
| `NN-opis.jpg` | zdjęcie telefonem: ulotka leku, etykieta, paragon, tabliczka; druk ≥ 10 pt |
| `NN-opis.txt` | tekst wzorcowy przepisany ręcznie (kolejność czytania jak w F4, akapity oddzielone pustą linią) |

Rozkład: 10 × ulotki/etykiety PL, 10 × druk EN/DE/FR/ES, 5 × słabe światło, 5 × paragony i tabliczki.

Pomiar: CER liczony przez `pl.czytnik.core.text.TextMetrics.characterErrorRate(wzorzec, wynik OCR)`; wynik OCR
bierzemy z ekranu Diagnostyka (pole „Oryginał”) po wymuszonym odczycie (dotknięcie podglądu). Cel: średnio CER ≤ 0,05.

## 2. Ekran tabletu – komiks (≥ 20 zdjęć, poza repo)

Zdjęcia telefonem ekranu tabletu z aplikacją Marvela, w warunkach Damiana:

- pojedynczy dymek; dymek + ucięty sąsiedni; ramka narracyjna; wyrazy dźwiękonaśladowcze (THWIP!),
- z odblaskiem i bez, z latarką i bez, odległość ok. 15 / 25 / 35 cm (mora).

Do każdego zdjęcia plik `.txt` z tekstem dymka. Wynik: CER jak wyżej + notatka, czy dymek został wybrany poprawnie.

## 3. Sekwencje klatek (do strojenia auto-odczytu)

Nagrywane w aplikacji: Ustawienia → Diagnostyka → Nagrywanie klatek → NAGRYWAJ, przejście przez 1–2 strony
komiksu dymek po dymku, UDOSTĘPNIJ NAGRANIE. Plik zawiera tylko rozpoznany tekst i ramki, bez obrazów.

Nagranie trafia do `core/src/test/resources/sequences/<nazwa>.jsonl` razem z `<nazwa>.expected.txt` (dymki, które
powinny zostać przeczytane, w kolejności). `ReplayTest` odtwarza je przy każdym buildzie – każda zmiana parametrów
w `AutoReadConfig` jest sprawdzana na prawdziwych danych Damiana. Nagrania zawierają krótkie fragmenty dialogów;
jeśli repozytorium ma być publiczne, trzymamy je w prywatnym forku albo zastępujemy własnymi dymkami.
