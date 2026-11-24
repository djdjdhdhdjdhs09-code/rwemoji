package rw.emoji;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.util.*;

public final class RWemoji extends JavaPlugin implements Listener, TabExecutor {
    private Catalog catalog;
    private Browser browser;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        char group = group(Bukkit.getMinecraftVersion());
        try (InputStream in = getResource("sprites.tsv.gz")) {
            catalog = new Catalog(in, group);
        } catch (Exception ex) {
            getLogger().severe("Cannot load emojis: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        browser = new Browser(this, catalog);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(browser, this);
        getCommand("rwemoji").setExecutor(this);
        getCommand("rwemoji").setTabCompleter(this);
        getLogger().info("Loaded " + catalog.size() + " emojis for Minecraft " + Bukkit.getMinecraftVersion() + ".");
    }

    @Override
    public void onDisable() {
        if (browser != null) browser.clear();
    }

    /** 1 = 1.21.11, 2 = 26.1.x, 3 = 26.2 and newer */
    static char group(String v) {
        try {
            String[] p = v.split("\\.");
            int major = Integer.parseInt(p[0]);
            if (major == 1) return '1';
            int minor = p.length > 1 ? Integer.parseInt(p[1].replaceAll("\\D.*", "")) : 0;
            if (major > 26 || minor >= 2) return '3';
            return '2';
        } catch (RuntimeException ex) {
            return '3';
        }
    }

    static boolean isBedrock(UUID id) {
        try {
            Class<?> c = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = c.getMethod("getInstance").invoke(null);
            return (boolean) c.getMethod("isFloodgatePlayer", UUID.class).invoke(api, id);
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- chat ----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!getConfig().getBoolean("chat.enabled", true)) return;
        if (!e.getPlayer().hasPermission("rwemoji.use")) return;
        boolean fallback = getConfig().getBoolean("bedrock-fallback", true);
        var old = e.renderer();
        e.renderer((source, name, message, viewer) -> {
            Component out = old.render(source, name, message, viewer);
            return textOnly(viewer, fallback) ? catalog.parseText(out) : catalog.parse(out);
        });
    }

    private boolean textOnly(Audience viewer, boolean fallback) {
        if (viewer instanceof Player p) return fallback && isBedrock(p.getUniqueId());
        return true; // console
    }

    // ---- signs ----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        if (!getConfig().getBoolean("signs.enabled", true)) return;
        if (!e.getPlayer().hasPermission("rwemoji.use")) return;
        for (int i = 0; i < 4; i++) {
            Component line = e.line(i);
            if (line != null) e.line(i, catalog.parse(line));
        }
    }

    // ---- command ----
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!s.hasPermission("rwemoji.admin")) { s.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true; }
                reloadConfig();
                s.sendMessage(Component.text("[RWemoji] Config reloaded.", NamedTextColor.GREEN));
            }
            case "list", "search" -> {
                if (!(s instanceof Player p)) { s.sendMessage(Component.text("Players only. " + catalog.size() + " emojis loaded.")); return true; }
                if (!p.hasPermission("rwemoji.list")) { p.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true; }
                String query = "";
                int page = 1;
                if (sub.equals("search")) {
                    query = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
                } else if (a.length > 1) {
                    try { page = Integer.parseInt(a[1]); }
                    catch (NumberFormatException ex) { p.sendMessage(Component.text("Page must be a number.", NamedTextColor.RED)); return true; }
                }
                if (isBedrock(p.getUniqueId())) { bedrockList(p, query); return true; }
                browser.open(p, query, page, Section.ALL);
            }
            default -> s.sendMessage(Component.text("Usage: /rwemoji <list [page]|search <text>|reload>", NamedTextColor.RED));
        }
        return true;
    }

    private void bedrockList(Player p, String query) {
        String q = query.toLowerCase(Locale.ROOT).strip();
        List<String> out = new ArrayList<>();
        for (String a : catalog.entries(Section.ALL)) {
            if (q.isEmpty() || a.contains(q)) out.add(a);
            if (out.size() >= 40) break;
        }
        p.sendMessage(Component.text("Emojis (type :name: in chat): " + String.join(", ", out), NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) {
            List<String> r = new ArrayList<>(List.of("list", "search"));
            if (s.hasPermission("rwemoji.admin")) r.add("reload");
            r.removeIf(x -> !x.startsWith(a[0].toLowerCase(Locale.ROOT)));
            return r;
        }
        return List.of();
    }
              }
