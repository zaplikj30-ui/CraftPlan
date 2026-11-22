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
- Média a fotografie se **neanotují destruktivně**: kóty/poznámky jsou oddělené, existující externí originály se nepřepisují; v CraftPlanu se drží **jedna autoritativní fotoverze ve zvolené kvalitě** bez dalšího automatického uložení do Galerie.
- Již potvrzené uložení nesmí být falešné ani se tiše vracet na starší stav. Plný disk, pád, chybějící příloha či ztráta napájení vyžadují ověřitelné chování.
- **Undo/Redo ≠ koš ≠ obnova po pádu ≠ přenosná záloha ≠ synchronizace.**
- Původní Zaměřovač 1, jeho data, běhy a produkční prostředí jsou **NEMĚNIT**. Konfigurátor–Nářezák a Převaděč spojitosti zůstávají samostatnými produkty.
- Veřejný GitHub neobsahuje zákaznická data, soukromé fotografie, hesla ani tokeny. Žádné neodsouhlasené merge, Actions, HOME, buildy, APK ani výdaje (projektový limit 12 USD/měsíc).

### Pravidlo technologie: nejdříve hotová a dlouhodobě udržitelná řešení

**VÝSLOVNÉ ROZHODNUTÍ vlastníka 22. 11. 2026 – závazné pro celý CraftPlan a všechny vývojové etapy.** Před vlastním programováním jakékoli významné technické součásti **nejdříve prohledat aktuální internet, oficiální dokumentaci a dostupné, již funkční a udržované technologie / knihovny / systémová API**. Neomezuje se to na Kotlin ani na databázi: pravidlo platí pro **UI, gesta, kreslení, soubory, sdílení, export/import, fotografie, PDF, přesné hodnoty, lokální ukládání, přenosné zálohy, obnovení, cloud a synchronizaci, práci na pozadí, Bluetooth, budoucí Windows/iOS a další součásti**. Nepsat vlastní grafický engine, databázi, synchronizační infrastrukturu, přenosový protokol ani jiné složité základy, **pokud vhodné hotové řešení splní naše požadavky**. Jde o prevenci křehkých, těžko opravitelných řešení a pozdějšího velkého přepisování, nikoli o zákaz vlastního kódu pro specifické funkce CraftPlanu.

**Povinný rozhodovací postup pro autora a koordinátora, navazující na `AGENTS.md`:**

1. **Nejdříve požadavky a závislosti:** určit, co musí daná funkce skutečně dělat nyní a co nesmí znemožnit později; rozlišit přenos/sdílení, obnovitelnou zálohu, lokální databázi a synchronizaci více zařízení jako **různé problémy**.
2. **Průzkum hotových možností:** porovnat zpravidla 2–3 realistické kandidáty včetně systémového řešení, pokud existuje. Doložit **oficiální zdroje, stáří a stav údržby, kompatibilitu Android + realistické budoucí Windows/iOS/iPadOS, stabilitu API, licenci, bezpečnost, podporu offline, oprávnění a náklady**; žádná volba pouze podle popularity.
3. **Vyhodnotit celkový dopad:** co kandidát řeší hotově, co musíme doprogramovat, jak se testuje, jak zabráníme ztrátě či tichému přepisu přesných měr, fotek a vazeb, jaký je postup opravy, migrace a případné **výměny komponenty bez přepsání celé aplikace**. Srovnat i údržbu, závislost na dodavateli a provozní limit **12 USD/měsíc**.
4. **Rozhodnutí a brána:** zaznamenat výběr, nevýhody, neověřené body a zkušební scénáře. Vlastní komplexní řešení povolit **jen s konkrétním zdůvodněním**, že hotové varianty nevyhovují nebo by byly prokazatelně horší. **Žádný výběr, implementace, build, Actions, připojení placené služby, použití zákaznických dat ani experiment bez příslušného GO vlastníka.**

