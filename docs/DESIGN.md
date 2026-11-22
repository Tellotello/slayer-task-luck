# Design

How Slayer Task Luck works, for contributors and reviewers. Everything is read-only: the plugin observes varps,
widgets and chat. It adds no menu entries, injects no input, makes no network calls, and doesn't use reflection.

## Layout

| Package | Contents |
| --- | --- |
| `com.slayertaskluck` | Plugin wiring, config, overlay mode |
| `model` | Saved data: `Roll`, `OddsSnapshot`, `SnapshotEntry`, `TrackerData`, `Confidence` |
| `tracking` | `SlayerTracker` (game events), `OddsParser`, `WeightInference`, `OddsModel`, `RollScorer`, `TrackerStore` (persistence), `Masters`, `CsvExport` |
| `math` | `LuckMath`, `Fairness`, `StatsCalculator`, `GroupStats`, `Verdict` |
| `ui` | Side panel, overlay, chart, luck bar, view model (`TrackerView`, `ViewBuilder`) |

Everything except `SlayerTracker`, the Swing classes and the plugin class is pure Java with unit tests.

## Detecting assignments

| Signal | Source |
| --- | --- |
| Task creature | `VarPlayerID.SLAYER_TARGET` (row id in `DBTableID.SlayerTask`, which also gives the name) |
| Assigned amount | `VarPlayerID.SLAYER_COUNT_ORIGINAL` |
| Remaining amount | `VarPlayerID.SLAYER_COUNT` (kills only lower this) |
| Konar location | `VarPlayerID.SLAYER_AREA` |
| Master role slot | `VarbitID.SLAYER_MASTER` (1 = Turael/Aya, 5 = Duradel/Kuradal) |
| Master name | NPC dialogue (`ChatMessageType.DIALOG`, `"Speaker\|text"`), else the last master clicked |

- A new assignment changes `SLAYER_TARGET` and/or `SLAYER_COUNT_ORIGINAL`, or raises `SLAYER_COUNT`, within one
  tick. `SlayerTracker` marks the tick and reads all values back on the next `GameTick`, when the master's dialogue
  line has also arrived.
- After a real login or world hop, the client loads these varps over about 2 ticks. The tracker waits 3 ticks and
  takes the loaded values as the baseline, never as an assignment. `LOGGED_IN` after an ordinary loading screen
  doesn't reset anything.
- Completing or cancelling a task (count goes to 0) is not an assignment.

## Odds

### Task list

`InterfaceID.SlayerRewardsTaskList.DRAWABLE` holds the rows as text pairs: `"Gargoyles (130-200)"` followed by
`"4.9%"`. `OddsParser` pairs them by pattern, not by child index. The list is read one tick after `WidgetLoaded`,
and it is credited to the last known master the player clicked or talked to within 100 ticks. If there's none, it's
ignored and the panel says why.

### Exact weights

The game shows `floor(1000 * w / W) / 10` percent for each task. `WeightInference` searches for the smallest total
`W < 1000` for which every row's shown value pins an integer weight and those weights add up to `W`. Below 1000 each
shown value allows at most one integer, so the answer is unique. The unit tests use a real captured Kuradal list,
which resolves to a total of 161 (154 while one of its tasks is held). If no exact fit exists, the shown per-mille values are used and the snapshot is
marked approximate.

### The task you hold

The list shows the task you currently hold as 0% and rescales the rest. Each snapshot records that held task, and when
a later roll needs its weight, `OddsModel` borrows it from another exact snapshot of the same master. At roll time,
the task held then, if still active, is removed from the roll.

### Blocklist

The rewards Tasks tab (`SlayerRewards.TASKS_CONTENT_SCROLLABLE`, rows `"Slot N:"` then a task name) is read when
the rewards interface opens. A change marks older snapshots as stale.

### Per-roll odds

`RollScorer` uses the latest snapshot of the master group taken before the roll. Confidence is:

- `NONE` (shown, not counted) when there's no snapshot, or the task received isn't on it.
- `STALE` (counted, flagged) when Slayer level, combat level or the blocklist changed after the snapshot, the held
  task's weight is unknown, or the weights are approximate.
- `CURRENT` otherwise.

Boss tasks arrive as the specific boss, so a task not on the list falls back to the list's "Boss" row when that row
is possible.

## Math (`math` package)

- **Dry streak:** the product of (1 − p_wanted) over the rolls since the last wanted task.
- **Lifetime:** expected = Σp, variance = Σp(1−p), and the exact Poisson-binomial distribution by O(n²) dynamic
  programming. The verdict uses the mid-p percentile, P(X < k) + ½P(X = k), against configurable tails.
- **Longest dry streak:** P(some run ≥ L) over the actual sequence of p values, by O(n·L) dynamic programming over
  the current run length.
- **Fairness** (from 50 counted rolls): per-task observed vs expected, and one global Monte Carlo test that replays
  every roll with its own distribution (up to 20,000 times, within a fixed work budget) and compares a chi-square
  statistic. Per-task rows are descriptive only.
- **Detours:** filler assignments since the last wanted roll, each timed until the next assignment (capped at 2 hours).

## Performance

Measured with `PerformanceBenchmark` (a test skipped by default; run with
`STL_PERF=1 ./gradlew test --tests '*PerformanceBenchmark' --rerun-tasks`, which writes `build/perf-report.txt`).
Typical results, where 1,000 rolls means 1,000 target rolls plus 1,000 filler skips:

| Path | Thread | Cost |
| --- | --- | --- |
| Overlay draw | client, every frame | ~0.06 ms (a frame at 50 fps is 20 ms) |
| Event handlers (varps, tick, chat, clicks) | client | O(1); a few comparisons and varp reads |
| Log a new task | client, per assignment | ~0.02 ms |
| Read a task list | client, when opened | ~0.05 ms |
| Data lock held for a copy | any | 0.2 ms at 1,000 rolls; 0.4 ms at 20,000 |
| View rebuild after a new roll (incl. fairness) | plugin executor | ~90 ms at 1,000 rolls; ~0.5 s at 20,000 |
| View rebuild after other changes (fairness cached) | plugin executor | ~0.6 ms at 1,000 rolls |
| Save | plugin executor, debounced | ~3 ms at 1,000 rolls (≈0.6 MB file) |
| Panel refresh | Swing | ~6 ms |

The fairness simulation only re-runs when the counted rolls change, and it's capped at 4 million simulated rolls per
run.

## Persistence and threading

- One JSON file per RuneScape profile, `<rsprofile>.json`, in `getPluginDirectory()`. The store creates the folder,
  writes atomically (temp file, then move), debounces saves by 1.5 s, and retries a failed save. An unreadable file is
  renamed aside, never overwritten. Gson comes from injection.
- Game events arrive on the client thread and mutate `TrackerStore` under its lock. Saves, stats and the Monte Carlo
  test run on one plugin-owned executor, which coalesces changes into a single view rebuild. The panel updates via
  `invokeLater`; the overlay reads a volatile, precomputed view and does no math per frame.
