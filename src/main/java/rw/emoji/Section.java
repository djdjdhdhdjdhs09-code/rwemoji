package rw.emoji;

enum Section {
    ALL("All"), ITEMS("Items"), BLOCKS("Blocks"), PARTICLES("Particles"), GUI("GUI"), TRIMS("Trims"),
    ENTITIES("Entities"), EFFECTS("Effects"), PAINTINGS("Paintings"), MAPS("Maps"), SKY("Sky"), OTHER("Other");

    final String label;

    Section(String label) { this.label = label; }

    static Section of(String atlas, String path) {
        switch (atlas) {
            case "items": return ITEMS;
            case "blocks": return BLOCKS;
            case "particles": return PARTICLES;
            case "armor_trims": return TRIMS;
            case "paintings": return PAINTINGS;
            case "map_decorations": return MAPS;
            case "celestials": return SKY;
            case "banner_patterns", "shield_patterns", "decorated_pot", "chests", "shulker_boxes", "beds", "signs":
                return ENTITIES;
            case "gui": return path.startsWith("mob_effect/") ? EFFECTS : GUI;
            default: return OTHER;
        }
    }

    static Section byName(String s) {
        for (Section x : values()) if (x.name().equalsIgnoreCase(s)) return x;
        return ALL;
    }
}
