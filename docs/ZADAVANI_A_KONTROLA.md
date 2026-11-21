# CraftPlan – zadání programátora a důkazní protokol

**Pravidlo:** toto je šablona, ne oprávnění ke spuštění kódu, Actions, merge nebo APK.

## Jedno kopírovatelné zadání

```text
KOMU: programátor / jiný samostatný chat
AI: přesná dostupná verze a úsilí; prostředí; proč
REPO: zaplikj30-ui/CraftPlan
BRANCH/PR: izolovaná větev a draft PR, odkaz
HEAD/BASE: aktuálně živě ověřené commit SHA
ZDROJ PRAVDY: AGENTS.md, START_ZDE.md, relevantní CraftPlan zadání/issue
CÍL: konkrétní jeden funkční celek a měřitelná kritéria
HRANICE: co se nemění, ochrana přesných hodnot, médií, ID, Undo/Redo, offline
TECHNICKÝ POSTUP: minimální bezpečná implementace; osvědčená řešení vs alternativy
TESTY: věrné pozitivní, negativní, hraniční, regresní a výkonové podle rizika;
       SOURCE / lokální / Actions / emulátor / fyzický Android odděleně
POVINNÁ AUTORSKÁ BRÁNA: sám na aktuálním SHA proveď hluboké oponentské
       SOURCE/SECURITY review CELÉHO kumulativního diffu proti base jako jiný
       nezávislý kontrolor; zkontroluj skutečná produkční volání, data/UI/gesta,
       oprávnění, výkon a hraniční scénáře; vlastní nálezy oprav, retestuj,
       znovu projdi diff na NOVÉM SHA až bez známých opravitelných FAIL.
PŘEDÁNÍ: do PR zapiš SHA/base, celý rozsah review, PASS/FAIL/SKIP/NOT RUN/BLOCKED,
       přesné důkazy, neověřená místa, rizika a další krok.
HOTOVO: až autorská brána PASS a bez známých opravitelných FAIL; teprve potom
       jiný nezávislý kontrolor. Nezávislé APPROVE SOURCE není GO pro merge.
STOP: konflikt HEAD/base, možná ztráta dat, neshoda zadání, blokér, rozpočet.
NEPOVOLENO: HOME, automatický cloud/Actions, merge, APK, výdaje bez GO vlastníka.
```

**Evidence v PR:** název funkce, base→HEAD, změněné soubory, zdrojové vazby, regresní scénáře, tabulka výsledků, rizika, vlastní verdikt a nové SHA po opravách. Nevyužívej starý PASS jedničky jako důkaz dvojky.

**Nezávislý kontrolor** je jiný chat/osoba. Neopravuje; vrací APPROVE SOURCE / CHANGES REQUIRED / STOP a v případě nálezu následuje oprava → retesty → nové autorské review → nová nezávislá kontrola. Vlastník rozhoduje o merge, vydání a zásadních změnách.
