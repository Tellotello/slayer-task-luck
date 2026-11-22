# Slayer Task Luck

How unlucky have you really been at getting the Slayer tasks you want? Slayer Task Luck logs every task you're
assigned, attaches the exact odds of each roll, and shows whether your luck is normal, unlucky or lucky.

It's made for anyone hunting specific tasks: Turael/Aya skipping toward a task at Duradel/Kuradal, Nieve/Steve, Konar
or any other master, or just wondering whether the game's listed odds hold up.

The plugin is **read-only**. It never clicks, types or changes anything in the game, and it makes no network calls. Your
data stays on your computer.

## Getting started

1. Talk to the master you're rolling at, open **Rewards**, then open the **task list**. The plugin saves that master's
   odds. Open it again after you block or unblock a task, or after a level-up if you get a reminder.
2. Open the **Slayer Task Luck** side panel and tick the tasks you're hunting under **Wanted tasks**. Your chance per
   roll updates as you tick.
3. Slayer as usual. Every assignment is logged automatically, including skips with a filler master (Turael, Aya, Spria).

Pick any master from the **Master** list at the top of the panel; the ones you've used come first.

## What you see

### Overlay

Shown while you're skipping (no task, a filler task, or talking to a master) by default:

```
Kuradal                     unlucky
Dry streak             20 - 1 in 16
Wanted          3 / 9.1 - 1 in 33
```

### Side panel

- **Headline:** a verdict (*unlucky*, *normal* or *lucky*), your current dry streak, wanted tasks received vs
  expected, and the filler detours since your last wanted task, with the time they took.
- **Luck bar:** lifetime luck from unluckiest to luckiest. The shaded ends are the rarest results on each side.
- **Luck over time:** wanted tasks minus expected after each roll, against the normal range. A line below the band
  means sustained bad luck.
- **History:** every task, newest first, with its listed chance. You can exclude a roll from the stats, and undo that.
- **Fairness by task:** after 50 rolls at a master, a check of whether you really get each task as often as the listed
  odds say.
- **Wanted tasks:** the tasks that master can give you, with their chances and your total chance per roll. The task
  you held when you opened the list is included too, shown as **?** until you open the list again without a task.
- **Data:** export everything as CSV, or reset.

## How the numbers work

- **Exact odds.** The task list shows each chance rounded *down* to 0.1%, which is why it adds up to about 98-99%. The
  plugin recovers the whole-number weights behind it, so it works with exact probabilities. For example, one Kuradal
  list resolves to weights totalling 161; the total depends on your blocks and unlocks.
- **Odds at the moment you roll.** Each roll uses the latest list you opened before it. A task you still hold can't
  be rolled, so it's left out of that roll.
- **"1 in N".** If the listed odds are right, a result at least this extreme happens about once in N tries. Only the
  rarest results (10% on each side by default) are coloured.
- **Wanted vs expected** uses the exact distribution of your own rolls (Poisson-binomial). It reports a mid-p
  percentile, so an exactly average result sits in the middle.
- **Longest dry streak** tells you whether your worst streak is unusual for how many rolls you've made. Any long hunt
  has a scary-looking current streak at some point.
- **Wanted list changes** apply to new rolls. Past rolls keep the wanted list they were rolled under, unless you choose
  **Apply to past rolls**.
- **The fairness check** replays your own roll history thousands of times with the listed odds and compares the spread
  of tasks. It doesn't simulate any game content.

## Settings

| Setting | Default |
| --- | --- |
| Show overlay | While skipping (or Always, Never) |
| Show wanted line / detours line | On / Off |
| Aya, Turael, Spria are fillers | On (filler tasks are skips: logged and counted as detours, never as luck rolls) |
| Unlucky below / lucky above | 10% / 10% |

Data is saved per RuneScape profile (account and world type) in RuneLite's `plugin-data/slayer-task-luck` folder.

## Development

Requirements: JDK 11. Gradle comes with the wrapper.

```
./gradlew build   # compile and run the unit tests
./gradlew run     # start RuneLite with this plugin (--developer-mode --debug)
```

To log in with a Jagex account in the development client, see
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

How it works internally, including detection, odds recovery and the math: [docs/DESIGN.md](docs/DESIGN.md).

## License

BSD 2-Clause. See [LICENSE](LICENSE).
