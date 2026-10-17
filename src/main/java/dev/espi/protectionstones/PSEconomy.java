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

package dev.espi.protectionstones;

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.utils.MiscUtil;
import dev.espi.protectionstones.utils.SchedulerUtil;
import dev.espi.protectionstones.utils.WGUtils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Handler for ProtectionStones economy related tasks.
 */
public class PSEconomy {
    private final List<PSRegion> rentedList = new CopyOnWriteArrayList<>();
    private ScheduledTask rentRunner;
    private ScheduledTask taxRunner;

    public PSEconomy() {
        if (!ProtectionStones.getInstance().isVaultSupportEnabled()) {
            ProtectionStones.getInstance().getLogger().warning("Vault is not enabled! Economy functions (renting & buying) will not work!");
            return;
        }
        loadRentList();

        // Region and economy APIs are processed on the global scheduler, never on an async worker.
        rentRunner = SchedulerUtil.repeatGlobally(task -> updateRents(), 1L, 200L);
        if (ProtectionStones.getInstance().getConfigOptions().taxEnabled) {
            taxRunner = SchedulerUtil.repeatGlobally(task -> updateTaxes(), 1L, 200L);
        }
    }

    private void updateRents() {
        rentedList.removeIf(region -> region.getTypeOptions() == null || region.getRentStage() != PSRegion.RentStage.RENTING);
        for (PSRegion region : rentedList) {
            try {
                Duration rentPeriod = MiscUtil.parseRentPeriod(region.getRentPeriod());
                if (Instant.now().getEpochSecond() > region.getRentLastPaid() + rentPeriod.getSeconds()) {
                    doRentPayment(region);
                }
            } catch (Exception ignored) {
                // A malformed rent period should not stop rent processing for other regions.
            }
        }
    }

    private void updateTaxes() {
        WGUtils.getAllRegionManagers().forEach((world, regionManager) -> {
            for (ProtectedRegion region : regionManager.getRegions().values()) {
                if (ProtectionStones.isPSRegion(region)) {
                    processTaxes(PSRegion.fromWGRegion(world, region));
                }
            }
        });
    }

    /** Stops the economy cycle. Used for reloads when creating a new PSEconomy. */
    public void stop() {
        if (rentRunner != null) {
            rentRunner.cancel();
            rentRunner = null;
        }
        if (taxRunner != null) {
            taxRunner.cancel();
            taxRunner = null;
        }
    }

    /** Load list of regions that are rented into memory. */
    public void loadRentList() {
        rentedList.clear();
        for (World world : WGUtils.getAllRegionManagers().keySet()) {
            RegionManager manager = WGUtils.getRegionManagerWithWorld(world);
            if (manager == null) continue;
            for (ProtectedRegion region : manager.getRegions().values()) {
                if (ProtectionStones.isPSRegion(region)) {
                    rentedList.add(PSRegion.fromWGRegion(world, region));
                }
            }
        }
    }

    /** Process taxes for a region on the global scheduler. */
    public static void processTaxes(PSRegion region) {
        if (region == null || region.getTypeOptions() == null || region.getTypeOptions().taxPeriod == -1) return;
        Runnable process = () -> {
            region.updateTaxPayments();

            if (!region.getTaxPaymentsDue().isEmpty() && region.getTaxAutopayer() != null) {
                PSPlayer payer = PSPlayer.fromUUID(region.getTaxAutopayer());
                EconomyResponse response = region.payTax(payer, payer.getBalance());
                if (response != null && response.amount != 0) {
                    String regionName = region.getName() == null ? region.getId() : region.getName() + " (" + region.getId() + ")";
                    sendToOnlinePlayer(payer.getUuid(), PSL.TAX_PAID.msg()
                            .replace("%amount%", String.format("%.2f", response.amount))
                            .replace("%region%", regionName));
                }
            }

            if (region.isTaxPaymentLate()) {
                region.deleteRegion(true);
            }
        };

        if (Bukkit.isGlobalTickThread()) process.run();
        else SchedulerUtil.executeGlobally(process);
    }

    /**
     * Process a rent payment for a region. The check, Vault transactions, player lookups,
     * WorldGuard flag updates, and persistence all run on the global scheduler.
     */
    public static void doRentPayment(PSRegion region) {
        Runnable payment = () -> doRentPaymentOnGlobal(region);
        if (Bukkit.isGlobalTickThread()) payment.run();
        else SchedulerUtil.executeGlobally(payment);
    }

    private static void doRentPaymentOnGlobal(PSRegion region) {
        PSPlayer tenant = PSPlayer.fromUUID(region.getTenant());
        PSPlayer landlord = PSPlayer.fromUUID(region.getLandlord());
        String regionName = region.getName() == null ? region.getId() : region.getName();

        if (!tenant.hasAmount(region.getPrice())) {
            sendToOnlinePlayer(tenant.getUuid(), PSL.RENT_EVICT_NO_MONEY_TENANT.msg()
                    .replace("%region%", regionName)
                    .replace("%price%", String.format("%.2f", region.getPrice())));
            sendToOnlinePlayer(landlord.getUuid(), PSL.RENT_EVICT_NO_MONEY_LANDLORD.msg()
                    .replace("%region%", regionName)
                    .replace("%tenant%", tenant.getName()));
            region.removeRenting();
            return;
        }

        sendToOnlinePlayer(tenant.getUuid(), PSL.RENT_PAID_TENANT.msg()
                .replace("%price%", String.format("%.2f", region.getPrice()))
                .replace("%landlord%", landlord.getName())
                .replace("%region%", regionName));
        sendToOnlinePlayer(landlord.getUuid(), PSL.RENT_PAID_LANDLORD.msg()
                .replace("%price%", String.format("%.2f", region.getPrice()))
                .replace("%tenant%", tenant.getName())
                .replace("%region%", regionName));

        tenant.pay(landlord, region.getPrice());
        region.setRentLastPaid(Instant.now().getEpochSecond());
        try {
            region.getWGRegionManager().saveChanges();
        } catch (StorageException e) {
            ProtectionStones.getInstance().getLogger().severe("Unable to save rent payment for region " + region.getId() + ": " + e.getMessage());
        }
    }

    private static void sendToOnlinePlayer(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) SchedulerUtil.executeForEntity(player, () -> PSL.msg(player, message));
    }

    /** @return list of rented regions. */
    public List<PSRegion> getRentedList() {
        return rentedList;
    }
}
