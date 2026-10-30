# Twelve Pits (Ayo): Game Rules

These rules are final. The game engine must follow them exactly. Do not add, remove or change any rule unless the owner says so.

## 1. Board and pieces

- 12 pits in two rows of 6.
- 48 seeds in total.
- Two players: Player 1 (bottom row) and Player 2 (top row).
- Each player owns the 6 pits on their own side.
- Each player has one store, at one end of the board (left and right). Captured seeds go into the owner's store.
- Every pit and every store shows its seed count as a small number.

### Pit numbering (use this in code)

Seeds travel **anticlockwise**. Number the pits 0 to 11 in the direction of travel:

- Player 1 owns pits **0 to 5**: the bottom row, left to right.
- Player 2 owns pits **6 to 11**: the top row, right to left.
- The next pit after pit `i` is `(i + 1) % 12`. After pit 11 comes pit 0.

## 2. Setup

- Every pit starts with **4 seeds**.
- Both stores start at 0.
- Nothing is captured at the start.

### Who plays first (alternates every game)

Playing first can be an advantage, so the starting player **alternates every game**:

- If Player 1 plays first in this game, Player 2 plays first in the next game, then Player 1 again, and so on.
- The very first game ever: Player 1 starts (in a game against the computer, the human is Player 1).
- The starter changes when a game **finishes** (44 seeds captured). It applies to "Play again" and to any new game started after a finished game.
- **Restart** during a game, or leaving a game unfinished and starting a new one, does **not** change the starter. The new game uses the same starting player as the unfinished one.
- The game remembers who started the last finished game and **saves it**, so the alternation continues after the app is closed. The record is kept separately for each game mode (against the computer, two players on one phone, two phones).
- A game that is resumed after closing the app keeps the same starting player it already had.
- The saved record of who starts next is reset only if the player resets their scores.

## 3. Taking a turn

1. Pick **one of your own pits** that has seeds. You can never start from your opponent's side.
2. Pick up **all** the seeds in that pit. The pit is now empty.
3. Drop one seed in each following pit, one at a time, starting with the next pit.
4. You drop a seed in **every** pit you pass. If you have 12 or more seeds and go all the way round, the pit you started from also gets a seed like any other pit.

## 4. Relay (keep going)

When the last seed of a sowing lands:

- **In an empty pit:** your turn ends.
- **In a pit that already had seeds** (either side): pick up **all** the seeds in that pit, including the one you just dropped, and keep sowing from the next pit.
- This repeats until the last seed lands in an empty pit, or the last seed makes a capture (section 5).
- Pits with 12 or more seeds follow the same rule. There is no special case.
- **Endless relay (very rare):** in a few positions the relay never ends, because the same board comes back again and again with no capture. If the last seed lands in a pit that already had seeds, and the whole board and that pit are **exactly the same as at an earlier landing in the same turn**, the turn ends at that moment. No more seeds are sown, nothing more is captured, and play passes to the other player as usual (section 6). This rule never applies to a normal turn.

## 5. Capture

Capture is the **only** way seeds go to a store.

- Whenever a dropped seed makes a pit hold **exactly 4 seeds**, those 4 seeds are captured. The pit becomes empty. They go into the store of the **owner of that side**, except when it was your **last** seed: then they go into **your** store, even if the pit is on your opponent's side.
- This applies to every seed you drop, on both sides, during the first sowing and during any relay.

What happens next depends on whether it was your last seed:

| Situation | Result |
|---|---|
| The pit becomes 4 in the **middle** of your sowing (not your last seed), on **your** side | You capture it and your sowing **continues** |
| The pit becomes 4 in the **middle** of your sowing (not your last seed), on your **opponent's** side | Your opponent captures it and your sowing **continues** |
| Your **last** seed makes **your own** pit 4 | You capture it and your turn **ends** |
| Your **last** seed makes your **opponent's** pit 4 | **You** capture it and your turn **ends** |

- A pit with 5 or more seeds cannot be captured. It can only be captured again after it has been emptied and refilled to exactly 4.
- The starting position has all pits at 4, but nothing is captured at the start. A pit is only captured when a dropped seed makes it exactly 4.

## 6. After a turn

- Play passes to the other player.
- If the player whose turn it is has **no seeds on their own side**, their turn is **skipped** and the other player plays again.
- When a turn is skipped, the game must show a clear message and play a sound so the player knows why.
- The skipped player gets their turn back once they have seeds on their side again.

## 7. End of the game

