# Public Module Facing API

**GameModule**
```java
void beforeLoad() // Runs before onLoad() is called. If not overridden, this just defaults to placing a map according to the game's config
void onLoad() // Runs after the game is loaded
void onStart() // Runs when the game first starts
void unload() // Unloads the game module
void onUnload() // Runs when the game unloads
GameData data() // Returns the module's data
MapManager mapManager() // Returns the module's map manager
UIManager uiManager() // Returns the module's UI Manager
PlayerManager playerManager() // Returns the module's player manager
TimeManager timeManager() // Returns the module's time manager
TeamManager teamManager() // Returns the module's team manager
BorderManager borderManager() // Returns the module's border manager
GameWorld world() // Returns this module's active world
```

## MAP SYSTEM

Modules currently assume one active GameWorld. The code still keeps the internal shape needed to support multiple worlds later, but public module code should use `world()` or `mapManager().world()` for now.

**MapManager**
```java
GameMap mapFromId(String id) // Reads a map object from a registered map id without loading it as the active world
CompletableFuture<GameMap> load(String id) // Loads a copy of a world-based map from an id into the module's single active GameWorld
CompletableFuture<GameMap> load(GameMap map) // Loads a copy of a world-based template GameMap into the module's single active GameWorld
CompletableFuture<GameMap> place(String id, GameLocation location) // Places a copy of a schematic-based map from an id into the world, completes when loaded/ready
CompletableFuture<GameMap> place(String id, GameLocation location, MapRotation rotation) // Places a copy of a schematic-based map from an id into the world at a rotation, completes when loaded/ready
CompletableFuture<GameMap> place(GameMap map, GameLocation location) // Similar to above but takes in a GameMap instead, completes when loaded/ready
CompletableFuture<GameMap> place(GameMap map, GameLocation location, MapRotation rotation) // Similar to above but can specify a rotation too
GameMap map() // Returns the current active map
GameWorld world() // Returns the module's active world
```
**GameMap**
```java
String id() // Returns map id
String name() // Returns map display name
List<String> tags() // Returns map tags
Map<String, List<GameLocation>> getPoints() // Returns raw map point data
Map<String, List<GameRegion>> getRegions() // Returns raw map region data
Map<String, Object> metadata() // Returns custom map.yml data from non-reserved keys
Object metadata(String key) // Returns one custom metadata value
Map<String, GameMap> submaps() // Returns embedded submaps by id
GameMap getSubmap(String id) // Returns one embedded submap
GameWorld world() // Returns the world this map was placed into, or null (or the world contained by this map as well if its world backed)
GameLocation origin() // Returns placement origin, or null (a world map is 0, 0)
MapRotation rotation() // Returns placement rotation (a world map is DEG_0)
List<GameLocation> getPoints(String name) // Returns all transformed locations under that point name
GameLocation getPoint(String name) // Returns first transformed location under that point name
List<GameRegion> getRegions(String name) // Returns all transformed regions under that region name
GameRegion getRegion(String name) // Returns first transformed region under that region name
```
**GameWorld**
```java
GameLocation worldSpawn() // Returns the world spawn location (the default location where players respawn if they do not have a respawn point, and the default place they go to when getting teleported to the world for the first time)
void setWorldSpawn(GameLocation spawn) // Sets the world spawn location
Map<String, List<GameLocation>> getPoints() // Returns raw map point data
Map<String, List<GameRegion>> getRegions() // Returns raw map region data
List<GameLocation> getPoints(String name) // Returns all named world points under that specified point name
GameLocation getPoint(String name) // Returns first index of named points under that specified point name
List<GameRegion> getRegions(String name) // Returns all named world regions under that specified region name
GameRegion getRegion(String name) // Returns first index of named points under that specified region name
void addPoint(String name, GameLocation location) // Adds a new named point to the map at that location
void removePoint(String name, GameLocation location) // Removes a named point of specified name at specified location
void addRegion(String name, GameRegion region) // Adds a new named region occupying specified region
void removeRegion(String name, GameRegion region) // Removes a named region of specified name that occupies specified region
boolean regionContainsPos(String regionName, GameLocation location) // Returns whether or not specified location is within any region under specified name
GameWorldGamerules gamerules() // Returns this GameWorld's gamerules object
void setTime(long time) // Sets the time of the GameWorld
long time() // Returns the current time of the GameWorld
enum GameWeather { CLEAR, RAIN, THUNDER } // Basic state for the GameWorld's weather
void setWeather(GameWeather weather) // Sets the weather or the GameWorld
GameWeather weather() // Returns the weather of the GameWorld
void setDifficulty(Difficulty difficulty) // Sets the difficulty of the world
Difficulty difficulty() // Returns the GameWorld's difficulty
void setHungerEnabled(boolean enabled) // Sets whether or not the mechanic of hunger and saturation is enabled in the GameWorld
boolean hungerEnabled() // Returns whether or not hunger is enabled
// Block management (more may be added in future)
CompletableFuture<Void> setBlock(GameLocation location, BlockSpec block) // Sets a block at a location, like /setblock
CompletableFuture<Void> fillBlocks(GameRegion region, BlockSpec block) // Fills a region, like /fill
CompletableFuture<Void> replaceBlocks(GameRegion region, BlockSpec from, BlockSpec to) // Fills a region but only replacing certain blocks
// Entity management (more may be added in future)
CompletableFuture<Void> summon(EntitySpec entity, GameLocation loc) // Summons an entity at a location
```
**GameWorldGamerules**
```java
// Methods will dynamically be generated whenever the API builds, similar to the GameEvents. It will read the gamerules registry, convert the gamerule ids to camelCase.
// If a method returns void and takes in a parameter, like for example void testGamerule(boolean enabled), it is a setter and sets that gamerule
// If a method does not return void and does not take in a parameter, like for example boolean testGamerule(), it is a getter and gets that gamerule's value
```
**GameRegion**
```java
GameRegion(GameLocation min, GameLocation max) // Creates a bounded region from two corners
GameLocation min() // Returns minimum corner
GameLocation max() // Returns maximum corner
GameLocation center() // Returns region center
boolean contains(GameLocation location) // Returns whether the location is inside the region
double sizeX() // Returns region width
double sizeY() // Returns region height
double sizeZ() // Returns region depth
double volume() // Returns sizeX * sizeY * sizeZ
```
**GameLocation**
```java
GameLocation() // Constructs new empty game location at 0, 0, 0 with pitch 0 and yaw 0
GameLocation(double x, double y, double z) // Similar to above but with an x y and z position
GameLocation(double x, double y, double z, double yaw, double pitch) // Similar to above but with a yaw and pitch
double x() // Returns x position
double y() // Returns y position
double z() // Returns z position
double yaw() // Returns yaw
double pitch() // Returns pitch
int blockX() // Returns integer/block grid bound x position
int blockY() // Returns integer/block grid bound y position
int blockZ() // Returns integer/block grid bound z position
```
**MapRotation**
```java
DEG_0
DEG_90
DEG_180
DEG_270
int degrees() // Returns rotation in degrees
static MapRotation fromDegrees(int degrees) // Returns rotation from 0, 90, 180, or 270 degrees (clamped to nearest multiple of 90)
```

