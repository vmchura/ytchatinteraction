---
stepsCompleted:
  - step-01-init
  - step-02-discovery
  - step-03-success
  - step-04-journeys
  - step-07-project-type
  - step-08-scoping
  - step-09-functional
  - step-11-polish
  - step-12-complete
inputDocuments: []
workflowType: 'prd'
---

# Product Requirements Document - Tournament Prediction Game

**Author:** Vicc
**Date:** 2026-04-06

## 1. Executive Summary

A prediction game feature for registered users of the YouTube Chat Interaction platform to engage with external StarCraft tournaments by betting on group stage rankings and match outcomes. Admin configures players, multipliers, and results. Users place bets with a fixed budget per matchup. A cumulative leaderboard drives competition.

## 2. Problem Statement

Registered users watch external tournaments (not hosted on this platform) but have no interactive way to engage with the competition. Adding a prediction system increases user retention, session time, and community interaction during live tournament broadcasts.

## 3. Goals & Success Metrics

| Goal | Metric |
|------|--------|
| Increase user engagement during tournaments | % of registered users placing at least one bet |
| Drive repeat visits across tournament rounds | % of bettors returning for subsequent rounds |
| Create social competition | Leaderboard view count |
| Keep implementation simple and maintainable | Feature delivered in single sprint |

## 4. Scope

### In Scope
- Admin CRUD for prediction events, matchups (groups + matches), multipliers, and results
- User betting interface for group stage (player-position bets) and match stage (exact score bets)
- Fixed 100-point budget per matchup, cumulative score leaderboard
- Admin-set multipliers per betting option
- Automatic scoring when admin enters results
- Leaderboard page

### Out of Scope
- Real-time notifications or push alerts
- User-to-user messaging or chat about predictions
- Historical prediction analytics beyond the leaderboard
- Integration with external tournament APIs
- Mobile native app (web responsive only)

## 5. User Personas

### 5.1 Admin (Vicc)
- Sets up prediction events and matchups
- Configures player names, races, and multipliers
- Locks matchups before rounds start
- Enters results and triggers scoring
- Has existing admin privileges on the platform

### 5.2 Registered User (Bettor)
- Any authenticated user on the platform
- Views current matchups and multipliers
- Places bets within 100-point budget per matchup
- Views leaderboard and personal score
- Can place multiple bets per matchup (sum <= 100)

## 6. User Journeys

### 6.1 Admin: Create Prediction Event
1. Navigate to admin prediction page
2. Create new prediction event (name)
3. Add Round 1 matchups: 4 groups with 4 player names + races each
4. Set multipliers for each player-position combination (4x4 grid per group)
5. Lock Round 1 matchups when ready
6. After group stage completes, enter results (final position order per group)
7. System scores all bets automatically
8. Add Round 2 matchups: 4 quarterfinal matches with player names + races
9. Set multipliers for each score option (4-0 through 0-4)
10. Repeat lock → result → score cycle for rounds 3 and 4

### 6.2 User: Place Bets
1. Navigate to prediction page
2. See event name, current round tabs, leaderboard sidebar
3. Select a group/match card
4. For group stage: pick a player, select position, enter points to bet (see multiplier)
5. Place multiple bets until budget (100) is used or satisfied
6. Submit bets
7. Repeat for other groups/matches in the round
8. After admin scores, refresh page to see results (green = won, grey = missed) and score update

### 6.3 User: View Leaderboard
1. Navigate to prediction page
2. See leaderboard on sidebar (or full page view on mobile)
3. See own rank highlighted, top players, and score

## 7. Functional Requirements

### 7.1 Prediction Events (Admin)
| ID | Requirement | Priority |
|----|-------------|----------|
| FE-01 | Admin can create a prediction event with a name | Must |
| FE-02 | Admin can view list of prediction events | Must |
| FE-03 | Admin can edit event name and status | Should |

### 7.2 Prediction Matchups (Admin)
| ID | Requirement | Priority |
|----|-------------|----------|
| FM-01 | Admin can create group stage matchups (4 players with names and races) | Must |
| FM-02 | Admin can create match matchups (2 players with names and races) | Must |
| FM-03 | Admin can set round number and match label for each matchup | Must |
| FM-04 | Admin can set multipliers per bet option (group: player-position grid, match: score options) | Must |
| FM-05 | Admin can lock a matchup to prevent further betting | Must |
| FM-06 | Admin can enter results for a scored matchup (positions for groups, score for matches) | Must |
| FM-07 | Scoring triggers automatically when results are entered | Must |
| FM-08 | Admin can edit matchup details (players, multipliers) while status is Open | Should |

