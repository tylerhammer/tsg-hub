# TSG Hub

Clan events for **Type Shiii Gaming** (TSG), right in your RuneLite sidebar. Join a bingo team, climb the Skill of the Week leaderboard, find a raid party, and let the plugin track your progress while you play.

![TSG Hub sidebar: clan events, a live bingo board, and clan parties](docs/images/hero.png)

> TSG Hub only works for characters in the **TSGaming** clan. Anyone else sees a members-only message, and nothing is sent.

## Features

- **Bingo boards** with team tasks that update automatically from kills, drops, raids and collection log unlocks.
- **Skill of the Week** and **Boss of the Week** leaderboards you join with one click.
- **Custom** events (drop parties, clan trips, anything else) announced with the time, world and location.
- **Prizes**: events can list GP prizes for 1st, 2nd and 3rd place.
- **Clan parties** for raids, bossing and skilling. Join with one click, no passphrase to type, and see your party's health, prayer, gear, inventory and skills live.
- **Members** list showing which clanmates are online, their world, area and what they're doing.
- **Drops** history of the clan's big drops, raid loot, pets and collection log items, so you can catch up on what you missed.
- **Discord linking**: link your RuneScape account to the clan Discord, and your Discord roles follow your in-game clan rank.
- **Admin tools** for clan admins: create events, teams and tasks, and review proof, all without leaving the game.

## Bingo

Your team's board opens straight from the sidebar. The header shows your team's rank, points and tasks done, plus the prize for each place. Every task shows its points, a progress bar, and who contributed what. Open tasks sort first; tick **Hide completed** to focus on what's left.

**Scores** ranks every team, and **Team** lists your teammates. The board updates live while it's open.

When you make progress, a message appears in **your own chatbox only**. When your team finishes a tile, teammates who are online in clan chat get a local alert too. TSG Hub never posts to public or clan chat.

<p>
<img src="docs/images/board-tasks.png" width="320" alt="A team's bingo board with progress on each task">
<img src="docs/images/board-scores.png" width="320" alt="Team scoreboard">
</p>

### What gets tracked

| Task type | How it's tracked |
|---|---|
| **Boss kills** | The in-game kill count message, or loot drops for bosses without one |
| **Item drops** | Any listed item from NPC loot, matched by RuneLite item ID |
| **Item sets** | Every piece of a set, such as Barrows or Bandos. Teammates can each find different pieces |
| **Any jar / Any boss pet** | Built-in groups, so admins don't have to list every item |
| **Raids** | Chambers of Xeric, Theatre of Blood and Tombs of Amascut completions, per mode. Optionally clan-only |
| **Collection log** | New collection log unlocks count toward set tasks |
| **Manual** | Submit a screenshot link or note; an admin reviews it |