## TIME SYSTEM
**TimeManager**
```java
GameTimer newTimer() // Returns and doesn't start a new GameTimer with no max limit
GameTimer newTimer(int ticks) // Returns and doesn't start a new GameTimer with specified max time
void cancelAll() // Cancells all active GameTimers owned by the TimeManager
```
**GameTimer**
```java
GameTimer start() // Starts the timer
boolean isStarted() // Returns whether or not the timer is started
GameTimer setMaxTicks(int ticks) // Sets the max ticks of a timer, can even be called while a timer is running. Setting it to GameTimer.UNLIMITED makes it no max limit (setting the max ticks to a value equal to or below the elapsed ticks will naturally call onFinish)
int maxTicks() // Returns the maxTicks of a timer (returns -1 if unlimited, which is also the value of GameTimer.UNLIMITED)
GameTimer pause() // Pauses the timer
GameTimer resume() // Resumes the timer
boolean isPaused() // Returns whether or not the timer is paused
void cancel() // Cancels the timer
boolean isCancelled() // Returns whether or not the timer is cancelled
int elapsedTicks() // Returns elapsed ticks of the timer
double elapsedSeconds() // Calculates and returns elapsed seconds of the timer
int remainingTicks() // Returns remaining ticks of the timer (-1 if unlimited)
double remainingSeconds() // Calculates and returns remaining seconds of the timer
GameTimer onTick(Consumer<GameTimer> action) // Runs action every 1 tick of the timer, including the first tick 0
GameTimer onTick(int interval, Consumer<GameTimer> action) // Runs action every interval ticks, including 0
GameTimer onFinish(Consumer<GameTimer> action) // Runs action upon the timer finishing by reaching or surpassing its max ticks
boolean isFinished() // Returns whether or not the timer is finished
```

