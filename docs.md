# Public Module Facing API

**GameModule**
```java
void beforeLoad() // Runs before onLoad() is called. If not overridden, this just defaults to placing a map according to the game's config
void onLoad() // Runs after the game is loaded
void onStart() // Runs when the game first starts
void unload() // Unloads the game module
void onUnload() // Runs when the game unloads
MapManager mapManager() // Returns the module's map manager
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
