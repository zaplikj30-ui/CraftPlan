# CraftPlan – průběžný registr rozhodnutí o vzhledu a ovládání

**Stav:** živý produktový/UX návrh. **Datum prvního zápisu:** 22. 11. 2026. **Autor rozhodnutí:** vlastník CraftPlanu v projektové diskusi. Samotný záznam **není** schválení implementace, výběru technologie, rendereru, merge ani APK. Repozitář je veřejný: žádné zákaznické údaje či soukromé fotografie.

## Pravidla evidence

**POTVRZENO** = vlastník výslovně vybral funkční směr, nikoli finální vzhled či provedený test. **PREFERENCE** = přání, jehož detaily potřebují dopracovat. **NÁVRH** = pouze podnět asistenta, dosud neschválený. **OTEVŘENO** = čeká na rozhodnutí nebo praktické ověření. Novější výslovné rozhodnutí vlastníka má přednost, rozpor se musí přiznat; historicky schválenou ergonomii nevylepšovat bez důvodu.

Tento soubor zapisuje **pouze nový průběžný UX kontext**. Úplný katalog funkcí, bezpečnostní pravidla a M0 kontrakty zůstávají v níže odkazovaných PR #1–#3; tento záznam je nenahrazuje. Nová rozhodnutí ukládat průběžně do téhož izolovaného UX PR s krátkým logem změn, ne čekat na konec chatu. Při novém chatu začít tímto souborem a aktuálními PR, nikoli pouhou pamětí konverzace.

## Potvrzené směry a preference vlastníka

