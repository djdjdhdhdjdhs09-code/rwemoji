package rw.emoji;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.*;

/** The emoji panel: search box, section picker, pages, click = copy the :shortcode:. */
final class Browser implements Listener {
    private record Session(String query, int page, Section section) {}

    private final RWemoji plugin;
    private final Catalog catalog;
    private final Map<UUID, Session> sessions = new HashMap<>();

    Browser(RWemoji plugin, Catalog catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { sessions.remove(e.getPlayer().getUniqueId()); }

    void clear() { sessions.clear(); }

    private ClickCallback.Options once() {
        return ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
    }

    private DialogAction nav(UUID id, int delta) {
        return DialogAction.customClick((view, audience) -> {
            Session old = sessions.get(id);
            String text = view.getText("search");
            String sec = view.getText("section");
            Section section = sec == null ? (old == null ? Section.ALL : old.section()) : Section.byName(sec);
            String query = text == null ? "" : text;
            boolean sameView = old != null && old.section() == section && old.query().equals(norm(query));
            int page = (delta == 0 || !sameView) ? 1 : old.page() + delta;
            Player p = Bukkit.getPlayer(id);
            if (p == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> open(p, query, page, section));
        }, once());
    }

    private static String norm(String q) {
        String s = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        if (s.startsWith(":")) s = s.substring(1);
        if (s.endsWith(":")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private ActionButton button(Component label, Component tip, int width, DialogAction action) {
        return ActionButton.builder(label).tooltip(tip).width(width).action(action).build();
    }

    void open(Player p, String query, int page, Section section) {
        String q = norm(query);
        List<String> match = new ArrayList<>();
        for (String a : catalog.entries(section)) if (q.isEmpty() || a.contains(q)) match.add(a);
        int per = Math.max(6, plugin.getConfig().getInt("browser.per-page", 30));
        int cols = Math.max(1, Math.min(5, plugin.getConfig().getInt("browser.columns", 3)));
        int pages = Math.max(1, (match.size() + per - 1) / per);
        int pg = Math.max(1, Math.min(pages, page));
        List<String> slice = match.subList((pg - 1) * per, Math.min(match.size(), pg * per));
        UUID id = p.getUniqueId();
        sessions.put(id, new Session(q, pg, section));

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(Component.text("< Previous", NamedTextColor.RED), Component.text("Previous page"), 150, nav(id, -1)));
        buttons.add(button(Component.text("Search / Apply", NamedTextColor.YELLOW), Component.text("Search and show page 1"), 150, nav(id, 0)));
        buttons.add(button(Component.text("Next >", NamedTextColor.GREEN), Component.text("Next page"), 150, nav(id, 1)));
        for (String alias : slice) {
            Component label = Component.text().append(catalog.icon(alias))
                    .append(Component.text(" " + shorten(alias, 18), NamedTextColor.WHITE)).build();
            Component tip = Component.text(":" + alias + ":", NamedTextColor.YELLOW)
                    .append(Component.text("  (click to copy)", NamedTextColor.GRAY));
            buttons.add(button(label, tip, 150, DialogAction.staticAction(ClickEvent.copyToClipboard(":" + alias + ":"))));
        }

        List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
        for (Section s : Section.values()) {
            options.add(SingleOptionDialogInput.OptionEntry.create(s.name(),
                    Component.text(s.label + " (" + catalog.entries(s).size() + ")"), s == section));
        }
        Component info = Component.text("Page " + pg + " / " + pages + "  -  " + match.size()
                + " emojis. Click one to copy its shortcode, then paste it in chat.", NamedTextColor.GRAY);
        DialogBase base = DialogBase.builder(Component.text("RWemoji", NamedTextColor.GOLD))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(info)))
                .inputs(List.of(
                        DialogInput.text("search", Component.text("Search")).width(300).maxLength(64).initial(q).build(),
                        DialogInput.singleOption("section", 300, options, Component.text("Section"), true)))
                .build();
        ActionButton close = ActionButton.builder(Component.text("Close", NamedTextColor.RED)).width(150).build();
        Dialog dialog = Dialog.create(f -> f.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).columns(cols).exitAction(close).build()));
        p.showDialog(dialog);
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
          }
