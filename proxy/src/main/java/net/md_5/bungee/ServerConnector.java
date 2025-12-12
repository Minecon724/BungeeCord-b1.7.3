package net.md_5.bungee;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import java.io.DataInput;
import java.security.PublicKey;
import java.util.Objects;
import java.util.Queue;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.connection.CancelSendSignal;
import net.md_5.bungee.connection.DownstreamBridge;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.PacketDecoder;
import net.md_5.bungee.netty.PacketHandler;
import net.md_5.bungee.netty.PipelineUtils;
import net.md_5.bungee.protocol.MinecraftOutput;
import net.md_5.bungee.protocol.packet.DefinedPacket;
import net.md_5.bungee.protocol.packet.Packet1Login;
import net.md_5.bungee.protocol.packet.Packet9Respawn;
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
    private boolean sentMessages;

    private enum State
    {

        LOGIN, FINISHED;
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
    public void connected(ChannelWrapper channel) throws Exception
    {
        this.ch = channel;

        // TODO: Figure out if this is needed or not
        /*ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF( "Login" );
        out.writeUTF( user.getAddress().getHostString() );
        out.writeInt( user.getAddress().getPort() );*/
        //channel.write( new PacketF9BungeeMessage( out.toByteArray() ) );

        channel.write( user.getPendingConnection().getHandshake() );

        // IP Forwarding
        boolean flag = BungeeCord.getInstance().config.isIpForwarding();
        long address = flag ? Util.serializeAddress(user.getAddress().getAddress().getHostAddress()) : 0;
        byte header = (byte) (flag ? MAGIC_HEADER : 0);
        // end
        channel.write(new Packet1Login(Vanilla.PROTOCOL_VERSION, user.getPendingConnection().getHandshake().getUsername(), address, header));
    }

    @Override
    public void disconnected(ChannelWrapper channel) throws Exception
    {
        user.getPendingConnects().remove( target );
    }

    @Override
    public void handle(Packet1Login login) throws Exception
    {
        Preconditions.checkState( thisState == State.LOGIN, "Not exepcting LOGIN" );

        ServerConnection server = new ServerConnection( ch, target );
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
            user.setClientEntityId( login.getEntityId() );
            user.setServerEntityId( login.getEntityId() );

            // Set tab list size, this sucks balls, TODO: what shall we do about packet mutability
            Packet1Login modLogin = new Packet1Login( login.getEntityId(), login.getLevelType(), login.getSeed(), (byte) login.getDimension() );
            user.unsafe().sendPacket( modLogin );
            
            if ( user.getServer() != null )
            {
                user.sendDimensionSwitch(login.getDimension());
                
                // Remove from old servers
                user.getServer().setObsolete( true );
                user.getServer().disconnect( "Quitting" );
            }

            // TODO: Fix this?
            if ( !user.isActive() )
            {
                server.disconnect( "Quitting" );
                // Silly server admins see stack trace and die
                bungee.getLogger().warning( "No client connected for pending server!" );
                return;
            }

            // Add to new server
            // TODO: Move this to the connected() method of DownstreamBridge
            target.addPlayer( user );
            user.getPendingConnects().remove( target );

            user.setServer( server );
            ch.getHandle().pipeline().get( HandlerBoss.class ).setHandler( new DownstreamBridge( bungee, user, server ) );
        }

        bungee.getPluginManager().callEvent( new ServerSwitchEvent( user ) );

        thisState = State.FINISHED;

        throw new CancelSendSignal();
    }

    @Override
    public void handle(PacketFFKick kick) throws Exception
    {
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

        String message = bungee.getTranslation( "connect_kick" ) + target.getName() + ": " + event.getKickReason();
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
    }

    @Override
    public String toString()
    {
        return "[" + user.getName() + "] <-> ServerConnector [" + target.getName() + "]";
    }
}
