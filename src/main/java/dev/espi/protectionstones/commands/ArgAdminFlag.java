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
import dev.espi.protectionstones.FlagHandler;
import dev.espi.protectionstones.PSL;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.ProtectionStones;
import dev.espi.protectionstones.utils.UUIDCache;
import dev.espi.protectionstones.utils.WGUtils;
import dev.espi.protectionstones.utils.SchedulerUtil;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

class ArgAdminFlag {
    static boolean argumentAdminFlag(CommandSender p, String[] args) {

        if (args.length < 5) {
            PSL.msg(p, ArgAdmin.getFlagHelp());
            return true;
        }

        if (args.length > 3 && args[3].equalsIgnoreCase("-g") && args.length < 6) {
            return PSL.msg(p, ArgAdmin.getFlagHelp());
        }
        final String selectedFlag;
        final String selectedValue;
        final String selectedGroup;
        if (args[3].equalsIgnoreCase("-g")) {
            selectedFlag = args[5];
            StringBuilder builder = new StringBuilder();
            for (int i = 6; i < args.length; i++) builder.append(args[i]).append(' ');
            selectedValue = builder.toString();
            selectedGroup = args[4];
        } else {
            selectedFlag = args[3];
            StringBuilder builder = new StringBuilder();
            for (int i = 4; i < args.length; i++) builder.append(args[i]).append(' ');
            selectedValue = builder.toString();
            selectedGroup = "";
        }

        if (WGUtils.getFlagRegistry().get(selectedFlag) == null)
            return PSL.msg(p, PSL.FLAG_NOT_SET.msg());

        Runnable apply = () -> {
        World w = WGUtils.getLoadedWorld(args[2]);
        if (w == null) {
            PSL.msg(p, PSL.INVALID_WORLD.msg());
            return;
        }
        RegionManager rgm = WGUtils.getRegionManagerWithWorld(w);
        if (rgm == null) {
            PSL.msg(p, PSL.INVALID_WORLD.msg());
            return;
        }
        for (ProtectedRegion r : rgm.getRegions().values()) {
            PSRegion psRegion = PSRegion.fromWGRegion(w, r);
            if (ProtectionStones.isPSRegion(r) && psRegion != null) {
                String flagValue = selectedValue;

                // apply %player% placeholder
                if (FlagHandler.getPlayerPlaceholderFlags().contains(selectedFlag) && (!r.getOwners().getUniqueIds().isEmpty())) {
                    String name = UUIDCache.getNameFromUUID(r.getOwners().getUniqueIds().stream().findFirst().get());
                    if (name != null) {
                        flagValue = flagValue.replace("%player%", name);
                    }
                }

                ArgFlag.setFlag(psRegion, p, selectedFlag, flagValue.trim(), selectedGroup);
            }
        }
        };
        if (p instanceof org.bukkit.entity.Player player) SchedulerUtil.executeForEntity(player, apply);
        else SchedulerUtil.executeGlobally(apply);
        return true;
    }
}
