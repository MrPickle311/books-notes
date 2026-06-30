# 🌐 Kompendium Sieci dla Inżyniera Systemów Rozproszonych (Spis Treści)

> Zbiór najważniejszych konceptów sieciowych z perspektywy projektowania, wdrażania i debugowania systemów rozproszonych. Od konfiguracji "żelastwa" (L2), przez wirtualizację Linuksa, aż po protokoły aplikacyjne (L7).

---

## 🗺️ Szybka Nawigacja

*   [1. Fundamenty i Modele Sieciowe](#1-fundamenty-i-modele-sieciowe)
*   [2. Infrastruktura: Warstwa L2 i Segmentacja (Switching)](#2-infrastruktura-warstwa-l2-i-segmentacja-switching)
*   [3. Infrastruktura: Zaawansowany Routing, IP i SDN (Warstwa L3)](#3-infrastruktura-zaawansowany-routing-ip-i-sdn-warstwa-l3)
*   [4. Infrastruktura: Szkoła Wyższej Jazdy (Chmura, Tunele, Architektura)](#4-infrastruktura-szkoła-wyższej-jazdy-chmura-tunele-architektura)
*   [5. Synchronizacja Czasu i Obserwowalność (System & IP menu)](#5-synchronizacja-czasu-i-obserwowalność-system--ip-menu)
*   [6. Komunikacja Transportowa (L4)](#6-komunikacja-transportowa-l4)
*   [7. Protokoły Aplikacyjne i Nazywanie (L7)](#7-protokoły-aplikacyjne-i-nazywanie-l7)
*   [8. Równoważenie Ruchu i Architektura](#8-równoważenie-ruchu-i-architektura)
*   [9. Wzorce Niezawodności (Resiliency Patterns)](#9-wzorce-niezawodności-resiliency-patterns)
*   [10. Bezpieczeństwo Sieciowe (Network Security)](#10-bezpieczeństwo-sieciowe-network-security)
*   [11. Linuksowy Przybornik Inżyniera Sieci (Narzędzia CLI)](#11-linuksowy-przybornik-inżyniera-sieci-narzędzia-cli)
*   [12. Wewnętrzna Architektura Sieciowa Linuksa (Wirtualizacja)](#12-wewnętrzna-architektura-sieciowa-linuksa-wirtualizacja)
*   [13. Sieci w Świecie Kontenerów (Docker / Kubernetes)](#13-sieci-w-świecie-kontenerów-docker--kubernetes)

---

## 1. [Fundamenty i Modele Sieciowe](./1_foundamentals.html)

*Zrozumienie, jak systemy kategoryzują ruch sieciowy i z jakimi problemami mierzą się na starcie.*

### 🔹 1.1. 8 Mitów Systemów Rozproszonych
Dlaczego sieć zawsze będzie rzucać kłody pod nogi:
*   **Opóźnienia** (*latency*) są niezerowe.
*   **Przepustowość** (*bandwidth*) nie jest nieskończona.
*   **Awarie** (*outages*) i brak niezawodności sieci.
*   **Topologia** (*topology*) nie jest stała i ulega zmianom.

### 🔹 1.2. Modele referencyjne i Protokoły

| Warstwa | Nazwa Warstwy | Jednostka PDU | Kluczowe Elementy i Protokoły | Perspektywa DSE (Distributed Systems Engineer) |
| :---: | :--- | :---: | :--- | :--- |
| **L7** | **Aplikacji** | Wiadomość / Dane | `HTTP`, `DNS`, `gRPC`, `SMTP`, `WebSockets` | Bezpośrednia interakcja z aplikacją, nagłówki, routing, kodowanie. |
| **L4** | **Transportowa** | Segment / Datagram | `TCP`, `UDP`, Porty | Multiplexing, flow control, congestion control, port exhaustion. |
| **L3** | **Sieciowa** | Pakiet | `IP`, Routing, `ICMP`, `IPSec` | Routing pakietów między podsieciami, adresacja `CIDR`, `NAT`, zapory ogniowe. |
| **L2** | **Łącza danych** | Ramka | `MAC`, `VLAN`, `ARP`, Ethernet | Lokalna sieć fizyczna i wirtualna, przełączanie ramek. |
| **L1** | **Fizyczna** | Bit | Kable, sygnały elektryczne/świetlne | Zazwyczaj czarna skrzynka (hardware). |

### 🔹 1.3. Architektura Data Plane vs Control Plane

*   **Data Plane (Płaszczyzna Danych)**: *"Głupie, ale bardzo szybkie"* mechanizmy odpowiedzialne za bezpośrednie przesyłanie i przerzucanie pakietów z portu na port (np. `Envoy`, `iptables`, `eBPF` w jądrze).
*   **Control Plane (Płaszczyzna Sterowania)**: *"Mózg"* całej sieci, podejmujący decyzje o routingu, zasadach bezpieczeństwa i konfigurujący podległe mu elementy Data Plane (np. `Kubernetes API Server`, `Istio`, `BGP`).

---

## 2. [Infrastruktura: Warstwa L2 i Segmentacja (Switching)](./2_infrastructure_l2.html)

*Sprzętowe fundamenty sieci lokalnej – to, co konfigurujesz logując się do switcha.*

*   **2.1. ARP (Address Resolution Protocol)**:
    *   Jak adres IP dogaduje się z fizycznym adresem MAC.
    *   Rola tablic ARP w urządzeniach.
    *   Zastosowanie **Gratuitous ARP (GARP)** przy implementacji mechanizmów High Availability (HA) i przełączaniu IP.
*   **2.2. DHCP i Leases (Dzierżawy)**:
    *   Proces **DORA** (`Discover`, `Offer`, `Request`, `Acknowledge`).
    *   Cykl życia adresu IP w sieci dynamicznej.
    *   Skutki wygaśnięcia dzierżawy IP dla działających systemów.
*   **2.3. Bridging i Switching (Logika L2)**:
    *   Mechanizm **MAC Learning** i tablice **FDB** (*Forwarding Database*): jak switch uczy się przypisania portów do adresów MAC.
*   **2.4. Pętle sieciowe i STP (Spanning Tree Protocol)**:
    *   Zjawisko **Broadcast Storm** i katastrofalne w skutkach awarie klastrów spowodowane fizycznym zapętleniem sieci.
*   **2.5. VLAN-y w praktyce (802.1Q)**:
    *   **Porty Access**: nietagowane, dedykowane dla bezpośrednich hostów.
    *   **Porty Trunk**: tagowane, przeznaczone do przesyłania ruchu wielu VLAN-ów między przełącznikami.
*   **2.6. MTU (Maximum Transmission Unit) i Jumbo Frames**:
    *   Rozmiar ramki/pakietu i jego wpływ na wydajność sieciową.
    *   Niezgodność MTU (*MTU Mismatch*) jako jedna z najczęstszych przyczyn uciętych połączeń i trudnych do wykrycia awarii.

> [!WARNING]
> **Krytyczna rola ICMP w Path MTU Discovery**
> Zablokowanie ruchu ICMP na zaporach sieciowych psuje mechanizm **Path MTU Discovery (PMTUD)**. Routery pośredniczące nie mogą odesłać informacji *"pakiet jest za duży (DF bit set)"*, co prowadzi do cichego gubienia dużych żądań (np. długo wiszące połączenia `gRPC` kończące się błędem *payload timeout*).

---

## 3. [Infrastruktura: Zaawansowany Routing, IP i SDN (Warstwa L3)](./3_infrastructure_l3.html)

*Jak pakiety odnajdują drogę w gąszczu internetu i centrów danych.*

*   **3.1. Adresacja IP, CIDR oraz IPv6**:
    *   Podział na podsieci i kalkulacja masek.
    *   Adresacja publiczna vs prywatna (`RFC 1918`).
    *   Problem wyczerpywania się puli adresów IP w wielkich klastrach Kubernetes.
    *   Przejście na **IPv6**: eliminacja NAT, wymóg PMTUD, mechanika Dual-Stack.
    *   Różnica pojęciowa: **IP TTL** (L3 / liczba przeskoków-hops) vs **DNS TTL** (L7 / sekundy ważności cache).
*   **3.2. SDN (Software-Defined Networking)**:
    *   Podstawa chmur obliczeniowych.
    *   Wirtualizacja sieci (np. `VPC` w AWS / GCP).
    *   Oddzielenie sprzętu od logiki za pomocą reguł **Match-and-Action** (dopasuj cechy pakietu i wykonaj zdefiniowaną akcję).
*   **3.3. Routing Statyczny vs Dynamiczny (IGP i EGP)**:
    *   **RIP / OSPF**: Protokoły wewnętrzne (**IGP**) służące do dynamicznego mapowania sieci wewnątrz jednej autonomicznej organizacji.
    *   **BGP (Border Gateway Protocol)**: Protokół internetu (**EGP**). Używany również wewnętrznie w klastrach Kubernetes (np. przez `Calico`) do propagacji tras adresów kontenerów.
    *   **IP-Anycast (oparte na BGP)**: Absolutny fundament globalnych systemów rozproszonych. Technika pozwalająca przypisać ten sam adres IP do wielu serwerów na świecie. BGP automatycznie kieruje zapytanie użytkownika do fizycznie najbliższego centrum danych. Tak działają sieci CDN (np. `Cloudflare`) oraz globalne Load Balancery.
*   **3.4. BFD (Bidirectional Forwarding Detection)**:
    *   Protokół wykrywający awarie fizycznych linków w milisekundach, wymuszający natychmiastowe przeliczenie tras przez BGP/OSPF.
*   **3.5. VRF (Virtual Routing and Forwarding)**:
    *   Całkowicie odizolowane tablice routingu uruchomione na jednym urządzeniu fizycznym (pozwala na bezpieczne nakładanie się tych samych adresów IP w różnych sieciach prywatnych).
*   **3.6. NAT (Network Address Translation) i Middleboxy**:
    *   Rodzaje: **SNAT** (Masquerading), **DNAT** (Port Forwarding), **CGNAT**.

> [!NOTE]
> **Dlaczego NAT potrafi zrywać połączenia TCP?**
> Middleboxy i routery realizujące NAT muszą utrzymywać w pamięci tablicę translacji sesji. Aby zwolnić pamięć, sesje nieaktywne przez określony czas są usuwane (**NAT Timeout**). Z perspektywy aplikacji połączenie wiszące (np. nieaktywny strumień `gRPC`) zostaje cicho zerwane, dlatego kluczowe jest włączenie mechanizmu **KeepAlive**.

*   **3.7. Firewalling na routerach (IP -> Firewall)**:
    *   **Filter**: odrzucanie lub akceptowanie pakietów na podstawie reguł.
    *   **Mangle**: zaawansowana modyfikacja nagłówków pakietów w locie.

---

## 4. [Infrastruktura: Szkoła Wyższej Jazdy (Chmura, Tunele, Architektura)](./4_advanced_infrastructure.html)

*Jak dostawcy internetu i duże chmury organizują fizycznie globalną łączność.*

### 🔹 4.1. Architektura Data Center (Sieci chmurowe)
*   **Topologia Spine-Leaf**: Nowoczesny sposób łączenia serwerów. Zamiast tradycyjnych struktur drzewiastych, stosuje się płaską sieć o stałym, minimalnym opóźnieniu (*latency*) dla ruchu **Wschód-Zachód** (komunikacja między mikroserwisami).
*   **Ruch Północ-Południe vs Wschód-Zachód**: W chmurach obliczeniowych nawet **90%** transferu to komunikacja wewnątrz centrum danych (Wschód-Zachód), a nie bezpośredni ruch do klienta (Północ-Południe).
*   **ECMP (Equal-Cost Multi-Path)**: Balansowanie ruchu na poziomie warstwy L3 na wiele równoległych fizycznych tras, aby optymalnie wykorzystać przepustowość sieci Spine-Leaf.

### 🔹 4.2. Sieci Nakładkowe (Overlay Networks) i Wirtualizacja L2
*   **VXLAN (Virtual eXtensible LAN)**: Enkapsulacja całych ramek warstwy L2 (Ethernet) w pakiety UDP. Podstawa wirtualizacji sieci chmurowych (VPC) oraz Kubernetes. Pozwala na tworzenie płaskich sieci lokalnych na wierzchu skomplikowanej infrastruktury L3, izolując poszczególnych dzierżawców od siebie (*Multi-tenancy*).

### 🔹 4.3. Sieci Rozległe (MPLS i VPLS)
*   **MPLS (Multi-Protocol Label Switching)**: Szybkie przesyłanie pakietów po etykietach zamiast pełnego routingu IP w szkieletach operatorskich.
*   **VPLS**: Technika rozciągania wirtualnej warstwy L2 (VLAN-ów) pomiędzy odległymi geograficznie centrami danych.

### 🔹 4.4. Tunele i VPN
*   Szyfrowanie i tunelowanie ruchu na poziomie L3 za pomocą protokołów: `IPsec`, `WireGuard` oraz `OpenVPN`.

### 🔹 4.5. Ruch Multicastowy
*   Protokoły `IGP` (`IGMP`, `PIM`) umożliwiające subskrypcję na strumienie ruchu sieciowego (przydatne m.in. dla klastrów rozproszonych o niskich opóźnieniach propagacji stanu).

### 🔹 4.6. Zarządzanie Pasmem (QoS)
*   **Bufferbloat**: Zjawisko nadmiernego buforowania pakietów na routerach, które drastycznie zwiększa czasy RTT (opóźnienia ping).
*   **Traffic Shaping**: Priorytetyzacja ruchu (chroni krytyczne pakiety kontrolne przed zablokowaniem przez wielkie transfery plików).

---

## 5. [Synchronizacja Czasu i Obserwowalność (System & IP menu)](./5_time_and_observability.html)

*Kluczowe mechanizmy pozwalające zachować spójność stanu w rozproszonych systemach.*

*   **5.1. NTP (Network Time Protocol)**:
    *   Podstawowa synchronizacja zegarów systemowych.
    *   Skutki rozbieżności czasu (*clock skew*): błędy walidacji tokenów JWT, odrzucanie uścisków dłoni TLS oraz rozjeżdżające się logi aplikacji.
*   **5.2. PTP (Precision Time Protocol)**:
    *   Precyzyjna synchronizacja z dokładnością do nanosekund.
    *   Kluczowa dla nowoczesnych rozproszonych baz danych (np. `Google Spanner`, `CockroachDB`) do ustalania globalnej kolejności zdarzeń (konsensus).
*   **5.3. Monitoring sieci (SNMP, NetFlow / Traffic Flow)**:
    *   **SNMP**: Aktywne odpytywanie urządzeń sieciowych o metryki i statusy.
    *   **NetFlow / IPFIX**: Pasywna analiza telemetrii sieciowej (*kto, z kim, jaki wolumen danych wymienia*).

---

## 6. [Komunikacja Transportowa (L4)](./6_transport_l4.html)

*Jak systemy operacyjne realizują transport danych.*

*   **6.1. Gniazda (Sockets), Porty i Stany TCP**:
    *   Zjawiska Multiplexingu i Demultiplexingu.
    *   **Port Exhaustion**: Wyczerpanie puli portów efemerycznych (tymczasowych) przy tworzeniu tysięcy szybkich, krótkotrwałych połączeń.
    *   **Stany TCP**: Wycieki połączeń w stanach `TIME_WAIT` (recykling gniazd, `SO_REUSEADDR`) vs `CLOSE_WAIT` (błędy w kodzie aplikacji nie zamykającej gniazd).
*   **6.2. TCP (Transmission Control Protocol) - Fundamenty**:
    *   **3-way handshake**: Koszt czasowy nawiązania połączenia (`SYN` -> `SYN-ACK` -> `ACK`).
    *   **Head-of-Line (HoL) Blocking**: Zgubienie jednego pakietu wstrzymuje przetwarzanie całej kolejki odbiorcy, nawet jeśli kolejne pakiety dotarły prawidłowo.
    *   **Operational Gotchas**: Zakleszczenie algorytmu Nagle'a i Delayed ACK (`TCP_NODELAY`), oraz różnice między **TCP Keepalive** (L4) a **HTTP Keep-Alive** (L7).
*   **6.3. TCP - Mechanizmy Kontrolne (Wydajność)**:
    *   **Flow Control (Kontrola przepływu)**: Ochrona odbiorcy przed zalaniem danymi (Backpressure za pomocą oku odbiorcy `rwnd`).
    *   **Congestion Control (Kontrola zatłoczenia)**: Ochrona samej sieci przed przeciążeniem (mechanizm `TCP Slow Start` – główny powód, dla którego powinieneś stosować *Connection Pooling*). Porównanie algorytmów **CUBIC** (oparty na stratach pakietów) oraz **BBR** (oparty na opóźnieniach i BDP).
*   **6.4. UDP (User Datagram Protocol)**:
    *   Szybkie, bezpołączeniowe wysyłanie pakietów bez gwarancji dostarczenia.
*   **6.5. QUIC**:
    *   Nowoczesny protokół transportowy od Google oparty na UDP, eliminujący HoL Blocking i przyspieszający uściski dłoni (wykorzystywany w standardzie `HTTP/3`).

---

## 7. [Protokoły Aplikacyjne i Nazywanie (L7)](./7_application_protocols_and_naming.html)

*Język, w którym rozmawiają bezpośrednio nasze serwery i aplikacje.*

*   **7.1. DNS w Systemach Rozproszonych**:
    *   Czasy życia rekordów (`TTL`), specyfika cache'owania adresów (np. w maszynie wirtualnej Java JVM), techniki load balancingu przez `Round-Robin DNS`.
*   **7.2. Ewolucja standardu HTTP**:
    *   `HTTP/1.1` (wprowadzenie `Keep-Alive`).
    *   `HTTP/2` (multipleksowanie strumieni w ramach jednego połączenia TCP, pułapka limitu `max_concurrent_streams` i błędy `REFUSED_STREAM`).
    *   `HTTP/3` (przejście na wydajny protokół transportowy `QUIC`).
*   **7.3. Protokoły RPC i asynchroniczne**:
    *   Wydajna komunikacja binarna `gRPC` (i wyzwania load balancingu L4 vs L7), dwukierunkowe `WebSockets`, jednokierunkowe subskrypcje `SSE` (Server-Sent Events).
*   **7.4. SMTP (Simple Mail Transfer Protocol)**:
    *   Tradycyjny protokół wysyłki poczty elektronicznej.
*   **7.5. CDN (Content Delivery Networks) i Nagłówki Cache L7**:
    *   Rola serwerów brzegowych w systemach rozproszonych.
    *   Sterowanie buforowaniem: nagłówki `Cache-Control` (`public`, `private`, `no-store`, `max-age`).
    *   Walidacja warunkowa (Conditional Requests): nagłówki `ETag` i `If-None-Match` oraz statusy `304 Not Modified`.
    *   Asynchroniczna aktualizacja: dyrektywa `stale-while-revalidate` (SWR) i redukcja opóźnień do 0ms.

---

## 8. [Równoważenie Ruchu i Architektura](./8_load_balancing_and_architecture.html)

*Rozpraszanie obciążenia pomiędzy maszyny.*

### 🔹 8.1. L4 Load Balancing vs L7 Load Balancing

| Cecha | L4 Load Balancing (Transportowy) | L7 Load Balancing (Aplikacyjny) |
|---|---|---|
| **Poziom działania** | Warstwa L4 (`TCP` / `UDP`) | Warstwa L7 (`HTTP` / `gRPC` / `TLS`) |
| **Wgląd w payload** | Brak (widzi tylko adresy IP i porty) | Pełny (widzi nagłówki, ciasteczka, ścieżki URL) |
| **Wydajność** | Ekstremalnie wysoka (minimalny narzut CPU) | Średnia (wymaga terminacji TLS i analizy żądania) |
| **Możliwości** | Proste przekazywanie pakietów IP | Routing na bazie ścieżek, retries, autoryzacja |
| **Przykłady** | `IPVS`, `HAProxy` (L4 mode), AWS `NLB` | `Nginx`, `Envoy`, `HAProxy` (L7), AWS `ALB` |

*   **8.1.2. Direct Server Return (DSR)**:
    *   Unikanie wąskiego gardła wyjściowego. Modyfikacja MAC L2 / tunelowanie pakietów przy zachowaniu VIP na loopbacku backendów.
*   **8.2. Algorytmy balansowania i Consistent Hashing**:
    *   Prosty `Round Robin`, `Least Connections` oraz `Weighted Round Robin`.
    *   **Consistent Hashing (np. Ketama)**: mapowanie na okręgu (*Hash Ring*), minimalizacja cache invalidations i unikanie *Cache Stampede* przy dodawaniu/usuwaniu serwerów (przenoszenie tylko 1/N połączeń).
*   **8.3. Reverse Proxies i API Gateways**:
    *   Wykorzystanie `Nginx`, `HAProxy` oraz `Envoy`.
    *   Przekazywanie oryginalnych IP klientów przez nagłówki takie jak `X-Forwarded-For` lub protokół `Proxy Protocol`.
*   **8.4. Service Mesh**:
    *   Wzorce architektoniczne oparte na kontenerach pomocniczych Sidecar (np. `Istio`, `Linkerd`).

---

## 9. [Wzorce Niezawodności (Resiliency Patterns)](./9_resiliency_patterns.html)

*Jak budować odporne systemy, gdy sieć zawodzi.*

*   **9.1. Timeouts**:
    *   Rozróżnienie i właściwe konfigurowanie **Connection Timeout** (czas na nawiązanie uścisku L4) oraz **Read Timeout** (czas oczekiwania na odpowiedź L7).
*   **9.2. Retries, Exponential Backoff, Jitter i Budżety**:
    *   Ponowne próby połączeń, wydłużanie czasu oczekiwania między próbami oraz dodawanie elementu losowości (*Jitter*), aby zapobiec zjawisku zalania serwera przez klientów (*Thundering Herd*).
    *   **Burze Ponowień (Retry Storms)**: ryzyko wykładniczego wzrostu ruchu w głębokich łańcuchach mikrousług.
    *   **Budżety Ponowień (Retry Budgets)**: ograniczanie ponowień do ułamka (np. 10%) udanych zapytań w celu ochrony przeciążonych usług.
*   **9.3. Circuit Breakers i Bulkheads**:
    *   Maszyna stanów bezpiecznika (`CLOSED`, `OPEN`, `HALF-OPEN`) chroniąca przed niepotrzebnymi zapytaniami sieciowymi oraz wzorzec Grodzi (Bulkhead) izolujący pule wątków/zasobów.
*   **9.4. Health Checks**:
    *   **Liveness Probes**: czy proces żyje.
    *   **Readiness Probes**: czy usługa jest w pełni gotowa do przyjmowania ruchu.
    *   *Gotcha:* Unikanie podpinania zewnętrznych zależności (np. DB) pod Liveness Probe, aby zapobiec kaskadowym restartom całego klastra (*Cascading Failures*).

---

## 10. [Bezpieczeństwo Sieciowe (Network Security)](./10_network_security.html)

*Ochrona danych przesyłanych przez niezaufane medium.*

*   **10.1. TLS, SSL Handshake, SNI i ALPN**:
    *   Matematyczny i czasowy koszt nawiązywania bezpiecznego połączenia (TLS 1.2 vs TLS 1.3).
    *   **SNI (Server Name Indication)**: przesyłanie nazwy hosta w ClientHello w celu dopasowania właściwego certyfikatu na reverse-proxy.
    *   **ALPN (Application-Layer Protocol Negotiation)**: negocjowanie protokołu aplikacyjnego (np. HTTP/2) w locie podczas uścisku TLS, eliminujące nadmiarowy round-trip.
*   **10.2. mTLS (Mutual TLS)**:
    *   Standard uwierzytelniania dwukierunkowego w architekturze *Zero-Trust* wewnątrz sieci mikrousług.
*   **10.3. Zapory sieciowe**:
    *   Porównanie tradycyjnych zapor L4 (IP/porty) z inteligentnymi **WAF (Web Application Firewall)** działającymi na poziomie L7.
*   **10.4. CORS i zabezpieczenia przeglądarkowe**:
    *   Polityka *Cross-Origin Resource Sharing* (Same-Origin Policy), zapytania Preflight (`OPTIONS`) oraz poprawne konfigurowanie nagłówków odpowiedzi serwera.

---

## 11. [Linuksowy Przybornik Inżyniera Sieci (Narzędzia CLI)](./11_cli_tools.html)

*Narzędzia, które musisz opanować, aby skutecznie debugować problemy sieciowe.*

| Kategoria Narzędzi | Narzędzie CLI | Zastosowanie Praktyczne |
| :--- | :---: | :--- |
| **DNS & Routing** | `dig`, `nslookup`, `ping`, `traceroute`, `mtr` | Diagnostyka problemów z rozwiązywaniem nazw, sprawdzanie dostępności hosta (`ICMP`) oraz analiza utraty pakietów na trasie. |
| **Porty & Połączenia** | `ss`, `lsof` | Podgląd otwartych gniazd TCP/UDP, sprawdzanie procesów nasłuchujących na danych portach. |
| **Narzędzia L7** | `curl`, `nc` (netcat) | Wykonywanie testowych zapytań HTTP/API, wysyłanie surowych ciągów danych na porty TCP/UDP. |
| **Sniffing & Analiza** | `tcpdump`, `Wireshark` / `tshark` | Przechwytywanie, filtrowanie i analizowanie surowego ruchu sieciowego na poziomie pakietów. |
| **Zarządzanie Hostem** | `ip` (`addr`, `route`, `neigh`) | Sprawdzanie i konfiguracja kart sieciowych, tablicy routingu oraz tablicy sąsiadów ARP. |
| **Wydajność** | `iperf3` | Pomiar realnej przepustowości (throughput) i wydajności sieci między dwoma punktami. |

---

## 12. [Wewnętrzna Architektura Sieciowa Linuksa (Wirtualizacja)](./12_linux_architecture.html)

*Jak kernel zastępuje fizyczne routery, przełączniki i kable.*

> 💡 **Uwaga:** Pełne omówienie tego rozdziału wraz z interaktywnymi diagramami i szczegółami technicznymi znajdziesz w osobnym pliku: [12_linux_architecture.html](./12_linux_architecture.html).

*   **12.1. Network Namespaces (netns)**:
    *   Wirtualna izolacja stosu sieciowego w jądrze. Każdy namespace posiada własne interfejsy i tablice routingu.
*   **12.2. Virtual Ethernet Pairs (veth)**:
    *   Wirtualny kabel sieciowy. Zawsze tworzony w parach – dane wstrzyknięte do jednego końca natychmiast pojawiają się w drugim.
*   **12.3. Linux Bridge (docker0, br0)**:
    *   Wirtualny switch warstwy L2 działający w jądrze systemu, łączący końcówki interfejsów `veth`.
*   **12.4. Netfilter, iptables, nftables i Conntrack**:
    *   Stos przetwarzania pakietów w jądrze, realizujący filtrowanie, manipulację pakietami oraz translację adresów NAT.
    *   **Conntrack (Connection Tracking)**: śledzenie stanów połączeń. Ryzyko przepełnienia tablicy conntrack (`nf_conntrack_max`), diagnostyka (`dmesg`, `sysctl`) i remediacja.
*   **12.5. Strojenie Buforów TCP Socket**:
    *   Wydajność systemów o wysokiej przepustowości i opóźnieniach (BDP - Bandwidth-Delay Product).
    *   Strojenie parametrów jądra: `rmem` i `wmem` (bufor zapisu/odczytu) oraz mechanizm autotuningu TCP.
*   **12.6. Interfejsy TUN/TAP**:
    *   Wirtualne interfejsy sieciowe obsługiwane programowo z przestrzeni użytkownika (L3 `TUN` - pakiety IP, L2 `TAP` - ramki Ethernet). Podstawa działania programów typu VPN (np. `OpenVPN`).
*   **12.7. eBPF (Extended Berkeley Packet Filter)**:
    *   Uruchamianie bezpiecznego, skompilowanego kodu bezpośrednio w kontekście jądra (lub karty sieciowej - `XDP`) bez konieczności modyfikacji kodu jądra. Rewolucja w obserwowalności i wydajności sieci.

---

## 13. [Sieci w Świecie Kontenerów (Docker / Kubernetes)](./13_container_networking.html)

*Jak wirtualne klocki Linuksa łączą się w wielkie klastry.*

*   **13.1. Docker Networking**:
    *   Zrozumienie trybów działania sieci kontenerowej: `bridge` (wirtualny switch), `host` (współdzielenie stosu z gospodarzem), `none` (izolacja).
*   **13.2. CNI (Container Network Interface)**:
    *   Standard wtyczek sieciowych dla Kubernetes. Wykorzystanie zaawansowanych technik enkapsulacji (m.in. `VXLAN`) oraz protokołu dynamicznego routingu `BGP` do budowy wydajnych sieci nakładkowych.
*   **13.3. Kube-proxy i Kubernetes Services**:
    *   Jak usługi w klastrze są mapowane i dystrybuowane. Rola translacji adresów w locie za pomocą `iptables` oraz `IPVS`.
    *   *Podatek od iptables:* Problem liniowego przeszukiwania reguł iptables w dużych klastrach i migracja do IPVS / Cilium (eBPF).
*   **13.4. DNS w Kubernetesie (Pułapka ndots:5)**:
    *   Jak domyślna konfiguracja `ndots:5` w `/etc/resolv.conf` powoduje drastyczny narzut na rozwiązywanie domen zewnętrznych (amplifikacja zapytań DNS).
    *   Remediacja i konfiguracja `spec.dnsConfig` na poziomie podów.

> [!IMPORTANT]
> **Dlaczego skalowanie iptables w Kubernetesie to problem?**
> Agent `kube-proxy` domyślnie mapuje Kubernetes Services na tysiące reguł `iptables` na każdym hoście. Ponieważ `iptables` przetwarza reguły liniowo (od góry do dołu), przy bardzo dużych klastrach (np. 10 000+ usług) narzut procesora na dopasowanie każdego pakietu staje się krytyczny. Rozwiązaniem tego problemu jest przejście na technologię `IPVS` lub w pełni oparte na `eBPF` rozwiązanie typu `Cilium`.