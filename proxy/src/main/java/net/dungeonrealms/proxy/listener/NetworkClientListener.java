package net.dungeonrealms.proxy.listener;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import net.dungeonrealms.common.network.PingResponse;
import net.dungeonrealms.common.network.ServerAddress;
import net.dungeonrealms.common.network.ShardInfo;
import net.dungeonrealms.common.network.ping.ServerPinger;
import net.dungeonrealms.common.network.ping.type.BungeePingResponse;
import net.dungeonrealms.network.GameClient;
import net.dungeonrealms.network.packet.type.BasicMessagePacket;
import net.dungeonrealms.proxy.DungeonBungee;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Class written by APOLLOSOFTWARE.IO on 7/15/2016
 */
public class NetworkClientListener extends Listener
{

    public void startInitialization(GameClient client)
    {
        if (client == null) return;

        DungeonBungee.getBungeeLogger().log(Level.INFO, "[UNFIXED]: NetworkClientListener.java is enabling");
        client.registerListener(this);
    }


    public void stopInvocation(GameClient client)
    {
        if (client == null) return;

        DungeonBungee.getBungeeLogger().log(Level.INFO, "[UNFIXED]: NetworkClientListener.java is enabling");
        client.removeListener(this);
    }


    @Override
    public void received(Connection connection, Object object)
    {
        if (object instanceof BasicMessagePacket)
        {
            BasicMessagePacket packet = (BasicMessagePacket) object;

            byte[] data = packet.data;
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

            try
            {
                String task = in.readUTF();

                if (task.equals("MoveSessionToken"))
                {
                    UUID uuid = UUID.fromString(in.readUTF());

                    getProxy().getScheduler().runAsync(DungeonBungee.getInstance(), () -> {
                        ProxiedPlayer player = getProxy().getPlayer(uuid);

                        Iterator<ServerInfo> optimalShardFinder = getOptimalShards().iterator();
                        while (optimalShardFinder.hasNext())
                        {
                            ServerInfo target = optimalShardFinder.next();

                            try
                            {
                                PingResponse ping = new BungeePingResponse(ServerPinger.fetchData(new ServerAddress(target.getAddress().getHostName(), target.getAddress().getPort()), 20));
                                if (!ping.isOnline() || ping.getMotd().contains("offline"))
                                {

                                    if (!optimalShardFinder.hasNext())
                                    {
                                        // CONNECT THEM TO LOBBY LOAD BALANCER //
                                        player.connect(getProxy().getServerInfo("Lobby"));
                                        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Unable to find a session for you.");
                                        return;
                                    }

                                    continue;
                                }
                            } catch (Exception e)
                            {

                                if (!optimalShardFinder.hasNext())
                                {
                                    // CONNECT THEM TO LOBBY LOAD BALANCER //
                                    player.connect(getProxy().getServerInfo("Lobby"));
                                    player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Unable to find a session for you.");
                                    return;
                                }

                                continue;
                            }

                            if (target.canAccess(player) && !(player.getServer() != null && player.getServer().getInfo().equals(target)))
                            {
                                player.sendMessage(ChatColor.GRAY + "" + ChatColor.BOLD + "Moving your current session...");
                                player.connect(target);
                                break;
                            } else if (!optimalShardFinder.hasNext())
                            {
                                // CONNECT THEM TO LOBBY LOAD BALANCER //
                                player.connect(getProxy().getServerInfo("Lobby"));
                                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Unable to find a session for you.");
                                return;
                            }
                        }
                    });

                }

            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }


    private ProxyServer getProxy()
    {
        return DungeonBungee.getInstance().getProxy();
    }


    public List<ServerInfo> getOptimalShards()
    {
        List<ServerInfo> servers = new ArrayList<>();

        for (ShardInfo shardInfo : ShardInfo.values())
        {
            // We want to only put them on a US as they may fail the criteria for another shard.
            // They are free to join another shard once connected.

            String name = shardInfo.getPseudoName();
            if (name.startsWith("us") && !name.equalsIgnoreCase("us0"))
                servers.add(getProxy().getServerInfo(name));
        }

        Collections.sort(servers, (o1, o2) -> ((o1.getPlayers().size())) - (o2.getPlayers().size()));
        return servers;
    }

}
