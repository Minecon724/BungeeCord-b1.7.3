package net.md_5.bungee.command;

import java.util.Map;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

/**
 * Command to list and switch a player between available servers.
 */
public class CommandServer extends Command
{

    public CommandServer()
    {
        super( "server", "bungeecord.command.server" );
    }

    @Override
    public void execute(CommandSender sender, String[] args)
    {
        if ( !(sender instanceof ProxiedPlayer player) )
        {
            return;
        }
        Map<String, ServerInfo> servers = ProxyServer.getInstance().getServers();
        if ( args.length == 0 )
        {
            player.sendMessage( ProxyServer.getInstance().getTranslation( "current_server", player.getServer().getInfo().getName() ) );

            StringBuilder serverList = new StringBuilder();
            for ( ServerInfo server : servers.values() )
            {
                if ( server.canAccess( player ) )
                {
                    serverList.append( server.getName() );
                    serverList.append( ", " );
                }
            }
            if (!serverList.isEmpty())
            {
                serverList.setLength( serverList.length() - 2 );
            }
            player.sendMessage( ProxyServer.getInstance().getTranslation( "server_list", serverList.toString() ) );
        } else
        {
            ServerInfo server = servers.get( args[0] );
            if ( server == null )
            {
                player.sendMessage( ProxyServer.getInstance().getTranslation( "no_server", args[0] ) );
            } else if ( !server.canAccess( player ) )
            {
                player.sendMessage( ProxyServer.getInstance().getTranslation( "no_server_permission", args[0] ) );
            } else
            {
                player.connect( server );
            }
        }
    }
}
