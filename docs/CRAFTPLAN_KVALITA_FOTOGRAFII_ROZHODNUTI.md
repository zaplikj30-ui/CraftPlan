# CraftPlan – kvalita fotografií při focení a importu

**Rozhodnutí vlastníka – 22. 11. 2026, poslední upřesnění:** každá fotografie se trvale uloží **jen jednou v CraftPlanu ve zvolené kvalitě**. Nové snímky se automaticky NEukládají také do systémové Galerie telefonu. Není povinný skrytý interní archiv originálů. Dřívější varianta „Do aplikace i do galerie“ je nahrazena; historicky zůstává dohledatelná v předchozích revizích PR. Externí originály při importu zůstávají nedotčené.
**Stav:** **POTVRZENÉ funkční/UX zadání**; datové a technické detaily níže označené **OTEVŘENO**.  
**Rozsah:** nové fotografie fotoaparátem a přidání existujících fotografií z galerie/souborů do editoru či zakázky.  
**Nejde o:** schválení technologie, implementace, testu, změnu již existujících souborů, automatický merge nebo povolení pracovat s nenahraditelnými zákaznickými daty.

## 1. Potvrzené chování – dvě volby kvality, jeden interní obrazový podklad

**Nastavení → Fotografie** obsahuje dvě nezávislé volby kvality. **Místo ukládání není přepínač:** uložená fotografie je pouze v datech CraftPlanu, ne zároveň jako nový snímek v systémové Galerii.

| Nastavení | Účel | Volby |
|---|---|---|
| **Kvalita při fotografování** | Výchozí nastavení pro **nově pořízené** fotografie v CraftPlanu | **Úsporná / Střední / Plná** |
| **Kvalita při importu** | Výchozí nastavení pro **existující** fotografie přidávané z galerie/souborů | **Úsporná / Střední / Plná** |

- **První instalace:** fotografování **Plná**, import **Plná**. Obě volby si nezávisle pamatují změny. Každá nově pořízená/importovaná fotografie vytvoří **jednu trvale uloženou verzi v aktuálně vybrané kvalitě v CraftPlanu**; nevytváří druhý plný interní archiv ani automatickou kopii v Galerii.
- **Při konkrétním fotografování** musí být možné zvolenou kvalitu vidět a **změnit jen pro daný snímek** (přímo v procesu focení / přidání před finálním potvrzením fotografie).
- **Při konkrétním importu** musí být možné zvolenou kvalitu vidět a **změnit jen pro právě přidávanou fotografii** před potvrzením.
- Jednorázová změna kvality platí jen pro dané focení/import a nemění globální výchozí úroveň. Žádná volba druhého místa ukládání již není součástí zadání.
- Přechod do editoru zůstává s potvrzovacím náhledem **OK / Znovu / Storno** tam, kde to pro daný vstup dává smysl; samotné zpracování nesmí potichu změnit nebo poškodit kóty.
- **Kvalita uložené fotografie** je něco jiného než **dočasná kvalita zobrazení / vykreslovací výkon editoru (F17)** a něco jiného než **velikost a kvalita zákaznického PDF/JPG exportu**. Tři různé věci neslučovat do jediného přepínače.
- Změna výchozího nastavení nemění **dříve uložené** fotografie. Zmenšená fotografie nezíská dodatečně ztracené detaily přepnutím na „Plná“.

### Co znamenají tři názvy

- **Úsporná:** menší pracovní soubor, méně obrazového detailu.
- **Střední:** kompromis velikosti a obrazového detailu.
- **Plná:** maximální *dostupná* kvalita daného zdroje; neslibovat vyšší kvalitu, než skutečně existuje.

**OTEVŘENO:** přesné rozlišení, maximální delší strana, kompresní parametry, podporované formáty, barevný profil, zacházení s metadaty a případná velikostní indikace. Žádná číselná MP/JPEG hranice není tímto rozhodnutím schválena. Nastavení není totožné s ImageMeter „Maximum texture resolution“.

## 2. Ochrana původních souborů a jediné uložené fotografie

**POTVRZENO:** existující zdrojové soubory mimo aplikaci se nikdy nemění, nemažou ani nepřepisují. CraftPlan vede pro každou fotografii jednu trvale uloženou obrazovou verzi ve vybrané kvalitě.

