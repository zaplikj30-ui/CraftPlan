# CraftPlan – platný základ projektu a zdroje požadavků

**CraftPlan je schválený název nové aplikace i samostatného veřejného repozitáře [zaplikj30-ui/CraftPlan](https://github.com/zaplikj30-ui/CraftPlan).** Původní „Zaměřovač 2“ je pouze historický pracovní název v převzatých dokumentech. Zaměřovač 1 zůstává oddělený. Dostupnost obchodního názvu a ochranných známek nebyla právně ověřena.

**Viditelnost schválena vlastníkem: PUBLIC (21. 11. 2026).** Účel: používat standardní GitHub-hosted Actions testy, které GitHub aktuálně poskytuje veřejným repozitářům zdarma ([oficiální podmínky](https://docs.github.com/en/actions/concepts/billing-and-usage)). Neplatí automaticky pro všechny placené produkty, larger runnery a externí služby. Nové workflow nespouštět bez odpovídajícího GO, držet projektový strop 12 USD/měsíc a před během ověřit podmínky. Na GitHub neukládat žádná tajemství, tokeny, reálná zákaznická data ani soukromá média; veřejný je i obsah historie repozitáře. Zvolit výchozí GITHUB_TOKEN read-only a vyžadovat schválení běhů z nedůvěryhodných forků; HOME runner nadále RETIRED.

**21. 11. 2026 · Produktové sjednocení návrhu.** Tento dokument je **hlavní krátký rozcestník**. Úplné funkční požadavky a jejich důkazy zůstávají v odkazovaných dokumentech. Nejde o schválení implementace, výběr definitivní databáze, merge, APK ani Actions.

## 1. Jasná identita projektů a zdroj pravdy

- **Zaměřovač 1**: stávající aplikace v `zaplikj30-ui/Zamerovac-android`. Její produkční kód, historie, chyby, otevřené PR a data se do v2 automaticky nepřenášejí.
- **CraftPlan (dříve Zaměřovač 2)**: samostatný nový projekt v **tomto** repozitáři, výchozí platforma Android a budoucí možnost Windows/iOS/iPadOS. Kanonické podklady byly převzaty z [dokumentačního PR #234](https://github.com/zaplikj30-ui/Zamerovac-android/pull/234), nikoli ze staré hlavní větve v1. Do přijetí aktuálního dokumentačního PR v CraftPlanu jsou na jeho izolované větvi, ne v `main`.
- V novém repozitáři budou mít přednost jeho **AGENTS.md (pravidla práce), START_ZDE.md (rozcestník), tento souhrn a příslušné funkční zadání**. GitHub issue/PR se zdrojem a **novějším výslovným rozhodnutím vlastníka** zpřesňuje starší dokument. Pokud nastane rozpor, **nepřebírat automaticky starší formulaci**: označit ROZPOR/NEOVĚŘENO, dohledat původ a před změnou návrh sjednotit.
- Dokumenty ze Zaměřovače 1, zejména [původní obecný návrh z 6. 11.](https://github.com/zaplikj30-ui/Zamerovac-android/blob/8c0f03e68788625e098f31756c069824503fac41/NAVRH_NOVEHO_ZAMEROVACE_OD_NULY.md), staré checkpointy, stará SHA a stará implementační rozhodnutí jsou **historické reference**, nikoli automatické požadavky dvojky. **Nedědit** z jedničky její CI/workflow, HOME, testovací statusy, verze ani staré GO.

## 2. Potvrzené společné zásady pro v2

1. **Nová aplikace na novém technickém základu**, ne přejmenovaná či klonovaná jednička. Zachovat důvěryhodné zkušenosti, chování a testy z v1; starý kód přebírat jen po cíleném posouzení.
2. **První malý celek čistě lokálně a offline**: fotografie → nová editovatelná kóta → úchopy/gesta → Undo/Redo → bezpečně uložit → zavřít → obnovit. Žádné účty, cloud, synchronizační fronta ani server v první etapě.
3. **Technologie první zkoušky: Kotlin + Jetpack Compose.** Jednoduché datové a geometrické jádro oddělené od Android UI, grafiky a hardware. **SQLite/Room je preference k ověření**, nikoliv implementačně potvrzená jediná volba; porovnat její konkrétní funkci, výkon a zotavení. C++/Qt pouze při prokázané výrazné výhodě.
4. **Neměnné datové zásady**: přesná hodnota + jednotka + identita měření oddělená od čáry/pixelů; původní bajty médií zachované; stabilní ID a vazby; editovatelné anotace; žádné tiché přepsání posledního potvrzeného stavu; ochrana při pádu, přerušení a plném disku. Undo/Redo není systém verzování či cloudových záloh.
5. **Diagnostika od první funkční M1**, ale malá, volitelná, pasivní a bez soukromých dat. Měřit reálné časy operací a zátěž, ověřit režii zapnuto/vypnuto; případný automatický tester odděleně v TEST sestavení až později.
6. **Vlastní testovací identita/package ID, data, podpis a verzování**: první plánovaná testovací verze `0.1.0-test`, dále `0.1.1-test`, `0.2.0-test`; `1.0.0` až po samostatném schválení. Nová APK nikdy nesmí být aktualizací v1.
7. **Budoucí samostatné oblasti**: bezpečné zálohy a sync, další měřidla, panorama, půdorys a nárysy, instalační plány, čas, faktury, zákaznické funkce a možné moduly. Neimplementovat vše hned; Konfigurátor–Nářezák i Převaděč spojitosti zůstávají samostatné do případného nového rozhodnutí.
8. **Funkce a stav se nezaměňují**: nápad ≠ schválené chování ≠ implementace ≠ automatický PASS ≠ fyzický Android PASS. Žádný nový cloudový běh, merge, APK ani náklady bez odpovídajícího GO.

## 3. Kde je úplné zadání

| Účel | Jeden dokument pro detail |
| --- | --- |
| Rozhodnutí vlastníka a jejich časový vývoj | [Živý návrh Zaměřovače 2](ZAMEROVAC_2_ZIVY_NAVRH.md) |
| Všechny základní nástroje včetně 49 historicky ověřovaných požadavků a 12 oblastí | [Katalog funkcí](ZAMEROVAC_2_KATALOG_FUNKCI.md) |
| 39 skupin budoucích/otevřených nápadů a jejich jednotlivé stavy | [Nedotažené nápady](ZAMEROVAC_2_NEDORESENE_NAPADY.md) |
| F01 panorama, F02 sféra, F03 lasery, F04 půdorys a F05 nárys/fotky stěny | [Kompletní zadání F01–F05](ZAMEROVAC_2_F01_F05_ZADANI.md) |
| Hlasové diktování / volitelná AI / „Okótuj místnost“ | [Živý návrh §19–21](ZAMEROVAC_2_ZIVY_NAVRH.md#19-návrat-úplného-nápadu-ai-kótování-a-hlasově-ovládaného-asistenta-21-11-2026) + [F10/F13 v registru](ZAMEROVAC_2_NEDORESENE_NAPADY.md) |
| Bezpečné ukládání a co smí/nemá zmizet při pádu | [Ústava dat M0](ZAMEROVAC_2_USTAVA_DAT.md) |
| Etapy M0, M1, M2… a důkazy HOTOVO | [Strom vývoje](ZAMEROVAC_2_STROM_VYVOJE.md) |
| Poučení z chyb v1 a diagnostika M1 | [Porovnání s v1](ZAMEROVAC_2_POROVNANI_S_V1.md) |
| Dohledatelnost a historické vazby, nikoli povely | [Registr návazností v1](ZAMEROVAC_2_REGISTR_NAVAZNOSTI_V1.md), [Inventura](ZAMEROVAC_2_INVENTURA_POZADAVKU.md) |

**Platnost:** poslední explicitní rozhodnutí vlastníka je nad starým návrhem. Podrobné funkční zadání je nad stručnou rekapitulací, pokud nejsou ve skutečném rozporu. V rozporu se nic nepřepisuje potichu; návrh se opraví ve vlastním v2 repozitáři s odkazem na rozhodnutí.

## 4. Oddělení a původ přenesených podkladů

- Základní dokumenty byly zkopírovány ze **zafixovaného HEAD původního dokumentačního PR #234**: `8c0f03e68788625e098f31756c069824503fac41`. Zůstává zachována dohledatelnost, nikoli závaznost starého kódu či workflow.
- **Odkazy na soubory jedničky jsou historické, připnuté k původnímu commitu**; vnitřní odkazy směřují do tohoto projektu. Zdroje rozhodnutí v PR/issues jsou stále citovatelné, ale nezakládají aktuální úkoly.
- Vlastní `AGENTS.md` a `START_ZDE.md` definují nynější práci. Nejsou převzatá stará workflow ani původní Android kód, APK, tajemství, média, zákaznická data nebo označení PASS jedničky.
- Každé budoucí doplnění musí zaznamenat jeho stav a zdroj zde; případný rozpor se starší verzí řešit explicitně, bez tichého přepsání.
- Po dokončení autorovy kontroly na konkrétním SHA následuje jiný nezávislý kontrolor; **bez výslovného schválení se neslučuje PR**. Dokumentační import není povolení k programování ani spuštění Actions.

## 5. Neuzavřené věci – nepředstírat rozhodnutí

- Konkrétní databázové schéma, crash recovery a měřitelné limity výkonu; renderer a přesná gesta se ověří prototypem.
- Přesný vzhled uživatelských obrazovek, většina budoucích technologií a fyzická podpora laserů.
- Výchozí varianta dávkového diktování A/B; kategorie rozpoznávané pro „Okótuj místnost“; AI lokálně/API, cena a ochrana obrazových podkladů.
- Mezinárodní QR platby, konkrétní způsob zámku, budoucí synchronizační protokol a případné společné moduly.

**TEĎ:** ověřit přenesené dokumenty a jejich vazby v izolovaném PR CraftPlanu, dokončit autorskou a nezávislou kontrolu, pak rozhodnout o přijetí dokumentace. **POZDĚJI:** datový návrh M0 a odděleně schválená malá M1 implementace. **NEMĚNIT:** data ani kód v1, HOME runner, schválené kóty/ergonomii, žádné Actions/merge/APK bez GO.
