# CraftPlan – hlavní plán stavby (technologicky neutrální návrh)

> **Stav: NÁVRH k věcné kontrole a výslovnému schválení vlastníkem, nikoli uzavřený harmonogram ani GO k implementaci.**  
> **Datum návrhu:** 22. 11. 2026. **Verze:** 0.1 – pracovní sjednocení závěrů Claude, ChatGPT a Codex.  
> **Projekt:** [CraftPlan](https://github.com/zaplikj30-ui/CraftPlan) – samostatná nová aplikace. Android jako první cílová platforma, do budoucna možnost Windows, iOS a iPadOS.  
> **Technologie / renderer / databáze:** **NEVYBRÁNO**. Java, Kotlin s Android View/Canvas, Kotlin s Compose nebo kombinací, Flutter, Qt a další obhajitelné kombinace zůstávají před rozhodovací bránou k posouzení. SQLite/Room je kandidát, ne implementační povolení.

**Tento dokument je samostatný nový návrh v jiném draft PR než dokumentační [PR #1](https://github.com/zaplikj30-ui/CraftPlan/pull/1).** Nepřepisuje schválené funkční požadavky ani automaticky nemění starší dokumenty. Historické formulace o „schváleném Kotlin/Compose prototypu“ jsou **ROZPOR s novějším výslovným rozhodnutím vlastníka** posoudit technologie před výběrem. Dokud se nerozhodne a dotčené dokumenty nesjednotí, žádný dřívější technický návrh není GO k programování.

## 1. Zásady a cíl

- Stavět **po malých funkčních, kontrolovatelných a vratných etapách**, nikoli celou vizi najednou. Každá etapa má svůj výstup, důkazy, závislosti, hranice a podmínku **HOTOVO**.
- Zachovat všechny relevantní historické požadavky a budoucí **F01–F39** v registru; jejich neexistence v M1 neznamená zamítnutí. Schválené chování, záměr a otevřený nápad jsou odlišné stavy.
- Skutečné naměřené rozměry **nikdy neodvozovat pouze z pixelů**. Hodnota, jednotka, původ a identita měření jsou oddělené od zobrazované geometrie.
- Média a fotografie se **neanotují destruktivně**; originální bajty a editovatelné objekty zůstávají oddělené.
- Již potvrzené uložení nesmí být falešné ani se tiše vracet na starší stav. Plný disk, pád, chybějící příloha či ztráta napájení vyžadují ověřitelné chování.
- **Undo/Redo ≠ koš ≠ obnova po pádu ≠ přenosná záloha ≠ synchronizace.**
- Původní Zaměřovač 1, jeho data, běhy a produkční prostředí jsou **NEMĚNIT**. Konfigurátor–Nářezák a Převaděč spojitosti zůstávají samostatnými produkty.
- Veřejný GitHub neobsahuje zákaznická data, soukromé fotografie, hesla ani tokeny. Žádné neodsouhlasené merge, Actions, HOME, buildy, APK ani výdaje (projektový limit 12 USD/měsíc).

## 2. Přehled navrženého pořadí

| Etapa | Malý ověřitelný výstup | Závislost / poznámka |
|---|---|---|
| **M0-A** | Technologicky neutrální kontrakty dat, identity, geometrie, měření, ukládání a obnovy; požadavky na testy | **TEĎ – pouze návrh** |
| **M0-B** | Nezávislé DESIGN/SECURITY review celého návrhu | Opravy a opakovaná kontrola před branou |
| **M0-C** | Jen **samostatně povolené** malé srovnatelné technické experimenty | Podle otevřených rizik, žádné tři kompletní aplikace |
| **M0-D** | Záznam rozhodnutí vlastníka o technologii, rendereru a základu ukládání; upravený plán M1 | Po měřeních / výslovném přijetí rizik |
| **M1** | První skutečně funkční **offline** průchod foto → kóta → gesta → Undo/Redo → save → reopen, malá diagnostika | Syntetická/nahraditelná data, samostatné implementační GO |
| **M2** | Rychlé zaměření, zakázky, složky, galerie, náhledy, koš; ověřená **přenosná záloha a obnova** před nenahraditelnými zakázkami | Navazuje na ověřené M1 |
| **M3a–d** | Další editační nástroje, **F35 instalační plány** (časná šablona **včetně PDF a sdílení**), **F04/F05 půdorys/nárys**, pak napojení plánu na zakázku | M3b nelze uzavřít bez zákazníkovi použitelného výstupu |
| **M4a–c** | Zákaznické výstupy, plný editovatelný export/import, oddělený import kopie v1 | **Minimální PDF a sdílení musí vzniknout před/současně s M3b**; neblokovat zálohu M2 |
| **M5** | Jeden fyzicky ověřený laser a protokol; další značky postupně | První proveditelnost F03 zkoumat už v M0; M4 není nutná závislost |
| **Pozdější větve** | F01/F02 panoramata a sféry, F06 3D náhled, B1/B2 zálohy a sync, hlas/AI, obchodní funkce, Windows/iOS | Rizika technicky ověřit včas, plnou implementaci nepředbíhat |

**Pořadí M3/M4/M5 není definitivní harmonogram.** Plán je orientovaný na závislosti, ne na pevná data a automaticky schválené milníky.

## 3. M0 – návrhová a rozhodovací etapa (bez automatického povolení kódu)

### M0-A – Přesně definovat kontrakty

| Oblast | Otázky a povinné výstupy návrhu |
|---|---|
| **Přesná měření** | Veličina (délka, plocha, úhel…), jednotka, zdroj, původní vstup, stav „nezměřeno“, formát přesné hodnoty (např. kanonický desetinný text / jiný přesný model), povolený rozsah, výpočty, převody a pravidla explicitního zaokrouhlení; testy desetinné čárky i zlomkových palců |
| **Identity a vazby** | Stabilní ID zákazníka (až později), zakázky, dokumentu, média, kóty, události měření, stěny, výkresu a přílohy; životní cyklus, přesuny, mazání/koš, nejednoznačné návaznosti |
| **Souřadnice a dokumenty** | Foto: vlastní souřadnice a EXIF orientace; panorama: úhlová kotva, projekce, šev/póly; půdorys: metrická geometrie, topologie a identita stěn; nárys: vazba na stěnu a její části. **Nedělat jeden univerzální model pixelů.** |
| **Editace a gesta** | Co je dočasný náhled tahu, co validovaný příkaz a kdy vzniká krok Undo/Redo; volba, zda a v jakém rozsahu historie přežije restart; pravidla při zrušení gesta |
| **Ukládání a média** | Výslovně popsat pořadí uložení originálního média a záznamu o něm, kontrolu úplnosti, okamžik zobrazení „uloženo“, souběžné/zpožděné zápisy a zachování posledního potvrzeného stavu. Určit vlastnictví příloh, bezpečné čištění osiřelých médií po ověření všech živých/obnovitelných odkazů, chování při ztrátě přístupu k externí fotografii a zákaz smazat poslední platný originál. DB transakce sama nepokrývá samostatný mediální soubor. |
| **Obnova a zálohování** | Pád procesu vs. skutečný výpadek napájení, plný disk, vadná příloha, nesprávná migrace, poškozený záznam; pravidla návratu k předchozímu stavu s jasnou informací. **Již v M0-A určit minimální kontrakt přenosné zálohy M2:** verze formátu, úplný inventář ID, vazeb, přesných hodnot/jednotek a původních mediálních bajtů, integrita, úplnost, ochrana před částečným výstupem a pravidla soukromí. Poškozená/neúplná/nepodporovaná záloha nesmí přepsat poslední platnou práci. Oddělit lokální recovery od přenosné zálohy a sync. |
| **Migrace dat CraftPlanu** | Verze schématu a souborů, bezpečné otevírání starší/novější verze, test vratnosti nebo bezpečné odmítnutí. **Nezaměňovat s importem ze Zaměřovače 1.** |
| **Přenositelnost** | Rozhraní modelu a formát nezávislé na UI; platformní integrace jen tam, kde jsou potřeba. Pro **každého vážného kandidáta** M0-D srovnatelně odhadnout dlouhodobé náklady a náročnost portu na Windows/iOS/iPadOS, včetně neověřených předpokladů; fyzický portovací prototyp není nyní povinný. Žádný předčasný plugin engine, účet, server ani multiplatformní UI. |
| **Rozhodovací zkoušky** | Srovnatelné scénáře, konkrétní zařízení, měřené vlastnosti a návrh prahů PASS/FAIL; jen syntetická nebo bezpečně veřejná data. |

**Výstup M0-A:** malý kontrolovatelný datový slovník, diagram vazeb, popis ukládacího protokolu **i minimální přenosné zálohy M2**, příklady správných/chybných vstupů a matice akceptačních scénářů. Provázat původních **49 požadavků a všech F01–F39** na existující katalog/registr: u každého dohledat stav (připraveno / otevřeno / odloženo / skutečně ověřeno) a etapu nebo „zatím bez etapy“. Nepřidávat duplicitní zdroj pravdy a při změnách kontrolovat, že žádný detail nezmizel. **Není to hotový program.**

### M0-B – Nezávislé návrhové review

Oponent musí zkusit vyvrátit bezpečnost potvrzeného uložení, přesnost hodnot/jednotek, stabilitu vazeb při dělení stěn, souřadnicové modely, budoucí migrace a přiměřenost řešení. Autor opraví nalezené problémy a kontrola se na konečné revizi zopakuje. Verdikt **APPROVE DESIGN / CHANGES REQUIRED / STOP**. Starší nezávislé **APPROVE SOURCE dokumentačního přenosu PR #1 není automaticky APPROVE DESIGN nových M0 kontraktů**.

### M0-C – Omezené experimenty, **až po GO**

Vhodné kandidátní zkoušky, jejichž skutečný rozsah se ještě schválí:

1. **Přesnost:** round-trip metrických a imperiálních měr, velké/malé hodnoty, typ veličiny a definované zaokrouhlení.
2. **Ukládání:** SQLite/Room proti případné jednoduché souborové variantě; odpojení média, pád v jednotlivých fázích, plný disk, souběh, migrace, „uloženo“ až po trvalém potvrzení, byte-for-byte originál.
3. **Editor:** stejný snímek, 1/20/50/200 kót, 100/1 000 tahů, zoom/pan, lupa, prst/S Pen, přerušení gesta, odezva, p95/p99, paměť, velikost aplikace a náročnost údržby. Porovnat Compose Canvas a nativní Android View/Canvas; Flutter podle významu budoucích platforem. **Java není automaticky vyřazena**, případný přínos je nutné posoudit proti nákladům.
4. **Panorama:** nejdřív editovatelné kóty na známém hotovém panoramatu a šev; zvlášť kvalita pořízení, stitching a mobilní paměť.
5. **Laser F03:** dostupnost protokolu/SDK, typ Bluetooth, přenos hodnoty versus vzdálené spuštění, licence, fyzická zkouška pro konkrétní model, pokud je pro produkt rozhodující.
6. **Portovatelnost:** jednoduchý průchod na druhé platformě pouze tehdy, pokud její požadovaný termín a rozsah zásadně mění volbu technologie.

**Nezačínat zkoušky bez schváleného prostředí, rozpočtu a odděleného GO.** Výsledky rozlišovat dle úrovně důkazu: návrh / SOURCE / automatický test / emulátor / reálný telefon / konkrétní přístroj.

### M0-D – Rozhodovací brána

Sepsat výsledky, **srovnatelné odhady budoucího portu a dlouhodobé náklady každé vážné technologické varianty**, výslovně přijatá rizika a reálné budoucí potřeby. Nerozhodovat jen podle plynulosti jediného 2D editoru či popularity frameworku. Po eventuálních zkouškách aktualizovat kontrakty a podle podstatnosti změn zopakovat DESIGN/SECURITY review. **Vlastník teprve poté výslovně rozhodne** o jazyku/frameworku, editoru/rendereru, úložišti a případném spuštění M1. Zvolená technologie není důkaz, že aplikace funguje.

**M0 HOTOVO** = sjednocené ověřitelné kontrakty a testy; žádný nevyřešený kritický bezpečnostní rozpor; nezávislé návrhové review; výsledky povolených experimentů nebo jasné přijetí neověřených rizik; dokumentované rozhodnutí vlastníka; konkrétní zadání prvního malého M1 úkolu.

## 4. M1 – první skutečný offline pracovní celek

**Rozsah:** vybrat/pořídit fotografii, zachovat originál, vložit a přesně editovat kótu, ovládat úchopy/gesta prstem a perem včetně zoom/pan, Undo/Redo, bezpečně uložit, ukončit či v testu přerušit a znovu otevřít poslední platný stav. **Rychlé zaměření bez povinného zákazníka** musí být možné; plné zakázky přijdou v M2.

**Od začátku malá volitelná místní diagnostika** bez zákaznických dat, s ověřenou režií a možností prohlížení, výběru a sdílení/mazání záznamů v rozsahu schváleného funkčního kontraktu. Nedělat velký automatický testovací framework.

**HOTOVO / důkazy:** přesnost čísel a jednotek, byte-for-byte originál, funkční Undo/Redo a interakce, test přerušení a plného disku, žádné falešné „uloženo“, bezpečný reopen, výkon delšího tahu a mnoho pohybů, diagnostika nezhoršuje ukládání. Automatické testy **nejsou** důkazem funkčnosti fyzického Androidu. Samostatný autorský SOURCE/SECURITY review, nezávislé review, fyzické scénáře a vlastníkovo GO.

**Omezení:** pouze syntetická/nahraditelná data; žádné nenahraditelné zakázky, účty, sync, server, automatický import dat ze v1 nebo plošná implementace budoucích funkcí.

## 5. M2 – organizace práce a brána pro skutečná data

Zakázky, zákazníci, strom složek a podsložek, rychlé zaměření bez zákazníka a jeho pozdější přiřazení, galerie/náhledy, prohlížení bez nechtěných úprav, koš a bezpečné přesuny/kopie s uchováním identit a referencí.

**Před nenahraditelnými zákaznickými zakázkami** samostatně prokázat **přenosnou zálohu a skutečnou obnovu** podle kontraktu M0-A, se všemi objekty, ID, vazbami, přesnými hodnotami a originálními médii. Záloha má verzovaný formát a ověřitelný inventář i integritu; částečný export se nesmí vydávat za platnou zálohu. Ochrana soukromí je povinná. Může to být malý účelový mechanismus dříve než plný editovatelný export M4. Nepředstírat, že dvě lokální revize chrání před ztrátou telefonu.

**HOTOVO:** zachované ID a vazby při přesunech/mazání; obnova z koše a podle povolených pravidel; výkon galerie s 10/100/1 000 položkami; restart, plný disk a chybějící příloha. **Test přenosné zálohy:** vytvořit → ověřit → obnovit na čisté instalaci či odděleném zařízení → porovnat všechny ID, vazby, objekty, přesné hodnoty a originální mediální bajty. Poškozený nebo neúplný archiv, nepodporovaná verze a nedostatek místa se bezpečně odmítnou, **aniž by přepsaly poslední platnou práci**. Testovat také pokus o obnovu do prostředí s existující platnou prací. Kde ještě nelze použít skutečná data, jasně to označit.

## 6. M3 a M4 – rozvoj podle pracovních priorit a závislostí

### M3a – další editační nástroje

Přidávat postupně jen potřebné tvary, symboly, úhly, šipky, poznámky a přílohy. Každý nástroj musí mít editovatelné objekty, gesta, Undo/Redo, bezpečný zápis a regresní testy na existujícím M1/M2.

### M3b – instalační plány F35, **časná první část**

Vysoká produktová priorita: **editovatelná orientační šablona** např. přípravy vody/elektřiny/odpadu ještě před konkrétním návrhem kuchyně. Nečekat automaticky na všechny grafické nástroje, F04/F05 ani na úplnou fakturaci.

**Závislost:** nejmenší funkční část M4a (offline PDF export a vědomé systémové sdílení) musí vzniknout **před nebo současně s M3b**. **HOTOVO M3b** znamená: vytvořit šablonu → upravit značky/výšky/popisky → bezpečně uložit/otevřít → prohlédnout předávaný obsah → exportovat a otevřít skutečné PDF → vyzkoušet sdílení. PDF musí být jednoznačně označeno **„ORIENTAČNÍ – předběžné doporučení, není realizační pokyn“**, datem a revizí. Nesmí nechtěně odhalit interní poznámky. **Neověřené univerzální výšky/pozice přípojek nelze označovat za normu ani bezpečný realizační pokyn**; závisí na stavbě, výrobcích, spotřebičích a předpisech. Nevyžaduje celý M4b, účet ani server.

### M3c – půdorys F04 + nárys/práce se stěnou F05

Postupně stěny, rohy, přichytávání a odlišné režimy výběru/tažení; editovatelné navázané nárysy a fotopoznámky. **F05 je vnořená část F04, nikoli samostatný produkt či další zapínatelná funkce.** Při dělení/zalomení stěny zachovat měření a návazné objekty; nejednoznačné vazby vyžadují výslovné rozhodnutí uživatele.

### M3d – instalační plány F35, **část navázaná na zakázku**

Kopie šablony navázaná na konkrétní zaměření, půdorys, rozměry, zakázku a pozdější výrobní dokumentaci. Přenos objektů nesmí tiše měnit původní přesná měření.

**Stavy:** **ORIENTAČNÍ → KONKRÉTNÍ pro zakázku → volitelně SCHVÁLENO PRO REALIZACI**. Poslední stav pouze po vědomé odborné a podle potřeby zákaznické kontrole, nikoli automaticky po změně kóty. Uložený dokument i vydané PDF musí nést stav, datum, identifikaci zakázky a revizi **konkrétního instalačního dokumentu**. Již vydané schválené PDF se nesmí tiše přepisovat – další změna vytváří novou revizi, nikoli historii celé zakázky F32.

**HOTOVO M3d:** prokázané vědomé přechody stavů, zachování původní šablony a schváleného výstupu, přesných hodnot, bezpečné uložení/obnovení a skutečné PDF se správným stavem/datem/revizí; neověřený plán nikdy nesmí vypadat jako pokyn k realizaci.

### M4a – zákaznické výstupy

**Nejmenší část M4a je povinnou závislostí M3b:** offline vytvořit PDF orientačního instalačního plánu, ověřit náhled a skutečný vyexportovaný soubor, nabídnout vědomé systémové sdílení bez vlastního účtu/serveru. Další JPG/PDF výstupy mohou vzniknout později. U F35 vždy uvádět správný **stav, datum, revizi a identifikaci zakázky, pokud existuje**. Orientační PDF nesmí působit jako schválený realizační pokyn; vydané schválené PDF musí zůstat neměnné. Odstranit nežádoucí interní poznámky a nepotřebná EXIF/metadata. Exportovaný pohled není totéž co editovatelný pracovní originál.

### M4b – editovatelný přenos zakázky

Verzovaný archiv, import a export včetně ID, přesných dat, editovatelných objektů, médií a kontroly chyb/bezpečnosti importu. **Není podmínkou M2 minimální záchranné zálohy; ta musí být ověřená dřív, než se používají nenahraditelná data.**

### M4c – oddělený import ze Zaměřovače 1

Pouze **z kopie** a bez jakéhokoli zásahu do původní jedničky. Odmítnout nejasná či nepodporovaná data bez tiché ztráty; potřebné kompatibility dokládat věrnými scénáři.

**Pořadí:** **Minimální M4a (PDF + sdílení) musí vzniknout před nebo současně s M3b.** Jejich společný použitelný výstup je podmínkou HOTOVO první F35. Ostatní M3a/M4a lze přeskupit podle závislostí; velkou M3c nerozjíždět jen kvůli pořadovému číslu. Každá podetapa má vlastní testovatelný rozsah, review a GO.

## 7. M5 – připojení laserů F03

Jeden konkrétní fyzicky dostupný model/firmware a telefon: nejprve ruční zadání a bezpečný **příjem** přesného měření do správné kóty; **dálkové spuštění z telefonu** ověřit jako samostatnou schopnost jen u přístroje, který ji skutečně podporuje. Pak postupně další značky a protokoly.

**HOTOVO:** fyzický test, přesná jednotka a veličina, ruční offline náhradní cesta, správný kontext aktivní kóty, změna výběru během čekání, duplicitní hodnota, výpadek spojení, restart a potvrzené uložení. Softwarový dekodér ani oficiální seznam podporovaných laserů není sám o sobě fyzický PASS CraftPlanu. **M4 není nutnou technickou závislostí M5.**

## 8. Pozdější větve – nic nezahodit, nic nepředbíhat

- **F01 horizontální panorama a F02 plná sféra:** import hotového, prohlížení a editovatelné kóty, průvodce pořízením a kvalitní spojování zdrojových snímků; dva nezávislé přepínače nástrojů. **Riziko ověřit časně**, plný editor nestavět v M1.
- **F06 prostorový orientační náhled** až na stabilních F04/F05; není automaticky CAD/3D engine.
- **B1 přenosné/offline a pozdější cloudové zálohování** a **B2 živá synchronizace** jsou oddělené; cloud až s prokázanou lokální integritou, ID/revizemi, konflikty a samostatným GO.
- **F10/F13 hlasové diktování a případná volitelná AI:** validované stejné editační příkazy jako ruční editor; AI nesmí vyrábět skutečné naměřené hodnoty z pixelů.
- **F33/F34 pracovní čas a obchodní funkce, F36/F37 zákaznický režim/sdílení, další F01–F39, internacionalizace a moduly:** podle produktové priority, soukromí, bezpečnosti a nákladů. V matici požadavků neztratit **zvuk/video přílohy, přepínače viditelnosti nástrojů a offline nápovědu**. **F07 a F32 zůstávají odložené**, nikoli bez povolení znovu naplánované.
- **Windows/iOS/iPadOS:** před závazným termínem portu samostatná proveditelnost myši/pera, souborů, Bluetooth, balení, ceny a reálně sdíleného jádra/UI. Samotný obecný příslib frameworku není důkaz funkčnosti aplikace.
- **Konfigurátor–Nářezák a Převaděč spojitosti:** samostatné projekty; případná integrace jen po novém rozhodnutí vlastníka.

## 9. Kontrola a režim práce

**Před významnou implementací:** jasný kontrakt, vstupy/výstupy, testy, hranice, aktuální HEAD/base, samostatná větev/PR a odpovídající GO. Autor provede po změnách skutečné testy a vlastní důkladné SOURCE/SECURITY review celého kumulativního diffu, opraví chyby a zopakuje kontrolu na finálním SHA. Jiný chat/osoba potom provede nezávislé review s verdiktem **APPROVE SOURCE / CHANGES REQUIRED / STOP**. **Zdrojový PASS není fyzický PASS.** Merge/Actions/APK/vydání/placený běh jen po samostatném výslovném GO.

**TEĎ:** pouze dokončit M0-A a nezávislou návrhovou kontrolu, připravit srovnatelná testovací kritéria.  
**POZDĚJI:** samostatně schválené experimenty M0-C, rozhodnutí M0-D, environment pro schválené technologie, první implementace M1.  
**NEMĚNIT:** Zaměřovač 1 a jeho data, HOME runner, žádné neodsouhlasené CI, APK, merge ani náklady.

## 10. Otevřená rozhodnutí a známé rozpory

1. **Technologie:** starší AGENTS/START_ZDE/Strom vývoje a další dokumenty PR #1 mluví o schváleném Kotlin + Compose prototypu. **Novější výslovné rozhodnutí vlastníka: definitivní technologie ani první renderer nejsou vybrány.** Tyto dokumenty bude třeba **po schválení nového rozhodnutí** sjednotit v kontrolované změně; nynější draft to sám potichu nemění.
2. **Pořadí M2–M5:** původní strom odkládá export a instalační plány. Tento nový návrh přesouvá **minimální přenosnou zálohu** před skutečné zakázky, zvyšuje prioritu **F35** a dovoluje ranou technickou zkoušku laseru/panoramatu. **Přeskupení je návrh k rozhodnutí**, ne už schválená změna funkčních požadavků.
3. **Reprezentace míry:** mikrometry a přesný text jsou **kandidáti k porovnání**, ne uzavřený datový formát; včetně zlomkových palců, ploch a možného přetečení.
4. **Undo/Redo po restartu**, podrobné technické a validační detaily zálohy podle základního kontraktu M0-A, cílové slabší zařízení, termín iOS/Windows a praktické pořadí F35/F04/F05/F03/F01 vyžadují upřesnění podle priorit.
5. **Neověřeno:** všechny runtime výkonnostní hodnoty, bezpečnost při fyzickém výpadku, dostupnost konkrétního Bluetooth profilu, stitching panoramat, kompatibilita Windows/iOS a náklady konkrétních knihoven.

**Tento dokument je hlavní návrh k připomínkování, dokud nebude výslovně schválen. Nenahrazuje aktuální zdroj pravdy v PR #1 automaticky.**

## 11. Podklady a dohledatelnost

Podklady pocházejí z kontrolované dokumentační revize PR #1 (HEAD `837988f3e5712fe71a983307dc5417605b429d7b`), nezávislého SOURCE/SECURITY review tohoto dokumentačního přenosu a ze tří pozdějších poradních technologických posudků Claude, ChatGPT a Codex (nejde o runtime testy). Odkazy jsou **SHA-připnuté**, protože tyto soubory ještě nejsou v `main`. Po případném schváleném sloučení PR #1 lze odkazy sjednotit.

- [Pravidla práce AGENTS](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/AGENTS.md)
- [Rozcestník START_ZDE](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/START_ZDE.md)
- [Strom vývoje M0–M5](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_STROM_VYVOJE.md)
- [Jednotný základ](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_JEDNOTNY_ZAKLAD_A_PRENOS.md)
- [Ústava dat M0](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_USTAVA_DAT.md)
- [Funkční katalog](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_KATALOG_FUNKCI.md)
- [Registr F01–F39](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md)
- [F01–F05 zadání](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_F01_F05_ZADANI.md)
- [Živý návrh](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_ZIVY_NAVRH.md)
- [Inventura požadavků](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_INVENTURA_POZADAVKU.md)
- [Porovnání s v1](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_POROVNANI_S_V1.md)
- [Laser F03](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_LASERY_F03_NAVRH.md)
- [Přehled PR #1 a nezávislé dokumentační kontroly](https://github.com/zaplikj30-ui/CraftPlan/pull/1)