| ID | Funkce / rozhodnutí | Stav a hranice |
|---|---|---|
| UX-01 | **Úvodní obrazovka – varianta A**: dva hlavní vstupy **Nové zaměření** (pořídit / vybrat fotografii) a **Pokračovat v práci** (poslední uložené rychlé zaměření). Není nutný zákazník ani zakázka. | **POTVRZENO**; přesný vzhled, prázdný stav a umístění ostatních voleb otevřené |
| UX-02 | Hlavní část editoru má zabírat fotografie, editovatelná přesná kóta, úchopy/lupa, prst/S Pen, zoom/pan, Undo/Redo, potvrzené uložení a reopen. Plynulost je zásadní, originál zachovat. | **Platný cíl M1 podle PR #2**, nikoli potvrzení fyzické funkčnosti |
| UX-03 | **Přesunutelná a připínatelná lišta**: uživatel zvolí horní, dolní, levou nebo pravou stranu; umístění zůstane po restartu. | **POTVRZENO**; detail přemístění/otočení ještě navrhnout |
| UX-04 | **Vlastní pořadí ikon**: nástroje lze přeuspořádat, osobní rozložení zapamatovat. | **POTVRZENO**; gesto, režim editace, reset otevřené |
| UX-05 | **Zamykatelná lišta / uspořádání**, aby nešlo omylem přesunout celou lištu ani ikony; zamknutí nesmí zakazovat běžně používat nástroje. | **PREFERENCE vyjádřená vlastníkem**; výchozí zámek a odemčení otevřené |
| UX-06 | **Dvě stále dostupné položky**: **Výběr/Přidání** a **Opakovat**. Nemají zmizet při rolování či sbalení ostatních ikon. | **POTVRZENO**; přesná sémantika Výběr/Přidání ještě rozhodnout |
| UX-07 | **Opakování vybraného nástroje**: zapnuté dovolí vložit postupně kótu–kótu–kótu, později čtverec–čtverec–čtverec; vypnuté režim jedné akce. | **POTVRZENO – funkční záměr**; nesmí kolidovat s dlouhým stiskem/přesouváním ikon ani s Undo |
| UX-08 | Ostatní nástroje (mimo dvě stálé položky) mohou být schované, při velkém počtu přístupné rolováním (dole/nahoře vodorovně, na straně svisle) nebo rozbalením. | **POTVRZENA potřeba škálování**, volba mechanismu **OTEVŘENA** |
| UX-09 | Vlastník zvažuje **swipe, šipku, vyjížděcí panel, případně vlastní nastavení režimu**. Asistent doporučil „swipe listuje, šipka rozbalí“, ale vlastník definitivně nevybral. | **OTEVŘENO**; nesmí se tiše přepsat na schválenou variantu |
| UX-10 | Při sbalení zůstávají dvě stálé položky a možnost ostatní nástroje opět otevřít. | **PREFERENCE vlastníka**; konkrétní režimy sbalení a vizuální styl otevřené |
| UX-11 | Nastavení umístění, pořadí a uzamčení mají být **uživatelské preference**, nikoli data zakázky; má zůstat možnost bezpečně obnovit použitelný stav při změně velikosti/orientace obrazovky. | **POTVRZENO v principu + bezpečnostní doporučení**, detaily otevřené |
| UX-12 | Malá, dobrovolná, pasivní diagnostika už s první M1 kótou: **přesunutelná samostatná vlaječka**, uložená poloha, OFF/ON, historie relací a uživatelsky řízený výběr/sdílení/mazání. Diagnostika nesmí ovládat editor ani ukládání. | **Potvrzený funkční cíl PR #1/#3**, nikoli návrh nové velké diagnostické obrazovky |
| UX-13 | První uživatelsky viditelné testovací označení **`0.1.0-test`**; vlastní řada CraftPlanu od nuly a oddělená instalace od Zaměřovače 1. Verze má být snadno dohledatelná („O aplikaci“). | **Dřívější schválené rozhodnutí PR #1**, není APK |
| UX-14 | První M1 je malá **testovací** offline verze; velká galerie, zakázky, lasery, půdorysy, panorama, AI či fakturace až podle budoucích závislostí. Všechny historické potřeby/F01–F39 zůstávají v katalogu. | **Cíl etap PR #2**; není to rozhodnutí navždy vyloučit budoucí funkce |
| UX-15 | **Nejdřív navrhnout budoucí celek aplikace**, navigaci mezi zakázkami, galerií, různými dokumenty; potom vybrat malý M1 díl, aniž by se později musel bourat základ. | **POTVRZENÝ návrhový postup** |
| UX-16 | **Věrně převzít nejen fungování, ale i již dotaženou vizuální podobu kóty ze Zaměřovače 1**: tvar, vzhled, umístění, vizuální velikost a poměry koncových a středových úchopů, žluté čáry/hroty, zobrazení a natáčení popisku, přesnou lupu a osvědčené dotykové ovládání. Vlastník považuje tento design za hotový a vyhovující – **nepředělávat kótu ani úchopy pro samotnou změnu**. Věrnost ověřit oproti skutečnému referenčnímu vzhledu v1 (např. na zařízení / referenčním snímku) a typickým gestům; případné nutné odchylky kvůli různým rozměrům obrazovek, přístupnosti a výkonu výslovně odůvodnit a předložit vlastníku. **Odstranit sekání**, otestovat prst/S Pen a 100/1 000 tahů; nepřebírat automaticky starý kód, data ani jeho chyby. | **VÝSLOVNĚ POTVRZENO vlastníkem 22. 11. 2026 – včetně přesného vizuálního provedení**, nejen principu ovládání; vizuální referenční kontrola a fyzické testy CraftPlanu NOT RUN |
| UX-17 | **Nová kóta zůstává po nakreslení vybraná** (viditelné úchopy, dostupná následná úprava); klepnutí na prázdné místo výběr zruší. Klepnutí na kótu/její hodnotový popisek dovolí ručně otevřít zadání či úpravu rozměru. | **POTVRZENO vlastníkem 22. 11. 2026**, důsledně odlišit výběr od nechtěného tahu a gesta |
| UX-18 | **Pořadí „nejprve nakreslit, potom zadat číslo“**. Nezměřená kóta zůstává uložitelná jako **nezměřeno**, nikoli jako skutečná nula či délka odhadnutá z pixelů. Hodnotu lze doplnit a později upravit. Před zadáním místo `?` malá značka skládacího dřevěného metru s ryskami (viz UX-21). | **POTVRZENO**; přesná kresba/rozměr ikonky k návrhu a fyzickému ověření |
| UX-19 | Při budoucím **připojení laseru F03** může bezpečně přijatá hodnota **rovnou vyplnit právě označenou kótu**, bez zbytečného ručního přepisování; pouze při jednoznačné identitě cílové kóty/pole, kompatibilní jednotce, platném měření a neexpirovaném kontextu. Při nejednoznačnosti odmítnout automatické přiřazení a nabídnout kontrolu, nikdy nezapsat do jiné kóty. | **POTVRZENÝ UX CÍL, TECHNICKY POZDĚJI** (F03/M5); **laser není součástí M1**, případné rané experimenty jen po GO; vazba na CP3-05/T37 v PR #3 |
| UX-20 | **Zadávání hodnoty nesmí zakrýt systémová klávesnice**. Dialog/list/okno posunout či zmenšit vůči skutečně viditelné ploše a udržet **Uložit hodnotu** dosažitelné v režimu na výšku i na šířku, na malém telefonu a při znovuotevření/otočení. Zadávání zachová rozepsanou hodnotu a jednotku, nepřipustí falešně potvrzený zápis. | **POTVRZENÝ požadavek**; konkrétní řešení horního dialogu vs. přizpůsobitelný panel **OTEVŘENO**, fyzický Android/IME test NOT RUN |
| UX-21 | **Místo otazníku `?` schválen motiv malé ikony skládacího dřevěného metru (truhlářský skládací metr) s viditelnými ryskami.** Má být drobná, vizuálně čistá, s dostatečně velkou dotykovou oblastí; klepnutí na místo hodnoty umožní její zadání. Není to automatické měření, nenahrazuje přesnou hodnotu, jednotku ani stav **nezměřeno**. Po vyplnění se zobrazí číselná míra a jednotka. Nezaměňovat za svinovací pásmo; předchozí návrhy textu „Zadat“ nebo „—“ se nevolí. | **POTVRZENÝ MOTIV vlastníkem 22. 11. 2026**; konkrétní grafika, velikost a kontrast k pozdějšímu návrhu/ověření, nikoli GO k programování |
| UX-22 | **Věrně zachovat přesouvání samotného čísla podél kóty**: prstem / S Penem lze přesunout popisek `850 mm` do stran **ve směru kótovací čáry** (i šikmo), nezávisle na celé kótě a koncových bodech. Nemění se hodnota, jednotka, geometrie ani poloha kóty; pozice popisku musí přežít uložení/reopen a fungovat s Undo/Redo. | **VÝSLOVNĚ ZNOVU POTVRZENO vlastníkem 22. 11. 2026**; doložené ve v1 `editor.js` `labelT`, fyzický PASS CraftPlanu NOT RUN |
| UX-23 | **OPRAVA VLASTNÍKA: v liště jednoduše zapínat/vypínat SAMOSTATNÉ POSOUVÁNÍ ČÍSELNÝCH POPISKŮ kót podél čáry.** Když je „Posun čísel“ vypnutý, tažení po čísle jej nesmí nechtěně posunout relativně vůči jeho kótě; koncové úchopy, tažení celé kóty a zadávání/úprava skutečné hodnoty dál fungují obvykle. Při přesunu celé kóty popisek zůstává připojen na své relativní pozici a přesouvá se spolu s kótou, nikoli nezávisle po ní. Po zapnutí lze číslo zase záměrně přesunout. **Není to zamknutí hodnoty měření, celé kóty ani lišty.** | **UPŘESNĚNO VLASTNÍKEM 22. 11. 2026 – předchozí výklad jako zámek geometrie byl CHYBNÝ A NAHRAZEN**. Výchozí stav ON/OFF a finální název/ikona zůstávají k testování; návrh asistenta: výchozí posouvání vypnuté, v liště ruční zapnutí |

