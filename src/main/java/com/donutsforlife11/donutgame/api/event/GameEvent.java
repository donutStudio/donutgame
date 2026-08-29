package com.donutsforlife11.donutgame.api.event;

import java.util.AbstractList;
import java.util.List;
import java.util.function.Function;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.damage.DamageSource;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;

public class GameEvent<T extends Event> {
    private final T event;
    private final GameModule module;

    GameEvent(T event) {
        this(event, null);
    }

    GameEvent(T event, GameModule module) {
        this.event = event;
        this.module = module;
    }

    public boolean isCancelled() {
        return event instanceof Cancellable cancellable && cancellable.isCancelled();
    }

    public void setCancelled(boolean cancelled) {
        if (event instanceof Cancellable cancellable) {
            cancellable.setCancelled(cancelled);
        }
    }

    public Class<? extends Event> getEventType() {
        return event.getClass();
    }

    public GamePlayer getPlayer() {
        Object player = getRaw("player");
        if (player instanceof Player bukkitPlayer) {
            return gamePlayer(bukkitPlayer);
        }
        Object entity = getRaw("entity");
        return entity instanceof Player bukkitPlayer ? gamePlayer(bukkitPlayer) : null;
    }

    public GameEntity getEntity() {
        Object entity = getRaw("entity");
        if (entity instanceof Entity bukkitEntity) {
            return gameEntity(bukkitEntity);
        }
        Object player = getRaw("player");
        return player instanceof Entity bukkitEntity ? gameEntity(bukkitEntity) : null;
    }

    public GameEntity getDamager() {
        Object damager = getRaw("damager");
        if (damager instanceof Entity entity) {
            return deathSafeDamager(gameEntity(entity));
        }
        Object damageSource = getRaw("damageSource");
        if (damageSource instanceof DamageSource source) {
            GameEntity causingEntity = gameEntity(source.getCausingEntity());
            if (causingEntity != null) return deathSafeDamager(causingEntity);
            GameEntity directEntity = gameEntity(source.getDirectEntity());
            if (directEntity != null) return deathSafeDamager(directEntity);
        }
        if (event instanceof PlayerDeathEvent deathEvent) {
            return deathSafeDamager(gamePlayer(deathEvent.getEntity().getKiller()));
        }
        return null;
    }

    public GameEntity getProjectile() {
        return entity("projectile");
    }

    public GameEntity getHitEntity() {
        return entity("hitEntity");
    }

    public GameEntity getRightClicked() {
        return entity("rightClicked");
    }

    public GameEntity getVehicle() {
        return entity("vehicle");
    }

    public GameEntity getLeashHolder() {
        return entity("leashHolder");
    }

    public GameLocation getLocation() {
        Object location = getRaw("location");
        if (location instanceof Location bukkitLocation) {
            return gameLocation(bukkitLocation);
        }
        GameLocation blockLocation = getBlockLocation();
        if (blockLocation != null) {
            return blockLocation;
        }
        GameEntity entity = getEntity();
        return entity == null ? null : entity.location();
    }

    public GameLocation getBlockLocation() {
        Object block = getRaw("block");
        Block bukkitBlock = block instanceof Block directBlock ? directBlock : null;
        if (bukkitBlock == null) {
            block = getRaw("clickedBlock");
            if (!(block instanceof Block clickedBlock)) {
                return null;
            }
            bukkitBlock = clickedBlock;
        }
        return gameBlock(bukkitBlock);
    }

    public GameLocation getClickedBlockLocation() {
        return location("clickedBlock");
    }

    public GameLocation getHitBlockLocation() {
        return location("hitBlock");
    }

    public GameLocation getFrom() {
        return location("from");
    }

    public GameLocation getTo() {
        return location("to");
    }

    public void setTo(GameLocation location) {
        setBukkitLocation("to", location);
    }

    public List<GameLocation> blockList() {
        return locations("blockList");
    }

    public GameLocation getRespawnLocation() {
        Object location = getRaw("respawnLocation");
        return location instanceof Location bukkitLocation ? gameLocation(bukkitLocation) : null;
    }

    public void setRespawnLocation(GameLocation location) {
        setBukkitLocation("respawnLocation", location);
    }

    public ItemStack getItem() {
        Object item = getRaw("item");
        ItemStack itemStack = item instanceof ItemStack directItem ? directItem : null;
        if (itemStack == null) {
            item = getRaw("itemStack");
            if (!(item instanceof ItemStack fallback)) {
                return null;
            }
            itemStack = fallback;
        }
        return itemStack.clone();
    }

    public ItemStack getItemInHand() {
        Object item = getRaw("itemInHand");
        return item instanceof ItemStack itemStack ? itemStack.clone() : null;
    }

    public ItemStack getItemDrop() {
        Object item = getRaw("itemDrop");
        if (item instanceof org.bukkit.entity.Item itemEntity) {
            return itemEntity.getItemStack().clone();
        }
        return null;
    }

