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
2. **Evidence a anotace:** uložená verze má ID, kvalitu, rozměry, orientaci, vazbu na původ a kontrolu integrity. Kóty a poznámky jsou samostatně editovatelné. Náhledy/cache a dočasné soubory sloužící pro bezpečný zápis nejsou druhou trvale uloženou originální fotografií.
3. **Pořízení snímku v CraftPlanu:**
   - Uloží se **právě jedna autoritativní obrazová verze** v kvalitě **Úsporná / Střední / Plná**, pouze uvnitř aplikace; do systémové Galerie se automaticky nic nového nezapisuje. Nižší kvalita není později vratná do plného rozlišení. Případný technický plný mezisoubor kamery není druhý trvalý archiv; před bezpečným potvrzením vybrané verze se nesmí předčasně ztratit. Jeho životnost a bezpečné uvolnění patří do M0-A.
   - **Pád/storno/plný disk:** výsledek nepovažovat za uložený před potvrzením kompletní verze a kót. Poslední potvrzené soubory zachovat; rozpracované mezisoubory izolovat a řešit bez destrukce platné práce.
4. **Záloha a obnova:** M2 záloha zahrnuje skutečně **jedinou interně uloženou verzi v její zvolené kvalitě** a příslušné editovatelné údaje. Na novém zařízení se musí obnovit **stejné bajty uložené verze**, nikoli neexistující plný originál tam, kde byl uložen jen zmenšený snímek. Záloha celé zakázky nesmí pro svou obnovu vyžadovat původní externí zdroj, systémovou Galerii ani původní telefon.
5. **Stabilita měření:** kvalita obrazu nikdy nesmí měnit skutečné naměřené číslo, jednotku ani původ měření, pouze případnou viditelnost obrazového detailu. Převody rozměrů, EXIF orientace/zrcadlení, souřadnice ukotvených kót, následná výměna fotografie F09 a export musí zůstat jednoznačné a editovatelné.
6. **Selhání:** chybějící přístup ke zdroji, nedostatek místa, přerušení importu či pořízení, chyba komprese/rotace nesmí vést k falešnému „uloženo“, nahrazení poslední platné fotografie ani tichému poškození zdroje.

## 3. Jednoduchá uživatelská nápověda (návrh textu pro budoucí offline aplikaci)

> V Nastavení → Fotografie nastavíš zvlášť kvalitu focení a importu: **Úsporná / Střední / Plná**. Při první instalaci jsou obě na **Plná**. Kvalitu lze pro jednu fotografii jednorázově změnit. Do CraftPlanu se trvale uloží **jen jedna fotografie ve vybrané kvalitě**; žádný další skrytý plný originál ani automatická další kopie v Galerii. Při importu zůstává externí originál beze změny. Zmenšenou kvalitu nelze později „vrátit“ do plných detailů.

## 4. Návrh ověřovacích scénářů (NOT RUN)

| ID | Scénář | Očekávání |
|---|---|---|
| FOTO-K01 | Čistá instalace: kvalita focení/importu Plná; změnit jen jednu globální kvalitu; restart; pořídit snímek | Obě výchozí kvality i jejich nezávislé uložení platí; žádná volba druhého místa uložení a žádná nová fotka v systémové Galerii |
| FOTO-K02 | Otevřít focení, jednorázově vybrat Plná, potvrdit fotografii, příště fotit znovu | Nový vstup vychází z původního výchozího nastavení, ne z jednorázové volby |
| FOTO-K03 | Import existujícího obrázku ve Střední a Úsporné kvalitě | Zdrojový soubor a jeho bajty mimo aplikaci beze změny, pracovní kopie je odlišná a správně označená |
| FOTO-K04 | Zvolit Plná pro již poškozený/zmenšený vstup | Aplikace nevymýšlí chybějící obrazové detaily |
| FOTO-K05 | Vyměnit orientaci/EXIF, různé rozlišení a formáty, velký snímek; vložit a přemístit kóty | Bez změny přesných hodnot/jednotek, po save/reopen stejné ID a geometrie |
| FOTO-K06 | Pád, plný disk, odebraná oprávnění a poškozený/zmizelý zdroj | Žádné falešné uložení ani nevratné přepsání platného zdroje/dokumentu |
| FOTO-K07 | Úplná přenosná záloha M2 a obnova na jiném zařízení bez původního telefonu/externího zdroje | Identické bajty **skutečně uložené verze**, všechny kóty, ID a vazby; nedeklarovat nezachované rozlišení |
| FOTO-K08 | Změnit globální kvalitu po vytvoření dokumentu a exportovat/vrátit se | Staré soubory bez automatické rekomprese, zobrazování a export oddělené |
| FOTO-K09 | Hromadný import více snímků; zrušit uprostřed nebo vybrat pro jeden jiný profil | **OTEVŘENO:** přesná UX pravidla pro dávky; nesmí dojít k tiché záměně nebo částečnému nezaznamenanému úspěchu |
| FOTO-K10 | Vyfotit ve všech třech kvalitách, znovu otevřít a zkontrolovat uložené soubory | Každé pořízení má jednu trvale uloženou autoritativní obrazovou verzi, žádný druhý interní originál ani nový snímek v Galerii |
| FOTO-K11 | Zrušené focení, plný disk, pád během zpracování, dočasný plný mezisoubor kamery | Žádné falešné uložení, poslední platná verze zachována a bezpečně odstranitelné rozpracované médium |
| FOTO-K12 | Import ve všech třech kvalitách, poté zdroj mimo aplikaci znepřístupnit | V CraftPlanu funguje jediná uložená verze, mimo aplikaci byl zdroj zachován a žádný další trvalý interní plný originál nevznikl |
| FOTO-K13 | Změna globální kvality, další focení, restart a znovuotevření starší fotografie | Jen nové snímky používají novou kvalitu, staré se automaticky nerekomprimují |

