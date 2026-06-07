These are methods you can use from game module code:

## CONTEXT

**GameContext**
```java
Plugin getPlugin() // Returns base Donutgame plugin
Logger getLogger() // Returns base Donutgame plugin’s logger
GameMap createMap(String mapPath) // Creates GameMap object based on YAML filepath
CompletableFuture<GameMap> initializeMap(String mapPath) // Loads world from GameMap YAML
TeamManager teamManager() // Returns context TeamManager
TimeManager timeManager() // Returns context TimeManager

// TO BE IMPLEMENTED
PlayerManager playerManager() // Returns context PlayerManager
BorderManager borderManager() // Returns context BorderManager
```

## MAPS
**GameMap**
```java
CompletableFuture<GameMap> loadWorld() // Loads world specified in map yaml
void unloadWorld() // Unloads map world
CompletableFuture<GameMap> resetWorld() // Unloads map world and loads a new one immediately
List<Location> getPoints(String pointType) // Returns list of points of specified point name
World getWorld() // Returns active Bukkit world
String getInstanceWorldName() // Return world name of specific loaded world instance
String getTemplateWorldName() // Returns name of source world that the instance copied from
boolean isLoaded() // Returns whether or not the map is fully loaded
```

## TEAMS
**TeamManager**
```java
GameTeam createTeam(String id, TeamProperties) // Creates a team with specified properties
GameTeam createTeam(String id) // Creates a team with default properties
GameTeam getTeam(String id) // Returns team with specified id
GameTeam getPlayerTeam(Player player) // Returns team which specified player belongs to
boolean playerHasTeam(Player player) // Returns whether or not the specified player is in a team
Collection<GameTeam> getTeams() // Returns collection of current team owned by the current team manager
boolean join(Player player, String teamId) // Joins specified player to specified team
boolean leave(Player player) // Removes specified player from any team
void clear() // Removes all teams owned by the current team manager
```
**GameTeam**
```java
String getId() // Returns team id
TeamProperties getProperties() // Returns team properties
void setProperties(TeamProperties properties) // Sets team properties to specified properties
boolean hasPlayer(Player player) // Returns whether or not the team contains specified player
int getSize() // Returns number of members on the team
void addPlayer(Player player) // Adds specified player to team
void removePlayer(Player player) // Removes specified player to team
Team getBukkitTeam() // Returns the Bukkit/vanilla team
void unregister() // Unregisters team from Bukkit scoreboard teams service
```

## TIME MANAGEMENT
**TimeManager**
```java
GameTimer createTimer(int time) // Creates timer that continously counts up to a time limit starting from specified time in ticks
GameTimer createTimer() // Creates timer that continously counts up infinitely starting from 0
```
**GameTimer**
```java
GameTimer start() // Starts the timer; used for initially starting after creating timer
boolean isStarted() // Returns whether or not the timer is started
int getElapsedTicks() // Returns number of ticks since timer started
int getElapsedSeconds() // Returns number of seconds since timer started, rounded to nearest integer
int getRemainingTicks() // Returns number of ticks remaining in timer, returns -1 if timer is counting upwards indefinitely
int getRemainingSeconds() // Returns number of seconds remaining in timer, rounded to nearest integer, returns -1 if timer is counting upwards indefinitely
boolean isFinished() // Returns whether or not the timer is finished
GameTimer toggleRunning() // Toggles running timer to paused, paused timer to running
GameTimer setRunning(boolean running) // Pauses timer if running is false, and resumes if running is true
GameTimer pause() // Pauses timer, equivalent to setRunning(false)
GameTimer resume() // Resumes timer, equivalent to setRunning(true)
boolean isRunning() // Returns false if timer is paused and true if running
GameTimer cancel() // Cancels timer early
GameTimer whileRunning(Consumer<GameTimer> action) // Runs specified action every 1 tick while the timer is running
GameTimer whileRunning(int interval, Consumer<GameTimer> action) // Runs specified action every interval ticks while the timer is running
GameTimer onEnd(Consumer<GameTimer> action) // Runs specified action when timer finishes
GameTimer onCancel(Consumer<GameTimer> action) // Runs specified action when timer cancels
GameTimer onToggleRunning(Consumer<GameTimer> action) // Runs specified action when timer changes from paused to resumed or vice versa
GameTimer onToggleRunning(boolean running, Consumer<GameTimer> action) // Runs specified action whenever running is toggled to true or false depending on what is set
GameTimer whilePaused(Consumer<GameTimer> action) // Runs specified action every 1 tick while the timer is paused
GameTimer whilePaused(int interval, Consumer<GameTimer> action) // Runs specified action every interval ticks while the timer is paused
```

