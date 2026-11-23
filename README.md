# CraftPlan

Offline Android aplikace pro zaměřování a dokumentaci zakázek. Vývojová verze M1 otevírá přibalenou syntetickou fotografii. Přiblížení se ovládá dvěma prsty, posun jedním i při výchozím přiblížení. Kóta může zasahovat do volné plochy kolem snímku. Po stisku **Přidat kótu** táhněte prstem nebo S Penem po fotografii od prvního bodu k druhému. Kóta zůstane ukotvená při změně přiblížení nebo posunu a zobrazí stav **NEZMĚŘENO**. Klepnutím na kótu ji vyberete. Tažením koncového úchopu změníte jeden konec, tažením středu posunete celou kótu. Při kreslení a tažení konce pomáhá lupa. Kóta je pouze v paměti.

## Debug sestavení

V připraveném prostředí CraftPlan spusťte z kořene repozitáře následující příkazy.

```sh
source /workspace/.craftplan-android/activate.sh
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Mimo prostředí CraftPlan použijte JDK 17 nebo 21, Gradle 8.13 a Android SDK 36. Debug sestavení je určeno pro Android 13 a novější.
