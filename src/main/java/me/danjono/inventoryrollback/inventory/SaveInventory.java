package me.danjono.inventoryrollback.inventory;

import com.nuclyon.technicallycoded.inventoryrollback.InventoryRollbackPlus;
import com.nuclyon.technicallycoded.inventoryrollback.nms.EnumNmsVersion;
import me.danjono.inventoryrollback.InventoryRollback;
import me.danjono.inventoryrollback.data.LogType;
import me.danjono.inventoryrollback.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;

public class SaveInventory {

    private final InventoryRollbackPlus main;
    private final Player player;
    private final LogType logType;
    private final DamageCause deathCause;
    private final String causeAlias;

    private final PlayerInventory mainInventory;
    private final Inventory enderChestInventory;

    public SaveInventory(Player player, LogType logType, DamageCause deathCause, String causeAliasIn, PlayerInventory mainInventory, Inventory enderChestInventory) {
        this.main = InventoryRollbackPlus.getInstance();
        this.player = player;
        this.logType = logType;
        this.deathCause = deathCause;
        this.causeAlias = causeAliasIn;

        this.mainInventory = mainInventory;
        this.enderChestInventory = enderChestInventory;
    }

    public void createSave() {
        Long timestamp = System.currentTimeMillis();
        PlayerData data = new PlayerData(player, logType, timestamp);

        for (ItemStack item : mainInventory.getContents()) {
            if (item != null) {
                data.setMainInventory(mainInventory.getContents());
                break;
            }
        }

        if (main.getVersion().isNoHigherThan(EnumNmsVersion.v1_8_R3)) {
            for (ItemStack item : mainInventory.getArmorContents()) {
                if (item != null) {
                    data.setArmour(mainInventory.getArmorContents());
                    break;
                }
            }
        }

        if (enderChestInventory.getContents().length > 0)
            for (ItemStack item : enderChestInventory.getContents()) {
                if (item != null) {
                    data.setEnderChest(enderChestInventory.getContents());
                    break;
                }
            }

        data.setXP(getTotalExperience(player));
        data.setHealth(player.getHealth());
        data.setFoodLevel(player.getFoodLevel());
        data.setSaturation(player.getSaturation());
        data.setWorld(player.getWorld().getName());

        // Location data
        Location pLoc = player.getLocation();
        // Multiply by 10, truncate, divide by 10
        // This has the effect of only keeping 1 decimal of precision
        double locX = ((int)(pLoc.getX() * 10)) / 10d;
        double locY = ((int)(pLoc.getY() * 10)) / 10d;
        double locZ = ((int)(pLoc.getZ() * 10)) / 10d;
        data.setX(locX);
        data.setY(locY);
        data.setZ(locZ);

        data.setLogType(logType);
        data.setVersion(InventoryRollback.getPackageVersion());

        if (causeAlias != null) data.setDeathReason(causeAlias);
        else if (deathCause != null) data.setDeathReason(deathCause.name());
        else data.setDeathReason("UNKNOWN");

        // Remove excess saves if limit is reached
        CompletableFuture<Void> purgeTask = data.purgeExcessSaves();

        // Save new data
        purgeTask.thenRun(data::saveData);

    }

    //Conversion to Base64 code courtesy of github.com/JustRayz
    public static String toBase64(ItemStack[] contents) {
        boolean convert = false;

        for (ItemStack item : contents) {
            if (item != null) {
                convert = true;
                break;
            }
        }

        if (convert) {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

                dataOutput.writeInt(contents.length);

                // TODO: remove bundle error saving workaround
                boolean useBundleWorkaround = InventoryRollbackPlus.getInstance().getVersion()
                        .isWithin(EnumNmsVersion.v1_17_R1, EnumNmsVersion.v1_17_R1);

                for (ItemStack stack : contents) {
                    // TODO: remove bundle error saving workaround
                    /*if (stack != null && useBundleWorkaround && stack.getType().equals(Material.BUNDLE))
                    {
                        dataOutput.writeObject(null);
                        continue;
                    }*/
                    dataOutput.writeObject(stack);
                }
                dataOutput.close();
                byte[] byteArr = outputStream.toByteArray();
                // TODO: remove bundle error saving workaround
                //test(byteArr);
                return Base64Coder.encodeLines(byteArr);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to save item stacks.", e);
            }
        }

        return null;
    }

    // TODO: remove bundle error saving workaround
    /*public static void test(byte[] byteArr) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(byteArr);
        BukkitObjectInputStream dataInput = null;
        ItemStack[] stacks = null;
        try {
            dataInput = new BukkitObjectInputStream(inputStream);
            stacks = new ItemStack[dataInput.readInt()];
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        for (int i = 0; i < stacks.length; i++) {
            try {
                Object objectRead = dataInput.readObject();
                stacks[i] = (ItemStack) objectRead;
            } catch (IOException | ClassNotFoundException | NullPointerException e) {
                e.printStackTrace();
            }
        }
        try { dataInput.close(); } catch (IOException ignored) {}
    }*/

    //Credits to Dev_Richard (https://www.spigotmc.org/members/dev_richard.38792/)
    //https://gist.github.com/RichardB122/8958201b54d90afbc6f0
    private float getTotalExperience(Player player) {
        int level = player.getLevel();
        float currentExp = player.getExp();
        int experience;
        int requiredExperience;

        if(level >= 0 && level <= 15) {
            experience = (int) Math.ceil(Math.pow(level, 2) + (6 * level));
            requiredExperience = 2 * level + 7;
        } else if(level > 15 && level <= 30) {
            experience = (int) Math.ceil((2.5 * Math.pow(level, 2) - (40.5 * level) + 360));
            requiredExperience = 5 * level - 38;
        } else {
            experience = (int) Math.ceil((4.5 * Math.pow(level, 2) - (162.5 * level) + 2220));
            requiredExperience = 9 * level - 158;
        }

        experience += Math.ceil(currentExp * requiredExperience);

        return experience;
    }

}
