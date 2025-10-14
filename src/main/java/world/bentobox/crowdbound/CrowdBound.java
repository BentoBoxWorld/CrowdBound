package world.bentobox.crowdbound;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.generator.ChunkGenerator;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.commands.admin.DefaultAdminCommand;
import world.bentobox.bentobox.api.commands.island.DefaultPlayerCommand;
import world.bentobox.bentobox.api.configuration.Config;
import world.bentobox.bentobox.api.configuration.WorldSettings;
import world.bentobox.crowdbound.commands.player.ClaimCommand;

/**
 * Main Boxed class - provides a survival game inside a box
 * @author tastybento
 */
public class CrowdBound extends GameModeAddon {

    private static final String NETHER = "_nether";
    private static final String THE_END = "_the_end";

    // Settings
    private Settings settings;

    private final Config<Settings> configObject = new Config<>(this, Settings.class);

    @Override
    public void onLoad() {
        // Save the default config from config.yml
        saveDefaultConfig();
        // Load settings from config.yml. This will check if there are any issues with it too.
        loadSettings();

        // Register commands
        playerCommand = new DefaultPlayerCommand(this) {
            @Override
            public void setup()
            {
                super.setup();
                // Commands
                new ClaimCommand(this);
            }
        };

        adminCommand = new DefaultAdminCommand(this) {
            @Override
            public void setup()
            {
                super.setup();
                // Special commands
            }
        };
    }

    private boolean loadSettings() {
        // Load settings again to get worlds
        settings = configObject.loadConfigObject();
        if (settings == null) {
            // Disable
            logError("Settings could not load! Addon disabled.");
            setState(State.DISABLED);
            return false;
        }
        return true;
    }

    @Override
    public void onEnable() {
        // Check for recommended addons
        if (this.getPlugin().getAddonsManager().getAddonByName("Border").isEmpty()) {
            this.logWarning("CrowdBound normally requires the Border addon.");
        }
        if (this.getPlugin().getAddonsManager().getAddonByName("InvSwitcher").isEmpty()) {
            this.logWarning("CrowdBound recommends the InvSwitcher addon.");
        }

    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        if (loadSettings()) {
            log("Reloaded settings");
        }
    }

    /**
     * @return the settings
     */
    public Settings getSettings() {
        return settings;
    }

    @Override
    public void createWorlds() {
        String worldName = settings.getWorldName().toLowerCase();
        // Create overworld
        islandWorld = getWorld(worldName, World.Environment.NORMAL);

        // Make the nether if it does not exist
        if (settings.isNetherGenerate()) {
            netherWorld = getWorld(worldName, World.Environment.NETHER);
        }
        // Make the end if it does not exist
        if (settings.isEndGenerate()) {
            endWorld = getWorld(worldName, World.Environment.THE_END);
        }
    }

    /**
     * Gets a world or generates a new world if it does not exist
     * @param worldName2 - the overworld name
     * @param env - the environment
     * @return world loaded or generated
     */
    private World getWorld(String worldName2, Environment env) {
        // Set world name
        worldName2 = env.equals(World.Environment.NETHER) ? worldName2 + NETHER : worldName2;
        worldName2 = env.equals(World.Environment.THE_END) ? worldName2 + THE_END : worldName2;
        //boxedBiomeProvider = new BoxedBiomeGenerator(this);
        World w = WorldCreator
                .name(worldName2)
                //.generator(getChunkGenerator(env))
                .environment(env)
                .seed(this.getSettings().getSeed())
                .createWorld();
        // Set spawn rates
        if (w != null) {
            setSpawnRates(w);
        }
        return w;

    }

    private void setSpawnRates(World w) {
        if (getSettings().getSpawnLimitMonsters() > 0) {
            w.setSpawnLimit(SpawnCategory.MONSTER, getSettings().getSpawnLimitMonsters());
        }
        if (getSettings().getSpawnLimitAmbient() > 0) {
            w.setSpawnLimit(SpawnCategory.AMBIENT, getSettings().getSpawnLimitAmbient());
        }
        if (getSettings().getSpawnLimitAnimals() > 0) {
            w.setSpawnLimit(SpawnCategory.ANIMAL, getSettings().getSpawnLimitAnimals());
        }
        if (getSettings().getSpawnLimitWaterAnimals() > 0) {
            w.setSpawnLimit(SpawnCategory.WATER_ANIMAL, getSettings().getSpawnLimitWaterAnimals());
        }
        if (getSettings().getTicksPerAnimalSpawns() > 0) {
            w.setTicksPerSpawns(SpawnCategory.ANIMAL, getSettings().getTicksPerAnimalSpawns());
        }
        if (getSettings().getTicksPerMonsterSpawns() > 0) {
            w.setTicksPerSpawns(SpawnCategory.MONSTER, getSettings().getTicksPerMonsterSpawns());
        }
    }

    @Override
    public WorldSettings getWorldSettings() {
        return getSettings();
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return Bukkit.getWorld(worldName).getGenerator();
    }

    @Override
    public void saveWorldSettings() {
        if (settings != null) {
            configObject.saveConfigObject(settings);
        }
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.api.addons.Addon#allLoaded()
     */
    @Override
    public void allLoaded() {
        // Save settings. This will occur after all addons have loaded
        this.saveWorldSettings();
    }

    @Override
    public boolean isUsesNewChunkGeneration() {
        return true;
    }
}
