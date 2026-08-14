These are methods you can use from game module code:

## MODULE

**GameModule**
```java
void beforeLoad() // Runs before the module loads
void onLoad() // Runs when the module loads
void onStart() // Runs when the module starts
void onUnload() // Runs when the module unloads
CompletableFuture<Boolean> unload() // Unloads this module instance
InputStream resource(String path) // Returns a resource from the module jar
Donutgame plugin() // Returns the base Donutgame plugin
String id() // Returns module id
String name() // Returns module name
YamlConfiguration config() // Returns module config
GameData data() // Returns module data helper
int index() // Returns active game index
GameWorld world() // Returns current game world
PlayerManager playerManager() // Returns module PlayerManager
MapManager mapManager() // Returns module MapManager
UiManager uiManager() // Returns module UiManager
TimeManager timeManager() // Returns module TimeManager
TeamManager teamManager() // Returns module TeamManager
BorderManager borderManager() // Returns module BorderManager
GameEventRegistrar events() // Returns module event registrar
void log(String message) // Logs an info message for this module
void logWarning(String message) // Logs a warning message for this module
void logError(String message, Throwable throwable) // Logs an error message for this module
```

**GameData**
```java
YamlConfiguration configuration(String name) // Loads a YAML configuration resource by name
ItemStack item(String input) // Parses an item string
ItemStack randomPool(List<String> pool) // Returns a random item from a pool
Collection<ItemStack> loot(String path) // Returns loot from a loot table
Collection<ItemStack> loot(String path, GamePlayer player) // Returns loot from a loot table for a player
void give(GamePlayer player, Collection<ItemStack> items) // Gives items to a player
void give(GamePlayer player, ItemStack... items) // Gives items to a player
Component itemName(ItemStack item) // Returns an item's display name
Reader reader(String path) // Opens a text resource reader
```

## TEAMS

**TeamManager**
```java
GameTeam newTeam() // Creates a team with default properties
GameTeam newColoredTeam() // Creates a team with the next default color
Collection<GameTeam> getTeams() // Returns current teams
GameTeam getPlayerTeam(GamePlayer player) // Returns the team a player belongs to
boolean playerHasTeam(GamePlayer player) // Returns whether a player is in a team
void clear() // Removes all teams
```

**GameTeam**
```java
GameTeam addPlayer(GamePlayer player) // Adds a player to the team
GameTeam removePlayer(GamePlayer player) // Removes a player from the team
void remove() // Removes and unregisters the team
Collection<GamePlayer> getMembers() // Returns online registered team members
GameTeam setColor(NamedTextColor color) // Sets team color
GameTeam setDisplayName(Component displayName) // Sets team display name
Component displayName() // Returns team display name
GameTeam setSpectatable(boolean spectatable) // Sets whether the team can be spectated
boolean isSpectatable() // Returns whether the team can be spectated
GameTeam setTeamGlow(boolean teamGlow) // Sets whether teammates glow for each other
GameTeam setPrefix(Component prefix) // Sets team prefix
GameTeam setSuffix(Component suffix) // Sets team suffix
GameTeam setFriendlyFire(boolean friendlyFire) // Sets friendly fire
GameTeam setSeeFriendlyInvisibles(boolean seeFriendlyInvisibles) // Sets whether invisible teammates are visible
GameTeam setNametagVisibility(Team.OptionStatus nametagVisibility) // Sets nametag visibility
GameTeam setCollisionRule(Team.OptionStatus collisionRule) // Sets collision rule
NamedTextColor color() // Returns team color
boolean teamGlow() // Returns whether teammate glow is enabled
Component prefix() // Returns team prefix
Component suffix() // Returns team suffix
boolean friendlyFire() // Returns whether friendly fire is enabled
boolean seeFriendlyInvisibles() // Returns whether invisible teammates are visible
Team.OptionStatus nametagVisibility() // Returns nametag visibility
Team.OptionStatus collisionRule() // Returns collision rule
Team bukkitTeam() // Returns the Bukkit scoreboard team
```

## TIME MANAGEMENT

