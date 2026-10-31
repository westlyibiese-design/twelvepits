# Twelve Pits (Ayo): Google Play notes

A plain checklist, written to be followed from a phone. Google changes its rules often. Anything marked **verify in Play Console** is something to check on the screen in front of you, because the number or rule may have changed.

Contact email to use everywhere: **westlyibiese@gmail.com**

## 1. What you will upload

- Every successful GitHub run builds two signed files, named `TwelvePits-<version>.apk` and `TwelvePits-<version>.aab`.
- Google Play wants the **.aab** (the App Bundle). The .apk is only for installing on your own phone.
- On the GitHub run page, under Artifacts, download **TwelvePits-release-aab**. GitHub gives it to you as a .zip. Open the zip in your Files app and extract it to get the .aab.
- The version code goes up by itself on every run, so every upload is higher than the one before. The version name is `appVersionName` in `gradle.properties`.
- The app targets Android 16 (API level 36) and runs on Android 8.0 and up. Google raises the required target level every year: **verify in Play Console** that 36 is still accepted when you upload.

## 2. Create the Play developer account (once)

1. Go to https://play.google.com/console and sign in with the Google account you want to own the game.
2. Choose the account type: personal or organisation. Organisations may need a business registration number (a D-U-N-S number): **verify in Play Console**.
3. Pay the one-time registration fee (it was US$25 at my last information: **verify in Play Console**).
4. Complete identity verification (a government ID and sometimes a payment profile). Allow some days: **verify in Play Console**.
5. Use the developer name you want players to see. The game says "© Neribo Group", so the same name is best.

## 3. New developer accounts: testing before release

New personal developer accounts may be required to run a closed test with a minimum number of testers for a minimum number of days before the game can go to production. **Verify in Play Console** whether this applies to your account, how many testers and how many days. If it applies:

1. Create a closed test track and upload the .aab there.
2. Add testers by email or by a Google Group.
3. Ask them to install and keep the game for the required days.
4. Then apply for production access in Play Console.

## 4. Create the app

1. In Play Console choose **Create app**.
2. App name: `Twelve Pits (Ayo)`. Default language: English. App or game: **Game**. Free or paid: **Free** (you cannot change a free app to paid later).
3. Accept the declarations.
4. Package name is `com.westly.twelvepits`. It is fixed by the first upload and can never be changed.

## 5. Play App Signing (important to understand)

- When you upload the first .aab, Play asks you to use **Play App Signing**. Say yes.
- Your keystore (the one in the GitHub secrets) becomes the **upload key**. You sign every upload with it, exactly as the workflow already does.
- Google keeps the **app signing key**. Google uses it to sign the apps that reach players' phones.
- Do not lose your keystore. If you lose the upload key, Google can reset it, but it takes a support request.
- The .apk from GitHub is signed with the upload key. It installs fine on your phone for testing, but it is not the same signature as the Play version. If you later install the Play version on the same phone, uninstall the GitHub .apk first.

## 6. Store listing drafts

**App name** (30 characters at most): `Twelve Pits (Ayo)`

**Short description** (80 characters at most, this one is 80):

```
Classic Ayo board game. Play the computer, a friend on one phone, or two phones.
```

**Full description** (4,000 characters at most, this one is about 1,200):

```
Twelve Pits (Ayo) is a classic seed-sowing board game for two players, made for your phone.

Pick up the seeds from one of your pits and sow them anticlockwise, one seed in every pit. If your last seed lands in a pit that already has seeds, pick that pit up and keep going. Whenever a pit becomes exactly four seeds, those four seeds are captured. The game ends when 44 seeds have been captured, and the player with more seeds in their store wins.

PLAY YOUR WAY
- Play against the computer on Easy, Normal or Hard.
- Play with a friend on one phone and pass it between turns.
- Play with a friend on two phones, side by side. The phones connect directly over Bluetooth and Wi-Fi, with no internet.

MADE TO FEEL GOOD
- A full-screen landscape board with dark hardwood, antique gold and emerald glow.
- Smooth seed animation. Choose Slow, Normal or Fast seed speed.
- Music, sound effects and optional vibration, each with its own switch.
- Your game is saved after every turn, so you can continue later.
- The player who starts changes every game, so it stays fair.
- Scores are kept for every mode and level.

SIMPLE AND PRIVATE
- No account, no ads, no internet needed.
- Everything stays on your phones. Nothing is sent to any server.

A short how-to-play guide inside the game explains the rules in a minute.

Twelve Pits (Ayo) is published by Neribo Group.
```

The two-phone mode is described in the full description above.

**Category:** Games, then the Board category. **Tags:** pick what Play offers for board and strategy games.

**Contact details:** email `westlyibiese@gmail.com`. A website and a phone number are optional: **verify in Play Console**.

## 7. Graphics you must prepare yourself

Sizes and limits change, so **verify in Play Console** when you upload.

| Item | What it needs |
|---|---|
| App icon | 512 x 512 pixels, PNG. You already have it. |
| Feature graphic | 1024 x 500 pixels, PNG or JPEG, no transparency. Shown at the top of the listing. |
| Phone screenshots | At least 2. PNG or JPEG. Take them on your phone with the game running. The game screen is landscape, so landscape screenshots are right for it. Also add a menu or settings screenshot if you like. |
| Tablet screenshots | Optional, but recommended if you want the game to show as tablet-ready. |

## 8. Privacy policy link

Play asks for a link to a privacy policy. The text is in `docs/PRIVACY_POLICY.md`. The link must open **without signing in**, so:

