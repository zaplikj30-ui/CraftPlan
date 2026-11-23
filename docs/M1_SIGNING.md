# Testovací podpis CraftPlan M1

Instalační sestavení z větve `m1/first-dimension` podepisuje GitHub Actions jedním vyhrazeným klíčem pro `cz.craftplan.app`. Lokální varianta `debug` má vývojový podpis a verzi `1`. Pro aktualizace na zařízení používejte instalační variantu `m1` z GitHub Actions.

## Jednorázové nastavení vlastníkem

1. Na důvěryhodném počítači mimo klon repozitáře vytvořte PKCS#12. Zvolte silné heslo a zadejte ho pouze do interaktivní výzvy. PKCS#12 používá stejné heslo pro úložiště i klíč. Uschovejte zálohu souboru a hesla odděleně v úložišti pod vaší kontrolou.

   ```bash
   umask 077
   keytool -genkeypair -storetype PKCS12 -keystore craftplan-m1.p12 \
     -alias craftplan-m1 -keyalg RSA -keysize 3072 -validity 10000 \
     -dname 'CN=CraftPlan M1 Test Signing'
   ```

2. V repozitáři **zaplikj30-ui/CraftPlan → Settings → Secrets and variables → Actions → Secrets** uložte heslo jako `CRAFTPLAN_M1_KEYSTORE_PASSWORD`. Klíč zakódovaný do Base64 uložte jako `CRAFTPLAN_M1_KEYSTORE_BASE64`. S přihlášeným GitHub CLI lze poslat hodnotu přímo po standardním vstupu, takže se neobjeví v terminálu ani jako argument příkazu.

   ```bash
   base64 < craftplan-m1.p12 | tr -d '\r\n' |
     gh secret set CRAFTPLAN_M1_KEYSTORE_BASE64 --repo zaplikj30-ui/CraftPlan
   ```

3. Vyexportujte veřejný certifikát a zjistěte jeho SHA-256. V **Actions → Variables** vytvořte repozitářovou proměnnou `CRAFTPLAN_M1_CERT_SHA256` s první hodnotou z výstupu `sha256sum` (64 hex znaků). Otisk je veřejný údaj. Změna otisku znamená změnu podpisové identity, proto jej po prvním vydání zachovejte.

   ```bash
   keytool -exportcert -storetype PKCS12 -keystore craftplan-m1.p12 \
     -alias craftplan-m1 -file craftplan-m1-cert.der
   sha256sum craftplan-m1-cert.der
   ```

Hodnoty Secrets patří pouze do nastavení GitHubu. Příkazy spusťte bez trasování shellu. Dočasná kopie klíče při sestavení vzniká v dočasném adresáři runneru a po skončení kroku se odstraní. Artefakt obsahuje APK a jeho kontrolní součet.

## Kde vlastník uchovává lokální podpisové soubory (potvrzeno 23. 11. 2026)

Vlastník potvrdil, že **na ploše svého počítače** má uložené tyto tři soukromé soubory:

- `CraftPlan-M1-stabilni-podpis.p12` – stabilní podpisový klíč M1.
- `CraftPlan-M1-HESLO-SOUKROME.txt` – heslo k podpisovému klíči.
- `CraftPlan-M1-GITHUB-SECRET-BASE64.txt` – Base64 reprezentace téhož klíče pro GitHub Secret.

**Pouze evidence umístění; žádný z těchto souborů, jejich obsah ani heslo nejsou součástí GitHubu.** Umístění na ploše není bezpečná nezávislá záloha. Vlastník má vytvořit a chránit další soukromou zálohu mimo počítač. Obsahy nikdy neposílat do issue, PR, commitu ani veřejného chatu.

## Vydání a ověření aktualizace

Po schválení vydání pošlete na `m1/first-dimension` nový commit s textem `[publish m1 apk]` v commit message. Lze použít prázdný commit. Workflow při běžném pushi vydávací úlohu přeskočí. Pro nový pokus o vydání po chybě použijte další označený commit. Běžný rerun stejného run ID úlohu nevydá.

`versionCode` je 1000 + pořadové číslo běhu tohoto workflow. Zachovejte stejnou identitu workflow a nepoužívejte druhý zdroj číslování. Skript ověří konfiguraci, veřejný certifikát v keystore, podpis výsledného APK, ID balíčku, očekávané číslo verze a aktuální HEAD větve. Při selhání nevznikne instalační artefakt GitHub Actions.

1. Na syntetickém testovacím telefonu ověřte první APK M1 ze stabilního klíče. Starší APK podepsané jiným klíčem vyžaduje jednorázový přechod přes odinstalování. Použijte zařízení bez nenahraditelných dat.
2. V dalším schváleném běhu vytvořte APK se stejným certifikátem a vyšším `versionCode`. Ověřte kontrolní součty a proveďte aktualizaci přes systémový instalátor nebo `adb install -r novy.apk` bez mezikroku odinstalování. Zkontrolujte spuštění a novou verzi.
3. Až bude hotové trvalé ukládání, vytvořte před aktualizací syntetický dokument a po ní porovnejte jeho ID, obrazové bajty, kotvy a přesné hodnoty. Dnešní kóta je pouze v paměti.
