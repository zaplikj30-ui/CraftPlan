# CraftPlan

Offline Android aplikace pro zaměřování a dokumentaci zakázek. Vývojová verze M1 otevírá přibalenou syntetickou fotografii. Přiblížení se ovládá dvěma prsty, posun jedním. Po stisku **Přidat kótu** klepněte na dva body fotografie. Kóta zůstane ukotvená při změně přiblížení nebo posunu a zobrazí stav **NEZMĚŘENO**. Kóta je pouze v paměti.

## Debug sestavení

V připraveném prostředí CraftPlan spusťte z kořene repozitáře následující příkazy.

```sh
source /workspace/.craftplan-android/activate.sh
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Mimo prostředí CraftPlan použijte JDK 17 nebo 21, Gradle 8.13 a Android SDK 36. Debug sestavení je určeno pro Android 13 a novější.
