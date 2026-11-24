package rw.emoji;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.PatternReplacementResult;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** All sprites that exist in the running Minecraft version, keyed by their :shortcode:. */
final class Catalog {
    static final Pattern TOKEN = Pattern.compile(":([a-z0-9_./-]+):");

    private final Map<String, Component> icons = new HashMap<>();
    private final Map<Section, List<String>> sections = new EnumMap<>(Section.class);

    /** group: 1 = 1.21.11, 2 = 26.1.x, 3 = 26.2 and newer */
    Catalog(InputStream gz, char group) throws IOException {
        for (Section s : Section.values()) sections.put(s, new ArrayList<>());
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(gz), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length < 4 || p[3].indexOf(group) < 0) continue;
                if (icons.containsKey(p[0])) continue;
                icons.put(p[0], Component.object(ObjectContents.sprite(Key.key("minecraft", p[1]), Key.key("minecraft", p[2])))
                        .color(NamedTextColor.WHITE));
                sections.get(Section.ALL).add(p[0]);
                sections.get(Section.of(p[1], p[2])).add(p[0]);
            }
        }
        for (List<String> l : sections.values()) Collections.sort(l);
    }

    int size() { return icons.size(); }

    boolean has(String alias) { return icons.containsKey(alias); }

    Component icon(String alias) { return icons.get(alias); }

    List<String> entries(Section s) { return sections.get(s); }

    /** Replaces every :alias: with its sprite. Unknown :words: are left as they are. */
    Component parse(Component in) {
        return in.replaceText(TextReplacementConfig.builder()
                .match(TOKEN)
                .condition((m, count, replaced) -> icons.containsKey(m.group(1))
                        ? PatternReplacementResult.REPLACE : PatternReplacementResult.CONTINUE)
                .replacement((m, b) -> icons.get(m.group(1)))
                .build());
    }

    /** For Bedrock players: :heart: becomes [heart]. */
    Component parseText(Component in) {
        return in.replaceText(TextReplacementConfig.builder()
                .match(TOKEN)
                .condition((m, count, replaced) -> icons.containsKey(m.group(1))
                        ? PatternReplacementResult.REPLACE : PatternReplacementResult.CONTINUE)
                .replacement((m, b) -> Component.text("[" + m.group(1) + "]"))
                .build());
    }
      }