**Všechny testy NOT RUN.** Nejde o nové PASS ani povolení experimentů.

**NOVÉ ZÁVAZNÉ UPŘESNĚNÍ (22. 11. 2026):** dřívější návrh ukládání do dvou cílů je **nahrazen jedinou trvalou fotografií v CraftPlanu** ve zvolené kvalitě. [PR #3](https://github.com/zaplikj30-ui/CraftPlan/pull/3) eviduje bezpečný save/recovery, roli externího zdroje a zálohu jediné uložené verze. Historická schválení dvojího cíle zůstávají v historii revizí, nikoli jako platná implementační volba. FOTO-K01–13 a T45–47 jsou návrhové scénáře: **NOT RUN**.

**Produktově uzavřeno:** dvě nezávislé kvality, výchozí **Plná/Plná**, jedna autoritativní fotografie uvnitř CraftPlanu, žádný automatický druhý soubor v Galerii nebo skrytý interní archiv. M2 obnovuje identické bajty skutečně uložené fotografie; neobnovuje ztracený detail zmenšeniny. **OTEVŘENO technicky:** formáty a parametry kvality, bezpečná dočasná média a životnost po potvrzení/stornu, potvrzovací mechanismus, integrita archivu a praktické testy. Žádné programování ani experiment nebyly povoleny.

## 5. Návaznosti a hranice

- [PR #4 – UX rozhodnutí](https://github.com/zaplikj30-ui/CraftPlan/pull/4): budoucí odkaz do UX registru; nový funkční požadavek na dvě volby a jednorázové přepnutí.
- [PR #3 – M0-A datové kontrakty](https://github.com/zaplikj30-ui/CraftPlan/pull/3): jediná autoritativní interní verze, nedotčený externí zdroj, identita, bezpečný save a záloha skutečně uložených bajtů.
- [PR #2 – plán etap](https://github.com/zaplikj30-ui/CraftPlan/pull/2): přesná pravidla pro M1 focení/import, napojení na editor; bezpečná přenosná záloha M2 před nenahraditelnými zakázkami.
- [PR #1 – původní pravidla](https://github.com/zaplikj30-ui/CraftPlan/pull/1): originály nezničit; staré definice „původních bajtů“ sjednotit s novou volbou velikosti, **nepřepsat historii bez vysvětlení**.
- ImageMeter je pouze **funkční reference**. Žádný jeho zdrojový kód ani obrazové prvky se nepřebírají; přesná ekvivalence jeho interního nastavení kvality uložení **nebyla prokázána**.
- Technologie, renderer, obrazový kodek, interní schéma a hranice výkonu zůstávají **NEVYBRÁNO**. Programování, build, testování na Androidu, Actions, merge, výdaje a zásahy do v1 **NEPOVOLENY**.

**HOTOVO na úrovni UX rozhodnutí:** dvě kvality, výchozí **Plná/Plná**, každá fotografie v aplikaci uložena jen jednou, žádný automatický další plný originál ani ukládání do systémové Galerie. **NEHOTOVO:** návrhové schválení celého M0-A, přesné technické parametry, nezávislé review změněné revize a praktické testy.