**TimeManager**
```java
GameTimer newTimer(int time) // Creates a timer with a max duration in ticks
GameTimer newTimer() // Creates an unlimited timer
void cancelAll() // Cancels all active timers
```

**GameTimer**
```java
GameTimer start() // Starts the timer
GameTimer setMaxTicks(int ticks) // Sets max timer duration in ticks
GameTimer setUnlimitedMaxTicks() // Makes the timer run indefinitely
GameTimer pause() // Pauses the timer
GameTimer resume() // Resumes the timer
void cancel() // Cancels the timer
GameTimer onTick(Consumer<GameTimer> action) // Runs an action every tick
GameTimer onTick(int interval, Consumer<GameTimer> action) // Runs an action every interval ticks
GameTimer onFinish(Consumer<GameTimer> action) // Runs an action when the timer finishes
boolean isStarted() // Returns whether the timer has started
int getMaxTicks() // Returns max timer duration in ticks
int getElapsedTicks() // Returns elapsed ticks
int getRemainingTicks() // Returns remaining ticks, or -1 if unlimited
boolean isPaused() // Returns whether the timer is paused
boolean isCancelled() // Returns whether the timer is cancelled
boolean isFinished() // Returns whether the timer is finished
```

## PLAYER MANAGEMENT

**PlayerManager**
```java
PlayerManager onPlayerRegistered(Consumer<GamePlayer> action) // Runs action when a player registers
PlayerManager onPlayerUnregistered(Consumer<GamePlayer> action) // Runs action when a player unregisters
boolean isRegistered(Entity entity) // Returns whether an entity is registered
boolean isRegistered(Player player) // Returns whether a Bukkit player is registered
boolean isRegistered(GamePlayer player) // Returns whether a game player is registered
Collection<GamePlayer> getPlayers() // Returns all registered players
Collection<GamePlayer> getSpectators() // Returns registered spectators
Collection<GamePlayer> getNonSpectators() // Returns registered non-spectators
GamePlayer getPlayer(UUID uuid) // Returns registered player by uuid
GamePlayer getPlayer(Player player) // Returns registered player by Bukkit player
void setSpectatablePlayers(Collection<GamePlayer> players) // Sets players that spectators can spectate
void setSpectatableTeams(Collection<GameTeam> teams) // Sets teams that spectators can spectate
Collection<GamePlayer> spectatablePlayers() // Returns spectatable players
Collection<GameTeam> spectatableTeams() // Returns spectatable teams
boolean register(Player player) // Registers a Bukkit player into the game
int register(Collection<Player> players) // Registers multiple Bukkit players
boolean unregister(Player player) // Unregisters a Bukkit player from the game
GameModule module() // Returns owning module
```

**GamePlayer**
```java
Player player() // Returns the Bukkit player
void setSpectator() // Sets the player as a spectator
void setSpectator(GameLocation location) // Sets the player as a spectator at a location
void setSpectatorForRound(GameLocation location) // Sets the player as a temporary round spectator
void setNonSpectator() // Sets the player as a non-spectator
void resetForRound(GameLocation location, GameMode gameMode) // Resets player state for a round
void syncSpectatorState() // Reapplies spectator state
void removeEffect(PotionEffectType effect) // Removes a potion effect
Collection<PotionEffect> getEffects() // Returns current potion effects
void clearEffects() // Removes all potion effects
void respawn() // Respawns immediately at respawn location
void respawn(int ticks) // Respawns after a delay in ticks
void respawn(int ticks, Supplier<GameLocation> locationSupplier) // Respawns after a delay at a supplied location
void cancelRespawn() // Cancels active respawn timer
void setRespawnLocation(GameLocation location) // Sets respawn location
Map<Integer, ItemStack> addToStoredInventory(Collection<ItemStack> items) // Adds items to stored inventory
boolean isSpectator() // Returns whether the player is a spectator
GamePlayer setSpectatable(boolean spectatable) // Sets whether this player can be spectated
String getName() // Returns player name
void setGameMode(GameMode gameMode) // Sets player game mode
GameMode gameMode() // Returns player game mode
boolean isOnline() // Returns whether the player is online
GameLocation respawnLocation() // Returns respawn location
GameTimer respawnTimer() // Returns active respawn timer
void clearInventory() // Clears player inventory
void setFoodLevel(int foodLevel) // Sets food level
void setSaturation(float saturation) // Sets saturation
void clearExperience() // Clears experience
GameTeam team() // Returns player's team
Collection<GamePlayer> teammates() // Returns player's teammates
GamePlayer closestTeammate(Predicate<GamePlayer> filter) // Returns closest teammate matching filter
static ItemStack spectatorCompass() // Creates the spectator compass item
static boolean isSpectatorCompass(ItemStack item) // Returns whether an item is a spectator compass
static void removeSpectatorCompass(PlayerInventory inventory) // Removes spectator compasses from inventory
static ItemStack[] stripSpectatorCompass(ItemStack[] contents) // Removes spectator compasses from item contents
static ItemStack stripSpectatorCompass(ItemStack item) // Removes a spectator compass item
```