    public Material getBlockType() {
        Object block = getRaw("block");
        return block instanceof Block bukkitBlock ? bukkitBlock.getType() : null;
    }

    public Material getClickedBlockType() {
        Object block = getRaw("clickedBlock");
        return block instanceof Block bukkitBlock ? bukkitBlock.getType() : null;
    }

    public Action getAction() {
        return get("action", Action.class);
    }

    public EquipmentSlot getHand() {
        return get("hand", EquipmentSlot.class);
    }

    public BlockFace getBlockFace() {
        return get("blockFace", BlockFace.class);
    }

    public Result getResult() {
        return get("result", Result.class);
    }

    public void setResult(Result result) {
        set("result", result);
    }

    public int getDroppedExp() {
        return getInt("droppedExp", 0);
    }

    public void setDroppedExp(int droppedExp) {
        set("droppedExp", droppedExp);
    }

    public int getExpToDrop() {
        return getInt("expToDrop", 0);
    }

    public void setExpToDrop(int expToDrop) {
        set("expToDrop", expToDrop);
    }

    public int getAmount() {
        return getInt("amount", 0);
    }

    public void setAmount(int amount) {
        set("amount", amount);
    }

    public int getFoodLevel() {
        return getInt("foodLevel", 20);
    }

    public void setFoodLevel(int foodLevel) {
        set("foodLevel", foodLevel);
    }

    public int getLevel() {
        return getInt("level", 0);
    }

    public int getOldLevel() {
        return getInt("oldLevel", 0);
    }

    public int getNewLevel() {
        return getInt("newLevel", 0);
    }

    public int getSlot() {
        return getInt("slot", -1);
    }

    public int getRawSlot() {
        return getInt("rawSlot", -1);
    }

    public int getPreviousSlot() {
        return getInt("previousSlot", -1);
    }

    public int getNewSlot() {
        return getInt("newSlot", -1);
    }

    public double getDamage() {
        return getDouble("damage", 0);
    }

    public void setDamage(double damage) {
        set("damage", damage);
    }

    public double getFinalDamage() {
        return getDouble("finalDamage", 0);
    }

    public DamageCause getDamageCause() {
        return get("cause", DamageCause.class);
    }

    public float getYield() {
        Object value = getRaw("yield");
        return value instanceof Number number ? number.floatValue() : 0;
    }

    public void setYield(float yield) {
        set("yield", yield);
    }

    public boolean shouldDropItems() {
        return getBoolean("dropItems", false);
    }

    public void setDropItems(boolean dropItems) {
        set("dropItems", dropItems);
    }

    public boolean getKeepInventory() {
        return getBoolean("keepInventory", false);
    }

    public void setKeepInventory(boolean keepInventory) {
        set("keepInventory", keepInventory);
    }

    public boolean getKeepLevel() {
        return getBoolean("keepLevel", false);
    }

    public void setKeepLevel(boolean keepLevel) {
        set("keepLevel", keepLevel);
    }

    public Component getDeathMessage() {
        return get("deathMessage", Component.class);
    }

    public void setDeathMessage(Component deathMessage) {
        set("deathMessage", deathMessage);
    }

    public void clearDrops() {
        if (event instanceof PlayerDeathEvent deathEvent) deathEvent.getDrops().clear();
    }

    public Component getMessage() {
        return get("message", Component.class);
    }

    public void setMessage(Component message) {
        set("message", message);
    }

    public Component getJoinMessage() {
        return get("joinMessage", Component.class);
    }

    public void setJoinMessage(Component joinMessage) {
        set("joinMessage", joinMessage);
    }

    public Component getQuitMessage() {
        return get("quitMessage", Component.class);
    }

    public void setQuitMessage(Component quitMessage) {
        set("quitMessage", quitMessage);
    }

    public <V> V get(String property, Class<V> valueType) {
        Object value = getRaw(property);
        return safeValue(value, valueType);
    }

