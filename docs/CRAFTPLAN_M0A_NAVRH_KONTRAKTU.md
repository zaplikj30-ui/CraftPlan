# CraftPlan – M0-A: návrh kontraktů dat, geometrie a ochrany práce

> **STAV: PRACOVNÍ NÁVRH / K NEZÁVISLÉMU DESIGN/SECURITY REVIEW.** Není to schválené implementační zadání ani HOTOVO M0-A.  
> **Datum:** 22. 11. 2026 · **Rámec:** vlastníkem schválený [hlavní plán stavby M0–M5](https://github.com/zaplikj30-ui/CraftPlan/blob/68646525fabadc6acd51d57e21184026a95cf7c9/docs/CRAFTPLAN_HLAVNI_PLAN_STAVBY.md) a nezávislé APPROVE DESIGN [PR #2](https://github.com/zaplikj30-ui/CraftPlan/pull/2).  
> **Povoleno:** jen příprava dokumentace M0-A. **Nepovoleno:** kód, build, Actions, HOME, APK, merge, výdaje, úprava původního Zaměřovače 1.  
> **Technologie/renderer/databáze/čísla:** **NEVYBRÁNO**. Starší preference Kotlin+Compose v [AGENTS](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/AGENTS.md)/START_ZDE jsou překonané novějším rozhodnutím vlastníka.

## 1. Úplné zadání, ne pouze 49 bodů

Pro návrh M0 se povinně použije **[úplná kontrolní matice](CRAFTPLAN_M0A_MATICE_NAVAZNOSTI.md)**: **12 J-oblastí, všech 39 F01–F39, všech 49 historických dílčích kontrol a 28 historických uživatelských testů**, dále [živý návrh](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_ZIVY_NAVRH.md), [registr dalších návazností](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_REGISTR_NAVAZNOSTI_V1.md) a původní detaily. **Matice zachovává odkazy; její krátký název nenahrazuje úplné podrobné funkční zadání.** Budoucí funkce nemusejí vzniknout hned, ale jejich **nároky na identity, souřadnice, měření, média, vazby a obnovu** nesmí budoucí vývoj nutit bourat základy.

**Výslovně posoudit:** F01/F02 panoramata a sféry, F03 lasery, F04/F05 půdorys a nárys, F09 výměnu podkladové fotografie, F11 odvozené kóty, F35 instalační plány, F36 oprávnění zákazníků a F37 sdílení. F07/F31/F32 jsou odložené, F23/F24/F25 patří Konfigurátoru–Nářezáku; nic z toho nepřebírat jako implementační GO.

## 2. Rozlišení stavů tvrzení

**POTVRZENÝ FUNKČNÍ CÍL:** požadované chování z primárního zadání, ne vytvořená aplikace.  
**KANDIDÁT:** předložená technická možnost, dosud neodsouhlasená.  
**OTEVŘENO:** chybí konkrétní rozhodnutí nebo důkaz.  
**TEST PŘIPRAVIT:** návrhový akceptační scénář, **NOT RUN**, nikoli PASS.

## 3. Logický datový slovník (není to schéma DB)

| Objekt | Identita a pravidla | První potřeba |
|---|---|---|
| **Rychlý podklad** | Pracuje offline bez povinného zákazníka; pozdější přesun do zakázky nemění ID ani kóty | M1 |
| **Zákazník/zakázka/složka** | Samostatné stabilní ID, hierarchie, přesuny, koš, budoucí oprávnění | M2 |
| **Dokument** | ID, typ (FOTO, PANORAMA, SFÉRA, PŮDORYS, NÁRYS, INSTALAČNÍ PLÁN), verze formátu a vazby | FOTO M1 |
| **Médium** | ID, nezměnitelné originální bajty, rozměry, EXIF, kontrola úplnosti; náhled je odvozený | M1 |
| **Anotace/grafický objekt** | ID, typ, geometrie, styl, dokument a odkazy; kóta není pouhá popsaná pixelová čára | M1 |
| **Měření** | ID, **přesná** hodnota + jednotka + veličina + zdroj + stav ověření; možnost uchovat původní zápis | M1 |
| **Událost měřidla** | Původ, čas/pořadí, jednotka, validace, identita cílové kóty pro bezpečné přiřazení | F03/M5 |
| **Kotva/reference** | Vazba na ID média, objektu, dokumentu, stěny, rohu či úseku; nejasnost se nesmí tiše vyřešit špatně | M0+ |
| **Stěna/roh/úsek/nárys** | Identita a topologie půdorysu; F05 je vnořená část F04, ne samostatný produkt | M3c |
| **Instalační dokument/revize PDF** | Editovatelný pracovní stav a samostatná nezměnitelná identita vydaného výstupu | M3b/M3d |
| **Potvrzená datová revize** | Bezpečné obnovení posledního potvrzeného stavu; nejde o požadavek na plnou historii zakázek F32 | M1 |

**KANDIDÁT:** globálně jednoznačné ID při vytvoření. Nikdy ID neodvozovat ze jména souboru, pořadí v galerii, pixelové polohy nebo názvu zákazníka. **Přesun ≠ kopie**: přesun zachovává ID; nezávislá kopie dostává nové ID s dohledatelným původem. Při importu/obnově odlišit návrat původního objektu od nové kopie a řešit kolize; zakázat tiché nahrazení cizího objektu stejným ID.

**Příklady vazeb:** `zakázka → dokument FOTO → původní médium + anotace kóty → měření`; `půdorys → stěna → nárys + dveře/okna/fotopoznámky`. Rychlý podklad může existovat i mimo zakázku.

## 4. Přesná čísla, jednotky a měřicí kontext

**POTVRZENÉ:** číselný údaj a původ nejsou odvozené z pixelů, délky kreslené úsečky ani zaokrouhleného popisku. Pro každé měření uchovat veličinu (délka/plocha/úhel…), jednotku, přesnou hodnotu, původ `RUČNÍ/LASER/VÝPOČET/IMPORT`, ID a stav ověření. Bluetooth samo nezaručuje, že jednotka a údaj jsou správné.

**KANDIDÁTI datové reprezentace:** kanonický přesný desetinný text, případně přesný koeficient/exponent nebo racionální zápis se zlomky; rozhodnout podle požadavků, platformy a testů. **Samotné celočíselné mikrometry nejsou obecně přesné:** `1/16 in = 1,5875 mm = 1 587,5 µm`. `Float/Double` může pomoci s obrazem, ne jako **jediný zdroj pravdy** rozměru; nepřipustit přetečení plochy. Vybraný přenosový formát nesmí vyžadovat pouze JVM.

**Návrh stavů (OTEVŘENO pro názvy/přechody):** `NEZMĚŘENO`, `ZADÁNO`, `OVĚŘENO`, `NEJASNÁ JEDNOTKA`, `NEPŘIŘAZENO`. Česká čárka a anglická tečka se zpracují pouze podle jednoznačně známého locale; **vnitřní formát dat na locale nespoléhá**. Převody a výpočty mají definovaná pravidla přesnosti, hranic a vědomého zaokrouhlování; zobrazení v jiné jednotce nikdy potichu nemění uloženou hodnotu.

- Posunutí kóty o 20 pixelů mění její geometrické kotvy, **ne naměřenou míru**.
- Součet F11 dává novou **odvozenou** hodnotu s odkazy na zdrojová měření, ne nové fyzické měření.
- Neslučitelné veličiny (např. délka + úhel), neznámá jednotka, `NaN`, nekonečno a mimo-rozsahové hodnoty se odmítnou.
- Opožděná hodnota laseru se nesmí přiřadit jiné právě vybrané kótě; příjem měření a vzdálené spuštění měřidla jsou **oddělené schopnosti**.
- Při změně topologie F04 se nesmí z měřené délky vytvořit dva odhady délek z kresby.

**TEST PŘIPRAVIT:** `850,5 mm` po uložení/reopen; přesný převod `1/16 in ↔ 1,5875 mm`; velké plochy a zlomky; několikeré opakované zobrazení v jiných jednotkách bez driftu; odmítnutí neznámé jednotky z laseru.

## 5. Trvalé souřadnice pro různé podklady

**Společné:** identita, měření, validované editační příkazy, ukládání, odkazy, Undo/Redo. **Ne společné trvalé pixely obrazovky.**

| Podklad | Co trvale uložit / návrh pravidla | Co není pravda modelu |
|---|---|---|
| **FOTO** | Kotvy ve vlastním obrazovém prostoru s explicitní EXIF orientací/mirroring transformací, odkazem na originál; **KANDIDÁT** normalizované 2D souřadnice. Konvenci os/rotací teprve uzavřít | Zoom, velikost displeje, poloha lupy, přechod na tablet |
| **F01 vodorovné panorama** | Kotvy v panoramatickém směrovém prostoru (kandidát azimut + další úhlová/polohová složka), **šev 0°/360°**, původní snímky; přesná konvence otevřená | Posunutí/„roztočení“ filmového pásu a aktuální výřez |
| **F02 plná sféra** | Referenční směr, dva úhly či ekvivalent, póly, krytí a orientace sféry; 360°×180° fotografie není prostorový model kuchyně | Pohled kamery a zoom |
| **F04 půdorys** | Vrcholy, úseky, stabilní ID stěn, topologie a **skutečné měřítko jen při doloženém měření**. U neokótované skici přiznat neznámou škálu | Magnetická vodítka, zoom, pan |
| **F05 nárys stěny** | Lokální prostor pohledu na stěnu + explicitní identita stěny, polohy oken/dveří a detailních médií; při dělení stěny zachovat vazby či označit k ručnímu rozhodnutí | Výběr a poloha UI panelu |
| **F35 instalace** | Editovatelný plán se značkami, měřenými hodnotami, ID zakázky, stavem a revizí; zvlášť vydaný PDF otisk | Přiblížení a rozvržení PDF |

**F09 výměna fotografie:** anotace mají zachovatelné kotvy a oddělenou transformaci nového podkladu. Srovnat novou fotografii s kótami, aniž se mění přesné hodnoty; po „Hotovo“ se vrací běžné samostatné editace. **F01/F02 se musí později testovat na švu, nikoli jen na jedné ploché fotografii.**

## 6. Ukládání a obnova – návrhová bezpečnostní smlouva

### 6.1 Pět rozlišitelných stavů

1. **Rozpracovaný tah** – může při pádu zmizet; není potvrzené uložení.
2. **Dokončený příkaz** – validovaná změna modelu; jeden dokončený tah = jeden logický Undo krok, nikoli povinné vytvoření plného archivu.
3. **Zápis probíhá** – aplikace stále nesmí hlásit `ULOŽENO`.
4. **Potvrzený zápis** – teprve po ověření, že datový stav i potřebná originální média tvoří konzistentní znovuotevřitelný celek; přesně popsat dosaženou trvalost i při přerušení/výpadku napájení.
5. **Chyba/obnova** – přiznat problém, zachovat poslední potvrzenou platnou práci; žádné tiché vydávání staré revize za novou.

### 6.2 Databáze a soubory médií jsou jiná úložiště

**RIZIKO:** databázová transakce sama nezaručuje, že jiný soubor s fotografií byl bezpečně zapsán. **KANDIDÁTNÍ postup k technickému ověření:** připravit médium odděleně, dokončit a ověřit jeho úplnost, zveřejnit jej stabilnímu umístění, potom potvrdit jeho ID/odkaz a revizi v modelu. Přerušení v každé fázi musí mít bezpečný výsledek; teprve pak je dovoleno ukázat potvrzené uložení. Konkrétní pořadí fsync/atomic rename/transakce, koordinaci více zápisů a fallback pro platformu rozhodnout až podle ověřených zkoušek.

**Neodstraňovat osiřelá média bez ověření**, zda je potřebuje ještě živý dokument, koš, předchozí potvrzená revize nebo rozpracovaná obnova. Při ztrátě přístupu k externímu souboru/SD kartě nesmazat poslední platný originál. Odvozené miniatury jsou naopak znovuvytvořitelné.

**Úložiště:** SQLite/Room a jednoduché souborové řešení zůstávají kandidáty. U každého prokázat skutečnou trvalost, koordinaci souborů, oprávnění, plný disk, souběžné zápisy, dobu uložení a rozsah potřebných záloh.

### 6.3 Undo/Redo, koš a historie

Undo/Redo vrací validované editační příkazy; **není** náhradou zálohování, obnovy po pádu či koše. **OTEVŘENO:** zda a jaký rozsah Undo/Redo přetrvá restart. Nepřidávat automaticky neomezenou historii plných zakázek; F32 je odložená. **Revize již vydaného instalačního dokumentu F35** jsou jiný důležitý případ, ne plná historie zakázky.

## 7. Minimální přenosná záloha M2 – kontrakt uzavřít v M0-A

Definovat **verzovaný manifest** včetně všech ID, vazeb, přesných měření, původních bajtů médií a kontroly úplnosti/integrity; záloha se nesmí označit jako hotová, dokud celý výstup není úplný. **OTEVŘENO:** konkrétní archivní formát, kontrolní součty/hash, struktura manifestu a ochrana osobních údajů; zatím nic neukládat na cloud či veřejný GitHub.

Obnova: ověřit verzi, integritu, vazby, počet/média, místo na disku a zda už existuje jiná platná práce **před jakýmkoli ničivým přepsáním**. Vadný, neúplný, nepodporovaný nebo na disk nevejdoucí archiv musí být odmítnut bez ztráty posledního platného stavu. **M2 HOTOVO:** úplná záloha → validace → obnova na čisté instalaci/odděleném zařízení → porovnání všech objektů a originálních bajtů; pak negativní testy s existující prací.

Lokální crash recovery řeší pád aplikace; přenosná záloha ztrátu telefonu; M4 plný editovatelný přenos; B2 až později synchronizaci. **Ani dvě místní kopie nejsou ochrana před ztrátou zařízení.**

## 8. Instalační plány F35 a export

Dvě fáze: **(a) ORIENTAČNÍ** šablona před návrhem, která už v M3b musí mít použitelné PDF a systémové sdílení (minimální M4a); **(b) KONKRÉTNÍ** kopie přiřazená zakázce a podle potřeby volitelný stav **SCHVÁLENO PRO REALIZACI** až po vědomé odborné kontrole. PDF vždy uvádí **stav, datum, revizi a identifikaci zakázky, pokud existuje**. Orientační podklad musí říkat **„ORIENTAČNÍ – není realizační pokyn“**, nikdy nesmí vydávat neověřené instalační výšky za univerzální normu. Již vydané schválené PDF neměnit – nová změna = nová revize **instalačního dokumentu**, nikoli historie celé zakázky F32.

## 9. Verze, import, přenositelnost a soukromí

Každý dokument/záloha má rozpoznatelnou verzi formátu. Migrace starších souborů se testuje na kopii; při nepodporovaném novějším formátu se nesmějí neznámé objekty či naměřené hodnoty potichu zahodit. **Formát dat nesmí být závislý na jediném rendereru ani pouze na JVM**. Import ze Zaměřovače 1 je samostatný pozdější projekt M4c **pouze z kopie**, bez úpravy v1.

Před M0-D u každého vážného kandidáta porovnat výkon Android editoru, bezpečnost dat, možnost budoucího Windows/iOS/iPadOS portu, Bluetooth a další hardware, licence, náklady a dlouhodobou údržbu. Žádný framework nyní není schválen.

Veřejný repozitář nesmí obsahovat skutečné zakázky, zákaznické fotografie, GPS, přihlašovací údaje ani tokeny. Testy jen se syntetickými/nahraditelnými podklady; export zákazníkovi nesmí nechtěně odkrýt skryté interní poznámky ani EXIF/GPS.

## 10. Scénáře k pozdějšímu ověření (vše nyní NOT RUN)

| Kód | Scénář | Podmínka budoucího PASS |
|---|---|---|
| T01 | Foto + kóta → save → úplně zavřít → reopen | Identická přesná hodnota, kotvy a originál |
| T02 | 1 000 dlouhých/přerušovaných tahů, Undo/Redo | Předvídatelné příkazy, žádná změna přesné míry a ověřená odezva |
| T03 | Přerušení zápisu dat/metadata | Zachována poslední potvrzená revize |
| T04 | Přerušení mezi souborem média a DB záznamem | Žádné falešné `uloženo`, žádný zničený originál |
| T05 | Plný disk, odmítnuté oprávnění, ztracený externí soubor | Přiznaná chyba bez poškození platné práce |
| T06 | Současný/opožděný zápis, pád, výpadek napájení | Žádné přepsání novější potvrzené práce; fyzický test oddělit od simulace |
| T07 | 1/16 in, lokální formát, plochy, jednotky | Přesný round-trip bez driftu/přetečení |
| T08 | EXIF, otočení displeje, změna výřezu/zvětšení | Kotvy správně, číselné míry beze změny |
| T09 | Panorama šev 359,9°/0,1° a pól sféry | Kotvy konzistentní i mimo aktuální výřez |
| T10 | Rozdělení F04 stěny a navázaný F05 nárys | Identita a naměřená délka zachované či vazba jasně nevyřešená |
| T11 | Laser – zpožděný údaj, nejasná jednotka | Nikdy nevložit do špatné kóty |
| T12 | Kompletní přenosná záloha, obnova na čisté instalaci | Stejná data, ID, jednotky, vazby, originální mediální bajty |
| T13 | Poškozená/neúplná/starší či novější záloha; existující práce | Bez tichého zničení platné práce |
| T14 | F35 orientační → konkrétní → schválená revize PDF | Správný stav/datum/revize, vydané PDF nepřepsáno |
| T15 | Migrace/otevření jiné verze dokumentu | Neznámá data se neztratí, bezpečné odmítnutí nebo migrace |
| T16 | Srovnání technologie pro 2D editor | Stejné podklady, měřený výkon, paměť, rizika a náklady |

**T01–T16 jsou NOVÉ návrhy scénářů, nikoli náhrada historických 28 testů.** Nyní **NOT RUN**. Teprve po schválení konkrétních úkolů a prostředí lze realizovat malé experimenty M0-C/M1/M2 a rozlišovat SOURCE, automat, emulátor a fyzický Android.

## 11. Otevřené otázky pro nezávislé DESIGN/SECURITY review

1. Rozhodnout přesnou kanonickou reprezentaci všech veličin a pravidla převodů (včetně výpočtů ploch).
2. Vybrat bezpečný algoritmus trvalého potvrzení pro koordinaci DB a externích médií; určit obnovu a čištění osiřelých souborů.
3. Přesně definovat orientaci a souřadnice foto/panorama/sféry, neznámou škálu půdorysu a nejednoznačné vazby rozdělené stěny.
4. Uzavřít politiku Undo/Redo po restartu a přesnou hranici jedné uživatelské změny.
5. Definovat minimální manifest zálohy, integritu, kontrolu obnovy a bezpečný import do existující práce.
6. Definovat verze/migrace a způsob práce s neznámými typy bez ztráty dat.
7. Uzavřít bezpečnostní přechody a revize F35, včetně zákaznického PDF a kontroly před vydáním.
8. Porovnat celý návrh s **[maticí 12+39+49+28 a detailními zdroji](CRAFTPLAN_M0A_MATICE_NAVAZNOSTI.md)**; při chybě doplnit a znovu posoudit.

**Neuzavřené otázky nejsou automaticky vyřešené přítomností této dokumentace.** Autor + nezávislý kontrolor musí rozhodnout, které lze vyřešit návrhově a které vyžadují samostatně povolené technické ověření; bez něj se neuděluje neopodstatněné APPROVE DESIGN.

## 12. Hranice a další kroky

**TEĎ:** autorova kontrola obou nových dokumentů a celé kumulativní změny, nezávislé DESIGN/SECURITY review. **POZDĚJI:** dopracování přesných rozhodnutí M0-A, M0-B brána, schválené M0-C experimenty, M0-D rozhodnutí o technologii. **NEMĚNIT:** PR #1 a #2 bez separátního povolení, `main`, původní Zaměřovač 1 a HOME, CI, APK, zákaznická data a peníze.

### Zdroje

[Hlavní plán](https://github.com/zaplikj30-ui/CraftPlan/blob/68646525fabadc6acd51d57e21184026a95cf7c9/docs/CRAFTPLAN_HLAVNI_PLAN_STAVBY.md) · [Ústava dat](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_USTAVA_DAT.md) · [Katalog funkcí](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_KATALOG_FUNKCI.md) · [F01–F05](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_F01_F05_ZADANI.md) · [Živý návrh](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_ZIVY_NAVRH.md) · [Inventura 49+28](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_INVENTURA_POZADAVKU.md) · [Registr F01–F39](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md) · [Historické návaznosti](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_REGISTR_NAVAZNOSTI_V1.md).
