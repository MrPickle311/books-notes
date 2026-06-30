# Roadmapa Projektowa 2026: Od JVM do Jądra Linuxa i Systemów Rozproszonych

Poniższy zestaw projektów jest zaprojektowany w myśl zasady **"Build to Learn"**. Zamiast tworzyć kolejne aplikacje biznesowe CRUD, skupiamy się na niskopoziomowej inżynierii systemowej, integracji międzyprocesowej (IPC), inżynierii platformowej oraz wyzwaniach architektury rozproszonej.

Jako inżynier z backgroundem w C/C++ i wieloletnim doświadczeniem w Javie, Twoim celem jest połączenie tych światów za pomocą nowoczesnych narzędzi (Go, GraalVM, Project Panama, eBPF, Loom).

## 🛠 Kamień Milowy 1: "Zero-Copy" Data Pipeline (Pamięć Współdzielona)

*Przełamywanie barier między procesami z pominięciem stosu sieciowego (TCP).*

**Cel:** Zbudowanie systemu przesyłania wiadomości między procesami w różnych językach, wykorzystując tę samą przestrzeń w pamięci RAM. Czas przesyłu: < 1 mikrosekunda.

**Architektura i Technologie:**

* **C++ (Producent):** Używa wywołań systemowych `mmap` i `shm_open` (POSIX Shared Memory) do zaalokowania bufora kołowego (Ring Buffer) bezpośrednio w pamięci.

* **Java 25 (Konsument):** Wykorzystuje **Project Panama (FFM API)** do mapowania wskaźników na ten sam obszar *off-heap* z pominięciem Garbage Collectora.

* **Go (Obserwator):** Używa pakietu `syscall` do odczytywania stanu bufora.

**Powiązanie z TLPI:**

* Rozdział 49: Memory Mappings (`mmap`)

* Rozdział 54: POSIX Shared Memory

## 🐳 Kamień Milowy 2: Własny "J-Docker" (Silnik Kontenerów od Zera)

*Zrozumienie, czym naprawdę jest środowisko uruchomieniowe w chmurze.*

**Cel:** Napisanie własnego narzędzia CLI w Go, które izoluje proces Javy używając mechanizmów Linuxa, aby dogłębnie zrozumieć błędy typu `OOMKilled`.

**Architektura i Technologie:**

* **Go (CLI Tool):** Aplikacja konsolowa przyjmująca komendę do uruchomienia (np. `java -jar app.jar`).

* **Linux System Calls:** Użycie `CLONE_NEWPID` i `CLONE_NEWNET` do izolacji.

* **Cgroups v2:** Skonfigurowanie twardego limitu RAM/CPU.

* **Java:** Uruchomienie zasobożernego Spring Boota i obserwacja reakcji jądra (sygnały).

**Powiązanie z TLPI:**

* Rozdziały o tworzeniu procesów (`clone`, `fork`, `execve`).

* Namespaces (izolacja procesów).

## ☸️ Kamień Milowy 3: Kubernetes Operator dla Aplikacji Natywnych

*Przejście na poziom zarządzania infrastrukturą chmurową.*

**Cel:** Stworzenie inteligentnego kontrolera K8s, który automatyzuje cykl życia ultralekkich mikroserwisów.

**Architektura i Technologie:**

* **Java (Aplikacja):** Spring Boot skompilowany do pliku binarnego za pomocą **GraalVM Native Image** (AOT) w minimalnym obrazie `scratch`.

* **Go (K8s Operator):** Kontroler używający frameworka **Kubebuilder**, rejestrujący CRD i decydujący o skalowaniu podów oraz obsłudze sygnałów `SIGTERM`.

**Powiązanie z TLPI:**

* Rozdział 20 i kolejne: Sygnały (Signals) i ich poprawna obsługa w cyklu życia procesów rozproszonych.

## 🔬 Kamień Milowy 4: Zero-Instrumentation eBPF Profiler

*Święty Graal inżynierii platformowej i observability.*

**Cel:** Narzędzie do profilowania aplikacji JVM działające poza maszyną wirtualną (kernel-space), bez narzutu na aplikację.

**Architektura i Technologie:**

* **Ograniczone C (Sonda eBPF):** Kod wstrzykiwany do jądra Linuxa, podpinający się pod gniazda sieciowe (`sendto`, `recvfrom`) używane przez JVM.

* **Go (User-Space Loader):** Program ładujący sondę, odczytujący i wizualizujący mapy eBPF.

**Powiązanie z TLPI:**

* Rozdział 58-61: Sockets API (warstwa transportowa i implementacja sieci w systemie).

## ⚙️ Kamień Milowy 5: Customowy Process Manager (Platform Engineering)