- a link to the file on GitHub works only if the repository `westlyibiese-design/twelvepits` is **public**, or
- put the same text on a simple web page you control (for example on your Netlify or Cloudflare Pages site) and give that link.

The policy in `docs/PRIVACY_POLICY.md` already describes the two-phone mode (Bluetooth and Wi-Fi only between the two phones, nothing sent to any server). Put the updated text at your link before you release this version, and keep it in step with the game.

## 9. App content (the declarations)

Answer these in **Policy and programs, App content**. The answers below are what is true for the game today.

**Privacy policy:** add the link from section 8.

**Ads:** the app does **not** contain ads.

**App access:** all functionality is available without special access (no login).

**Data safety form:**

- Does the app collect or share any of the required user data types? **No.**
- The app has no account, no analytics, no ads and no internet permission. Everything stays on the phone.
- The Two phones mode sends the player's name, avatar and moves **directly to the other player's phone** over Bluetooth and Wi-Fi (Google Play services Nearby Connections). Nothing is sent to the developer or to any server, so nothing is "collected" or "shared" in the sense of the form. Google's Data safety help says data processed only on the device or passed directly between two devices without leaving them is not collected: **verify in Play Console** that this still reads the same way, and read the exact wording of the questions before you answer.
- Is all of the user data collected by your app encrypted in transit? This question does not apply when nothing is collected: **verify in Play Console** how the form shows it.
- Do you provide a way for users to request that their data is deleted? Not applicable. No data is collected. The player can clear everything by uninstalling the game.
- Note: player names that the player types stay on the phone only. They are never sent anywhere, so they are not "collected".

**Content rating questionnaire (IARC):** choose the category **Game**, then answer honestly. The expected answers:

- Violence: no. (Seeds are moved on a board. There are no characters being hurt.)
- Blood, fear, sexual content, nudity, bad language: no.
- Drugs, alcohol or tobacco: no.
- Gambling or simulated gambling: no.
- Users can talk to each other or share content: **no chat and no sharing of content.** In the Two phones mode the two players only exchange the name and avatar they chose and their moves. The questionnaire may ask about "users interacting": read the question and answer with this fact: **verify in Play Console**.
- Shares the user's location: no. (On Android 8 to 12 Android makes the game ask for Location so that it can search for the other phone, but the game never reads or shares where the player is.)
- Lets users buy things: no.

The expected result is the lowest age rating. The questionnaire decides the real result: **verify in Play Console**.

**Target audience and content:**

- Pick the age groups the game is aimed at. A board game suits all ages, but choosing groups that include children brings extra rules (the Families policy). The simplest choice is to select only age groups of 13 and over. **Verify in Play Console** and read the question carefully.
- Does the game appeal to children? Answer honestly.

**Permissions used by the Two phones mode** (the full list is in `app/src/main/AndroidManifest.xml`; the list follows Google's "Nearby Connections: get started" page, and each line has the Android versions it applies to):

| Permission | Android versions | Why |
|---|---|---|
| `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` | 12 and newer | Be found by, find, and talk to the other phone. |
| `NEARBY_WIFI_DEVICES` (flag: never for location) | 13 and newer | Wi-Fi connection to the other phone. |
| `ACCESS_FINE_LOCATION` | 10 to 12 | Android requires it to search for nearby phones. Never read by the game. |
| `ACCESS_COARSE_LOCATION` | 8 and 9 | Same reason. |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | 11 and older | Old Bluetooth permissions. |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | 12 and older | Wi-Fi state for the connection. |

The game still has **no internet permission**: the manifest removes it even if a library tries to add it, and the GitHub build fails if it ever appears.

- Play Console may ask you to **declare the location permission** (a form that asks why the app needs `ACCESS_FINE_LOCATION`) and, if the app is shown to families, may ask extra questions. Answer: the permission is used only to find the other phone for the Two phones mode on Android 10 to 12, the location is never read, stored or sent, and the mode is optional. **Verify in Play Console** which forms appear.
- If Play asks about **Bluetooth** use, answer the same way: only to connect to the other player's phone.
- The game works on phones without Bluetooth or Wi-Fi hardware (the other modes). In the "device catalog" Play may show that the Two phones mode needs **Google Play services**: **verify in Play Console**.
- Two phones needs Google Play services on both phones. A phone without it shows a message and the other two modes still work.

**Other declarations:** not a news app, no government or health features, no financial features, no COVID-19 features, no advertising ID used. The app does not use the advertising ID because it has no ads. If the form asks about the advertising ID, answer that the app does not use it: **verify in Play Console**.

## 10. Release steps

1. In Play Console open your app, then **Testing** (internal or closed) or **Production**.
2. **Create new release**, upload the .aab.
3. Release name: leave the suggestion, or use the version name.
4. Release notes: for the first release, write for example: `First release of Twelve Pits (Ayo). Play the computer, a friend on one phone, or a friend on another phone nearby.`
5. Set the countries or regions.
6. **Review** the release, then **Start rollout**.
7. Google reviews the app. The first review can take days: **verify in Play Console**.

## 11. Before every update

1. Push the new code. Wait for the green tick on GitHub Actions.
2. Download the new `TwelvePits-release-aab` artifact.
3. Create a new release and upload the new .aab. The version code is already higher.
4. Write release notes for the players.

## 12. After the game is live

- Check **Android vitals** and **Pre-launch report** in Play Console for crashes.
- Upload the R8 mapping file only if Play asks for it. It is already inside the .aab.
- Keep your keystore and its passwords safe in more than one place.