1. **Import:** existující externí originál (Galerie/soubory) zůstává ve svém původním umístění a bajtech. Do CraftPlanu se uloží jedna vlastní použitelná verze vybrané kvality; externí zdroj není druhá kopie vytvářená aplikací ani spolehlivá součást její zálohy.
2. **Evidence a anotace:** uložená verze má ID, kvalitu, skutečné rozměry a orientační stav uložených bajtů, jedinou verzovanou mapu těchto bajtů do kanonického prostoru kotev, samostatně evidovaný původní EXIF a kontrolu integrity. Kóty a poznámky jsou samostatně editovatelné. Náhledy/cache a dočasné soubory sloužící pro bezpečný zápis nejsou druhou trvale uloženou originální fotografií.
3. **Pořízení snímku v CraftPlanu:**
   - Uloží se **právě jedna autoritativní obrazová verze** v kvalitě **Úsporná / Střední / Plná**, pouze uvnitř aplikace; do systémové Galerie se automaticky nic nového nezapisuje. Nižší kvalita není později vratná do plného rozlišení. Případný technický plný mezisoubor kamery není druhý trvalý archiv; před bezpečným potvrzením vybrané verze se nesmí předčasně ztratit. Společné pravidlo ochrany potřebného mezimédia níže platí i pro import.
   - **Pád/storno/plný disk:** výsledek nepovažovat za uložený před potvrzením kompletní verze a kót. Poslední potvrzené soubory zachovat; rozpracované mezisoubory izolovat a řešit bez destrukce platné práce.
4. **Záloha a obnova:** M2 záloha zahrnuje skutečně **jedinou interně uloženou verzi v její zvolené kvalitě** a příslušné editovatelné údaje. Na novém zařízení se musí obnovit **stejné bajty uložené verze**, nikoli neexistující plný originál tam, kde byl uložen jen zmenšený snímek. Záloha celé zakázky nesmí pro svou obnovu vyžadovat původní externí zdroj, systémovou Galerii ani původní telefon.
5. **Stabilita měření:** kvalita obrazu nikdy nesmí měnit skutečné naměřené číslo, jednotku ani původ měření, pouze případnou viditelnost obrazového detailu. Převody rozměrů, EXIF orientace/zrcadlení, souřadnice ukotvených kót, následná výměna fotografie F09 a export musí zůstat jednoznačné a editovatelné. Zdrojový EXIF je informace o původu, nikoli pokyn k opakovanému otočení již normalizovaných bajtů.
6. **Selhání:** chybějící přístup ke zdroji, nedostatek místa, přerušení importu či pořízení, chyba komprese/rotace nesmí vést k falešnému „uloženo“, nahrazení poslední platné fotografie ani tichému poškození zdroje.