*Zrozumienie, jak systemy operacyjne zarządzają flotą demonów (np. `systemd`).*

**Cel:** Zbudowanie własnego zarządcy procesów, który uruchamia flotę aplikacji Javowych, monitoruje ich zdrowie (liveness), zbiera ich standardowe wyjście (logi) i automatycznie je restartuje po awarii.

**Architektura i Technologie:**

* **Go (Supervisor Daemon):** Proces "matka". Tworzy grupy procesów i zarządza cyklem życia dzieci.

* **Java (Worker):** Aplikacje (procesy potomne), które celowo losowo zgłaszają błędy lub rzucają `System.exit()`.

* **Strumienie IPC:** Daemon w Go przechwytuje `stdout` i `stderr` procesów Javy za pomocą anonimowych potoków (pipes), wzbogaca je o metadane (timestamp, PID) i zapisuje w jednym zagregowanym pliku.

**Powiązanie z TLPI:**

* Rozdział 24-28: Tworzenie, przerywanie i monitorowanie procesów potomnych (`waitpid`, osierocone procesy, procesy zombie).

* Rozdział 34: Grupy procesów, Sesje i kontrola zadań (Daemons).

* Rozdział 44: Pipes i FIFOs.

## 🌐 Kamień Milowy 6: Rozproszony Menedżer Blokad - "Mini-Raft" (Systemy Rozproszone)

*Rozwiązywanie problemu "Globalnego Beana" i koordynacji w klastrze.*

**Cel:** Implementacja uproszczonego algorytmu konsensusu (Raft), aby stworzyć własny, rozproszony system zarządzania blokadami (Distributed Lock) na wzór Zookeepera/ETCD.

**Architektura i Technologie:**

* **Go (Klaster Raft):** 3 małe węzły napisane w Go komunikujące się przez gRPC. Wybierają między sobą "Lidera" (Leader Election) i replikują stan (Log Replication). Jeśli węzeł zginie, pozostałe wybierają nowego lidera.

* **Java (Aplikacja Kliencka):** Posiada proces w tle (np. generowanie raportów). Zanim proces wystartuje, aplikacja wysyła zapytanie gRPC do klastra w Go o nałożenie globalnej blokady (Distributed Lock). Dzięki temu tylko jedna instancja Javy w całym klastrze Kubernetes wykonuje zadanie.

**Powiązanie z TLPI & Teorią:**

* Rozdziały 58-61: Sockets (Zrozumienie zachowania sieci podczas zerwania połączenia - Network Partitions).

* *Literatura dodatkowa:* Publikacja "In Search of an Understandable Consensus Algorithm (Raft)".

## 🚀 Kamień Milowy 7: API Gateway typu "C10K" (Zaawansowane I/O i Project Loom)

*Jak radzić sobie z dziesiątkami tysięcy jednoczesnych połączeń bez topienia procesora.*

**Cel:** Zbudowanie wydajnego Reverse Proxy / Load Balancera, który rozrzuca ruch HTTP(S) do setek usług w Javie, maksymalizując wykorzystanie nowoczesnych modeli I/O.

**Architektura i Technologie:**

* **Go (Edge Proxy):** Przyjmuje surowy ruch sieciowy. Go Runtime pod maską genialnie wykorzystuje asynchroniczne I/O (`epoll` w Linuxie), co pozwala obsłużyć 100k połączeń na jednym małym serwerze. Dodatkowo Proxy zarządza terminacją mTLS (Mutual TLS).

* **Java 25 (Backend API):** Mikroserwisy wystawiające szybkie endpointy HTTP. Używają **Project Loom (Virtual Threads)**, dzięki czemu każde przychodzące żądanie (nawet te powolne, np. I/O blokujące do bazy danych) dostaje własny lekki wątek w Javie, bez obciążania puli wątków systemowych (Platform Threads).

**Powiązanie z TLPI:**

* Rozdział 63: Alternatywne Modele I/O (`epoll`, Event-Driven I/O, Non-blocking I/O). Zrozumienie, co robią w tle środowiska uruchomieniowe Go oraz Javy (Netty/Tomcat).

## 🗄️ Kamień Milowy 8: Zero-Copy File Server (Zaawansowane I/O na Dysku)

*Odkrycie, jak Apache Kafka czy Nginx osiągają przepustowość rzędu gigabajtów na sekundę.*

**Cel:** Zbudowanie serwera plików, który serwuje dane statyczne lub zrzuty bazy danych (np. kilkugigabajtowe pliki) prosto do gniazda sieciowego, omijając całkowicie bufor przestrzeni użytkownika (User-Space) i Garbage Collector.

