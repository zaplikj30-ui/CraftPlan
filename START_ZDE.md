# START ZDE – CraftPlan

**CraftPlan** je nová samostatná aplikace, historicky nazývaná „Zaměřovač 2“. Tento repozitář je **jediným budoucím místem pro platná rozhodnutí CraftPlanu**. Původní [Zaměřovač 1](https://github.com/zaplikj30-ui/Zamerovac-android) zůstává samostatný; jeho dokumentace, kód, PR a SHA jsou jen historické reference. V současnosti jde o **návrh**, nikoli funkční Android aplikaci.

## Čti v tomto pořadí

1. [AGENTS.md](AGENTS.md) – závazná pravidla bezpečnosti, testů, autorské a nezávislé kontroly, schvalování, HOME a rozpočtu.
2. [CraftPlan – jednotný základ a precedence rozhodnutí](docs/ZAMEROVAC_2_JEDNOTNY_ZAKLAD_A_PRENOS.md) – co je v2 a co historie, co platí a jak řešit rozpory.
3. [Živý návrh a poslední funkční rozhodnutí](docs/ZAMEROVAC_2_ZIVY_NAVRH.md) – dlouhodobé záměry a upřesnění vlastníka.
4. [Katalog 49 historických detailů a dalších funkcí](docs/ZAMEROVAC_2_KATALOG_FUNKCI.md) – kontrolní seznam; **není to 49 implementovaných funkcí**.
5. [F01–F05: panorama, sféra, lasery, půdorys, práce se stěnou](docs/ZAMEROVAC_2_F01_F05_ZADANI.md) – potvrzené chování, ne hotový kód.
6. [F01–F39: budoucí a otevřené nápady](docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md) – zachovat nápady, automaticky neprogramovat.
7. [Ústava dat M0](docs/ZAMEROVAC_2_USTAVA_DAT.md) a [strom vývoje M0/M1/...](docs/ZAMEROVAC_2_STROM_VYVOJE.md) – nejprve bezpečný offline prototyp.
8. [Inventura požadavků](docs/ZAMEROVAC_2_INVENTURA_POZADAVKU.md), [porovnání s v1 a diagnostika M1](docs/ZAMEROVAC_2_POROVNANI_S_V1.md), [registr historických návazností](docs/ZAMEROVAC_2_REGISTR_NAVAZNOSTI_V1.md), [podpora laserů F03](docs/ZAMEROVAC_2_LASERY_F03_NAVRH.md) – ověřovací podklady.
9. [Šablona práce a kontrol](docs/ZADAVANI_A_KONTROLA.md) – při zadávání programátorovi.

## Jaký stav má projekt

- **Zvolen nový název CraftPlan a samostatný PUBLIC repozitář.** Původní Zaměřovač 1 se neklonuje a neruší.
- **První etapou je M0** (přesný a bezpečný datový návrh), pak po samostatném GO **M1** (fotografie – kóta – gesta – Undo/Redo – uložení – obnova – jednoduchá diagnostika), čistě offline.
- **Kotlin + Jetpack Compose** je schválený směr prvního prototypu, **SQLite/Room kandidát k ověření**. Ani technologie, databáze či fyzické funkce nejsou dokončené.
- **Nápad ≠ souhlas s implementací ≠ kód ≠ PASS na Androidu.** Každé nové rozhodnutí zapisovat sem s původem a datem, starší v1 dokumenty nikdy nepřebírat jako příkazy.
- **Žádné Actions, merge, APK ani nákupy** bez samostatného schválení. HOME runner je RETIRED.

**Původ dokumentace:** [Zaměřovač 1 – draft PR #234](https://github.com/zaplikj30-ui/Zamerovac-android/pull/234), zdrojový HEAD `8c0f03e68788625e098f31756c069824503fac41`. Přenesené starší názvy souborů `ZAMEROVAC_2_*` zůstaly kvůli zpětné dohledatelnosti; aktuální název produktu je vždy **CraftPlan**.
