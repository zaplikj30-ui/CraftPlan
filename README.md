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

Diagnostika úchytu: ve stávajícím ručně zapínaném záznamu syntetického editoru jsou nově události `toolbar_window` (DOWN/UP/CANCEL a změny počtu ukazatelů, souřadnice v okně, typ vstupu, naměřený obdélník úchytu, stav lišt) a `toolbar_grip` (začátek, lokální souřadnice, rozměr, práh, výsledný tah, důvod ukončení a změna stavu). Záznam nerozhoduje o zachycení dotyku; neblokuje systémová gesta a neukládá fotografie ani hodnoty. Dotyk mimo aplikaci nebo zachycený systémem nemusí být zaznamenán. Po Start vyzkoušet úspěšné i neúspěšné tahy/klepnutí prstem a S Penem na výšku i šířku; Stop a Uložit časovou osu, ne pouze souhrn. Testy a fyzické ověření NOT RUN.

Po fyzické diagnostice sestavení 53: zachycené tahy na úchytu fungují, opakované pokusy těsně pod ním se do něj nedoručují (prst i S Pen, obě orientace). Horní úchyt má navíc sdílenou oblast 160 × 20 dp směrem do lišty; výška a vzhled lišt zůstávají. Nadřazený pozorovatel událostí na tlačítkách nic nespotřebuje před rozpoznáním svislého tahu. První rozhodný vodorovný tah, klepnutí a dlouhé podržení ponechá původním ovladačům. Svislý tah po překročení stávajícího prahu zruší stisk tlačítka a při uvolnění nastaví horní řádek podle směru. Boční úchyt a systémová navigace se nemění. Diagnostika rozlišuje samostatný a sdílený úchyt a důvod předání gesta. Nezveřejňovat surový záznam zařízení. Fyzicky ověřit klepnutí, opakování dlouhým stiskem, oba vodorovné režimy a oba svislé směry na prstu/S Penu, na výšku/šířku a bez nechtěných akcí. Testy a sestavení NOT RUN.

Sestavení 54 (revize `79043da20b03ad5cbccca1201a973643e5152fef`, GitHub Actions 45509435359): build a automatické kontroly PASS. Vlastník potvrdil na telefonu všech šest závěrečných kontrol: svislé tažení bez aktivace tlačítka, klepnutí, dlouhé podržení pro opakování, vodorovné rolování a sbalení obou lišt, prst i S Pen v obou orientacích a rychlé opakované vysouvání/zasouvání. Zbývá ověřit zkrácený boční úchyt v sestavení 55.

Po kontrole vzhledu sestavení 54 je boční svislý úchyt sbalené lišty zkrácen: vnější viditelná délka 50 dp (dříve 70), vnitřní čárka 44 dp (dříve 64), dotyková výška 54 dp (dříve 74). Zůstává svisle vycentrovaný; cílem je vizuální odstup od systémového indikátoru gest bez změny polohy či výšky lišty, vodorovného úchytu a pravidel gest. Skutečný odstup a ovládání na zařízení zatím NOT RUN.

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

### Vícejazyčný rukopis pera v dialogu kóty