- The game continues until **44 seeds** have been captured (11 captures of 4 seeds).
- The game ends **immediately** when the 11th capture happens, even in the middle of a sowing.
- The last **4 seeds** stay on the board and are not played or counted.
- The player with **more seeds in their store** wins.
- A draw is impossible: 11 captures split between two players always leaves one with at least 6 captures (24 seeds) against at most 5 captures (20 seeds).
- A player reaching 24 seeds does **not** end the game early. Play continues until 44.

## 8. Algorithm for the game engine

```
sowTurn(startPit):                      // startPit is one of the current player's pits, and not empty
    seeds = board[startPit]; board[startPit] = 0
    current = startPit
    loop:
        while seeds > 0:
            current = (current + 1) % 12
            board[current] += 1
            seeds -= 1
            if board[current] == 4:
                // last seed (seeds == 0): the mover takes it, even from the opponent's side
                receiver = (seeds == 0) ? mover : ownerOf(current)
                store[receiver] += 4
                board[current] = 0
                capturedTotal += 4
                if capturedTotal == 44: return GAME_OVER
                if seeds == 0: return END_TURN     // last seed made a capture: turn ends
        // last seed has landed on `current`
        if board[current] == 1: return END_TURN    // pit was empty: turn ends
        if (board, current) already seen this turn: return END_TURN   // endless relay (section 4)
        remember (board, current) for this turn
        seeds = board[current]; board[current] = 0 // relay: pick up the whole pit
        // continue the loop and sow from the next pit
```

`ownerOf(pit)`: pits 0 to 5 belong to Player 1, pits 6 to 11 belong to Player 2.

After `END_TURN`, switch players. If the new current player has no seeds in their 6 pits, skip their turn and show the message and sound.

**Engineering note (not a game rule):** the endless relay rule above already stops every endless turn, so this limit is only a last safety net. Add a very high step limit in the relay loop (for example 10,000 sowing steps in one turn) only to stop the app from freezing if there is a bug. If it is ever reached, log an error. It must never be reached in normal play.

## 9. Test cases (the engine must pass all of these)

Pits are written p0 to p11.

**Test 1: first move.** Start position, Player 1 picks p0.
Expected end state: p0=2, p1=7, p2=1, p3=6, p4=1, p5=6, p6=6, p7=6, p8=0, p9=1, p10=6, p11=6. No captures. Total on board is 48.

**Test 2: mid-sowing capture.** Player 1 picks p1 with 3 seeds. p3 holds 3. p4 is empty. Sowing: p2 +1, p3 becomes 4 (captured by Player 1, p3 emptied), p4 becomes 1 (was empty).
Expected: Player 1 store +4, turn ends.

**Test 3: last seed on own pit.** Player 1 picks p0 with 3 seeds. p3 holds 3. The last seed lands on p3 and makes it 4.
Expected: Player 1 captures 4, turn ends.

**Test 4: last seed on opponent's pit.** Player 1 picks p5 with 1 seed. p6 holds 3. The seed lands on p6 and makes it 4.
Expected: Player 1 captures 4 into their own store (it was the last seed), Player 1's turn ends.

**Test 5: full lap.** Board: p3 holds 12 seeds and every other pit holds 1 seed (no pit holds 3). Player 1 picks p3.
Expected end state: p3 = 1 and every other pit = 2. Every pit, including the starting pit, received exactly 1 seed. No capture, turn ends (the last seed landed in the emptied starting pit).

**Test 6: skipped turn.** Player 2 has no seeds on p6 to p11 when it is their turn. Expected: the turn is skipped, the message and sound trigger, and Player 1 plays again.

**Test 7: game end.** When the 11th capture is made (capturedTotal reaches 44), the game ends immediately and the player with more captured seeds wins.

**Test 8: alternating starter.** Game 1 starts with Player 1 and is played to the end. Game 2 must start with Player 2, game 3 with Player 1. After closing and reopening the app, the next game still starts with the other player. Pressing Restart in the middle of a game keeps the same starter.

**Test 9: save and resume.** Play 5 turns, close the app, reopen it. The Continue button appears, and the board, both stores, whose turn it is and the starting player are exactly the same as before closing. When that game ends, the Continue button disappears.

**Test 10: endless relay.** Player 1 to play pit 1. Pits p0 to p11 hold `1, 3, 1, 0, 1, 0, 2, 1, 0, 1, 0, 2`. Player 1's store holds 20 and Player 2's store holds 16.
Expected: after 159 sowing steps the board repeats, the turn ends, no seed is lost (the pits then hold `1, 0, 2, 1, 2, 0, 2, 1, 0, 1, 0, 2`, the stores still hold 20 and 16) and it is Player 2's turn.

