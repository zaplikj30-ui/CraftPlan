# Zaměřovač 2 – schválená funkční podoba F01–F05

> **CraftPlan (historicky „Zaměřovač 2“).** Tento dokument je součástí aktuálního produktového zadání CraftPlanu. Pochází z [PR #234 původního Zaměřovače](https://github.com/zaplikj30-ui/Zamerovac-android/pull/234); odkazy na jedničku jsou historické reference, nikoli pokyny ke kopírování starého kódu nebo workflow. Platí [hlavní pravidlo zdroje pravdy](ZAMEROVAC_2_JEDNOTNY_ZAKLAD_A_PRENOS.md).

**Záznam rozhovoru s vlastníkem 21. 11. 2026.** Závazný **funkční záměr** pro budoucí etapy Zaměřovače 2, **NE** hotový produkt, schválení implementace, technická proveditelnost či Android PASS. Přesné detaily doplňuje [katalog](ZAMEROVAC_2_KATALOG_FUNKCI.md), [inventura](ZAMEROVAC_2_INVENTURA_POZADAVKU.md), [registr otevřených otázek](ZAMEROVAC_2_NEDORESENE_NAPADY.md) a [F03 laserový návrh](ZAMEROVAC_2_LASERY_F03_NAVRH.md).

## Společné zásady všech pěti bodů

- **Jedno společné jádro editoru a údajů**, nikoli kopie pro panorama, půdorys a nárys. Uložená měření jsou přesná čísla s jednotkou a zdrojem (ručně / ověřený laser); **nikdy je neodvozovat z pixelů**. Výpočty, model, kresba, obrazovka a hardware oddělené; budoucí iOS/Windows bez předčasné implementace multiplatformního frameworku.
- **Původní fotografie/media nedestruktivně**, editovatelné kóty, značky a poznámky samostatně; stabilní ID a vztahy v zakázce. Zachovat Undo/Redo, offline práci, ukládání a obnovení, zálohu/import/export. Přerušení, plný disk a nejasné vztahy nesmí zničit existující práci.
- `Nastavení → Přizpůsobení nástrojů`: jednotlivé položky **jen zobrazit/skrýt**, ne smazat související data ani zabraňovat bezpečnému čtení již vytvořených dokumentů. F01 a F02 **dva nezávislé přepínače**, F05 **není samostatný přepínač** – patří pod F04 Půdorys. **Výchozí viditelnost nástrojů odložena** až na návrh UI; běžné nástroje editoru nemají být bez důvodu skryté. `Experimentální funkce` mají vlastní oddělené dobrovolné zapínání neověřených novinek, nemohou omlouvat chyby dat.
- Bosch MeasureOn a ImageMeter jsou **funkční reference, ne zdroj kódu, grafiky ani kopie vzhledu**. Návrhy ověřovat na reálném Androidu, než bude funkcionalita označená jako fyzicky funkční.

## F01 – horizontální panoramatická fotografie místnosti

**Potvrzené chování:** uživatel **fotografuje přímo v Zaměřovači 2**, aplikace ho **postupně navádí** při sérii překrývajících se **vodorovných snímků kolem celé místnosti** a výsledné fotografie složí do jednoho **otočného 360° panoramatu**. Nesmí záviset na výrobcem dodaném panoramatickém režimu fotoaparátu. Zároveň **importovat již hotové panorama**. Nemusí fotografovat strop a podlahu. Jde o „**nekonečný film**“: při pohybu doleva/doprava obraz přijíždí z kraje, uprostřed se přirozeně čelně srovnává, ne fisheye či roztažený dlouhý obrázek. Plynulý zoom dvěma prsty, případně +/−.

**Galerie:** fotografie/panorama se prohlíží a otáčí, již vytvořené kóty a poznámky jsou vidět, **bez nechtěných editací**. **Editor:** otáčení a přiblížení zůstávají dostupná a navíc lze tvořit/měnit kóty; tažení prázdnou oblastí pohled otáčí, uchopení kóty ji edituje – potřeba ergonomického prototypu, ne povinného dalšího přepínače režimů. Kóty ukotveny vůči panoramatu i přes šev 0°/360° a po znovuotevření, naměřené hodnoty nezávislé na projekci.

**Poznámka s detailní fotografií:** konkrétní místo (např. okno či zásuvka) může mít fotografickou poznámku/několik detailních fotek; využít **stávající typ poznámek a příloh**, nevytvářet pro panorama další datový systém.

**Technicky ověřit později:** kompatibilitu průvodce a stitching různých telefonů, kvalitu spojů, zobrazení stěn bez nadměrného zakřivení (kandidát perspektivní výřez z cylindrického panoramatu), šev, zoom/kóty/výkon, uložení/export a obnovu. **Nepředstírat HOTOVO**.

## F02 – plná sférická 360° fotografie

**Potvrzené chování:** obdobné F01 a se sdílenými částmi editoru, ale pohled může směřovat **doleva/doprava, nahoru/dolů a případně měnit náklon**. Plné pokrytí = 360° vodorovně a 180° svisle; má-li být vidět strop a podlaha, musí být také skutečně nafocené. Přirozený výřez, zoom, přesné ukotvené kóty, poznámky s fotopřílohami. **Není to skutečný 3D geometrický model ani procházení místností** – stanoviště pozorovatele zůstává jedno.

**Zapínání:** F02 má svůj přepínač nezávislý na F01. Obě nebo jen jedna mohou být viditelné; skrytí nic nemaže.

**Ověřit později:** vedené pořízení celé sféry nebo import plné/částečné foto-sféry, švy na pólech, kompatibilitu, úpravy kót při otáčení ve všech osách, výkon/export. Nepředpokládat, že pořízení plné sféry už bylo realizováno.

## F03 – laserové dálkoměry různých výrobců

**Potvrzený cíl:** postupně **co nejširší podpora modelů a značek**; uživatel nemá vyhledávat protokoly ani kupovat všechny lasery. V jedné nabídce `Připojit měřidlo` zvolit podporovaný laser, přesná naměřená hodnota správné veličiny/jednotky se přiřadí k aktivní kótě; ruční vstup je stále možný. Zachovat historii a Undo/Redo.

**Technický směr:** jeden typ ověřené události měření, malé adaptéry **podle skutečného BLE/Classic/HID/modelového protokolu**, případně legálně použitelné oficiální API výrobce. Veřejný seznam podporovaných laserů ImageMeter je **mapa kandidátů**, ne důkaz podpory Zaměřovačem. Nevyvíjet plugin engine „pro jistotu“. Každý protokol potřebuje vlastní doloženou validaci jednotek a typu měření; **neznámé/duplicitní/zpožděné/chybné údaje nesmějí tiše přepsat kótu**.

**Stav:** pořadí dalších modelů později podle dokumentace a možnosti testu. Softwarový dekodérový PASS ≠ skutečný test měřidla na telefonu; nikdy netvrdit fyzickou podporu bez konkrétního důkazu. Detailní plán a kandidáti v [samostatném návrhu F03](ZAMEROVAC_2_LASERY_F03_NAVRH.md).

## F04 – půdorys místnosti a editovatelné stěny

**Potvrzené chování:** v zakázce `+ → Vytvořit půdorys`; volný náčrt stěn prstem/S Penem se **jemně narovná na editovatelné úsečky**, bez násilného vnucování všech úhlů na 90°. **Magnet přichytávání lze zapnout/vypnout**. Přiblížení nového konce k existujícímu rohu (včetně poslední čáry k prvnímu) nabídne při magnetu spojení, nikoliv nové automatické stěny. Nepravidelné místnosti a šikminy zůstávají.

**Pomocný kříž:** tenké vodorovné a svislé **dočasné vodítko vycházející z právě taženého bodu** (nikoli mnoho křížů od uložených rohů) zvýrazní zarovnání s relevantním uloženým rohem/úrovní. Magnet vypnutý = vodítko může orientovat, **neposouvá body**. Pomocné čáry nejsou uloženou geometrií.

**Úpravy:** uchopit existující roh, posunout ho a ponechat napojené stěny spojené – například obdélník → lichoběžník. **Vložit další lomový bod doprostřed stěny**, táhnout ho do strany a rozdělit stěnu na dva propojené segmenty, tedy výklenek/zalomení. Bod půjde měnit či odstranit. Půdorys zůstává editovatelný i po uložení.

**Rozměry:** kóty zadávat ručně nebo laserem; neodvozovat čísla z tahů. Při rozdělení stěny **nezměnit ani automaticky nerozdělit původní přesné měření**; v případě nejasného vztahu nabídnout opětovné přiřazení. **Změna délky stěny ze 3 na 4 m nesmí bez výslovného rozhodnutí automaticky přestavět zbytek půdorysu.** Nárys, fotky a dveře navázané na stěnu neztratit.

## F05 – vnořená součást F04: práce s vybranou stěnou

**Potvrzené zařazení:** F05 **není další samostatně zapínaná funkce**. Je to pracovní/testovací podbod půdorysu F04. Uživatel **vybere stěnu v půdorysu**, zobrazí se **volba, jak s ní pracovat**; F05 nemá být povinná pro běžné nakreslení půdorysu.

**Volby, které lze kombinovat pro tutéž stěnu:**

1. **Jen půdorys**, bez dalšího kreslení stěny.
2. **Nárys / hrubý nákres stěny zepředu** na editovatelném 2D plátně: okna, dveře, výšky, překážky, voda/odpad, zásuvky/elektřina a **libovolné volné čáry, náčrty a symboly**. Přesné kóty polohy okna/dveří od levé nebo pravé strany, od podlahy, šířka/výška; ručně nebo laserem. Na půdorysu má být odpovídající značka okna či dveří.
3. **Vyfotit nebo nahrát fotografii celé stěny a kótovat přímo přes ni**, místo povinného kreslení prázdného nárysu. To je **výslovně potvrzená alternativa**, nikoliv dosud nerozhodnutý nápad. Využít běžný editor fotografií a bez změny originálních bajtů.
4. **Připojit více detailních fotografií** k celé stěně či konkrétnímu prvku (oknu, odpadu, zásuvce) jako existující poznámky/fotopřílohy. Nárys, kotovaná fotografie a další detaily mohou **existovat současně**, nevybírá se trvale jen jeden pohled.

**Datové vazby:** konkrétní stěna má **stabilní ID**, všechny její nárysy, okna, dveře, kotované fotky, detailní poznámky a kóty se vážou na stěnu a podle potřeby konkrétní bod/objekt. Změna, vložení zlomu či rozdělení stěny **nesmí tiše smazat ani osiřet data**. Pokud je přiřazení nejednoznačné, uživatel rozhodne, kam vazbu přesunout; bez ztráty historie. 2D nárys s kótami není automaticky skutečný 3D model.

## Co ještě **není** rozhodnuté (neblokuje tento návrh)

- F01/F02: přesný stitching/renderer, podpora konkrétních telefonů, zorný úhel, kompatibilita a ověřené UI gest.
- F03: přesný pořádek modelů a dostupnost protokolů, fyzická kompatibilita; žádný nákup nyní.
- F04: gesto pro vložení lomového bodu, přesná tolerance přichytávání, počet zobrazených referenčních bodů, případný samostatný přepínač vizuálních vodítek; hranice změny měřené délky a geometrie.
- F05: přesná podoba kontextové nabídky stěny, gesto odlišující výběr stěny od tažení rohu, způsob práce s několika fotografiemi a přenos detailu při rozdělení stěny.
- Zobrazení volitelných pokročilých nástrojů po první instalaci; přehledné a vyzkoušené uspořádání nastavení.

**Stav a brány:** F01–F05 jsou **funkční záměry**, žádné „5/5 implementováno“. Vhodné samostatné malé prototypy a řízené etapové implementace se SOURCE/SECURITY vlastní kontrolou, nezávislou kontrolou a GO vlastníka. **NE** spuštění Actions, HOME, merge či vydání APK tímto dokumentem.