## PLAYER MANAGEMENT
**PlayerManager**
```java
Collection<Player> getPlayers() // Returns all players in the game
void setSpectator(Player player) // Sets specified player to a spectator (not spectator mode, the plugins implementation of spectators)
void setNonSpectator(Player player) // Makes specified player a non spectator 
Collection<Player> getSpectators() // Returns all spectators
Collection<Player> getNonSpectators() // Returns all players in game who are not spectators
void setPlayerSpawn(Player player, Location location) // Sets spawnpoint/respawn location of specified player
Location getPlayerSpawn(Player player) // Returns player spawn location
void respawnPlayer(Player player) // Respawns specified player instantaneously
void respawnPlayer(Player player, int time) // Respawns specified player after given time duration in ticks
void cancelRespawn(Player player) // Cancels active respawn timers on the player
```

Everything below is yet to be implemented:

## USER INTERFACE
**UIManager**
```java
void title(Audience audience, Component title) // Sends a title to specified audience
void subtitle(Audience audience, Component subtitle) // Sends subtitle to audience, regardless of currently shown title
void actionbar(Audience audience, Component actionbar) // Sends actionbar to audience
void chat(Audience audience, Component message) // Sends chat message to specified audience
GameSidebar createSidebar() // Creates a sidebar but does not display it yet
GameBossbar createBossbar() // Creates a bossbar but does not display it yet
void clearUi(Audience audience) // Clears all UI elements for a specified audience
```
**GameSidebar**
```java
Audience audience() // Returns audience that can see the sidebar
GameSidebar setAudience(Audience audience) // Sets audience of sidebar
GameSidebar setVisibility(boolean visible) // Shows sidebar to be visible or not
boolean isVisible() // Returns whether or not the sidebar is visible
ArrayList<ValueDisplay> lines() // Returns list of lines
void remove() // Deletes and hides the sidebar
```
**GameBossbar**
```java
Audience audience() // Returns audience that can see the bossbar
GameBossbar setAudience(Audience audience) // Sets the audience of bossbar
GameBossbar setVisibility(boolean visible) // Shows bossbar to be visible or not
boolean isVisible() // Returns whether or not the bossbar is visible
GameBossbar setTitle(Component name) // Sets title of bossbar
Component getTitle() // Returns bossbar title
GameBossbar setValue(int value) // Sets value of bossbar
int getValue() // Returns bossbar value
GameBossbar setMax(int value) // Sets max of bossbar
int getMax() // Returns bossbar max
GameBossbar setStyle(BossBar.Overlay style) // Sets style of bossbar
BossBar.Overlay getStyle() // Returns style of bossbar
GameBossbar setColor(BossBar.Color color) // Sets color of bossbar
BossBar.Color getColor() // Returns color of bossbar
```
**ValueDisplay**
```java
enum ValueType {NONE, INTEGER, DECIMAL, TIME, FRACTION, PERCENT, COMPONENT} // The types of values that can be displayed by a ValueDisplay
ValueType getType() // Returns type of value display
Component getLabel() // Returns label of value display
ValueDisplay getValue() // Returns value of value display
ValueDisplay none(String label) // Line that does not display a value
ValueDisplay integer(String label, ToIntFunction<Player> number) // Line that displays single integer
ValueDisplay decimal(String label, ToDoubleFunction<Player> number) // Line that displays single double
ValueDisplay time(String label, ToIntFunction<Player> time) // Line that displays a time (mm:ss)
ValueDisplay fraction(String label, ToIntFunction<Player> numerator, ToIntFunction<Player> denominator) // Line that displays single fraction (n/d)
ValueDisplay percent(String label, ToDoubleFunction<Player> percent) // Line that displays single percent (0.xx -> xx%)
ValueDisplay component(String label, Function<Player, Component> Component) // Line that displays single component
```

## BORDERS
**BorderManager**
```java
enum BorderShape {CUBOID, CYLINDROID, ELLIPSOID} // The available 3D geometric border shapes
GameBorder createBorder(BorderShape shape, Location center, Vector dimensions) // Creates cuboid border with specified center and dimensions
```
**GameBorder**
```java
Location getCenter() // Returns center location of border
Vector getDimensions() // Returns current dimensions of border
BorderShape getShape() // Returns 3D shape of border
void setDimensions(Vector dimensions) // Changes dimensions of border instantaneously
void setDimensions(Vector dimensions, int time) // Changes dimensions of border over specified time in ticks
void setCenter(Location center) // Moves center of border instantaneously
void setCenter(Location center, int time) // Moves center of border over specified time in ticks
void setDamage(double amount, int interval) // Sets how much damage to deal players outside the border every specified interval in ticks
```