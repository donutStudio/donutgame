These are methods intended to be exposed and used from game module code. If a method is exposed for other parts of the internal plugin to use, but is not intended for API usage, it will not be in these docs.

## MODULE

**GameModule**
```java
void beforeLoad() // Runs before the module loads
void onLoad() // Runs when the module loads
void onReload() // Runs when the module reloads, if not overridden defaults to just calling onLoad again
void onStart() // Runs when the module starts
void onUnload() // Runs when the module unloads
void reload() // Performs a "reload" of the module (the reload basically calls onReload, starts the load sequence and countdown and stuff again in startLoadSequence, etc. This would be used for things like say multiple rounds in a game like Void Wars)
CompletableFuture<Boolean> unload() // Unloads this module instance
void registerEventHandlers(Object target) // Registers @GameEventHandler methods from a helper object owned by the module
InputStream resource(String path) // Returns a resource from the module jar
Donutgame plugin() // Returns the base Donutgame plugin
String id() // Returns module id
String name() // Returns module name
YamlConfiguration config() // Returns module config.yml
YamlConfiguration config(String path) // Returns a YamlConfig based on specified path from jar instead of just defaulting to config.yml 
GameData data() // Returns module data helper
int index() // Returns active game index
GameWorld world() // Returns current game world
PlayerManager playerManager() // Returns module PlayerManager
MapManager mapManager() // Returns module MapManager
UiManager uiManager() // Returns module UiManager
TimeManager timeManager() // Returns module TimeManager
TeamManager teamManager() // Returns module TeamManager
BorderManager borderManager() // Returns module BorderManager
void log(String message) // Logs an info message for this module
void logWarning(String message) // Logs a warning message for this module
void logError(String message, Throwable throwable) // Logs an error message for this module
```
**GameData**
```java
LootTable lootTable(String key) // Returns loot table from a namespaced key id, defaulting to the module namespace when omitted
LootTable lootTable(List<ItemStack> items) // Wraps a compact equal-weight item pool as a loot table. Duplicates are allowed and increase weight
// Later on in GameData we will add built in support for other data driven registries in minecraft like advancement, item_modifier, recipe, predicate, function, etc
// The reason we are adding and enforcing namespaces is just to simplify and keep in line with vanilla parsing, but there will be some protection/fencing enforced to avoid game modules from clashing namespaces with stuff. An omitted loot table namespace defaults to the current module namespace.
```
**GameItems**
```java
static ItemStack item(String input) // Parses a compact item string into an ItemStack
static List<ItemStack> items(List<String> inputs) // Parses compact item strings into ItemStacks
static Collection<ItemStack> items(LootTable lootTable) // Generates items from a loot table with a random seed
static Collection<ItemStack> items(LootTable lootTable, long seed) // Generates items from a loot table with a specified seed
static Component displayName(ItemStack item) // Returns item_name when set, otherwise the Bukkit display name
```
## TEAMS

