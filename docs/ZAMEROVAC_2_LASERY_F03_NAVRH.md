# F03 – plán podpory laserových dálkoměrů více výrobců

> **CraftPlan (historicky „Zaměřovač 2“).** Tento dokument je součástí aktuálního produktového zadání CraftPlanu. Pochází z [PR #234 původního Zaměřovače](https://github.com/zaplikj30-ui/Zamerovac-android/pull/234); odkazy na jedničku jsou historické reference, nikoli pokyny ke kopírování starého kódu nebo workflow. Platí [hlavní pravidlo zdroje pravdy](ZAMEROVAC_2_JEDNOTNY_ZAKLAD_A_PRENOS.md).

**Zaměřovač 2 · 21. 11. 2026 · dokumentační návrh schváleného směru, nikoli implementace ani ověřená kompatibilita.**

## 1. Co vlastník schválil

Zaměřovač 2 má postupně zvládnout **co nejvíce laserových dálkoměrů různých značek**, nikoli jen Bosch, bez nutnosti kupovat každý model. Jedno uživatelské ovládání **Připojit měřidlo → přijmout měření → přiřadit do aktivní kóty**. Podpora ručního zadávání zůstává vždy. Technické protokoly a kompatibilitu řeší programátoři, nikoli uživatel; konkrétní značky se vyberou podle dostupných podkladů a přínosu v pozdější etapě.

V [seznamu ImageMeter](https://imagemeter.com/manual/bluetooth/devices/) je mnoho konkrétních modelů Bosch, Leica, Hilti, DeWalt, Stabila, Laserliner, Würth, Mileseey, Xiaomi aj. **To je referenční veřejný seznam kandidátů, nikoli seznam modelů podporovaných Zaměřovačem**. Převzít pouze veřejné faktické parametry/odkazy, **nekopírovat kód, grafiku, obchodní značku ani neveřejné protokoly z ImageMeteru**. Použít legálně dostupné originální návody, výrobní SDK/protokoly, normální veřejné API a vlastní čisté implementace se zkontrolovanými licencemi.

## 2. Zásadní poznatek – neexistuje jediný univerzální protokol

Bluetooth je pouze komunikační cesta, nikoli společný formát měření. I dva modely téže značky mohou používat odlišné zprávy či jednotky. ImageMeter podle veřejného seznamu rozlišuje **Bluetooth Low Energy (BLE), Bluetooth Classic i zařízení s klávesnicovým/HID vstupem**; některé přístroje neposílají jednotku a některé nejdou automaticky identifikovat. Jeho dokumentace sama nabízí ruční výběr protokolu, pokud automatické rozpoznání selže.

**Tři dostupné cesty (nejprve ověřit na konkrétních modelech):**

| Varianta | Kdy | Výhoda | Riziko/omezení |
|---|---|---|---|
| A. Vlastní adaptér pro dokumentovanou rodinu protokolů | Standardní BLE/GATT notifikace, Bluetooth Classic zprávy či HID/klávesnice podle konkrétního přístroje | Lehký, opakovatelný pro kompatibilní modely | Žádné tvrzení o podpoře celé značky bez ověření |
| B. Oficiální SDK/API výrobce (např. Leica DISTO) | Když dostupné, vhodné pro Android a jeho licence to dovoluje | Menší riziko chybného dekódování, více funkcí zařízení | Licenční podmínky, přístup a cílovou platformu teprve prověřit; nevydávat C# SDK za hotové Android SDK |
| C. Ruční vložení naměřené hodnoty | Neznámý protokol, nedostupné zařízení, nemožnost ověřit bezpečnost | Funguje bez BT a bez dalších nákupů | Přenos není automatický; neznačit jako podporovaný laser |

**Doporučení:** jednoduché jádro a adaptéry podle **ověřeného protokolu/modelové rodiny**, nikoli „jeden adaptér na značku“ ani hned pluginový framework. Pro neznámé zařízení nabídnout pouze detekci, diagnostiku se souhlasem a ruční zadání; při nedoložené jednotce nehádat délku.

## 3. Návrh oddělených částí (ne implementační API)

```text
Android: Najít/připojit Bluetooth zařízení, oprávnění, stav spojení
                  |
         Ověření konkrétního modelu/protokolu
                  |
       Malý adaptér (BLE / Classic / HID / SDK)
                  |
      Přísná validace měření, jednotky, typu hodnoty
                  |
       Jediná standardní událost Měření
                  |
         Aktivní kóta / historie / ruční vstup
```

Standardní událost má odděleně uchovávat: **přesnou numerickou hodnotu bez binárního zaokrouhlení** (například Decimal/řetězec podle zvoleného datového kontraktu), explicitní jednotku, typ veličiny (délka, plocha, úhel – nesměšovat), identitu zdrojového zařízení/protokolu a čas/unikátní identifikátor události. Nedovolené/NaN/nekonečno/neznámou jednotku/nevalidní rozsah odmítnout; duplicitní a zpožděné pakety nesmějí znovu nebo na nesprávnou kótu přepsat poslední měření. **Žádný neznámý přijatý údaj nezapsat automaticky do již uložené kóty.** Jedna potvrzená operace se zapisuje jako samostatná editace pro Undo/Redo.

Když zařízení neposílá jednotku, modelový profil musí mít **vědomě potvrzenou jednotku**, zobrazenou před přenosem; pokud není jistá, hodnota je **NEOVĚŘENÁ** a do kóty nejde bez potvrzení uživatele. Samotný přepínač na přístroji může měnit jednotky nezávisle na aplikaci, což je důvod k fyzickému testu.

Ochrana: při skenování a spojení použít oficiální Android oprávnění pro příslušné verze (Android 12+ **BLUETOOTH_SCAN / BLUETOOTH_CONNECT**), odmítnuté oprávnění nesmí poškodit práci; žádné odesílání zákaznických fotografií či kót výrobcům zařízení. Vynechat skenování na pozadí bez potřeby, omezit počet pokusů a odpojovat bezpečně. Citlivé surové BT pakety neshromažďovat trvale; cílený dočasný diagnostický záznam jen na výslovné zapnutí a po anonymizaci.

## 4. Konkrétní kandidáti pro pozdější etapy (nejde o podporu naší aplikací)

| Kandidát | Co je externě doloženo | Co ještě nemáme |
|---|---|---|
| **Bosch GLM 100-25 C** | ImageMeter veřejně uvádí **BLE, automatická detekce ne, ruční volba protokolu, přenos délky, jednotku nepřenáší**; Bosch deklaruje Bluetooth přenos do MeasureOn | Náš vlastní dekodér, přesný protokol, zdroj pravdy pro jednotku a fyzický PASS v Zaměřovači 2 |
| **Bosch GLM 50 C (starší)** | V ImageMeter evidován **Bluetooth Classic** a jiná podpora než u 100-25 C | Prověřit konkrétní protokol; není bezpečné jeden označit za druhý |
| **Leica DISTO D2/D810 a další** | Leica zveřejňuje kompatibilní DISTO přístroje a informace pro vývojářské partnery; ImageMeter u části uvádí **BLE a přenos jednotek** | Podmínky použití SDK/API v naší Android aplikaci, modelová verze a test |
| **Hilti PD-38 / PD-I** | ImageMeter: PD-38 **Classic**, PD-I **BLE**, tedy rozdílné přenosové varianty | Nezávislé legální podklady k dekódování a fyzické ověření |
| **Stabila LD250 BT / LD520, DeWalt DW0330S** | ImageMeter uvádí tyto kandidáty mezi Bluetooth LE podporovanými modely | Konkrétní GATT profil, rozsah funkcí a ověřený test |
| **HID/klávesnicové měřidlo** | ImageMeter popisuje rodinu měřidel předávajících údaj jako klávesnicový vstup | Bezpečné rozpoznání a přiřazení celé hodnoty s jednotkou, aby klávesové vstupy nepřepisovaly jiná pole |

**Externě ověřená inspirace k vzdálenému spouštění (21. 11. 2026):** ImageMeter má v [oficiálním seznamu Bluetooth zařízení](https://imagemeter.com/manual/bluetooth/devices/) samostatný sloupec **Remote Trigger**. Uvádí například **Bosch GLM 100-25c: ano**, **GLM 50c: ano**, ale **PLR 50c: ne**; současně u jednotlivých modelů rozlišuje délku, plochu, úhel a přenos jednotky. **Toto jsou pouze funkce uvedené pro ImageMeter, nikoli ověřené možnosti v Zaměřovači 2.** Požadavek vlastníka: když laser leží nebo je upevněný, lze – *je-li konkrétní model kompatibilní* – stisknout **„Změřit“ přímo v aplikaci**, laser skutečně dálkově spustit a přesný výsledek převzít do vybrané kóty. Předem rozlišit přenos hodnoty (laser spustí uživatel na přístroji) a vzdálený příkaz k měření (spouští jej telefon). K implementaci použít **vlastní kód a legálně dostupný protokol/SDK**, ne kód ImageMeteru. Při ověřování každého modelu evidovat zvlášť: **příjem hodnot**, **vzdálené spuštění**, **délka/plocha/úhel**, **jednotky** a samostatný fyzický PASS/NOT RUN. USB/Wi-Fi jsou jen případné alternativní komunikační cesty pro konkrétní zařízení, **ne povinné nové funkce**. Zatím bez implementace a bez fyzického testu.

**Neseřazeno jako slib vydání.** První pilot: **jeden dostupný Bosch**; další odlišná rodina podle dokumentace (pravděpodobně Leica BLE či jiný veřejně doložený BLE profil); teprve po úspěchu rozšiřovat do Classic/HID a dalších značek.

## 5. Jak dokážeme podporu bez nákupu každého laseru

1. **Dokumentační důkaz:** pro model zapisovat výrobce, přesné označení, Bluetooth Classic/BLE/HID, formát/protokol nebo oficiální API, licenční stav, které typy hodnot a jednotky umí přenést. Pokud chybí, stav **NEOVĚŘENO / nehlásit podporu**.
2. **Věrný automatický test dekodéru:** pouze z legálně dostupných veřejných vzorků nebo reálného anonymizovaného záznamu vlastního přístroje; otestovat čísla, jednotky, délku/úhel/plochu, duplicity, záporné/chybné hodnoty, fragmentované pakety, přerušení, opětovné spojení a změnu aktivní kóty. **PASS dekodéru není fyzický PASS měřidla.**
3. **Fyzický test konkrétního modelu, kdykoliv bude dostupný:** skutečný laser a Android, přesný firmware, nastavení jednotek, přenos 1,234 m a dalších známých hodnot, ruční kontrola na displeji laseru, chybové stavy, Bluetooth zap/vyp, oprávnění, opakované připojení, historie, Undo/Redo a uložení/obnova. Testovat i když laser hlásí plochu či úhel do aktivní **délkové** kóty (musí odmítnout).
4. **Matice stavu:** `KANDIDÁT / PROTOKOL DOLOŽEN / DEKODÉR PASS / TELEFON+MODEL PASS / NENÍ PODPOROVÁN`. Do aplikace a nápovědy uvádět pouze opravdu ověřenou úroveň. Přístroje nepodporované nebo bez protokolu nesmějí dostat štítek „funguje“.
5. **Alternativa bez vlastního přímého protokolu:** prověřit, zda výrobce dovoluje vědomý import hodnot standardním sdílením/souborem/klávesnicovým vstupem. I to vyžaduje validaci a nesmí se vydávat za automatickou podporu Bluetooth.

## 6. Rozhodnutí a odložené detaily

**FAKT:** ImageMeter uvádí velký seznam modelů a rozdíly přenosu; Leica má výrobní ekosystém pro integrační partnery; Android má samostatné BLE/Classic/HID mechanismy a oprávnění. **POTVRZENÝ CÍL:** naše vlastní postupně rozšiřovaná podpora přesných měření s jedním UX. **NEOVĚŘENO:** funkční implementace konkrétních protokolů v Zaměřovači 2, skutečná kompatibilita, přesné pořadí modelů; žádný nákup nutný nyní. **NEIMPLEMENTOVAT nyní** generický plugin systém nebo velkou knihovnu adapterů bez věrného pilotního testu.

Ve v2 **M0** vymezit jen datový kontrakt měření nezávislý na hardwaru. V samostatné pozdější etapě **M5** nejprve jeden reálný adaptér, jeho testy a vlastní SOURCE/SECURITY review; nezávislá kontrola a GO vlastníka před integrací. Nezaměňovat to s dokončenou opravou experimentálního `BoschClassicReader.java` ze Zaměřovače 1.

**Veřejné zdroje (ověřeno 21. 11. 2026):**
- https://imagemeter.com/manual/bluetooth/devices/ — faktický přehled modelů, jednotek a typů komunikace (referenční seznam, **ne převzetí jejich implementace**).
- https://imagemeter.com/manual/faq/index.html — automatické rozpoznání nemusí stačit, možnost manuálně zvolit protokol.
- https://shop.leica-geosystems.com/measurement-tools/disto/leica-disto-apps — kompatibilní DISTO modely.
- https://leica-geosystems.com/about-us/partners/development-partner — vývojářské partnerství/rozhraní; licenci a vhodnost pro Android je nutné ověřit.
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions — Android BT oprávnění.
- https://www.bosch-professional.com/gb/en/products/glm-100-25-c-0601072Y00 — podpora Bluetooth pro Bosch GLM 100-25 C.
- [F03 v inventuře](ZAMEROVAC_2_INVENTURA_POZADAVKU.md), [katalogu funkcí](ZAMEROVAC_2_KATALOG_FUNKCI.md) a [registru otázek](ZAMEROVAC_2_NEDORESENE_NAPADY.md).
