# AGENTS.md – pravidla práce AI pro CraftPlan

**Projekt:** [CraftPlan](https://github.com/zaplikj30-ui/CraftPlan), nová nezávislá aplikace. **Původní Zaměřovač 1** ([Zamerovac-android](https://github.com/zaplikj30-ui/Zamerovac-android)) je pouze historická/funkční reference, není součástí tohoto repozitáře. Konfigurátor–Nářezák a Převaděč spojitosti zůstávají samostatné. Nikdy tiše nekopíruj staré PR, SHA, workflow, GO ani známé chyby jako aktuální úkol CraftPlanu.

## Zdroj pravdy, stav a rozhodnutí

1. Čti **[START_ZDE.md](START_ZDE.md)** a [jednotný základ](docs/ZAMEROVAC_2_JEDNOTNY_ZAKLAD_A_PRENOS.md), příslušné podrobné funkční zadání, aktuální HEAD/base, relevantní PR/issues a nové důkazy. Názvy souborů „ZAMEROVAC_2“ jsou historické pracovní označení nynějšího **CraftPlanu**.
2. **Novější výslovné rozhodnutí vlastníka pro CraftPlan má přednost před starým textem**, pokud neporušuje platná bezpečnostní pravidla. Zjištěný ROZPOR/NEOVĚŘENO zaznamenej a odstraň vysvětleným rozhodnutím; neber starý text jako povel, neslévej tiše různé varianty. Nová rozhodnutí trvale zapisuj do **tohoto** repozitáře v izolovaném PR. Historické odkazy na Zaměřovač 1 slouží k ověření, nikoli ke změně v1.
3. Rozlišuj **nápad / schválené funkční zadání / schválený technický návrh / implementaci / automatický PASS / fyzicky ověřeno / vydáno**. Neprohlašuj dokumentaci, starý test, syntetiku či SOURCE review za fungující Android. U každého tvrzení označ FAKT, ODHAD či NEOVĚŘENO, pokud to není zřejmé.
4. Dokumentace ani její přijetí **nejsou GO** pro programování, cloud testy, merge nebo APK. Vlastník výslovně schvaluje zásadní rozsah, architekturu/technologii, merge, vydání, výdaje a rizikové akce.

## Role a povinná autorská kontrola

- **KOORDINÁTOR** určí cíl, prioritu, autora, rozsah, rizika, důkazy a HOTOVO/STOP; není nezávislým kontrolorem vlastní práce. Srovná A) malou vratnou opravu, B) osvědčenou alternativní technologii/AI/prostředí, C) částečnou či úplnou přestavbu podle důkazů, dat, nákladů a výkonu. Po dvou selháních či vysokém nákladu rozhodne POKRAČOVAT / ZMĚNIT SMĚR / STOP.
- **PROGRAMÁTOR (autor každého kódu)** před zásahem ověří HEAD/base, souběžné PR, zadání, existující kód a relevantní produkční vazby. Sám implementuje, testuje, opravuje a **PŘED PŘEDÁNÍM provede plnohodnotné vlastní oponentské SOURCE/SECURITY review v hloubce nezávislého kontrolora**: celý kumulativní diff proti base na aktuálním SHA, reálné produkční cesty, data/média/ID/jednotky, UI/gesta, hraniční stavy, výkon, bezpečnost a důkazy. Aktivně hledá vlastní přehlédnutí; zelené testy nestačí. Každý opravitelný nález opraví, retestuje a znovu zkontroluje celý dotčený diff **na novém SHA**, až bez známých opravitelných FAIL. V PR doloží SHA/base, rozsah, testy a PASS/FAIL/SKIP/NOT RUN/BLOCKED, rizika a vlastní verdikt. BLOCKED není HOTOVO.
- **NEZÁVISLÝ KONTROLOR** = jiný chat/osoba. Nastupuje až po dokončené autorské bráně; čte celý aktuální diff, zadání, produkční vazby, důkazy a bezpečnost. Neopravuje. Verdikt **APPROVE SOURCE / CHANGES REQUIRED / STOP**; nález → autorova oprava + retesty + nové vlastní review → znovu nezávislé review aktuálního SHA. SOURCE schválení není merge ani runtime PASS.
- **VLASTNÍK** rozhoduje o významném rozsahu, technologii, změně pravidel, merge, veřejném vydání a platbách. U významné bezpečnostní/architektonické změny nejprve DESIGN/SECURITY review. Každá věc má izolovanou větev/draft PR, žádné přepisování cizí práce.
- Zadání autorovi v jednom kopírovatelném bloku: komu; přesná dostupná AI/verze a uvažování, prostředí, proč; cíl; hranice; aktuální SHA/base; odkaz na úplné zadání; testy; **povinná důkladná autorská SOURCE/SECURITY kontrola a retesty**; HOTOVO/STOP; důkazní výstup. Viz [šablona](docs/ZADAVANI_A_KONTROLA.md). Nežádej opakovaně již udělený souhlas.

## Pravidlo jednoduché a bezpečné varianty (rozhodnutí vlastníka 22. 11. 2026)

