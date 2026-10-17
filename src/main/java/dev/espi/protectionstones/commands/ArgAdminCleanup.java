/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.espi.protectionstones.commands;

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.PSL;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.ProtectionStones;
import dev.espi.protectionstones.utils.SchedulerUtil;
import dev.espi.protectionstones.utils.WGUtils;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

class ArgAdminCleanup {

    static boolean argumentAdminCleanup(CommandSender p, String[] preParseArgs) {
        if (preParseArgs.length < 3 || !Arrays.asList("remove", "preview").contains(preParseArgs[2].toLowerCase())) {
            PSL.msg(p, ArgAdmin.getCleanupHelp());
            return true;
        }

        String cleanupOperation = preParseArgs[2].toLowerCase();

        World w;
        String alias = null;

        List<String> args = new ArrayList<>();

        for (int i = 3; i < preParseArgs.length; i++) {
            if (preParseArgs[i].equals("-t") && i != preParseArgs.length - 1) {
                alias = preParseArgs[++i];
            } else {
                args.add(preParseArgs[i]);
            }
        }

        if (args.size() > 1 && WGUtils.getLoadedWorld(args.get(1)) != null) {
            w = WGUtils.getLoadedWorld(args.get(1));
        } else {
            if (p instanceof Player) {
                w = ((Player) p).getWorld();
            } else {
                PSL.msg(p, args.size() > 1 ? PSL.INVALID_WORLD.msg() : PSL.ADMIN_CONSOLE_WORLD.msg());
                return true;
            }
        }

        int days;
        try {
            days = (args.size() > 0) ? Integer.parseInt(args.get(0)) : 30;
        } catch (Exception e) {
            PSL.msg(p, PSL.ADMIN_ERROR_PARSING.msg());
            return true;
        }

        File previewOutputFile = null;
        if (cleanupOperation.equals("preview")) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd H-m-s");
            previewOutputFile = new File(ProtectionStones.getInstance().getDataFolder().getAbsolutePath() + "/" + LocalDateTime.now().format(formatter) + " cleanup preview.txt");
        }

        RegionManager rgm = WGUtils.getRegionManagerWithWorld(w);
        if (rgm == null) {
            PSL.msg(p, PSL.INVALID_WORLD.msg());
            return true;
        }

        Map<String, ProtectedRegion> regions = new HashMap<>(rgm.getRegions());
        String finalAlias = alias;
        int finalDays = days;

        final File outputFile = previewOutputFile;
        SchedulerUtil.executeGlobally(() -> {
            sendPSL(p, PSL.ADMIN_CLEANUP_HEADER.msg()
                    .replace("%arg%", cleanupOperation)
                    .replace("%days%", "" + finalDays));

            HashSet<UUID> relevantPlayers = new HashSet<>();
            List<PSRegion> psRegions = new ArrayList<>();

            for (ProtectedRegion protectedRegion : regions.values()) {
                PSRegion r = PSRegion.fromWGRegion(w, protectedRegion);
                if (r == null) {
                    continue;
                }

                if (finalAlias != null && (r.getTypeOptions() == null || !r.getTypeOptions().alias.equals(finalAlias))) {
                    continue;
                }

                psRegions.add(r);
                relevantPlayers.addAll(r.getOwners());
                relevantPlayers.addAll(r.getMembers());
            }

            HashSet<UUID> activePlayers = new HashSet<>();
            long now = System.currentTimeMillis();

            for (UUID uuid : relevantPlayers) {
                try {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    long lastPlayed = (now - op.getLastPlayed()) / 86400000L;
                    if (lastPlayed < finalDays) {
                        activePlayers.add(uuid);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            List<PSRegion> toDelete = new ArrayList<>();
            for (PSRegion r : psRegions) {
                long numOfActiveOwners = r.getOwners().stream().filter(activePlayers::contains).count();
                long numOfActiveMembers = r.getMembers().stream().filter(activePlayers::contains).count();

                if (numOfActiveOwners == 0) {
                    if (ProtectionStones.getInstance().getConfigOptions().cleanupDeleteRegionsWithMembersButNoOwners || numOfActiveMembers == 0) {
                        toDelete.add(r);
                    }
                }
            }

            Iterator<PSRegion> deleteRegionsIterator = toDelete.iterator();
            regionLoop(deleteRegionsIterator, p, cleanupOperation.equalsIgnoreCase("remove"), outputFile, new StringBuilder());
        });
        return false;
    }

    private static void regionLoop(Iterator<PSRegion> deleteRegionsIterator, CommandSender p, boolean isRemoveOperation,
                                   File outputFile, StringBuilder previewIds) {
        if (deleteRegionsIterator.hasNext()) {
            PSRegion next = deleteRegionsIterator.next();
            Runnable process = () -> {
                processRegion(next, p, isRemoveOperation, outputFile, previewIds);
                SchedulerUtil.executeGlobally(() -> regionLoop(deleteRegionsIterator, p, isRemoveOperation, outputFile, previewIds));
            };
            if (isRemoveOperation) {
                SchedulerUtil.executeForLocation(next.getProtectBlockLocation(), process);
            } else {
                SchedulerUtil.laterGlobally(process, 1L);
            }
        } else {
            sendPSL(p, PSL.ADMIN_CLEANUP_FOOTER.msg()
                    .replace("%arg%", isRemoveOperation ? "remove" : "preview"));

            if (!isRemoveOperation) {
                String previewContents = previewIds.toString();
                SchedulerUtil.runAsync(() -> {
                    try {
                        Files.writeString(outputFile.toPath(), previewContents, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                        send(p, ChatColor.YELLOW + "Dumped the list regions that can be deleted in " + outputFile.getName() + " (in the plugin folder).");
                    } catch (IOException e) {
                        ProtectionStones.getInstance().getLogger().severe("Failed to write cleanup preview: " + e.getMessage());
                        send(p, ChatColor.RED + "Internal error, please check the console logs.");
                    }
                });
            }
        }
    }

    private static void processRegion(PSRegion r, CommandSender p, boolean isRemoveOperation,
                                      File outputFile, StringBuilder previewIds) {
        if (isRemoveOperation) {
            send(p, ChatColor.YELLOW + "Removed region " + r.getId() + " due to inactive owners.");
            r.deleteRegion(true);
        } else {
            send(p, ChatColor.YELLOW + "Found region " + r.getId() + " that can be deleted.");
            previewIds.append(r.getId()).append('\n');
        }
    }

    private static void sendPSL(CommandSender sender, String message) {
        Runnable sendMessage = () -> PSL.msg(sender, message);
        if (sender instanceof Player player) SchedulerUtil.executeForEntity(player, sendMessage);
        else SchedulerUtil.executeGlobally(sendMessage);
    }

    private static void send(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            SchedulerUtil.executeForEntity(player, () -> {
                if (message != null) sender.sendMessage(message);
            });
        } else {
            SchedulerUtil.executeGlobally(() -> {
                if (message != null) sender.sendMessage(message);
            });
        }
    }
}
