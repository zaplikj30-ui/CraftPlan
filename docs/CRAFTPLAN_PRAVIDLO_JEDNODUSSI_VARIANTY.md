# CraftPlan – pravidlo jednodušší bezpečné varianty

**Podnět a souhlas vlastníka:** projektová diskuse dne 22. 11. 2026.  
**Stav:** **POTVRZENÁ ZÁSADA PRO ROZHODOVÁNÍ O ROZSAHU A NÁVRHU**; jednotlivé změny funkcí a technologií se stále schvalují zvlášť.  
**Platnost:** **celý CraftPlan**, M0–M5 i pozdější etapy; nejen ukládání fotografií.

## 1. Smysl pravidla

**Nepokračovat automaticky v původní složité variantě jen proto, že byla jednou navržena.** Pokud se při posuzování, návrhu, malém schváleném experimentu nebo vývoji ukáže, že část řešení:
- nejde spolehlivě uskutečnit nebo není prokázáno, že funguje;
- je nepřiměřeně složitá na implementaci, používání či údržbu;
- vyvolává nadměrné chyby, bezpečnostní rizika, ztrátu dat, slabý výkon nebo problematické závislosti;
- má nepřiměřenou cenu, požadavky na hardware nebo provoz,

**přerušit prosazování dané varianty a skutečně zvážit jednoduchou, bezpečnější a dlouhodobě udržovatelnou alternativu.** Výstupem může být zjednodušení chování, použití již ověřeného systémového/hotového řešení, rozdělení na menší etapy, odložení pokročilé části nebo výjimečně návrh zrušení nepřiměřené funkce.

Nejde o povel každou náročnou funkci automaticky zahodit. Těžkost musí být **konkrétně vysvětlena**, ne jen odhadnutá podle názvu technologie či prvního neúspěchu. Složitost celého projektu nesmí zbytečně narůstat kvůli jedné volitelné funkci.

## 2. Krátký kontrolní postup

Před významným programováním, při překročení rizika a po neúspěšné zkoušce/opravení:

1. **Jaký skutečný uživatelský výsledek potřebujeme?** Oddělit nezbytné chování od pohodlného či pokročilého vylepšení.
2. **Co konkrétně nevyhovuje?** Důkaz nebo přiznaný odhad; složitost, čas, údržba, výkon, cena, soukromí, přesnost, závislosti a návratnost.
3. **Jaké máme bezpečné volby?** (A) jednoduchá oprava / zjednodušený návrh, (B) udržované hotové nebo systémové řešení, (C) užší první etapa a pozdější rozšíření, (D) odložení nebo doporučení nepokračovat.
4. **Co je cena každé volby?** Co uživatel získá a ztratí, jak se změní data, záloha, export, migrace, hardware a budoucí rozšíření. Zapsat rizika a neověřená místa.
5. **Rozhodnutí vlastníka.** Předložit srozumitelně nejjednodušší variantu, která splní podstatný účel a bezpečnostní minimum. Bez výslovného souhlasu vlastníka **neškrtat schválené funkce ani neměnit zásadní UX, data či technologii**.

Pokud se jedna cesta opakovaně nedaří, uplatnit také existující pravidlo v \`AGENTS.md\`: po **dvou selháních nebo vysokém nákladu** výslovně rozhodnout **POKRAČOVAT / ZMĚNIT SMĚR / STOP**. Tato dokumentační zásada ho **rozvíjí, nenahrazuje**.

## 3. Co nelze „zjednodušit“ na úkor bezpečnosti

- **Žádná ztráta nebo tiché přepsání poslední platné potvrzené práce.**
- **Žádná změna přesných měřených hodnot či jednotek kvůli vizuální kvalitě, exportu, kompresi, ukládání či zvolenému frameworku.**
- **Žádné falešné „uloženo“ ani vydávání zmenšené kopie za plný originál.**
- **Žádné znehodnocení existujících originálních fotografií a médií.**
- **Žádné používání nenahraditelných zakázek bez ověřené přenosné zálohy a obnovy.**

Pokud levnější/jednodušší varianta tyto podmínky nesplní, **není přípustnou alternativou**. Funkci zúžit, odložit, nebo zastavit; neoslabovat skrytě bezpečnost dat.

## 4. Praktické příklady k budoucímu návrhu

- **Fotografie (navazující [PR #6](https://github.com/zaplikj30-ui/CraftPlan/pull/6)):** kdyby automatické zachování více verzí působilo nepřiměřenou složitost či falešné sliby o plném originálu, posoudit jednodušší a pravdivě popsaný způsob ukládání. Současná vlastníkem schválená **volba Jen do aplikace / Do aplikace i galerie** zůstává platná, dokud vlastník výslovně nerozhodne jinak. Neodstraňovat ji bez review.
- **Panorama/sféra:** pokud je vlastní automatické spojování snímků příliš riskantní, může časnější menší etapa pracovat s **již hotovým panoramatem** a editovatelnými kótami; funkce pořízení/stitchingu se vede dál jako odložená, nikoli potichu zapomenutá.
- **Technologie editoru:** pokud kombinace dvou UI vrstev nepřináší doloženou výhodu, zohlednit jednodušší jednolitou variantu; samotná popularita ani odhad výkonu nestačí.

Příklady jsou **možnosti k vyhodnocení**, nikoli nová schválení funkcí, výběr technologie nebo implementační povolení.

## 5. Důsledky pro projektovou evidenci

- U problematického úkolu vést stručný záznam **problém → důkaz/nejistota → jednodušší varianty → dopad → rozhodnutí vlastníka → zbývající rizika/testy**.
- Každou odsunutou funkci a její ID dál uchovat v katalogu a matici. **Odloženo ≠ zrušeno ≠ hotovo.**
- Měřitelná kritéria HOTOVO a nezávislé review platí i pro zjednodušenou variantu.
- Tento dokument je zatím pouze návrhová evidence v izolovaném PR. **Žádný merge, commit do ostatních PR, build, Actions, APK, placený experiment ani výběr technologie nebyl tímto schválen.**