## Závazné shrnutí kóty pro další UX návrh (výslovně potvrzeno vlastníkem 22. 11. 2026)

**Princip: v CraftPlanu má kóta nejen fungovat, ale i vypadat co nejvěrněji jako v Zaměřovači 1. Vlastník výslovně potvrzuje, že vzhled a tvary úchopů, jejich poměry, natahování, uchopení středu, otáčení, lupa a automatická čitelnost čísla už byly dobře vymyšlené a dotažené; není cílem je znovu navrhovat.** Při návrhu/testu nejprve porovnat referenční funkční chování v1, ale **nepřebírat automaticky její kód, data ani výkonové chyby**.

**Vizuální reference před realizací:** doložit konkrétní referenční vzhled kóty a úchopů ve v1 (snímky/ověření na původním editoru, pouze bezpečné testovací podklady); nespoléhat na orientační skici z chatu. Zachovávat motiv a ergonomii, ale ne automaticky přenášet implementaci v1.\n\n**Jediné výslovně požadované uživatelské změny oproti této referenci nyní:**
1. Nahradit `?` u dosud nezměřené kóty **malou ikonou skládacího dřevěného metru s ryskami** (ne svinovací pásmo).
2. **Opravit zadávací okno**, aby systémová klávesnice nikdy nepřekryla akci **Uložit hodnotu**; adaptovat layout pro viditelnou plochu a orientaci.
3. **Odstranit sekání/latenci** při tažení a posunu, při zachování již vyhovujícího poměru úchopů a chování; ověřit skutečným prstem/S Penem a sériemi tahů, OFF/ON diagnostika.