### 7.3 Betting (User)
| ID | Requirement | Priority |
|----|-------------|----------|
| FB-01 | User can view all open matchups for current round with multipliers displayed | Must |
| FB-02 | User can place a bet: select option + specify points (1-100) | Must |
| FB-03 | User can place multiple bets per matchup, total sum <= 100 points | Must |
| FB-04 | User can edit or delete bets while matchup is Open | Must |
| FB-05 | User cannot bet on Locked or Scored matchups | Must |
| FB-06 | User sees potential winnings (points × multiplier) in real-time on the bet card | Should |

### 7.4 Scoring & Leaderboard
| ID | Requirement | Priority |
|----|-------------|----------|
| FS-01 | Correct bets earn: points_bet × multiplier added to user score | Must |
| FS-02 | Incorrect bets earn 0 (no penalty) | Must |
| FS-03 | Leaderboard shows all users ranked by total cumulative score | Must |
| FS-04 | Leaderboard highlights current user's rank and score | Must |
| FS-05 | Score is cumulative across all matchups in the event | Must |

### 7.5 Results Display
| ID | Requirement | Priority |
|----|-------------|----------|
| FR-01 | Scored matchups show results: which bets won (green) and lost (grey) | Must |
| FR-02 | User sees their score delta per matchup after scoring | Should |
| FR-03 | User sees final player positions (groups) or match score (matches) | Must |

## 8. Data Model

### 8.1 prediction_events
| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL PK | Auto-increment ID |
| name | VARCHAR NOT NULL | Event display name |
| status | VARCHAR NOT NULL DEFAULT 'Open' | Open/Locked/Completed |
| created_at | TIMESTAMP NOT NULL DEFAULT NOW() | Creation timestamp |

### 8.2 prediction_matchups
| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL PK | Auto-increment ID |
| event_id | BIGINT FK → prediction_events | Parent event |
| round | INT NOT NULL | 1=groups, 2=quarters, 3=semis, 4=final |
| match_label | VARCHAR | "Group A", "QF1", "SF1", "Final" |
| bet_type | VARCHAR NOT NULL | 'GroupPosition' or 'MatchScore' |
| player1_name | VARCHAR | Player 1 name |
| player1_race | VARCHAR | Player 1 race (Zerg/Terran/Protoss) |
| player2_name | VARCHAR | Player 2 name (null for groups if not needed beyond player1-4) |
| player2_race | VARCHAR | Player 2 race |
| player3_name | VARCHAR | Player 3 name (group stage only) |
| player3_race | VARCHAR | Player 3 race |
| player4_name | VARCHAR | Player 4 name (group stage only) |
| player4_race | VARCHAR | Player 4 race |
| multipliers | JSONB NOT NULL | Admin-set multipliers per option |
| result | JSONB | Results after scoring (null until scored) |
| status | VARCHAR NOT NULL DEFAULT 'Open' | Open/Locked/Scored |
| result_scored_at | TIMESTAMP | When results were entered |

### 8.3 prediction_bets
| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL PK | Auto-increment ID |
| user_id | BIGINT FK → users | Better |
| matchup_id | BIGINT FK → prediction_matchups | Matchup bet on |
| selection | VARCHAR NOT NULL | 'player1-position1', '4-2', etc. |
| points_bet | INT NOT NULL CHECK > 0 | Points wagered |
| created_at | TIMESTAMP NOT NULL DEFAULT NOW() | When bet was placed |
| UNIQUE(user_id, matchup_id, selection) | | One bet per option per user per matchup |

### 8.4 prediction_user_scores
| Column | Type | Description |
|--------|------|-------------|
| user_id | BIGINT FK → users | User |
| event_id | BIGINT FK → prediction_events | Event |
| total_score | INT NOT NULL DEFAULT 0 | Cumulative winnings |
| PK(user_id, event_id) | | One score per user per event |

## 9. Multipliers Format (JSONB)

### Group Stage Example
```json
{
  "player1-position1": 2.0,
  "player1-position2": 3.0,
  "player1-position3": 5.0,
  "player1-position4": 8.0,
  "player2-position1": 3.5,
  "player2-position2": 2.0,
  "player2-position3": 4.0,
  "player2-position4": 6.0,
  "player3-position1": 4.0,
  "player3-position2": 3.0,
  "player3-position3": 2.0,
  "player3-position4": 4.0,
  "player4-position1": 6.0,
  "player4-position2": 5.0,
  "player4-position3": 3.5,
  "player4-position4": 2.0
}
```

