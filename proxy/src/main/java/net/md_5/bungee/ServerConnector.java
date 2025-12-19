package net.md_5.bungee;

import com.google.common.base.Preconditions;

import java.util.Objects;
import java.util.Queue;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.connection.BungeeMessageHandler;
import net.md_5.bungee.connection.CancelSendSignal;
import net.md_5.bungee.connection.DownstreamBridge;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.PacketHandler;
import net.md_5.bungee.protocol.packet.DefinedPacket;
import net.md_5.bungee.protocol.packet.Packet1Login;
import net.md_5.bungee.protocol.packet.PacketF9BungeeMessage;
import net.md_5.bungee.protocol.packet.PacketFFKick;
import net.md_5.bungee.protocol.Vanilla;

@RequiredArgsConstructor
public class ServerConnector extends PacketHandler
{

	private final static int MAGIC_HEADER = 2;
    private final ProxyServer bungee;
    private ChannelWrapper ch;
    private final UserConnection user;
    private final BungeeServerInfo target;
    private State thisState = State.LOGIN;

    private ServerConnection server;
    private BungeeMessageHandler bungeeMessageHandler;

    private enum State
    {

        LOGIN, FINISHED
    }

    @Override
    public void exception(Throwable t) throws Exception
    {
        String message = "Exception Connecting:" + Util.exception( t );
        if ( user.getServer() == null )
        {
            user.disconnect( message );
        } else
        {
            user.sendMessage( ChatColor.RED + message );
        }
    }

    @Override
    public void connected(ChannelWrapper channel)
    {
        this.ch = channel;

        this.server = new ServerConnection(ch, target);
        this.bungeeMessageHandler = new BungeeMessageHandler(server, user);

        if (user.getHandshakingServer() != null) {
            user.getHandshakingServer().setObsolete(true);
            user.getHandshakingServer().disconnect("Connected to another server");
        }
        user.setHandshakingServer( server );

        channel.write( user.getPendingConnection().getHandshake() );

        boolean ipForwardingEnabled = BungeeCord.getInstance().config.isIpForwarding();
        long address = ipForwardingEnabled ? Util.addressToLoginPacketValue(user.getAddress().getAddress()) : 0;
        byte header = (byte) (ipForwardingEnabled ? MAGIC_HEADER : 0);

        // This is sent to the server
        channel.write(new Packet1Login(Vanilla.PROTOCOL_VERSION, user.getDisplayName(), address, header));
    }

    @Override
    public void disconnected(ChannelWrapper channel)
    {
        user.getPendingConnects().remove( target );
    }

    @Override
    public void handle(Packet1Login login)
    {
        Preconditions.checkState( thisState == State.LOGIN, "Not exepcting LOGIN" );

        ServerConnectedEvent event = new ServerConnectedEvent( user, server );
        bungee.getPluginManager().callEvent( event );

        // TODO: ch.write( BungeeCord.getInstance().registerChannels() );
        Queue<DefinedPacket> packetQueue = target.getPacketQueue();
        synchronized ( packetQueue )
        {
            while ( !packetQueue.isEmpty() )
            {
                ch.write( packetQueue.poll() );
            }
        }

        synchronized ( user.getSwitchMutex() )
        {
            // Once again, first connection
            user.setClientEntityId( login.getProtocolVersion() );
            user.setServerEntityId( login.getProtocolVersion() );

            // This is sent to the client
            Packet1Login modLogin = new Packet1Login( login.getProtocolVersion(), "", 0, (byte) 0 );
            user.unsafe().sendPacket( modLogin );
            
            if ( user.getServer() != null )
            {
                user.sendDimensionSwitch(login.getDimension());
                
                // Remove from old servers
                user.getServer().setObsolete( true );
                user.getServer().disconnect( "Connected to another server" );
            }

            // TODO: Fix this?
            if ( !user.isActive() )
            {
                server.disconnect( "Connected to another server" );
                // Silly server admins see stack trace and die
                bungee.getLogger().warning( "No client connected for pending server!" );
                return;
            }

            // Add to new server
            // TODO: Move this to the connected() method of DownstreamBridge
            target.addPlayer( user );
            user.getPendingConnects().remove( target );

            user.setServer( server );
            user.setHandshakingServer( null );
            ch.getHandle().pipeline().get( HandlerBoss.class ).setHandler( new DownstreamBridge( bungee, user, server ) );
        }

        bungee.getPluginManager().callEvent( new ServerSwitchEvent( user ) );

        thisState = State.FINISHED;

        throw new CancelSendSignal();
    }

    @Override
    public void handle(PacketFFKick kick)
    {
        if (user.getHandshakingServer() != null && user.getHandshakingServer() != user.getServer()) {
            return;
        }

        ServerInfo def = bungee.getServerInfo( user.getPendingConnection().getListener().getFallbackServer() );
        if ( Objects.equals( target, def ) )
        {
            def = null;
        }
        ServerKickEvent event = bungee.getPluginManager().callEvent( new ServerKickEvent( user, kick.getMessage(), def, ServerKickEvent.State.CONNECTING ) );
        if ( event.isCancelled() && event.getCancelServer() != null )
        {
            user.connect( event.getCancelServer() );
            return;
        }

        String message = bungee.getTranslation( "connect_kick", target.getName(), event.getKickReason() );
        if ( user.getServer() == null )
        {
            user.disconnect( message );
        } else
        {
            user.sendMessage( message );
        }
    }

    @Override
    public void handle(PacketF9BungeeMessage bungeeMessage) throws Exception
    {
        bungeeMessageHandler.handleBungeeMessage( bungeeMessage );

        throw new CancelSendSignal();
    }

    @Override
    public String toString()
    {
        return "[" + user.getName() + "] <-> ServerConnector [" + target.getName() + "]";
    }
}