## UI SYSTEM
**UIManager**
```java
void title(GamePlayer player, Component title) // Shows a title on one player's screen
void title(Collection<GamePlayer> players, Component title) // Shows a title on players' screens
void subtitle(GamePlayer player, Component subtitle) // Shows a subtitle on one player's screen (uses packet tracking so it can display even when no title is currently visible)
void subtitle(Collection<GamePlayer> players, Component subtitle) // Shows a subtitle on players' screens
void actionbar(GamePlayer player, Component actionbar) // Shows an actionbar on one player's screen
void actionbar(Collection<GamePlayer> players, Component actionbar) // Shows an actionbar on players' screens
void chat(GamePlayer player, Component message) // Sends a chat message to one player
void chat(Collection<GamePlayer> players, Component message) // Sends a chat message to players
void gameMessage(GamePlayer player, Component message) // Sends a formatted "game message" to one player's chat
void gameMessage(Collection<GamePlayer> players, Component message) // Sends a formatted "game message" to players' chat
void playSound(GamePlayer player, GameSound sound) // Plays a sound for one player
void playSound(Collection<GamePlayer> players, GameSound sound) // Plays a sound for players
GameSidebar newSidebar() // Creates a new sidebar
List<GameSidebar> sidebars() // Returns active sidebars
```
**GameSound**
```java
static GameSound of(Sound sound) // Creates a sound with default MASTER category, player location, 1 volume, 1 pitch, and 0 min volume
GameSound category(SoundCategory category) // Returns a copy using the specified sound category
GameSound location(GameLocation location) // Returns a copy using the specified location; null uses the receiving player's current location
GameSound volume(float volume) // Returns a copy using the specified volume
GameSound pitch(float pitch) // Returns a copy using the specified pitch
GameSound minVolume(float minVolume) // Returns a copy using the specified cross-world fallback/minimum volume
```
**GameSidebar**
```java
GameSidebar setViewers(Collection<GamePlayer> viewers) // Sets the viewers of the sidebar (by default a sidebar's viewers is supplied from the module's playerManager.getPlayers())
GameSidebar setViewers(Supplier<Collection<GamePlayer>> viewers) // Sets the viewers similar to above but explicitly supplying it
GameSidebar setTitle(Component title) // Sets the title of the sidebar (by default it uses the game's name in gold and bold)
GameSidebar setTitle(Supplier<Component> title) // Similar to above but supplies it
GameSidebar addEntry(SidebarEntry entry) // Adds a sidebar entry to the end sidebar
GameSidebar insertEntry(int index, SidebarEntry entry) // Inserts an entry to a certain position in the sidebar
GameSidebar setEntry(int index, SidebarEntry entry) // Replaces an index of the sidebar entries to a different entry
GameSidebar replaceEntry(SidebarEntry oldEntry, SidebarEntry newEntry) // Replaces first instance of specified entry with a different one
GameSidebar removeEntry(SidebarEntry entry) // Removes first instance of specified entry from the sidebar
GameSidebar removeEntry(int index) // Removes entry from sidebar based on an index
List<SidebarEntry> entries() // Returns the sidebar entries
GameSidebar show() // Shows the sidebar to its viewers, replacing existing sidebars they may have on their screen
GameSidebar hide() // Hides the sidebar from its viewers
boolean visible() // Returns whether or not the sidebar is shown to its viewers
GameSidebar setRefreshInterval(int ticks) // Sets the sidebar refresh interval, defaults to 10 ticks, 0 to disable auto refreshing
GameSidebar refresh() // Refreshes the sidebar manually
```
**SidebarEntry**
```java
// These static factory methods return SidebarEntries with certain formatting constraints
static SidebarEntry blank() // Blank line
static SidebarEntry custom(Component component) // Global non-labeled component
static SidebarEntry custom(Supplier<Component> component) // Global non-labeled supplied component
static SidebarEntry custom(Function<GamePlayer, Component> component) // Player specific non-labeled component
static SidebarEntry integer(String label, int value) // Global integer
static SidebarEntry integer(String label, IntSupplier value) // Global supplied integer
static SidebarEntry integer(String label, Function<GamePlayer, Integer> value) // Player specific component
static SidebarEntry fraction(String label, int numerator, int denominator) // Global fraction
static SidebarEntry fraction(String label, IntSupplier numerator, IntSupplier denominator) // Global supplied fraction
static SidebarEntry fraction(String label, Function<GamePlayer, Integer> numerator, Function<GamePlayer, Integer> denominator) // Player specific fraction
static SidebarEntry time(String label, int ticks) // Global time
static SidebarEntry time(String label, IntSupplier ticks) // Global supplied time
static SidebarEntry time(String label, Function<GamePlayer, Integer> time) // Player specific time
static SidebarEntry component(String label, Component component) // Global component
static SidebarEntry component(String label, Supplier<Component> component) // Global supplied component
static SidebarEntry component(String label, Function<GamePlayer, Component> component) // Player specific component
SidebarEntry setLabel(String label) // Sets (or adds if it doesn't exist) label on the sidebar entry
SidebarEntry setLabel(Supplier<String> label) // Similar to above but supplies it
SidebarEntry setLabel(Function<GamePlayer, String> label) // Similar to above but makes it player specific
SidebarEntry removeLabel() // Removes this entry's label, thus giving it no label
String label() // Returns the sidebar's label
```