## 10. Saving the game and scores

The game must save automatically, so a player never loses progress.

**Mid-game save**
- Save after **every finished turn**, and also when the app goes to the background or is closed.
- Save the finished result of the turn (all 12 pit counts, both stores, whose turn is next, total captured so far, move number, game mode, computer difficulty, and who started this game). Never save the middle of a sowing animation. If the app is closed during an animation, the saved state is the final result of that turn.
- When the app opens and a saved game exists, the main menu shows a **Continue** button. Continue restores the exact position, including whose turn it is and any skipped-turn state.
- Starting a new game while a saved game exists asks for confirmation first, because it replaces the saved game.
- When a game ends, the saved mid-game state is deleted.
- A saved game must always match the rules: 48 seeds in total (pits plus stores plus the seeds left over once the game ends). If a saved file is damaged or does not add up, ignore it and start fresh without crashing.

**Scores**
- The seeds in both stores are part of the mid-game save.
- Also save the overall results across games: games won by each side, per game mode (against the computer is counted separately for each difficulty).
- Scores are kept after the app is closed and can be reset by the player in Settings, with a confirmation.
- The chosen seed speed, sound settings and each player's name and avatar are also saved.

## 11. Presentation: what the player sees

**Stores.** Each player has one store, built into the board at one end.
- Player 1 (bottom row) owns the **right** store.
- Player 2 (top row) owns the **left** store.
- Every capture is shown: the 4 captured seeds leave their pit and travel into the store that receives them (section 5). The pile in the store grows and its number goes up. At game end each player's store holds the seeds that player won.

**Seed movement (like a real game, one seed at a time).**
1. **Pick up (0.30 s):** the seeds lift out of the chosen pit. They are shown as a small group held just above the pit with a number showing how many are still in hand.
2. **Drop:** the seeds hop one at a time into the next pits. Each hop takes **0.40 s** at normal speed, and the pit number goes up the moment the seed lands. The sound is `sfx_seed_drop_a`, `_b` or `_c`, chosen at random.
3. **Relay:** when the last seed lands in a pit that already had seeds, wait 0.35 s, glow that pit softly, then pick up all its seeds (step 1) and continue.
4. **Capture:** when a pit becomes 4: emerald glow for 0.25 s, the 4 seeds fly together into the receiving store (0.60 s) with `sfx_capture`, the store number rises, and a small "+4" floats up. Sowing then continues, or the turn ends as the rules say.
5. **Skipped turn:** a banner such as "No seeds, turn skipped" for 1.5 s with `sfx_turn_skipped`.
6. The board ignores taps while seeds are moving. Selectable pits glow softly when it is a human player's turn.

**Seed speed setting (saved).** Slow = 0.65 s per seed, **Normal = 0.40 s (default)**, Fast = 0.20 s. All other durations above scale in proportion. A long turn of 25 seeds takes about 10 s at Normal and about 5 s at Fast.

**Full screen.** The game screen is full screen in landscape (see section 12).

**Numbers.** Every pit and both stores show a small count badge at all times (positions are in `board_geometry.json`). A pit shows at most 14 seed sprites and a store at most 30; the badge always shows the true number.

## 12. Screens and orientation

- The launch screen and menus are upright (portrait).
- Only the **game screen** is landscape (sensorLandscape), set when a game starts.
- The game screen must use the **whole physical screen**: edge-to-edge, with the status bar and navigation bar hidden (immersive mode) and the background drawn behind any camera cutout. The board and both stores must be as large as the screen allows, so every pit, store, number and button is clearly visible and easy to tap, with nothing hidden by a notch, camera hole or rounded corner. It must work on all common phone shapes (16:9 up to 21:9) and on tablets.
- Game modes:
  1. Play against the computer.
  2. Two players on the same device.
  3. Two phones over Bluetooth or Wi-Fi. This is a planned feature and must not be shown as available until it is built.

## 13. Open decisions (default shown, the owner can change)

1. **Undo and Hint buttons:** shown in the design previews but **not part of these rules**. Do not build them unless the owner asks.
2. **"Who starts" choice on the game setup screen:** the design previews show a choice of "You", "Computer" or "Random". This is **replaced** by the alternating rule in section 2. Do not build that choice.
3. **Overall scores:** the default is games won per mode and difficulty (section 10). The owner may ask for more statistics later.
