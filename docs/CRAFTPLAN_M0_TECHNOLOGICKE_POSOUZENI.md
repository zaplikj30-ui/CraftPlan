# CraftPlan – M0: srovnatelné technologické posouzení před výběrem technologie

**Datum:** 22. 11. 2026. **Stav:** technologicky neutrální rešerše a srovnávací zadání M0; **nejde o výběr frameworku, rendereru, databáze či licence**. Důkazy: aktuální oficiální veřejná dokumentace a označené odborné úsudky; fyzický Android, S Pen, buildy, benchmarky, testy i experimenty **NOT RUN**.

## 1. Zdroje pravdy a hranice

- [PR #1 – úplné zadání](https://github.com/zaplikj30-ui/CraftPlan/pull/1): J1–J12, F01–F39, 49 historických dílčích požadavků, 28 historických testů. Staré věty o dříve schválené technologii nepřebírat; novější rozhodnutí vlastníka stanoví **technologie nevybrána**.
- [PR #2 – plán M0–M5](https://github.com/zaplikj30-ui/CraftPlan/pull/2): návrhová M0-A, nezávislé DESIGN/SECURITY M0-B, jen po zvláštním GO experimenty M0-C, rozhodovací brána vlastníka M0-D a teprve pak M1.
- [PR #3 – M0-A kontrakty, matice 12+39+49+28 a návrhy T01–T54](https://github.com/zaplikj30-ui/CraftPlan/pull/3): přesné hodnoty, identity, měřicí pole, geometrie, save/reopen, ochrana médií, zálohy, rizikové scénáře a dosud otevřené části návrhu.
- [PR #4 – UX-01 až UX-101, ROZ-01 až ROZ-40, video](https://github.com/zaplikj30-ui/CraftPlan/pull/4): aktuální volby vlastníka. **UX-100:** koš se automaticky nemaže, dokud ho uživatel vědomě nezapne; v zapnutém režimu roční lhůta a varování. **UX-101:** diktování více kót bez jednotlivých OK, po ukončení jedna společná kontrola „Potvrdit vše“; předtím jsou hlasové hodnoty neověřené. VIDEO-01 až VIDEO-03: společný foto/video fotoaparát, natáčení i přehrávání uvnitř aplikace; bez kreslení a střihu videa.
- Nový CraftPlan je samostatný projekt. **Zaměřovač 1 se nemění ani se nepřebírá jeho kód nebo produkční prostředí.** Veřejný repozitář nesmí obsahovat zákaznická či soukromá média. Žádné programování, CI/Actions, HOME, APK, placené služby ani merge bez odpovídajícího GO.

## 2. Pokrytí dlouhodobého zadání – nejen fotoeditor M1

| Oblast / původní funkce | Co musí technologie umožnit a co nesmí poškodit |
|---|---|
| D01–D08, J7/J8, M0-A | Přesná měření a jednotky, stabilní ID a vazby, verze, oddělené originály médií, transakční ukládání metadat + souborů, Undo/Redo, obnova po chybě, koš, přenosná úplná záloha M2, bezpečný import/export. **UI framework ani samotná SQLite nejsou zárukou.** |
| J3/J4/J9, F09/F11/F14/F17/F20 | Interaktivní kóty/úhly/tvary, referenční úchopy a lupa, dotyk prst/S Pen, rychlé pan/zoom, nepřekrývání popisků, EXIF, 100/1000 tahů, vlastní symboly, výkon, editovatelnost po výměně fotografie. |
| J1/J5, F08/F12/F15/F16/F18/F19/F21 | Zákazníci, zakázky, vnořené Rychlé zaměření, soubory, galerie, třídění, filtry, vlastnoruční přesuny/kopie, panel nástrojů, nastavení témat, tablety/skládací obrazovky a budoucí překlady. |
| J2 + nové VIDEO-01–03 | Pořízení fotografie, potvrzovací náhled, vlastní bezdestruktivní kopie originálu, společný přepínač **Fotografie / Video**, přehrávání videa v aplikaci, miniatury, audio a velké soubory v přenosné záloze. Videa nejsou editovatelnými kótovacími dokumenty. |
| F01/F02/F06 | Vodorovná i sférická panoramata, kotvy přes 0°/360° a póly, pozdější prostorový náhled; nevázat datovou přesnost na obrazovkové pixely. |
| F03/J6 | Bluetooth dálkoměry s modelem měřicí události, jednoznačným cílem a typem pole, jednotkou, zpožděním, duplicitou, oprávněním a fyzickým protokolem; neověřená podpora různých značek se nesmí tvrdit. |
| F04/F05/F35 | Metrická topologie půdorysu, lokální souřadnice a identita stěny, navázané nárysy/otvory/fotky, instalační šablony a verzované zákaznické výstupy. |
| F10/F13 | Česká řeč bez povinné AI, bezpečně rozpracovaná kóta, plynulé diktování, závěrečná společná kontrola; nejistá 850/580 se nesmí vydávat za potvrzenou míru. AI případně zvlášť a později. |
| F29/F37/M4 | PDF a tisk s volitelnou titulní stranou, čitelností a skrytými poznámkami, náhled totožný s exportem, odlišný editovatelný přenos ZIP a bezpečné sdílení. |
| F07/F22–F28/F30–F34/F36/F38/F39 | Odložená srovnání, obchodní/QR/časové funkce, hranice samostatného Konfigurátoru–Nářezáku, veřejné vydání až po ověření a diagnostika. **F39 – malá dobrovolná diagnostika už v M1.** Přesný stav všech F01–F39 zůstává v katalogu a matici PR #3, neztratit žádné ID. |
| Windows/iOS/iPadOS – budoucnost | Otevřeně verzovaný a přenositelný doménový model; UI/hardware oddělené. Termíny portů nejsou schváleny. Názvem multiplatformní framework automatická podpora fotoaparátu, laseru či pera nevzniká. |

## 3. Kandidáti – jednotné předpoklady a rozdíly

**Shodné zadání pro všechny:** offline Android jako první platforma; M1 foto → přesná editovatelná kóta → prst/pero → Undo/Redo → pravdivé uložení → zavření → znovuotevření; pozdější M2–M5 a případné porty musí zůstat technicky realizovatelné. U všech kandidátů je provozní výkon a bezpečné uložení **NEOVĚŘENO**.

| Varianta | Silné stránky (zdroj / odborný závěr) | Hlavní riziko a budoucí cena údržby | Stav |
|---|---|---|---|
| **A Java + Android View/Canvas** | Nativní Android kreslení a MotionEvent/perové vstupy, přímá kamera/Bluetooth; klasický ověřitelný základ. | Android UI pro Windows/iOS v zásadě přepsat; výkon a náklady bez zkoušky neurčené. | POSOUZENO koncepčně, NOT RUN |
| **B Kotlin + Android View/Canvas** | Přímé Android API, kontrola hit-testů prst/S Pen, přímé hardwarové vazby; doménu lze oddělit. | Android-first UI nutno později nahradit na dalších platformách; vlastní specifický editor stále potřebuje testy. | **Vážný kandidát**, NOT RUN |
| **C Kotlin + Jetpack Compose Canvas** | Deklarativní UI a existující Canvas, stylus vstup včetně možnosti interoperace s MotionEvent. | Plynulost hustého editoru, prioritní gesta a chování S Pen nejsou změřené; pozor na stav a překreslování. | POSOUZENO, NOT RUN |
| **D Kotlin + Compose UI + nativní View/Canvas editor** | Oficiálně podporovaná interoperabilita; formuláře/galerie v Compose a citlivá kreslicí plocha v nativním View. | Dvě UI vrstvy, životní cyklus a hranice gest mohou zdražit údržbu. Výhoda vůči B/C musí být doložena. | **Vážný kandidát**, NOT RUN |
| **E Flutter + Dart** | Jeden UI směr Android/iOS/Windows; pointer události včetně stylusu a pressure/tilt, nativní integrační mosty. | Laser, kamera, video a část práce na pozadí přes pluginy/nativní mosty; stav jejich údržby, náklady a fyzická ergonomie pera neověřeny. | **Vážná multiplatformní alternativa**, NOT RUN |
| **F C++ + Qt 6 / Qt Quick** | Reálné platformy Android/iOS/Windows, 2D/3D technologie a desktop. | Vyšší komplexita mobile integrace, specifické moduly a povinnosti LGPL/GPL či komerční licence a LTS; **právní/cenové posouzení neprovedeno**. | POSOUZENO, NOT RUN |
| **G Kotlin Multiplatform doména + nativní UI** | Sdílení přesných pravidel, dat a validací napříč Android/iOS/desktopem; UI a hardware lokální. | Není hotový společný UI renderer; samostatné UI porty mohou být náročné. Potenciální pozdější rozšíření B/C/D, ne nutně alternativa místo nich. | Architektonická možnost, NOT RUN |

**Oficiální ověřené možnosti, nikoli PASS aplikace:**

- Android [Compose Canvas](https://developer.android.com/develop/ui/compose/graphics/draw/overview), [pokročilý stylus](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/advanced-stylus-features) a [propojení Views s Compose](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose).
- [CameraX architektura](https://developer.android.com/media/camera/camerax/architecture) podporuje use cases Preview, ImageCapture a VideoCapture; [Media3 ExoPlayer](https://developer.android.com/media/media3/exoplayer) přehrávání. **Kodeky a kombinace kamer se musí vyzkoušet na cílových telefonech.**
- [Flutter desktop](https://docs.flutter.dev/platform-integration/desktop), [platform channels](https://docs.flutter.dev/platform-integration/platform-channels), [PointerEvent](https://api.flutter.dev/flutter/gestures/PointerEvent-class.html) (stylus, tlak/tilt). Existující API není důkaz shodné ergonomie S Penu.
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform/kmp-overview.html) a [podporované platformy](https://kotlinlang.org/docs/multiplatform/supported-platforms.html). Sdílení domény není automatický port celé aplikace.
- [Qt podporované platformy](https://doc.qt.io/qt-6/supported-platforms.html), [licence](https://doc.qt.io/qt-6/licensing.html), [LTS dostupnost](https://doc.qt.io/qt-6/qt-releases.html).
- [Android app-specific soubory](https://developer.android.com/training/data-storage/app-specific): při odinstalaci mohou být smazány, **nejde o obnovitelnou přenosnou zálohu**.

### 3.1 Stejné osy srovnání, kvalitativní odhad (nikoli bodové skóre)

| Rozhodovací osa | Nativní Android A/B | Compose C | Hybrid D | Flutter E | Qt F | KMP G |
|---|---|---|---|---|---|---|
| Android perové události | Přímá API | Compose/interop | Přímá API editoru | Flutter + případný most | Qt + případný most | Podle použitého Android UI |
| UI přenos Windows/iOS | Malý | Potenciální další přestavba, ne automatický Jetpack port | Částečný podle další architektury | Potenciálně vysoký | Potenciálně vysoký | Doména ano, UI samostatně |
| Přístup ke kamera/Bluetooth | Přímý | Přímý | Přímý | Plugin nebo most | Adaptér/modul | Platformní API |
| Android-only údržba | Odborně spíše přímá | Odborně přímá | Dvě UI vrstvy | Kontrola pluginů | Nativní mobilní adaptace | Více UI variant |
| Ukládání, zálohy, přesnost | **Stejný povinný kontrakt, nic není automaticky splněno** | Stejný | Stejný | Stejný | Stejný | Stejný |
| Reálná latence, paměť, S Pen, výdrž | **NOT RUN** | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |

**Bez měření není pravdivé** tvrdit, že Kotlin/View musí být rychlejší než Flutter nebo Compose, nebo že Flutter bez úprav dokonale zvládne všechny lasery. Nesmíme vybírat podle popularity ani jediného číselného skóre.

## 4. Oddělené technické volby – předčasně je nespojovat

1. **UI jazyk/framework vs. renderer:** lze mít Compose obrazovky a nativní editor; potřebujeme samostatně srovnat složitost údržby a výsledný výkon.
2. **Přesný datový model:** hodnota, jednotka, původ, věcný typ, ID měřicího pole, souřadnice fotografie/panoramatu/stěny. Nesmí se odvodit skutečná míra jen z pixelů.
3. **Ukládání a media:** např. SQLite/Room oproti kontrolovanému jednoduchému souborovému návrhu; původní bajty samostatně, správná sekvence potvrzení/recovery, plný disk, kolize, vlastní koš. Databáze sama nemá všechny vlastnosti.
4. **Kamera/Video:** nejprve existující systémové API; přepínač Fotografie/Video je UX, a ne závazek napsat celou kameru od nuly.
5. **Česká řeč/AI:** dostupnost offline rozpoznávání a hlasového zopakování ověřit samostatně; UX-101 je bezpečnostní kontrakt, ne schválená AI služba.
6. **Sdílení vs. záloha vs. synchronizace:** přenosná záloha M2 před nenahraditelnými zakázkami; sync a cloud až samostatně později.
7. **Porty:** potřeba Windows/iOS je dlouhodobá, její časový horizont a rozsah UI ještě nejsou určené. Ten může zásadně změnit relativní výhodu B/D vůči E/F/G.

### 4.1 Srovnatelná rizika po kontrolách – bez předstírání benchmarku

**Rozhodovací brána není seznam podporovaných API.** Každá **vážně zvažovaná** cesta musí mít stejnou sadu problémů a důkazů. Pro následující oblasti se u každé varianty před rozhodnutím uvede **ZDROJ/ODHAD/NOT RUN/NEPOSOUZENO**: (1) 1/20/50/200 kót a prst/pero při 100/1000 tazích, (2) bezpečné potvrzení revize, pád/plný disk, (3) EXIF 1–8/velká fotografie a paměť, (4) foto/video, zvuk a kodeky, (5) PDF + ZIP/velké zálohy/obnova, (6) jeden konkrétní laser/protokol, (7) offline čeština a rozpracovaná hlasová dávka, (8) plán portu Windows/iOS/iPadOS, (9) licence, údržba, sestavení, opakované provozní náklady a případná závislost na balíčku. Nehodnotit jen teoretickou dostupnost knihovny jako `PASS`. **Žádné z těchto technických měření dosud neproběhlo.**

| Kandidát | Specifická integrační otázka, kterou musí mít výslovně vyřešenou |
|---|---|
| **A Java/View** | Je práce s přesným modelem/variantami dlouhodobě udržitelná a co bude nutné skutečně nahradit při portu UI? |
| **B Kotlin/View** | Jsou gestové vstupy/S Pen a renderování na zvoleném cílovém zařízení rychlé i se stejným bezpečným úložištěm? |
| **C Kotlin/Compose Canvas** | Co konkrétně znamená překreslování, práce se stavem a kolize se systémovými gesty u dlouhého tahu? |
| **D Compose + View editor** | Vyvažuje změřený přínos dvě vrstvy UI, jejich životní cyklus a testování mostu mezi editorem a obrazovkami? |
| **E Flutter** | Které funkce kamera/video/laser/pozadí potřebují nativní most, jak je udržovat a kolik reálně stojí Windows/iOS port? |
| **F Qt** | Které konkrétní moduly/licence/SDK a mobile adaptéry jsou skutečně použitelné za rozpočet a na cílovém hardware? |
| **G KMP + nativní UI** | Jaká část přesných dat je sdílitelná a kolik samostatného UI/hardware kódu vyžadují Windows/iOS? |

**Pravidlo malých zkoušek:** porovnávat nejdříve písemné podklady a existující udržované komponenty; pokud mezi kandidáty zbývá rozhodující nejistota, navrhnout jeden či několik **malých, předem srovnatelných, zvlášť povolených** experimentů, ne tři celé aplikace. U každého uvést zařízení, syntetická vstupní data, prahové hodnoty či důvod pro pozdější kalibraci, následky neúspěchu a rozhodnutí **POKRAČOVAT / ZMĚNIT SMĚR / STOP**. Návrhové posouzení neuděluje GO k experimentu.

**Bezpečnost a soukromí:** při technické volbě ověřit systémové Android zálohy a případné nečekané přenosy dat. Nový [PR #6](https://github.com/zaplikj30-ui/CraftPlan/pull/6) vyžaduje **jen jednu uloženou fotoverzi v aplikaci ve zvolené kvalitě**, bez automatického druhého originálu v systémové Galerii; externí importní zdroj se nemění. M2 přenosná záloha obnovuje skutečně uložené interní bajty, nikoliv neexistující plný externí originál.

## 5. Shodné testy k budoucímu GO – všechny NOT RUN

| ID | Scénář | Požadovaný důkaz |
|---|---|---|
| K01 | Syntetické foto EXIF 1–8, kóta/save/kill/reopen, otočení během tahu, PR #6 tři úrovně kvality a jedna interní uložená verze | Identické přesné hodnoty a kotvy, bajty autoritativní fotoverze beze změn při save/reopen, nepřítomnost dalšího automatického snímku v systémové Galerii; bez falešného save |
| K02 | 1/20/50/200 kót, 100/1000 tahů; dotyk konec/střed/popisek/lupa/zoom, prst a samostatně S Pen; diagnostika OFF/ON | P95/P99 input→obraz, jank, RAM, výkon, baterie, velikost aplikace, správná gesta; prahy stanovit až z cílového fyzického telefonu |
| K03 | Převody mm/cm/palce včetně 1/16, nula vs. NEZMĚŘENO, výpočet ploch a změna jednotky | Bez ztráty přesnosti, zaokrouhlení jen dle kontraktu, původní hodnota a zdroj zachované |
| K04 | ROZ-41/45: průběžný autosave dokončených kót, pád/kill, plný disk, nedostatek RAM, hovor či zamknutí uprostřed gesta, selhání před odchodem z editoru, staged draft a přerušení mezi médii a metadaty, opožděná revize, přechod na novější lokální schéma při plném disku nebo kill, rozdílná verze ZIP manifestu | Rozpracovaný tah bezpečně zrušen; dokončené změny nesmí být tiše ztraceny při běžném průběhu; nepředstírat obnovu čistě RAM po kill; potvrzené revize stabilní; neúspěšná migrace nesmí publikovat půl grafu ani zničit původní čitelná data, validní staged draft jen na výslovnou volbu; před běžným odchodem varování, žádná cache/video priorita nad integritou |
| K05 | ROZ-46: Rychlá zaměření a zakázky – kopírovat/přesunout strom a jednotlivou fotografii včetně kót, poznámek a příloh do Koše, obnovit, vysypat po zobrazení počtu a výslovném potvrzení; stejné názvy, zámky, nedostatek místa, částečná hromadná chyba, sdílená média | Všechny stabilní ID/vazby a média; fyzicky žádný napůl publikovaný stav složky/fotografie; nepřepíše se externí originál ani chráněné média; jednoznačný pravdivý výsledek po jednotlivých položkách; automatické mazání výchozí OFF |
| K06 | Záloha M2 jako běžný ZIP bez hesla: volitelný Koš (první výchozí ZAHRNOUT), vytvořit → `Uložit` do vybrané složky/dokumentového poskytovatele nebo `Sdílet` systémovou nabídkou Androidu (Google Disk/WhatsApp apod. podle dostupnosti) → restore na čisté instalaci bez původního telefonu; záloha více zakázek s různými potvrzenými revizemi a souběžným save/GC/Košem, negativní vadný ZIP, disk full, opakované exporty | Identický **konzistentní průřez** (snapshotCutId + přesná revizní mapa, žádný mix různých okamžiků), ID/přesná měření/bajty souborů, bez falešného potvrzení dokončení externího předání; žádný automatický upload, žádný požadavek hesla a bezpečné odmítnutí neúplného či škodlivého archivu |
| K07 | Foto → náhled OK/Znovu/Storno → editor; tři kvality jediné interní fotoverze, společná kamera foto/video, přehrání, audio a import | Jediné potvrzené uložení v CraftPlanu, žádné automatické publikování do Galerie; bezpoškozený externí importní zdroj, kodeky/oprávnění a stabilita na slabším Androidu |
| K08 | Referenční hotové panorama a šev; malý scénář půdorys–stěna–nárys, později prostorové zobrazení | Trvalé kotvy, topologie, souřadnice, Undo a návaznosti; nepsat nyní všechny moduly |
| K09 | Jednoznačný/nejistý Bluetooth laserový údaj, jednotka, cílové pole, zpoždění/duplicita | Žádná hodnota v nesprávném poli; hardware/protokol fyzicky až po GO |
| K10 | Český hlas 850/580, šum, nejasná jednotka, rozpracované kóty, společná kontrola a obnovení | Nepotvrzené hodnoty viditelně neověřené, oprava bez tiché chyby, užitečné offline chování reálně doložené |
| K11 | Schválené B01–B04/B07 PDF: plynulé výchozí rozložení a dostupné 1/2 fotografie, jedna automatická orientace pro celý soubor, `Obě verze` = dva oddělené PDF, výchozí zápatí číslo stránky/název ON a datum OFF, B07 nově dle ROZ-48: `Zahrnout polohu GPS` samostatně OFF při každém novém exportu, vědomě ON pro dostupné GPS vybraných fotek, chybějící GPS nevymýšlet; dva exporty po sobě s předtím zapnutými skrytými poznámkami a GPS a změnou revize během náhledu | Čitelný obsah a vazby foto/poznámky, dva úplné označené soubory ze shodného snapshotu, GPS OFF nezanechá polohu ani v metadatech/vložených obrazových souborech, ON přenese jen odpovídající dostupnou polohu a ne jiné EXIF/cizí data; každý NOVÝ export oba citlivé přepínače OFF, bez nechtěných skrytých textů/příloh, ostatní preference se pamatují |
| K12 | Portovací plán Windows/iOS: seznam UI/hardware adapterů, přenos dat, záloha, export, pero/laser | U každého kandidáta zdokumentovat co se přepíše, licenční podmínky, náklady a údržbu, bez vymyšlených odhadů času |
| K13 | Schválené B05/B06: jednoduchá úvodní nabídka; změna zobrazení miniatur/dlaždic/seznamu v galerii nebo zakázce; video záznam → přehratelný náhled → OK / Natočit znovu / Storno | Přepínání přehledů nekomplikuje úvod ani neztrácí uloženou preferenci; video uložit do správné složky jen po OK, Storno/neúplný soubor není potvrzená práce; nekreslit kóty do videa |
| K14 | Po samostatném GO dvě nezávislé zkoušky ROZ-47: Android D2D telefon→telefon a systémový cloud Auto Backup→restore, větší syntetické foto/video + kóty/poznámky/ZIP obsah, různé OS a chybové situace | Identické úplné ID/revize/vazby, přesné hodnoty, interní média byte-for-byte, editovatelnost a pravdivá informace o chybě; žádné nechtěné zveřejnění zákaznických dat; výsledky D2D/cloud oddělené, produkční výchozí nastavení vybrat teprve po validaci |

**Stav testu se musí psát PASS/FAIL/NOT RUN/BLOCKED dle skutečných důkazů.** Žádný uvedený test nebyl spuštěn. Bez samostatného GO nejsou dovoleny placené služby, Actions, emulátorové/fyzické experimenty ani APK.

## 6. Předběžné pořadí důkladného posouzení a otevřené otázky

**Historický odborný návrh pořadí rešerše, nikoli výsledek srovnatelných měření ani technologické schválení:** pro prvotní věcné srovnání byly označeny **B Kotlin/View/Canvas**, **D Kotlin/Compose + nativní editor** a **E Flutter**. Toto pořadí **nesmí být považováno za výběr ani automatické zúžení kandidátů** bez stejných předem stanovených kritérií. Varianta **C Compose Canvas** je důležitým samostatným srovnáním rendereru. **A Java, F Qt a G KMP** se nesmějí vyškrtnout bez zdůvodnění, zejména pokud se ukáže blízká potřeba Windows/iOS nebo zvláštní licence.

**Nepotřebujeme tři celé aplikace na zkoušku.** Po vyjasnění kontraktů a samostatném GO případně malé izolované testy K01/K02/K04/K07; K08–K10 jen tehdy, pokud riziko může změnit výběr technologie. Výběr rendereru, UI a databáze se má opírat o jejich vlastní důkazy.

**Před M0-D musí být uzavřeno:** přesná měřicí reprezentace a jednotky; ochrana originálů a pravdivé potvrzení uložení; zásady Undo po restartu; bezpečný manifest přenosné zálohy M2; souřadnice EXIF/panoramatu/půdorysu; podmínky použití samostatně neověřeného hlasu; reálné náklady a licence; cílová třída Android zařízení včetně potřeby S Pen; potřeba a realistický horizont Windows/iOS.

**Finance:** limit placených služeb 12 USD/měsíc; tento dokument nepovoluje žádný výdaj. Qt Licence a LTS dostupnost vyžadují samostatné právní/cenové posouzení. Flutter pluginy i Android knihovny je nutné posoudit jednotlivě v době použití; nechceme neudržovaný balíček jako nevratný základ.

### 6.1 Schválené drobnosti B01–B07 – důsledky pro M0-D

**Vlastník schválil všech sedm 22. 11. 2026, podrobné závazné znění má PR #4.** Nejsou to nové blokující technologické volby: B01 plynulé PDF default, B02 jednotná automatická orientace dokumentu, B03 dva samostatné PDF při exportu obou verzí, B04 zápatí číslo stránky/název zakázky ON a datum OFF, B05 čistá úvodní stránka bez přepínače miniatur, B06 video náhled s OK/Znovu/Storno, **B07 NOVĚ UPŘESNĚNO ROZ-48:** GPS v zákaznickém PDF jen po vědomém přepnutí (každý nový export výchozí OFF), bez GPS nebo dalších EXIF úniků, když je vypnuto. Předchozí schválené režimy a budoucí požadavky zůstávají. **K11/K13 jsou kontrolní scénáře NOT RUN**; u žádného kandidáta nepředstírat PDF/video výkon či bezpečnost. Přenosná M2 záloha zachová skutečně uložené bajty a není zákaznickým filtrovaným PDF.

### 6.2 ROZ-47 – skutečný test Android záloh před volbou výchozího chování (22. 11. 2026)

Vlastník upřednostňuje **vyzkoušet skutečný systémový přenos mezi telefony a úplně porovnat výsledek**, nikoliv předem funkci trvale vyřadit. **D2D a cloud Auto Backup vyhodnocovat zvlášť**; Android 12+ rozlišuje pravidla `device-transfer` a `cloud-backup` a výsledná dostupnost závisí na zařízení/OS/poskytovateli. Zaznamenat objektové ID, revisions, vnořené vazby, přesná měření, byte-for-byte interní fotky a přílohy, Koš, videa a možnost další editace. Nekompletní přenos není PASS, staré zákaznické údaje ani reálné soukromé fotografie nepoužívat. K dispozici je dokumentovaný Android D2D/cloud backup testing včetně obnovení na testovacím zařízení; ale **K14 NOT RUN** a konkrétní test/build až po samostatném GO. Výchozí produkční cloud backup a produkční automatické přenosy nejsou touto diskusí definitivně schváleny. Vlastní plná ruční ZIP záloha M2 zůstává bezpečnou nezávislou cestou.


## 7. Brána a další bezpečný krok

- **M0-A:** sjednotit návrhové smlouvy z PR #3 s posledními UX-100/101 a úplnou maticí požadavků; nedělat jen seznam políček.
- **M0-B:** nezávislé DESIGN/SECURITY review aktuálního M0 + UX a tohoto porovnání jiným chatem/osobou. Autorská kontrola není nezávislé schválení. Nálezy opravit a znovu zkontrolovat konečné SHA.
- **M0-C:** jen po výslovném GO vlastníka vybrané scénáře se syntetickými daty a předem definovanými zařízeními/rozpočtem; nyní NOT RUN.
- **M0-D:** vlastník teprve na základě srovnatelných důkazů či výslovně přijatých rizik rozhodne o UI, rendereru, ukládání a prvním schváleném malém M1 úkolu.

**Závěr:** Připravený technologický rozcestník a společné požadavky k nezávislé oponentuře; žádná finální technologie, renderer či úložiště nejsou vybrány, Android aplikace nebyla vytvořena a žádná podmínka M0-D není tímto textem automaticky splněna.
