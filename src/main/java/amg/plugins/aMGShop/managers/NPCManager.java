package amg.plugins.aMGShop.managers;

import amg.plugins.aMGShop.AMGShop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.NamespacedKey;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NPCManager implements Listener {
    private final AMGShop plugin;
    private final Map<UUID, String> npcTypes;
    private Villager buyNPC;
    private Villager sellNPC;
    private final NamespacedKey npcTypeKey;
    private static final int NAMETAG_VISIBILITY_DISTANCE = 5; // 5 blocks visibility distance

    public NPCManager(AMGShop plugin) {
        this.plugin = plugin;
        this.npcTypes = new HashMap<>();
        this.npcTypeKey = new NamespacedKey(plugin, "npc_type");
        
        // Register player move event to handle nametag visibility
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Send a debug message if debug mode is enabled
     * @param message The message to send
     */
    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[Debug] " + message);
        }
    }
    
    // Event handlers to protect our NPCs
    
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Cancel damage to our NPCs
        if (isShopNPC(event.getEntity())) {
            event.setCancelled(true);
            debug("Cancelled damage to shop NPC: " + event.getEntity().getUniqueId());
        }
    }
    
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        // Keep our NPCs loaded when chunks unload
        for (Entity entity : event.getChunk().getEntities()) {
            if (isShopNPC(entity)) {
                entity.setPersistent(true);
                if (entity instanceof Villager villager) {
                    // Store NPC's exact position and rotation
                    Location loc = villager.getLocation();
                    if (entity.equals(buyNPC)) {
                        saveLocationToConfig("npcs.buy", loc);
                    } else if (entity.equals(sellNPC)) {
                        saveLocationToConfig("npcs.sell", loc);
                    }
                }
                debug("Made shop NPC persistent during chunk unload: " + entity.getUniqueId());
            }
        }
    }
    
    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        // Run NPC spawning in the next tick to ensure thread safety
        Bukkit.getScheduler().runTask(plugin, () -> {
            synchronized (this) {
                // Check if our NPCs should be in this chunk
                Location buyLoc = getLocationFromConfig("npcs.buy");
                Location sellLoc = getLocationFromConfig("npcs.sell");
                
                if (buyLoc != null && buyLoc.getChunk().equals(event.getChunk()) && (buyNPC == null || !buyNPC.isValid())) {
                    // Respawn buy NPC if it should be in this chunk but isn't
                    String name = plugin.getConfig().getString("npcs.buy.name", "&6Shop Keeper").replace("&", "§");
                    Villager.Type villagerType = getVillagerType(plugin.getConfig().getString("npcs.buy.villager_type", "PLAINS"));
                    Villager.Profession profession = getVillagerProfession(plugin.getConfig().getString("npcs.buy.profession", "LIBRARIAN"));
                    buyNPC = createNPC(buyLoc, name, "buy", villagerType, profession);
                    debug("Respawned Buy NPC in loaded chunk at " + formatLocation(buyLoc));
                }
                
                if (sellLoc != null && sellLoc.getChunk().equals(event.getChunk()) && (sellNPC == null || !sellNPC.isValid())) {
                    // Respawn sell NPC if it should be in this chunk but isn't
                    String name = plugin.getConfig().getString("npcs.sell.name", "&eMerchant").replace("&", "§");
                    Villager.Type villagerType = getVillagerType(plugin.getConfig().getString("npcs.sell.villager_type", "DESERT"));
                    Villager.Profession profession = getVillagerProfession(plugin.getConfig().getString("npcs.sell.profession", "MASON"));
                    sellNPC = createNPC(sellLoc, name, "sell", villagerType, profession);
                    debug("Respawned Sell NPC in loaded chunk at " + formatLocation(sellLoc));
                }
            }
        });
    }
    
    // Handle player movement to update nametag visibility based on distance
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Only check every few blocks to reduce performance impact
        if (event.getFrom().distanceSquared(event.getTo()) < 4) { // 2 blocks squared
            return;
        }
        
        updateNametagVisibilityForPlayer(player);
    }
    
    // Update nametag visibility when players join
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Schedule a delayed task to update nametag visibility after the player fully joins
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateNametagVisibilityForPlayer(player);
            debug("Updated nametag visibility for joining player: " + player.getName());
        }, 20L); // 1 second delay
    }
    
    // Update nametag visibility based on player distance to NPCs
    private void updateNametagVisibilityForPlayer(Player player) {
        // Skip if nametags are completely hidden
        if (plugin.getConfig().getBoolean("npcs.hide_nametag", false)) {
            return;
        }
        
        // Check buy NPC
        if (buyNPC != null && buyNPC.isValid()) {
            boolean isClose = player.getLocation().distance(buyNPC.getLocation()) <= NAMETAG_VISIBILITY_DISTANCE;
            buyNPC.setCustomNameVisible(isClose);
            debug("Buy NPC nametag visibility for " + player.getName() + ": " + isClose);
        }
        
        // Check sell NPC
        if (sellNPC != null && sellNPC.isValid()) {
            boolean isClose = player.getLocation().distance(sellNPC.getLocation()) <= NAMETAG_VISIBILITY_DISTANCE;
            sellNPC.setCustomNameVisible(isClose);
            debug("Sell NPC nametag visibility for " + player.getName() + ": " + isClose);
        }
    }

    public void loadNPCs() {
        try {
            // Remove any existing NPCs first
            removeNPCs();
            
            // Remove any existing shop NPCs in all worlds
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Villager && isShopNPC(entity)) {
                        entity.remove();
                        debug("Removed leftover shop NPC: " + entity.getUniqueId());
                    }
                }
            }
            
            // Load Buy NPC if it exists in config
            if (plugin.getConfig().contains("npcs.buy.location")) {
                Location location = getLocationFromConfig("npcs.buy");
                if (location != null && location.getWorld() != null) {
                    // Ensure chunk is loaded
                    if (!location.getChunk().isLoaded()) {
                        location.getChunk().load();
                        debug("Loaded chunk for Buy NPC at " + formatLocation(location));
                    }
                    
                    String name = plugin.getConfig().getString("npcs.buy.name", "&6Shop Keeper").replace("&", "§");
                    Villager.Type villagerType = getVillagerType(plugin.getConfig().getString("npcs.buy.villager_type", "PLAINS"));
                    Villager.Profession profession = getVillagerProfession(plugin.getConfig().getString("npcs.buy.profession", "LIBRARIAN"));
                    
                    buyNPC = createNPC(location, name, "buy", villagerType, profession);
                    debug("Loaded Buy NPC at " + formatLocation(location));
                } else {
                    plugin.getLogger().warning("Failed to load Buy NPC - Invalid location or world");
                }
            }
            
            // Load Sell NPC if it exists in config
            if (plugin.getConfig().contains("npcs.sell.location")) {
                Location location = getLocationFromConfig("npcs.sell");
                if (location != null && location.getWorld() != null) {
                    // Ensure chunk is loaded
                    if (!location.getChunk().isLoaded()) {
                        location.getChunk().load();
                        debug("Loaded chunk for Sell NPC at " + formatLocation(location));
                    }
                    
                    String name = plugin.getConfig().getString("npcs.sell.name", "&eMerchant").replace("&", "§");
                    Villager.Type villagerType = getVillagerType(plugin.getConfig().getString("npcs.sell.villager_type", "DESERT"));
                    Villager.Profession profession = getVillagerProfession(plugin.getConfig().getString("npcs.sell.profession", "MASON"));
                    
                    sellNPC = createNPC(location, name, "sell", villagerType, profession);
                    debug("Loaded Sell NPC at " + formatLocation(location));
                } else {
                    plugin.getLogger().warning("Failed to load Sell NPC - Invalid location or world");
                }
            }
            
            // Update nametag visibility for all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateNametagVisibilityForPlayer(player);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error loading NPCs: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Load Sell NPC if it exists in config
        if (plugin.getConfig().contains("npcs.sell.location")) {
            Location location = getLocationFromConfig("npcs.sell");
            if (location != null) {
                String name = plugin.getConfig().getString("npcs.sell.name", "&eMerchant").replace("&", "§");
                Villager.Type villagerType = getVillagerType(plugin.getConfig().getString("npcs.sell.villager_type", "DESERT"));
                Villager.Profession profession = getVillagerProfession(plugin.getConfig().getString("npcs.sell.profession", "MASON"));
                
                sellNPC = createNPC(location, name, "sell", villagerType, profession);
                debug("Loaded Sell NPC at " + formatLocation(location));
            }
        }
        
        // Update nametag visibility for all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateNametagVisibilityForPlayer(player);
        }
    }

    public void createBuyNPC(Location location) {
        // Remove old Buy NPC if it exists
        if (buyNPC != null) {
            buyNPC.remove();
            buyNPC = null;
            debug("Removed old Buy NPC");
        }

        // Create new Buy NPC
        String name = plugin.getConfig().getString("npcs.buy.name", "&6Shop Keeper").replace("&", "§");
        Villager.Type villagerType = getVillagerType(plugin.getConfig().getString("npcs.buy.villager_type", "PLAINS"));
        Villager.Profession profession = getVillagerProfession(plugin.getConfig().getString("npcs.buy.profession", "LIBRARIAN"));
        
        buyNPC = createNPC(location, name, "buy", villagerType, profession);
        
        // Save location to config
        saveLocationToConfig("npcs.buy", location);
        debug("Created Buy NPC at " + formatLocation(location));
        
        // Update nametag visibility for all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateNametagVisibilityForPlayer(player);
        }
    }

    public void createSellNPC(Location location) {
        // Remove old Sell NPC if it exists
        if (sellNPC != null) {
            sellNPC.remove();
            sellNPC = null;
            debug("Removed old Sell NPC");
        }

        // Create new Sell NPC
        String name = plugin.getConfig().getString("npcs.sell.name", "&eMerchant").replace("&", "§");
        Villager.Type villagerType = getVillagerType(plugin.getConfig().getString("npcs.sell.villager_type", "DESERT"));
        Villager.Profession profession = getVillagerProfession(plugin.getConfig().getString("npcs.sell.profession", "MASON"));
        
        sellNPC = createNPC(location, name, "sell", villagerType, profession);
        
        // Save location to config
        saveLocationToConfig("npcs.sell", location);
        debug("Created Sell NPC at " + formatLocation(location));
        
        // Update nametag visibility for all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateNametagVisibilityForPlayer(player);
        }
    }
    
    private Villager createNPC(Location location, String name, String type, Villager.Type villagerType, Villager.Profession profession) {
        // Make sure chunk is loaded before spawning
        if (!location.getChunk().isLoaded()) {
            location.getChunk().load();
        }
        
        // Remove any existing NPCs at this location
        location.getWorld().getNearbyEntities(location, 1, 1, 1).forEach(entity -> {
            if (entity instanceof Villager && isShopNPC(entity)) {
                entity.remove();
                debug("Removed duplicate NPC at spawn location: " + entity.getUniqueId());
            }
        });
        
        Villager npc = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        
        // Set basic properties
        npc.customName(Component.text(name));
        npc.setCustomNameVisible(false);  // Initially hidden until player is close
        npc.setAI(false);
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.setVillagerType(villagerType);
        npc.setProfession(profession);
        npc.setVillagerLevel(5); // Max level
        npc.setPersistent(true); // Make sure NPC persists through chunk unloads
        npc.setRemoveWhenFarAway(false); // Prevent automatic removal
        npc.setCollidable(false); // Prevent pushing
        
        // Set exact position and rotation
        npc.teleport(location);
        
        // Store NPC type in persistent data
        PersistentDataContainer pdc = npc.getPersistentDataContainer();
        pdc.set(npcTypeKey, PersistentDataType.STRING, type);
        
        // Store in our map for easy lookup
        npcTypes.put(npc.getUniqueId(), type);
        
        debug("Created " + type + " NPC with ID: " + npc.getUniqueId());
        return npc;
    }
    
    private void saveLocationToConfig(String path, Location location) {
        plugin.getConfig().set(path + ".location.world", location.getWorld().getName());
        plugin.getConfig().set(path + ".location.x", location.getX());
        plugin.getConfig().set(path + ".location.y", location.getY());
        plugin.getConfig().set(path + ".location.z", location.getZ());
        plugin.getConfig().set(path + ".location.yaw", location.getYaw());
        plugin.getConfig().set(path + ".location.pitch", location.getPitch());
        plugin.saveConfig();
        debug("Saved location to config: " + path);
    }
    
    private Location getLocationFromConfig(String path) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path + ".location");
        if (section == null) return null;
        
        String worldName = section.getString("world");
        if (worldName == null) return null;
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");
        
        Location location = new Location(world, x, y, z, yaw, pitch);
        debug("Loaded location from config: " + path + " = " + formatLocation(location));
        return location;
    }
    
    private String formatLocation(Location location) {
        return String.format("(World: %s, X: %.2f, Y: %.2f, Z: %.2f)", 
            location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
    }
    
    private Villager.Type getVillagerType(String typeName) {
        try {
            return Villager.Type.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            debug("Invalid villager type: " + typeName + ", using PLAINS instead");
            return Villager.Type.PLAINS;
        }
    }
    
    private Villager.Profession getVillagerProfession(String professionName) {
        try {
            return Villager.Profession.valueOf(professionName.toUpperCase());
        } catch (IllegalArgumentException e) {
            debug("Invalid villager profession: " + professionName + ", using LIBRARIAN instead");
            return Villager.Profession.LIBRARIAN;
        }
    }

    public String getNPCType(UUID npcUUID) {
        return npcTypes.get(npcUUID);
    }
    
    public boolean isShopNPC(Entity entity) {
        if (entity == null) return false;
        
        // Check if it's one of our NPCs by UUID
        if (npcTypes.containsKey(entity.getUniqueId())) {
            return true;
        }
        
        // Check persistent data as a backup
        if (entity instanceof Villager) {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            return pdc.has(npcTypeKey, PersistentDataType.STRING);
        }
        
        return false;
    }
    
    public String getNPCTypeFromEntity(Entity entity) {
        if (entity == null) return null;
        
        // Check our map first for efficiency
        String type = npcTypes.get(entity.getUniqueId());
        if (type != null) {
            return type;
        }
        
        // Check persistent data as a backup
        if (entity instanceof Villager) {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            if (pdc.has(npcTypeKey, PersistentDataType.STRING)) {
                return pdc.get(npcTypeKey, PersistentDataType.STRING);
            }
        }
        
        return null;
    }

    public void removeNPCs() {
        if (buyNPC != null) {
            buyNPC.remove();
            buyNPC = null;
            debug("Removed Buy NPC");
        }
        if (sellNPC != null) {
            sellNPC.remove();
            sellNPC = null;
            debug("Removed Sell NPC");
        }
        npcTypes.clear();
    }
    
    public void updateNameTagVisibility() {
        boolean hideNametag = plugin.getConfig().getBoolean("npcs.hide_nametag", false);
        debug("Updating nametag visibility, hide_nametag=" + hideNametag);
        
        // If nametags are completely hidden, update all NPCs
        if (hideNametag) {
            if (buyNPC != null) {
                buyNPC.setCustomNameVisible(false);
            }
            
            if (sellNPC != null) {
                sellNPC.setCustomNameVisible(false);
            }
        } else {
            // Otherwise, update based on player distance
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateNametagVisibilityForPlayer(player);
            }
        }
    }
}