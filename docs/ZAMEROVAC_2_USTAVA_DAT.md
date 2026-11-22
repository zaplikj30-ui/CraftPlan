# Zaměřovač 2 – M0: jednostránková ústava dat a ukládání (návrh)

> **CraftPlan (historicky „Zaměřovač 2“).** Tento dokument je součástí aktuálního produktového zadání CraftPlanu. Pochází z [PR #234 původního Zaměřovače](https://github.com/zaplikj30-ui/Zamerovac-android/pull/234); odkazy na jedničku jsou historické reference, nikoli pokyny ke kopírování starého kódu nebo workflow. Platí [hlavní pravidlo zdroje pravdy](ZAMEROVAC_2_JEDNOTNY_ZAKLAD_A_PRENOS.md).

**21. 11. 2026 · STAV: historicky vlastník odsouhlasil principy jednoduché ochrany dat a předběžné prověření SQLite. Novější rozhodnutí 22. 11. 2026 stanoví: technologie, renderer ani databáze NEVYBRÁNY; současný [M0-A kontrakt PR #3](https://github.com/zaplikj30-ui/CraftPlan/pull/3) a [hlavní plán PR #2](https://github.com/zaplikj30-ui/CraftPlan/pull/2) mají přednost před zdejšími staršími návrhy. Konkrétní implementace, obnova a výkon NEOVĚŘENY.** Záměr: dostatečná ochrana dat bez přehnané historie. Platí nejprve pro malý **offline** základ v2; synchronizace/účty až v pozdější samostatné etapě.

## Co musí platit

1. **Jednotka práce:** zakázka obsahuje stabilní ID, **jednu vlastní uloženou fotografickou verzi na podklad ve zvolené kvalitě**, složky a editovatelné anotace; externí originál při importu zůstává nedotčen. Přesná naměřená hodnota a jednotka nejsou odvozené z pixelů ani z formátovaného popisku. Zoom, přesun a změna telefonu uložené hodnoty nemění.
2. **Rozpracovaná změna:** při právě probíhajícím tahu nebo před potvrzením uložení může po pádu zmizet poslední nedokončený úkon. Běžný tah kóty nevytváří trvalou kopii celé historie. Jeden dokončený tah je nejvýše jeden krok Undo; počet tahů nesmí sám zvyšovat cenu dalšího uložení.
3. **Potvrzené uložení:** aplikace nesmí oznámit „uloženo“, dokud bezpečný zápis skutečně neskončil. Již úspěšně uložená práce se nesmí tiše ztratit či vrátit do staršího stavu. Po restartu se musí otevřít poslední platný potvrzený stav, případně ověřená předchozí kopie s jasnou informací.
4. **Ochrana médií:** existující externí zdroje fotografie/zvuku/videa se nepřepisují anotacemi ani nemažou; kóty a poznámky jsou oddělené a editovatelné. V CraftPlanu je na fotografii **jedna trvale uložená verze vybrané kvality**, bez povinného dalšího plného originálu a bez automatického publikování do systémové Galerie. Odkazy/ID zůstávají platné, při chybě se nezničí jediný platný uložený snímek.
5. **Přiměřená pojistka:** běžná trvalá historie každého pohybu není požadavek. Kandidát je „1 aktuální + 1 předchozí platný stav“ pro jednotku ukládání, **nikoli dnes zvolená technologie**. Porovnat jednoduché soubory proti SQLite/Room a vybrat nejmenší řešení, které obstojí ve stejných testech. Undo/Redo, koš a export mají jiný účel než crash recovery.
6. **Bezpečná chyba:** plný disk, zamítnuté oprávnění, přerušení zápisu, poškozený soubor nebo chybějící příloha nesmějí vyvolat falešné potvrzení uložení. Přiznat problém; zachovat poslední ověřený stav.
7. **Pozdější rozšíření:** nyní žádné přihlašování, sync fronty, cloud ani konfliktní protokol. Data mají zůstat srozumitelná, verzovaná a s ID, aby pozdější online funkce nevyžadovaly nevratný přepis.

## Konkrétní technická oponentura

- **FAKT:** v1 již má `projects.json` + `projects.bak`, ale při ukládání může ještě **3× archivovat další úplné DB stavy** a prochází rostoucí historické revize; diagnostika naměřila cca 5 s ([#221](https://github.com/zaplikj30-ui/Zamerovac-android/issues/221)). To nechceme opakovat.
- **HISTORICKÁ PREFERENCE KE SROVNÁNÍ (21. 11. 2026, nikoli výběr technologie):** prověřit transakční **SQLite**, ukládání jen změněné fotografie/kóty a originály médií mimo DB. Žádná neomezená historie plných zakázek; obnova a Undo odděleně. SQLite samo nezaručuje druhou zálohu ani ochranu před ztrátou telefonu. Jde o schválení návrhové preference, **nikoli definitivní volbu driveru, struktury DB či povolení implementace**.

## Minimální zkouška před schválením řešení

- Přidat fotografii a přesnou kótu → 100/1 000 opakovaných přesunů → uložit → ukončit → otevřít; změřit plynulost, latenci uložení a paměť, bez rostoucí ceny kvůli počtu tahů.
- Ověřit přesné číslo a jednotku, geometrii, Undo/Redo, **stejné bajty skutečně uložené fotoverze** po save/reopen a v obnově z M2 zálohy; externí zdroj při importu zůstává byte-for-byte nedotčený.
- Simulovat přerušení zápisu, zaplnění disku, vadný soubor a chybějící médium; žádný tichý přepis posledních platných dat a žádné falešné „uloženo“.
- Stejný scénář provést na emulátoru a alespoň jednom skutečném Androidu; později zahrnout odlišnou značku a slabší zařízení podle [plánu testování](ZAMEROVAC_2_ZIVY_NAVRH.md). Výsledky oddělit: SOURCE / automat / emulátor / fyzický telefon.

**OTEVŘENO:** konkrétní SQLite driver/knihovna nebo jednoduchá souborová varianta, schéma dat, mechanismus obnovy/předchozího platného stavu, meze výkonu, kvalita/kodeky a bezpečná práce s dočasnými soubory. **Produktově uzavřeno v [PR #6](https://github.com/zaplikj30-ui/CraftPlan/pull/6):** jedna trvale uložená fotografie ve zvolené kvalitě pouze v CraftPlanu; výchozí focení/import **Plná/Plná**, bez automatického dalšího uložení do Galerie. Externí originál nikdy neničit a zmenšeninu nevydávat za původní plný zdroj. **Minimální přenosná záloha s prokázanou obnovou je povinná v M2 před nenahraditelnými zakázkami**, nikoli až v budoucí synchronizaci. Teprve po samostatném GO provést fyzické testy; tento kontrakt není důkaz runtime.
