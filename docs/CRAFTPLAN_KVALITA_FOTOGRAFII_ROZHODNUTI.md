# CraftPlan – kvalita fotografií při focení a importu

**Rozhodnutí vlastníka:** 22. 11. 2026, projektová konverzace CraftPlan. **Nejnovější upřesnění stejného dne:** vlastník si v nastavení vybírá, zda se nové fotografie ukládají jen do aplikace, nebo i do galerie telefonu. Toto rozhodnutí nahrazuje předchozí návrh povinného ukládání každého originálu do galerie.  
**Stav:** **POTVRZENÉ funkční/UX zadání**; datové a technické detaily níže označené **OTEVŘENO**.  
**Rozsah:** nové fotografie fotoaparátem a přidání existujících fotografií z galerie/souborů do editoru či zakázky.  
**Nejde o:** schválení technologie, implementace, testu, změnu již existujících souborů, automatický merge nebo povolení pracovat s nenahraditelnými zákaznickými daty.

## 1. Potvrzené chování – kvalita a místo uložení

V aplikaci bude sekce **Nastavení → Fotografie** se **dvěma nezávislými volbami kvality a třetí samostatnou volbou místa uložení nově vyfocených snímků**:

| Nastavení | Účel | Volby |
|---|---|---|
| **Kvalita při fotografování** | Výchozí nastavení pro **nově pořízené** fotografie v CraftPlanu | **Úsporná / Střední / Plná** |
| **Kvalita při importu** | Výchozí nastavení pro **existující** fotografie přidávané z galerie/souborů | **Úsporná / Střední / Plná** |
| **Ukládání nových fotografií** | Kam se ukládají snímky **nově pořízené prostřednictvím CraftPlanu** | **Jen do aplikace / Do aplikace i do galerie telefonu** |

- Všechny tři volby jsou **nezávislé** a zapamatují si poslední výchozí nastavení. **Předvolená možnost ukládání při první instalaci je OTEVŘENO** – nesmí být tiše domyšlena.
- **Při konkrétním fotografování** musí být možné zvolenou kvalitu vidět a **změnit jen pro daný snímek** (přímo v procesu focení / přidání před finálním potvrzením fotografie).
- **Při konkrétním importu** musí být možné zvolenou kvalitu vidět a **změnit jen pro právě přidávanou fotografii** před potvrzením.
- Jednorázová změna **kvality** nesmí přepsat příslušné trvalé nastavení; příští nezávislé focení či import opět načte globální výchozí volbu. **Volba místa ukládání** je zatím odsouhlasena v globálním nastavení, nikoli jako povinný přepínač při každém focení.
- Přechod do editoru zůstává s potvrzovacím náhledem **OK / Znovu / Storno** tam, kde to pro daný vstup dává smysl; samotné zpracování nesmí potichu změnit nebo poškodit kóty.
- **Kvalita uložené fotografie** je něco jiného než **dočasná kvalita zobrazení / vykreslovací výkon editoru (F17)** a něco jiného než **velikost a kvalita zákaznického PDF/JPG exportu**. Tři různé věci neslučovat do jediného přepínače.
- Změna výchozího nastavení nemění **dříve uložené** fotografie. Zmenšená fotografie nezíská dodatečně ztracené detaily přepnutím na „Plná“.

### Co znamenají tři názvy

- **Úsporná:** menší pracovní soubor, méně obrazového detailu.
- **Střední:** kompromis velikosti a obrazového detailu.
- **Plná:** maximální *dostupná* kvalita daného zdroje; neslibovat vyšší kvalitu, než skutečně existuje.

**OTEVŘENO:** přesné rozlišení, maximální delší strana, kompresní parametry, podporované formáty, barevný profil, zacházení s metadaty, výchozí předvolená úroveň při první instalaci a případná velikostní indikace. Žádná číselná MP/JPEG hranice není tímto rozhodnutím schválena. Nastavení není totožné s ImageMeter „Maximum texture resolution“.

## 2. Neměnné zásady ochrany originálů – výslovně potvrzeno

Vlastník výslovně rozhodl: **originální soubory se nikdy nemění.**