## USER INTERFACE

**UiManager**
```java
void title(GamePlayer player, Component title) // Sends a title to a player
void title(Collection<GamePlayer> players, Component title) // Sends a title to players
void subtitle(GamePlayer player, Component subtitle) // Sends a subtitle to a player
void subtitle(Collection<GamePlayer> players, Component subtitle) // Sends a subtitle to players
void actionbar(GamePlayer player, Component actionbar) // Sends an actionbar to a player
void actionbar(Collection<GamePlayer> players, Component actionbar) // Sends an actionbar to players
void chat(GamePlayer player, Component message) // Sends chat message to a player
void chat(Collection<GamePlayer> players, Component message) // Sends chat message to players
void gameMessage(GamePlayer player, Component message) // Sends formatted game message to a player
void gameMessage(Collection<GamePlayer> players, Component message) // Sends formatted game message to players
void sound(GamePlayer player, Sound sound) // Plays a sound to a player
void sound(Collection<GamePlayer> players, Sound sound) // Plays a sound to players
void sound(Collection<GamePlayer> players, Sound sound, float volume, float pitch) // Plays a sound with volume and pitch
void sound(Collection<GamePlayer> players, Sound sound, SoundCategory category, GameLocation location, float volume, float pitch, float minVolume) // Plays a located sound
void sound(Collection<GamePlayer> players, String sound, SoundCategory category, GameLocation location, float volume, float pitch, float minVolume) // Plays a named located sound
GameSidebar newSidebar() // Creates a sidebar
Set<GameSidebar> sidebars() // Returns active sidebars
GameGlow glow(GameEntity entity, Collection<GamePlayer> viewers, NamedTextColor color) // Makes an entity glow for viewers
GameGlow glow(GameEntity entity, GamePlayer viewer, NamedTextColor color) // Makes an entity glow for a viewer
GameGlow glow(GameLocation location, BlockData blockData, Collection<GamePlayer> viewers, NamedTextColor color) // Makes a block glow for viewers
GameGlow glow(GameLocation location, BlockData blockData, GamePlayer viewer, NamedTextColor color) // Makes a block glow for a viewer
void refreshTeamGlows() // Refreshes team glow state
void refreshPlayerState() // Refreshes player visibility and glow state
void clear() // Clears sidebars and glows
```

**GameSidebar**
```java
GameSidebar viewers(Supplier<Collection<GamePlayer>> viewersSupplier) // Sets sidebar viewers
GameSidebar title(Supplier<Component> titleSupplier) // Sets sidebar title
GameSidebar blank() // Adds a blank line
GameSidebar integer(String label, IntSupplier valueSupplier) // Adds a global integer line
GameSidebar integer(String label, Function<GamePlayer, Integer> valueSupplier) // Adds a player-specific integer line
GameSidebar fraction(String label, IntSupplier numeratorSupplier, IntSupplier denominatorSupplier) // Adds a fraction line
GameSidebar time(String label, IntSupplier secondsSupplier) // Adds a time line
GameSidebar dynamicTime(Supplier<String> labelSupplier, IntSupplier secondsSupplier) // Adds a time line with dynamic label
GameSidebar show() // Shows and refreshes the sidebar
void delete() // Deletes the sidebar
```

