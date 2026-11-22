# CraftPlan – kvalita fotografií při focení a importu

**Rozhodnutí vlastníka:** 22. 11. 2026, projektová konverzace CraftPlan.  
**Stav:** **POTVRZENÉ funkční/UX zadání**; datové a technické detaily níže označené **OTEVŘENO**.  
**Rozsah:** nové fotografie fotoaparátem a přidání existujících fotografií z galerie/souborů do editoru či zakázky.  
**Nejde o:** schválení technologie, implementace, testu, změnu již existujících souborů, automatický merge nebo povolení pracovat s nenahraditelnými zákaznickými daty.

## 1. Potvrzené chování – dvě nezávislá nastavení

V aplikaci bude sekce **Nastavení → Fotografie** a v ní **dvě samostatné uživatelské volby**:

| Nastavení | Účel | Volby |
|---|---|---|
| **Kvalita při fotografování** | Výchozí nastavení pro **nově pořízené** fotografie v CraftPlanu | **Úsporná / Střední / Plná** |
| **Kvalita při importu** | Výchozí nastavení pro **existující** fotografie přidávané z galerie/souborů | **Úsporná / Střední / Plná** |

- Tato dvě nastavení jsou **nezávislá** a zapamatují si poslední výchozí volbu.
- **Při konkrétním fotografování** musí být možné zvolenou kvalitu vidět a **změnit jen pro daný snímek** (přímo v procesu focení / přidání před finálním potvrzením fotografie).
- **Při konkrétním importu** musí být možné zvolenou kvalitu vidět a **změnit jen pro právě přidávanou fotografii** před potvrzením.
- Jednorázová změna **nesmí přepsat** příslušné trvalé nastavení; příští nezávislé focení či import opět načte globální výchozí volbu.
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
3. **Nová fotografie z fotoaparátu:** pokud při pořizování vznikne plnohodnotný zdrojový snímek a pak se zmenšuje, nesmí být tento zdroj **tiše a nevratně nahrazen** zmenšeninou, přičemž UI tvrdí, že originál je zachován. **OTEVŘENO PRO M0-A:** způsob uchování/umístění zdrojového snímku a pravidla jeho životního cyklu tak, aby se současně naplnila úspora místa a ochrana originálu. Je třeba vyjasnit rozdíl mezi „plná kvalita ze snímače“ a „originál již uložený v CraftPlanu“.
4. **Důležitý datový rozpor k uzavření:** starší M0 texty v [PR #1](https://github.com/zaplikj30-ui/CraftPlan/pull/1) a [PR #3](https://github.com/zaplikj30-ui/CraftPlan/pull/3) požadují zachovat **původní bajty médií i v aplikačním datovém modelu/záloze**. Novější produktové rozhodnutí dovoluje ukládat **zmenšenou vlastní kopii** při importu a zdroj ponechat v galerii. To **není totéž** jako bezpečné uchování interního originálu: externí originál může být později přesunut nebo smazán. **Nevyřešit tiše automatickým smazáním interního originálu ani předpokladem, že galerie navždy existuje.** Před M1/M2 dopracovat v M0-A pravidlo pro vlastní originál vs. odvozenou kopii, dostupnost zdroje, úplnost přenosné zálohy a pravdivé označení toho, co lze obnovit.
5. **Stabilita měření:** kvalita obrazu nikdy nesmí měnit skutečné naměřené číslo, jednotku ani původ měření, pouze případnou viditelnost obrazového detailu. Převody rozměrů, EXIF orientace/zrcadlení, souřadnice ukotvených kót, následná výměna fotografie F09 a export musí zůstat jednoznačné a editovatelné.
6. **Selhání:** chybějící přístup ke zdroji, nedostatek místa, přerušení importu či pořízení, chyba komprese/rotace nesmí vést k falešnému „uloženo“, nahrazení poslední platné fotografie ani tichému poškození zdroje.

## 3. Jednoduchá uživatelská nápověda (návrh textu pro budoucí offline aplikaci)

> V Nastavení → Fotografie si nastavíš zvlášť kvalitu pro **focení** a pro **fotky z galerie**. Obě nabízejí **Úsporná**, **Střední** a **Plná**. Při přidání konkrétní fotografie můžeš kvalitu jednorázově změnit, aniž bys změnil nastavení pro příště. **Původní fotka v galerii se nepřepíše ani nesmaže**; CraftPlan pro práci vytvoří svou vlastní kopii. Zmenšením může ubýt detail, který pozdější změna na „Plná“ nevrátí. U nově focených snímků musí aplikace před použitím pro důležitou práci jasně vysvětlit, kde zůstává původní snímek.

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

**Všechny testy NOT RUN.** Nejde o nové PASS ani povolení experimentů.

## 5. Návaznosti a hranice

- [PR #4 – UX rozhodnutí](https://github.com/zaplikj30-ui/CraftPlan/pull/4): budoucí odkaz do UX registru; nový funkční požadavek na dvě volby a jednorázové přepnutí.
- [PR #3 – M0-A datové kontrakty](https://github.com/zaplikj30-ui/CraftPlan/pull/3): musí výslovně rozlišit zdrojové médium, interní originál, pracovní odvozenou kopii, výchozí profil, přiřazené ID/verze a obsah zálohy.
- [PR #2 – plán etap](https://github.com/zaplikj30-ui/CraftPlan/pull/2): přesná pravidla pro M1 focení/import, napojení na editor; bezpečná přenosná záloha M2 před nenahraditelnými zakázkami.
- [PR #1 – původní pravidla](https://github.com/zaplikj30-ui/CraftPlan/pull/1): originály nezničit; staré definice „původních bajtů“ sjednotit s novou volbou velikosti, **nepřepsat historii bez vysvětlení**.
- ImageMeter je pouze **funkční reference**. Žádný jeho zdrojový kód ani obrazové prvky se nepřebírají; přesná ekvivalence jeho interního nastavení kvality uložení **nebyla prokázána**.
- Technologie, renderer, obrazový kodek, interní schéma a hranice výkonu zůstávají **NEVYBRÁNO**. Programování, build, testování na Androidu, Actions, merge, výdaje a zásahy do v1 **NEPOVOLENY**.

**HOTOVO v tomto PR:** trvale zapsané vlastníkem schválené UX chování, hranice ochrany originálů, odkryté bezpečnostní otázky, návrhy nápovědy a akceptačních scénářů. **NENÍ HOTOVO:** navazující datový kontrakt M0-A a jeho nezávislé DESIGN/SECURITY review, technické parametry, implementace či fyzické ověření.
