package net.md_5.bungee.connection;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.*;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.PacketHandler;
import net.md_5.bungee.netty.PacketWrapper;
import net.md_5.bungee.protocol.packet.PacketF9BungeeMessage;
import net.md_5.bungee.protocol.packet.PacketFFKick;

@RequiredArgsConstructor
public class DownstreamBridge extends PacketHandler
{

    private final ProxyServer bungee;
    private final UserConnection con;
    private final ServerConnection server;

    // We cannot initialize this, it won't compile
    private BungeeMessageHandler bungeeMessageHandler;

    @Override
    public void exception(Throwable t) throws Exception
    {
        ServerInfo def = bungee.getServerInfo( con.getPendingConnection().getListener().getFallbackServer() );
        if ( server.getInfo() != def )
        {
            server.setObsolete( true );
            con.connectNow( def );
            con.sendMessage( bungee.getTranslation( "server_went_down" ) );
        } else
        {
            con.disconnect( Util.exception( t ) );
        }
    }

    @Override
    public void disconnected(ChannelWrapper channel) throws Exception
    {
        // We lost connection to the server
        server.getInfo().removePlayer( con );
        if ( bungee.getReconnectHandler() != null )
        {
            bungee.getReconnectHandler().setServer( con );
        }

        if ( !server.isObsolete() )
        {
            con.disconnect( bungee.getTranslation( "lost_connection" ) );
        }
    }

    @Override
    public void handle(PacketWrapper packet) throws Exception
    {
        if ( !server.isObsolete() )
        {
            EntityMap.rewrite( packet.buf, con.getServerEntityId(), con.getClientEntityId() );
            con.sendPacket( packet );
        }
    }

    @Override
    public void handle(PacketF9BungeeMessage bungeeMessage) throws Exception
    {
        if (bungeeMessageHandler == null) {
            bungeeMessageHandler = new BungeeMessageHandler(server, con);
        }

        bungeeMessageHandler.handleBungeeMessage( bungeeMessage );

        throw new CancelSendSignal();
    }

    @Override
    public void handle(PacketFFKick kick) throws Exception
    {
        ServerInfo def = bungee.getServerInfo( con.getPendingConnection().getListener().getFallbackServer() );
        if ( Objects.equals( server.getInfo(), def ) )
        {
            def = null;
        }
        ServerKickEvent event = bungee.getPluginManager().callEvent( new ServerKickEvent( con, kick.getMessage(), def, ServerKickEvent.State.CONNECTED ) );
        if ( event.isCancelled() && event.getCancelServer() != null )
        {
            con.connectNow( event.getCancelServer() );
        } else
        {
            con.disconnect( bungee.getTranslation( "server_kick" ) + event.getKickReason() );
        }
        server.setObsolete( true );
        throw new CancelSendSignal();
    }

    @Override
    public String toString()
    {
        return "[" + con.getName() + "] <-> DownstreamBridge <-> [" + server.getInfo().getName() + "]";
    }
}