**Zpřesnění častého nedorozumění:** [SQLite](https://www.sqlite.org/index.html) je lokální databáze a například Android Room nad ní staví knihovní vrstvu; **SQLite sama o sobě neznamená synchronizaci dvou zařízení**. Pro sdílení souboru lze prověřit hotové [Android Sharesheet](https://developer.android.com/training/sharing/send) a [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files). Pro pozdější zálohování či sync jsou kandidáty [Google Drive API](https://developers.google.com/workspace/drive/api/guides/appdata), [Firestore s offline podporou](https://firebase.google.com/docs/firestore/manage-data/enable-offline), [PowerSync](https://powersync.com/), [Couchbase Lite](https://docs.couchbase.com/couchbase-lite/current/) a pro plánování úloh [WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent). **Jde o příklady existujících možností, ne schválený výběr ani doklad, že jsou bezpečné pro datový model CraftPlanu.** Například synchronizace s pravidlem „poslední zápis vyhrává“ nesmí bez vlastních validačních/konfliktních pravidel tiše přepsat platné měření; fotografie a přílohy vyžadují zvláštní kontrolu integrity. Prvně ověřit přenosnou zálohu a obnovu, automatickou synchronizaci nepředbíhat.

**Stav:** zásada **POTVRZENO**, konkrétní technologie / databáze / renderer / způsob synchronizace **NEVYBRÁNY**. Průzkum existujících nástrojů je podklad, **nikoli test nebo GO**. U každé budoucí etapy se toto pravidlo uplatní znovu podle aktuálního stavu technologií.

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
| **Obnova a zálohování** | Pád procesu vs. skutečný výpadek napájení, plný disk, vadná příloha, nesprávná migrace, poškozený záznam; bezpečný návrat k předchozímu stavu s jasnou informací. **Již v M0-A určit minimální kontrakt M2 přenosné zálohy** jako **jeden konzistentní průřez více zakázkami, jejich potvrzenými revizemi a chráněnými médii**: verze formátu, úplné ID/vazby, přesné hodnoty/jednotky a **identické bajty skutečně interně uložených fotoverzí vybrané kvality**, integrita, ochrana před souběžným zápisem/GC a částečným výstupem a soukromí. Nevyžadovat originální externí galerii k obnově; neúplný/nepodporovaný archiv nesmí přepsat poslední platnou práci. |
| **Migrace dat CraftPlanu** | Verze schématu a souborů, bezpečné otevírání starší/novější verze, test vratnosti nebo bezpečné odmítnutí. **Nezaměňovat s importem ze Zaměřovače 1.** |
| **Přenositelnost** | Rozhraní modelu a formát nezávislé na UI; platformní integrace jen tam, kde jsou potřeba. Pro **každého vážného kandidáta** M0-D srovnatelně odhadnout dlouhodobé náklady a náročnost portu na Windows/iOS/iPadOS, včetně neověřených předpokladů; fyzický portovací prototyp není nyní povinný. Žádný předčasný plugin engine, účet, server ani multiplatformní UI. |
| **Rozhodovací zkoušky** | Srovnatelné scénáře, cílové slabší zařízení, konkrétní prst/pero a realistická velká média, měřené vlastnosti/latence/paměť a předem navržené prahy PASS/FAIL; u každé vážné technologie explicitní integrační náklady foto/video/PDF/Bluetooth/offline hlasu/portu, údržba a licence; jen syntetická nebo bezpečně veřejná data. |

**Výstup M0-A:** malý kontrolovatelný datový slovník, diagram vazeb, popis ukládacího protokolu **i minimální přenosné zálohy M2**, příklady správných/chybných vstupů a matice akceptačních scénářů. Provázat původních **49 požadavků a všech F01–F39** na existující katalog/registr: u každého dohledat stav (připraveno / otevřeno / odloženo / skutečně ověřeno) a etapu nebo „zatím bez etapy“. Nepřidávat duplicitní zdroj pravdy a při změnách kontrolovat, že žádný detail nezmizel. **Není to hotový program.**

**Aktuální M0-A návrhový výstup (22. 11. 2026):** v existujícím [PR #3](https://github.com/zaplikj30-ui/CraftPlan/pull/3) jsou dopsány konkrétní kandidátní kontrakty přesného LENGTH, potvrzení revize/obnovy po pádu a manifest/restore M2. **Stav je návrh k cílenému review, nikoli HOTOVO či fyzický PASS.** Kontrakt M2 zachovává schválený volitelný Koš ROZ-24 (při první záloze předvolen zahrnout).

**M0-A/CP3-02/03 – dopracovaný bezpečnostní návrh (22. 11. 2026, PR #3 §7.1.2/§9.1.0):** úplný ZIP není soubor nezávislých „posledních“ fotek, ale **jeden konzistentní potvrzený průřez více zakázkami a revizemi** s chráněnými bajty médií, ověřeným seznamem ID/vazeb a atomickou obnovou. Aktualizace lokálního schématu musí chránit původní validní data a při neúspěšné migraci nepředstírat dokončení ani publikovat napůl převedený graf. **Rozhodovací brána k cílenému security review, fyzický PASS NOT RUN; žádný GO pro kód/experiment.**

**Nezávislé review Codex PR #3 (22. 11. 2026):** [CHANGES REQUIRED – 6 HIGH, 1 MEDIUM](https://github.com/zaplikj30-ui/CraftPlan/pull/3#issuecomment-7083195494). Autorské návrhové opravy CP3-01 (idempotentní save + storageGeneration), CP3-02 (připnutý průřez a nezničitelná starší ZIP), CP3-03 (migrace), validace cizích rozšíření, původních mír a mapy EXIF jdou do **existujícího PR #3**, technologické scénáře do PR #5. **Skutečný limit M1 pro neukončitelné zlomky typu `1/3 in` ZŮSTÁVÁ OTEVŘENÝ PRO VLASTNÍKA**; nepředstírat volbu za něj. Bez nové celoprojektové série review, další nezávislá kontrola jen na opravovaných SHA; testy NOT RUN.



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

**M0 HOTOVO** = sjednocené ověřitelné kontrakty a testy; **žádný nevyřešený kritický bezpečnostní rozpor včetně trvalosti save, obnovy a fotografie**; nezávislé návrhové review **změněných revizí**; výsledky případných povolených experimentů nebo výslovně přijatá neblokující neověřená rizika; dokumentované rozhodnutí vlastníka; konkrétní zadání prvního malého M1 úkolu. Dosavadní posudky [PR #5](https://github.com/zaplikj30-ui/CraftPlan/pull/5) jsou výchozí nálezy, ne automatické schválení po opravách.

**SCHVÁLENÁ přesnost M1 – 22. 11. 2026 (PR #4 ROZ-42, PR #3 §4.0.1):** přesná kanonická desetinná reprezentace délky v mm bez zaokrouhlení uložení, exaktní převody včetně `1/16 in = 1.5875 mm`, zachování původního zadání/jednotky a oddělení platné `0 mm` od `NEZMĚŘENO`. Typovaná veličina a původ měření se nepřepíší podle pixelů. Konkrétní číselné limity a API jsou **nepotvrzené technické návrhy** pro cílené nezávislé review; žádný runtime PASS ani výběr databáze.

**SCHVÁLENO ROZ-43 (22. 11. 2026; PR #3 §5.1.1, PR #4 ROZ-43):** EXIF otočení/zrcadlení normalizovat právě jednou; kotvy kót vést vůči autoritativní fotografii, ne obrazovce, takže zoom/pan/rotace zařízení nemění uložené body; F09 výměna podkladu zachová oba **odlišné potřebné snímky**, každý s vlastní jedinou autoritativní interní verzí, a umožní ruční přizpůsobení, ne tichý odhad shody; orientace, výměna ani posun kóty nemění skutečně zadané číslo. Původní externí import neměnit. Konkrétní EXIF 1–8/transformace u/v a fyzické testy **NOT RUN**, bez implementačního GO.

**SCHVÁLENO ROZ-44 / M2 obnova ZIP (22. 11. 2026; PR #3 §7.1.0 a PR #4 ROZ-44):** prázdná instalace obnoví všechny v záloze zahrnuté editovatelné objekty, přesné míry a skutečně interně uložené fotoverze; instalace s existující prací nic nepřepisuje, nabídne vědomé `Importovat jako kopii` s novými ID a konzistentními vazbami; vadný/neúplný/škodlivý ZIP i nedostatek místa znamenají bezpečné odmítnutí bez ztráty práce a neúplné publikace; volitelný Koš se obnoví, pokud byl zahrnut, jinak musí být jeho vynechání výslovně oznámeno. ZIP bez hesla, jen vědomé Uložit/Sdílet, žádný automatický upload zůstávají. **Křížový restore a diskové chování NOT RUN, žádné GO.**

**SCHVÁLENO ROZ-45 – autosave a RAM/přerušení (22. 11. 2026; PR #3 §6.1.2, PR #4 ROZ-45):** dokončené validní příkazy bezpečně průběžně ukládat bez zpomalování kreslení; nedokončený tah při přerušení hovorem/uzamčením/OS zrušit, ostatní dokončené kóty zachovat; při nízké paměti degradovat pouze rekonstruovatelnou cache či náročné video, ne data; po pádu nabídnout obnovu/zahození jen **skutečně bezpečně uloženého novějšího návrhu**, který se bez výslovné volby nemaže (nikdy neslibovat pouze RAM); selže-li uložení, před běžným odchodem varovat a nabídnout opakování. **Fyzické garance a interval autosave NOT RUN, bez implementačního GO.**

**SCHVÁLENO ROZ-46 / Koš a bezpečné mazání (22. 11. 2026; PR #3 §6.6, PR #4 ROZ-46):** smazaná složka jde do Koše s celým podstromem; fotografie s vlastními kótami/poznámkami/přílohami; obnova zachová vazby a při ztraceném umístění použije zřetelný záchranný kontejner. Ruční vysypání vždy ukáže počet/rozsah a vyžádá potvrzení; selhání hromadné operace ukáže skutečný dílčí výsledek, jedna složka/fotografie nikdy není napůl publikovaná. Automatické mazání výchozí OFF; chráněná média nelikvidovat. **Testy NOT RUN, bez GO.**

**ROZ-47 – M0 systémový přenos Androidu: záměr ověřit, nikoliv předem zakázat (22. 11. 2026):** uživatel požaduje prakticky přenést CraftPlan mezi dvěma testovacími telefony a prokázat úplnost zakázek, ID, přesných kót, skutečných foto/video souborů, poznámek, Koše a editovatelnosti; zvlášť testovat Google cloud Auto Backup a D2D. Podmínky: pouze syntetická data, jasný testovací rozsah, žádné nenadálé produkční cloudové odesílání, výslovné GO před případným kódem/buildem/testem, nezávislý ZIP M2 ponechat. **Brány oddělit:** M0-A požaduje definovaný návrh/testový scénář; M1 GO vyžaduje vlastní schválené bezpečné uložení a syntetickou práci, **nikoliv automaticky již provedený Android D2D/cloud test** – ten potřebuje dostupnou testovací aplikaci nebo samostatně povolený experiment; zhodnotí se před produkčním povolením konkrétní systémové cesty a nenahraditelnými reálnými daty. **Produkční výchozí stav D2D/cloud OTEVŘENÝ, oba fyzické testy NOT RUN.**

## 4. M1 – první skutečný offline pracovní celek

**NOVĚ SCHVÁLENÝ PRODUKTOVÝ KONTRAKT SAVE/RECOVERY (22. 11. 2026; PR #3 §6.1.1, PR #4 ROZ-41):** fotografie, kóty, poznámky a potřebné přílohy tvoří jediné logické potvrzení; `ULOŽENO` teprve po ověření úplné revize. Chyba zápisu v běžícím editoru zachová rozpracované změny se stavem `NEULOŽENO` a akcí `Zkusit znovu`; po pádu otevřít poslední validní potvrzenou revizi a bezpečně zachránitelné novější rozpracované změny případně nabídnout k nedestruktivní obnově. **Nikdy automaticky nesmazat poslední funkční potvrzenou práci kvůli místu.** Fyzické garance procesu/power-cut a přesné implementační řešení NOT RUN; bez dalšího GO nedeklarovat testovací PASS.


**Rozsah:** vybrat/pořídit fotografii a v CraftPlanu **bezpečně trvale uložit jednu obrazovou verzi právě zvolené kvality podle novějšího [PR #6](https://github.com/zaplikj30-ui/CraftPlan/pull/6)**, bez automatického dalšího snímku v systémové Galerii; externí importní originál nezměnit; vložit a přesně editovat kótu, ovládat úchopy/gesta prstem a perem včetně zoom/pan, Undo/Redo, bezpečně uložit, ukončit/přerušit a znovu otevřít poslední platný stav. Rychlé zaměření bez zákazníka zůstává; zakázky přijdou v M2.

**Od začátku malá volitelná místní diagnostika** bez zákaznických dat, s ověřenou režií a možností prohlížení, výběru a sdílení/mazání záznamů v rozsahu schváleného funkčního kontraktu. Nedělat velký automatický testovací framework.

**HOTOVO / důkazy M1 (budoucí, NOT RUN):** přesná čísla/jednotky včetně 1/16 in, **byte-for-byte ověření jediné skutečně uložené verze fotografie podle PR #6** (není požadavek na další interní plný originál), externí zdroj importu nedotčený, Undo/Redo a gesta, pád/plný disk bez falešného ULOŽENO, bezpečný reopen, výkon mnoha tahů a diagnostika bez zásahu do ukládání. Automatické testy nenahrazují fyzický Android. Autorský SOURCE/SECURITY review, cílené nezávislé review, fyzické scénáře i vlastníkovo GO zůstávají povinné.

**Omezení:** pouze syntetická/nahraditelná data; žádné nenahraditelné zakázky, účty, sync, server, automatický import dat ze v1 ani plošná implementace budoucích funkcí. Fotografie PR #6 již produktově rozhodnuty: Plná/Plná při první instalaci, jedna uložená fotoverze v aplikaci bez automatické kopie do Galerie. Před M1 GO návrhově uzavřít a nezávisle posoudit bezpečné save/recovery a **testovací plán ROZ-47**; fyzické D2D/cloud testy **nejsou podmínkou pro samo syntetické M1**, potřebují oddělené GO a reálnou testovací instalaci. **Před reálnými nenahraditelnými daty M2 musí být ověřen náš vlastní přenosný ZIP a restore.** Produkční systémový cloud zapnout pouze po výsledcích a zvláštním rozhodnutí vlastníka.

## 5. M2 – organizace práce a brána pro skutečná data

Zakázky, zákazníci, strom složek a podsložek, rychlé zaměření bez zákazníka a jeho pozdější přiřazení, galerie/náhledy, prohlížení bez nechtěných úprav, koš a bezpečné přesuny/kopie s uchováním identit a referencí.

**Před nenahraditelnými zákaznickými zakázkami** prokázat M2 přenosnou zálohu a obnovu ID, měření, kót, poznámek, souborů a vazeb na jiném zařízení bez původního telefonu. **Produktově schváleno:** uživatel vědomě vytvoří běžný ZIP **bez hesla a bez povinného šifrování** a zvolí `Uložit` do vybraného úložiště/složky, případně `Sdílet` běžnou nabídkou Androidu (dostupné aplikace např. Google Disk, WhatsApp, Telegram, e-mail, Bluetooth či Quick Share podle zařízení). Odeslání i cíle potvrzuje uživatel; nic se samo nenahrává. Aplikace pravdivě upozorní, že osoba s přístupem k nešifrovanému ZIPu může jeho obsah číst. Případné heslo až jako samostatná budoucí volitelná funkce, nyní neimplementovat. Koš volitelný, první záloha předvoleno ZAHRNOUT. Kontrola integrity, úplnosti a bezpečná obnova jsou povinné; přenos jako takový NENÍ důkaz obnovitelnosti.

**HOTOVO:** zachované ID a vazby při přesunech/mazání (přesun zachovává ID, kopie nová a správně přemapovaná); obnova z koše, UX-100 automatické mazání výchozí OFF; výkon galerie s 10/100/1 000 položkami, restart, plný disk a chybějící příloha. **Test přenosné zálohy:** vytvořit → ověřit → obnovit na jiné/čisté instalaci → porovnat ID, vazby, objekty, přesné hodnoty a **byte-for-byte skutečně uložené verze všech interních fotografií**; nečekat automaticky plný externí originál, pokud byl uložen jen zmenšený podklad. Poškozený/neúplný/nepodporovaný archiv či nedostatek místa odmítnout beze ztráty práce. Otestovat i existující cílovou práci a kolize ID.

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

**SCHVÁLENO ROZ-48 / upřesnění B07 (22. 11. 2026):** zákaznické PDF má v přípravě samostatný citlivý přepínač `Zahrnout polohu GPS`, **výchozí OFF u každého nového exportu**, bez zapamatování předchozího zapnutí. Pokud ho uživatel vědomě zapne, použít jen skutečně dostupné GPS u vybraných fotografií; při OFF žádná GPS v PDF ani skrytě v metadatech/vložených obrázcích. Chybějící polohu nevymýšlet. Samostatně výchozí OFF zůstávají `Zahrnout skryté poznámky` dle ROZ-23; běžné volby PDF se pamatují. Původní interní jediná verze fotografie a přenosná M2 ZIP záloha zůstávají nedotčené. **Viditelná poloha na stránce PDF je samostatný dosud neschválený rozsah**, konkrétní multi-foto metadata / ověření PDF = OPEN/NOT RUN; bez GO pro implementaci.


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
- **B1 rozšířené pozdější cloudové zálohování** a **B2 živá synchronizace** jsou oddělené od **minimální přenosné zálohy M2**; cloud až s prokázanou lokální integritou, ID/revizemi, konflikty a samostatným GO. Požadavek J11 na budoucí telefon/tablet a případný počítač je pokryt stabilními ID, revizemi a portovatelným formátem v M0; **v M1 žádná síťová synchronizační infrastruktura**.
- **F10/F13 hlasové diktování a případná volitelná AI:** potvrzené UX-101 = souvislá uživatelsky zahájená relace s více kótami a společnou kontrolou „Potvrdit vše“; čísla před potvrzením zůstávají neověřená a bez zveřejnění jako skutečná měření. Zkoumat jednoduché krátké výroky před vlastním hlasovým enginem. AI nesmí vyrábět skutečně naměřené hodnoty z pixelů.
- **F33/F34 pracovní čas a obchodní funkce, F36/F37 zákaznický režim/sdílení, další F01–F39, internacionalizace a moduly:** podle produktové priority, soukromí, bezpečnosti a nákladů. V matici požadavků neztratit **zvuk/video přílohy, přepínače viditelnosti nástrojů a offline nápovědu**. **F07 a F32 zůstávají odložené**, nikoli bez povolení znovu naplánované.
- **Windows/iOS/iPadOS:** před závazným termínem portu samostatná proveditelnost myši/pera, souborů, Bluetooth, balení, ceny a reálně sdíleného jádra/UI. Samotný obecný příslib frameworku není důkaz funkčnosti aplikace.
- **Konfigurátor–Nářezák a Převaděč spojitosti:** samostatné projekty; případná integrace jen po novém rozhodnutí vlastníka.

## 9. Kontrola a režim práce

**Před významnou implementací:** jasný kontrakt, vstupy/výstupy, testy, hranice, aktuální HEAD/base, samostatná větev/PR a odpovídající GO. Autor provede po změnách skutečné testy a vlastní důkladné SOURCE/SECURITY review celého kumulativního diffu, opraví chyby a zopakuje kontrolu na finálním SHA. Jiný chat/osoba potom provede nezávislé review s verdiktem **APPROVE SOURCE / CHANGES REQUIRED / STOP**. **Zdrojový PASS není fyzický PASS.** Merge/Actions/APK/vydání/placený běh jen po samostatném výslovném GO.

**TEĎ:** sjednotit M0-A se všemi novějšími UX a foto rozhodnutími, vypořádat nálezy kontrolorů a připravit srovnatelná testovací kritéria; **neopakovat celou již hotovou rešerši bez důvodu**, ale změněné revize kontrolovat.  
**POZDĚJI:** samostatně schválené experimenty M0-C, rozhodnutí M0-D, environment pro schválené technologie, první implementace M1.  
**NEMĚNIT:** Zaměřovač 1 a jeho data, HOME runner, žádné neodsouhlasené CI, APK, merge ani náklady.

## 10. Otevřená rozhodnutí a známé rozpory

1. **Technologie:** historická tvrzení v AGENTS/START_ZDE/Stromu vývoje a dalších dokumentech PR #1 byla v tomto kole dokumentačních oprav **výslovně aktualizována**: nejnovější rozhodnutí vlastníka stanoví **žádná technologie, renderer ani DB zatím NEVYBRÁNY**. Kotlin/Compose je kandidát, ne GO. [Aktuální PR #1](https://github.com/zaplikj30-ui/CraftPlan/pull/1).
2. **Pořadí M2–M5:** původní strom dokumentoval starší pořadí; po pozdějším souhlasu vlastníka je tento návrh **aktuální pracovní pořadí** s minimální přenosnou zálohou v M2 před nenahraditelnými zakázkami a časným F35. Konkrétní rozsah pozdějších podetap i případný významný odklad zůstávají předmětem samostatného rozhodnutí vlastníka.
3. **Reprezentace míry – produktově UZAVŘENO ROZ-42:** kanonická přesná desetinná délka v mm bez roundingu uložené hodnoty, původní zápis/jednotka odděleně a `0` ≠ `NEZMĚŘENO`. **Technicky OTEVŘENO:** číselné limity, kompatibilní exact-decimal knihovna/API, neukončitelné desetinné zlomky, samostatné plochy a úhly, přetečení, testy a fyzický runtime PASS. Mikrometry nejsou alternativním schváleným kanonickým produktem.
4. **Undo/Redo v M1:** ROZ-09 dovoluje po restartu zapomenout Undo/Redo, ne potvrzené objekty. Foto PR #6: jedna interní verze, výchozí Plná/Plná. Autorské návrhy PR #3 §4.1.1, §6.1.1 a §7.1.1 (přesnost, commit/recovery, M2 manifest) čekají na cílené nezávislé review a fyzické důkazy až po GO. **Schváleno:** každý nový PDF export začíná se skrytými poznámkami OFF, běžné PDF volby se pamatují. Záloha je ZIP bez hesla; uživatel volí Uložit/Sdílet pomocí Androidu, včetně WhatsApp a Google Disk podle dostupnosti aplikací. Budoucí volitelná ochrana heslem není nynější požadavek. Otevřené jsou technická disková garance, konkrétní API a M0-D technologie.
5. **Neověřeno:** všechny runtime výkonnostní hodnoty, bezpečnost při fyzickém výpadku, dostupnost konkrétního Bluetooth profilu, stitching panoramat, kompatibilita Windows/iOS a náklady konkrétních knihoven.

**Tento dokument je aktuálním návrhovým vodítkem pro pořadí M0–M5 a je propojen s upravenými pravidly PR #1.** Zůstává DRAFT bez implementačního GO, bez uzavřené M0-A a bez výběru technologie.

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
