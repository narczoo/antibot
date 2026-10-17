# 🛡️ AntyBot - Kompleksowy System Ochrony + Auth Premium dla Minecraft 1.21.4

Wysokowydajny, nowoczesny i lekki plugin na serwery **Minecraft 1.21.4** (Paper, Purpur, Spigot), zaprojektowany z myślą o maksymalnej stabilności głównego wątku serwera (**zero lagów TPS**), bezproblemowym wpuszczaniu prawdziwych graczy oraz natychmiastowym eliminowaniu fal zmasowanych ataków botów.

---

## ⚡ Główne Moduły i Funkcjonalności

### 1. 🛑 Zaawansowana Ochrona Przed Spamem Połączeń (Connection Spam Protection)
- **Globalny limit CPS (Global Throttle):** Płynne przeliczanie nowych połączeń na sekundę na całym serwerze. Odrzuca nagłe fale setek botów na etapie `AsyncPlayerPreLoginEvent`, chroniąc pętlę sieciową serwera.
- **Wsparcie dla współdzielonych adresów IP (Shared IP Grace):** Automatycznie przyznaje dodatkowe sloty połączeń i podwyższony próg burst dla adresów IP, z których gra już zweryfikowany/zaufany gracz (np. rodzeństwo na jednym Wi-Fi, akademiki, sieci domowe i LAN).
- **Ograniczona kolejka weryfikacji (Verification Queue Bound):** Ścisły limit równocześnie prowadzonych weryfikacji (Sign Captcha oraz test ruchu). Zapobiega przeciążeniu pamięci RAM i tworzeniu tysięcy niepotrzebnych zadań schedulerów podczas ataków.
- **Tymczasowe blokady w pamięci RAM (Temp-Block Rate-Limit):** Krótkie blokady czasowe (np. 15s) zamiast permanentnych banów IP. Pakiety botów z tego samego IP są ucinane w czasie $O(1)$ bez obciążania zapytań HTTP czy bazy danych, a niewinni gracze z dynamicznym IP nie zostają zablokowani na stałe.
- **Ochrona Single-Flight Handshake:** Eliminuje zjawisko wielokrotnego, równoległego przetwarzania pakietów logowania z tego samego IP/konta w trakcie trwającej procedury weryfikacyjnej.

---

### 2. 🌐 Asynchroniczny System Anti-VPN, Proxy i Datacenter
- **Wielopoziomowe wykrywanie:** Blokuje połączenia z komercyjnych sieci VPN, serwerów Proxy, sieci Tor oraz serwerowni/centrów danych (Hosting/Datacenter, m.in. OVH, Hetzner, AWS, DigitalOcean).
- **Zewnętrzne bazy API:** Obsługa bazy **ProxyCheck.io** (z opcjonalnym darmowym lub płatnym kluczem API) z automatycznym zapasowym dostawcą **IP-API.com**.
- **Kontrolowana pula wątków:** Dedykowany `ExecutorService` z ograniczoną liczbą wątków i ograniczoną kolejką zadań. Zapobiega tworzeniu setek wątków HTTP przy atakach botów.
- **Ograniczniki przepustowości (Rate Limiting & Semaphore):** Globalny limit zapytań HTTP na minutę oraz semafor regulujący maksymalną liczbę równoczesnych połączeń wychodzących.
- **Polityki awaryjne (Fail-Open / Fail-Closed):** Pełna kontrola nad zachowaniem pluginu w przypadku przeciążenia kolejki lub awarii zewnętrznych dostawców API.
- **In-Memory Cache (24h):** Wyniki zapytań zapisywane są w pamięci podręcznej RAM, eliminując powtarzalne zapytania sieciowe.
- **Biała lista:** Konfigurowalne listy zaufanych adresów IP oraz nicków graczy (np. dla administracji).

---

### 3. 👑 Automatyczne Logowanie Graczy Premium (Auto-Login)
- **Integracja z oficjalnym Mojang API:** Asynchroniczne sprawdzanie statusu konta gracza z własnym in-memory cache'em (TTL 6h).
- **Zero haseł dla Premium:** Gracze posiadający oryginalną wersję gry Minecraft logują się całkowicie automatycznie bez potrzeby rejestracji ani wpisywania komendy `/login`.
- **Automatyczny bypass weryfikacji:** Konta Premium automatycznie omijają weryfikację Captcha.

---