Tasks can be **Team** (everyone's progress pools together), **Everyone** (each member reaches the target), or **Solo** (one member does it alone).

## Competitions and custom events

**Skill of the Week** tracks the XP you gain in one skill. **Boss of the Week** counts kills of one boss. Click the event, press join, and play: your gain and rank update as you go.

**Custom events**, like drop parties, show when and where they happen, along with the host and any notes.

The **Events** list splits live and upcoming events, with each one's prize pool. Open an event to see the prize for each place.

Every event shows its start and end in your own time zone, with the zone name (for example "Mon 5 Oct, 7pm AEDT") and how long until it starts or ends. Competitions count gains made between those exact times, wherever you are.

<p>
<img src="docs/images/competition.png" width="320" alt="Skill of the Week leaderboard">
</p>

## Clan parties

The **Parties** section lists every open party in the clan, with its title, world and members. Click one to join, or start your own. A new party is titled by where its members are, like `Theatre of Blood` or `Wilderness lvl 40-42`, until a title is set. The leader can use **Lock party** to stop anyone else joining.

There's no passphrase to share. Once you're in, each member's panel shows their health, prayer, special attack, run energy, gear, inventory, skills and active prayers, live over RuneLite's party connection. Right-click a member to move them up or down your list.

A party closes when its last member leaves. Members drop out after 3 minutes without checking in, or after 30 minutes at the login screen.

<p>
<img src="docs/images/parties.png" width="320" alt="Clan party list">
</p>

## Members

The **Members** section lists clanmates who are in clan chat with sharing on. Each row shows their world, area and what they're doing, such as `Mining · Motherlode Mine`, `Vorkath` or `Chambers of Xeric`. Clanmates on your world have their world shown in green.

Activity is detected automatically from where you are and the XP you gain. Only the area name is sent, never your exact tile.

Anyone in clan chat with sharing on appears in the list. Your area and activity are only shown if you turn on **Share location and activity** under **Sharing** in the plugin settings; otherwise clanmates just see **Online** and your world, the same as clan chat shows. Leave clan chat to hide yourself, and you also drop off the list when you log out.

Online clanmates are listed first, including those in clan chat without TSG Hub, who show just their world. The rest of the clan is listed under **Offline**; tick **Show offline** to expand it. Offline members who use TSG Hub show how long ago they were last online.

Admins with a hub key can right-click any member to edit their admin note. On Gnome Child characters it also sets whose alt they are; type part of a name to search the clan for their main. Alt links, notes and last seen times follow TSG Hub users through name changes. Hover a member's rank to see whose alt they are, or a main's alts. Admin notes show as a note icon with the note on hover, and only admins see them.

<p>
<img src="docs/images/members.png" width="320" alt="Online clan members with their world, area and activity">
</p>

## Drops

The **Drops** section keeps a history of the clan's big drops, so you can see what clanmates got while you were offline. Drops are grouped by day, and each row shows the item, who got it, its value and how long ago. Tags mark raid loot (`CoX`, `ToB`, `ToA`), pets and new collection log items.

It's built from the clan chat broadcasts your clan already has turned on: drops over the clan's value threshold, raid loot, pets and new collection log items. Anyone online with sharing on records them, so drops from clanmates who don't use TSG Hub show up too. If several people see the same broadcast, it's only recorded once.

<p>
<img src="docs/images/drops.png" width="320" alt="Recent clan drops with item, player, value and time">
</p>

## Getting started

1. Install **TSG Hub** from the Plugin Hub.
2. Log in to a character in the TSGaming clan and open the TSG Hub sidebar.
3. Click **Enable sharing**. Nothing is sent until you do.
4. Pick an event. For bingo, enter the team code an admin gave you. Your team is fixed once you join.

<p>
<img src="docs/images/home.png" width="320" alt="TSG Hub home with Events, Parties, Members and Drops tiles">
</p>

**Disconnect from event** on the **Team** tab stops tracking on this device. Your team keeps its progress, and you can rejoin with the same code.

## Link your Discord

Every clan member can link their RuneScape account to the clan Discord:

1. Run `/hub key` in the clan Discord. The bot replies with a key that starts with `tsghub_`.
2. Paste it into **Hub key** under **Discord** in the TSG Hub plugin settings, with sharing on.

The plugin confirms the key in the sidebar and links your Discord account to the character you're logged in as. Using the same key on your alts links them too, and your Discord role follows your highest-ranked character. A name change carries over automatically next time you log in. The key renews itself while you use it. If it stops working, or you lose it, run `/hub key` again for a new one; `/hub revoke` disables it and unlinks your account.

### Discord rank sync

Once you're linked, your Discord rank role follows your in-game clan rank. When the Owner or a Deputy Owner changes your rank in game, their plugin tells the TSG Hub service, and the bot swaps your Discord role within a few seconds. If your rank changed before you linked, you get the right role as soon as you link.

Only rank changes reported by the Owner or a Deputy Owner who is also a Discord admin are applied. Reports from anyone else are ignored.

## For admins

![Admin window: events on the left, the selected event's tasks on the right](docs/images/admin-tasks.png)

Admin tools use the same hub key. If you're a Discord admin (the server owner, a member with an admin role, or anyone with Manage Server), `/hub key` gives you a key with admin access. Keys from before hub keys existed, starting with `tsgadm_`, keep working and move into the **Hub key** setting by themselves.

Once the key checks out, an admin button appears in the sidebar header. It opens a separate window with your clan's events on the left and the selected event on the right. Admins also see and can join hidden events. If you lose your Discord admin role, your key keeps linking your account but the admin tools disappear.

Your in-game clan rank doesn't grant admin access. Owners and Deputy Owners keep admin tools through their Discord admin role, and their plugin keeps Discord rank roles in sync while they're logged in with sharing on.

1. **New event**: pick bingo, Skill of the Week, Boss of the Week or a custom event, then set its name and its start and end date and time. Times are entered in your computer's time zone, shown next to the fields, and players see them converted to theirs. Custom events can leave the end time empty. You can add optional prizes for 1st, 2nd and 3rd place, in millions of GP, and hide scores from players until the end.
2. **Teams**: add teams. Each gets a permanent invite code; use **Copy code** to share it.
3. **Tasks**: add tasks shared by every team. Item tasks have type-ahead search with icons and ready-made sets, and raid tasks let you choose each raid and mode.
4. **Claims**: approve or reject manual submissions. The tab shows how many are waiting.

![Pending manual claims awaiting review](docs/images/admin-claims.png)

You can edit a task after the event starts. Progress the service already recorded is recalculated against the new requirements. To credit something the plugin couldn't see, open a team's **Details** and use **Mark complete** with a short note.

## Privacy

TSG Hub is **opt-in**. Until you click **Enable sharing** (or turn on **Share game and clan progress** in the plugin settings), it sends nothing.

Once you opt in, it sends the following to the TSG Hub service:

- Your RuneScape display name, and the clan name and rank your client detects
- If you've set a hub key, the key with your display name and RuneLite account hash when the plugin checks it, to link your Discord account to this character and follow name changes
- If you're the Owner or a Deputy Owner with an admin hub key, every clan member's name, rank number and rank title from your clan settings, once after you log in and again whenever a rank changes, so Discord roles follow in-game ranks
- Progress for events you've joined: kill counts, drops, raid completions, collection log unlocks and skill XP
- Loot you receive while a bingo event you've joined is active, so admins can reconcile a task from earlier drops if its item list changes
- Manual proof you submit
- Whether you're currently in the clan chat channel and your world, so teammates' completion alerts reach you and clanmates see you as online
- With **Share location and activity** on, your area name and current activity (never your exact tile)
- Clan chat broadcasts for drops, raid loot, pets and collection log items, for the clan's **Drops** history

In a clan party, your stats, gear and inventory go to the other members through **RuneLite's party service**, not the TSG Hub service. The TSG Hub service only learns your display name, party and world, plus your area name if location sharing is on.

Turning sharing off, or disconnecting from an event, deletes your local token and asks the service to revoke it. Progress your team already earned stays with the clan.

Clan membership, rank and progress are reported by each player's client. They're useful for convenience checks, but a modified client can fake them, so admins should double-check high-stakes results. Admin access comes only from Discord-issued hub keys whose holder is a Discord admin, never from the reported rank. Rank changes only update Discord roles when they come from an Owner or Deputy Owner who is also a Discord admin.

## Credits

The live party member panels are adapted from [Party Panel](https://github.com/TheStonedTurtle/party-panel) by TheStonedTurtle, under the BSD 2-Clause license. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

TSG Hub is licensed under the [BSD 2-Clause License](LICENSE).
