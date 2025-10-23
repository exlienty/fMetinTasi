package com.kripto;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

class LicenseManager {
    private static final String VERIFY_URL = "http://lisansip:5000/verify";
    private final JavaPlugin plugin;


    public LicenseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean verifyLicense(String licenseKey) {
        try {
            URL url = new URL(VERIFY_URL + "?license=" + licenseKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String result = in.readLine();
            in.close();

            return "VALID".equalsIgnoreCase(result);
        } catch (Exception e) {
            return false;
        }
    }
}


public class MetinTasiPlugin extends JavaPlugin implements Listener, TabExecutor {

    private FileConfiguration config;
    private FileConfiguration dataConfig;
    private File dataFile;
    private int freezeSuresi;

    private final Map<String, MetinTasi> metinTaslari = new HashMap<>();
    private final Random random = new Random();

    private File langFile;
    private YamlConfiguration lang;

    private class MetinTasi {
        public String isim;
        public Location location;
        public int can;
        public int maxCan;
        public ArmorStand hologram;

        public MetinTasi(String isim, Location location, int maxCan) {
            this.isim = isim;
            this.location = location;
            this.maxCan = maxCan;
            this.can = maxCan;
        }
    }

    private static class RewardItem {
        String displayName;
        Material material;
        int amount;
        int chance;
        String originalEntry;