### 4. 🔐 Wbudowany System Logowania i Rejestracji (Non-Premium)
- **Bezpieczeństwo kryptograficzne:** Haszowanie haseł algorytmem **SHA-256 z indywidualną unikalną solą (salt)** dla każdego konta.
- **Ograniczenia dla niezalogowanych:** Zamrożenie pozycji gracza, blokada czatu, komend, otrzymywania obrażeń, upuszczania przedmiotów oraz otwierania ekwipunków.
- **Sesje logowania po IP:** Gracz non-premium, który ponownie wejdzie na serwer z tego samego IP w wyznaczonym czasie (np. 60 minut), zostaje zalogowany automatycznie bez ponownego wpisywania hasła.
- **Asynchroniczny zapis na dysk (`saveAsync`):** Zapis kont graczy z flagami dirty i blokadami `ReentrantLock` — zapis na dysk nie obciąża głównego wątku serwera.

---

### 5. 📜 Weryfikacja Captcha w GUI Tabliczki (Sign GUI Captcha)
- **Interaktywny interfejs:** Wykorzystuje natywny interfejs edycji tabliczki Minecrafta.
- **Czytelny Actionbar:** Kod weryfikacyjny oraz odliczanie czasu wyświetlają się w pasku Actionbar nad ekwipunkiem (`KOD: 8K9P | Pozostały czas: 25s`).
- **Losowa linijka wprowadzania:** Gracz proszony jest o wpisanie kodu w określonej linijce tabliczki (np. linijka 2 lub 3), co w 100% eliminuje boty makro.
- **Automatyczny fallback:** Opcjonalna możliwość wpisania kodu na czacie, jeśli klient gracza nie otworzy interfejsu tabliczki.

---

### 6. 🚶 Weryfikacja Ruchu (Movement Check)
- **Test zachowania człowieka:** Wymusza na nowo dołączającym graczu wykonanie określonej liczby ruchów kamerą (Pitch/Yaw) oraz kilku kroków.
- Pasek postępu wyświetlany na żywo na Actionbarze.
- Skutecznie odsiewa proste boty pakietowe, które nie implementują zaawansowanej fizyki gracza.

---

### 7. 🚨 Inteligentny Tryb Obrony Przed Atakiem (Attack Mode)
- **Automatyczna aktywacja:** Ciągłe monitorowanie tempa wejść (CPS). W przypadku skoku wejść serwer aktywuje tryb obrony na określony czas.
- **Weryfikacja ponownego połączenia (Reconnect Check):** Podczas ataku gracz przy pierwszej próbie otrzymuje prośbę o odczekanie kilku sekund i ponowne wejście — boty uderzające jednorazowo odpadają natychmiast.
- **Inteligentne alerty dla administracji:** Throttling komunikatów o zablokowanych botach (agregacja alertów co sekundę) chroni administratorów przed zaspamowaniem czatu.

---

### 8. 📡 Weryfikacja Pingu (Ping-Before-Join) i Filtr Nicków
- **Ping Check:** Odrzuca połączenia, które nie wysłały wcześniejszego zapytania `ServerListPing` o stan serwera (zabezpieczenie przed tępymi botami łączącymi się bezpośrednio socketem). Pamięć pingu zabezpieczona twardym limitem 25 000 wpisów (Bounded Cache).
- **Filtr Regex:** Błyskawiczna walidacja długości i wzorców generowanych automatycznie nicków botów (`bot_*`, `player123`, `mcdrop*`, `crash*`).

---

### 9. 🔄 Automatyczne Sprawdzanie Aktualizacji (GitHub Updater)
- Asynchroniczne odpytywanie oficjalnego repozytorium GitHub (`Narczo12/api`).
- Automatyczne powiadamianie administratorów o dostępności nowej wersji przy dołączeniu na serwer oraz dedykowana komenda `/ab update`.

---

## 🚀 Instalacja

1. Pobierz lub skompiluj plik **`AntyBot-1.21.4.jar`**.
2. Umieść plik w katalogu **`plugins/`** na swoim serwerze Minecraft (**1.21.4** Paper / Purpur / Spigot).
3. Uruchom serwer. Plugin automatycznie wygeneruje konfigurację w folderze `plugins/AntyBot/`:
   - `config.yml` — główny plik konfiguracyjny z pełnymi opisami w języku polskim,
   - `accounts.yml` — baza zarejestrowanych kont graczy i sesji,
   - `trusted.yml` — lista zaufanych graczy omijających dodatkowe kontrole.

