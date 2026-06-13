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
PlayerManager playerManager() // Returns context PlayerManager
UIManager uiManager() // Returns context UIManager
MapManager mapManager() // Returns context MapManager

// TO BE IMPLEMENTED\
BorderManager borderManager() // Returns context BorderManager
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
Collection<Player> getMembers() // Returns list of online players on the team
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
Collection<Player> getPlayers() // Returns all registered players in the game
void setSpectator(Player player) // Sets specified player to a spectator (not spectator mode, the plugins implementation of spectators)
void setNonSpectator(Player player) // Makes specified player a non spectator 
Collection<Player> getSpectators() // Returns all registered spectators
Collection<Player> getNonSpectators() // Returns all registered players in game who are not spectators
void setPlayerSpawn(Player player, Location location) // Sets spawnpoint/respawn location of specified player
Location getPlayerSpawn(Player player) // Returns player spawn location
void respawnPlayer(Player player) // Respawns specified player instantaneously
void respawnPlayer(Player player, int time) // Respawns specified player after given time duration in ticks
void cancelRespawn(Player player) // Cancels active respawn timers on the player
PlayerManager onPlayerRegistered(Consumer<Player> action) // Runs action on player registered into game
```

## USER INTERFACE
**UIManager**
```java
void title(Audience audience, Component title) // Sends a title to specified audience
void subtitle(Audience audience, Component subtitle) // Sends subtitle to audience, regardless of currently shown title
void subtitle(Audience audience, Component subtitle, Title.Times times) // Sends subtitle with a times
void actionbar(Audience audience, Component message) // Sends actionbar to specified audience
void chat(Audience audience, Component message) // Sends chat message to specified audience
void gameMessage(Audience audience, Component message) // Sends formatted game message to specified audience
GameSidebar createSidebar() // Creates a sidebar but does not display it yet
GameBossbar createBossbar() // Creates a bossbar but does not display it yet
void clearUi(Audience audience) // Clears all UI elements for a specified audience
// TO BE IMPLEMENTED:
void setGlowing(Entity entity, boolean glowing) // Sets whether or not an entity is glowing for all players
void setGlowing(Entity entity, boolean glowing, Audience audience) // Same as above but entity only glows for a specified audience
void setGlowColor(Entity entity, ChatColor color) // Sets glow color of an entity
```
**GameSidebar**
```java
Audience audience() // Returns audience that can see the sidebar
GameSidebar setAudience(Audience audience) // Sets audience of sidebar
GameSidebar addLine(Component line) // Adds static component line to end of sidebar
GameSidebar addLine(ValueDisplay display) // Adds formatted value display line to end of sidebar
GameSidebar removeLine(int index) // Removes specified line number
GameSidebar clearLines() // Removes all lines from sidebar
List<Object> getLines() // Returns immutable list of sidebar lines
GameSidebar setUpdateInterval(int interval) // Sets how often dynamic sidebar lines update in ticks
int getUpdateInterval() // Returns how often dynamic sidebar lines update in ticks
GameSidebar setVisibility(boolean visible) // Shows sidebar to be visible or not
boolean isVisible() // Returns whether or not the sidebar is visible
GameSidebar refresh() // Immediately refreshes visible sidebar lines
void remove() // Deletes and hides the sidebar
```
**GameBossbar**
```java
Audience audience() // Returns audience that can see the bossbar
GameBossbar setAudience(Audience audience) // Sets the audience of bossbar
Component getTitle() // Returns bossbar title
GameBossbar setTitle(Component title) // Sets static title of bossbar
GameBossbar setTitle(ValueDisplay display) // Sets formatted value display as bossbar title
int getValue() // Returns bossbar value
GameBossbar setValue(int value) // Sets static value for bossbar
GameBossbar setValue(ValueDisplay value) // Sets value for bossbar based on a value display
int getMax() // Returns bossbar max
GameBossbar setMax(int max) // Sets static max for bossbar
GameBossbar setMax(ValueDisplay max) // Sets max for bossbar based on a value display
GameBossbar setUpdateInterval(int interval) // Sets how often dynamic bossbar values update in ticks
int getUpdateInterval() // Returns how often dynamic bossbar values update in ticks
GameBossbar setStyle(BossBar.Overlay style) // Sets style of bossbar
BossBar.Overlay getStyle() // Returns style of bossbar
GameBossbar setColor(BossBar.Color color) // Sets color of bossbar
BossBar.Color getColor() // Returns color of bossbar
GameBossbar setVisibility(boolean visible) // Shows bossbar to be visible or not
boolean isVisible() // Returns whether or not the bossbar is visible
GameBossbar refresh() // Immediately refreshes visible bossbar
void remove() // Deletes and hides the bossbar
```
**Values**
```java
// Using an empty string for a label omits the label to create a value only display
static ValueDisplay integer(String label, int value) // Creates static global integer display
static ValueDisplay integer(String label, IntSupplier value) // Creates global live integer display
static ValueDisplay integer(String label, ToIntFunction<Player> value) // Creates player-specific live integer display
static ValueDisplay decimal(String label, double value) // Creates static global decimal display
static ValueDisplay decimal(String label, DoubleSupplier value) // Creates global live decimal display
static ValueDisplay decimal(String label, ToDoubleFunction<Player> value) // Creates player-specific live decimal display
static ValueDisplay time(String label, int time) // Creates static global time display in seconds
static ValueDisplay time(String label, IntSupplier time) // Creates global live time display in seconds
static ValueDisplay time(String label, ToIntFunction<Player> time) // Creates player-specific live time display in seconds
static ValueDisplay fraction(String label, int numerator, int denominator) // Creates static global fraction display
static ValueDisplay fraction(String label, IntSupplier numerator, IntSupplier denominator) // Creates global live fraction display
static ValueDisplay fraction(String label, ToIntFunction<Player> numerator, ToIntFunction<Player> denominator) // Creates player-specific live fraction display
static ValueDisplay percent(String label, double percent) // Creates static global percent display where 0.5 displays as 50%
static ValueDisplay percent(String label, DoubleSupplier percent) // Creates global live percent display where 0.5 displays as 50%
static ValueDisplay percent(String label, ToDoubleFunction<Player> percent) // Creates player-specific live percent display where 0.5 displays as 50%
static ValueDisplay component(String label, Component value) // Creates static global component text display
static ValueDisplay component(String label, Supplier<Component> value) // Creates global live component text display
static ValueDisplay component(String label, Function<Player, Component> value) // Creates player-specific live component text display
```
**ValueDisplay**
```java
enum ValueType {INTEGER, DECIMAL, TIME, FRACTION, PERCENT, COMPONENT} // The types of values that can be displayed by a ValueDisplay
enum ValueScope {STATIC, GLOBAL, PLAYER} // Whether value is static, live and shared, or live and different per player
ValueType getType() // Returns type of value display
ValueScope getScope() // Returns scope of value display
String getLabel() // Returns label of value display
boolean isDynamic() // Returns whether or not the value display updates after creation
boolean isGlobal() // Returns whether or not the value display scope is global
Component getDisplay() // Returns formatted display component if it is global or static
Component getDisplay(Player player) // Returns formatted display component for specified player
String getValue() // Returns formatted value as a string if it is global or static
String getValue(Player player) // Returns formatted value as a string for specified player
```

## MAP AND WORLD MANAGEMENT
**MapManager**
```java
CompletableFuture<GameMap> setMap(GameMap map) // Sets/replaces the current map and makes a new world with the new map, teleports players to it, and discards the previous world without ending or unloading the game and keeping the world name/index/game index the same throughout
CompletableFuture<Void> placeMap(GameMap map, Location loc) // Places a map directly into the game world at a location instead of loading a new world
CompletableFuture<Void> placeMap(GameMap map, Location loc, MapRotation rot) // Same as above but lets you place map in certain orientation along the y axis of placed location
**GameWorld**
```java
World getBukkitWorld() // Returns the bukkit world of the GameWorld
List<Location> getPoints(String pointName) // Returns list of locations based of specified point name
double distanceToPoint(Location loc, String pointName) // Returns closest distance of a specified location to an instance of a specified point
void addPoint(Location loc, String pointName) // Adds point of specified name to a certain location
int removePoint(Location loc, String pointName) // Removes all points of specified point name at specified location (returns number removed)
List<BoundingBox> getRegions(String regionName) // Returns list of cuboid regions based of specified region name
boolean posInRegion(Location loc, String regionName) // Returns whether or not a location is inside an instance of a specified region
void addRegion(BoundingBox box, String regionName) // Adds region of specified name covering bounding box of coordinates
int removeRegion(BoundingBox box, String regionName) // Removes all regions that cover exactly the specified bounding box with a certain name (returns number removed)
void onEntityEnterRegion(String regionName, Consumer<Entity> action) // Runs action when an entity enters a region
void onEntityExitRegion(String regionName, Consumer<Entity> action) // Runs action when an entity exits a region
CompletableFuture<Void> clearArea(BoundingBox box) // Deletes blocks/entities/everything else in a bounding box that does not necessarily have to be a region
```
**GameMap**
```java
String getId() // Returns map id
String getName() // Returns map name
List<String> getTags() // Returns list of tags the map has
boolean hasTag(String tag) // Returns whether or not the map has a certain tag
List<GameMap> getSubmaps(String... tags) // Returns list of direct submaps of the map, and optionally provide one or more tags to filter by
```
enum **MapRotation**:
```java
enum MapRotation {DEG_0, DEG_90, DEG_180, DEG_270} // The four possible rotations of the map along y axis, counterclockwise
static MapRotation fromDegrees(int degrees) // Returns a MapRotation which rotates map by either 0, 90, 180, or 270 degrees
```

Everything below is yet to be implemented:

## BORDERS
**BorderManager**
```java
enum BorderShape {CUBOID, CYLINDROID, ELLIPSOID} // The available 3D geometric border shapes
GameBorder createBorder(BorderShape shape, Location center, Vector dimensions) // Creates cuboid border with specified center and dimensions
List<GameBorder> getBorders() // Returns list of active borders
boolean borderContainsLocation(GameBorder border, Location location) // Returns whether or not specified location is inside specified border's bounds
void setBorderDamage(double amount, int interval) // Sets the damage of all borders owned by the BorderDamage; it would apply the specified amount of damage to all players not standing in any border every interval ticks
```
**GameBorder**
```java
Location getCenter() // Returns center location of border
Vector getDimensions() // Returns current dimensions of border
BorderShape getShape() // Returns 3D shape of border
GameBorder setDimensions(Vector dimensions) // Changes dimensions of border instantaneously
GameBorder setDimensions(Vector dimensions, int time) // Changes dimensions of border over specified time in ticks
GameBorder setCenter(Location center) // Moves center of border instantaneously
GameBorder setCenter(Location center, int time) // Moves center of border over specified time in ticks
void deleteBorder() // Deletes the border
```
