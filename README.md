# CraftPlan

Offline Android aplikace pro zaměřování a dokumentaci zakázek. Vývojová verze M1 otevírá přibalenou syntetickou fotografii. Přiblížení se ovládá dvěma prsty, posun jedním i při výchozím přiblížení. Kóty mohou zasahovat do volné plochy kolem snímku a při pan/zoom zůstávají ukotvené k fotografii. Dokument kót je zatím pouze v paměti.

### M1 – více kót, lišta a historie

- **Seznam +** otevře nabídku nástrojů. Krátké klepnutí na ikonu kóty připraví jeden tah. Dlouhé podržení zapíná nebo vypíná opakování. Jednorázová kóta zůstane vybraná. Při opakování se nová kóta dokončí bez výběru a bez dialogu. Opuštění fotografie režim kreslení vypne.
- První klepnutí na libovolnou část kóty ji označí. Pouze další klepnutí na popisek/znak metru označené kóty otevře dialog; čára a konce dialog neotevírají. Přímý zásah viditelné geometrie má přednost před pouze rozšířenou dotykovou oblastí. Konce, čáry, popisky a notýsek vstupují do společného rozhodování samostatně; při shodě má blízký skutečný úchop přednost, u překrývajících se popisků rozhoduje i vzdálenost od středu. Velké dotykové oblasti zůstávají mimo kolize. Při téměř shodné vzdálenosti má mírnou přednost označený objekt; při přesném překryvu bez výběru rozhoduje stabilní pořadí vykreslení. Opakovaná klepnutí kandidáty nepřepínají. Cíl dotyku se zachytí při jeho zahájení a zůstane stejný po dobu gesta. Vybranou kótu lze upravovat i v režimu opakování. Tažení konce či středu upravuje kotvu či celek, při kreslení a tažení konce pomáhá lupa.
- Tahem doprava přes dvě pevná tlačítka rozbalíte vodorovně posuvnou část, doleva jedním gestem sbalíte obě části lišty zpět na dvě pevná tlačítka (i když byla otevřená pouze horní část). Tahem za horní úchyt nahoru/dolů (nebo klepnutím) samostatně rozbalíte/sbalíte zkušební horní řádek: přizpůsobení zobrazení, viditelnost poznámek a ukončení kreslení/odznačení. Celý blok lišt včetně úchytu a nápovědy sahá při gestové navigaci až ke spodnímu okraji displeje, pod systémový indikátor gest; systémová gesta zůstávají v režii Androidu. Pro neprůhledné systémové ovládací prvky, výřez a klávesnici zůstává potřebný odstup. Platí na výšku i na šířku. Při dialogu hodnoty je lišta skrytá a po zavření si zachová stav. Obsahuje **Undo**, **Redo**, smazání výběru a společný zámek podélného posunu čísel. Zámek začíná odemčený. Tažení zamčeného čísla nepohne ani popiskem, ani geometrií; klepnutí na číslo označené kóty otevře hodnotu. Mazání vybrané kóty má ikonu popelnice a lze je vrátit. Jeho stav a rozbalení lišty jsou lokální preference společné pro fotografie.
- Historie drží posledních 30 potvrzených změn v paměti. Umožňuje vrátit vytvoření, smazání, přesun kotvy/celku/popisku a změnu hodnoty, jednotky či poznámky. Zrušený tah, výběr, pan/zoom a preference historii nemění. Nová potvrzená změna po Undo ruší Redo.

### Doladění lišty po sestavení 51

Horní testovací pruh Zpět / Testovací fotografie je odstraněn v obou orientacích. Editor využívá jeho místo; systémový stavový pruh a navigace Androidu zůstávají. Návrat z fotografie používá stávající systémové Zpět se stejným ukončením režimu kreslení a diagnostiky; pravidla dialogu se nemění.

V testovací variantě je spodní rolovatelná oblast prodloužená osmi dočasnými, neaktivními textovými tlačítky budoucích funkcí (barva a tloušťka kóty, více kót, zámek polohy lišty, přesun lišty, uspořádání ikon, obnova pořadí, export) podle PR #4. Slouží k fyzické zkoušce rolování, neznamenají implementaci těchto funkcí ani konečné pořadí. Dvě rychlá tlačítka zůstávají pevná. Po fyzickém testu sestavení 52 jsou tlačítka opět vysoká 56 dp a s původní svislou výplní 9 dp na obou stranách, stejně v obou orientacích. Vodorovné rolování vlastník potvrdil jako funkční, svislé ovládání je stále nespolehlivé. Úchyt má širší dotykovou oblast (horní 160 × 27 dp, boční 32 × 74 dp), gesto se zachytává od počátku v jeho vlastní oblasti. Nezasahovat do systémového Zpět/Domů. Fyzická spolehlivost úchytu, rolování i přístupnost při gestové navigaci vyžadují další test APK; příčina předchozích selhání nebyla na zařízení potvrzena.

