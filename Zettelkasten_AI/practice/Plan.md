~~Dokończ projekty:~~
- ~~batch + artykuł jakiś na ten temat~~

Przeczytaj książki pod kątem visy:
- security
- web network performance
- ~~spring security~~

Dokończ projekty:
- book course converter

Dokończ integrację Twoich notatek i ich synchronizację na obsidianie

Binzesiwo

Memorize:
- DDIA
- postgres books
- mongo book
- java jvm, 
- jmm
- architecture the hard parts
- jvm bytecode 
- garbage collection algorithms
- JIT + code cache
- aot vs jit
- sql-antipatterns
- Tiered Compilation
- Core Library Methods and JIT Limits
- CAS (Compare and Swap) and Atomics
- Unsafe and VarHandles
- Small Performance Enhancements in Java 9
- indeksy w postgresie 
- indeksy w mongodb
- wyucz się tych hashjoinów etc. w execution planie
- DDD - agregaty i inne encje z DDD. Rozdział 5 i 6 
- DDD co to bounded context, domeny, subdomeny: chapter 3 ,2 i 1
- DDD Chapter 9: Communication Patterns
- cqrs & even sourcing. DDD Rozdział 7 i 8 
- DDD Chapter 4: Integrating Bounded Contexts
- SAGA: archiecture the hard parts & DDD chapter 9
- hexagonal
- fulltext search w mongo i postgresql
- mongo vector search i LLM
- 
- CDC postgres i mongo
- event driven ze wszystkich książek
- data mesh - tylko teoria
- mongo - aggregation pipelines
- 
- transakcje: DDIA, mastering postgresql, postgres mistakes, mongodb
- DDD: heurystyki, 
- mikroserwisy - wszystkie książki
- DDD: Chapter 13: Domain-Driven Design in the Real World 
- DDD:  Chapter 11: Evolving Design Decisions
- event storming
- Derived Data vs. Distributed Transactions
- schematy w mongo
- schematic vs schemaless
- mongo: WiredTiger, transkacje single-doc i multidoc
- WAL w postgresie i mongo i w ogóle
- 
- distributed trasnactions vs distributed trasnactions to several systems
- Avoid Finalization +  `try-with-resources`

Pomysły na artykuły:
- [ ] map reduce vs dataflow engines
- [ ] 

DDIA:
- [ ] pobaw się tą architekturą dataflow z rozdziałów 12 i 13, stream procesory

Practice sql:
- [ ] pobaw się indeksami w postgresie 
- [ ] pobaw się transakcjami i izolacjami
- [ ] pobaw się indeksami w mongo
- [ ] pobaw się execution planami w postgresie 
- [ ] pobaw się execution planami w mongo
- [ ] pobaw się CDC w mongo i postgresie
- [ ] mongo vector search i LLM

Practice DDD i architektura (hards parts etc..):
- [ ] zbuduj system praktykując DDD - użyj example domain
- [ ] pobaw się kilkoma architekturami 
- [ ]  pobaw się teorią w praktyce z DDD. Użyj JMolecules
- [ ] event driven tak jak według książek
- [ ] mikroserwisy
- [ ] spróbuj zaimplementować sagę
- [ ] spróbuj zaimplementować distributed transaction
- [ ] stwórz elegancki kod domenowy zgodnie z ksiażką DDD

Practice (na podstawie tego można trzaskać artykuły):
- [ ] stwórz klaster z kilkoma apkami w javie 
- [ ] poćwicz replikację - tutaj pewnie można kafkę albo mongo użyć
- [ ] poćwicz sharding - mongo, kafka
- [ ] poćwicz schema migration avro na kafce
- [ ] stwórz duży ruch za pomocą AI na klastrze
- [ ] chaos enginerring
- [ ] skonfiguruj logi garbage collectora + jakieś zdarzenia 
- [ ] muszę stworzyć takie apki, które będą musiały użyć innych GC
- [ ] latency test, throughput test, load test, stress test, endurance test, capacity test, degradation test. Optimizing java: chapter 4.
- [ ] pobaw się autoskalowaniem w stress teście
- [ ] pobaw się terraformem w localstack
- [ ] pobaw się Spring batch 
- [ ] pobaw się JITWatch - zobacz jak np. loop unrolling działa
- [ ] pobaw się visualvm
- [ ] pobaw się jprofiler
- [ ] pobaw się Java Flight Recorder
- [ ] zobacz jaki wpływ Domain Objects mają na pamięć
- [ ] Pobaw się Method Handles zamiast refleksji
- [ ] stwórz taki problem, gdzie sam garbage collector będzie źródłem problemów. Będzie za dużo CPU zużywał. Chapter 8 - optimizng java.
- [ ] zrób allocation rate analysis
- [ ] pobaw się w praktyce **Thread-Local Allocation (TLABs)**
- [ ] Pobaw się narzędziem jHiccup
- [ ] spróbuj różnych garbage collectorów
- [ ] profilowanie apek jvm
- [ ] samplowanie apek
- [ ] pobaw się AOT + knative, czy tam odpal to na lambdach
- [ ] pobaw się aeronem, agroną etc...
- [ ] zobacz, czym się różni samplowanie od profilowania
- [ ] pobaw się narzędziem jclarity - Uses machine learning to diagnose root causes of performance issues automatically
- [ ] pobaw się async profiler
- [ ] Pobaw się Allocation Profiling
- [ ] Zobacz co to TLAB-Driven Sampling
- [ ] pobaw się różnymi narzędziami do multithreadingu w różnych konfiguracjach
- [ ] zrób monitoring klastra w grafanie:
	- [ ] Istio service mesh 
	- [ ] jvm stats
	- [ ] native memory usage w kontenerze wraz użyciem heap memory