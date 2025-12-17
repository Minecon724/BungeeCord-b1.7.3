package net.md_5.bungee.connection;

import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.EntityMap;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.Util;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.PacketHandler;
import net.md_5.bungee.netty.PacketWrapper;
import net.md_5.bungee.protocol.packet.Packet3Chat;
import net.md_5.bungee.protocol.packet.PacketF9BungeeMessage;

public class UpstreamBridge extends PacketHandler
{

    private final ProxyServer bungee;
    private final UserConnection con;

    public UpstreamBridge(ProxyServer bungee, UserConnection con)
    {
        this.bungee = bungee;
        this.con = con;

        BungeeCord.getInstance().addConnection( con );
        //con.getTabList().onConnect();
        //con.unsafe().sendPacket( BungeeCord.getInstance().registerChannels() );
    }

    @Override
    public void exception(Throwable t) throws Exception
    {
        con.disconnect( Util.exception( t ) );
    }

    @Override
    public void disconnected(ChannelWrapper channel) throws Exception
    {
        // We lost connection to the client
        PlayerDisconnectEvent event = new PlayerDisconnectEvent( con );
        bungee.getPluginManager().callEvent( event );
        //con.getTabList().onDisconnect();
        BungeeCord.getInstance().removeConnection( con );

        if ( con.getServer() != null )
        {
            con.getServer().disconnect( "Quitting" );
        }
    }

    @Override
    public void handle(PacketWrapper packet) throws Exception
    {
        EntityMap.rewrite( packet.buf, con.getClientEntityId(), con.getServerEntityId() );
        if ( con.getServer() != null )
        {
            con.getServer().getCh().write( packet );
        }
    }

    @Override
    public void handle(Packet3Chat chat) throws Exception
    {
        ChatEvent chatEvent = new ChatEvent( con, con.getServer(), chat.getMessage() );
        if ( !bungee.getPluginManager().callEvent( chatEvent ).isCancelled() )
        {
            chat.setMessage( chatEvent.getMessage() );
            if ( !chatEvent.isCommand() || !bungee.getPluginManager().dispatchCommand( con, chat.getMessage().substring( 1 ) ) )
            {
                con.getServer().unsafe().sendPacket( chat );
            }
        }
        throw new CancelSendSignal();
    }

    @Override
    public void handle(PacketF9BungeeMessage bungeeMessage) throws Exception
    {
        throw new CancelSendSignal();
    }

    @Override
    public String toString()
    {
        return "[" + con.getName() + "] -> UpstreamBridge";
    }
}