**GameGlow**
```java
void clear() // Clears the glow effect
```

## MAP AND WORLD MANAGEMENT

**MapManager**
```java
CompletableFuture<GameMap> setMap(String mapId) // Loads and sets map by id
CompletableFuture<GameMap> setMap(GameMap map) // Loads and sets map
CompletableFuture<Void> placeMap(String mapId, Location location) // Places map by id in the current world
CompletableFuture<Void> placeMap(String mapId, Location location, MapRotation rotation) // Places map by id with rotation
CompletableFuture<Void> placeMap(GameMap map, Location location) // Places map in the current world
CompletableFuture<Void> placeMap(GameMap map, Location location, MapRotation rotation) // Places map with rotation
CompletableFuture<Void> unloadCurrentWorld() // Unloads current game world
GameWorld currentWorld() // Returns current game world
GameMap currentMap() // Returns current map
```

**GameWorld**
```java
World bukkitWorld() // Returns the Bukkit world
String name() // Returns logical world name
GameLocation spawnLocation() // Returns world spawn location
void setPvp(boolean enabled) // Sets PvP game rule
void addPoint(GameLocation location, String pointName) // Adds a named point
void removePoint(GameLocation location, String pointName) // Removes a named point
void addRegion(GameRegion region, String regionName) // Adds a named region
void removeRegion(GameRegion region, String regionName) // Removes a named region
List<GameLocation> getPoints(String pointName) // Returns named points
GameLocation point(String pointName) // Returns first named point
double distanceToPoint(GameLocation location, String pointName) // Returns closest distance to named point
List<GameRegion> getRegions(String regionName) // Returns named regions
GameRegion region(String regionName) // Returns first named region
boolean posInRegion(GameLocation location, String regionName) // Returns whether a location is inside a named region
boolean contains(Location location) // Returns whether a Bukkit location is in this world
boolean contains(GameLocation location) // Returns whether a game location is valid
boolean contains(Entity entity) // Returns whether an entity is in this world
void setBlock(GameLocation location, Material material) // Sets a block to a material
void setBlock(GameLocation location, BlockData blockData) // Sets a block to block data
CompletableFuture<Void> fill(GameRegion region, Material material) // Fills a region with material
CompletableFuture<Void> fill(GameRegion region, BlockData blockData) // Fills a region with block data
GameEntity summon(EntityType entityType, GameLocation location) // Summons an entity
GameChest placeChest(GameLocation location) // Places a game chest
GameEntity entity(Entity entity) // Wraps an entity in this world
```

**GameMap**
```java
String id() // Returns map id
String name() // Returns map name
List<String> tags() // Returns map tags
Map<String, List<MapPoint>> points() // Returns map points
Map<String, List<MapRegion>> regions() // Returns map regions
List<GameMap> submaps() // Returns direct submaps
```

**GameLocation**
```java
static GameLocation fromBukkit(Location location) // Converts a Bukkit location to a GameLocation
Location toBukkit(World world) // Converts to a Bukkit location in a world
double distanceSquared(GameLocation other) // Returns squared distance to another location
double x() // Returns x coordinate
double y() // Returns y coordinate
double z() // Returns z coordinate
double pitch() // Returns pitch
double yaw() // Returns yaw
```

**GameRegion**
```java
static GameRegion fromBoundingBox(BoundingBox box) // Converts a bounding box to a GameRegion
boolean contains(GameLocation location) // Returns whether the region contains a location
GameLocation center() // Returns region center
GameLocation min() // Returns minimum corner
GameLocation max() // Returns maximum corner
```

enum **MapRotation**:
```java
enum MapRotation {DEG_0, DEG_90, DEG_180, DEG_270} // The four possible rotations of the map along y axis, counterclockwise
int degrees() // Returns rotation in degrees
static MapRotation fromDegrees(int degrees) // Returns a MapRotation for 0, 90, 180, or 270 degrees
```

## ENTITIES