### Match Stage Example
```json
{
  "4-0": 6.0,
  "4-1": 4.0,
  "4-2": 3.0,
  "4-3": 4.5,
  "3-4": 3.0,
  "2-4": 2.5,
  "1-4": 8.0,
  "0-4": 10.0
}
```

## 10. Scoring Logic

When admin enters results for a matchup:
1. Load matchup with result JSONB and multipliers JSONB
2. Load all `prediction_bets` for that matchup
3. For each bet:
   - Parse `selection` string
   - Compare against `result` JSONB
   - If match: `winnings = points_bet × multipliers[selection]`
   - If no match: `winnings = 0`
4. For each winning bet: UPSERT into `prediction_user_scores` (add winnings to total_score)
5. Update matchup `status` → 'Scored', set `result_scored_at`
6. Run entire operation in a transaction

### Result Format (JSONB)

**Group stage result:**
```json
{
  "positions": {
    "1": "player3",
    "2": "player1",
    "3": "player4",
    "4": "player2"
  }
}
```

Bet selection `"player3-position1"` matches if `result.positions["1"] == "player3"`.

**Match stage result:**
```json
{
  "score": "4-2"
}
```

Bet selection `"4-2"` matches if `result.score == "4-2"`.

## 11. Bet Validation Rules

| Rule | Description |
|------|-------------|
| Budget cap | SUM(points_bet) per user per matchup <= 100 |
| Min bet | 1 point minimum per bet |
| Max bet | 100 points maximum per bet |
| Matchup status | Bets only allowed on 'Open' matchups |
| Duplicate prevention | One bet per (user, matchup, selection) combination |
| Valid selection | Selection must exist in matchup's multipliers JSONB keys |

## 12. Tournament Round Structure

| Round | Format | Matchups | Bet Type | Options per matchup |
|-------|--------|----------|----------|-------------------|
| 1 | Groups | 4 groups × 4 players | GroupPosition | 16 (4 players × 4 positions) |
| 2 | Quarterfinals | 4 matches × 1v1 Bo7 | MatchScore | 8 (4-0 through 0-4) |
| 3 | Semifinals | 2 matches × 1v1 Bo7 | MatchScore | 8 |
| 4 | Final | 1 match × 1v1 Bo7 | MatchScore | 8 |

**Total: 11 bettable matchups across the tournament.**

## 13. UI Design

### 13.1 User Betting Page
- **Layout:** Two-column (bet cards left, leaderboard right). Stacks vertically on mobile.
- **Round tabs:** R1 | R2 | R3 | R4. Locked rounds greyed out.
- **Group card:** 4 player rows with position dropdown + bet input + multiplier display. Budget bar at bottom.
- **Match card:** Player vs header. 8 score buttons with bet inputs. Budget bar at bottom.
- **Leaderboard:** Ranked list with user highlighted. Always visible.

### 13.2 Admin Configuration Page
- **Event management:** Create/edit events, view status
- **Matchup editor:** Add players (name + race dropdown), set round and label
- **Multiplier grid:** 4×4 table for groups, 8-cell row for matches. Editable number inputs.
- **Lock button:** Locks betting on a matchup
- **Result entry:** Dropdown selectors for positions/scores
- **Score trigger:** Button to run scoring (or auto-trigger on result save)

### 13.3 Results Display
- Scored cards show green (won) / grey (lost) on each bet
- Score delta shown per matchup ("+240 this round")
- Final positions/scores displayed on the card

## 14. Technical Architecture

Follows existing project patterns (Slick, Play Framework, ScalaJS, PostgreSQL):

| Layer | Files |
|-------|-------|
| Evolution | `server/conf/evolutions/default/23.sql` |
| Models | `server/app/models/Prediction*.scala` |
| Components | `server/app/models/component/Prediction*Component.scala` |
| Repositories | `server/app/models/repository/Prediction*Repository.scala` |
| Services | `server/app/services/PredictionService.scala` |
| Controllers | `server/app/controllers/PredictionController.scala` |
| Views | Twirl templates for admin + user pages |
| Client | ScalaJS components for interactive bet cards |

## 15. Non-Functional Requirements

| Requirement | Detail |
|-------------|--------|
| Performance | Leaderboard query should be fast (index on total_score) |
| Concurrency | Multiple users betting simultaneously must not exceed budget validation |
| Security | Admin endpoints require admin role. Users can only modify their own bets. |
| Data integrity | Scoring runs in a transaction. Bet validation is atomic. |