1. **Import z galerie nebo souborů:** CraftPlan nesmí **měnit, přepisovat ani mazat** existující zdrojový soubor mimo aplikaci. Při Úsporná/Střední může vytvořit **novou vlastní zmenšenou pracovní kopii** do svých dat. Originál v galerii nebo zdrojovém umístění zůstává v původních bajtech.
2. **Původní médium a pracovní kopie jsou dvě rozdílné věci.** Zmenšenou kopii nelze označovat jako nezměněný originál ani v exportu, záloze či dokumentaci. U každé uložené fotografie má být dohledatelný zdroj, použitá kvalita, rozměry/otočení a zda je k dispozici i původní nezmenšená verze. Odkazy, ID a anotace/kóty se při odvození kopie neztrácejí.
3. **Nová fotografie z fotoaparátu – produktově POTVRZENO, volitelné místo uložení:**
   - **Jen do aplikace:** CraftPlan uloží vlastní fotografii v kvalitě **Úsporná / Střední / Plná**. **Do galerie záměrně nic nepřidává.** Při nižší zvolené kvalitě **není slíben žádný další samostatně uchovaný originál v plném rozlišení**; uživatel musí před potvrzením rozumět tomu, že pozdější přepnutí na Plná neobnoví detaily. Již vzniklý zdrojový soubor nesmí aplikace tiše přepsat ani smazat. **OTEVŘENO pro M0-A:** bezpečné chování fotoaparátu, vznik/nakládání s případným dočasným plnohodnotným snímkem a ukládací protokol tak, aby nevznikal skrytý destruktivní krok.
   - **Do aplikace i do galerie telefonu:** CraftPlan uloží **vlastní pracovní kopii** ve vybrané kvalitě a **samostatný původní snímek v plné dostupné kvalitě do galerie**. Původní soubor v galerii se nikdy nepřepisuje ani nemaže. Android toto nemusí zajistit automaticky: aplikace musí obě operace **výslovně provést a ověřit**.
   - **Selhání:** není dovoleno nepravdivě potvrdit uložení do obou míst, pokud jedno selhalo; uživatel musí vědět, kde snímek skutečně existuje. Nezničit jediný platný zdroj ani poslední uložený stav. **OTEVŘENO PRO M0-A:** mechanismus galerie, zrušené focení, částečný úspěch, uživatelsky pravdivá oznámení, následná dostupnost zdroje a návaznost přenosné zálohy.
