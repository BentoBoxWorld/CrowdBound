package world.bentobox.crowdbound.generators;

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.PortalType;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.util.ExpiringSet;
import world.bentobox.bentobox.util.Pair;
import world.bentobox.crowdbound.CrowdBound;
import world.bentobox.crowdbound.database.NetherChunksMade;

public class NetherChunkMaker implements Listener {

    private static final int ROOF_HEIGHT = 107;
    private CrowdBound addon;
    private Random rand = new Random();
    private final Database<NetherChunksMade> handler;
    private NetherChunksMade netherChunksMade;
    private final int maxChestFills;
    private ExpiringSet<UUID> portalPlayer = new ExpiringSet<>(10, TimeUnit.SECONDS);

    public NetherChunkMaker(CrowdBound addon) {
        super();
        this.addon = addon;
        handler = new Database<>(addon, NetherChunksMade.class);
        netherChunksMade = handler.loadObject("NetherChunks");
        if (netherChunksMade == null) {
            netherChunksMade = new NetherChunksMade();
            handler.saveObjectAsync(netherChunksMade);
        }
        maxChestFills = addon.getSettings().getChestFills();
    }
 
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onNetherPortalEnter(EntityPortalEnterEvent e) {
        // Only trigger if the player is going from the overworld to the nether
        if (e.getPortalType() != PortalType.NETHER 
                || e.getEntityType() != EntityType.PLAYER
                || !addon.inWorld(e.getLocation())
                || portalPlayer.contains(e.getEntity().getUniqueId()) // If they are in the map, ignore
                ) {
            return;
        }
        Player p = (Player)e.getEntity();
        // Add the player as teleporting
        portalPlayer.add(p.getUniqueId());
        if (e.getLocation().getWorld().getEnvironment() == Environment.NETHER) {
            return;
        }
        
        if (CrowdBound.isWarpedCompass(p.getInventory().getItemInMainHand()) 
                || CrowdBound.isWarpedCompass(p.getInventory().getItemInOffHand())) {
            // Refresh the nether!
            int chunkRadius = Bukkit.getViewDistance();
            int x = p.getLocation().getChunk().getX();
            int z = p.getLocation().getChunk().getZ();
            // Removing the listing of chunks from the database will cause them to be re-made
            for (int i = x - chunkRadius; i < x + chunkRadius; i++) {
                for (int j = z - chunkRadius; j < z + chunkRadius; j++) {
                    this.netherChunksMade.getChunkSet().remove(Pair.of(i, j));
                }
            }
            handler.saveObject(netherChunksMade);
            User.getInstance(p).sendMessage("crowdbound.nether.refresh");
            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> p.playSound(p, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1F, 1F));
            // Get the item in the main hand
            ItemStack mainHandItem = p.getInventory().getItemInMainHand();

            if (CrowdBound.isWarpedCompass(mainHandItem)) {
                // Reduce the amount by 1. If the new amount is 0, Bukkit automatically sets the slot to null.
                mainHandItem.subtract(1); 
                return;
            } 

            // If not in the main hand, check the off-hand
            ItemStack offHandItem = p.getInventory().getItemInOffHand();

            if (CrowdBound.isWarpedCompass(offHandItem)) {
                // Reduce the amount by 1.
                offHandItem.subtract(1);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (e.getWorld().getEnvironment() != Environment.NETHER 
                || !addon.getSettings().isUseUpsideDown()
                || !addon.inWorld(e.getWorld())) {
            return;
        }
        if (!netherChunksMade.getChunkSet().add(Pair.of(e.getChunk().getX(), e.getChunk().getZ()))) {
            return;
        }
        int chestFills = 0;
        handler.saveObjectAsync(netherChunksMade); // Save to database

        // Get the overworld chunk we are copying from
        Chunk overworldChunk = addon.getOverWorld().getChunkAt(e.getChunk().getX(), e.getChunk().getZ());
        // Determine the attrition
        int rawAttritionValue = addon.getSettings().getAttrition();
        double attrition = (rawAttritionValue >= 0 && rawAttritionValue <= 100)
                // If TRUE: Calculate the percentage (using 100.0 for double division).
                ? rawAttritionValue / 100.0
                        // If FALSE: Use the default 5% (0.05).
                        : 0.05;

        // Remove any tile entity contents
        Arrays.stream(e.getChunk().getTileEntities())
        .filter(en -> en.getLocation().getBlockY() < ROOF_HEIGHT)
        .forEach(tileEntity -> {
            // Check if the tile entity is an InventoryHolder (like a chest, furnace, etc.)
            if (tileEntity instanceof InventoryHolder ih) {
                // Get the inventory and clear its contents
                ih.getInventory().clear();
            }
        });
        // Removed any entities in this chunk - they will be replaced
        Arrays.stream(e.getChunk().getEntities())
        .filter(en -> en.getType() != EntityType.PLAYER)
        .filter(en -> en.getLocation().getBlockY() < ROOF_HEIGHT)
        .forEach(Entity::remove);

        // Loop through the chunk and set blocks
        for (int y = e.getWorld().getMinHeight(); y < ROOF_HEIGHT; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Block overworldBlock = overworldChunk.getBlock(x, y, z);
                    Block newBlock = e.getChunk().getBlock(x, y, z);
                    newBlock.setBiome(Biome.BASALT_DELTAS);
                    if (overworldBlock.getType() == newBlock.getType() 
                            || newBlock.getType() == Material.NETHER_PORTAL // We must not touch these otherwise errors occur
                            || y > 100 && rand.nextDouble() < attrition) {
                        continue;
                    }
                    BlockData bd = overworldBlock.getBlockData();
                    Material material = bd.getMaterial(); // Get the material for the switch
                    BlockData newBlockData = bd.clone(); // Clone the BlockData to modify it

                    // --- Tag-Based Conversion ---
                    if (Tag.BUTTONS.isTagged(material)) {
                        newBlockData = rand.nextBoolean() ? Material.AIR .createBlockData() :  Material.STONE_BUTTON.createBlockData(); // Converts all buttons to stone
                    } else if (Tag.DOORS.isTagged(material) || Tag.FENCE_GATES.isTagged(material)) {
                        newBlockData = Material.AIR.createBlockData(); // Converts all doors to air
                    } else if (Tag.CORAL_BLOCKS.isTagged(material)) {
                        newBlockData = Material.NETHERRACK.createBlockData(); // Converts all coral blocks to netherrack
                    } else if (Tag.LOGS.isTagged(material)) {
                        // Converts all overworld logs to Warped Stem (a Nether log-like material)
                        newBlockData = Material.WARPED_STEM.createBlockData();
                        e.getChunk().getBlock(x, y, z).setBiome(Biome.WARPED_FOREST);
                    } else if (Tag.LEAVES.isTagged(material)) {
                        // Converts all overworld leaves to Nether Wart Block or similar
                        newBlockData = Material.NETHER_WART_BLOCK.createBlockData();
                    } else if (Tag.STONE_BRICKS.isTagged(material)) {
                        // Converts all stone bricks to Nether Bricks
                        newBlockData = rand.nextBoolean() ? Material.AIR .createBlockData() :  Material.NETHER_BRICKS.createBlockData();
                    } else if (Tag.SAND.isTagged(material)) {
                        // Converts all types of sand to Soul Sand
                        newBlockData = Material.SOUL_SAND.createBlockData();
                        e.getChunk().getBlock(x, y, z).setBiome(Biome.SOUL_SAND_VALLEY);
                    } else if (Tag.DIRT.isTagged(material)) {
                        // Converts all dirt/grass-like blocks to Soul Soil
                        newBlockData = Material.NETHERRACK.createBlockData();
                    } else if (Tag.FLOWERS.isTagged(material) || Tag.SAPLINGS.isTagged(material)) {
                        // Converts flowers/saplings to a less common Nether material
                        newBlockData = Material.CRIMSON_ROOTS.createBlockData();
                        e.getChunk().getBlock(x, y, z).setBiome(Biome.CRIMSON_FOREST);
                    } else if (Tag.COAL_ORES.isTagged(material) || Tag.IRON_ORES.isTagged(material) || Tag.GOLD_ORES.isTagged(material)) {
                        // Converts overworld ores to their Nether equivalent (or just a common Nether block)
                        newBlockData = Material.GLOWSTONE.createBlockData(); // Example conversion
                    } else if (Tag.CROPS.isTagged(material)) {
                        newBlockData = Material.NETHER_WART.createBlockData();
                        e.getChunk().getBlock(x, Math.max(y-1, e.getWorld().getMinHeight()), z).setType(Material.SOUL_SAND);
                    } else if (Tag.BANNERS.isTagged(material)) {
                        newBlockData = Material.BLACK_BANNER.createBlockData();
                    } else if (Tag.TRAPDOORS.isTagged(material)) {
                        newBlockData = Material.WARPED_TRAPDOOR.createBlockData();
                    } else if (Tag.BADLANDS_TERRACOTTA.isTagged(material)) {
                        newBlockData = Material.NETHER_BRICKS.createBlockData();
                    } else if(Tag.ALL_HANGING_SIGNS.isTagged(material)) {
                        newBlockData = Material.WARPED_HANGING_SIGN.createBlockData();
                    } else if(Tag.ALL_SIGNS.isTagged(material)) {
                        newBlockData = Material.WARPED_SIGN.createBlockData();
                    } else if(Tag.ANVIL.isTagged(material)) {
                        newBlockData = Material.CRACKED_POLISHED_BLACKSTONE_BRICKS.createBlockData();
                    } else if(Tag.CAMPFIRES.isTagged(material)) {
                        newBlockData = Material.SOUL_CAMPFIRE.createBlockData();
                    } else if(Tag.CANDLE_CAKES.isTagged(material)) {
                        newBlockData = Material.WARPED_HYPHAE.createBlockData();
                    } else if(Tag.COAL_ORES.isTagged(material)) {
                        newBlockData = Material.NETHER_QUARTZ_ORE.createBlockData();
                    } else if(Tag.COPPER_ORES.isTagged(material)) {
                        newBlockData = Material.NETHER_QUARTZ_ORE.createBlockData();
                    } else if(Tag.DIAMOND_ORES.isTagged(material)) {
                        newBlockData = Material.NETHER_GOLD_ORE.createBlockData();
                    } else if (Tag.EMERALD_ORES.isTagged(material)) {
                        newBlockData = Material.NETHER_GOLD_ORE.createBlockData();
                    } else if (Tag.GOLD_ORES.isTagged(material)) {
                        newBlockData = Material.NETHER_GOLD_ORE.createBlockData();
                    } else if (Tag.IRON_ORES.isTagged(material)) {
                        newBlockData = Material.NETHER_GOLD_ORE.createBlockData();
                    } else if (Tag.LAPIS_ORES.isTagged(material)) {
                        newBlockData = Material.NETHER_GOLD_ORE.createBlockData();
                    } else if (Tag.REDSTONE_ORES.isTagged(material)) {
                        newBlockData = Material.NETHER_QUARTZ_ORE.createBlockData();
                    } else if (Tag.BEDS.isTagged(material)) {
                        newBlockData = Material.GLOWSTONE.createBlockData();
                    } else if (Tag.BEEHIVES.isTagged(material)) {
                        newBlockData = Material.GLOWSTONE.createBlockData();
                    } else if (Tag.BARS.isTagged(material)) {
                        newBlockData = Material.IRON_BARS.createBlockData();
                    } else if (Tag.CAVE_VINES.isTagged(material)) {
                        newBlockData = Material.GLOWSTONE.createBlockData();
                    } else if (Tag.CAULDRONS.isTagged(material)) {
                        if (rand.nextBoolean()) {
                            newBlockData = rand.nextDouble() < attrition ? Material.AIR.createBlockData() :Material.CAULDRON.createBlockData();
                        } else {
                            newBlockData = rand.nextDouble() < attrition ? Material.AIR.createBlockData() :Material.LAVA_CAULDRON.createBlockData();
                        }
                    } else if (Tag.COPPER_CHESTS.isTagged(material)) {
                        newBlockData = rand.nextDouble() < attrition ? Material.AIR.createBlockData() : Material.OXIDIZED_COPPER_CHEST.createBlockData();

                    }  else if (Tag.FENCES.isTagged(material)) {
                        newBlockData = rand.nextDouble() < attrition ? Material.AIR.createBlockData() :Material.NETHER_BRICK_FENCE.createBlockData();
                    } else if (Tag.SLABS.isTagged(material)) {
                        newBlockData = rand.nextDouble() < attrition ? Material.AIR.createBlockData() :Material.NETHER_BRICK_SLAB.createBlockData();
                    } else if (Tag.STAIRS.isTagged(material)) {

                        newBlockData = rand.nextDouble() < attrition ? Material.AIR.createBlockData() : Material.NETHER_BRICK_STAIRS.createBlockData() ;

                    } else if (Tag.WALLS.isTagged(material)) {
                        newBlockData = rand.nextDouble() < 0.1 ? Material.AIR .createBlockData() :  Material.NETHER_BRICK_WALL.createBlockData();
                    } else if (newBlock.getType() == Material.OBSIDIAN) {
                        newBlockData = Material.OBSIDIAN.createBlockData();
                    }
                    // --- Individual Block Conversion (Switch Statement) ---

                    else { // Only proceed to switch if no Tag conversion was applied

                        switch (material) {
                        case AIR:
                            // Nothing to do here
                            break;
                        case GRASS_BLOCK:
                            newBlockData = rand.nextDouble() < 0.2 ? Material.SOUL_SOIL.createBlockData() : Material.NETHERRACK.createBlockData();
                            break;
                        case NETHER_PORTAL:
                            if (e.getChunk().getBlock(x, y, z).getType() == Material.NETHER_PORTAL) {
                                newBlockData = Material.NETHER_PORTAL.createBlockData();
                            } else {
                                newBlockData = Material.AIR.createBlockData();
                            }
                            break;
                        case OBSIDIAN:
                            // Set Obi to air
                            newBlockData = Material.AIR.createBlockData();
                            break;
                        case HAY_BLOCK:
                            newBlockData = Material.GLOWSTONE.createBlockData();
                            break;
                        case GRAVEL:
                            break;
                        case STONE:
                        case ANDESITE:
                        case DIORITE:
                        case GRANITE:
                        case BUBBLE_COLUMN:
                            newBlockData = Material.NETHERRACK.createBlockData();
                            break;
                        case KELP:
                        case SEAGRASS:
                            newBlockData = Material.MAGMA_BLOCK.createBlockData();
                            break;
                        case WATER:
                            // The Nether is hot! Convert water/kelp etc. to lava.
                            newBlockData = Material.LAVA.createBlockData();
                            break;
                        case LAVA:
                            // Keep lava as lava
                            break;
                        case TORCH:
                        case WALL_TORCH:
                            // Convert to a more intense light source
                            newBlockData = Material.SOUL_TORCH.createBlockData();
                            break;
                        case COBBLESTONE:
                            newBlockData = Material.BASALT.createBlockData();
                            break;
                        case TALL_GRASS:
                            newBlockData = Material.AIR.createBlockData();
                            break;
                        case SHORT_GRASS:
                        case SHORT_DRY_GRASS:
                            if (rand.nextDouble() < 0.1) {
                                newBlockData = Material.FIRE.createBlockData();
                            } else {
                                newBlockData = Material.AIR.createBlockData();
                            }
                            break;
                        case GLASS:
                        case GLASS_PANE:
                            // Convert to a dark, smoky pane
                            newBlockData = Material.BLACK_STAINED_GLASS_PANE.createBlockData();
                            break;
                        case BEDROCK:
                            // Bedrock remains bedrock
                            break;
                        case BRICKS:
                            newBlockData = Material.NETHER_BRICK.createBlockData();
                            break;
                        case CHEST:
                            newBlockData = Material.CHEST.createBlockData();
                            break;
                        default:
                            newBlockData = Material.BLACKSTONE.createBlockData();
                            break;
                        }
                    }
                    // Apply the new BlockData to the shadow world chunk
                    newBlock.setBlockData(newBlockData, false);

                    // Set aspects of the block to match the overworld
                    if (bd instanceof Fence fence && newBlockData instanceof Fence newFence) {
                        fence.getFaces().forEach(bf -> newFence.setFace(bf, true));
                    }
                    if (bd instanceof Stairs stairs && newBlockData instanceof Stairs newStairs) {
                        newStairs.setFacing(stairs.getFacing());
                    }
                    if (bd instanceof Slab slab && newBlockData instanceof Slab newSlab) {
                        newSlab.setType(slab.getType());
                    }
                    if (newBlock.getState() instanceof Chest chest && chestFills < maxChestFills) {
                        chestFills++;
                        // If it's a chest, then put some random stuff in it
                        Location chestLocation = chest.getLocation();
                        LootContext context = new LootContext.Builder(chestLocation)
                                .build(); // .build() creates the final immutable context object
                        // Define the LootTable you want to use (e.g., a Bastion Treasure chest)
                        LootTable netherLoot = LootTables.BASTION_TREASURE.getLootTable();
                        // A. Set the LootTable on the Chest's BlockState
                        chest.setLootTable(netherLoot);

                        // B. Generate the loot immediately
                        // Note: You must provide a random number generator and a context.
                        // The 'null' for the loot context is often acceptable for basic generation.
                        chest.getInventory().clear(); // Clear any pre-existing items (important!)
                        chest.setSeed(System.currentTimeMillis() + chestLocation.hashCode()); // Set a seed for unique loot

                        // Generate the items and place them in the inventory
                        netherLoot.fillInventory(chest.getInventory(), rand, context);

                        // Facing
                        if (bd instanceof org.bukkit.block.data.type.Chest chestData && newBlockData instanceof org.bukkit.block.data.type.Chest newChest) {
                            newChest.setFacing(chestData.getFacing());
                        }
                        // C. Apply the changes
                        chest.update(true); 

                    }
                    newBlock.setBlockData(newBlockData, false);

                }
            }
        }
        // Now do Mobs
        Arrays.stream(overworldChunk.getEntities())
        .filter(en -> en instanceof LivingEntity)
        .forEach(en -> {
            EntityType newType = getNetherEnt(en.getType());
            if (newType != null) {
                addon.getNetherWorld().spawnEntity(en.getLocation().toVector().toLocation(addon.getNetherWorld()), newType, true);
            }
        });
    }

    private EntityType getNetherEnt(EntityType type) {
        return  switch (type) {
        case ALLAY:
            // A friendly flying helper, creatively mapped to the friendly, flying GHAST?
            yield EntityType.GHAST;
        case ARMADILLO:
            // A shell-armored creature, maybe a MAGMA_CUBE as a rolling/bouncing threat?
            yield EntityType.MAGMA_CUBE;
        case AXOLOTL:
            // An aquatic, helpful mob, creatively mapped to the fiery BLAZE
            yield EntityType.BLAZE;
        case BAT:
            // A small flying creature, mapped to the flying, hostile GHAST
            yield EntityType.GHAST;
        case BEE:
            // A stinging insect, maybe a smaller, aggressive MAGMA_CUBE
            yield EntityType.MAGMA_CUBE;
        case BLAZE:
            // Already a Nether mob, keep it
            yield EntityType.BLAZE;
        case BOGGED:
            // A variant of SKELETON, use WITHER_SKELETON
            yield EntityType.WITHER_SKELETON;
        case BREEZE:
            // Already a mob from a deep structure, could default or use a powerful Nether mob
            yield EntityType.BLAZE; // Or null, as it's not strictly Overworld
        case CAMEL:
            // A large rideable desert mob, mapped to the ridable STRIDER
            yield EntityType.STRIDER;
        case CAT:
            // A smaller, friendly ground mob, mapped to the aggressive HOGLIN
            yield EntityType.HOGLIN;
        case CAVE_SPIDER:
            // A smaller, venomous SPIDER, mapped to the smaller, fiery MAGMA_CUBE
            yield EntityType.MAGMA_CUBE;
        case CHICKEN:
            // A small, passive mob, mapped to the small, aggressive ZOMBIFIED_PIGLIN
            yield EntityType.ZOMBIFIED_PIGLIN;
        case COD:
            // An Overworld fish, mapped to the hostile GHAST (flying over lava like fish in water)
            yield EntityType.GHAST;
        case COPPER_GOLEM:
            // A golem variant, he continues to exist here!
            yield EntityType.COPPER_GOLEM;
        case COW:
            // A large, passive mob, mapped to the large, aggressive HOGLIN
            yield EntityType.HOGLIN;
        case CREEPER:
            // An explosive mob, mapped to the projectile-shooting GHAST
            yield EntityType.GHAST;
        case DONKEY:
            // A pack animal, mapped to the rideable STRIDER
            yield EntityType.STRIDER;
        case ENDERMAN:
            // Already a mob that can spawn in the Nether, keep it
            yield EntityType.ENDERMAN;
        case ENDERMITE:
            // A small burrowing mob, mapped to the small MAGMA_CUBE
            yield EntityType.MAGMA_CUBE;
        case EVOKER:
            // An illager type, mapped to the powerful PIGLIN_BRUTE
            yield EntityType.PIGLIN_BRUTE;
        case FOX:
            // A cunning predator, mapped to the aggressive PIGLIN
            yield EntityType.PIGLIN;
        case FROG:
            // A jumping amphibian, mapped to the bouncing MAGMA_CUBE
            yield EntityType.MAGMA_CUBE;
        case GHAST:
            // Already a Nether mob, keep it
            yield EntityType.GHAST;
        case GLOW_SQUID:
            // A bioluminescent aquatic mob, mapped to the glowing MAGMA_CUBE
            yield EntityType.MAGMA_CUBE;
        case GOAT:
            // A mountain climber/jumper, mapped to the bounding MAGMA_CUBE
            yield EntityType.MAGMA_CUBE;
        case HOGLIN:
            // Already a Nether mob, keep it
            yield EntityType.HOGLIN;
        case HORSE:
            // A rideable animal, mapped to the rideable STRIDER
            yield EntityType.STRIDER;
        case HUSK:
            // A desert ZOMBIE, mapped to the ZOMBIE_VILLAGER's Nether equivalent
            yield EntityType.ZOMBIFIED_PIGLIN;
        case ILLUSIONER:
            // A ranged illager, mapped to the ranged PIGLIN
            yield EntityType.PIGLIN;
        case IRON_GOLEM:
            // A defensive golem, mapped to the defensive WITHER_SKELETON (Fortress guardian)
            yield EntityType.WITHER_SKELETON;
        case LLAMA:
            // A pack animal, mapped to the rideable STRIDER
            yield EntityType.STRIDER;
        case MAGMA_CUBE:
            // Already a Nether mob, keep it
            yield EntityType.MAGMA_CUBE;
        case MOOSHROOM:
            // A variant of COW, mapped to the HOGLIN
            yield EntityType.HOGLIN;
        case MULE:
            // A pack animal, mapped to the rideable STRIDER
            yield EntityType.STRIDER;
        case OCELOT:
            // A jungle cat, mapped to the aggressive PIGLIN
            yield EntityType.PIGLIN;
        case PANDA:
            // A large, rare Overworld mob, mapped to the PIGLIN_BRUTE
            yield EntityType.PIGLIN_BRUTE;
        case PARROT:
            // A small, flying pet, mapped to the flying, hostile GHAST
            yield EntityType.GHAST;
        case PHANTOM:
            // A flying undead mob, mapped to the flying, hostile GHAST
            yield EntityType.GHAST;
        case PIG:
            // A passive ground mob, naturally mapped to the ZOMBIFIED_PIGLIN
            yield EntityType.ZOMBIFIED_PIGLIN;
        case PIGLIN:
            // Already a Nether mob, keep it
            yield EntityType.PIGLIN;
        case PIGLIN_BRUTE:
            // Already a Nether mob, keep it
            yield EntityType.PIGLIN_BRUTE;
        case PILLAGER:
            // A ranged illager, mapped to the ranged PIGLIN
            yield EntityType.PIGLIN;
        case POLAR_BEAR:
            // A large, aggressive, cold-climate mob, mapped to the HOGLIN
            yield EntityType.HOGLIN;
        case PUFFERFISH:
            // A small, poisonous aquatic mob, mapped to the small MAGMA_CUBE
            yield EntityType.MAGMA_CUBE;
        case RABBIT:
            // A small, ground-dwelling mob, mapped to the ZOMBIFIED_PIGLIN
            yield EntityType.ZOMBIFIED_PIGLIN;
        case RAVAGER:
            // A large, powerful illager beast, mapped to the HOGLIN
            yield EntityType.HOGLIN;
        case SHEEP:
            // A wool-producing passive mob, mapped to the ZOMBIFIED_PIGLIN
            yield EntityType.ZOMBIFIED_PIGLIN;
        case SHULKER:
            // Already a mob from a specific structure, can default or use a powerful Nether mob
            yield EntityType.WITHER_SKELETON;
        case SILVERFISH:
            // A small stone-dwelling mob, mapped to the small MAGMA_CUBE
            yield EntityType.MAGMA_CUBE;
        case SKELETON:
            // An undead archer, mapped to the WITHER_SKELETON (Nether archer analog)
            yield EntityType.WITHER_SKELETON;
        case SKELETON_HORSE:
            // An undead rideable animal, mapped to the STRIDER
            yield EntityType.STRIDER;
        case SLIME:
            // A bouncy mob, mapped to the MAGMA_CUBE (Nether slime)
            yield EntityType.MAGMA_CUBE;
        case SNIFFER:
            // A large, ancient Overworld mob, mapped to the PIGLIN_BRUTE
            yield EntityType.PIGLIN_BRUTE;
        case SNOW_GOLEM:
            // A ranged, defensive golem, mapped to the ranged BLAZE
            yield EntityType.BLAZE;
        case SPIDER:
            // A common hostile mob, mapped to the aggressive PIGLIN
            yield EntityType.PIGLIN;
        case SQUID:
            // An aquatic mob, mapped to the fiery BLAZE
            yield EntityType.BLAZE;
        case STRAY:
            // A frozen SKELETON, mapped to the WITHER_SKELETON
            yield EntityType.WITHER_SKELETON;
        case STRIDER:
            // Already a Nether mob, keep it
            yield EntityType.STRIDER;
        case TURTLE:
            // A slow, armored reptile, mapped to the HOGLIN
            yield EntityType.HOGLIN;
        case VEX:
            // A small, flying summon, mapped to the BLAZE
            yield EntityType.BLAZE;
        case VILLAGER:
            // A passive humanoid, mapped to the PIGLIN (Nether equivalent of society/village)
            yield EntityType.PIGLIN;
        case VINDICATOR:
            // A melee illager, mapped to the PIGLIN_BRUTE
            yield EntityType.PIGLIN_BRUTE;
        case WANDERING_TRADER:
            // A traveling Overworld trader, mapped to the PIGLIN
            yield EntityType.PIGLIN;
        case WARDEN:
            // A deep, powerful mob, mapped to the WITHER
            yield EntityType.WITHER;
        case WITCH:
            // A ranged spellcaster, mapped to the ranged BLAZE
            yield EntityType.BLAZE;
        case WITHER:
            // Already a Nether-related boss, keep it
            yield EntityType.WITHER;
        case WITHER_SKELETON:
            // Already a Nether mob, keep it
            yield EntityType.WITHER_SKELETON;
        case WOLF:
            // A ground-based predator/companion, mapped to the aggressive HOGLIN
            yield EntityType.HOGLIN;
        case ZOGLIN:
            // Already a Nether mob, keep it
            yield EntityType.ZOGLIN;
        case ZOMBIE:
            // A common undead, mapped to the ZOMBIFIED_PIGLIN (Nether's common undead)
            yield EntityType.ZOMBIFIED_PIGLIN;
        case ZOMBIE_HORSE:
            // An undead rideable animal, mapped to the STRIDER
            yield EntityType.STRIDER;
        case ZOMBIE_VILLAGER:
            // An undead VILLAGER, mapped to the ZOMBIFIED_PIGLIN
            yield EntityType.ZOMBIFIED_PIGLIN;
        case ZOMBIFIED_PIGLIN:
            // Already a Nether mob, keep it
            yield EntityType.ZOMBIFIED_PIGLIN;
        case HAPPY_GHAST:
            // Happy Ghasts exist in both dimensions
            yield EntityType.HAPPY_GHAST;
        default:
            // Default for any remaining non-mob entities (projectiles, items) or unknowns
            yield null;

        };
    }
}