## ENTITIES
**GameItem / GameItemBase**
```java
GameItem(Material material) // Wraps a new item of a material
GameItem(ItemStack item) // Wraps a copy of a Bukkit item stack
static GameItem of(Material material) // Creates a new GameItem
static GameItem from(ItemStack item) // Wraps a Bukkit item stack, returning null for null
Material material() // Returns the item's material
int amount() // Returns the item amount
void setAmount(int amount) // Sets the item amount
GameItem configure(Consumer<ItemStack> configuration) // Applies a low-level Bukkit configuration when needed
boolean editMeta(Consumer<ItemMeta> editor) // Edits the item's metadata
boolean isSimilar(GameItem other) // Checks whether two items are similar
ItemStack bukkitItem() // Returns the wrapped Bukkit item stack for Paper/Bukkit interop
ItemStack copyBukkitItem() // Returns a cloned Bukkit item stack
// Common ItemStack wrappers are exposed on GameItemBase, including getType/setType, getAmount/setAmount,
// getItemMeta/setItemMeta/editMeta, enchantment helpers, item flag helpers, and Paper data component methods
// like getData/setData/hasData/unsetData/resetData/copyDataFrom/matchesWithoutData.
```

**GameItemComponent / GameItemComponents**
```java
record GameItemComponent<T>(NamespacedKey key, PersistentDataType<?, T> type)
GameItemComponents.SPECTATOR_MENU // Marks an item as opening the spectator teleport menu for spectators on right click
GameItemComponents.INFINITE_BUILD // Replenishes placed blocks and defaults max stack size to 65 when no max-stack override exists
GameItemComponents.TEAM_SYNC // Recolors the item to the holder's team, then removes itself from the item
GameItemComponents.AUTO_IGNITE // Stores an auto-ignite fuse in ticks for explosive spawn/use or block-placement TNT behavior
T getData(GameItemComponent<T> component) // Reads custom plugin-owned item data from PDC
T getDataOrDefault(GameItemComponent<T> component, T fallback) // Reads custom data with a fallback
boolean hasData(GameItemComponent<T> component) // Checks if custom data is present
void setData(GameItemComponent<T> component, T value) // Stores custom plugin-owned item data
void removeData(GameItemComponent<?> component) // Removes custom plugin-owned item data
```