**GameEntity**
```java
boolean exists() // Returns whether the entity exists
GameLocation location() // Returns entity location
boolean teleport(GameLocation location) // Teleports entity
void remove() // Removes entity
void kill() // Kills or removes entity
void setHealth(double health) // Sets health if damageable
void damage(double damage) // Damages entity if damageable
void addEffect(PotionEffectType effect) // Adds default potion effect
void addEffect(PotionEffectType effect, int ticks) // Adds potion effect for ticks
void addEffect(PotionEffectType effect, int ticks, int amplifier) // Adds potion effect with amplifier
void addEffect(PotionEffectType effect, int ticks, int amplifier, boolean hideParticles) // Adds potion effect with particle option
void addVanillaEffect(PotionEffectType effect) // Adds default vanilla-duration effect
void addVanillaEffect(PotionEffectType effect, int seconds) // Adds effect for seconds
void addVanillaEffect(PotionEffectType effect, int seconds, int amplifier) // Adds effect for seconds with amplifier
void addVanillaEffect(PotionEffectType effect, int seconds, int amplifier, boolean hideParticles) // Adds effect for seconds with particle option
void effect(PotionEffectType effect) // Adds default vanilla-duration effect
void effect(PotionEffectType effect, int seconds) // Adds effect for seconds
void effect(PotionEffectType effect, int seconds, int amplifier) // Adds effect for seconds with amplifier
void effect(PotionEffectType effect, int seconds, int amplifier, boolean hideParticles) // Adds effect for seconds with particle option
void addEffect(PotionEffect effect) // Adds a potion effect
void setAttributeBaseValue(Attribute attribute, double value) // Sets an attribute base value
Double getAttributeValue(Attribute attribute) // Returns an attribute value
PersistentDataContainer persistentData() // Returns persistent data container
<P, C> void setData(NamespacedKey key, PersistentDataType<P, C> type, C value) // Sets persistent data
<T extends Entity> T as(Class<T> entityClass) // Casts to Bukkit entity type
Entity bukkitEntity() // Returns Bukkit entity
GameWorld world() // Returns entity world
UUID uuid() // Returns entity uuid
EntityType type() // Returns entity type
```

## EVENTS

**GameEventRegistrar**
```java
<T extends Event> void player(Class<T> eventType, Function<T, Player> playerGetter, Consumer<GamePlayerEvent<T>> handler) // Registers a player event handler
<T extends Event> void entity(Class<T> eventType, Function<T, Entity> entityGetter, Consumer<GameEntityEvent<T>> handler) // Registers an entity event handler
<T extends Event> void location(Class<T> eventType, Function<T, Location> locationGetter, Consumer<GameLocationEvent<T>> handler) // Registers a location event handler
<T extends Event> void block(Class<T> eventType, Function<T, Location> locationGetter, Consumer<GameBlockEvent<T>> handler) // Registers a block location event handler
```

## BORDERS

**BorderManager**
```java
enum BorderShape {CUBOID, CYLINDROID, ELLIPSOID} // The available 3D geometric border shapes
GameBorder newBorder(GameRegion region) // Creates a cuboid border from a region
GameBorder newBorder(BorderShape shape, GameLocation center, Vector dimensions) // Creates a border with specified shape, center, and dimensions
void setBorderDamage(double damage, int interval) // Sets border damage and tick interval
Collection<GameBorder> borders() // Returns active borders
void clear() // Removes all borders
double damage() // Returns border damage amount
double interval() // Returns border damage interval
double particleSpacing() // Returns particle spacing
double particleViewDistance() // Returns particle view distance
Particle defaultParticle() // Returns border particle
Particle movingParticle() // Returns moving border particle
```

**GameBorder**
```java
GameBorder setCenter(GameLocation target) // Moves center instantly
GameBorder setCenter(GameLocation target, int ticks) // Moves center over time
GameBorder setDimensions(Vector target) // Changes dimensions instantly
GameBorder setDimensions(Vector target, int ticks) // Changes dimensions over time
boolean containsLocation(GameLocation location) // Returns whether location is inside border
boolean containsLocation(double x, double y, double z) // Returns whether relative coordinates are inside border
BorderShape shape() // Returns border shape
GameLocation center() // Returns border center
Vector dimensions() // Returns border dimensions
boolean isMoving() // Returns whether border is moving or resizing
```