Po vytvoření kóty zůstává vybraná; klepnutí do volného místa výběr zruší; klepnutí na ni umožní editaci hodnoty. Budoucí laser bezpečně zapisuje do jednoznačně označené cílové kóty podle pravidel F03/CP3-05. **Tohle je schválený funkční směr, nikoli runtime PASS nebo technologické/implementační GO.** Další odlišnosti oproti v1 navrhovat pouze samostatně s důvodem a odsouhlasením vlastníka.

**Nový referenční zdroj kóty:** [Technologicky neutrální reference vzhledu a ovládání kóty](CRAFTPLAN_KOTA_REFERENCNI_UX_KONTRAKT.md) přebírá doložené proporce úchopů, gest, posunu čísla a lupy z konkrétní revize v1. **Později výslovně opraveno:** ovladač v liště má zabránit nechtěnému samostatnému posunu čísel, nikoli zamykat geometrii kót.\n\n## Otevřené návrhové otázky k dalšímu projednání

1. **Lišta:** bude swipe, šipka, vyjížděcí panel, nebo uživatelsky volitelná kombinace? Jak přesně přesunout, přeskupit a zamknout bez kolize s kreslením, lupou, scrollováním, S Penem a změnou orientace? Dva základní ovladače musí být stále dostupné.
2. **Výběr/Přidání a Opakování:** přesná definice každého přepínače, jedna vs. opakovaná akce, krátký/dlouhý stisk, označení stavu, návrat do režimu výběru a interakce s Undo/Redo.
3. **Kóta a zadání skutečné míry:** postup vytvořit → zůstane vybraná → klepnutím na kótu otevřít hodnotu → klepnutím mimo odznačit je potvrzen. **Motiv malé ikonky skládacího dřevěného metru s ryskami namísto `?` je potvrzen**, otevřen zůstává konkrétní grafika, poloha a adaptivní chování zadávacího okna nad klávesnicí, stavy při zrušení/vymazání čísla a fyzické ověření gest. F03 laser je pozdější integrace. **Nové upřesnění:** zatím se navrhuje přepínač možnosti *samostatně posouvat čísla*, nikoli zámek celé kóty. Výchozí stav a zobrazení ovladače jsou otevřené. 
4. **Celá navigace:** rychlé zaměření vs. práce v zakázce, galerie, editory dalších dokumentů, menu a nastavení, práce na výšku/šířku.
5. **Vizuální jazyk:** barvy, ikony, rozměry, vzhled lišty a výchozí pořadí dosud neschváleny. Skici v chatu byly pouze orientační ilustrace.

**Zámek celé geometrie kóty:** vlastník se na jeho smysl ptá, zatím ho neschválil. **Není součástí UX-23**; případná potřeba hromadného zámku anotací se posoudí odděleně a až podle zkušeností s editorem.\n\n## Bezpečnost a způsob další práce

- Každé přeuspořádání nástrojů nebo přepnutí jejich zobrazení nesmí změnit měření, jednotky, obsah dokumentu ani originál fotografie.
- Rozlišit rolování nabídky od přetažení nástroje/lišty a od práce na fotografii. Vybraný nástroj ani běžící gesto se nesmí změnit náhodně.
- Před dalšími významnými UX rozhodnutími porovnat odpovídající část původní specifikace a přidat nové potvrzené rozhodnutí do tohoto registru v samostatné dokumentační větvi. Neprovádět implementaci, testy, Actions, merge ani APK bez samostatného GO.
- **Důkazy**: T01–T44 v M0-A jsou návrhy testů, **NOT RUN**; na skutečném Androidu zatím nic z nové aplikace neověřeno.
- **Rozhodnuto vs. uloženo:** tento soubor zachycuje stav rozhovoru 22. 11. 2026; nové změny v dalších chatech až po konkrétním commitu/odkazu, ne pouhým slibem.

## Zdroje (historické názvy souborů jsou podklady CraftPlanu)

- [PR #1 – katalog, ergonomie, diagnostika, verze](https://github.com/zaplikj30-ui/CraftPlan/pull/1).
- [PR #2 – hlavní plán a rozlišení M0/M1/M2](https://github.com/zaplikj30-ui/CraftPlan/pull/2).
- [PR #3 – M0-A kontrakty a návrhové testy T01–T44](https://github.com/zaplikj30-ui/CraftPlan/pull/3).
- **Původ nových UX-01 až UX-23:** výslovná rozhodnutí, preference a otázky vlastníka v projektové konverzaci CraftPlan dne 22. 11. 2026. Nejde o převzetí starého Zaměřovače 1 jako produktu.

**Důležité:** PR #1–#3 a `main` se nemění; tento soubor je samostatná pracovní UX dokumentace k budoucí kontrole.
