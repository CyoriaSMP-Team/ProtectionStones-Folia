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

import dev.espi.protectionstones.PSPlayer;
import dev.espi.protectionstones.PSProtectBlock;
import dev.espi.protectionstones.PSL;
import dev.espi.protectionstones.ProtectionStones;
import dev.espi.protectionstones.utils.SchedulerUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.text.DecimalFormat;
import java.util.*;

public class ArgGet implements PSCommandArg {

    @Override
    public List<String> getNames() {
        return Collections.singletonList("get");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Collections.singletonList("protectionstones.get");
    }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() {
        return null;
    }

    private boolean openGetGUI(Player p) {
        PSL.msg(p, PSL.GET_HEADER.msg());
        for (PSProtectBlock b : ProtectionStones.getInstance().getConfiguredBlocks()) {
            if ((!b.permission.equals("") && !p.hasPermission(b.permission)) || (b.preventPsGet && !p.hasPermission("protectionstones.admin"))) {
                continue; // no permission
            }

            String price = new DecimalFormat("#.##").format(b.price);

            TextComponent tc = new TextComponent(PSL.GET_GUI_BLOCK.msg()
                    .replace("%alias%", b.alias)
                    .replace("%price%", price)
                    .replace("%description%", b.description)
                    .replace("%xradius%", ""+b.xRadius)
                    .replace("%yradius%", ""+b.yRadius)
                    .replace("%zradius%", ""+b.zRadius));

            tc.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(PSL.GET_GUI_HOVER.msg()
                    .replace("%alias%", b.alias)
                    .replace("%price%", price)
                    .replace("%description%", b.description)
                    .replace("%xradius%", ""+b.xRadius)
                    .replace("%yradius%", ""+b.yRadius)
                    .replace("%zradius%", ""+b.zRadius)).create()));
            tc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + ProtectionStones.getInstance().getConfigOptions().base_command + " get " + b.alias));

            p.spigot().sendMessage(tc);
        }
        return true;
    }

    @Override
    public boolean executeArgument(CommandSender s, String[] args, HashMap<String, String> flags) {
        Player p = (Player) s;
        if (!p.hasPermission("protectionstones.get"))
            return PSL.msg(p, PSL.NO_PERMISSION_GET.msg());

        // /ps get (for GUI)
        if (args.length == 1) return openGetGUI(p);

        if (args.length != 2)
            return PSL.msg(p, PSL.GET_HELP.msg());

        // check if argument is valid block
        PSProtectBlock cp = ProtectionStones.getProtectBlockFromAlias(args[1]);
        if (cp == null)
            return PSL.msg(p, PSL.INVALID_BLOCK.msg());

        // check for block permission (custom)
        if (!cp.permission.equals("") && !p.hasPermission(cp.permission))
            return PSL.msg(p, PSL.GET_NO_PERMISSION_BLOCK.msg());

        // check if /ps get is disabled on this
        if (cp.preventPsGet && !p.hasPermission("protectionstones.admin"))
            return PSL.msg(p, PSL.GET_NO_PERMISSION_BLOCK.msg());

        // debug message
        if (!ProtectionStones.getInstance().isVaultSupportEnabled() && cp.price != 0) {
            Bukkit.getLogger().info("Vault is not enabled but there is a price set on the protection stone! It will not work!");
        }

        if (!ProtectionStones.getInstance().isVaultSupportEnabled() || cp.price == 0) {
            return giveItem(p, cp);
        }

        UUID playerId = p.getUniqueId();
        double price = cp.price;
        SchedulerUtil.executeGlobally(() -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            var economy = ProtectionStones.getInstance().getVaultEconomy();
            if (!economy.has(offlinePlayer, price)) {
                SchedulerUtil.sendToPlayer(playerId, PSL.NOT_ENOUGH_MONEY.msg().replace("%price%", String.format("%.2f", price)));
                return;
            }

            EconomyResponse response = economy.withdrawPlayer(offlinePlayer, price);
            if (response == null || !response.transactionSuccess()) {
                SchedulerUtil.sendToPlayer(playerId, response == null ? PSL.NOT_ENOUGH_MONEY.msg() : response.errorMessage);
                return;
            }
            SchedulerUtil.executeForEntity(p, () -> givePaidItem(p, cp, playerId, price),
                    () -> SchedulerUtil.executeGlobally(() -> economy.depositPlayer(Bukkit.getOfflinePlayer(playerId), price)));
        });
        return true;
    }

    private boolean giveItem(Player p, PSProtectBlock block) {
        // check if item was able to be added (inventory not full)
        if (!p.getInventory().addItem(block.createItem()).isEmpty()) {
            if (ProtectionStones.getInstance().getConfigOptions().dropItemWhenInventoryFull) { // drop on floor
                PSL.msg(p, PSL.NO_ROOM_DROPPING_ON_FLOOR.msg());
                p.getWorld().dropItem(p.getLocation(), block.createItem());
            } else { // cancel event
                PSL.msg(p, PSL.NO_ROOM_IN_INVENTORY.msg());
                return true;
            }
            return true;
        }

        return PSL.msg(p, PSL.GET_GOTTEN.msg());
    }

    private void givePaidItem(Player p, PSProtectBlock block, UUID playerId, double price) {
        Map<Integer, org.bukkit.inventory.ItemStack> remaining = p.getInventory().addItem(block.createItem());
        if (remaining.isEmpty()) {
            PSL.msg(p, PSL.GET_GOTTEN.msg());
            return;
        }

        if (ProtectionStones.getInstance().getConfigOptions().dropItemWhenInventoryFull) {
            PSL.msg(p, PSL.NO_ROOM_DROPPING_ON_FLOOR.msg());
            remaining.values().forEach(item -> p.getWorld().dropItem(p.getLocation(), item));
            return;
        }

        SchedulerUtil.executeGlobally(() -> {
            var economy = ProtectionStones.getInstance().getVaultEconomy();
            EconomyResponse refund = economy.depositPlayer(Bukkit.getOfflinePlayer(playerId), price);
            SchedulerUtil.executeForEntity(p, () -> {
                PSL.msg(p, PSL.NO_ROOM_IN_INVENTORY.msg());
                if (refund == null || !refund.transactionSuccess()) {
                    PSL.msg(p, refund == null ? "Unable to refund the protection block price." : refund.errorMessage);
                }
            });
        });
    }

    // tab completion
    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        List<String> l = new ArrayList<>();
        for (PSProtectBlock b : ProtectionStones.getInstance().getConfiguredBlocks()) {
            if ((!b.permission.equals("") && !sender.hasPermission(b.permission)) || (b.preventPsGet && !sender.hasPermission("protectionstones.admin"))) continue; // no permission
            l.add(b.alias);
        }
        return args.length == 2 ? StringUtil.copyPartialMatches(args[1], l, new ArrayList<>()) : null;
    }

}
