package kr.lunaf.nationSystem.service;

import kr.lunaf.nationSystem.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;

import java.io.File;
import java.io.FileInputStream;
import java.util.Random;

public class StructureService {
    private final PluginConfig pluginConfig;
    private final java.io.File dataFolder;

    public StructureService(PluginConfig pluginConfig, java.io.File dataFolder) {
        this.pluginConfig = pluginConfig;
        this.dataFolder = dataFolder;
    }

    public boolean placeWallStructure(Location location) {
        return placeStructure(pluginConfig.wallStructurePath(), location);
    }

    public boolean placeCenterStructure(Location location) {
        return placeStructure(pluginConfig.centerStructurePath(), location);
    }

    public boolean placeBuildingStructure(String relativePath, Location location, String direction) {
        return placeStructure(relativePath, location, direction);
    }

    public boolean hasWallStructure() {
        return structureExists(pluginConfig.wallStructurePath());
    }

    public boolean hasCenterStructure() {
        return structureExists(pluginConfig.centerStructurePath());
    }

    public boolean hasBuildingStructure(String relativePath) {
        return structureExists(relativePath);
    }

    private boolean placeStructure(String relativePath, Location location) {
        File file = new File(dataFolder, relativePath);
        if (!file.exists()) {
            return false;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            StructureManager manager = Bukkit.getStructureManager();
            Structure structure = manager.loadStructure(input);
            structure.place(
                location,
                false,
                StructureRotation.NONE,
                Mirror.NONE,
                0,
                1.0f,
                new Random()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean placeStructure(String relativePath, Location location, String direction) {
        File file = new File(dataFolder, relativePath);
        if (!file.exists()) {
            return false;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            StructureManager manager = org.bukkit.Bukkit.getStructureManager();
            Structure structure = manager.loadStructure(input);
            StructureRotation rotation = switch (direction.toUpperCase(java.util.Locale.ROOT)) {
                case "WEST" -> StructureRotation.CLOCKWISE_90;
                case "NORTH" -> StructureRotation.CLOCKWISE_180;
                case "EAST" -> StructureRotation.COUNTERCLOCKWISE_90;
                default -> StructureRotation.NONE;
            };
            structure.place(
                location,
                false,
                rotation,
                Mirror.NONE,
                0,
                1.0f,
                new Random()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean structureExists(String relativePath) {
        File file = new File(dataFolder, relativePath);
        return file.exists();
    }
}
