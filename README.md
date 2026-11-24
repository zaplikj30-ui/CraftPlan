# CraftPlan

Offline Android aplikace pro zaměřování a dokumentaci zakázek. Vývojová verze M1 otevírá přibalenou syntetickou fotografii. Přiblížení se ovládá dvěma prsty, posun jedním i při výchozím přiblížení. Kóta může zasahovat do volné plochy kolem snímku. Po stisku **Přidat kótu** táhněte prstem nebo S Penem po fotografii od prvního bodu k druhému. Kóta zůstane ukotvená při změně přiblížení nebo posunu a zobrazí stav **NEZMĚŘENO**. Klepnutím na kótu ji vyberete. Tažením koncového úchopu změníte jeden konec, tažením středu posunete celou kótu. Při kreslení a tažení konce pomáhá lupa. Kóta je pouze v paměti.

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

Fyzické ověření dotyku, dlouhého podržení, přetažení, otáčení, restartu a APK vyžaduje následné samostatné GO. Úprava nepřidává trvalé ukládání dokumentů, další kóty ani měření ploch a úhlů.