        public RewardItem(String displayName, Material material, int amount, int chance, String originalEntry) {
            this.displayName = displayName;
            this.material = material;
            this.amount = amount;
            this.chance = chance;
            this.originalEntry = originalEntry;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String licenseKey = getConfig().getString("license-key", "");
        LicenseManager lm = new LicenseManager(this);

        if (licenseKey == null || licenseKey.isEmpty()) {
            getLogger().severe("Lisans anahtarı girilmemiş! Lütfen config.yml dosyasına license-key ekleyin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!lm.verifyLicense(licenseKey)) {
            getLogger().severe("Lisans doğrulanamadı! Plugin devre dışı bırakıldı.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        config = getConfig();
        loadDataFile();
        loadLangFile();

        freezeSuresi = config.getInt("metintasi_timefreeze", 15);

        getServer().getPluginManager().registerEvents(this, this);

        PluginCommand cmdAyarla = getCommand("metintasiayarla");
        PluginCommand cmdSil = getCommand("metintasisil");
        PluginCommand cmdAna = getCommand("metintasi");

        if (cmdAyarla != null) {
            cmdAyarla.setExecutor(this);
        }

        if (cmdSil != null) {
            cmdSil.setExecutor(this);
        }

        if (cmdAna != null) {
            cmdAna.setExecutor(this);
        }

        loadAllMetinTaslari();
        getLogger().info("MetinTasi plugin basariyla aktiflestirildi!");
    }

    private void loadDataFile() {
        try {
            dataFile = new File(getDataFolder(), "data.yml");
            if (!dataFile.exists()) {
                dataConfig = new YamlConfiguration();
                dataConfig.createSection("metin-taslari");
                saveDataFile();
            } else {
                dataConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(
                        new FileInputStream(dataFile), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            getLogger().warning("Data.yml yuklenirken hata olustu: " + e.getMessage());
            dataConfig = new YamlConfiguration();
            dataConfig.createSection("metin-taslari");
        }
    }

    private void saveDataFile() {
        try {
            dataConfig.save(dataFile);
        } catch (Exception e) {
            getLogger().warning("Data.yml kaydedilirken hata olustu: " + e.getMessage());
        }
    }

    private void loadLangFile() {
        langFile = new File(getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            saveResource("lang.yml", false);
        }
        try {
            lang = YamlConfiguration.loadConfiguration(new InputStreamReader(
                    new FileInputStream(langFile), StandardCharsets.UTF_8));
        } catch (Exception e) {
            getLogger().warning("Lang.yml yuklenirken hata olustu: " + e.getMessage());
            lang = new YamlConfiguration();
        }
    }

    private void loadAllMetinTaslari() {
        if (dataConfig.contains("metin-taslari")) {
            int loadedCount = 0;
            for (String isim : dataConfig.getConfigurationSection("metin-taslari").getKeys(false)) {
                String path = "metin-taslari." + isim;
                Location loc = dataConfig.getLocation(path + ".location");
                int maxCan = dataConfig.getInt(path + ".maxCan", config.getInt("metintasi_can", 1000));

                if (loc != null && loc.getWorld() != null) {
                    MetinTasi metinTasi = new MetinTasi(isim, loc, maxCan);
                    metinTaslari.put(isim, metinTasi);

                    Block block = loc.getBlock();
                    if (block.getType() == Material.AIR || block.getType() == Material.BEDROCK) {
                        block.setType(Material.SPONGE);
                    }

                    spawnHologram(metinTasi);
                    loadedCount++;
                }
            }
            getLogger().info(loadedCount + " adet metin tasi yuklendi!");
        }
    }

    private String getLang(String path) {
        String msg = lang.getString(path);
        if (msg == null) {
            switch (path) {
                case "odul_mesaj":
                    return "&6【&e&l🎁&6】 &e&lMETİN TAŞI &6【&e&l🎁&6】\n   &7→ &6%item% &ex%amount% &8[&e%chance%%&8]\n&aŞanslısın! &7ᴍᴇᴛɪɴ ᴛᴀsıɴᴅᴀɴ ʙɪʀ ᴘᴀʀᴄᴀ ᴅᴜsᴛᴜ.";
                case "block_not_selected":
                    return "&cLutfen bir bloga bakarak bu komutu kullanin!";
                case "invalid_block":
                    return "&cSadece Obsidian veya Sponge secebilirsiniz!";
                case "metintasi_set":
                    return "&aMetin Tasi basariyla ayarlandi: &e%isim%";
                case "metintasi_remove":
                    return "&cMetin Tasi kaldirildi: &e%isim%";
                case "metintasi_no_active":
                    return "&cAktif bir Metin Tasi bulunmuyor!";
                case "metintasi_not_found":
                    return "&cBu isimde bir Metin Tasi bulunamadi: &e%isim%";
                case "metintasi_already_exists":
                    return "&cBu isimde bir Metin Tasi zaten mevcut: &e%isim%";
                case "respawn_message_title":
                    return "&9METIN TASI SPAWNLANDI";
                case "respawn_message_subtitle":
                    return "&7Hazir ve yeniden kirilabilir!";
                case "hologram_hp_prefix":
                    return "&cHP: ";
                case "hologram_hp_suffix":
                    return "&f/%max%";
                case "invalid_reward_format":
                    return "&cConfig icinde hatali odul formati: &e%entry%";
                case "reload_success":
                    return "&aPlugin basariyla yeniden yuklendi!";
                case "usage_ayarla":
                    return "&cKullanim: &6/metintasiayarla <isim>";
                case "usage_sil":
                    return "&cKullanim: &6/metintasisil <isim>";
                case "must_be_player":
                    return "&cBu komut sadece oyuncular tarafindan kullanilabilir!";
                case "no_permission":
                    return "&cBu komutu kullanmak icin yetkin yok!";
                default:
                    return path;
            }
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (metinTaslari.isEmpty()) return;

        Player p = e.getPlayer();
        Block b = e.getBlock();

        for (MetinTasi metinTasi : metinTaslari.values()) {
            if (b.getLocation().equals(metinTasi.location)) {
                e.setCancelled(true);
                metinTasi.can--;

                updateHologram(metinTasi);
                giveRandomReward(p);

                if (metinTasi.can <= 0) {
                    breakMetinTasi(metinTasi);
                }
                break;
            }
        }
    }

    private void breakMetinTasi(MetinTasi metinTasi) {
        metinTasi.location.getBlock().setType(Material.BEDROCK);
        removeHologram(metinTasi);

        final ArmorStand[] respawnHolograms = new ArmorStand[4];
        final String metinTasiIsim = metinTasi.isim;

        int[] countdown = {freezeSuresi};

        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (countdown[0] <= 0) {
                for (ArmorStand hologram : respawnHolograms) {
                    if (hologram != null && !hologram.isDead()) {
                        hologram.remove();
                    }
                }

                metinTasi.location.getBlock().setType(Material.SPONGE);
                metinTasi.can = metinTasi.maxCan;
                spawnHologram(metinTasi);

                String respawnTitle = config.getString("hologram.respawn_title", "&6&l★ &a&l%isim% &6&l★")
                        .replace("%isim%", metinTasiIsim.toUpperCase());
                String respawnSubtitle = config.getString("hologram.respawn_subtitle", "&aYenilendi!");

                Bukkit.getOnlinePlayers().forEach(player -> {
                    player.sendTitle(
                            ChatColor.translateAlternateColorCodes('&', respawnTitle),
                            ChatColor.translateAlternateColorCodes('&', respawnSubtitle),
                            10, 70, 20
                    );
                });

                task.cancel();
                return;
            }

            Location baseLoc = metinTasi.location.clone().add(0.5, getHologramHeight(), 0.5);

            if (respawnHolograms[0] == null || respawnHolograms[0].isDead()) {
                respawnHolograms[0] = baseLoc.getWorld().spawn(baseLoc, ArmorStand.class);
                setupHologram(respawnHolograms[0], false);
            }

            String respawnTitle = config.getString("hologram.respawn_title", "&6&l★ &c&l%isim% &6&l★")
                    .replace("%isim%", metinTasiIsim.toUpperCase());
            respawnHolograms[0].setCustomName(ChatColor.translateAlternateColorCodes('&', respawnTitle));

            Location subtitleLoc = baseLoc.clone().subtract(0, 0.35, 0);
            if (respawnHolograms[1] == null || respawnHolograms[1].isDead()) {
                respawnHolograms[1] = subtitleLoc.getWorld().spawn(subtitleLoc, ArmorStand.class);
                setupHologram(respawnHolograms[1], true);
            }
            String respawnSubtitle = config.getString("hologram.respawn_subtitle", "&7Yenileniyor...");
            respawnHolograms[1].setCustomName(ChatColor.translateAlternateColorCodes('&', respawnSubtitle));

            Location timeLoc = baseLoc.clone().subtract(0, 0.65, 0);
            if (respawnHolograms[2] == null || respawnHolograms[2].isDead()) {
                respawnHolograms[2] = timeLoc.getWorld().spawn(timeLoc, ArmorStand.class);
                setupHologram(respawnHolograms[2], true);
            }

            int minutes = countdown[0] / 60;
            int seconds = countdown[0] % 60;
            String timeFormatted = String.format("%d:%02d", minutes, seconds);

            String timeText = config.getString("hologram.respawn_time_format", "&e⏳ &f%time%")
                    .replace("%time%", timeFormatted);
            respawnHolograms[2].setCustomName(ChatColor.translateAlternateColorCodes('&', timeText));

            Location messageLoc = baseLoc.clone().subtract(0, 0.95, 0);
            if (respawnHolograms[3] == null || respawnHolograms[3].isDead()) {
                respawnHolograms[3] = messageLoc.getWorld().spawn(messageLoc, ArmorStand.class);
                setupHologram(respawnHolograms[3], true);
            }
            String messageText = config.getString("hologram.respawn_message", "&7Kırılmaya hazırlanıyor");
            respawnHolograms[3].setCustomName(ChatColor.translateAlternateColorCodes('&', messageText));

            countdown[0]--;
        }, 0L, 20L);
    }

    private void giveRandomReward(Player player) {
        List<String> rewards = config.getStringList("metintasi_rewards");
        if (rewards.isEmpty()) return;

        int dropChance = config.getInt("metintasi_dropchance", 80);
        int roll = random.nextInt(100) + 1;

        if (roll > dropChance) {
            return;
        }

        int totalChance = 0;
        List<RewardItem> rewardItems = new ArrayList<>();

        for (String entry : rewards) {
            String[] parts = entry.split(":");
            if (parts.length >= 3) {
                try {
                    if (parts[0].equals("CUSTOM")) {
                        String displayName = parts[1];
                        Material material = Material.valueOf(parts[2].toUpperCase());
                        int amount = Integer.parseInt(parts[3]);
                        int chance = Integer.parseInt(parts[4]);

                        totalChance += chance;
                        rewardItems.add(new RewardItem(displayName, material, amount, chance, entry));

                    } else if (parts[0].equals("COMMAND")) {
                        String displayName = parts[1];
                        Material material = Material.valueOf(parts[2].toUpperCase());
                        int amount = Integer.parseInt(parts[3]);
                        int chance = Integer.parseInt(parts[4]);

                        totalChance += chance;
                        rewardItems.add(new RewardItem(displayName, material, amount, chance, entry));

                    } else {
                        String displayName = parts[0];
                        Material material = Material.valueOf(parts[1].toUpperCase());
                        int amount = Integer.parseInt(parts[2]);
                        int chance = (parts.length >= 4) ? Integer.parseInt(parts[3]) : 10;

                        totalChance += chance;
                        rewardItems.add(new RewardItem(displayName, material, amount, chance, entry));
                    }
                } catch (Exception e) {
                    getLogger().warning("Hatali odul formati: " + entry);
                }
            }
        }

        if (rewardItems.isEmpty() || totalChance == 0) {
            return;
        }

        int randomValue = random.nextInt(totalChance) + 1;
        int currentChance = 0;

        for (RewardItem reward : rewardItems) {
            currentChance += reward.chance;
            if (randomValue <= currentChance) {
                processReward(player, reward);
                return;
            }
        }
    }

    private void processReward(Player player, RewardItem reward) {
        if (reward.originalEntry.startsWith("CUSTOM:")) {
            giveCustomItem(player, reward);
        } else if (reward.originalEntry.startsWith("COMMAND:")) {
            executeCommandReward(player, reward);
        } else {
            giveNormalItem(player, reward);
        }

        String coloredDisplayName = ChatColor.translateAlternateColorCodes('&', reward.displayName);
        String message = getLang("odul_mesaj")
                .replace("%item%", coloredDisplayName)
                .replace("%amount%", String.valueOf(reward.amount))
                .replace("%chance%", String.valueOf(reward.chance));

        player.sendMessage(message);
    }

    private void giveNormalItem(Player player, RewardItem reward) {
        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(reward.material, reward.amount));
    }

    private void giveCustomItem(Player player, RewardItem reward) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(reward.material, reward.amount);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', reward.displayName));

        if (reward.originalEntry.split(":").length > 5) {
            String loreString = reward.originalEntry.split(":")[5];
            List<String> lore = new ArrayList<>();
            for (String line : loreString.split("\\|")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        player.getInventory().addItem(item);
    }

    private void executeCommandReward(Player player, RewardItem reward) {
        try {
            String[] parts = reward.originalEntry.split(":");
            if (parts.length >= 7) {
                String command = parts[5].replace("%player%", player.getName());
                String customMessage = parts[6];

                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', customMessage));
            }
        } catch (Exception e) {
            getLogger().warning("Komut odulu calistirilirken hata: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("metintasi")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("metintasi.reload")) {
                    sender.sendMessage(getLang("no_permission"));
                    return true;
                }
                reloadPlugin();
                sender.sendMessage(getLang("reload_success"));
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("clearholograms")) {
                if (!sender.hasPermission("metintasi.reload")) {
                    sender.sendMessage(getLang("no_permission"));
                    return true;
                }
                int cleared = clearAllHolograms();
                sender.sendMessage("§a" + cleared + " adet hologram temizlendi!");
                return true;
            }

            sender.sendMessage("§6=== MetinTasi Plugin ===");
            sender.sendMessage("§e/metintasiayarla <isim> §7- Metin taşı ayarla");
            sender.sendMessage("§e/metintasisil <isim> §7- Metin taşı sil");
            sender.sendMessage("§e/metintasi reload §7- Plugin'i yeniden yükle");
            sender.sendMessage("§e/metintasi clearholograms §7- Tüm hologramları temizle");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(getLang("must_be_player"));
            return false;
        }

        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("metintasiayarla")) {
            if (!p.hasPermission("metintasi.ayarla")) {
                p.sendMessage(getLang("no_permission"));
                return true;
            }

            if (args.length != 1) {
                p.sendMessage(getLang("usage_ayarla"));
                return true;
            }

            String isim = args[0].toLowerCase();

            if (metinTaslari.containsKey(isim)) {
                p.sendMessage(getLang("metintasi_already_exists").replace("%isim%", isim));
                return true;
            }

            Block target = p.getTargetBlockExact(5);
            if (target == null) {
                p.sendMessage(getLang("block_not_selected"));
                return true;
            }

            if (target.getType() != Material.OBSIDIAN && target.getType() != Material.SPONGE) {
                p.sendMessage(getLang("invalid_block"));
                return true;
            }

            int maxCan = config.getInt("metintasi_can", 1000);
            MetinTasi metinTasi = new MetinTasi(isim, target.getLocation(), maxCan);
            metinTaslari.put(isim, metinTasi);

            String path = "metin-taslari." + isim;
            dataConfig.set(path + ".location", target.getLocation());
            dataConfig.set(path + ".maxCan", maxCan);
            saveDataFile();

            spawnHologram(metinTasi);

            p.sendMessage(getLang("metintasi_set").replace("%isim%", isim));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("metintasisil")) {
            if (!p.hasPermission("metintasi.sil")) {
                p.sendMessage(getLang("no_permission"));
                return true;
            }

            if (args.length != 1) {
                p.sendMessage(getLang("usage_sil"));
                return true;
            }

            String isim = args[0].toLowerCase();

            if (!metinTaslari.containsKey(isim)) {
                p.sendMessage(getLang("metintasi_not_found").replace("%isim%", isim));
                return true;
            }

            MetinTasi metinTasi = metinTaslari.get(isim);
            removeHologram(metinTasi);
            metinTasi.location.getBlock().setType(Material.AIR);
            metinTaslari.remove(isim);

            dataConfig.set("metin-taslari." + isim, null);
            saveDataFile();

            p.sendMessage(getLang("metintasi_remove").replace("%isim%", isim));
            return true;
        }

        return false;
    }

    private void reloadPlugin() {
        for (MetinTasi metinTasi : metinTaslari.values()) {
            removeHologram(metinTasi);
        }
        metinTaslari.clear();

        reloadConfig();
        config = getConfig();
        loadLangFile();
        loadDataFile();

        freezeSuresi = config.getInt("metintasi_timefreeze", 15);

        int yeniMaxCan = config.getInt("metintasi_can", 1000);

        loadAllMetinTaslari();

        for (String isim : metinTaslari.keySet()) {
            String path = "metin-taslari." + isim;
            dataConfig.set(path + ".maxCan", yeniMaxCan);
        }
        saveDataFile();

        getLogger().info("Plugin basariyla reload edildi! " + metinTaslari.size() + " metin tasi aktif.");
    }

    private int clearAllHolograms() {
        int silinenHologram = 0;

        getLogger().info("Tüm hologramlar temizleniyor...");

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (ArmorStand as : world.getEntitiesByClass(ArmorStand.class)) {
                if (as.isInvisible() && as.isCustomNameVisible() && as.isMarker()) {
                    if (isMetinTasiHologram(as)) {
                        as.remove();
                        silinenHologram++;
                    }
                }
            }
        }

        getLogger().info(silinenHologram + " adet hologram temizlendi!");

        for (MetinTasi metinTasi : metinTaslari.values()) {
            metinTasi.hologram = null;
        }

        return silinenHologram;
    }

    private boolean isMetinTasiHologram(ArmorStand as) {
        if (as.getCustomName() == null) return false;

        String name = ChatColor.stripColor(as.getCustomName());
        return name.contains("HP") ||
                name.contains("Kır ve ödüller kazan") ||
                name.contains("Yenileniyor") ||
                name.contains("⏳") ||
                name.contains("★") ||
                name.contains("METİN") ||
                name.contains("METIN") ||
                name.contains("Yenilenme");
    }

    private void spawnHologram(MetinTasi metinTasi) {
        removeHologram(metinTasi);

        Location loc = metinTasi.location.clone().add(0.5, getHologramHeight(), 0.5);

        ArmorStand titleHologram = loc.getWorld().spawn(loc, ArmorStand.class);
        titleHologram.setInvisible(true);
        titleHologram.setCustomNameVisible(true);
        titleHologram.setMarker(true);
        titleHologram.setGravity(false);
        titleHologram.setSmall(false);
        titleHologram.setCustomName(getHologramTitle(metinTasi.isim));

        Location subtitleLoc = loc.clone().subtract(0, 0.3, 0);
        ArmorStand subtitleHologram = subtitleLoc.getWorld().spawn(subtitleLoc, ArmorStand.class);
        subtitleHologram.setInvisible(true);
        subtitleHologram.setCustomNameVisible(true);
        subtitleHologram.setMarker(true);
        subtitleHologram.setGravity(false);
        subtitleHologram.setSmall(true);
        subtitleHologram.setCustomName(getHologramSubtitle());

        Location healthLoc = loc.clone().subtract(0, 0.6, 0);
        metinTasi.hologram = healthLoc.getWorld().spawn(healthLoc, ArmorStand.class);
        metinTasi.hologram.setInvisible(true);
        metinTasi.hologram.setCustomNameVisible(true);
        metinTasi.hologram.setMarker(true);
        metinTasi.hologram.setGravity(false);
        metinTasi.hologram.setSmall(true);

        updateHologram(metinTasi);
    }

    private void updateHologram(MetinTasi metinTasi) {
        if (metinTasi.hologram != null && !metinTasi.hologram.isDead()) {
            String healthFormat = config.getString("hologram.health_format", "&c❤ &f%current%&7/&c%max%");
            String healthText = healthFormat
                    .replace("%current%", String.valueOf(metinTasi.can))
                    .replace("%max%", String.valueOf(metinTasi.maxCan))
                    .replace("%isim%", metinTasi.isim.toUpperCase());
            metinTasi.hologram.setCustomName(ChatColor.translateAlternateColorCodes('&', healthText));
        }
    }

    private void removeHologram(MetinTasi metinTasi) {
        if (metinTasi.location != null) {
            Location loc = metinTasi.location.clone().add(0.5, getHologramHeight(), 0.5);
            double radius = 3.0;

            for (ArmorStand as : loc.getWorld().getEntitiesByClass(ArmorStand.class)) {
                if (as.getLocation().distance(loc) <= radius &&
                        as.isInvisible() &&
                        as.isCustomNameVisible() &&
                        as.isMarker()) {
                    as.remove();
                }
            }
        }
        metinTasi.hologram = null;
    }

    private double getHologramHeight() {
        return config.getDouble("hologram.height", 1.8);
    }

    private String getHologramTitle(String isim) {
        String title = config.getString("hologram.title", "&6&l★ &e&l%isim% &6&l★");
        title = title.replace("%isim%", isim.toUpperCase());
        return ChatColor.translateAlternateColorCodes('&', title);
    }

    private String getHologramSubtitle() {
        String subtitle = config.getString("hologram.subtitle", "&fKır ve ödüller kazan!");
        return ChatColor.translateAlternateColorCodes('&', subtitle);
    }

    private void setupHologram(ArmorStand hologram, boolean isSmall) {
        hologram.setInvisible(true);
        hologram.setCustomNameVisible(true);
        hologram.setMarker(true);
        hologram.setGravity(false);
        hologram.setSmall(isSmall);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("metintasi")) {
            if (args.length == 1) {
                suggestions.add("reload");
                suggestions.add("clearholograms");
            }
        } else if (command.getName().equalsIgnoreCase("metintasisil")) {
            if (args.length == 1) {
                suggestions.addAll(metinTaslari.keySet());
            }
        }

        return suggestions;
    }
}
