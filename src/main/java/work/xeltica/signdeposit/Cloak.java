package work.xeltica.signdeposit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

@SerializableAs("Cloak")
public class Cloak implements ConfigurationSerializable {
    public static final UUID publicCloakUUID = new UUID(0, 0);

    public Cloak() {
    }

    public Cloak(Location location, String playerId) {
        setLocation(location);
        setPlayerId(playerId);
        setLevel(level);
    }

    @Override
    public Map<String, Object> serialize() {
        var m = new HashMap<String, Object>();
        m.put("location", getLocation());
        m.put("playerId", getPlayerId());
        m.put("level", getLevel());
        return m;
    }

    public static Cloak deserialize(Map<String, Object> map) {
        var cloak = new Cloak();

        cloak.setLocation((Location)map.get("location"));
        cloak.setPlayerId((String) map.get("playerId"));
        cloak.setLevel((int) map.get("level"));

        return cloak;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerUUID() {
        return UUID.fromString(this.playerId);
    }

    public void setPlayerUUID(UUID playerId) {
        this.playerId = playerId.toString();
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public boolean isPublic() {
        return getPlayerUUID().equals(publicCloakUUID);
    }

    private Location location;
    private String playerId;
    private int level;

}
