# CraftPlan – pravidla pro Codex a AI (pracovní větev M1)

Tento `AGENTS.md` je **jediné aktuální pracovní pravidlo pro implementaci na této větvi**. Podrobné produktové požadavky, návrhy a historie zůstávají v existujících PR. Neopisuj je do zadání. Starší `AGENTS.md` a šablona zadání v dosud nesloučeném PR #1 jsou historické a nesmějí přebít novější rozhodnutí vlastníka ani tento pracovní postup.

## Kde čerpat pravdu

- Nejvyšší prioritu má **výslovný GO vlastníka s jeho omezeným rozsahem**, nejnovější schválené rozhodnutí a bezpečnostní kontrakty. Souhlas s UX návrhem sám o sobě není GO k programování, sestavení či vydání.
- [PR #1](https://github.com/zaplikj30-ui/CraftPlan/pull/1) – úplný katalog a historie; [PR #2](https://github.com/zaplikj30-ui/CraftPlan/pull/2) – etapy; [PR #3](https://github.com/zaplikj30-ui/CraftPlan/pull/3) – přesné hodnoty, identity, revize a obnova; [PR #4](https://github.com/zaplikj30-ui/CraftPlan/pull/4) – UX; [PR #5](https://github.com/zaplikj30-ui/CraftPlan/pull/5) – schválená technologie M0-D; [PR #6](https://github.com/zaplikj30-ui/CraftPlan/pull/6) – fotografie. Čti relevantní sekce a nezbytné návaznosti, nikoli automaticky celý projekt.
- M1: Kotlin, Compose pro běžné UI, jeden Android View/Canvas editor, Android 13+ (minSdk 33, compileSdk 36), Room 2.8.5 + BundledSQLiteDriver/SQLite Bundled 2.7.1 **až v povoleném kroku ukládání**. Prostředí Codexu už je připraveno; zbytečně znovu neinstaluj závislosti.

## Jak zadávat a dokončovat úkoly

1. **Zadání pro Codex piš krátce:** GO, jeden konkrétní cíl, pracovní větev, relevantní PR/oddíl, co se nesmí změnit, testy a očekávaný výstup. Nepřepisuj celé dokumenty, nepřidávej opakované obecné checklisty. Podrobnosti dohledá autor v PR.
2. Pracuj po malých funkčních krocích. Před změnou ověř současný `HEAD` a případné souběžné změny. Zachovej celý katalog budoucích funkcí; odložení není zrušení. Nezakládej automaticky nová PR ani dokumenty, neměň `main`, neprováděj merge ani force-push.
3. **Schválené implementační GO na určené existující pracovní větvi standardně zahrnuje dokončení: cílené testy → kontrola relevantního diffu → commit → běžný fast-forward push do téže větve → ověření vzdáleného HEAD.** Není třeba dodatečné zadání „ještě to odešli“. Výjimka: výslovné `LOCAL ONLY`, nevyřešený konflikt, chybějící oprávnění nebo push, který by spustil **nepovolený build/Actions/APK**; v takovém případě NIC nespouštěj a uveď `LOCAL ONLY`, SHA a přesnou překážku. Automatický push není povolením workflow, vydání ani výdajů. Před pushem ověř podmínky workflow.
4. **Sestavení, GitHub Actions, APK, emulátor, technologické experimenty, placené služby a HOME runner vyžadují vlastní výslovné GO pro daný krok.** Žádné zákaznické fotografie či nenahraditelná data, dokud neprokážeme bezpečný save/reopen a M2 zálohu s obnovou.
5. Před předáním kontroluj celý **relevantní rozdíl**, běžné i chybové cesty; provedené testy musí být skutečné. Rizikové změny dej k cílenému nezávislému review, ne opakovaným auditům celého projektu. Opravy oprav a ověř, neobcházej kontrolu.
6. **Výstup krátce česky:** skutečné commit SHA a ověřený vzdálený HEAD/odkaz (nebo `LOCAL ONLY`), změněné soubory, co a proč, testy `PASS/FAIL`, `NOT RUN` a rizika. `PASS` kompilace není důkaz spuštění na telefonu, fyzických gest, trvalého uložení ani obnovy.

## Neporušitelné zásady ochrany práce

- Přesné naměřené délky nikdy neodvozuj z pixelů ani neukládej jako `Float/Double`. `0 mm` je platná hodnota a **není** `NEZMĚŘENO`. Zachovej původní text, jednotku, přesný decimal/rational a jejich validaci dle PR #3.
- Fotografie, kóty a revize mají stabilní identity; kotvy patří ke správné fotografii. Odděl měření, geometrii, gesta, vykreslení a ukládání; současné uživatelsky ověřené ovládání neměň mimo GO.
- `ULOŽENO` až po trvalém potvrzení celé konzistentní revize včetně obrazových bajtů, metadat a anotací. Zápis/import/pád nesmí zničit poslední platnou revizi. Export nesmí nechtěně odhalit skryté poznámky.
- Původní Zaměřovač 1 nikdy neměň. Veřejný GitHub nesmí obsahovat soukromé zakázky, zákaznická data, hesla ani klíče. Pro vývoj zatím jen syntetická a nahraditelná data.
- Pokud je řešení zbytečně složité, navrhni menší bezpečnou variantu bez zrušení schválených funkcí. Cloud/synchronizaci nyní neimplementuj; stabilní identity/revize a ochranu konfliktů zachovej pro budoucnost.

**Cíl: skutečně funkční offline M1, nikoli další byrokracie.**
