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
| UX-16 | U kót zachovat osvědčené úchopy, žluté čáry, přesnou lupu, čitelnost čísel a možnost přesunu celé kóty středem, koncového bodu koncem. Primární problém v1 je sekání; nepřevádět jeho kód či produkční data. | **Dříve potvrzený ergonomický cíl podle PR #1**, finální UI CraftPlanu neodsouhlaseno |

## Otevřené návrhové otázky k dalšímu projednání

1. **Lišta:** bude swipe, šipka, vyjížděcí panel, nebo uživatelsky volitelná kombinace? Jak přesně přesunout, přeskupit a zamknout bez kolize s kreslením, lupou, scrollováním, S Penem a změnou orientace? Dva základní ovladače musí být stále dostupné.
2. **Výběr/Přidání a Opakování:** přesná definice každého přepínače, jedna vs. opakovaná akce, krátký/dlouhý stisk, označení stavu, návrat do režimu výběru a interakce s Undo/Redo.
3. **Kóta a zadání skutečné míry:** při jaké akci se otevře číslo/jednotka, stavy „nezměřeno“, ruční zadání, úchopy, lupa a kolize gest. Nevymýšlet nové ovládání již schválených úchopů bez předvedení rozdílu.
4. **Celá navigace:** rychlé zaměření vs. práce v zakázce, galerie, editory dalších dokumentů, menu a nastavení, práce na výšku/šířku.
5. **Vizuální jazyk:** barvy, ikony, rozměry, vzhled lišty a výchozí pořadí dosud neschváleny. Skici v chatu byly pouze orientační ilustrace.

## Bezpečnost a způsob další práce

- Každé přeuspořádání nástrojů nebo přepnutí jejich zobrazení nesmí změnit měření, jednotky, obsah dokumentu ani originál fotografie.
- Rozlišit rolování nabídky od přetažení nástroje/lišty a od práce na fotografii. Vybraný nástroj ani běžící gesto se nesmí změnit náhodně.
- Před dalšími významnými UX rozhodnutími porovnat odpovídající část původní specifikace a přidat nové potvrzené rozhodnutí do tohoto registru v samostatné dokumentační větvi. Neprovádět implementaci, testy, Actions, merge ani APK bez samostatného GO.
- **Důkazy**: T01–T44 v M0-A jsou návrhy testů, **NOT RUN**; na skutečném Androidu zatím nic z nové aplikace neověřeno.
- **Rozhodnuto vs. uloženo:** tento soubor zachycuje stav rozhovoru 22. 11. 2026; nové změny v dalších chatech až po konkrétním commitu/odkazu, ne pouhým slibem.

## Zdroje (historické názvy souborů jsou podklady CraftPlanu)

- [PR #1 – katalog, ergonomie, diagnostika, verze](https://github.com/zaplikj30-ui/CraftPlan/pull/1).
- [PR #2 – hlavní plán a rozlišení M0/M1/M2](https://github.com/zaplikj30-ui/CraftPlan/pull/2).
- [PR #3 – M0-A kontrakty a návrhové testy T01–T44](https://github.com/zaplikj30-ui/CraftPlan/pull/3).
- **Původ nových UX-01 až UX-15:** výslovná rozhodnutí, preference a otázky vlastníka v projektové konverzaci CraftPlan dne 22. 11. 2026. Nejde o převzetí starého Zaměřovače 1 jako produktu.

**Důležité:** PR #1–#3 a `main` se nemění; tento soubor je samostatná pracovní UX dokumentace k budoucí kontrole.