**Opravy po uživatelské zkoušce sestavení 57 (25. 11. 2026; dosud bez nové APK):** Samostatně rozpoznaná slova na konci poznámky se oddělují mezerou; bez přidávání mezer do hodnoty kóty, výběru textu, uvnitř textu, před interpunkci nebo mezi dva číselné úseky. Pole mají větší minimální výšku a vlastní nepřekrývající se zachytávací okraj (5 dp po stranách, 6 dp svisle na výšku / 4 dp na šířku). Tah zahájený v této oblasti si zachovává cíl i mimo rámeček; společná vrstva náhledu není omezena na rámeček. Předchozí dokončené tahy zůstávají viditelné při zahájení dalšího tahu stejné dávky. Odstup akcí 4 dp je trvale rezervován ve svislém i vodorovném uspořádání bez ohledu na otevření, zavření či animaci klávesnice; velikost tlačítek zůstává stejná. Vlastník pozoroval dotyk tlačítka Uložit s rámečkem poznámky i při zavřené klávesnici v sestavení 57. Vlastní numerická klávesnice má krátkou 220ms animaci výšky bez pružení, systémová používá nativní animované insety Compose. Při předání se výšky nesčítají; odchozí numerická rezervace se drží do dokončení nativního pohybu nebo omezeného čekání, příchozí využije prostor uvolněný IME. Odchozí klávesnice nepřijímá vstup, dodatečné posunutí na aktivní pole se odkládá do ustálení IME (s časovým limitem). Nemění se data kóty ani renderer fotografie. Po nahlášení občasného neúspěšného přepnutí systémové/numerické klávesnice v sestavení 57 řídí nativní show/hide jedna zrušitelná relace s identitou požadavku. Krátkodobé změny viditelnosti při předávání fokusu neukončí novou relaci; zobrazení se při neúspěšném prvním pokusu jednou zopakuje a nepřítomná klávesnice není považována za dokončenou relaci. Klepnutí do aktivního pole při chybějícím IME požádá o nové zobrazení, bez změny jeho režimu. Stav otevření a zavření se potvrzuje po krátkém ustálení (180/120 ms); skutečné nativní chování při velmi rychlém Back nebo pozdní události je nutné ověřit na zařízení. Doplněny jednotkové scénáře krátkodobého zobrazení/skrytí a chybějícího IME. Ověřit plynulost, rychlé přepnutí oběma směry, různé výšky IME, plovoucí/hardwarovou klávesnici, orientace, Back a zachování textu; animace a výkon na telefonu NOT RUN. Nabídka jazyků používá lokalizované názvy, rozlišuje automatickou/ruční volbu a potvrzené stažené modely; technické identifikátory ponechává jen pro odlišení či vyhledání jazyků, modely x-gesture nejsou textovou volbou, ale zůstávají v katalogu SDK. Gesta samotná nejsou implementována. Přidané jednotkové scénáře pro mezery, zachování náhledu a katalog jazyků; kompilace, JVM testy, lint, APK a fyzické zkoušky (obě orientace, obě klávesnice, přechod přes pole a tlačítka, zrušený kontakt) **NOT RUN**. Při dalším ověření prověřit i pauzu uvnitř jediného slova a krajní polohy pera, protože mezera se odvozuje heuristicky, nikoli spolehlivou detekcí hranice slova.


Po pádu dialogu v sestavení 56 je obnoveno textové pole Compose ze sestavení 55. Při prvním dokončeném tahu aktivním perem bez potřebného modelu aplikace nabídne jeho stažení; hover, klepnutí ani prst nabídku nespouštějí. Stažení potvrďte, po dokončení napište text znovu. Odmítnutá automatická nabídka se v témže dialogu pro stejný jazyk neopakuje. V menu jednotek **Jazyk rukopisu** lze model stáhnout ručně nebo zvolit jiný jazyk. Výchozí automatická volba BCP‑47 odpovídá jazyku prostředků aplikace a přizpůsobí se jeho změně. Potvrzení stažení výchozího modelu neukládá ruční volbu; ručně zvolený jazyk lze vrátit na Automaticky. Ruční volba se ukládá samostatně a nemění jazyk aplikace ani klávesnice. Po stažení funguje rozpoznávání na zařízení i bez sítě. Bez modelu zůstává běžné zadávání dostupné.

Pište aktivním perem přímo do hodnoty nebo poznámky. Hover ani klepnutí nespustí rozpoznávání. Po krátké pauze se výsledek vloží do aktuálního výběru jako upravitelný text. Modelový výsledek nepotvrzuje hodnotu ani nezmění jednotku. Ruční změna vstupu či cíle zneplatní čekající výsledek. Během rozpoznávání dialog vyčká a rozpoznanou úpravu lze potvrdit tlačítkem **Uložit** nebo odmítnout tlačítkem **Zrušit**.

SDK Digital Ink Recognition 19.0.0 se používá podle podmínek Google ML Kit. Google uvádí, že se tahy a výsledný text zpracovávají na zařízení a neposílají Googlu. Provozní telemetrie SDK může obsahovat například jazyk modelu, technické parametry zařízení a aplikace, velikosti vstupů a výstupů nebo dobu zpracování. Aplikace inicializuje ML Kit při použití výslovně schváleného modelu. Stažení se vyžádá až po potvrzení. Sloučený manifest přebírá od závislostí oprávnění pro internet a stav sítě a pro práci na pozadí přes WorkManager. Přenos při běhu aplikace na zařízení je **NOT RUN**. [Návod Google ML Kit](https://developers.google.com/ml-kit/vision/digital-ink-recognition/android) · [Podmínky SDK](https://developers.google.com/ml-kit/terms) · [Přehled telemetrie](https://developers.google.com/ml-kit/android-data-disclosure)

Fyzicky ověřit automatickou nabídku po prvním tahu v obou polích (bez nabídky při hoveru, klepnutí, prstu a zrušeném tahu), odmítnutí a ruční stažení, změnu jazyka aplikace a návrat na Automaticky, model stažený po potvrzení, letový režim, SwiftKey, pero s hoverem i bez něj, klepnutí a dlaň, střídání polí, tečku/čárku a zlomek, otočení, zrušení a Undo/Redo. Pro tento krok jsou povolena pouze syntetická data.