**Povinný bezpečnostní kontrakt potřebného mezimédia – focení i import (HIGH, návaznost PR #3 §6.1.1/§6.1.2):** Jakmile aplikace převezme pořízený snímek nebo data určená k importu, musí před ztrátovým zpracováním zabezpečit **soukromý, trvale obnovitelný staging potřebných obrazových bajtů** a jeho jednoznačnou identitu/vazbu na rozpracovanou operaci; při importu se nesmí spoléhat na to, že externí soubor či oprávnění vydrží do dalšího spuštění. Tento staging chránit, dokud není úplně ověřena a trvale potvrzena **jediná zvolená interní verze spolu s příslušnou revizí**, nebo uživatel výslovně zvolí `Storno`. Při chybě komprese, plném disku, ztraceném ACK, pádu či restartu **neodstraňovat jediné stále potřebné obnovitelné médium**: ověřit stav, ponechat kandidáta odděleného od potvrzené revize a nabídnout nedestruktivní obnovení či opakování, pokud je staging skutečně trvale dostupný. Neobnovený čistě paměťový snímek nelze slibovat; pokud se nepodařilo zajistit trvalý staging, operace se nesmí vydávat za bezpečně zachycenou. Po vědomém `Storno` nebo úspěšném potvrzení odstranit dočasná data **jen po ověření**, že už nejsou potřebná pro žádnou potvrzenou revizi, recovery/draft, Koš ani běžící zálohu. Nejde o povinný druhý trvalý archiv plných fotografií.

**Povinný kontrakt jediné orientační mapy (MEDIUM, PR #3 §5 a §7.1.1):** Autoritou jsou **skutečně uložené obrazové bajty** a k nim právě **jedna verzovaná transformace ze souřadnic těchto bajtů do kanonického prostoru kotev** (po fyzické normalizaci/otočení uloženého obrazu může být identická). Zaznamenat skutečné rozměry a orientační stav uložených bajtů; původní zdrojový EXIF/orientaci uchovávat **odděleně pouze jako provenienci**, nikoli jako druhou transformaci k aplikování. Při uložení, znovuotevření, M2 ZIP/obnově, výměně podkladu F09 i exportu nesmí dojít k druhé rotaci/zrcadlení ani k dvojímu přepočtu kotev. Při obnově zachovat **identické interní bajty, uloženou mapu a editovatelné kotvy**; nepřepočítávat orientaci z historického zdrojového EXIF. Neshodná/chybějící povinná mapa je chyba validace, nikoliv důvod potichu změnit kóty.

**Povinný kontrakt pravdivého `ULOŽENO` (HIGH, PR #3 §6.1.1/§6.1.1a a PR #5 §3.3.2):** Jednotkou potvrzení je **celá jedna konzistentní revize** fotografie, přesných kót, poznámek, všech povinných příloh a všech jejich ID/vazeb. Nové obrazové bajty musí být nejprve ověřené a trvale dostupné **pod finální cestou/jménem**, včetně potřebné garance trvalosti názvu v adresáři nebo doloženého ekvivalentu; teprve poté lze trvale publikovat úplný model a aktuální revizní ukazatel. Samotný soubor, náhled, úspěšná komprese nebo samostatný DB commit není potvrzením celého grafu. `ULOŽENO` smí označovat pouze právě zobrazenou `editGeneration` po ověřeném trvalém potvrzení všech povinných dat, nikoli novější změny na základě opožděného ACK starší operace. Při nejasném výsledku `OVĚŘUJI/NEPOTVRZENO`; `Zkusit znovu` musí respektovat původní `saveOperationId`, očekávanou revizi a `storageGeneration`. Při selhání ponechat poslední úplnou potvrzenou revizi i její média a chránit obnovitelné návrhy; nedovolit mix starých/nových vazeb ani falešné `ULOŽENO`. Konkrétní implementace diskových bariér a důkaz garance po pádu procesu i výpadku napájení zůstávají **NOT RUN / k cílenému testu**; žádná databáze není automaticky atomickou transakcí s mediálními soubory.

## 3. Jednoduchá uživatelská nápověda (návrh textu pro budoucí offline aplikaci)

> V Nastavení → Fotografie nastavíš zvlášť kvalitu focení a importu: **Úsporná / Střední / Plná**. Při první instalaci jsou obě na **Plná**. Kvalitu lze pro jednu fotografii jednorázově změnit. Do CraftPlanu se trvale uloží **jen jedna fotografie ve vybrané kvalitě**; žádný další skrytý plný originál ani automatická další kopie v Galerii. Při importu zůstává externí originál beze změny. Zmenšenou kvalitu nelze později „vrátit“ do plných detailů.

## 4. Návrh ověřovacích scénářů (NOT RUN)

| ID | Scénář | Očekávání |
|---|---|---|
| FOTO-K01 | Čistá instalace: kvalita focení/importu Plná; změnit jen jednu globální kvalitu; restart; pořídit snímek | Obě výchozí kvality i jejich nezávislé uložení platí; žádná volba druhého místa uložení a žádná nová fotka v systémové Galerii |
| FOTO-K02 | Otevřít focení, jednorázově vybrat Plná, potvrdit fotografii, příště fotit znovu | Nový vstup vychází z původního výchozího nastavení, ne z jednorázové volby |
| FOTO-K03 | Import existujícího obrázku ve Střední a Úsporné kvalitě | Zdrojový soubor a jeho bajty mimo aplikaci beze změny, pracovní kopie je odlišná a správně označená |
| FOTO-K04 | Zvolit Plná pro již poškozený/zmenšený vstup | Aplikace nevymýšlí chybějící obrazové detaily |
| FOTO-K05 | Fotografie s EXIF rotací/zrcadlením, normalizací uložených bajtů i bez ní, různé rozlišení a formáty; vložit a přemístit kóty, uložit, obnovit z M2 ZIP, exportovat a vyměnit podklad F09 | Jediná mapa skutečně uložené bajty → kotvy; zdrojový EXIF jen provenience; žádná dvojitá rotace ani posun kotev, stejné přesné míry a editovatelná geometrie |
| FOTO-K06 | Pád, plný disk, odebraná oprávnění, poškozený/zmizelý zdroj; selhání před/po zveřejnění finální cesty média a před/po publikaci revize; ztracený či opožděný ACK | Po restartu pouze úplná ověřená revize, žádný mix médií/kót/poznámek/příloh/vazeb a žádné falešné `ULOŽENO` pro novější `editGeneration`; poslední potvrzený stav a potřebný staging chráněny |
| FOTO-K07 | Úplná přenosná záloha M2 a obnova na jiném zařízení bez původního telefonu/externího zdroje, i se zdrojovým EXIF a normalizovanou orientací | Identické bajty **skutečně uložené verze**, jedna uložená orientační mapa, beze změny kót/kotev, poznámek, povinných příloh, ID a vazeb; nedeklarovat nezachované rozlišení |
| FOTO-K08 | Změnit globální kvalitu po vytvoření dokumentu a exportovat/vrátit se | Staré soubory bez automatické rekomprese, zobrazování a export oddělené |
| FOTO-K09 | Hromadný import více snímků; zrušit uprostřed nebo vybrat pro jeden jiný profil | **OTEVŘENO:** přesná UX pravidla pro dávky; nesmí dojít k tiché záměně nebo částečnému nezaznamenanému úspěchu |
| FOTO-K10 | Vyfotit ve všech třech kvalitách, znovu otevřít a zkontrolovat uložené soubory | Každé pořízení má jednu trvale uloženou autoritativní obrazovou verzi, žádný druhý interní originál ani nový snímek v Galerii |
| FOTO-K11 | Focení **i import**: zabezpečený staging před ztrátovým zpracováním; pád/restart, plný disk, ztracené oprávnění, nejasný ACK a výslovné `Storno` | Jediné obnovitelné mezimédium při chybě nezmizí; po restartu se validní staging nabídne k obnově, neúplný se nevydá za potvrzený; úklid pouze po doložené nepotřebnosti či vědomém Stornu bez cizích referencí |
| FOTO-K12 | Import ve všech třech kvalitách; externí zdroj znepřístupnit **během převodu** a po potvrzeném uložení; restart | Nezávislý interní staging umožní bezpečné dokončení/obnovu jen pokud byl prokazatelně trvale zajištěn; potvrzená jediná uložená verze funguje bez externího zdroje, který zůstává nedotčen; žádný druhý trvalý interní plný originál |
| FOTO-K13 | Změna globální kvality, další focení, restart a znovuotevření starší fotografie | Jen nové snímky používají novou kvalitu, staré se automaticky nerekomprimují |

**Všechny testy NOT RUN.** Nejde o nové PASS ani povolení experimentů.

**NOVÉ ZÁVAZNÉ UPŘESNĚNÍ (22. 11. 2026):** dřívější návrh ukládání do dvou cílů je **nahrazen jedinou trvalou fotografií v CraftPlanu** ve zvolené kvalitě. [PR #3](https://github.com/zaplikj30-ui/CraftPlan/pull/3) eviduje bezpečný save/recovery, roli externího zdroje a zálohu jediné uložené verze. Historická schválení dvojího cíle zůstávají v historii revizí, nikoli jako platná implementační volba. FOTO-K01–13 a T45–47 jsou návrhové scénáře: **NOT RUN**.

**Produktově uzavřeno:** dvě nezávislé kvality, výchozí **Plná/Plná**, jedna autoritativní fotografie uvnitř CraftPlanu, žádný automatický druhý soubor v Galerii nebo skrytý interní archiv. M2 obnovuje identické bajty skutečně uložené fotografie; neobnovuje ztracený detail zmenšeniny. **Bezpečnostní význam výše doplněn jako návrhový kontrakt, nikoli runtime důkaz. OTEVŘENO technicky:** formáty a parametry kvality, konkrétní realizace a fyzická trvanlivost stagingu/finalizace/ACK, účinné diskové bariéry, integrita archivu a praktické testy. Žádné programování ani experiment nebyly povoleny.

## 5. Návaznosti a hranice

- [PR #4 – UX rozhodnutí](https://github.com/zaplikj30-ui/CraftPlan/pull/4): budoucí odkaz do UX registru; nový funkční požadavek na dvě volby a jednorázové přepnutí.
- [PR #3 – M0-A datové kontrakty](https://github.com/zaplikj30-ui/CraftPlan/pull/3): jediná autoritativní interní verze, nedotčený externí zdroj, identita, bezpečný save a záloha skutečně uložených bajtů.
- [PR #2 – plán etap](https://github.com/zaplikj30-ui/CraftPlan/pull/2): přesná pravidla pro M1 focení/import, napojení na editor; bezpečná přenosná záloha M2 před nenahraditelnými zakázkami.
- [PR #1 – původní pravidla](https://github.com/zaplikj30-ui/CraftPlan/pull/1): originály nezničit; staré definice „původních bajtů“ sjednotit s novou volbou velikosti, **nepřepsat historii bez vysvětlení**.
- ImageMeter je pouze **funkční reference**. Žádný jeho zdrojový kód ani obrazové prvky se nepřebírají; přesná ekvivalence jeho interního nastavení kvality uložení **nebyla prokázána**.
- Technologie, renderer, obrazový kodek, interní schéma a hranice výkonu zůstávají **NEVYBRÁNO**. Programování, build, testování na Androidu, Actions, merge, výdaje a zásahy do v1 **NEPOVOLENY**.

**HOTOVO na úrovni UX rozhodnutí:** dvě kvality, výchozí **Plná/Plná**, každá fotografie v aplikaci uložena jen jednou, žádný automatický další plný originál ani ukládání do systémové Galerie. **NEHOTOVO:** návrhové schválení celého M0-A, přesné technické parametry, nezávislé review změněné revize a praktické testy.
