package top.mc100.qdbank.client;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class DefaultAccountListener implements Listener {
    private final QdBankPlugin plugin;
    public DefaultAccountListener(QdBankPlugin plugin){this.plugin=plugin;}
    @EventHandler
    public void join(PlayerJoinEvent event){
        plugin.ensureDefaultAccounts(event.getPlayer());
    }
}
