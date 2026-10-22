package model;

import java.util.List;

/** A place on campus. In the graph, each Location is one vertex. */
public class Location {
    private final String id;
    private final String name;
    private final String category;
    private final boolean rickshawStand;
    private final List<String> aliases;

    public Location(String id, String name, String category, boolean rickshawStand, List<String> aliases) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.rickshawStand = rickshawStand;
        this.aliases = List.copyOf(aliases);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public boolean isRickshawStand() {
        return rickshawStand;
    }

    public List<String> getAliases() {
        return aliases;
    }

    /** Swing combo boxes and lists call toString() to show an item. */
    @Override
    public String toString() {
        return name;
    }
}