---

## ⌨️ Komendy i Uprawnienia

### Komendy dla graczy:
| Komenda | Aliasy | Opis |
| :--- | :--- | :--- |
| `/login <hasło>` | `/l`, `/zaloguj` | Logowanie do konta non-premium |
| `/register <hasło> <powtórz>` | `/reg`, `/zarejestruj` | Rejestracja nowego konta non-premium |
| `/changepassword <stare> <nowe>` | `/zmienhaslo` | Zmiana hasła do konta |
| `/premium` | `/autologin` | Sprawdzenie statusu konta Premium / Auto-login |

### Komendy administracyjne:
> Wymagane uprawnienie: **`antibot.admin`**

| Komenda | Opis |
| :--- | :--- |
| `/ab status` | Szczegółowe statystyki: stan obrony, CPS, zablokowane boty, zaufani gracze, pamięć |
| `/ab reload` | Błyskawiczne przeładowanie pliku `config.yml` bez restartu serwera |
| `/ab attack <on\|off> [czas]` | Ręczne włączenie lub wyłączenie trybu obrony przed atakiem |
| `/ab captcha [gracz]` | Wymuszenie testu Sign GUI Captcha na sobie lub wybranym graczu |
| `/ab vpn status` | Podgląd stanu modułu Anti-VPN (wątki robocze, kolejka, limity, rozmiar cache) |
| `/ab vpn check <ip\|gracz>` | Ręczne sprawdzenie adresu IP gracza w bazie Anti-VPN |
| `/ab vpn clear` | Wyczyszczenie pamięci podręcznej IP w module Anti-VPN |
| `/ab update` | Ręczne sprawdzenie dostępności nowej wersji pluginu na GitHubie |
| `/ab trust <gracz>` | Ręczne dodanie gracza do listy zaufanych (`trusted.yml`) |
| `/ab untrust <gracz>` | Usunięcie gracza z listy zaufanych |
| `/ab unregister <gracz>` | Usunięcie konta gracza z bazy danych |
| `/ab setpassword <gracz> <hasło>` | Zmiana hasła dla wskazanego konta |
| `/ab forcelogin <gracz>` | Wymuszone zalogowanie gracza online z pominięciem hasła |
| `/ab setpremium <gracz> <true\|false>` | Ręczne ustawienie flagi Premium dla gracza |
| `/ab clear` | Reset liczników, czyszczenie pamięci podręcznej IP, sesji i kolejek |

### Uprawnienia:
- `antibot.admin` — dostęp do wszystkich komend `/ab` oraz powiadomień o atakach i aktualizacjach (domyślnie: OP).
- `antibot.bypass` — ominięcie wszystkich kontroli antybotowych, captchy, limitów IP oraz testów pingu (domyślnie: nikt).

---

## ⚙️ Przykładowa Konfiguracja (`config.yml`)

```yaml
# ============================================================
#  ANTYBOT - Konfiguracja Ochrony Serwera Minecraft 1.21.4
# ============================================================

prefix: "&8[&cAntyBot&8] &7"

attack-mode:
  enabled: true
  trigger-joins: 8
  trigger-seconds: 4
  attack-duration-seconds: 60
  require-reconnect: true
  reconnect-delay-seconds: 3

ip-limit:
  enabled: true
  max-connections-per-ip: 3
  shared-ip:
    enabled: true
    extra-connections: 2
  global-throttle:
    enabled: true
    max-joins-per-second: 15
  burst-protection:
    enabled: true
    max-connections: 5
    window-seconds: 3
  temp-block:
    enabled: true
    duration-seconds: 15
  verification-queue:
    max-concurrent-verifications: 25
  single-flight:
    enabled: true

anti-vpn:
  enabled: true
  trigger-mode: ALWAYS
  provider: AUTO
  api-key: ""
  block-hosting: true
  bypass-trusted: true
  cache-time-hours: 24
  max-worker-threads: 4
  max-pending-tasks: 100
  max-concurrent-requests: 5
  global-checks-per-minute: 60
  overload-policy: FAIL_OPEN

auth:
  enabled: true
  premium-auto-detect: true
  timeout-seconds: 60
  max-failed-attempts: 5
  blindness: true
  session:
    enabled: true
    timeout-minutes: 60

update-checker:
  enabled: true
  github-repo: "Narczo12/api"
  notify-admins-on-join: true
  check-interval-hours: 6
```

---