## Debug sestavení (pouze po samostatném GO)

**Pozor:** Následující příkazy jsou jen referenční postup, nikoli automatický úkol pro Codex. GO na opravu, implementaci nebo jednotkové testy **nepovoluje** sestavit APK. Bez samostatného výslovného GO vlastníka nesmí Codex spouštět `assembleDebug`, vytvářet či podepisovat APK ani předávat nefunkční odkazy na lokální soubory. Viz `AGENTS.md`.

Teprve po výslovném GO pro sestavení spusťte v připraveném prostředí CraftPlan z kořene repozitáře následující příkazy.

```sh
source /workspace/.craftplan-android/activate.sh
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Mimo prostředí CraftPlan použijte JDK 17 nebo 21, Gradle 8.13 a Android SDK 36. Debug sestavení je určeno pro Android 13 a novější.

## Pojmenování testovacích APK

Schválené pravidlo (23. 11. 2026): Každá testovací APK předaná uživateli musí mít jednotný název obsahující **skutečný** `versionName`, etapu, číslo sestavení, zkrácené SHA zdrojového commitu a typ podpisu.

Vzor pro GitHub Actions debug sestavení:

```text
CraftPlan-v<versionName>-M1-b<run_number>-<sha7>-debug.apk
```

Například při `versionName = 0.1.0-test`, GitHub Actions běhu č. 10 a commitu `52cfded...`:

```text
CraftPlan-v0.1.0-test-M1-b10-52cfded-debug.apk
```

Údaje se musí získávat z reálného buildu / GitHub Actions, nikoliv ručně odhadovat. Stejná verze má být viditelná i uvnitř aplikace. Uživateli předávat **přímý soubor .apk**, nikoliv ZIP vyžadující rozbalení. Přejmenování distribučního souboru nesmí měnit jeho obsah ani podpis. Změna názvu nemění Android `versionCode`, podpis ani instalovatelnost.

Toto je požadavek pro další sestavení a předávání APK; automatizace názvu v CI se provede až po samostatném schváleném GO. Nejde o důkaz změny již vydaných souborů.

## M1 – schválené doladění dialogu kóty (24. 11. 2026)

Navazuje na [PR #4 – UX kontrakt](https://github.com/zaplikj30-ui/CraftPlan/pull/4). Převody a zobrazení hodnot používají desetinnou čárku bez změny přesné kanonické hodnoty; přidán mikrometr (1 µm = 0,001 mm), sjednoceno zarovnání menu jednotek se zámkem a mezera 4 dp nad tlačítky akcí v dialogu na výšku.

Numerická klávesnice 4 × 4 zachová hlavní čísla uprostřed, malé alternativy vpravo dole (7/+; 8/−; 9/×; 4/÷; 5/=; 6/%; 1//; 2/±; 3/°; 0/Ø; čárka/tečka). Krátký stisk vkládá hlavní znak, dlouhý stisk zobrazí alternativu a po platném uvolnění vloží pouze ji. Druhá klávesnice: + − × ÷ / ± / = % / ° Ø m² m³ / tečka, mezera, návrat, R; přesné pořadí po řádcích je v kódu a testech. Symboly jsou textový vstup; nejde o kalkulačku ani nové druhy měřených veličin.

Poslední numerické tlačítko je zpočátku volné. Krátké klepnutí vloží nastavený symbol (u prázdného otevře výběr), dlouhý stisk otevře nabídku všech 15 vkládaných symbolů a vyprázdnění. Alternativně lze symbol podržet a přetáhnout na zvýrazněné pravé spodní políčko druhé klávesnice; při platném puštění se přiřadí a zobrazí numerická klávesnice. Výběr/přetažení samo nic nevkládá, neplatné puštění nic nemění. Preference je lokální a společná pro kóty. Historie laseru zůstává vyhrazená, bez nové funkce.

Fyzické ověření nových gest lišty, překryvů, posunu popisků a Undo/Redo vyžaduje následné samostatné GO pro APK a praktický test. Trvalé ukládání dokumentů, plochy a úhly jsou mimo tento rozsah.
