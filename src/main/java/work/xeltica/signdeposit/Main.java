package work.xeltica.signdeposit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class Main extends JavaPlugin implements Listener {
    private YamlConfiguration cloakRegistry;
    private List<Cloak> cloaks;

    private final File cloakPath = new File(getDataFolder(), "cloak.yml");

    @Override
    public void onEnable() {

        getServer().getPluginManager().registerEvents(this, this);

        cloakRegistry = YamlConfiguration.loadConfiguration(cloakPath);

        cloaks = (List<Cloak>)cloakRegistry.getList("cloaks", new ArrayList<Cloak>());
    }

    @Override
    public void onDisable() {

    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        var b = e.getBlock();
        var p = e.getPlayer();
        
        var cloak = getCloak(b);
        if (cloak != null) {
            if (!cloak.isPublic() && !cloak.getPlayerUUID().equals(e.getPlayer().getUniqueId())) {
                e.setCancelled(true);
                sendError(p, "権限がありません");
                return;
            }
            cloaks.remove(cloak);
            saveCloak();
            p.setLevel(p.getLevel() + cloak.getLevel());
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1, 1);
        }
    }

    private void sendError(Player p, String message) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + message));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1, 0.5f);
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent e) {
        HandleGriefed(e);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        HandleGriefed(e);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        var b = e.getClickedBlock();

        if (e.getAction() == Action.LEFT_CLICK_AIR) return;
        if (e.getAction() == Action.RIGHT_CLICK_AIR) return;

        var p = e.getPlayer();
        var isSneaking = p.isSneaking();
        var mode = switch (e.getAction()) {
            case LEFT_CLICK_BLOCK ->  "withdraw";
            case RIGHT_CLICK_BLOCK-> "deposit";
            default -> null;
        };

        var cloak = getCloak(b);
        if (cloak == null) return;

        if (!cloak.getPlayerId().equals(p.getUniqueId().toString()) && !cloak.isPublic()) {
            sendError(e.getPlayer(), "あなたのクロークではない");
            return;
        }
        
        var amount = (mode == "deposit" ? 1 : -1) * (isSneaking ? 10 : 1);

        // 預けるモードで預けるとレベルが負数になってしまうか、預入/引出量が0の場合はエラー音を出す
        if (p.getLevel() - amount < 0 || cloak.getLevel() + amount < 0 || amount == 0) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.BLOCKS, 1, 1.2f);
            return;
        }

        cloak.setLevel(cloak.getLevel() + amount);

        saveCloak();

        p.setLevel(p.getLevel() - amount);
        p.playSound(
            p.getLocation(),
            isSneaking ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
            SoundCategory.BLOCKS, 
            1, 
            mode == "deposit" ? 0.8f : 1
        );

        var sign = (Sign)e.getClickedBlock().getState();
        var rendered = renderCloak(cloak);
        
        for (var i = 0; i < 4; i++)
            sign.setLine(i, rendered[i]);

        sign.update();
    }

    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        if (e.getLine(0).equalsIgnoreCase("[exp]")) {
            var b = e.getBlock();
            try {
                // バグで看板扱いされない材質の看板があるので弾く
                if (!Tag.SIGNS.isTagged(b.getType())) {
                    e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1, 1);
                    sendError(e.getPlayer(), "オークの看板を使ってください");
                    return;
                }
                // その場所にクロークを過去に立てていて、プラグインの不具合や、未導入時の破壊などで消えてしまった場合に残ってる場合がある
                var cloak = getCloak(b);
                if (cloak == null) {
                    cloak = e.getLine(1).toLowerCase().contains("public") ? registerCloak(b.getLocation()) : registerCloak(e.getPlayer(), b.getLocation());
                }
                var rendered = renderCloak(cloak);
                for (var i = 0; i < 4; i++) {
                    e.setLine(i, rendered[i]);
                }
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 1, 1);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private Cloak registerCloak(Player p, Location loc) throws IOException {
        return registerCloak(p.getUniqueId(), loc);
    }

    private Cloak registerCloak(Location loc) throws IOException {
        return registerCloak(Cloak.publicCloakUUID, loc);
    }

    private Cloak registerCloak(UUID uuid, Location loc) throws IOException {
        var cloak = new Cloak(loc, uuid.toString());

        cloaks.add(cloak);
        saveCloak();

        return cloak;
    }

    private void saveCloak() {
        cloakRegistry.set("cloaks", cloaks);
        try {
            cloakRegistry.save(cloakPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String[] renderCloak(Cloak c) {
        String name;

        if (c.isPublic()) {
            name = "共用";
        } else {
            var player = getServer().getPlayer(c.getPlayerUUID());
            name = player != null ? player.getName() : "不明";
        }

        return new String[]  {
            "[§bEXP Cloak§r]",
            "§cOwner§r: §a" + name,
            "",
            "§aLevel " + c.getLevel(),
        };
    }

    private Cloak getCloak(Block b) {
        if (b == null)
            return null;
        if (!Tag.SIGNS.isTagged(b.getType()))
            return null;
        return cloaks.stream()
            .filter(c -> c.getLocation().equals(b.getLocation()))
            .findFirst()
            .orElse(null);
    }

    private void HandleGriefed(BlockEvent e) {
        Block b = e.getBlock();
        var cloak = getCloak(b);
        if (cloak != null) {
            var ent = b.getWorld().spawnEntity(b.getLocation(), EntityType.EXPERIENCE_ORB);
            if (ent instanceof ExperienceOrb) {
                // プレイヤーが死んだときのように、レベル * 7の経験値を消失時に落とす
                ((ExperienceOrb) ent).setExperience(cloak.getLevel() * 7);
            }
            cloaks.remove(cloak);
            saveCloak();
        }
    }
}
