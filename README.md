# 🛡️ AntyBot - Plugin Ochronny + Auth Premium dla Minecraft 1.21.4

Wysokowydajny, lekki i kompleksowy plugin na serwery **Minecraft 1.21.4** (Paper, Purpur, Spigot), łączący:
1. **Asynchroniczną ochronę przed botami** (`AsyncPlayerPreLoginEvent`),
2. **Weryfikację Captcha w interfejsie tabliczki (Sign GUI)**,
3. **Wbudowany system logowania i rejestracji z automatycznym logowaniem kont PREMIUM (Mojang API)**.

---

## ⚡ Główne moduły

### 1. 👑 Automatyczne Logowanie Graczy Premium (Auto-Login)
- Plugin asynchronicznie odpytuje oficjalne **Mojang API** o status konta gracza.
- **Gracze posiadający konto Premium (oryginalnego Minecrafta) NIE MUSZĄ się rejestrować ani wpisywać haseł!**
- Gracz Premium jest natychmiast logowany na serwer i witany komunikatem o aktywnym statusie Premium.
- Gracze Premium automatycznie omijają również weryfikację Captcha.

### 2. 🔐 System Logowania i Rejestracji (Non-Premium)
- Bezpieczne haszowanie haseł algorytmem **SHA-256 z indywidualną solą (salt)**.
- Komendy: `/register <hasło> <powtórzHasło>`, `/login <hasło>`, `/changepassword <stare> <nowe>`, `/premium`.
- Ograniczenia dla niezalogowanych: zamrożenie ruchu, blokada czatu, komend, obrażeń, wyrzucania przedmiotów i otwierania ekwipunków.
- **Auto-login po IP (Sesja):** Gracz non-premium, który ponownie wejdzie na serwer z tego samego IP w określonym czasie (np. 60 min), nie musi ponownie wpisywać hasła.

### 3. 📜 Weryfikacja Captcha w GUI Tabliczki (Sign GUI Captcha)
- Kod oraz **odliczanie pozostałego czasu** wyświetlają się **wyłącznie na Actionbarze** (np. `KOD: 8K9P | Pozostały czas: 25s`).
- Gracz wpisuje kod w konkretnej wskazanej linijce tabliczki (wskazanej strzałkami na tabliczce oraz na czacie) i klika **Gotowe**.
- Pełna ochrona przed botami makro.

### 4. 🚨 Tryb Ataku (Panic / Attack Mode)
- Wykrywa skok liczby wejść (CPS) i aktywuje czasową blokadę nowo dołączających botów.
- Weryfikacja ponownego połączenia (**Reconnect Check**).

### 5. 📡 Weryfikacja Pingu i Limity IP
- Odrzuca połączenia bez wcześniejszego zapytania o listę serwerów (Ping-Before-Join).
- Limit równoległych kont z jednego adresu IP (max połączeń per IP).
- Filtr regex podejrzanych wzorców nicków.

---

## 🚀 Instalacja

1. Skopiuj gotowy plik **`AntyBot-1.21.4.jar`** do katalogu **`plugins/`** na Twoim serwerze.
2. Uruchom lub zrestartuj serwer Minecraft (**1.21.4** Paper / Purpur / Spigot).
3. Gotowe! Pliki konfiguracyjne zostaną utworzone w folderze `plugins/AntyBot/`:
   - `config.yml` — konfiguracja antybota, captchy i logowania
   - `accounts.yml` — bezpieczna baza kont graczy
   - `trusted.yml` — baza zaufanych graczy

---

## ⌨️ Komendy

### Komendy dla graczy:
| Komenda | Opis |
| :--- | :--- |
| `/login <hasło>` | Logowanie do konta non-premium (alias: `/l`, `/zaloguj`) |
| `/register <hasło> <powtórz>` | Rejestracja nowego konta non-premium (alias: `/reg`, `/zarejestruj`) |
| `/changepassword <stare> <nowe>` | Zmiana hasła do konta (alias: `/zmienhaslo`) |
| `/premium` | Sprawdzenie statusu konta Premium / Auto-login (alias: `/autologin`) |

### Komendy administracyjne (`antibot.admin`):
| Komenda | Opis |
| :--- | :--- |
| `/ab status` | Statystyki ochrony, CPS, zablokowane boty, zaufani gracze |
| `/ab captcha [gracz]` | Wymuszenie testu Sign GUI Captchy na sobie lub wskazanym graczu |
| `/ab unregister <gracz>` | Usunięcie konta gracza z bazy danych |
| `/ab setpassword <gracz> <hasło>` | Ręczna zmiana hasła dla danego konta |
| `/ab forcelogin <gracz>` | Wymuszenie zalogowania gracza będącego online |
| `/ab attack <on\|off> [czas]` | Ręczne włączenie lub wyłączenie trybu ochrony |
| `/ab reload` | Przeładowanie pliku `config.yml` bez restartu serwera |
| `/ab trust <gracz>` | Ręczne dodanie gracza do listy zaufanych |
| `/ab untrust <gracz>` | Usunięcie gracza z listy zaufanych |
| `/ab clear` | Wyszyszczenie pamięci podręcznej i liczników sesji |

---

## ⚙️ Konfiguracja Autoryzacji (`config.yml`)

```yaml
auth:
  enabled: true
  # Automatyczne wykrywanie kont Mojang Premium przez API Mojang
  # Gracze Premium logują się bez hasła i rejestracji!
  premium-auto-detect: true
  # Czas na zalogowanie/rejestrację w sekundach
  timeout-seconds: 60
  # Maksymalna liczba błędnych haseł
  max-failed-attempts: 5
  min-password-length: 4
  max-password-length: 32
  # Efekt oślepienia do momentu zalogowania
  blindness: true
  # Auto-login po IP dla graczy non-premium
  session:
    enabled: true
    timeout-minutes: 60
```

---

## 🔨 Kompilacja ze źródeł

- **Windows:** Uruchom plik **`build.bat`**.
- **Terminal:** `.\mvnw.cmd clean package` (lub `./mvnw clean package` na Linuxie).
Skompilowany plik znajduje się w katalogu **`target/AntyBot-1.0.0.jar`**.
