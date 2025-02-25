package com.fastasyncworldedit.bukkit.regions.plotsquaredv4;

import com.fastasyncworldedit.core.FaweAPI;
import com.fastasyncworldedit.core.configuration.Caption;
import com.fastasyncworldedit.core.regions.FaweMask;
import com.fastasyncworldedit.core.regions.FaweMaskManager;
import com.fastasyncworldedit.core.regions.RegionWrapper;
import com.github.intellectualsites.plotsquared.plot.commands.MainCommand;
import com.github.intellectualsites.plotsquared.plot.config.Settings;
import com.github.intellectualsites.plotsquared.plot.database.DBFunc;
import com.github.intellectualsites.plotsquared.plot.flag.Flags;
import com.github.intellectualsites.plotsquared.plot.listener.WEManager;
import com.github.intellectualsites.plotsquared.plot.object.Plot;
import com.github.intellectualsites.plotsquared.plot.object.PlotArea;
import com.github.intellectualsites.plotsquared.plot.object.PlotPlayer;
import com.github.intellectualsites.plotsquared.plot.util.ChunkManager;
import com.github.intellectualsites.plotsquared.plot.util.SchematicHandler;
import com.github.intellectualsites.plotsquared.plot.util.UUIDHandler;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionIntersection;
import com.sk89q.worldedit.world.World;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlotSquaredFeature extends FaweMaskManager {

    private static final Logger LOGGER = LogManagerCompat.getLogger();

    public PlotSquaredFeature() {
        super("PlotSquared");
        LOGGER.info("Optimizing PlotSquared");
        if (com.fastasyncworldedit.core.configuration.Settings.IMP.ENABLED_COMPONENTS.PLOTSQUARED_v4_HOOK) {
            Settings.Enabled_Components.WORLDEDIT_RESTRICTIONS = false;
            try {
                setupBlockQueue();
                setupSchematicHandler();
                setupChunkManager();
            } catch (Throwable ignored) {
                LOGGER.info("Please update PlotSquared: https://www.spigotmc.org/resources/77506/");
            }
            if (Settings.PLATFORM.toLowerCase(Locale.ROOT).startsWith("bukkit")) {
                new FaweTrim();
            }
            if (MainCommand.getInstance().getCommand("generatebiome") == null) {
                new PlotSetBiome();
            }
        }
        // TODO: revisit this later on
        /*
        try {
            if (Settings.Enabled_Components.WORLDS) {
                new ReplaceAll();
            }
        } catch (Throwable e) {
            log.debug("You need to update PlotSquared to access the CFI and REPLACEALL commands");
        }
        */
    }

    public static String getName(UUID uuid) {
        return UUIDHandler.getName(uuid);
    }

    private void setupBlockQueue() throws RuntimeException {
        // If it's going to fail, throw an error now rather than later
        //QueueProvider provider = QueueProvider.of(FaweLocalBlockQueue.class, null);
        //GlobalBlockQueue.IMP.setProvider(provider);
        //HybridPlotManager.REGENERATIVE_CLEAR = false;
        //log.debug(" - QueueProvider: " + FaweLocalBlockQueue.class);
        //log.debug(" - HybridPlotManager.REGENERATIVE_CLEAR: " + HybridPlotManager.REGENERATIVE_CLEAR);
    }

    private void setupChunkManager() throws RuntimeException {
        ChunkManager.manager = new FaweChunkManager(ChunkManager.manager);
        LOGGER.info(" - ChunkManager: {}", ChunkManager.manager);
    }

    private void setupSchematicHandler() throws RuntimeException {
        SchematicHandler.manager = new FaweSchematicHandler();
        LOGGER.info(" - SchematicHandler: {}", SchematicHandler.manager);
    }

    public boolean isAllowed(Player player, Plot plot, MaskType type) {
        if (plot == null) {
            return false;
        }
        UUID uid = player.getUniqueId();
        if (Flags.NO_WORLDEDIT.isTrue(plot)) {
            player.print(Caption.of(
                    "fawe.cancel.reason.no.region.reason",
                    Caption.of("fawe.cancel.reason.no.region.plot.noworldeditflag")
            ));
            return false;
        }
        if (plot.isOwner(uid) || player.hasPermission("fawe.plotsquared.admin")) {
            return true;
        }
        if (type != MaskType.MEMBER) {
            player.print(Caption.of(
                    "fawe.cancel.reason.no.region.reason",
                    Caption.of("fawe.cancel.reason.no.region.plot.owner.only")
            ));
            return false;
        }
        if (plot.getTrusted().contains(uid) || plot.getTrusted().contains(DBFunc.EVERYONE)) {
            return true;
        }
        if (plot.getMembers().contains(uid) || plot.getMembers().contains(DBFunc.EVERYONE)) {
            if (!player.hasPermission("fawe.plotsquared.member")) {
                player.print(Caption.of(
                        "fawe.cancel.reason.no.region.reason",
                        Caption.of("fawe.error.no-perm", "fawe.plotsquared.member")
                ));
                return false;
            }
            if (!plot.getOwners().isEmpty() && plot.getOwners().stream().anyMatch(this::playerOnline)) {
                return true;
            } else {
                player.print(Caption.of(
                        "fawe.cancel.reason.no.region.reason",
                        Caption.of("fawe.cancel.reason.no.region.plot.owner.offline")
                ));
                return false;
            }
        }
        player.print(Caption.of(
                "fawe.cancel.reason.no.region.reason",
                Caption.of("fawe.cancel.reason.no.region.not.added")
        ));
        return false;
    }

    private boolean playerOnline(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline();
    }

    @Override
    public FaweMask getMask(Player player, MaskType type) {
        final PlotPlayer pp = PlotPlayer.wrap(player.getUniqueId());
        if (pp == null) {
            return null;
        }
        final Set<CuboidRegion> regions;
        Plot plot = pp.getCurrentPlot();
        if (isAllowed(player, plot, type)) {
            regions = plot.getRegions();
        } else {
            plot = null;
            regions = WEManager.getMask(pp);
            if (regions.size() == 1) {
                CuboidRegion region = regions.iterator().next();
                if (region.getMinimumPoint().getX() == Integer.MIN_VALUE && region
                        .getMaximumPoint()
                        .getX() == Integer.MAX_VALUE) {
                    regions.clear();
                }
            }
        }
        if (regions.isEmpty()) {
            return null;
        }
        PlotArea area = pp.getApplicablePlotArea();
        int min = area != null ? area.MIN_BUILD_HEIGHT : 0;
        int max = area != null ? Math.min(255, area.MAX_BUILD_HEIGHT) : 255;
        final HashSet<RegionWrapper> faweRegions = new HashSet<>();
        for (CuboidRegion current : regions) {
            faweRegions.add(new RegionWrapper(
                    current.getMinimumX(),
                    current.getMaximumX(),
                    min,
                    max,
                    current.getMinimumZ(),
                    current.getMaximumZ()
            ));
        }
        final CuboidRegion region = regions.iterator().next();
        final BlockVector3 pos1 = BlockVector3.at(region.getMinimumX(), min, region.getMinimumZ());
        final BlockVector3 pos2 = BlockVector3.at(region.getMaximumX(), max, region.getMaximumZ());
        final Plot finalPlot = plot;
        if (Settings.Done.RESTRICT_BUILDING && Flags.DONE.isSet(finalPlot) || regions.isEmpty()) {
            return null;
        }

        Region maskedRegion;
        if (regions.size() == 1) {
            maskedRegion = new CuboidRegion(pos1, pos2);
        } else {
            World world = FaweAPI.getWorld(area.worldname);
            List<Region> weRegions = regions.stream()
                    .map(r -> new CuboidRegion(
                            world,
                            BlockVector3.at(r.getMinimumX(), r.getMinimumY(), r.getMinimumZ()),
                            BlockVector3.at(r.getMaximumX(), r.getMaximumY(), r.getMaximumZ())
                    ))
                    .collect(Collectors.toList());
            maskedRegion = new RegionIntersection(world, weRegions);
        }

        return new FaweMask(maskedRegion) {
            @Override
            public boolean isValid(Player player, MaskType type) {
                if (Settings.Done.RESTRICT_BUILDING && Flags.DONE.isSet(finalPlot)) {
                    return false;
                }
                return isAllowed(player, finalPlot, type);
            }
        };
    }

}
