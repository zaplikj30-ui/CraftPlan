# CraftPlan

**Nová samostatná aplikace pro zaměřování a dokumentaci zakázek.** Nyní připravujeme návrh nového základu; nejde o hotovou Android aplikaci ani přejmenování původního Zaměřovače.

**Začni zde:** [START_ZDE.md](START_ZDE.md) – aktuální rozcestník, stav, schválené požadavky a důkazy. **Závazná pravidla práce AI:** [AGENTS.md](AGENTS.md).

## Produktový plán

- [CraftPlan – hlavní sjednocený základ a pravidlo, které rozhodnutí platí](docs/ZAMEROVAC_2_JEDNOTNY_ZAKLAD_A_PRENOS.md)
- [Živý návrh a rozhodnutí vlastníka](docs/ZAMEROVAC_2_ZIVY_NAVRH.md)
- [Kompletní katalog funkcí](docs/ZAMEROVAC_2_KATALOG_FUNKCI.md), [F01–F05](docs/ZAMEROVAC_2_F01_F05_ZADANI.md), [otevřené a budoucí nápady](docs/ZAMEROVAC_2_NEDORESENE_NAPADY.md)
- [Datový kontrakt M0](docs/ZAMEROVAC_2_USTAVA_DAT.md) a [vývojové etapy](docs/ZAMEROVAC_2_STROM_VYVOJE.md)

## Aktuální hranice

První plánovaný celek: **Android, technologie dosud NEVYBRÁNA, čistě offline fotografie → přesná editovatelná kóta → Undo/Redo → uložení a obnova**, s malou bezpečnou diagnostikou od začátku. Historický Kotlin + Jetpack Compose je pouze kandidát. Aktuální zdroje: [plán PR #2](https://github.com/zaplikj30-ui/CraftPlan/pull/2), [M0-A PR #3](https://github.com/zaplikj30-ui/CraftPlan/pull/3), [UX PR #4](https://github.com/zaplikj30-ui/CraftPlan/pull/4), [technologie PR #5](https://github.com/zaplikj30-ui/CraftPlan/pull/5), [fotografie PR #6](https://github.com/zaplikj30-ui/CraftPlan/pull/6). Přenosná záloha a prokázaná obnova patří do M2 před nenahraditelné zakázky; implementace ani APK nejsou schválené.

Repozitář je **PUBLIC**, proto nepřidávej žádné přístupové údaje, soukromé fotografie ani zákaznická data. GitHub-hosted Actions smí být použity jen podle aktuálních podmínek a samostatně schváleného workflow; **HOME runner zůstává RETIRED**.

**Historie:** dokumenty pod původním pracovním názvem „Zaměřovač 2“ byly převzaty z [PR #234 původního repozitáře](https://github.com/zaplikj30-ui/Zamerovac-android/pull/234) na zdrojovém HEAD `8c0f03e68788625e098f31756c069824503fac41`. Původní [Zaměřovač 1](https://github.com/zaplikj30-ui/Zamerovac-android) se vyvíjí odděleně. Jeho dřívější názory, PR, kód ani testy nejsou automaticky platnými rozhodnutími CraftPlanu.
