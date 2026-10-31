# Twelve Pits (Ayo): Test plan for your phone

Written to be followed from a phone, one step at a time. Tick each line in your head and write down anything that does not match.

- **Part A** is the game on one phone. Run it on every release.
- **Part B** is the Two phones mode. It needs two phones.
- Run the **whole plan** (A and B) once more before every Google Play upload.

> This file did not exist before Phase 9, because the Phase 8 file set was not delivered. Part A is a short checklist made from the rules in `RULES.md` and the earlier phases. Add to it whenever a bug is found.

## Part A: one phone

### A1. Install and launch
1. Install the new APK over the old one. It installs without uninstalling first.
2. The launch screen shows the emblem, then the main menu. Menus are upright.
3. Music plays on the menu. Tapping any button makes a short tap sound.

### A2. Play against the computer
4. Play, then Against the computer, then choose Easy, Normal or Hard. Setup shows your name and avatar. There is no "Who starts" choice.
5. The game screen turns sideways (landscape) and fills the screen. Your own pits glow on your turn.
6. Tap an empty pit or the computer's pit: a short "no" sound and the pit shakes. Nothing else happens.
7. Play a turn: seeds are lifted and dropped one by one with a sound for each. A capture happens when a pit becomes exactly 4 seeds and plays the capture sound.
8. Play on Easy, Normal and Hard. The computer answers each time and never takes a move that is not allowed.
9. Pause: Resume, Restart, Settings and Quit to menu all work. Restart asks first.
10. Finish a game. The result screen shows Victory or Defeat with the right sound, the score and the statistics.

### A3. Two players on one phone
11. Play, then Two players on one phone. Enter two different names. The two avatars cannot be the same.
12. The first game starts with Player ONE. After the game ends and you choose PLAY AGAIN, the other player starts.

### A4. Saving, Continue, scores
13. Play three or four turns, press Home, and open the game again from the app list. The menu shows Continue.
14. Tap Continue: the board, scores and whose turn it is are exactly as you left them.
15. Settings, then Scores: the games you finished are counted for the right mode and level. Reset scores asks first and clears them.
16. Start a new game while a saved game exists: the game asks before it replaces the saved game.

### A5. Settings, sound, vibration
17. Settings: music, music volume, sound effects, vibration and seed speed (Slow, Normal, Fast) each work and are remembered after closing the app.
18. Switch Vibration on: the phone vibrates on tap and capture. Switch it off: it does not.
19. Take a phone call or turn on another music app while the game is on: the game sound pauses and returns.

### A6. Screens and orientation
20. How to play opens and its text matches the rules.
21. Leaving the game screen returns the menus to upright. Rotating the phone 180 degrees during a game keeps the game going.
22. Set the phone's font size to the largest: the menus are still readable.
23. With TalkBack on, every button has a name, and a pit is read with its owner and its number of seeds.

### A7. Release checks (from the GitHub build)
24. The build on GitHub is green and `TwelvePits-release-aab` is available.
25. The app asks for Vibrate, and for the Bluetooth, Wi-Fi and Location permissions of the Two phones mode only. It does **not** ask for the internet.

## Part B: Two phones

You need two phones. Call them **A (host)** and **B (guest)**. Both need Google Play services (the Play Store app opens and works). Both need Bluetooth and Wi-Fi switched on, and the phones must be next to each other. The game does not need the internet.

### B1. Connect and play
1. A: Play, Two phones, then HOST A GAME. B: Play, Two phones, then JOIN A GAME. Allow the permissions on both phones.
2. A shows "Waiting for a player..." with A's name. B shows "Looking for games nearby..." and then A's name and avatar in the list.
3. On B, tap A's name. Both phones show the same 4-digit code. Tap Yes on A and wait: the game does **not** start. Tap Yes on B: the game starts on both.
4. If both phones chose the same avatar, B's avatar is a different one. Names and avatars are shown right on both.
5. Each phone shows its **own pits at the bottom** and its **own store on the right**. Its own pits glow on its turn only.
6. Play a whole game, alternating taps. The boards, the seeds in the stores and whose turn it is stay **identical** on both phones. Both animations run, one turn after the other, and a phone is not tappable while it plays the other player's turn.
7. Try to tap the other player's pit, and any pit when it is not your turn: nothing happens (the phone does not move or send anything).
8. Make a capture on each side and cause a skipped turn: both phones show the same capture sound and the same skipped turn.
9. Pause on A (tap the pause control): the menu shows Resume, Settings and Quit to menu, **no Restart** and no move counter. B is not paused and can see A's turn wait.

### B2. Finish and play again
10. Finish the game. A and B each show their own Victory or Defeat, with the right sound. The winner is the same on both phones.
11. Settings, then Scores: A and B both show the "Two phones" row with the game counted from each phone's own side (one phone's "You" is the other phone's "Opponent").
12. On B tap PLAY AGAIN. B shows "Waiting for <name>..." with Cancel. A shows "<name> wants to play again" with Accept and No thanks.
13. Tap Accept on A: a new game starts on both. **The other player starts** this time, and A is still the host (bottom pits as before).
14. Finish that game, then tap PLAY AGAIN on A, and No thanks on B: A shows that the other player does not want to play again. Both can still go to the main menu.
15. Tap PLAY AGAIN on B and then Cancel on B: the question on A goes away.

### B3. Leaving and losing the connection
16. Start another game. On B turn **Bluetooth and Wi-Fi off** in the middle of the game: within about 15 seconds A shows "Connection lost" with a Main menu button, and returns to the menu without crashing. The game is not saved and no score is added.
17. Start another game. Press Back on A and confirm "Leave the game? The game ends for both players.": B shows "<name> left the game" and goes back to the menu after Main menu.
18. Start another game. On B press the Home button (leave the game app) and wait: A shows that B left. When B opens the game again, it shows the menu, not the old game.
19. Start another game, play three turns, then close the game on B by swiping it away: A shows "Connection lost" or "<name> left the game". Neither phone crashes.
20. On the result screen, press Main menu on one phone: the other phone's result screen shows that the other player left, and PLAY AGAIN is gone.

### B4. Permissions and errors
21. In the phone's settings for the game, switch the permission off. Open Two phones, tap HOST A GAME: a plain explanation appears, then Android's question. Say no: a clear message and Try again. Say no again, with "Don't ask again" if Android offers it: a button that opens the game's settings. Allow it there, come back and try again: it works.
22. On Android 11 or older only: switch the phone's Location off, then tap HOST A GAME. The game asks you to switch Location on and opens the right settings page. On Android 12 and newer this step does not appear.
23. On B tap a host name, and on A tap **No** on the code question: B shows that the other player did not accept, with a way back.
24. On B tap a host name and then wait without tapping Yes on either phone: after 60 seconds both phones show that the connection took too long, with a way back.
25. A phone without Google Play services shows "Not available on this phone" and the other two modes still work (test only if you have such a phone).

### B5. Side effects
26. The screen stays on during a two-phone game and goes back to normal afterwards.
27. A single-phone game that was saved before still shows Continue and continues correctly after two-phone games. Starting a Two phones game never asks to replace it.
28. Swap the roles: B hosts and A joins. Everything above works the same.
29. After all games, the battery and Bluetooth are not kept busy: leave Two phones and check that the phone no longer shows Bluetooth in use by the game.
