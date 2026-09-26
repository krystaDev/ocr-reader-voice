# Nagrane sekwencje klatek

Każda sekwencja to para plików:

- `<nazwa>.jsonl` – klatki analizy nagrane w aplikacji (Ustawienia → Diagnostyka → Nagrywanie klatek), jedna klatka
  w linii: czas, jasność i rozpoznane bloki tekstu z ramkami. Bez obrazów.
- `<nazwa>.expected.txt` – teksty, które powinny zostać przeczytane automatycznie, w kolejności, po jednym w linii
  (linie bloku złączone spacją, bloki jednego odczytu rozdzielone ` / `).

`ReplayTest` odtwarza każdą sekwencję przez `MainController` i porównuje przeczytane teksty z oczekiwanymi
(z tolerancją na drobne błędy OCR). Sekwencje ze stron komiksów zawierają tylko krótkie fragmenty dialogów –
całe strony ani zdjęcia komiksów nie trafiają do repozytorium.
