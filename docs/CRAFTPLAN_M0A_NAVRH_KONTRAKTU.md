# CraftPlan – M0-A: návrh kontraktů dat, geometrie a ochrany práce

> **STAV: PRACOVNÍ NÁVRH / K NEZÁVISLÉMU DESIGN/SECURITY REVIEW.** Není to schválené implementační zadání ani HOTOVO M0-A.  
> **Datum:** 22. 11. 2026 · **Rámec:** vlastníkem schválený [hlavní plán stavby M0–M5](https://github.com/zaplikj30-ui/CraftPlan/blob/68646525fabadc6acd51d57e21184026a95cf7c9/docs/CRAFTPLAN_HLAVNI_PLAN_STAVBY.md) a nezávislé APPROVE DESIGN [PR #2](https://github.com/zaplikj30-ui/CraftPlan/pull/2).  
> **Povoleno:** jen příprava dokumentace M0-A. **Nepovoleno:** kód, build, Actions, HOME, APK, merge, výdaje, úprava původního Zaměřovače 1.  
> **Technologie/renderer/databáze/čísla:** **NEVYBRÁNO**. Starší preference Kotlin+Compose v [AGENTS](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/AGENTS.md)/START_ZDE jsou překonané novějším rozhodnutím vlastníka.

## 1. Úplné zadání, ne pouze 49 bodů

Pro návrh M0 se povinně použije **[úplná kontrolní matice](CRAFTPLAN_M0A_MATICE_NAVAZNOSTI.md)**: **12 J-oblastí, všech 39 F01–F39, všech 49 historických dílčích kontrol a 28 historických uživatelských testů**, dále [živý návrh](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_ZIVY_NAVRH.md), [registr dalších návazností](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_REGISTR_NAVAZNOSTI_V1.md) a původní detaily. **Matice zachovává odkazy; její krátký název nenahrazuje úplné podrobné funkční zadání.** Budoucí funkce nemusejí vzniknout hned, ale jejich **nároky na identity, souřadnice, měření, média, vazby a obnovu** nesmí budoucí vývoj nutit bourat základy.

**Výslovně posoudit:** F01/F02 panoramata a sféry, F03 lasery, F04/F05 půdorys a nárys, F09 výměnu podkladové fotografie, F11 odvozené kóty, F35 instalační plány, F36 oprávnění zákazníků a F37 sdílení. samostatné porovnání dvou fotografií F07, F31 a F32 jsou odložené; vazba fotografie na stěnu patří do F04/F05, F23/F24/F25 patří Konfigurátoru–Nářezáku; nic z toho nepřebírat jako implementační GO.

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

### 3.1 Další typové hranice pro budoucí funkce – nepředbíhat jejich implementaci

**F14 knihovny symbolů:** rozlišit **definici vestavěného odborného symbolu**, **definici uživatelského obrázkového symbolu** a **vložení/instanci symbolu do dokumentu**. Definice má stabilní identitu nezávislou na pořadí katalogu; každá instance má své vlastní ID, polohu, vzhled a odkaz na konkrétní definici. Vestavěnou definici uživatel nepřepisuje; uživatelská knihovna je oddělená. Pokud definice chybí nebo byla odstraněna z katalogu, uložená instance a původní importované médium se **nesmějí tiše ztratit**, zobrazí se bezpečná náhrada/oznámení a opravitelné přiřazení. **KANDIDÁTNÍ bezpečná vstupní hranice:** import ověřených JPG/PNG/WebP jako neaktivních obrazových dat, kontrola deklarovaného i skutečného typu/limitů a bezpečné dekódování; **nepřijímat spustitelný HTML/SVG/JS obsah jako obyčejnou značku**. Konkrétní nabídka značek, jejich licencování a vizuální podoba patří až do F14. [Podrobná F14](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md#L178).

**F14 verze knihovny a přenos symbolu:** ID definice **není** její pořadí ani jediná záruka stejné podoby. Použitá instance musí mít odkaz na **verzi definice či vlastní bezpečný zobrazitelný otisk**, aby změna významu/vzhledu katalogu bez schválení **nezměnila historický výkres**. V editovatelném ZIP/obnově se přenese minimálně nutná bezpečně otevřitelná definice nebo její původní raster s ID a verzí; přijímající instalace bez knihovny nesmí symbol potichu zahodit ani nahradit jiným významem. Konkrétní grafické značky/licence později, **ne plugin engine**. [F14](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md#L178).



**Více médií v jedné poznámce:** jedna poznámka má vlastní ID a může mít **seřazených více fotografických příloh** se samostatnými ID; jejich editovatelnost/vazby a originální bajty nezávisí na první náhledové fotografii. Náhled „stoh s počtem“ je zobrazovací pravidlo, nikoli jiný objekt. Samostatná hlasová příloha zůstává jiným typem média. [Historické rozhodnutí D](https://github.com/zaplikj30-ui/Zamerovac-android/blob/8a36e353448ba74a066e16372441604b7e7163c7/ROZHODNUTI_A_NAPADY_Z_TESTU.md#L40).

**Hromadná práce a jednotlivý editovatelný export:** M2 bude potřebovat jednoznačně vymezenou množinu vybraných ID pro **kopírovat / přesunout / smazat / ZIP**, včetně kontroly zámků jednotlivých položek i cílové složky; částečný úspěch nesmí být skrytý. Vybraná kopie dostane nové ID, přesun zachová původní; ZIP jedné fotografie zahrne originál, editovatelné anotace a všechny její přílohy, s verzí a bezpečným opětovným importem jako **kopie nebo jiná výslovná volba**, ne tajný přepis. Historicky schválené UI výběru a odznak „upravené foto“ jsou odlišné prezentační požadavky. [A–C](https://github.com/zaplikj30-ui/Zamerovac-android/blob/8a36e353448ba74a066e16372441604b7e7163c7/ROZHODNUTI_A_NAPADY_Z_TESTU.md#L14).

**F08 adresy a původ údajů:** kontaktní/fakturační adresa **zákazníka**, adresa **zakázky** a nepovinná adresa **fotografie** jsou rozdílné údaje. Případný návrh GPS/geokódování či registru musí mít zdroj, stav **NAVRŽENO vs. UŽIVATELEM POTVRZENO**, správný cíl a možnost odmítnout. Změna návrhu nesmí bez potvrzení přepsat ruční opravu; žádná povinná GPS ani síťové geokódování v M1. [F08](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md#L96).

**F33/F34 – budoucí čas a peníze nejsou geometrická míra:** model musí dovolovat samostatně identifikované úseky práce/pauzy a potvrzený Stop **konkrétního aktivního časovače** včetně bezpečného chování po restartu, oddělený Start/Stop případné GPS jízdy a vždy dostupnou **ručně potvrzenou vzdálenost dopravy**. Cena má mít **přesnou částku, měnu, schválená pravidla zaokrouhlení a původ sazby/položky**; vydaná faktura uchová **neměnný otisk odběratele, adresy, položek, cen, stavu, data a revize**, ne živé hodnoty změněné pozdější úpravou zákazníka/ceníku. Konkrétní časovač, fakturační UI, předpisy a GPS adapter jsou pozdější rozhodnutí. [F33](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md#L345) · [F34](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md#L359).

### 3.2 Zámky a zapisující příkazy – CP3-11

**Zámek složky je pravidlo ochrany práce, nikoli pouhá šedá ikona.** Jeden logický kontrolní bod při každé operaci, která mění obsah/umístění: ověřit příslušný **zdroj, cílový kontejner a všechny relevantní zamčené předky**, včetně samostatné editace/přesunu, vícenásobného výběru, přesunu do koše, obnovy z koše a importu/restore do již existujícího stromu. Při zámku včetně kolize s jiným objektem bezpečně odmítnout/požádat o odemčení; neprovádět skrytý částečný zápis, anebo jasně předem popsat a po akci přesně hlásit každou úspěšnou/neúspěšnou položku, s možností bezpečného Undo. Povolený export ZIP nesmí znamenat možnost obejít ochranu proti změně obsahu. Zámek složky **není** zabezpečení aplikace F30 ani síťová role F36. [Historické testy 21/25](https://github.com/zaplikj30-ui/Zamerovac-android/blob/8a36e353448ba74a066e16372441604b7e7163c7/REKONSTRUKCE_TESTU_21_28.md#L63).

### 3.3 Schválené ovládací detaily mimo čísla F/49

**Nesmí se zaměnit** (1) zkratka pro **rychlé opakování posledního úspěšného přidání v přehledu**, (2) **jednorázové kreslení** a (3) samostatný **dlouhým stiskem aktivovaný opakovaný kreslicí nástroj**. Opakování poslední akce nevytváří zbytečný trvalý odkaz na předchozí zakázku; při nevhodném kontextu nic tajně nevložit. **Duplikace celé kóty je odložena**, ne implementační požadavek. Dlouhý stisk může kolidovat s přeskupováním ikon a musí dostat pozdější fyzický UX test. [Novější rozhodnutí v živém návrhu](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_ZIVY_NAVRH.md#L310).

**Úchopy E / regresní chování:** koncový/rohový úchop upravuje jen konec/roh, střed přesouvá celek, popisek se přesouvá samostatně; při překryvu zón má přednost konec. Nepřepisovat nyní uživatelsky vyhovující ergonomii jen podle starého slovesa „opravit“. Čitelnost rotovaného textu kóty je zobrazovací pravidlo bez změny míry. Pozdější oddělené testy prst/S Pen, citlivost, „Lichoběžník“ vs. „Obdélník“ a správný název „Směřovat“. [Historické E](https://github.com/zaplikj30-ui/Zamerovac-android/blob/8a36e353448ba74a066e16372441604b7e7163c7/ROZHODNUTI_A_NAPADY_Z_TESTU.md#L47) · [Novější potvrzení](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_KATALOG_FUNKCI.md#L47).

## 4. Přesná čísla, jednotky a měřicí kontext

**POTVRZENÉ:** číselný údaj a původ nejsou odvozené z pixelů, délky kreslené úsečky ani zaokrouhleného popisku. Pro každé měření uchovat veličinu (délka/plocha/úhel…), jednotku, přesnou hodnotu, původ `RUČNÍ/LASER/VÝPOČET/IMPORT`, ID a stav ověření. Bluetooth samo nezaručuje, že jednotka a údaj jsou správné.

**KANDIDÁTI datové reprezentace:** kanonický přesný desetinný text, případně přesný koeficient/exponent nebo racionální zápis se zlomky; rozhodnout podle požadavků, platformy a testů. **Samotné celočíselné mikrometry nejsou obecně přesné:** `1/16 in = 1,5875 mm = 1 587,5 µm`. `Float/Double` může pomoci s obrazem, ne jako **jediný zdroj pravdy** rozměru; nepřipustit přetečení plochy. Vybraný přenosový formát nesmí vyžadovat pouze JVM.

**Návrh stavů (OTEVŘENO pro názvy/přechody):** `NEZMĚŘENO`, `ZADÁNO`, `OVĚŘENO`, `NEJASNÁ JEDNOTKA`, `NEPŘIŘAZENO`. Česká čárka a anglická tečka se zpracují pouze podle jednoznačně známého locale; **vnitřní formát dat na locale nespoléhá**. Převody a výpočty mají definovaná pravidla přesnosti, hranic a vědomého zaokrouhlování; zobrazení v jiné jednotce nikdy potichu nemění uloženou hodnotu.

- Posunutí kóty o 20 pixelů mění její geometrické kotvy, **ne naměřenou míru**.
- **F11 je jednorázově potvrzený snímek součtu 2–N uživatelem vybraných existujících kót**, nikoli živá formule: vytvořená kóta dostává **vlastní stabilní ID, přesně spočtenou potvrzenou hodnotu, jednotku, plnou editovatelnost a Undo/Redo**. ID vstupních kót lze uchovat jen jako **doložení původu**, ne jako automatickou závislost. **Pozdější změna nebo smazání zdrojové kóty NESMÍ změnit výsledek**, ledaže vlastník později výslovně schválí vědomý přepočet. Nabízená poloha nové kóty je pouze vizuální návrh; uživatel upravuje umístění/úchopy, nikoli původní naměřená čísla. [F11](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md#L145).
- Neslučitelné veličiny (např. délka + úhel), neznámá jednotka, `NaN`, nekonečno a mimo-rozsahové hodnoty se odmítnou.
- Opožděná hodnota laseru se nesmí přiřadit jiné právě vybrané kótě; příjem měření a vzdálené spuštění měřidla jsou **oddělené schopnosti**.
- Při změně topologie F04 se nesmí z měřené délky vytvořit dva odhady délek z kresby.

**TEST PŘIPRAVIT:** `850,5 mm` po uložení/reopen; přesný převod `1/16 in ↔ 1,5875 mm`; velké plochy a zlomky; několikeré opakované zobrazení v jiných jednotkách bez driftu; odmítnutí neznámé jednotky z laseru.

### 4.1 Sémantika hodnoty a jejího cíle – dopracování CP3-04/05

**Samostatné údaje, které nesmějí splynout:** (1) druh veličiny `LENGTH/AREA/ANGLE/TIME/MONEY/...` (samotná délka není obrazová délka), (2) přesné číslo **nebo chybějící číslo**, (3) jednotka této veličiny, (4) **původ skutečného měření** (ruční odečet, identita přístroje / model a protokol, nebo uživatelsky potvrzený výpočet), (5) **cesta vstupu** (psáno, diktováno, importováno, předáno přes Bluetooth) a původní nepozměněná přijatá reprezentace, (6) identita zdrojové události a původního výpočtu, (7) validační stav čísla/jednotky a **samostatně** stav přiřazení a odborného ověření. **Import není automaticky původ měření.** Stav `NEZMĚŘENO` není 0; nula je přípustná pouze jako výslovně zadaná, veličinově platná **číselná hodnota**. Nepřijmout neznámou jednotku, dvojznačný desetinný vstup či aritmetický overflow jako „0“.

**PŘED SCHVÁLENÍM KONTRAKTU rozhodnout:** podporované fyzikální veličiny a jednotky pro M1 a pozdější nástroje, intervaly a počet platných číslic, přesnost zdroje a pravidla předávání, sčítání/násobení, zaokrouhlování hodnoty vs. pouze zobrazení a reprezentaci přesných zlomků (např. `1/16 in = 1,5875 mm` bez ztráty). `Float/Double` nepoužívat jako jedinou autoritativní míru; **nevybírat zatím datový typ ani technologii**. Čtyři délky stran nepravidelného „Lichoběžníku“ obecně **nepostačují k určení jediné skutečné plochy** (vyžaduje doplňující geometricky určený údaj a pravidlo). „Obdélník“ má odlišně dvě nezávislé potvrzené hodnoty šířky/výšky; grafický úhel/sklon z náčrtu je orientační geometrie, ne automaticky reálně změřený úhel. [Podrobné historické zadání](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_KATALOG_FUNKCI.md#L60-L84).

**Událost laseru cílí na konkrétní měřicí POLE, ne jen kótu:** `dokument ID + objekt ID + stabilní ID/typ měřicího pole + očekávaná verze/kontext požadavku`; rozlišit délku jednotlivého úseku dimenzního řetězce, úsek od nuly kumulativního řetězce, rameno, šířku/výšku, průměr Ø vs. poloměr R a další správně typovaná pole. Přijmout jen po jednoznačné validaci typu veličiny, jednotky, stále platného cíle a potvrzení vhodnou uživatelskou akcí/předem schválenou politikou. **Změna aktivního pole i na témže objektu**, rozdělení segmentu, opožděná zpráva, reconnect/duplicita a nezáměrná změna stránky nesmějí přesměrovat hodnotu; nepřiřazený údaj lze ponechat v bezpečné historii měření, pokud ji pozdější kontrakt výslovně dovolí, jinak odmítnout s vysvětlením. Ruční zadání vždy zůstává. **Příjem a vzdálené spuštění** jsou nezávislé schopnosti konkrétního přístroje/protokolu/firmwaru/OS. [F03](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_LASERY_F03_NAVRH.md#L30).

### 4.2 Budoucí hlas/AI – návrh nikdy není potvrzené měření

Rozlišit **(a) dočasný návrh grafické kóty**, **(b) uživatelem potvrzený editovatelný grafický objekt**, **(c) samostatně potvrzenou přesnou naměřenou/ručně zadanou hodnotu**. Minimální průchod: povel či rozpoznaná hrana → dočasný návrh (možná bez hodnoty) → náhled/kontrola jednotky a bodů → **potvrdit / opravit / odmítnout** → validovaný běžný příkaz editoru → Undo/Redo/save. Odmítnutí nebo chyba nikdy nezapisují dokončenou kótu. **Bez skutečné hodnoty zůstane „NEZMĚŘENO“, nikoli nula, odhad či hodnota z pixelů.**

Zachovat tři **oddělené** budoucí scénáře: (1) prosté české diktování skutečné hodnoty s hlasovým zopakováním čísla a vědomým potvrzením (otevřeno přímé umístění A vs. dočasný zásobník B), (2) AI navrhne grafické umístění, ale číslo pochází **výhradně od uživatele**, (3) „Okótuj místnost“ navrhne **nové grafické kóty bez skutečných hodnot**. AI nesmí neomezeně měnit ani mazat hotová data a každý povel musí projít stejnými oprávněními a validací jako ruční editor; běžný editor musí fungovat bez AI/sítě. Žádný AI model, server či mikrofon nyní. [Živý návrh](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_ZIVY_NAVRH.md#L337).

## 5. Trvalé souřadnice pro různé podklady

**Společné:** identita, měření, validované editační příkazy, ukládání, odkazy, Undo/Redo. **Ne společné trvalé pixely obrazovky.**

| Podklad | Co trvale uložit / návrh pravidla | Co není pravda modelu |
|---|---|---|
| **FOTO** | Kotvy ve vlastním obrazovém prostoru s explicitní EXIF orientací/mirroring transformací, odkazem na originál; **KANDIDÁT** normalizované 2D souřadnice. Konvenci os/rotací teprve uzavřít | Zoom, velikost displeje, poloha lupy, přechod na tablet |
| **F01 vodorovné panorama** | Kotvy v panoramatickém směrovém prostoru (kandidát azimut + další úhlová/polohová složka), **šev 0°/360°**, původní snímky; přesná konvence otevřená | Posunutí/„roztočení“ filmového pásu a aktuální výřez |
| **F02 plná sféra** | Referenční směr, dva úhly či ekvivalent, póly, krytí a orientace sféry; 360°×180° fotografie není prostorový model kuchyně | Pohled kamery a zoom |
| **F04 půdorys** | Vrcholy, úseky, stabilní ID stěn, topologie a **skutečné měřítko jen při doloženém měření**. U neokótované skici přiznat neznámou škálu | Magnetická vodítka, zoom, pan |
| **F05 nárys stěny** | Lokální prostor pohledu na stěnu + explicitní identita stěny, polohy oken/dveří a detailních médií; při dělení stěny zachovat vazby či označit k ručnímu rozhodnutí | Výběr a poloha UI panelu |
| **F35 instalace** | Editovatelný plán se značkami, měřenými hodnotami, **nepovinným ID zakázky (orientační šablona i bez ní)**, stavem a revizí; zvlášť vydaný PDF otisk | Přiblížení a rozvržení PDF |

**F09 výměna fotografie – přesně dva potvrzené manuální režimy:** (1) **kóty jsou pevné, pohybuje se nová fotografie**, nebo (2) **fotografie je pevná a všechny kóty se dočasně přesouvají společně**. Pracovní změna zahrnuje ID původního i náhradního originálu a oddělenou vratnou transformaci. **Hotovo** potvrdí jeden validovaný Undo/Redo příkaz a vrátí **samostatné úchopy jednotlivých objektů**, není to trvalá skupina. **Zrušit** vrátí původní obraz, kotvy a hodnoty; **oba originály zůstávají bezpečně uchované**. Nevydávat výpočet perspektivy za potvrzenou reálnou míru. [F09](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md#L106).

**F01/F02 – původní a odvozená média:** samostatné identifikované **zdrojové fotografie → odvozené/poskládané panoramatické médium → dokument a kotvy s definovanou projekcí**; navíc možnost **importovat už hotové panorama** bez zdrojových dílčích snímků. **Částečně pořízená sféra** musí přiznat neúplné pokrytí a nepřisuzovat anotace neexistujícím oblastem. Kotvy se zachovají v editoru, prohlížeči i exportu, přes šev a póly; původní jednotlivé fotografie nejsou destruktivně přepsány výstupem. Konkrétní stitching a projekci posoudit experimentem, nikoli vybrat teď. F02 není 3D model místnosti. [F01/F02](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md#L17).

**F04/F05 – tři různé pravdy:** **náčrtná/topologická geometrie stěn**, **samostatné skutečné měřené hodnoty** a **stavební prvek (okno/dveře) s jedinou identitou sdílenou půdorysem a jedním či více nárysy**; ostatní fotografie a přílohy mají vlastní ID a vazby. **Změna potvrzené kóty ze 3 m na 4 m NESMÍ bez explicitního budoucího pravidla sama přestavět zbytek půdorysu**. Okno/dveře se nesmí stát dvěma konfliktními nezávislými kopiemi. Ke stěně může existovat **více současných nárysů, kotovaných fotografií i detailních příloh**. Vložení rohu/rozdělení stěny musí zachovat ostatní měření a vazby nebo je označit jako nejednoznačné k vědomému rozhodnutí. Žádný automatický solver/3D nyní. [F04/F05](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_F01_F05_ZADANI.md#L42).

### 5.1 Fotografie, EXIF a výměna podkladu – konvence musí být jednoznačná

**NÁVRH KONVENCE K POTVRZENÍ:** jediný **trvalý kanonický prostor fotografie po normalizaci EXIF orientace**, s explicitní šířkou/výškou po této normalizaci, počátkem vlevo nahoře, osou `x` vpravo, `y` dolů; uložené kotvy se vztahují k **neměnné identitě původního média a jeho verzované mapě orientace**. Z původních bajtů a EXIF se při importu odvodí **jedna** transformace zahrnující rotaci **i zrcadlení**; náhled ani export ji nesmějí podruhé aplikovat. Náhled, výřez, zoom, rotace telefonu a render PDF jsou pouze následné odvozené transformace s inverzním převodem pro editaci; nerozhodovat souřadnice podle pixelů displeje. Ověřit všech **8 EXIF orientací**, neznámou EXIF hodnotu bezpečně přiznat, různá rozlišení, crop a round-trip se stejnými kotvami. Při F09 oba původní originály i verzované mapy/transformace zůstávají, výměna s perspektivou vyžaduje vědomou ruční korekci, nikoliv automaticky přepočtenou skutečnou míru. Konkrétní normalizaci vs. jinou rovnocennou reprezentaci uzavřít v M0; nevybírat renderer nyní.

### 5.2 F01/F02 – kotvy neodpojovat od orientace/projekce

Každá panoramatická/sférická kotva musí mít **identitu podkladu, verzi úhlové/projekční konvence, orientaci referenčního směru, stav skutečného fotografického pokrytí** a grafickou definici celého objektu (koncové body i případné mezibody), nikoli jen bod na aktuálním displeji. U švu 0°/360° musí editace umět vyhodnotit **celý objekt** a jeho úchopy; přes pól se nerozhoduje jen podle equirectangular pixelů. **Nové složení, změna projekce/orientace či náhrada hotového importu:** buď prokazatelně zachovat/přemapovat všechny kotvy s doloženou transformací, nebo označit vazbu jako neověřenou a vyžádat manuální opravu; nikdy nepřipojit anotaci k jiné stěně potichu. Prohlížeč/galerie dovolují navigaci bez změny objektu, editor rozlišuje tažení prázdné plochy a úchopu. F01 má být vedené vodorovné focení **i možnost importu hotového panoramatu**, F02 samostatná plná sféra včetně případného neúplného pokrytí; podklady mohou obsahovat stejné typy poznámek/kót, ale různé kotevní konvence. Dva samostatné funkční přepínače, nikoli hotový stitching nyní. [F01–F05](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_F01_F05_ZADANI.md#L16).

### 5.3 F04/F05 – orientace stěny a vratné vazby

Každá stěna má trvalé ID a určenou **orientaci z referenčního počátečního vrcholu do koncového**; lokální souřadnice pohledu nárysu od této strany a vůči určené referenční podlahové rovině. **Levý/pravý okraj a výška od podlahy nejsou přímo obecné souřadnice displeje.** Otočení orientace, vložení/odstranění rohu, dělení i opětovné spojení stěn musí převést odkazy na totéž fyzické okno/dveře, měření, nárysy a přílohy **jedním validovaným vratným příkazem**, pokud je převod jednoznačný; není-li, zachovat původní hodnoty a vyžádat rozhodnutí, nevybírat bez důkazu nový segment. Undo/Redo má obnovit celý související graf objektů včetně ID a příloh, po save/reopen stejný výsledek. Šikmá stěna, sdílený vrchol, magnetické uzavření, viditelnost pomocných vodítek a otočený směr jsou samostatné negativní/pozitivní scénáře; vodítko není naměřená míra. F06 je až pozdější **odvozený orientační** pohled, nikoli povinný 3D model pro F04. [F04/F05](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_F01_F05_ZADANI.md#L39).

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

### 6.4 F39 – pasivní malá diagnostika od první M1 kóty

Diagnostika je **volitelná OFF/ON, pasivní a jednosměrná**: editor směrem k ní poskytuje pouze bezpečně pojmenované události, **diagnostika nesmí zpětně měnit gesto, příkaz, stav ani výsledek uložení**. Zachytit podle možností společnou **časovou osu** se sémantickými ID akcí (např. `KOTA_BEGIN`, `KOTA_COMMIT`, `UNDO`, `SAVE_START`, `SAVE_CONFIRMED`, `REOPEN`), čas dotyku, výpočtu, renderu, potvrzeného save/reopen a omezené ukazatele janku/paměti/CPU. **Whitelist bez hodnot měření, zákaznických ID, textů, souřadnic, GPS, fotografií a médií.** Žádný podrobný log každého pointermove.

Malý ohraničený buffer v paměti, měření bez blokujícího I/O v UI vlákně; případný zápis až dávkově mimo kritickou cestu. Plný/poškozený buffer, plný disk, chyba časování nebo sdílení logu nesmí blokovat editor, potvrdit falešné uložení ani smazat platná data – **přestat logovat bezpečně**. Ručně vyvolaný bezpečný přehled/sdílení se souhlasem uživatele, **bez automatického uploadu**. Povinně porovnat stejné úlohy OFF vs. ON včetně závady logování; při výrazné regresi diagnostiku nepovažovat za splněnou. **Záznam omezené historie diagnostických sezení:** oproti čistě pomíjivému kruhovému bufferu se pro uživatelem zapnuté sezení vede **omezený a bezpečně potvrzený souhrn** i po restartu, se sémantickými ID bez soukromých hodnot. Uživatel může vybrat **více sezení**, ručně zobrazit/sdílet pouze vybrané přehledy a hromadně je smazat; výpadek diagnostiky nebo plný disk **nesmí** změnit chování uložení zakázky, může nejvýše ztratit ještě nepotvrzenou diagnostickou událost a zobrazit omezení. **OTEVŘENO:** maximální velikost/retence logů, přesná UI podoba vlaječky/panelu, samostatná potvrzovací politika pro diagnostický souhrn. T22 zahrnout do restartu, poruchy logu i OFF/ON s totožnou zátěží.

**M1 – potvrzené minimální ovládání diagnostiky:** jedna nenápadná **přesunutelná vlaječka** mimo pracovní úchopy/nástroje; její uživatelem zvolená poloha **přežije restart** a při otočení/změně velikosti obrazovky zůstane dostupná, aniž by zakrývala ovládání. Klepnutí otevře **malý panel Diagnostika**, nikoli formulář. Jasný stav `OFF/ON`, samostatné sezení při každém novém zapnutí, výchozí `OFF`. Uložená historie uvádí datum/čas a počet povolených anonymních událostí; umožňuje **vybrat jedno, více, Vybrat vše, sdílet pouze vybrané (po náhledu a výslovném potvrzení) a Smazat vybrané/Smazat vše jedním potvrzením**. Sdílení nic nemaže, smazání relací nikdy nemaže zakázky, fotografie, měření ani jiné relace mimo výběr.

**Dva odlišné limity:** krátký **nepotvrzený kruhový buffer událostí** smí při přetížení vynechat událost s přiznaným počtem výpadků; **již bezpečně potvrzené diagnostické sezení se při zaplnění retenčního limitu nesmí samo tiše vymazat**. Uživateli zobrazit limit a nabídnout vědomý hromadný úklid; při nedostatku místa **bezpečně zastavit/omezit nový záznam** s přiznaným stavem, staré potvrzené relace zachovat. Chybný log, neúspěšné sdílení nebo limit nesmí blokovat editor ani potvrzení uložení skutečné práce. Přesný počet relací, plocha vlaječky a vizuální styl zůstávají **OTEVŘENO** bez zrušení těchto funkčních zásad. [Původní potvrzené ovládání M1 diagnostiky](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_POROVNANI_S_V1.md#L150-L171).

Pokročilý profiler a samostatná TESTOVACÍ APK zůstávají odložené. [F39](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md#L407).

### 6.5 Atomický význam revize a chybové fáze – CP3-01

**NÁVRH BEZPEČNOSTNÍHO INVARIANTU:** jedna **potvrzená revize** je konzistentní graf všech souvisejících dat a dostupných originálních médií se stabilními ID; obsahuje své `revision ID` a odkaz na **očekávaného předchůdce**. Současný či opožděný writer nesmí zveřejnit novou revizi, pokud se mezitím předchůdce změnil: buď validovaně opakuje na novém základu, nebo nabídne konflikt, ale **nikdy last-writer-wins bez souhlasu**. **KANDIDÁTNÍ fáze:** (A) připravit média, (B) bezpečně zveřejnit ověřené médium, (C) konzistentně potvrdit model+revision pointer, (D) zkontrolovat pro zobrazení `ULOŽENO`, (E) až později bezpečně uvolnit nedosažitelná média. **Bod C ani pořadí fsync/rename/power cut zatím nejsou zvoleny jako hotová implementace**; M0-C musí prokázat jejich trvanlivost na cílové platformě.

**Životnost médií:** po celou dobu aktivního writeru, potvrzené aktuální i obnovitelné předchozí revize, běžícího čtenáře zálohy/restore, koše a případného ještě obnovení nejednoznačné vazby musí být originál chráněn před mazáním. Garbage collection se spouští až po ověření, že média nejsou dosažitelná z žádného chráněného kořene a nejsou zrovna zapisována; nepoznané osiřelé médium se nemaže naslepo.

| Přerušení / souběh | Bezpečný stav po restartu (cílový kontrakt, nikoli fyzický PASS) |
|---|---|
| Před dokončením originálního média | Otevřít předchozí potvrzenou revizi; rozpracované médium není vidět jako hotové a nesmí poškodit původní |
| Po dokončení média, před potvrzením modelu | Otevřít předchozí revizi; nedořešené médium bezpečně izolovat a čistit až po kontrole všech chráněných odkazů |
| Během potvrzení modelu | Zvolit **jen** poslední zcela ověřitelnou potvrzenou revizi; nehlásit úspěch při nejednoznačném stavu |
| Po platném potvrzení, před zobrazením „Uloženo“ | Po reopen být dostupná nová plně potvrzená revize; UI nesmí zpětně tvrdit, že se neúplný zápis podařil |
| Před/po čištění média, při čtení zálohou, paralelní writer | Žádné odstranění originálu stále chráněného jakýmkoli snapshotem, čtenářem či writerem; konflikt přiznat |
| Plný disk, náhlé ukončení, skutečný výpadek napájení | Pravdivý stav, předchozí prokazatelně trvalá data; odlišit garanci proti pádu procesu a proti power-cut podle ověřeného úložiště |

**Undo/Redo:** (a) zrušení rozpracovaného gesta nezakládá nový uložený příkaz; (b) po **neúspěšném save** zůstává neuložená práce výslovně označena a lze ji zkusit bezpečně uložit/vrátit, ne prezentovat jako potvrzenou; (c) nová editace po Undo zneplatní neslučitelnou Redo větev; (d) jeden víceobjektový příkaz obnoví všechny své části a odkazy nebo vůbec nic; (e) **OTEVŘENO PRO VLASTNÍKA:** zda má samotná historie Undo/Redo přežít restart. I bez perzistentní historie musí aktuální potvrzené objekty po restartu zůstat. [Codex review CP3-01](https://github.com/zaplikj30-ui/CraftPlan/pull/3#issuecomment-7071031766).

## 7. Minimální přenosná záloha M2 – kontrakt uzavřít v M0-A

Definovat **verzovaný manifest** včetně všech ID, vazeb, přesných měření, původních bajtů médií a kontroly úplnosti/integrity; záloha se nesmí označit jako hotová, dokud celý výstup není úplný. **OTEVŘENO:** konkrétní archivní formát, kontrolní součty/hash, struktura manifestu a ochrana osobních údajů; zatím nic neukládat na cloud či veřejný GitHub.

Obnova: ověřit verzi, integritu, vazby, počet/média, místo na disku a zda už existuje jiná platná práce **před jakýmkoli ničivým přepsáním**. Vadný, neúplný, nepodporovaný nebo na disk nevejdoucí archiv musí být odmítnut bez ztráty posledního platného stavu. **M2 HOTOVO:** úplná záloha → validace → obnova na čisté instalaci/odděleném zařízení → porovnání všech objektů a originálních bajtů; pak negativní testy s existující prací.

**NEPŘÁTELSKÝ ARCHIV JE NEDŮVĚRYHODNÝ VSTUP:** import a obnova z externího ZIP/manifestu nesmí slepě věřit jménům, typu ani tvrzené velikosti. Odmítnout/bezpečně zpracovat **cesty mimo cílové místo (../, absolutní cesty), symlinky, duplicitní názvy i ID, kolidující cesty, cizí/nepodporované typy, podvržené vazby, extrémní počty záznamů, abnormální velikosti a rozbalovací poměr (ZIP bomb)**. **KANDIDÁTNÍ minimální hranice:** izolované omezené čtení do dočasného prostoru, jednoznačná normalizace a validace všech cest a vazeb, limity počtu/bajtů/hloubky/času, bezpečné dekódování médií, kontrola integrity a konzistentní validace **před zveřejněním jediného objektu**. Na chybě zachovat existující práci i originály; žádné spouštění obsahu archivu a žádný automatický import HTML/JS. Konkrétní limity/manifest/hash a atomické publikování jsou **OTEVŘENO pro M0 návrh a případný schválený experiment**, nikoli pro dnešní implementaci. [Historická kompatibilita](https://github.com/zaplikj30-ui/Zamerovac-android/blob/8a36e353448ba74a066e16372441604b7e7163c7/DATOVY_FORMAT_A_PC_KOMPATIBILITA.md#L44).

Lokální crash recovery řeší pád aplikace; přenosná záloha ztrátu telefonu; M4 plný editovatelný přenos; B2 až později synchronizaci. **Ani dvě místní kopie nejsou ochrana před ztrátou zařízení.**

### 7.1 Konzistentní záchranná záloha a bezpečná obnova – CP3-02

**NÁVRH MINIMÁLNÍHO ROZSAHU M2:** záloha vychází z **jedné již potvrzené revize**; její graf zahrnuje rychlé zaměření mimo zakázky, zakázky/složky, dokumenty, kóty/měření, původní média a přílohy, stav koše a pro podporované budoucí typy také **vlastní definice symbolů a konkrétní vydané revize dokumentů**. Zařazení/retenci odstraněných položek musí potvrdit budoucí vlastník pravidel záloh; nic neodvozovat ze zrovna aktuální obrazovky. **Nezahrnovat jako autoritativní obsah** regenerovatelné náhledy, cache ani místní jazyk/UI nastavení jiného telefonu. Ochrana proti souběžné úpravě, smazání/koši, exportu a GC: po dobu vytváření archivu **zafixovat logický snapshot a dostupnost všech jeho originálů**. Pokud to není zajištěno, zálohu nedokončit jako platnou. Manifest, všechna data a originály musí pocházet ze stejného potvrzeného bodu čtení.

**Obnova není stejná jako kopírování ZIP souborů:** číst a validovat v izolovaném prostoru, včetně úplnosti originálů, cest, verzí, referenční integrity a oprávnění; před potvrzením nic destruktivně nenahradit. Na prázdné instalaci obnovení původního snapshotu **zachová ID**. Při existující práci **nenahrazovat cizí ID**: výslovně odmítnout konflikt, nebo uživatelem potvrzené **„importovat jako novou kopii“** se systematickým přemapováním ID všech objektů, vazeb, symbolových závislostí a mediálních odkazů. Samostatná volba skutečného synchronizačního sloučení je až B2, nikoliv skryté chování obnovy. Úspěch až po ověření **celého finálního artefaktu v cílovém úložišti** a po úspěšném testu obnovy na **jiné instalaci bez přístupu k původnímu telefonu**, včetně původních bajtů.

**Soukromí:** exportovat původní bajty může zahrnovat EXIF/GPS i zákaznické údaje; zobrazit citlivost zálohy, omezit přístup/automatické zveřejnění a před prvním reálným použitím rozhodnout **zda a jaké šifrování/přístupový klíč** je nutný pro vybraný způsob přenosu/uložení. Zálohová bezpečnost a zákaznický filtrovaný PDF jsou **odlišné výstupy**; obsah zálohy netvrdit jako anonymizovaný.

### 7.2 Editovatelný ZIP podle přesné aktuální množiny – CP3-12

Pro ZIP jedné fotografie, vybrané složky i celého projektu určit **výchozí ID/množinu z jedné potvrzené revize** a spočítat **jen aktuálně dosažitelné potomky/odkazované originály a přílohy nutné k plné editovatelnosti tohoto výběru**. Do ZIP **nesmí** přibýt dříve přesunutá fotografie, historicky smazaná položka, cizí zakázka, nepoužitý originál ani cache jen kvůli fyzickému umístění v úložišti. Externí referenci do jiné zakázky buď bezpečně explicitně zahrnout se souhlasem, nebo ji reprezentovat jako omezený/nerozřešený odkaz; nikdy bez oznámení vyvézt cizí média. Uživateli před exportem ukázat předpokládaný výběr a citlivost výsledku, pojmenování je dynamické; finální ZIP musí po importu zachovat editovatelnost vybraných objektů a příloh. Test: 1 foto, složka s podsložkou, přílohy, přemístěný objekt mimo podstrom, archiv s neúplnou vazbou.

## 8. Instalační plány F35 a export

Dvě fáze: **(a) ORIENTAČNÍ** šablona před návrhem, která už v M3b musí mít použitelné PDF a systémové sdílení (minimální M4a); **(b) KONKRÉTNÍ** kopie přiřazená zakázce a podle potřeby volitelný stav **SCHVÁLENO PRO REALIZACI** až po vědomé odborné kontrole. PDF vždy uvádí **stav, datum, revizi a identifikaci zakázky, pokud existuje**. Orientační podklad musí říkat **„ORIENTAČNÍ – není realizační pokyn“**, nikdy nesmí vydávat neověřené instalační výšky za univerzální normu. Již vydané schválené PDF neměnit – nová změna = nová revize **instalačního dokumentu**, nikoli historie celé zakázky F32.

### 8.1 F35 – podpis/stav/vydání určité neměnné revize

**Orientační šablona je použitelná bez zákazníka či zakázky** a má vlastní ID; konkrétní plán může vzniknout jako editovatelná **kopie odkazující na šablonu a její verzi**, případně na ID zakázky. Odlišit **doporučené orientační rozmezí**, skutečnou potvrzenou míru a náčrtné zobrazení. Reference k podlaze/stěně/nábytku či instalačnímu bodu mají stabilní ID a zřejmý původ podkladu, nikoli pixelový odhad.

**Pracovní draft** se může měnit a není automaticky schválený. **Schválení pro realizaci** (kdo, kdy a s jakým odborným ověřením – **OTEVŘENO pro budoucí etapu**) musí být připojeno ke **konkrétnímu neměnnému obsahu, jeho zdrojovým revizím a stavu**, nikoli jen k živému ID dokumentu. **Vydání PDF** je samostatně identifikovaná operace: vybraný obsah a viditelnost jsou zafixovány, vyrobený soubor je kompletně ověřen a teprve pak označen za vydaný. Po neúspěšném generování žádný stav `VYDÁNO` a nic nesdílet. Pozdější změna geometrie/instalací vzniká jako **nový neschválený pracovní stav**; minulý vydaný a případně schválený otisk trvale zůstává navázán na svůj původní obsah/revizi. PDF uvádí stav, datum, revizi a zakázku pouze tehdy, když existuje; orientační dokument nese výstrahu. [F35](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md#L370).

## 9. Verze, import, přenositelnost a soukromí

Každý dokument/záloha má rozpoznatelnou verzi formátu. Migrace starších souborů se testuje na kopii; při nepodporovaném novějším formátu se nesmějí neznámé objekty či naměřené hodnoty potichu zahodit. **Formát dat nesmí být závislý na jediném rendereru ani pouze na JVM**. Import ze Zaměřovače 1 je samostatný pozdější projekt M4c **pouze z kopie**, bez úpravy v1.

**Neznámá rozšiřující pole:** pokud čtenář rozpozná bezpečnou známou verzi a podporovaný kontejner rozšíření, ale jeho obsah neumí zobrazit, musí jej při round-tripu **beze ztráty zachovat nebo bezpečně odmítnout editovatelný zápis**; nikdy tiše nesmazat neznámé hodnoty. **Data zakázky ≠ místní nastavení zařízení:** jazyk, poloha panelů, UI barvy a jiné lokální preference telefonu se při importu zakázky nesmějí automaticky přepsat. [Historický datový kontrakt](https://github.com/zaplikj30-ui/Zamerovac-android/blob/8a36e353448ba74a066e16372441604b7e7163c7/DATOVY_FORMAT_A_PC_KOMPATIBILITA.md#L44).

**Viditelnost a publikace jsou čtyři oddělené věci:** (1) **existence** objektu/kóty/poznámky v editovatelném projektu, (2) jeho **dočasná viditelnost v pracovním pohledu** (F37), (3) **vědomě vybraný obsah a náhled konkrétního PDF/ZIP/exportu** (F29/F37), (4) **budoucí zveřejněná revize konkrétnímu autorizovanému zákazníkovi** (F36). Výchozí stav **nic nesdílet**. Skrytá kóta dál existuje, schování tlačítka/nástroje F16 je pouze preference UI a **nemění obsah dokumentu ani práva**. Export musí explicitně určit, co zahrnuje, a nezveřejnit interní poznámky, skryté položky či přílohy jen proto, že zůstávají v projektu; pro editovatelnou zálohu naopak musí být jasně řečeno, že zahrnuje vše. Zákaznická publikace jen schválená revize a oprávněné ID/role, s možností odvolání; žádný zákaznický server nyní. [F16](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md#L198) · [F36/F37](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md#L383).

**Budoucí licence/přístup F38:** omezení funkce nebo změna oprávnění **nesmí zničit ani učinit neobnovitelná vlastní data**, zásadní export/obnova vlastních souborů musí mít samostatně vymezenou bezpečnou cestu. Přístup k funkci, vlastní zakázce a cizím zákaznickým datům jsou odlišná práva. Nezakládat licenční ani autentizační server v M0. [F38](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_INVENTURA_POZADAVKU.md#L79).

Před M0-D u každého vážného kandidáta porovnat výkon Android editoru, bezpečnost dat, možnost budoucího Windows/iOS/iPadOS portu, Bluetooth a další hardware, licence, náklady a dlouhodobou údržbu. Žádný framework nyní není schválen.

Veřejný repozitář nesmí obsahovat skutečné zakázky, zákaznické fotografie, GPS, přihlašovací údaje ani tokeny. Testy jen se syntetickými/nahraditelnými podklady; export zákazníkovi nesmí nechtěně odkrýt skryté interní poznámky ani EXIF/GPS.

### 9.1 Lokální schéma ≠ přenosný formát; reálný výběr archivu

**Dvě oddělená verzování:** fyzická lokální DB/souborová struktura (`local schema`) může měnit indexy a interní ukládání; **verze přenosného manifestu** je samostatná stabilní smlouva pro import/restore/Windows/iOS. Migrace každé verze, bezpečné odmítnutí neznámého obsahu a zachování podporovaných neznámých rozšíření mají **samostatné pozitivní i negativní testy**. Nevyvozovat kompatibilitu přenosného ZIP z čísla verze lokální DB.

**Výběr ZIP na Androidu:** historicky mohl standardní systémový picker filtrovat platný ZIP kvůli zavádějícímu MIME. Budoucí implementace musí dovolit vědomě zvolený skutečně platný archiv podle **bezpečně ověřeného obsahu**, nikoli jen přípony/MIME; typ není důvod obcházet bezpečnostní validaci. Otestovat výběr a opakovaný pokus, vadný manifest a bezpečné odmítnutí bez zápisu.

### 9.2 Náhled zákaznického výstupu a sdílený artefakt jsou jedna revize

Zvolit **jediný neměnný potvrzený zdroj dokumentu** a konkrétní obsahový výběr (včetně viditelnosti) pro **náhled i skutečné PDF/PNG**, nikoli render náhledu z jiného okamžiku než exportu. Pokud se dokument mezitím změní, původní vydávaný snímek zůstane stejný nebo je nutné vědomě obnovit náhled a znovu potvrdit. **Skutečný výsledný soubor** má v negativním testu prokázat, že nepřibral skryté poznámky, interní přílohy, nechtěná EXIF/GPS metadata ani externího zákazníka; samotný grafický náhled nestačí. F36 zůstává vzdálený návrh: **příspěvek zákazníka** má samostatného autora a vlastní identitu mimo profesionálovy naměřené hodnoty a mění jen jemu povolenou část; povolení platí pro **konkrétní publikovanou revizi**, ne celé úložiště. [F36/F37](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md#L382).

## 10. Scénáře k pozdějšímu ověření (vše nyní NOT RUN)

| Kód | Scénář | Podmínka budoucího PASS |
|---|---|---|
| T01 | Foto + kóta → save → úplně zavřít → reopen | Identická přesná hodnota, kotvy a originál |
| T02 | M1 na skutečném telefonu prstem a samostatně S Penem: koncový/středový úchop → lupa → zoom/pan → zrušení rozpracovaného tahu → nová editace → Undo/Redo → save → ukončení → reopen; navíc 1 000 dlouhých/přerušovaných tahů a magnetické kolize gest | Jen potvrzené příkazy jsou vratné; konec vs. celek vs. text mají správnou prioritu, lupa/pan nesmí posunout míru ani spustit náhodný úchop, zrušený tah se neuloží, po reopen jsou stejné kotvy/čísla/originál; latence a výpadky měřit bez předstírání prahu |
| T03 | Přerušení zápisu dat/metadata | Zachována poslední potvrzená revize |
| T04 | Přerušení mezi souborem média a DB záznamem | Žádné falešné `uloženo`, žádný zničený originál |
| T05 | Plný disk, odmítnuté oprávnění, ztracený externí soubor | Přiznaná chyba bez poškození platné práce |
| T06 | Současný/opožděný zápis, pád, výpadek napájení | Žádné přepsání novější potvrzené práce; fyzický test oddělit od simulace |
| T07 | 1/16 in, lokální formát, plochy, jednotky | Přesný round-trip bez driftu/přetečení |
| T08 | M1 během aktivního rozpracovaného tahu/úchopu: otočit telefon či změnit velikost okna (je-li podporována), zoom/pan a přechod náhledu; po zrušení či výslovném dokončení uložit a reopen; samostatně EXIF 1–8 | Rozpracovaný tah se bezpečně dokončí NEBO přiznaně zruší podle schváleného pravidla, nikdy se tiše nevloží či nepřemístí kóta; potvrzené kotvy/naměřená čísla zůstanou stejné, editor i lupa jsou ovladatelné prstem/S Penem; rotace, EXIF ani export nezrcadlí dvakrát |
| T09 | Panorama šev 359,9°/0,1° a pól sféry | Kotvy konzistentní i mimo aktuální výřez |
| T10 | Rozdělení F04 stěny a navázaný F05 nárys | Identita a naměřená délka zachované či vazba jasně nevyřešená |
| T11 | Laser – zpožděný údaj, nejasná jednotka | Nikdy nevložit do špatné kóty |
| T12 | Kompletní přenosná záloha, obnova na čisté instalaci | Stejná data, ID, jednotky, vazby, originální mediální bajty |
| T13 | Poškozená/neúplná/starší či novější záloha; existující práce | Bez tichého zničení platné práce |
| T14 | F35 orientační → konkrétní → schválená revize PDF | Správný stav/datum/revize, vydané PDF nepřepsáno |
| T15 | Migrace/otevření jiné verze dokumentu | Neznámá data se neztratí, bezpečné odmítnutí nebo migrace |
| T16 | Srovnání technologie pro 2D editor | Stejné podklady, měřený výkon, paměť, rizika a náklady |
| T17 | F11 součet dvou a více kót → potvrdit novou → změnit zdrojovou | Nová kóta zachová přesnou potvrzenou hodnotu a své ID, zdroj se změní nezávisle; Undo funguje |
| T18 | F14 vložený symbol, smazat/přejmenovat definici z knihovny, bezpečný JPG/PNG/WebP import | Instance a originální obrázek zůstanou, chybějící definice je přiznána; škodlivý typ odmítnut |
| T19 | F16 skrýt nástroj vs. F37 skrýt objekt vs. vydat export vs. F36 zveřejnit | Existence dat a oprávnění se nemění; obsah exportu přesně podle potvrzeného výběru, žádný únik |
| T20 | F10/F13 návrh hlas/AI s uživatelskou hodnotou vs. nezměřená grafická kóta | Zrušení bez zápisu, žádná nula/odhad z pixelů, potvrzení validovaným příkazem a Undo |
| T21 | F04/F05 kóta 3→4 m, rozdělená stěna, společné okno, 2 nárysy, fotodetaily | Bez automatického přestavění a dvojího okna; zachované či přiznané nejednoznačné vazby |
| T22 | M1: přemístit diagnostickou vlaječku → ON → první relace s tahy/Undo/save → OFF → ON → druhá relace → OFF → restart → poloha vlaječky i obě relace zachované → vybrat obě/Vybrat vše → náhled → sdílet jen vybrané → potvrdit hromadné smazání → jiné relace a zakázka zůstávají; samostatně plný limit, vadný log, OFF/ON/FAULT a chybný export | Výchozí OFF; staré potvrzené relace se při limitu NIKDY tiše nemažou, limit je přiznán a nový záznam bezpečně omezen; soubor bez soukromých údajů, bez automatického uploadu; sdílení nemaže; závada/FAULT nemění editor, originály, Undo ani pravdivé uložení; shodné funkční výsledky a změřená režie při OFF/ON na skutečném Androidu |
| T23 | Úmyslně škodlivý ZIP: ../, absolutní cesta, symlink, duplicitní ID, ZIP bomb | Omezené odmítnutí bez zápisu mimo izolované místo a bez ztráty existujících dat |
| T24 | F09 pevné kóty vs. pevná fotografie, Hotovo/Zrušit/Undo | Dvě samostatné módy, oba originály nedotčené, žádné trvalé seskupení kót |
| T25 | F08 GPS návrh adresy po ruční opravě, adresa zákazníka ≠ zakázky ≠ fotky | Bez automatického přepisu a záměny cíle, bez povinné polohové služby |
| T26 | F33 přerušený timer/Stop, jízda zvlášť, ruční km; F34 změna zákazníka po vydání | Žádný Stop nesprávného timeru, žádný přepis ceny/částek ani vydané faktury |
| T27 | F01/F02 dílčí zdrojové fotky / hotový import / částečná sféra | Zachování originálů, kotvení a projekce, přiznané neúplné pokrytí |
| T28 | STARÉ 1.1–1.8 vs. poslední schválená ergonomie, historické A–E | Žádný nevyžádaný redesign fungujících úchopů; fyzický regresní důkaz až později |
| T29 | M4 round-trip s neznámými rozšiřujícími poli a odlišnými lokálními preferencemi | Neznámé kompatibilní pole zachováno nebo bezpečně odmítnuto, cílové UI prefs nedotčené |
| T30 | Hromadný přesun se zámky, ZIP jedné fotky s přílohami, poznámka s více fotkami | ID, zámky, všechna média a editovatelný import zachované; bez skrytého částečného úspěchu |
| T31 | Revize media+metadata: fail mezi přípravou/potvrzením/GC, save zpožděný vs. novější, běžící záloha | Po reopen pouze poslední plně potvrzená revize, chráněné originály zůstaly; rozdíl pád procesu vs. power-cut doložit zvlášť |
| T32 | Undo/Redo: zrušení gesta, save error, nová editace po Undo, víceobjektová změna | Žádný falešný `ULOŽENO`, neplatné Redo se nevrátí, celý graf objektů se vrací konzistentně |
| T33 | M2 záloha během souběžné změny/koše a GC, rychlý podklad, vydané PDF, vlastní symboly | Vše ze stejného potvrzeného snapshotu; souběžná úprava neodpojila bajty ani ID |
| T34 | Obnova na čisté instalaci a do existující práce s kolizí ID; externí zařízení offline | Původní ID na čisté, v cílové práci odmítnutí či vědomá kopie s úplným remapem; originál nepotřebný k obnově; ochrana soukromí |
| T35 | Lokální schema vs. ZIP verze, falešný MIME v Android pickeru, starší/novější manifest | Validní ZIP lze vědomě vybrat; neznámé rozšíření zachováno nebo bezpečně odmítnuto, bez změny lokálních preferencí |
| T36 | Měřicí číslo 0 vs. NEZMĚŘENO, 1/16 in, přesnost zdroje, plocha nepravidelného Lichoběžníku | Nula se nezamění za absenci; 1/16 in přesné, plocha bez dostatku údajů označena neurčená, ne falešně spočtena |
| T37 | Laser: jiné pole stejné kóty, Ø/R, koncový úsek, změna kontextu, duplicitní reconnect | Hodnota se nepřipojí k jiné straně/poli; bezpečně nepřiřazeno či odmítnuto, ruční cesta funkční |
| T38 | EXIF všech 1–8, zrcadlení, crop, další rozlišení, editor/galerie/PDF, více výměn F09 | Stejné kotvy a čísla po round-tripu, žádné dvojí otočení/zrcadlení; ruční perspektivní korekce možná |
| T39 | Panorama F01/F02: celý objekt přes šev/pól, edit vs. viewer, nové přepočítání/stitch, chybějící pokrytí | Kotva přemapována se zdrojem nebo označena k opravě, jinak bez ztráty/nezáměrného posunu |
| T40 | F04/F05 šikmá stěna, obrátil se lokální směr, split+merge, okno, 2 nárysy a fotodetaily | Stejná strana/výška a ID, nejednoznačné případy přiznány, multiobjektové Undo a reopen úplné |
| T41 | F35 orientační bez zakázky → kopie → schválení obsahu → změna → neúspěšné PDF | Schválení zůstane jen původní neměnné revizi, změna je neschválená, chybné PDF není vydané |
| T42 | F37/F36 zákaznický náhled vs. skutečný soubor při současné editaci, externí příspěvek | Stejný neměnný vybraný obsah, žádné interní anotace/GPS/přílohy mimo výběr; příspěvek nemění řemeslníkova data |
| T43 | Zámek: jediný přesun, dávka, restore, import a koš v zamčené vnořené složce | Zdroj, cíl i zamčení předků kontrolováno jednotně, výsledek/částečný výsledek pravdivý |
| T44 | Export ZIP vybrané fotky/složky po přemístění jiné fotky ven; symbol s novou verzí katalogu | Z archivu nevytékají cizí/historické soubory, editovatelné přílohy a přesná podoba symbolu se zachovají |

**T01–T44 jsou NOVÉ návrhy scénářů, nikoli náhrada historických 28 testů.** Nyní **NOT RUN**. Teprve po schválení konkrétních úkolů a prostředí lze realizovat malé experimenty M0-C/M1/M2 a rozlišovat SOURCE, automat, emulátor a fyzický Android.

## 11. Otevřené otázky pro nezávislé DESIGN/SECURITY review

1. Rozhodnout přesnou kanonickou reprezentaci všech veličin a pravidla převodů (včetně výpočtů ploch).
2. Vybrat bezpečný algoritmus trvalého potvrzení pro koordinaci DB a externích médií; určit obnovu a čištění osiřelých souborů.
3. Přesně definovat orientaci a souřadnice foto/panorama/sféry, neznámou škálu půdorysu a nejednoznačné vazby rozdělené stěny.
4. Uzavřít politiku Undo/Redo po restartu a přesnou hranici jedné uživatelské změny.
5. Definovat minimální manifest zálohy, integritu, kontrolu obnovy a bezpečný import do existující práce.
6. Definovat verze/migrace a způsob práce s neznámými typy bez ztráty dat.
7. Uzavřít bezpečnostní přechody a revize F35, včetně zákaznického PDF a kontroly před vydáním.
8. Porovnat celý návrh s **[maticí 12+39+49+28 a detailními zdroji](CRAFTPLAN_M0A_MATICE_NAVAZNOSTI.md)**; při chybě doplnit a znovu posoudit.
9. **JEDNOTNÁ POVINNÁ BRÁNA M0-A / podklad pro M1:** není splněna pouhou evidencí 128 ID. Před `HOTOVO M0-A` musí být dokumentačně vypořádány všechny původní nálezy **R01–R14** a všech **16 skutečně uvedených nálezů CP3-01–CP3-13, CP3-15, CP3-16 a CP3-18**, včetně **CP3-13 / N02** (konkrétní ergonomie prst/pero, lupa, rotace, diagnostika, uchovaná vlaječka/historie), **N01** (sjednocená akceptační podmínka) a minimálně bezpečnostních invariantů **CP3-01/02** pro revize, originály a zálohu. Před M0-A uzavřít technologicky neutrální přesnost/jednotky, měřicí pole, identitu/vazby, souřadnicové konvence, potvrzený save, hranici Undo/Redo po restartu, minimální manifest/snapshot a bezpečné odmítnutí archivu; neuzavřené rozhodnutí nebo nové blokující review musí zůstat výslovně `OTEVŘENO`, nikoli falešně `HOTOVO`. Pro všechny navržené scénáře **T01–T44** vyznačit **PŘIPRAVENO / NOT RUN**, dokud není proveden odpovídající schválený test; návrh testu NENÍ PASS. Po uzavření návrhových kontraktů následuje **samostatné nezávislé M0-B DESIGN/SECURITY review** a vlastníkovy samostatné rozhodovací brány; **M0-C technické experimenty, build a M1 až po výslovném GO**, ne tímto PR.
10. **Odložené do konkrétních funkčních etap:** grafické kolekce symbolů, OCR/AI model, hlasový backend, účetní/časovací UI, klientský portál, GPS, finální stitching a serverové role/licence. Budoucí technologické posudky mají pokrýt všechny klíčové typy dokumentů a vstupy; ne jen fotoeditor.

11. **Konkrétní otevřené volby pro jednotnou bránu bodu 9 (N01):** počet potvrzených/obnovitelných revizí, přesná pravidla čísel a povolené rozsahy, finální EXIF a další souřadnicové konvence, životnost chráněných médií, hranice Undo po restartu, minimální zálohový manifest a limity importu, soukromí/ochrana přenosné zálohy. Mechanismus `fsync/rename`, fyzická odolnost proti výpadku napájení, reálné prst/S Pen/diagnostika a výkon vyžadují až schválený experiment či implementační test; **nepředstírat dnes jejich PASS ani nenutit jejich provedení pro pouhý návrh**.
12. **M1 diagnostika a výkon:** pro cílová fyzická zařízení a zátěž explicitně určit p95/p99 input→visible, frame jank, dobu potvrzeného uložení, paměť a přijatelnou režii OFF/ON. Vlastník určí minimální podporovanou třídu telefonu/tabletu a potřebu pera; naměřené prahy až po odpovídajícím GO. Prst/S Pen vyžadují skutečný Android, myš/klávesnice odpovídající pozdější fyzický scénář.

**Neuzavřené otázky nejsou automaticky vyřešené přítomností této dokumentace.** Autor + nezávislý kontrolor musí rozhodnout, které lze vyřešit návrhově a které vyžadují samostatně povolené technické ověření; bez něj se neuděluje neopodstatněné APPROVE DESIGN.

## 12. Hranice a další kroky

**TEĎ:** autorova kontrola obou nových dokumentů a celé kumulativní změny, nezávislé DESIGN/SECURITY review. **POZDĚJI:** dopracování přesných rozhodnutí M0-A, M0-B brána, schválené M0-C experimenty, M0-D rozhodnutí o technologii. **NEMĚNIT:** PR #1 a #2 bez separátního povolení, `main`, původní Zaměřovač 1 a HOME, CI, APK, zákaznická data a peníze.

### Zdroje

[Hlavní plán](https://github.com/zaplikj30-ui/CraftPlan/blob/68646525fabadc6acd51d57e21184026a95cf7c9/docs/CRAFTPLAN_HLAVNI_PLAN_STAVBY.md) · [Ústava dat](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_USTAVA_DAT.md) · [Katalog funkcí](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_KATALOG_FUNKCI.md) · [F01–F05](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_F01_F05_ZADANI.md) · [Živý návrh](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_ZIVY_NAVRH.md) · [Inventura 49+28](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_INVENTURA_POZADAVKU.md) · [Registr F01–F39](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md) · [Historické návaznosti](https://github.com/zaplikj30-ui/CraftPlan/blob/837988f3e5712fe71a983307dc5417605b429d7b/docs/ZAMEROVAC_2_REGISTR_NAVAZNOSTI_V1.md).