    private int getInt(String property, int fallback) {
        Object value = getRaw(property);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double getDouble(String property, double fallback) {
        Object value = getRaw(property);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean getBoolean(String property, boolean fallback) {
        Object value = getRaw(property);
        return value instanceof Boolean bool ? bool : fallback;
    }

    public void set(String property, Object value) {
        if (!isSafeValue(value)) {
            throw new IllegalArgumentException("Only scalar, enum, Component, and ItemStack values can be passed through game events.");
        }
        if (!GameEventRegistrar.invokeSetter(event, setterName(property), value)) {
            GameEventRegistrar.invokeSetter(event, property, value);
        }
    }

    public List<GameLocation> locations(String property) {
        Object value = getRaw(property);
        if (value instanceof List<?> list) {
            return scopedList(list, this::toGameLocation);
        }
        GameLocation location = toGameLocation(value);
        return location == null ? List.of() : List.of(location);
    }

    public List<GameEntity> entities(String property) {
        Object value = getRaw(property);
        if (value instanceof List<?> list) {
            return scopedList(list, this::toGameEntity);
        }
        GameEntity entity = toGameEntity(value);
        return entity == null ? List.of() : List.of(entity);
    }

    public List<GamePlayer> players(String property) {
        Object value = getRaw(property);
        if (value instanceof List<?> list) {
            return scopedList(list, this::toGamePlayer);
        }
        GamePlayer player = toGamePlayer(value);
        return player == null ? List.of() : List.of(player);
    }

    public GameLocation location(String property) {
        return toGameLocation(getRaw(property));
    }

    public GameEntity entity(String property) {
        return toGameEntity(getRaw(property));
    }

    public GamePlayer player(String property) {
        return toGamePlayer(getRaw(property));
    }

    protected final T bukkitEvent() {
        return event;
    }

    public final T event() {
        return event;
    }

    protected final GameModule module() {
        return module;
    }

    protected final GamePlayer gamePlayer(Player player) {
        if (module == null || player == null) {
            return null;
        }
        return module.playerManager().getPlayer(player);
    }

    protected final GameEntity gameEntity(Entity entity) {
        if (module == null || module.world() == null || entity == null || !module.world().contains(entity)) {
            return null;
        }
        return module.world().entity(entity);
    }

    protected final GameLocation gameLocation(Location location) {
        if (module == null || module.world() == null || location == null || !module.world().contains(location)) {
            return null;
        }
        return GameLocation.fromBukkit(location);
    }

    protected final GameLocation gameBlock(Block block) {
        return block == null ? null : gameLocation(block.getLocation());
    }

    protected final Object getRaw(String property) {
        return GameEventRegistrar.invokeGetter(event, getterNames(property));
    }

    private void setBukkitLocation(String property, GameLocation location) {
        if (location == null || module == null) {
            return;
        }
        GameEventRegistrar.invokeSetter(event, setterName(property), location.toBukkit(module.world().bukkitWorld()));
    }

    private <V> V safeValue(Object value, Class<V> valueType) {
        if (value == null || valueType == null) {
            return null;
        }
        if (value instanceof ItemStack itemStack && valueType.isAssignableFrom(ItemStack.class)) {
            return valueType.cast(itemStack.clone());
        }
        if (!isSafeValue(value) || !valueType.isInstance(value)) {
            return null;
        }
        return valueType.cast(value);
    }

    private boolean isSafeValue(Object value) {
        return value == null
            || value instanceof String
            || value instanceof Number
            || value instanceof Boolean
            || value instanceof Character
            || value instanceof Enum<?>
            || value instanceof Component
            || value instanceof ItemStack;
    }

    private GameLocation toGameLocation(Object value) {
        if (value instanceof Location location) {
            return gameLocation(location);
        }
        if (value instanceof Block block) {
            return gameBlock(block);
        }
        if (value instanceof Entity entity) {
            GameEntity gameEntity = gameEntity(entity);
            return gameEntity == null ? null : gameEntity.location();
        }
        return null;
    }

    private GameEntity toGameEntity(Object value) {
        if (value instanceof Entity entity) {
            return gameEntity(entity);
        }
        return null;
    }

    private GamePlayer toGamePlayer(Object value) {
        if (value instanceof Player player) {
            return gamePlayer(player);
        }
        return null;
    }

    private GameEntity deathSafeDamager(GameEntity candidate) {
        if (!(event instanceof PlayerDeathEvent deathEvent) || !(candidate instanceof GamePlayer attacker)) {
            return candidate;
        }
        GamePlayer target = gamePlayer(deathEvent.getEntity());
        if (target == null || target == attacker) {
            return null;
        }
        GameTeam team = attacker.team();
        return team != null && team == target.team() && !team.friendlyFire() ? null : candidate;
    }

    private <V> List<V> scopedList(List<?> backingList, Function<Object, V> converter) {
        return new AbstractList<>() {
            @Override
            public V get(int index) {
                return converter.apply(backingList.get(backingIndex(index)));
            }

            @Override
            public int size() {
                int size = 0;
                for (Object value : backingList) {
                    if (converter.apply(value) != null) {
                        size++;
                    }
                }
                return size;
            }

            @Override
            public V remove(int index) {
                Object removed = backingList.remove(backingIndex(index));
                return converter.apply(removed);
            }

            @Override
            public void clear() {
                backingList.removeIf(value -> converter.apply(value) != null);
            }

            private int backingIndex(int visibleIndex) {
                int currentVisibleIndex = 0;
                for (int backingIndex = 0; backingIndex < backingList.size(); backingIndex++) {
                    if (converter.apply(backingList.get(backingIndex)) == null) {
                        continue;
                    }
                    if (currentVisibleIndex == visibleIndex) {
                        return backingIndex;
                    }
                    currentVisibleIndex++;
                }
                throw new IndexOutOfBoundsException(visibleIndex);
            }
        };
    }

    private String[] getterNames(String property) {
        String suffix = capitalize(property);
        return new String[] {
            property,
            "get" + suffix,
            "is" + suffix
        };
    }

    private String setterName(String property) {
        return "set" + capitalize(property);
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