4. **Důležitý datový rozpor k uzavření:** starší M0 texty v [PR #1](https://github.com/zaplikj30-ui/CraftPlan/pull/1) a [PR #3](https://github.com/zaplikj30-ui/CraftPlan/pull/3) požadují zachovat **původní bajty médií i v aplikačním datovém modelu/záloze**. Novější produktové rozhodnutí připouští jen pracovní snímek v aplikaci (bez galerie) **nebo** pracovní kopii v aplikaci a plný originál v galerii; importovaný externí originál zůstává na svém místě. Nejde o totožnou úroveň obnovitelnosti a galerie může být později nedostupná. **Nevyřešit tiše přepsáním ani smazáním existujících originálů.** Před M1/M2 musí M0-A rozlišit, co je skutečně uložený originál, co odvozená pracovní kopie, jaký zdroj je dostupný a co úplná přenosná záloha dokáže či nedokáže obnovit. Nevydávat zmenšenou kopii za plnohodnotný originál. Dva výstupy mohou zabírat více místa.
5. **Stabilita měření:** kvalita obrazu nikdy nesmí měnit skutečné naměřené číslo, jednotku ani původ měření, pouze případnou viditelnost obrazového detailu. Převody rozměrů, EXIF orientace/zrcadlení, souřadnice ukotvených kót, následná výměna fotografie F09 a export musí zůstat jednoznačné a editovatelné.
6. **Selhání:** chybějící přístup ke zdroji, nedostatek místa, přerušení importu či pořízení, chyba komprese/rotace nesmí vést k falešnému „uloženo“, nahrazení poslední platné fotografie ani tichému poškození zdroje.

## 3. Jednoduchá uživatelská nápověda (návrh textu pro budoucí offline aplikaci)

> V Nastavení → Fotografie si nastavíš zvlášť kvalitu pro **focení** a pro **fotky z galerie**. Obě nabízejí **Úsporná**, **Střední** a **Plná**. Navíc si zvolíš, zda nové fotografie ukládat **Jen do aplikace**, nebo **Do aplikace i do galerie telefonu**. Při přidání konkrétní fotografie můžeš kvalitu jednorázově změnit bez přepsání nastavení pro příště. Pokud zvolíš **Jen do aplikace** a úspornou/střední kvalitu, plný snímek v galerii nebude a ztracené detaily nepůjde obnovit. Pokud zvolíš **Do aplikace i do galerie**, aplikace si uloží pracovní kopii podle kvality a plný originál zvlášť do galerie. Při **importu** se původní soubor v galerii nebo složce nikdy nepřepíše ani nesmaže.

## 4. Návrh ověřovacích scénářů (NOT RUN)

| ID | Scénář | Očekávání |
|---|---|---|
| FOTO-K01 | Nastavit focení Úsporná, import Plná; zavřít a znovu otevřít nastavení | Volby jsou dvě nezávislé, zachované |
| FOTO-K02 | Otevřít focení, jednorázově vybrat Plná, potvrdit fotografii, příště fotit znovu | Nový vstup vychází z původního výchozího nastavení, ne z jednorázové volby |
| FOTO-K03 | Import existujícího obrázku ve Střední a Úsporné kvalitě | Zdrojový soubor a jeho bajty mimo aplikaci beze změny, pracovní kopie je odlišná a správně označená |
| FOTO-K04 | Zvolit Plná pro již poškozený/zmenšený vstup | Aplikace nevymýšlí chybějící obrazové detaily |
| FOTO-K05 | Vyměnit orientaci/EXIF, různé rozlišení a formáty, velký snímek; vložit a přemístit kóty | Bez změny přesných hodnot/jednotek, po save/reopen stejné ID a geometrie |
| FOTO-K06 | Pád, plný disk, odebraná oprávnění a poškozený/zmizelý zdroj | Žádné falešné uložení ani nevratné přepsání platného zdroje/dokumentu |
| FOTO-K07 | Přenosná záloha a obnova na čistém zařízení, zdroj z galerie je mezitím nedostupný | Poctivě poznat, zda záloha obsahuje plný zdroj či jen pracovní kopii; nepředstírat obnovu detailu |
| FOTO-K08 | Změnit globální kvalitu po vytvoření dokumentu a exportovat/vrátit se | Staré soubory bez automatické rekomprese, zobrazování a export oddělené |
| FOTO-K09 | Hromadný import více snímků; zrušit uprostřed nebo vybrat pro jeden jiný profil | **OTEVŘENO:** přesná UX pravidla pro dávky; nesmí dojít k tiché záměně nebo částečnému nezaznamenanému úspěchu |
| FOTO-K10 | Pořídit snímek přes CraftPlan s volbou **Do aplikace i do galerie**, potvrdit, vybrat Úsporná/Střední/Plná | **Plný originál v galerii**, samostatná odpovídající pracovní kopie v CraftPlanu; uložení obou stran ověřeno |
| FOTO-K11 | Zrušit pořízení; v režimu **Do aplikace i galerie** chyba jedné ukládací operace; později přesunout/smazat originál | Přiznaný částečný úspěch/chyba, žádné falešné „uloženo“, žádné tiché smazání zdroje, bezpečné otevření pracovní kopie a jasný údaj o dostupnosti originálu |
| FOTO-K12 | Zvolit **Jen do aplikace**, pak pořídit a potvrdit Úsporná/Střední/Plná | Snímek je uložen v aplikaci ve správné kvalitě, **v galerii nepřibude žádný soubor**; uživatel ví, že plná kopie nemusí existovat |
| FOTO-K13 | Přepnout místo ukládání, zavřít a znovu otevřít aplikaci; pořídit další fotografii | Zapamatovaná volba platí pro nové snímky; dříve uložené se zpětně nepřesouvají a nemění |

**Všechny testy NOT RUN.** Nejde o nové PASS ani povolení experimentů.

## 5. Návaznosti a hranice

- [PR #4 – UX rozhodnutí](https://github.com/zaplikj30-ui/CraftPlan/pull/4): budoucí odkaz do UX registru; nový funkční požadavek na dvě volby a jednorázové přepnutí.
- [PR #3 – M0-A datové kontrakty](https://github.com/zaplikj30-ui/CraftPlan/pull/3): musí výslovně rozlišit zdrojové médium, interní originál, pracovní odvozenou kopii, výchozí profil, přiřazené ID/verze a obsah zálohy.
- [PR #2 – plán etap](https://github.com/zaplikj30-ui/CraftPlan/pull/2): přesná pravidla pro M1 focení/import, napojení na editor; bezpečná přenosná záloha M2 před nenahraditelnými zakázkami.
- [PR #1 – původní pravidla](https://github.com/zaplikj30-ui/CraftPlan/pull/1): originály nezničit; staré definice „původních bajtů“ sjednotit s novou volbou velikosti, **nepřepsat historii bez vysvětlení**.
- ImageMeter je pouze **funkční reference**. Žádný jeho zdrojový kód ani obrazové prvky se nepřebírají; přesná ekvivalence jeho interního nastavení kvality uložení **nebyla prokázána**.
- Technologie, renderer, obrazový kodek, interní schéma a hranice výkonu zůstávají **NEVYBRÁNO**. Programování, build, testování na Androidu, Actions, merge, výdaje a zásahy do v1 **NEPOVOLENY**.

**HOTOVO v tomto PR:** trvale zapsané vlastníkem schválené UX chování včetně volby místa uložení nových fotografií, hranice ochrany originálů, odkryté bezpečnostní otázky, návrhy nápovědy a akceptačních scénářů. **NENÍ HOTOVO:** navazující datový kontrakt M0-A a jeho nezávislé DESIGN/SECURITY review, technické parametry, implementace či fyzické ověření.