**Architektura i Technologie:**

* **Go / C++ (Serwer):** Wykorzystanie wywołania systemowego `sendfile()`, które instruuje jądro (kernel), aby przetransferowało bajty z dysku bezpośrednio do karty sieciowej.

* **Java (Klient Benchmarkowy):** Aplikacja wielowątkowa w Javie generująca ogromny ruch, weryfikująca przepustowość oraz obciążenie CPU serwera (które powinno być bliskie zera podczas transferu).

**Powiązanie z TLPI:**

* Rozdział 4 i 5: File I/O (`O_DIRECT` do omijania cache'u systemu plików).

* Rozdział 13: File I/O Buffering.

* Rozdział 61: Advanced I/O (`sendfile`).

## 🛡️ Kamień Milowy 9: "J-Sandbox" (Bezpieczeństwo i Capabilities)

*Wykorzystanie jądra systemu jako ostatecznej tarczy bezpieczeństwa dla untrusted kodu.*

**Cel:** Stworzenie bezpiecznego środowiska uruchomieniowego dla zewnętrznych pluginów (np. kodu napisanego przez użytkowników), które gwarantuje, że kod nie usunie plików systemowych ani nie otworzy złośliwych połączeń sieciowych.

**Architektura i Technologie:**

* **C / Go (Launcher):** Program, który przed uruchomieniem maszyny wirtualnej Javy, upuszcza swoje uprawnienia za pomocą Linux Capabilities, a następnie nakłada filtry **seccomp-bpf** na swój własny proces.

* **Java (Untrusted Code):** Proces JVM działający w trybie, w którym wywołanie np. `new Socket()` lub `new FileOutputStream()` zostaje natychmiast ubite przez jądro systemowe z sygnałem `SIGSYS`, ponieważ zablokowano wywołania systemowe `connect` i `open`.

**Powiązanie z TLPI:**

* Rozdział 38: Secure Privileged Programs.

* Rozdział 39: Linux Capabilities (dzielenie uprawnień roota na kawałki).

* *Rozszerzenie:* Linux Seccomp (Secure Computing Mode).

## ⏱️ Kamień Milowy 10: Low-Latency Time-Series Engine (Zarządzanie Czasem)

*Zrozumienie, jak systemy High-Frequency Trading operują czasem z precyzją co do nanosekundy.*

**Cel:** Zbudowanie własnego, asynchronicznego silnika bazy danych szeregów czasowych, który zapisuje metryki w interwałach milisekundowych bez ryzyka przerw wywołanych przez GC Javy.

**Architektura i Technologie:**

* **C / C++ (Timer Daemon):** Proces wykorzystujący `timerfd_create`, który integruje wyzwalacz czasowy bezpośrednio z pętlą zdarzeń (`epoll`). Budzi się z precyzją do nanosekundy i sygnalizuje konieczność zapisu.

* **Java 25 (Data Ingestion):** Używa off-heap (Panama) do zbierania danych z pamięci współdzielonej (podobnie jak w Kamieniu #1) i zapisuje je na dysk za pomocą zoptymalizowanego lock-free I/O, gdy Timer Daemon da sygnał. Używa `flock` do zapobiegania konfliktom zapisu.

**Powiązanie z TLPI:**

* Rozdział 23: Timers and Sleeping (`timerfd`, POSIX timers).

* Rozdział 55: File Locking (`fcntl`, wyłączny dostęp do plików na poziomie jądra).

### Słownik Pojęć Technologicznych (2026)

* **Project Panama (FFM API):** Bezpieczne wywoływanie kodu C/C++ i zarządzanie natywną pamięcią przez Javę.

* **GraalVM Native Image / AOT:** Kompilacja kodu Javy przed uruchomieniem, drastycznie obniżająca czas startu i RAM.

* **eBPF (Extended Berkeley Packet Filter):** Uruchamianie kodu wewnątrz jądra dla celów monitoringu i bezpieczeństwa.

* **Project Loom (Virtual Threads):** Nowy model współbieżności Javy rozwiązujący problem C10K dla blokującego kodu.

* **Raft Consensus:** Algorytm pozwalający rozproszonemu klastrowi serwerów podjąć wspólną decyzję.

* **Zero-Copy (sendfile):** Technika przesyłania danych (np. z pliku do sieci), w której procesor (CPU) nie musi kopiować danych pomiędzy przestrzenią jądra a przestrzenią aplikacji, oszczędzając zasoby.

* **Seccomp / Capabilities:** Mechanizmy jądra Linuxa drastycznie ograniczające to, co może zrobić proces (np. blokujące mu możliwość tworzenia nowych plików).
