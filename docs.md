These are methods you can use from game module code:

## CONTEXT

**GameContext**
```java
Plugin getPlugin() // Returns base Donutgame plugin
Logger getLogger() // Returns base Donutgame plugin’s logger
GameMap createMap(String mapPath) // Creates GameMap object based on YAML filepath
CompletableFuture<GameMap> initializeMap(String mapPath) // Loads world from GameMap YAML
TeamManager teamManager() // Returns context TeamManager

// TO BE IMPLEMENTED
TimeManager timeManager() // Returns context TimeManager
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

Everything below is yet to be implemented:

## TIME MANAGEMENT
**TimeManager**
```java
GameTimer createTimer(int time) // Creates timer that continously counts down to 0 starting from specified time in ticks
GameTimer createStopwatch() // Creates timer that continously counts up infinitely starting from 0
GameTimer createStopwatch(int time) // Creates timer that continously counts up to specified time in ticks starting from 0
```
**GameTimer**
```java
int getElapsedTicks() // Returns number of ticks since timer started
int getElapsedSeconds() // Returns number of seconds since timer started, rounded to nearest integer
int getRemainingTicks() // Returns number of ticks remaining in timer, returns -1 if timer is counting upwards indefinitely
int getRemainingSeconds() // Returns number of seconds remaining in timer, rounded to nearest integer, returns -1 if timer is counting upwards indefinitely
boolean isFinished() // Returns whether or not the timer is finished
void toggleRunning() // Toggles running timer to paused, paused timer to running
void toggleRunning(boolean running) // Pauses timer if running is false, and resumes if running is true
boolean isRunning() // Returns false if timer is paused and true if running
void cancel() // Cancels timer early
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
Collection<Player> getSpectators() // Returns all spectators
Collection<Player> getNonSpectators() // Returns all players in game who are not spectators
void setPlayerSpawn(Player player, Location location) // Sets spawnpoint/respawn location of specified player
Location getPlayerSpawn(Player player) // Returns player spawn location
void respawnPlayer(Player player) // Respawns specified player instantaneously
void respawnPlayer(Player player, int time) // Respawns specified player after given time duration in ticks
void cancelRespawn(Player player) // Cancels active respawn timers on the player
```

## BORDERS
**BorderManager**
```java
GameBorder createBorder(BorderShape shape, Location center, Vector dimensions) // Creates cuboid border with specified center and dimensions
```
**BorderShape** (Enum)
```java
BorderShape.CUBOID, BorderShape.CYLINDROID, BorderShape.ELLIPSOID // The three available border shapes
```
**GameBorder**
```java
Location getCenter() // Returns center location of border
Vector getDimensions() // Returns dimensions of border
BorderShape getShape() // Returns 3D shape of border
void setDimensions(Vector dimensions) // Changes dimensions of border instantaneously
void setDimensions(Vector dimensions, int time) // Changes dimensions of border over specified time in ticks
void setCenter(Location center) // Moves center of border instantaneously
void setCenter(Location center, int time) // Moves center of border over specified time in ticks
void setDamage(double amount, int interval) // Sets how much damage to deal players outside the border every specified interval in ticks
```