**GameEntityBase**
```java
// Most of these would have default implementations so not to make GameEntity and GamePlayer too big, but some that really really need overrides will have them
Entity bukkitEntity() // Returns the underlying bukkit entity, hopefully the API will try to make needing the bukkit entity uncommon but its still an option for any case that MIGHT need it
CompletableFuture<Boolean> teleport(GameLocation location) // Teleports this entity to a game location
GameLocation location() // Returns the entity's location
GameWorld world() // Returns the entity's GameWorld
// Health and damage
void setHealth(float health) // Sets the entity's health (clamped to its max health)
void heal() // Heals the entity to its max health
void heal(float amount) // Heals the entity by a certain amount
void damage(float amount) // Damages the entity by a certain amount with the generic damage type
void damage(float amount, DamageSource source) // Damages the entity but with a DamageSource
float health() // Returns the entity's current health
void kill() // Kills the entity (of course death behaves a bit different for players and entities though)
boolean isDead() // Returns whether or not the entity is dead
// Common entity config
void setVelocity(Vectory velocity) // Sets the entity's velocity
Vector velocity() // Returns the entity's velocity
void setCustomName(Component name) // Sets a custom name for the entity
void setCustomNameVisible(boolean visible) // Sets if the custom name is visible or not
Component customName() // Returns the entity's custom name
boolean customNameVisible() // Returns whether or not the entity's custom name is visible
void setInvulnerable(boolean invulnerable) // Sets if the entity is invulnerable or not
boolean isInvulnerable() // Returns whether or not the entity is invulnerable
void setInvisible(boolean invisible) // Sets if the entity is invisible or not
boolean isInvisible() // Returns whether or not the entity is invisible
void setNoGravity(boolean noGravity) // Sets whether or not the entity has no gravity
boolean hasNoGravity() // Returns whether or not the entity has no gravity
void setSilent(boolean silent) // Sets whether or not the entity is silent
boolean isSilent() // Returns whether or not the entity is silent
void setFireTicks(int ticks) // Sets the fire ticks of the entity
int fireTicks() // Returns the fire ticks on the entity
// Glowing
boolean setGlowing(boolean glowing) // Sets whether or not the entity is glowing (just plain old glowing, visible to everyone)
boolean setGlowing(boolean glowing, Collection<GamePlayer> viewers) // Sets whether or not the entity is glowing and who can see the glow
boolean setGlowing(boolean glowing, Supplier<Collection<GamePlayer>> viewers) // Similar to above but supplies who can see the glowing
void setGlowColor(NamedTextColor color) // Normal glowing syncs to the team color as in vanilla, running this overrides it with a custom glow color
// Effects
void addEffect(PotionEffectType effect, int ticks, int amplifier) // Adds a potion effect to an entity with an amplifier
void addEffect(PotionEffectType effect, int ticks, int amplifier, boolean hideParticles) // Similar to above but hides particles, similar to the the true/false at the end of vanilla /effect
void addEffect(PotionEffect effect) // Adds a potion effect you have to construct to the entity
void clearEffects() // Clears all effects of the entity
void clearEffect(PotionEffectType effect) // Clears instances of this specific potion effect type on the entity
void clearEffect(PotionEffect effect) // Similar to above but takes in a PotionEffect and not just a PotionEffectType
boolean hasEffect(PotionEffectType effect) // Returns whether or not the entity has this effect type
boolean hasEffect(PotionEffect effect) // Similar to above but takes in a PotionEffect
List<PotionEffect> effects() // Returns all active effects on the entity
// Attributes
void setAttributeBase(Attribute attribute, double value) // Sets the attribute base of an entity
void resetAttributeBase(Attribute attribute) // Resets the base value of that attribute to its default for that entity
double attributeBase(Attribute attribute) // Returns the value of that attribute's base on the entity
void addModifier(Attribute attribute, AttributeModifier modifier) // Adds an attribute modifier
void removeModifier(Attribute attribute, AttributeModifier modifier) // Removes an attribute modifier
void attributeModifierValue(Attribute attribute, AttributeModifier modifier) // Gets the value of an attribute modifier
List<AttributeModifier> attributeModifiers(Attribute attribute) // Returns all modifiers present for that attribute
double attributeValue(Attribute attribute) // Returns the calculated attribute value for this entity, including base and all modifiers present
// Items (more may be added in future)
void setItem(EquipmentSlot slot, ItemSpec item) // Sets an item in the specified slot like /item replace
void setItem(EquipmentSlot slot, GameItem item) // Similar to above but takes in a GameItem
void clearItems() // Clears all items in all slots of the entity (and inventory as well if its a player)
void clearItems(ItemSpec item) // Similar to above but only clears instances of a certain ItemSpec (that of course with components will make it check for items to clear with even more specifity)
void clearItems(GameItem item) // Similar to above but takes in a GameItem
GameItem getItem(EquipmentSlot slot) // Returns an item from an equipment slot
```
**GameEntity** (implements GameEntityBase)
```java
UUID uuid() // Returns the entity's UUID
```