**TeamManager**
```java
GameTeam newTeam() // Creates a team with default properties
GameTeam newColoredTeam() // Creates a team with the next default color
Collection<GameTeam> getTeams() // Returns current teams
Collection<GameTeam> getSpectatorTeams() // Returns teams whose members are all spectators
Collection<GameTeam> getNonSpectatorTeams() // Returns teams who have at least one non spectator member
void clear() // Removes all teams
```
**GameTeam**
```java
GameTeam addPlayer(GamePlayer player) // Adds a player to the team
GameTeam removePlayer(GamePlayer player) // Removes a player from the team
void remove() // Removes and unregisters the team
Collection<GamePlayer> getMembers() // Returns online registered team members
Collection<GamePlayer> getSpectatorMembers() // Returns spectating team members
Collection<GamePlayer> getNonSpectatorMembers() // Returns non spectating team members
boolean allMembersSpectators() // Returns whether or not all members on the team are spectating
GameTeam setColor(NamedTextColor color) // Sets team color
GameTeam setDisplayName(Component displayName) // Sets team display name
Component displayName() // Returns team display name
GameTeam setSpectatable(boolean spectatable) // Sets whether the team can be spectated
boolean isSpectatable() // Returns whether the team can be spectated
GameTeam setTeamGlow(boolean teamGlow) // Sets whether teammates glow for each other
GameTeam setPrefix(Component prefix) // Sets team prefix
GameTeam setSuffix(Component suffix) // Sets team suffix
GameTeam setFriendlyFire(boolean friendlyFire) // Sets friendly fire
GameTeam setFriendlyFireDamageTypes(Collection<DamageType> damageTypes) // Sets teammate damage types allowed even when friendly fire is false
GameTeam allowFriendlyFireDamageTypes(DamageType... damageTypes) // Adds teammate damage types allowed even when friendly fire is false
GameTeam denyFriendlyFireDamageTypes(DamageType... damageTypes) // Removes teammate damage types from the friendly-fire exception set
GameTeam setSeeFriendlyInvisibles(boolean seeFriendlyInvisibles) // Sets whether invisible teammates are visible
GameTeam setNametagVisibility(Team.OptionStatus nametagVisibility) // Sets nametag visibility
GameTeam setCollisionRule(Team.OptionStatus collisionRule) // Sets collision rule
NamedTextColor color() // Returns team color
boolean teamGlow() // Returns whether teammate glow is enabled
Component prefix() // Returns team prefix
Component suffix() // Returns team suffix
boolean friendlyFire() // Returns whether friendly fire is enabled
Set<DamageType> friendlyFireDamageTypes() // Returns allowed teammate damage types while friendlyFire is false
boolean allowsFriendlyFireDamage(DamageType damageType) // Returns whether this team allows the damage type against teammates
boolean seeFriendlyInvisibles() // Returns whether invisible teammates are visible
Team.OptionStatus nametagVisibility() // Returns nametag visibility
Team.OptionStatus collisionRule() // Returns collision rule
```
`friendlyFire` defaults to `false`. By default, direct damage and thrown projectile damage from teammates is blocked, while teammate-caused `DamageType.EXPLOSION` and `DamageType.PLAYER_EXPLOSION` damage is still allowed. Set `friendlyFire(true)` to allow all teammate damage, or adjust the allowed set with `setFriendlyFireDamageTypes(...)`.

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
GameTimer onTick(Consumer<GameTimer> action) // Runs an action at elapsed tick 0, then every tick
GameTimer onTick(int interval, Consumer<GameTimer> action) // Runs an action at elapsed tick 0, then every interval ticks
GameTimer onFinish(Consumer<GameTimer> action) // Runs an action when the timer finishes
boolean isStarted() // Returns whether the timer has started
int getMaxTicks() // Returns max timer duration in ticks
int getElapsedTicks() // Returns elapsed ticks
double getElapsedSeconds() // Returns elapsed seconds
int getRemainingTicks() // Returns remaining ticks, or -1 if unlimited
double getRemainingSeconds() // Returns remaining seconds
boolean isPaused() // Returns whether the timer is paused
boolean isCancelled() // Returns whether the timer is cancelled
boolean isFinished() // Returns whether the timer is finished
```

## PLAYER MANAGEMENT
**PlayerManager**
```java
PlayerManager onPlayerRegistered(Consumer<GamePlayer> action) // Runs action when a player registers
PlayerManager onPlayerUnregistered(Consumer<GamePlayer> action) // Runs action when a player unregisters
Collection<GamePlayer> getPlayers() // Returns all registered players
Collection<GamePlayer> getSpectators() // Returns registered spectators
Collection<GamePlayer> getNonSpectators() // Returns registered non-spectators
```

**GamePlayer**
```java
Player bukkitPlayer() // Returns bukkit player of player
void setSpectator(boolean spectator) // Sets the player as a spectator or a non spectator (true sets to spectator)
void setSpectator(boolean spectator, GameLocation location) // Sets the player as a spectator at a location
void respawn() // Respawns immediately at player respawn location
void respawn(GameLocation location) // Respawns immediately at specified location
void respawn(int ticks) // Respawns after a delay in ticks, defaulting at player's spawnPoint
void respawn(int ticks, GameLocation location) // Respawns after a delay at specified location
void respawn(int ticks, Supplier<GameLocation> locationSupplier) // Respawns after a delay at a supplied location
void cancelRespawn() // Cancels active respawn timer
void setSpawnPoint(GameLocation location) // Sets respawn location
void setSpawnPoint(double x, double y, double z) // Sets respawn location
void setSpawnPoint(double x, double y, double z, double pitch, double yaw) // Sets respawn location
boolean isSpectator() // Returns whether the player is a spectator 
// when a player is a spectator, they are in like an "alternate state"- so say data like their gamemode, inventory, health, etc is updated, it is just stored internally in the player like the rest of the data but is not indicated or shown to the spectating player unless they are no longer a spectator. So say a player is a spectator, and code runs that sets that players gamemode to survival with GamePlayer#setGameMode and gives them an item, they will not actually see these reflected changes until they are not a spectator)
void setSpectatablePlayers(Collection<GamePlayer> players) // Sets players that this player can spectate as a spectator
void setSpectatableTeams(Collection<GameTeam> teams) // Sets teams that this player can spectate as a spectator
Collection<GamePlayer> spectatablePlayers() // Returns spectatable players this player can spectate
Collection<GameTeam> spectatableTeams() // Returns spectatable teams this player can spectate
String name() // Returns player name
void setGameMode(GameMode gameMode) // Sets player game mode
GameMode gameMode() // Returns player game mode
boolean isOnline() // Returns whether the player is online
GameLocation spawnPoint() // Returns respawn location
GameTimer respawnTimer() // Returns active respawn timer
void giveItem(ItemStack item) // Gives player an item
void clearItems() // Clears player's inventory
void clearItems(ItemStack item) // Clears specified item stack from player (so no specified count clears all instances of that item, a count that would be the max amount of items to clear from the player. Data components would also matter. It works similarly to vanilla /clear)
int itemCount(ItemStack item) // Returns count of specified item in inventory
void setHunger(int hunger) // Sets food level
int hunger() // Returns current food level of player
void setSaturation(float saturation) // Sets saturation
float saturation() // Returns current saturation of player
void setLevel(int level) // Sets exp level of player
void setExp(float progress) // Sets ratio/progress of xp of current level, where 0.0 is an empty XP bar and 1.0 is a full XP bar
void setTotalExperience(int xp) // Sets total experience points of a player
int level() // Returns exp level of player
float exp() // Returns exp progress of player
int totalExperience() // Returns total exp of player
void setArrowsInBody(int arrows) // Sets the number of arrows visually stuck in the player
int arrowsInBody() // Returns the number of arrows stored for the player
void clearArrowsInBody() // Clears arrows visually stuck in the player
GameTeam team() // Returns player's team, null if player is not on a team
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
void playSound(GamePlayer player, Sound sound) // Plays a sound to a player
void playSound(Collection<GamePlayer> players, Sound sound) // Plays a sound to players (not documented but also just works with GamePlayer and not a collection)
void playSound(Collection<GamePlayer> players, Sound sound, float volume, float pitch) // Plays a sound with volume and pitch (not documented but also just works with GamePlayer and not a collection)
void playSound(Collection<GamePlayer> players, Sound sound, SoundCategory category, GameLocation location, float volume, float pitch, float minVolume) // Plays a located sound (not documented but also just works with GamePlayer and not a collection)
GameSidebar newSidebar() // Creates a sidebar
Set<GameSidebar> sidebars() // Returns active sidebars
GameGlow newGlow(GameEntity entity, Collection<GamePlayer> viewers, NamedTextColor color) // Makes an entity glow for viewers
GameGlow newGlow(GameEntity entity, GamePlayer viewer, NamedTextColor color) // Makes an entity glow for a viewer
GameGlow newGlow(GameLocation location, BlockData blockData, Collection<GamePlayer> viewers, NamedTextColor color) // Makes a block glow for viewers
GameGlow newGlow(GameLocation location, BlockData blockData, GamePlayer viewer, NamedTextColor color) // Makes a block glow for a viewer
void clear() // Clears sidebars, glows, and other active UI like titles/subtitles/actionbars
```
**GameSidebar**
```java
GameSidebar setViewers(Collection<GamePlayer> viewers) // Sets sidebar viewers
GameSidebar setViewers(Supplier<Collection<GamePlayer>> viewersSupplier) // Sets sidebar viewers from supplier
GameSidebar setTitle(Component title) // Sets sidebar title
GameSidebar setTitle(Supplier<Component> titleSupplier) // Sets sidebar title from supplier
GameSidebar setLabel(String label, String newLabel) // Changes label name of a label
GameSidebar setLabel(String label, Supplier<String> newLabelSupplier) // Changes label name of a label from supplier
GameSidebar addLine() // Adds an empty line
GameSidebar addLine(Component component) // Adds a line just containing a component
GameSidebar addInteger(String label, int value) // Adds a global integer line
GameSidebar addInteger(String label, IntSupplier valueSupplier) // Adds a global integer line from supplier
GameSidebar addInteger(String label, Function<GamePlayer, Integer> valueSupplier) // Adds a player-specific integer line
GameSidebar addFraction(String label, int numerator, int denominator) // Adds a global fraction line
GameSidebar addFraction(String label, IntSupplier numeratorSupplier, IntSupplier denominatorSupplier) // Adds a global fraction line from supplier
GameSidebar addFraction(String label, Function<GamePlayer, Integer> numeratorSupplier, Function<GamePlayer, Integer> denominatorSupplier) // Adds a player-specific fraction line
GameSidebar addTime(String label, int ticks) // Adds a formatted global time value
GameSidebar addTime(String label, IntSupplier ticksSupplier) // Adds a formatted global time value from supplier
GameSidebar addTime(String label, Function<GamePlayer, Integer> ticksSupplier) // Adds a player-specific formatted global time value
GameSidebar show() // Shows and refreshes the sidebar
GameSidebar hide() // Hides the sidebar
GameSidebar removeLine(String label) // Removes a line from the sidebar based on its label
void remove() // Deletes the sidebar
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
CompletableFuture<GameMap> resetMap() // Reloads the initially selected map
CompletableFuture<Void> placeMap(String mapId, Location location) // Places map by id in the current world
CompletableFuture<Void> placeMap(String mapId, Location location, MapRotation rotation) // Places map by id with rotation
CompletableFuture<Void> placeMap(GameMap map, Location location) // Places map in the current world
CompletableFuture<Void> placeMap(GameMap map, Location location, MapRotation rotation) // Places map with rotation
CompletableFuture<Void> unloadWorld() // Unloads current game world
GameWorld world() // Returns current game world
GameMap map() // Returns original map ID world is based off of
```
**GameWorld**
```java
World bukkitWorld() // Returns the Bukkit world
String name() // Returns logical world name
void setWorldSpawn(GameLocation location) // Sets world spawn
GameLocation worldSpawn() // Returns world spawn location
void setPvp(boolean enabled) // Sets PvP game rule
void setFallDamage(boolean enabled) // Sets fall damage game rule
void setHungerEnabled(boolean enabled) // Sets whether hunger changes are allowed for registered players in this game world
void addPoint(GameLocation location, String pointName) // Adds a named point
void removePoint(GameLocation location, String pointName) // Removes a named point
void addRegion(GameRegion region, String regionName) // Adds a named region
void removeRegion(GameRegion region, String regionName) // Removes a named region
List<GameLocation> getPoints(String pointName) // Returns named points
GameLocation getPoint(String pointName) // Returns first named point
double distanceToPoint(GameLocation location, String pointName) // Returns closest distance to instance of a named point
List<GameRegion> getRegions(String regionName) // Returns named regions
GameRegion getRegion(String regionName) // Returns first named region
boolean posInRegion(GameLocation location, String regionName) // Returns whether a location is inside a named region
void setBlock(double x, double y, double z, Material material) // Sets a block to a material
void setBlock(GameLocation location, Material material) // Sets a block to a material
void setBlock(double x, double y, double z, Material material) // Sets a block to block data
void setBlock(GameLocation location, BlockData blockData) // Sets a block to block data
CompletableFuture<Void> fill(double x0, double y0, double z0, double x1, double y1, double z1, Material material) // Fills a region
CompletableFuture<Void> fill(GameLocation pos1, GameLocation pos2, Material material) // Fills a region
CompletableFuture<Void> fill(GameRegion region, Material material) // Fills a region with material
CompletableFuture<Void> fill(double x0, double y0, double z0, double x1, double y1, double z1, BlockData blockData) // Fills a region
CompletableFuture<Void> fill(GameLocation pos1, GameLocation pos2, BlockData blockData) // Fills a region
CompletableFuture<Void> fill(GameRegion region, BlockData blockData) // Fills a region with block data
GameEntity summon(EntityType entityType, GameLocation location) // Summons an entity
GameEntity summon(EntityType entityType, double x, double y, double z) // Summons an entity
GameChest newChest(GameLocation location) // Places a game chest
GameChest newChest(double x, double y, double z) // Places a game chest
```
**GameChest**
```java
void clear() // Clears chest contents
void setLootTable(LootTable lootTable) // Replaces chest contents with generated loot table items using a random seed
void setLootTable(LootTable lootTable, long seed) // Replaces chest contents with generated loot table items using a specified seed
void addLootTable(LootTable lootTable) // Adds generated loot table items using a random seed
void addLootTable(LootTable lootTable, long seed) // Adds generated loot table items using a specified seed
void setItems(Collection<ItemStack> items) // Replaces chest contents with items
void addItems(Collection<ItemStack> items) // Adds items into random empty chest slots, falling back to normal inventory stacking
GameLocation location() // Returns chest location
boolean exists() // Returns whether the chest block exists
GameChest ensurePresent() // Places the chest block if missing
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
double distanceSquared(GameLocation other) // Returns squared distance to another location
double x() // Returns x coordinate
double y() // Returns y coordinate
double z() // Returns z coordinate
double pitch() // Returns pitch
double yaw() // Returns yaw
int getBlockX() // Returns floored x coordinate
int getBlockY() // Returns floored y coordinate
int getBlockZ() // Returns floored z coordinate
```
**GameRegion**
```java
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
boolean teleport(GameLocation location) // Teleports entity to a location
boolean teleport(double x, double y, double z) // Teleports entity to a location from coords
boolean teleport(double x, double y, double z, double pitch, double yaw) // Teleports entity to a location from position coords and rotation
void remove() // Removes entity
void kill() // Kills and removes entity
void setHealth(double health) // Sets health if damageable
void damage(double damage) // Damages entity if damageable
void heal() // Heals entity if damageable to their max health
void heal(double health) // Heals entity by a specified amount
void addEffect(PotionEffectType effect) // Adds default potion effect
void addEffect(PotionEffectType effect, int ticks) // Adds potion effect for ticks
void addEffect(PotionEffectType effect, int ticks, int amplifier) // Adds potion effect with amplifier
void addEffect(PotionEffectType effect, int ticks, int amplifier, boolean hideParticles) // Adds potion effect with particle option
void clearEffects(PotionEffectType effect) // Clears instances of that effect from the entity
void clearEffects() // Clears all effects from the entity
double getAttribute(Attribute attribute) // Gets attribute value of an entity, including base and modifiers
void resetAttribute(Attribute attribute) // Resets attribute base to default for that entity and clears modifiers
void setAttributeBase(Attribute attribute, double value) // Sets an attribute base value
double getAttributeBase(Attribute attribute) // Gets attribute base value
void resetAttributeBase(Attribute attribute) // Resets base value for an attribute without clearing modifiers
// Will add more functions later to add/get/remove attribute modifiers
PersistentDataContainer persistentData() // Returns persistent data container
<P, C> void setData(NamespacedKey key, PersistentDataType<P, C> type, C value) // Sets persistent data
<T extends Entity> T as(Class<T> entityClass) // Casts to Bukkit entity type
Entity bukkitEntity() // Returns Bukkit entity
GameWorld world() // Returns entity world
UUID uuid() // Returns entity uuid
EntityType type() // Returns entity type
```

## EVENTS

Put `@GameEventHandler` on a method in the `GameModule`. The method does not need `Listener`, and you do not register or bind it manually. For helper objects, call `registerEventHandlers(helper)` from the module. The parameter type decides the Bukkit event type and the game-scoped wrapper.

```java
@GameEventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onExplosion(GameEvent<EntityExplodeEvent> event) {
    event.blockList().removeIf(location -> !world().posInRegion(location, "mutable"));
}
```

The generic property methods use event method names like `blockList`, `yield`, `damage`, or `clickedBlock`. They try `property`, `getProperty`, and `isProperty`, so `event.locations("blockList")` reaches Paper's `blockList()` and `event.get("yield", Float.class)` reaches `getYield()`.

```java
GameEvent<T extends Event> // Base scoped wrapper for any Bukkit event type
GamePlayerEvent<T extends Event> // Delivers only when the event has a registered GamePlayer
GameEntityEvent<T extends Event> // Delivers only when the event entity is in the game world
GameLocationEvent<T extends Event> // Delivers only when the event location/entity/block is in the game world
GameBlockEvent<T extends Event> // Delivers only when the event block/clicked block is in the game world
```
**GameEventHandler**
```java
EventPriority priority() default EventPriority.NORMAL // Same priority concept as Bukkit events
boolean ignoreCancelled() default false // Skips already-cancelled cancellable events
```
**GameEvent**
```java
boolean isCancelled() // Returns cancellation state for cancellable events, false otherwise
void setCancelled(boolean cancelled) // Sets cancellation state if the wrapped event is cancellable
Class<? extends Event> getEventType() // Returns underlying Bukkit event class
GamePlayer getPlayer() // Returns scoped GamePlayer from getPlayer() or player getEntity(), null if not in this game
GameEntity getEntity() // Returns scoped GameEntity from getEntity() or getPlayer(), null if not in this game world
GameEntity getDamager() // Returns scoped GameEntity from getDamager(), null if not in this game world
GameEntity getProjectile() // Returns scoped projectile entity when supported
GameEntity getHitEntity() // Returns scoped hit entity when supported
GameEntity getRightClicked() // Returns scoped right-clicked entity when supported
GameEntity getVehicle() // Returns scoped vehicle when supported
GameEntity getLeashHolder() // Returns scoped leash holder when supported
GameLocation getLocation() // Returns scoped location from getLocation(), block/clicked block, or entity location
GameLocation getBlockLocation() // Returns scoped block/clicked-block location
GameLocation getClickedBlockLocation() // Returns scoped clicked block location when supported
GameLocation getHitBlockLocation() // Returns scoped hit block location when supported
GameLocation getFrom() // Returns scoped from location for movement/teleport events
GameLocation getTo() // Returns scoped to location for movement/teleport events
void setTo(GameLocation location) // Sets to location inside the game world when supported
List<GameLocation> blockList() // Returns live scoped block list for events like EntityExplodeEvent and BlockExplodeEvent
GameLocation getRespawnLocation() // Returns scoped respawn location when the Bukkit event supports it
void setRespawnLocation(GameLocation location) // Sets respawn location inside the game world when supported
ItemStack getItem() // Returns a clone of getItem() or getItemStack() when supported
ItemStack getItemInHand() // Returns a clone of getItemInHand() when supported
ItemStack getItemDrop() // Returns a clone of dropped item stack when supported
Material getBlockType() // Returns getBlock().getType() when supported
Material getClickedBlockType() // Returns getClickedBlock().getType() when supported
Action getAction() // Returns player interaction action when supported
EquipmentSlot getHand() // Returns interaction hand when supported
BlockFace getBlockFace() // Returns block face when supported
Event.Result getResult() // Returns event result when supported
void setResult(Event.Result result) // Sets event result when supported
double getDamage() // Returns getDamage() when supported, otherwise 0
void setDamage(double damage) // Calls setDamage(double) when supported
double getFinalDamage() // Returns final computed damage when supported
DamageCause getDamageCause() // Returns damage cause when supported
float getYield() // Returns explosion/block drop yield when supported
void setYield(float yield) // Sets explosion/block drop yield when supported
int getDroppedExp() // Returns getDroppedExp() when supported, otherwise 0
void setDroppedExp(int droppedExp) // Calls setDroppedExp(int) when supported
int getExpToDrop() // Returns block exp to drop when supported
void setExpToDrop(int expToDrop) // Sets block exp to drop when supported
int getAmount() // Returns amount for events like PlayerExpChangeEvent when supported
void setAmount(int amount) // Sets amount when supported
int getFoodLevel() // Returns food level when supported
void setFoodLevel(int foodLevel) // Sets food level when supported
int getLevel() // Returns level when supported
int getOldLevel() // Returns old level when supported
int getNewLevel() // Returns new level when supported
int getSlot() // Returns inventory/hotbar slot when supported
int getRawSlot() // Returns raw inventory slot when supported
int getPreviousSlot() // Returns previous held slot when supported
int getNewSlot() // Returns new held slot when supported
boolean shouldDropItems() // Returns drop-items flag when supported
void setDropItems(boolean dropItems) // Sets drop-items flag when supported
boolean getKeepInventory() // Returns keep-inventory flag when supported
void setKeepInventory(boolean keepInventory) // Sets keep-inventory flag when supported
boolean getKeepLevel() // Returns keep-level flag when supported
void setKeepLevel(boolean keepLevel) // Sets keep-level flag when supported
Component getDeathMessage() // Returns death message Component when supported
void setDeathMessage(Component deathMessage) // Calls setDeathMessage(Component) when supported
Component getMessage() // Returns message Component when supported
void setMessage(Component message) // Sets message Component when supported
Component getJoinMessage() // Returns join message when supported
void setJoinMessage(Component joinMessage) // Sets join message when supported
Component getQuitMessage() // Returns quit message when supported
void setQuitMessage(Component quitMessage) // Sets quit message when supported
<V> V get(String property, Class<V> valueType) // Reads safe scalar/enum/Component/ItemStack values, for example get("cause", DamageCause.class)
void set(String property, Object value) // Writes safe scalar/enum/Component/ItemStack values by setter, for example set("yield", 0.5f)
List<GameLocation> locations(String property) // Reads a Location/Block/Entity property or live List of them as GameLocations
List<GameEntity> entities(String property) // Reads an Entity property or live List of entities as GameEntities
List<GamePlayer> players(String property) // Reads a Player property or live List of players as GamePlayers
GameLocation location(String property) // Reads one Location/Block/Entity property as a GameLocation
GameEntity entity(String property) // Reads one Entity property as a GameEntity
GamePlayer player(String property) // Reads one Player property as a GamePlayer
```
**GamePlayerEvent**
```java
GamePlayer getPlayer() // Returns the registered player for this game
```
**GameEntityEvent**
```java
GameEntity getEntity() // Returns the game-scoped event entity
GameEntity getDamager() // Returns the game-scoped damager when supported
```
**GameLocationEvent**
```java
GameLocation getLocation() // Returns the game-scoped event location
```
**GameBlockEvent**
```java
GameLocation getLocation() // Returns the game-scoped block location
```

## BORDERS
**BorderManager**
```java
enum BorderShape {CUBOID, CYLINDROID, ELLIPSOID} // The available 3D geometric border shapes
GameBorder newBorder(GameRegion region) // Creates a cuboid border from a region
GameBorder newBorder(BorderShape shape, GameLocation center, Vector dimensions) // Creates a border with specified shape, center, and dimensions
void setBorderDamage(double damage, int interval) // Sets border damage
void setBorderInterval(int interval) // Sets border damage/tick interval
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
GameBorder setCenter(double x, double y, double z) // Moves center instantly
GameBorder setCenter(GameLocation target, int ticks) // Moves center over time
GameBorder setCenter(double x, double y, double z, int ticks) // Moves center over time
GameBorder setDimensions(Vector target) // Changes dimensions instantly
GameBorder setDimensions(double x, double y, double z) // Changes dimensions instantly
GameBorder setDimensions(Vector target, int ticks) // Changes dimensions over time
GameBorder setDimensions(double x, double y, double z, int ticks) // Changes dimensions over time
boolean containsLocation(GameLocation location) // Returns whether location is inside border
boolean containsLocation(double x, double y, double z) // Returns whether relative coordinates are inside border
BorderShape shape() // Returns border shape
GameLocation center() // Returns border center
Vector dimensions() // Returns border dimensions
boolean isMoving() // Returns whether border is moving or resizing
```
