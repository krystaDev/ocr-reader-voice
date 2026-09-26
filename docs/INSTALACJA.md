# Instalacja wersji testowej (bez Google Play)

1. **Pobranie APK** – każdy push buduje APK w GitHub Actions: repozytorium → zakładka **Actions** → ostatni zielony
   przebieg → sekcja **Artifacts** → `czytnik-glosowy-debug-<commit>` (plik ZIP, w środku `app-debug.apk`).
   Wymaga konta GitHub z dostępem do repozytorium. Dla Damiana: rozpakuj ZIP i wyślij sam plik `.apk` linkiem
   (np. Dysk Google) lub mailem.
2. **Pierwsza instalacja** – otwórz plik `.apk` na telefonie. Android zapyta o zgodę „Instaluj nieznane aplikacje”
   dla aplikacji, z której otwierasz plik (Chrome, Gmail, Pliki, Dysk) – zezwól, wróć i zainstaluj.
   Google Play Protect może pokazać ostrzeżenie o nieznanej aplikacji i zaproponować skanowanie – to normalne dla
   aplikacji spoza sklepu. Pierwszą instalację najlepiej zrobić razem z Damianem (drobny tekst w oknach systemu).
3. **Aktualizacje** – nową wersję instaluje się tak samo, „na” starą (wszystkie APK są podpisane tym samym kluczem),
   bez odinstalowywania: pobrany model tłumaczenia i ustawienia zostają. Aktualizacje nie przychodzą same.
4. **Pierwsze uruchomienie** – zgoda na aparat; aplikacja pobierze model tłumaczenia na język telefonu (ok. 30 MB,
   potrzebny internet – najlepiej Wi-Fi). Potem tłumaczenie działa bez internetu.
5. **Głosy** – jeśli aplikacja mówi „Brak głosu…”, w Ustawieniach aplikacji: ZAINSTALUJ GŁOSY (np. angielski i polski
   w Usługach mowy Google).

Wersję zainstalowanej aplikacji widać w Ustawienia → O aplikacji (`0.0.<numer buildu>-<commit>`).