## PLAYER SYSTEM
**PlayerManager**
```java
Collection<GamePlayer> getPlayers() // Returns all GamePlayers owned by the game
Collection<GamePlayer> getSpectators() // Returns all spectating GamePlayers owned by the game
Collection<GamePlayer> getNonSpectators() // Returns all non spectating GamePlayers owned by the game
```
**GamePlayer** (implements GameEntityBase)
```java
UUID uuid() // Returns the player's bukkit UUID
// Spectator stuff
void setSpectator(boolean spectator) // Sets the player to a spectator or not at their current location (so for example if this is called in an event when a player dies, the player would become a spectator at the location where they died)
void setSpectator(boolean spectator, GameLocation location) // Sets the player to a spectator at the specified location (as in immediately teleporting them to that location once they change from a non spectator to a spectator or vice versa)
boolean isSpectator() // Returns whether or not the player is a spectator; If a player is a spectator they will have a completely separate raw paper/bukkit inventory snapshot and other data while in spectator (like effects, attributes, etc). For example, if a player is in spectator, and an item is given to them, it will be stored in their inventory but they will not see it- but if they are not a spectator they will see it
void respawn() // Respawns the player instantly at their spawnpoint; respawning a player is defined as changing them from a spectator to a non spectator at their spawnpoint or whatever other location specified
void respawn(int ticks) // Respawns the player after a specified amount of ticks at their spawnpoint (respawn(0) is the same as just respawn())
void respawn(GameLocation location) // Respawns the player instantly at a specified custom location instead of just merely the player's spawnpoint
void respawn(int ticks, GameLocation location) // Respawns the palyer after a time delay at a specified location
void respawn(int ticks, Supplier<GameLocation> location) // Respawns the player after a time delay at a supplied location
void cancelRespawn() // Cancels any active respawns and their timers on this player; Calling one of the respawn(...) methods on a player that has an active respawn timer also calls this to cancel prior timers before resetting a new one
GameTimer respawnTimer() // Returns the active respawn timer on the player if it exists
void setSpectatablePlayers(Collection<GamePlayer> players) // Sets players this player can teleport to via their compass when they are a spectator
void setSpectatablePlayers(Supplier<Collection<GamePlayer>> players) // Similar to above but supplies the players directly
void setSpectatableTeams(Collection<GameTeam> teams) // Sets teams this player can access in their spectator menu
void setSpectatableTeams(Supplier<Collection<GameTeam>> teams) // Similar to above but supplies the teams directly
// General methods
void setSpawnPoint(GameLocation location) // Sets a player's spawnpoint at a specified game location
GameLocation spawnPoint() // Returns the player's spawnpoint
GameTeam getTeam() // Returns the player's team (or null if they aren't in one)
void setGameMode(GameMode gameMode) // Sets the player's gamemode
GameMode gameMode() // Returns the player's gamemode
void setHunger(int hunger) // Sets the player's hunger/foodLevel
int hunger() // Returns the player's hunger
void setSaturation(float saturation) // Sets the player's saturation
float saturation() // Returns the player's saturation
void setArrowsInBody(int arrows) // Sets the arrows in a player's body
int arrowsInBody() // Returns the arrows in a player's body
void reset() // Resets the player to the default state, including gamemode, inventory, health, attributes, effects, arrows in body, fire ticks, everything
// Items (more may be added in future)
void giveItem(ItemSpec item) // Gives an item to a player
void giveItem(GameItem item) // Similar to above but takes in a GameItem
List<GameItem> inventory() // Returns a player's inventory
List<GameItem> hotbar() // Kinda like above but constrained to only the hotbar
```