- **Netrvat na složité variantě za každou cenu.** Pokud se při návrhu, kontrole či schválené zkoušce ukáže, že řešení nejde spolehlivě provést, je příliš složité, drahé, rizikové nebo náročné na údržbu, **aktivně navrhni jednodušší bezpečnou alternativu**, využití osvědčeného hotového řešení, menší první etapu nebo odložení problematické části. Neopírej rozhodnutí jen o dojem: stručně uveď konkrétní problém a důkazy či nejistotu, přínosy a nevýhody variant a dopad na budoucí rozšíření. Navazuje to na pravidlo koordinátora **POKRAČOVAT / ZMĚNIT SMĚR / STOP** po dvou selháních či vysokém nákladu.
- **Zjednodušení nesmí snížit bezpečnost dat ani přesnost měření.** Nesmí tiše změnit kóty/jednotky, poškodit či přepsat originální média, ztratit potvrzenou práci, předstírat úspěšné uložení nebo odstranit ověřitelnou zálohu a obnovu před používáním nenahraditelných zakázek. **Schválenou funkci neškrtat bez souhlasu vlastníka**; odložené požadavky a jejich ID ponechat v katalogu. Rozhodnutí dokumentovat stručně v příslušných **existujících pravidlech či návrhových dokumentech**, nepřidávat automaticky nový dokument a nové PR pro každé jednotlivé upřesnění. Nové změny během probíhající kontroly výslovně označit novým SHA a umožnit opakovanou kontrolu změněné části.

## Produkt a bezpečnost dat

- **Záměr:** první malý funkční, skutečně **offline Android prototyp** v Kotlinu + Jetpack Compose; volba konkrétních knihoven, datového schématu a implementace vyžaduje ověření a samostatný GO. SQLite/Room je preferovaný kandidát ke zkoušce, ne hotové řešení. Nezaváděj nyní účty, síť, sync frontu, server, 3D/AI či plugin engine „do zásoby“. Doménový model/výpočty odděl od UI/hardwaru pro budoucí Windows/iOS/iPadOS.
- **Přesnost a neztrácení práce:** kóta = přesná hodnota, jednotka, zdroj měření a editovatelná geometrie; nikdy nedopočítej skutečnou míru z pixelů. Zachovej původní bajty fotografií/médií, stabilní ID a vazby, Undo/Redo, koš, potvrzený save, bezpečnou obnovu, pozdější zálohy/import/export a ochranu konfliktů při budoucím sync. Neoznamuj uloženo před potvrzením. Starší stav nikdy tiše nepřepiš přes novější. U testů používej syntetická data; nevynucuj zálohu nepotřebných testovacích zakázek.
- **Ergonomie:** schválené kóty, šipky, čáry, úchopy a lupu neměň bez souhlasu. Věrně testuj prst, S Pen, různé Android telefony/tablety, výkon. ImageMeter/MeasureOn pouze funkční reference, nikdy cizí kód/grafika/kopie UI.
- **Další jazyky a jednotky:** čeština první; angličtina první budoucí překlad, ne plošná lokalizace nyní. Metrické/imperiální převody nesmějí ztrácet přesnost.
- **Soukromí:** repozitář je PUBLIC – žádné klíče, tokeny, přihlašovací údaje, skutečné osobní zakázky, fotografie, GPS ani interní hesla v gitu, testech a reportech. Nedůvěryhodné vstupy, oprávnění, externí obsah, importy i případný JS/native bridge bezpečnostně oponuj.

## Testování, nápověda a vydání

- Testy podle rizika. U kóty/gest: skutečný vstup → změna modelu → obraz → save → reopen → Undo/Redo, pozitivní/negativní/hraniční, přesná čísla a původní bajty. U dat: plný disk, přerušení, zamítnutí oprávnění, selhání a cleanup. Důkazy rozlišuj SOURCE / lokální test / GitHub Actions / emulátor / fyzický Android / konkrétní laser; netestované **NOT RUN**. Po chybě z telefonu zapiš reprodukci v issue a pokud lze trvalou věrnou regresi RED před → PASS po na novém SHA.
- **Diagnostika** od první M1 malé funkce: volitelná a izolovaná, měří časy/odezvu a základní jank bez zákaznických dat; změř režii zap/vyp. Nesmí ovlivňovat samotný editor ani bezpečnost uložení. Syntetický autotester pouze zvlášť povolené TEST sestavení.
- Při nové funkci, změně UX/textů/nastavení/importu/exportu aktualizuj v **témže PR** příslušnou **offline nápovědu a testy** nebo dolož nerelevanci. Changelog/verze před vydáním; plán první v2 `0.1.0-test`, `1.0.0` až po novém GO. Nová app musí mít jiný package ID, podpis/datový prostor než v1 a rostoucí versionCode.
- **Build ≠ vydání ≠ instalace ≠ fyzický PASS.** APK pouze po odpovídajících regresích, nezávislém review a konkrétním GO vlastníka.

## GitHub Actions, HOME a náklady

- Projekt je **PUBLIC**. Standardní GitHub-hosted Actions mohou být podle podmínek GitHubu zdarma; neplatí obecně pro larger runnery ani externí placené služby. Nové workflow, cloud běh, merge ani APK **nespouštěj automaticky**. Schválený směr: jednoduchý hosted Chromium, bez vlastní P/Q/E/SHA byrokracie. Před případným testem ověř cenu, práva tokenu a skutečné podmínky, drž limit **12 USD/měsíc**; blízko stropu STOP.
- **HOME runner Zamerovac-Win11-QA RETIRED** – OFFLINE, služba STOP/Disabled. Nezapínej, nepřipojuj, neinstaluj, nespouštěj cizí PR na HOME. Staré workflow a přílohy v1 nejsou povolení.
- Po dokončení shrň v češtině výsledek, důkazy, rizika, zlepšení/zhoršení, hodnocení 1–10 opřené o důkazy a **TEĎ / POZDĚJI / NEMĚNIT**. Do PR zapisuj kompletní technický protokol a přímé URL, do chatu stručné vysvětlení.

**Pozor:** nové nastavení projektu ChatGPT se nemění přidáním tohoto souboru. Pokud se budou měnit projektové pokyny ChatGPT, vlastník je musí zvlášť změnit v nastavení.
