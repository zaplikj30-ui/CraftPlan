# Zaměřovač 2 – strom vývoje, pořadí a rozhodovací brány

> **CraftPlan (historicky „Zaměřovač 2“).** Tento dokument je součástí aktuálního produktového zadání CraftPlanu. Pochází z [PR #234 původního Zaměřovače](https://github.com/zaplikj30-ui/Zamerovac-android/pull/234); odkazy na jedničku jsou historické reference, nikoli pokyny ke kopírování starého kódu nebo workflow. Platí [hlavní pravidlo zdroje pravdy](ZAMEROVAC_2_JEDNOTNY_ZAKLAD_A_PRENOS.md).

**21. 11. 2026 · HISTORICKÁ PODOBA STROMU, AKTUALIZOVÁNO 22. 11. 2026.** Rozhodovací základ pro skutečné pořadí M0–M5 je novější [hlavní plán PR #2](https://github.com/zaplikj30-ui/CraftPlan/pull/2), datové základy [PR #3](https://github.com/zaplikj30-ui/CraftPlan/pull/3), uživatelská rozhodnutí [PR #4](https://github.com/zaplikj30-ui/CraftPlan/pull/4) a fotografie [PR #6](https://github.com/zaplikj30-ui/CraftPlan/pull/6). Strom a původní rozdělení níže jsou zachovanou historií, **nikoliv souhlasem se starým pořadím nebo technologií**. M0-A/B/C/D se dokončuje před M1; technologie/renderer/DB nejsou vybrány.
Tento dokument odpovídá na otázky **CO TEĎ → CO PAK → JAK POZNÁME HOTOVO → NA ČEM TO ZÁVISÍ**. Není kopií celé [vize](https://github.com/zaplikj30-ui/Zamerovac-android/blob/8a36e353448ba74a066e16372441604b7e7163c7/VIZE_A_PLAN.md) ani 49 požadavků; ty jsou v [inventuře](ZAMEROVAC_2_INVENTURA_POZADAVKU.md). Vychází z [porovnání skutečného kódu v1 a chyb](ZAMEROVAC_2_POROVNANI_S_V1.md), [ústavy dat M0](ZAMEROVAC_2_USTAVA_DAT.md) a [živého návrhu](ZAMEROVAC_2_ZIVY_NAVRH.md). V1 main při vytvoření podkladů: `8a36e353448ba74a066e16372441604b7e7163c7`.

**PODROBNÉ PRACOVNÍ SEZNAMY:** [Hlavní katalog 49 historických detailů, 12 oblastí a 39 budoucích skupin](ZAMEROVAC_2_KATALOG_FUNKCI.md) a **[samostatný seznam nedotažených nápadů s rozhodovacími otázkami](ZAMEROVAC_2_NEDORESENE_NAPADY.md)**. Strom níže je pouze přehled etap; není úplným seznamem funkcí. Čísla neznamenají počet hotových funkcí a nápad neznamená automatické GO k implementaci.

**PŘENOS CELÉHO ZADÁNÍ JEDNIČKY:** [Registr všech dohledaných návazností, včetně nedokončených nápadů](ZAMEROVAC_2_REGISTR_NAVAZNOSTI_V1.md) je povinný podklad této roadmapy. **Pořadí etap není seznamem toho, co chceme zachovat – nechceme nic z předchozích uživatelských diskusí vyhodit jen proto, že se to netvoří hned.** Zaměřovač 2 se bude rozrůstat po malých otestovaných celcích; výslovně vyloučené referenční funkce zůstávají vyloučené, dokud se rozhodnutí nezmění.

## 1. Jednoduchý strom – jak se aplikace bude stavět

```text
ZAMĚŘOVAČ 2 – samostatná nová aplikace, Android první
│
├─ M0  Základy a bezpečnost dat                    ← TEĎ
│  ├─ v1 vs. v2: co existuje / funguje / nefunguje / je nápad
│  ├─ zakázka – fotografie – kóta – číselná hodnota – jednotka – ID
│  ├─ souřadnice obrázku; otevřená cesta pro jiné typy podkladů
│  ├─ srovnání úložišť (SQLite/Room vs. jednoduchá souborová varianta)
│  └─ cílené testy, nezávislé posouzení datového návrhu
│
└─ M1  První FUNKČNÍ offline celek (technologie dosud NEVYBRÁNA)
   ├─ otevřít fotografii (originál se nemění)
   ├─ vložit, uchopit a přesunout přesnou kótu / prst a pero
   ├─ Undo / Redo, přiblížení a posun
   ├─ bezpečně uložit → ukončit → znovu otevřít
   ├─ JEDNODUCHÁ DIAGNOSTIKA OD ZAČÁTKU
   │  └─ vlaječka → zap/vyp → historie → vybrat/sdílet/mazat hromadně
   └─ stabilita a výkon 100/1000 opakovaných gest, selhání ukládání
       │
       ├─ M2  Zakázky, složky, fotografie, galerie, koš
       │  └─ čitelné kóty, miniatury, hledání a rychlost
       │
       ├─ M3  Další geometrie, symboly, poznámky a přílohy
       │  └─ kruh, obdélník, lichoběžník, úhel, řetězce, audio atd.
       │
       ├─ M4  Export/import a přenos ze staré jedničky
       │  └─ nejprve read-only import kopie, přesné hodnoty a média
       │
       └─ M5  Laser Bosch, poté další ověřené přístroje
          └─ obecný vstup hodnoty; konkrétní adaptéry postupně

POZDĚJI – celý dlouhodobý cíl; samostatně ověřované funkční větve
├─ B1  Rozšířené/cloudové zálohy; MINIMÁLNÍ PŘENOSNÁ ZÁLOHA JE JIŽ M2
├─ B2  Synchronizace telefon/tablet, konflikt, offline fronta
├─ B3  Panorama/360°, půdorysy a případné 2D/3D
├─ B4  Časovač práce zákazníka, hodinovky, výkazy, faktury
├─ B5  KONFIGURÁTOR → KUSOVNÍK → NÁŘEZÁK → výroba/kalkulace
├─ B6  Instalační plány/šablony + případný zákaznický portál
├─ B7  Monetizace a volitelné moduly/propojení aplikací
└─ B8  Windows/iOS/iPadOS + další jazyky a jednotky
```

**Schválené číslování nové aplikace (21. 11. 2026):** první budoucí testovací prototyp `0.1.0-test` → opravy `0.1.1-test` → větší etapa `0.2.0-test`; stabilní `1.0.0` až po samostatném rozhodnutí. **V2 má samostatné package ID, podpis/datový prostor a vlastní rostoucí `versionCode`**; není pokračováním čísel `1.4.x` z v1. Jde o plán, žádná APK není vytvořená. Podrobně [živý návrh §17](ZAMEROVAC_2_ZIVY_NAVRH.md).

**Závislosti:** B2 až po prokázaném bezpečném lokálním ukládání a řešení identity/revizí; záloha B1 je **jiná funkce** než živá sync B2. B3 potřebuje oddělenou geometrii a souřadnice; B4 vazbu na zákazníka/zakázku. **B5 je dlouhodobá cesta zaměření → návrh skříněk → kusovník → nářez → výroba/montáž.** Konfigurátor–Nářezák zatím zůstává **samostatnou aplikací**, neslučovat repozitáře ani sdílené živé databáze. Vstupní míry a konstrukční dílce/hranování/dekor/revize se nesmí tiše změnit bez uživatelova potvrzení. **B6: instalační plány jsou vysoko prioritním požadavkem [#72](https://github.com/zaplikj30-ui/Zamerovac-android/issues/72), který zařadit brzy po základní stabilizaci, a portál [#73](https://github.com/zaplikj30-ui/Zamerovac-android/issues/73) je zatím jen otevřený nápad.** B7 nesmí ohrozit data/licence. B8 vyžaduje samostatné platformní testy. Větve B1–B8 **nemají dnes pevné pořadí ani termín**; priority se rozhodnou po měřeních a ověření závislostí.

## 2. Výstup každé etapy a zkouška HOTOVO

| Etapa | Co přesně vznikne | Kdy smíme říct „HOTOVO“ | Stav 21. 11. 2026 |
|---|---|---|---|
| **M0** | Malý čitelný model dat, hranice modulů, kandidát SQLite/Room, kontrakt přesných hodnot/jednotek, zachování originálů, testy selhání | Odsouhlasená pravidla dat; pro sporné body krátký experiment; design/security review, žádné nevyřešené riziko ztráty dat | **ROZPRACOVÁNO:** inventura a porovnání jsou napsané, konkrétní DB a recovery ještě neověřeny |
| **M1** | Jeden použitelný offline tok: foto → kóta → plynulé úchopy → Undo/Redo → uložení → otevření, **diagnostická vlaječka s volbou více záznamů** | Věrné testy geometrie a hodnot, originální média, negativní ukládání, dlouhé tažení, srovnání výkonu; relevantní skutečný Android test odlišen od CI | **NOT STARTED** |
| **M2** | Zakázky, strom složek, galerie, náhledy, koš, **minimální přenosná záloha s prokázanou obnovou na oddělené instalaci** | Rychlost 10/100/1000 položek; integrita ID/médií; pád/plný disk; bezpečná obnova před nenahraditelnými zakázkami | **NOT STARTED** |
| **M3** | Další editorové nástroje, **časný F35 instalační plán s minimálním PDF a sdílením**, dále F04/F05 půdorysy a nárysy podle závislostí; audio/video podle schváleného rozsahu | Bezpečné návaznosti dokumentů a zákazníkovi použitelný výstup F35, jeho stav/revize; úplný plán viz PR #2 | **NOT STARTED** |
| **M4** | Zákaznické PDF/exporty, plný editovatelný přenos ZIP a oddělený import kopie v1; **nejmenší PDF pro F35 už před/současně s M3b** | Export→import→reopen, obsah výběru/soukromí, verze a chybné ZIP; **nezaměňovat s minimální záchrannou zálohou M2** | **NOT STARTED** |
| **M5** | Jeden fyzicky otestovaný Bosch protokol přes jednoduchý adaptér | Reálný přístroj + příslušný telefon; ruční hodnota i offline scénář; další značky jen jednotlivě | **NOT STARTED** |
| **B1–B8** | Jednotlivé pozdější rozvojové větve | Vlastní návrh, cena/riziko, samostatné GO, adekvátní testy a důkazy | **NÁPAD/PLÁN, BEZ IMPLEMENTACE** |

## 3. Jak bude vypadat strom uvnitř kódu (návrh hranic, ne předem postavená infrastruktura)

```text
Android aplikace (jazyk a renderer vybrat teprve v M0-D)
├─ Uživatelské obrazovky
│  ├─ zakázky + galerie                 (později M2)
│  ├─ editor foto/kóty                 (M1)
│  └─ malý panel diagnostiky           (M1)
├─ Pravidla a data (bez Android UI)
│  ├─ identita zakázek a anotací / vazby
│  ├─ přesná čísla, jednotky, souřadnice, geometrie
│  └─ editační příkazy + Undo/Redo
├─ Místní ukládání
│  ├─ zkouška SQLite / běžné knihovny Room
│  └─ originální média odděleně + ověřená obnova
└─ Jednoduché adaptéry (až podle potřeb etapy)
   ├─ výběr fotografie/systémové sdílení
   ├─ Bluetooth měřicí přístroj
   └─ budoucí import/export a síť (ne nyní)
```

**Nedělat:** platformu pluginů, velkou sadu abstrakcí, 3D renderer, vlastní databázi/sync engine, licenční server ani tři paralelní prototypy „pro budoucnost“. Připravenost na budoucnost znamená **stabilní ID, čitelná data a nepomíchané odpovědnosti**, nikoli prázdnou implementaci všech možných modulů.

## 4. Pravidlo práce: jeden malý funkční balík → ověření → další

1. **Před zadáním:** koordinátor přečte právě potřebné části [inventury](ZAMEROVAC_2_INVENTURA_POZADAVKU.md), [porovnání s v1](ZAMEROVAC_2_POROVNANI_S_V1.md), [datové ústavy](ZAMEROVAC_2_USTAVA_DAT.md), aktuální GitHub HEAD/base, [AGENTS](../AGENTS.md) a [#42](https://github.com/zaplikj30-ui/Zamerovac-android/issues/42). Dohodne jedno konkrétní očekávané chování a bezpečné hranice.
2. **Jedna práce = izolovaná větev/draft PR:** popsat vstup/výstup, testy, starou referenci a rizika, zvolit vhodnou AI/prostředí. V2 se nezapisuje přímo do stávající produkční v1; **samostatný repozitář CraftPlan již existuje**, nový Android projekt a **jiné package ID/datový prostor** až po příslušném implementačním GO.
3. **Programátor:** implementace, vlastní pozitivní/negativní/hranové testy, opravy nálezů, retesty a **důkladné vlastní SOURCE/SECURITY review celého kumulativního diffu na finálním SHA**. Záznam v PR: SHA/base, PASS/FAIL/SKIP/NOT RUN/BLOCKED, skutečné důkazy, rizika. Šablona: [ZADAVANI_A_KONTROLA.md](ZADAVANI_A_KONTROLA.md).
4. **Jiný nezávislý kontrolor:** nastupuje teprve po dokončené autorské bráně; výsledek APPROVE SOURCE / CHANGES REQUIRED / STOP. Není fyzický test. Známý opravitelný FAIL znamená vrátit autorovi, ne sloučit.
5. **Majitel:** schvaluje zásadní technologii, architekturu, merge, APK a výdaje. **Build ≠ vydaná APK ≠ test na Androidu.** Žádné HOME; cloudový test jen podle platného RUN GO/rozpočtu.
6. **Zpětná vazba:** chyba z telefonu → konkrétní reprodukce → anonymní diagnostický log → věrný regresní RED, pokud proveditelný → oprava → PASS + review. Nedělat nepřetržité audity bez nové změny.

## 5. Nejbližší tři kroky a rozhodnutí GO/STOP

| Pořadí | Úkol | Výsledek | Aktuální rozhodnutí |
|---|---|---|---|
| **1 – TEĎ** | Dokončit **M0**: krátký kontrakt základních objektů, souřadnic, přesné kóty, uložení a obnovení; ověřit vůči skutečným funkcím a chybám v1 | Jeden malý testovatelný návrh, vysvětlené riziko a co neprogramovat | **GO pouze pro dokumentační návrh a jeho kontrolu** |
| **2 – PAK** | Nezávislé **DESIGN/SECURITY review** základů, zejména SQLite/Room/obnova/původní bajty a hranice editoru; vyhodnotit i jiné jednoduché možnosti | APPROVE DESIGN / CHANGES REQUIRED / STOP | **ČEKÁ**, bez tohoto nedělat rozsáhlou implementaci |
| **3 – PO GO** | **M1 jediný malý věrný prototyp** po schváleném výběru technologie/rendereru M0-D + od začátku jednoduchá diagnostika; nic dalšího | Měřitelná funkce foto–kóta–uložení/obnova, regression, výkon, chyby a skutečný Android | **ZATÍM NESPUŠTĚNO**, kód/CI/APK samostatné GO |

**Nejvyšší riziko:** přepsat nebo ztratit potvrzenou práci, zhoršit plynulost kót, poškodit originální média nebo zaměnit CI za fyzický test. Každá etapa se musí umět zastavit nebo vrátit; jednička zůstává oddělená.

**Po dokončení etapy v PR zapsat:** co je lepší/horší, hodnotitelné testy, známé nedostatky, **TEĎ / POZDĚJI / NEMĚNIT**, odůvodněné hodnocení 1–10 a odkaz na další úkol. „HOTOVO“ pouze pro doložený rozsah, ne pro celou aplikaci.

**Stav tohoto souboru:** plán/dokumentace, žádná produkční změna a žádná automatická implementace.