## TEAM SYSTEM
**TeamManager**
```java
GameTeam newTeam() // Creates a new team on fully default settings
GameTeam newColoredTeam() // Creates a new team with preset settings based on tracking current teams and colors
Collection<GameTeam> getTeams() // Returns the teams that currently exist
Collection<GameTeam> getSpectatorTeams() // Returns the teams that have all players spectators
Collection<GameTeam> getNonSpectatorTeams() // Returns the teams that have at least one non spectator player on them
```
**GameTeam**
```java
GameTeam addPlayer(GamePlayer player) // Adds a player to the team
GameTeam removePlayer(GamePlayer player) // Removes a player from the team
GameTeam clearPlayers() // Clears all players from the team
void remove() // Removes the team and also from the team manager
Collection<GamePlayer> getPlayers() // Returns the players on the team
Collection<GamePlayer> getSpectators() // Returns players on the team who are spectators
Collection<GamePlayer> getNonSpectators() // Returns players on the team who are not spectators
boolean allSpectators() // Returns whether or not all players on the team are spectators
// Properties
GameTeam setColor(NamedTextColor color) // Sets the team's color
NamedTextColor color() // Returns the team's color
GameTeam setDisplayName(Component displayName) // Sets the team's display name
Component displayName() // Returns the team's display name
GameTeam setItem(ItemSpec item) // Sets the team's representative item
GameTeam setItem(GameItem item) // Similar to above but takes in a GameItem
GameItem item() // Returns the team's representative item
GameTeam setTeamGlow(boolean glow) // Sets whether or not there is team glow (as in being able to see your teammates glowing in your team color)
boolean teamGlow() // Whether or not team glow is enabled for the team
GameTeam setPrefix(Component prefix) // Sets the team's prefix
Component prefix() // Returns the team's prefix
GameTeam setSuffix(Component suffix) // Sets the team's suffix
Component suffix() // Returns the team's suffix
GameTeam setFriendlyFire(boolean friendlyFire) // Sets whether or not friendly fire is enabled
GameTeam setFriendlyFireDamageTypes(Collection<DamageType> damageTypes) // Sets a list on what damage types are allowed in friendly fire
GameTeam setFriendlyFireKillCredit(boolean friendlyFireCredit) // Sets whether or not kill credit is awarded for a friendly fire kill
boolean friendlyFire() // Returns whether or not this team's friendly fire is enabled
Collection<DamageType> friendlyFireDamageTypes() // Returns the friendly fire damage types
boolean friendlyFireKillCredit() // Returns whether or not kill credit is awarded on killing a teammate
GameTeam setSeeFriendlyInvisibles(boolean seeFriendlyInvisibles) // Sets whether or not teammates can see other invisible teammates
boolean seeFriendlyInvisibles() // Returns whether or not teammates can see other teammates invisible
GameTeam setNametagVisibility(Team.OptionStatus nametagVisibility) // Sets team nametag visibility rule
Team.OptionStatus nametagVisibility() // Returns team nametag visibility rule
GameTeam setCollisionRule(Team.OptionStatus nametagVisibility) // Sets team collision rule
Team.OptionStatus collisionRule() // Returns team collision rule
```
## BORDER SYSTEM
**BorderManager**
```java
enum BorderShape {CUBOID, CYLINDROID, ELLIPSOID} // The available 3D geometric border shapes
GameBorder newBorder(GameRegion region) // Creates a cuboid border from a region
GameBorder newBorder(BorderShape shape, GameLocation center, Vector dimensions) // Creates a border with specified shape, center, and dimensions
void setDamage(double damage) // Sets border damage
void setDamageInterval(int interval) // Sets interval in ticks
Collection<GameBorder> borders() // Returns active borders
void clear() // Removes all borders
double damage() // Returns border damage amount
double damageInterval() // Returns border damage interval
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
