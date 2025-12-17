package net.md_5.bungee.connection;

import com.google.common.base.Preconditions;
import io.netty.util.concurrent.ScheduledFuture;

import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.Random;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.Util;
import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.http.HttpClient;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.PacketHandler;
import net.md_5.bungee.protocol.Vanilla;
import net.md_5.bungee.protocol.packet.DefinedPacket;
import net.md_5.bungee.protocol.packet.Packet1Login;
import net.md_5.bungee.protocol.packet.Packet2Handshake;
import net.md_5.bungee.protocol.packet.PacketF9BungeeMessage;
import net.md_5.bungee.protocol.packet.PacketFEPing;
import net.md_5.bungee.protocol.packet.PacketFFKick;
import net.md_5.bungee.api.AbstractReconnectHandler;
import net.md_5.bungee.api.event.PlayerHandshakeEvent;

@RequiredArgsConstructor
public class InitialHandler extends PacketHandler implements PendingConnection
{

	private final Random rand = new Random();
    private final ProxyServer bungee;
    private ChannelWrapper ch;
    @Getter
    private final ListenerInfo listener;
    @Getter
    private Packet2Handshake handshake;
    private State thisState = State.HANDSHAKE;
    private final Unsafe unsafe = new Unsafe()
    {
        @Override
        public void sendPacket(DefinedPacket packet)
        {
            ch.write( packet );
        }
    };
    @Getter
    private boolean onlineMode = BungeeCord.getInstance().config.isOnlineMode();
    private ScheduledFuture<?> pingFuture;
    private InetSocketAddress vHost;
    private byte version = -1;
    private String serverId;

    private enum State
    {

        HANDSHAKE, LOGIN, FINISHED;
    }

    @Override
    public void connected(ChannelWrapper channel) throws Exception
    {
        this.ch = channel;
    }

    @Override
    public void exception(Throwable t) throws Exception
    {
        disconnect( ChatColor.RED + Util.exception( t ) );
    }

    @Override
    public void handle(PacketF9BungeeMessage pluginMessage) throws Exception
    {
    }

    private void respondToPing()
    {
        ServerInfo forced = AbstractReconnectHandler.getForcedHost( this );
        final String motd = ( forced != null ) ? forced.getMotd() : listener.getMotd();

        Callback<ServerPing> pingBack = new Callback<ServerPing>()
        {
            @Override
            public void done(ServerPing result, Throwable error)
            {
                if ( error != null )
                {
                    result = new ServerPing( (byte) -1, "-1", "Error pinging remote server: " + Util.exception( error ), -1, -1 );
                }
                result = bungee.getPluginManager().callEvent( new ProxyPingEvent( InitialHandler.this, result ) ).getResponse();

                String kickMessage = ChatColor.DARK_BLUE
                        + "\00" + result.getProtocolVersion()
                        + "\00" + result.getGameVersion()
                        + "\00" + result.getMotd()
                        + "\00" + result.getCurrentPlayers()
                        + "\00" + result.getMaxPlayers();
                BungeeCord.getInstance().getConnectionThrottle().unthrottle( getAddress().getAddress() );
                disconnect( kickMessage );
            }
        };

        pingBack.done( new ServerPing( bungee.getProtocolVersion(), bungee.getGameVersion(), motd, bungee.getOnlineCount(), listener.getMaxPlayers() ), null );
    }

    @Override
    public void handle(PacketFEPing ping) throws Exception
    {
        pingFuture = ch.getHandle().eventLoop().schedule( new Runnable()
        {
            @Override
            public void run()
            {
                respondToPing();
            }
        }, 200, TimeUnit.MILLISECONDS );
    }

    @Override
    public void handle(Packet1Login login) throws Exception
    {
        Preconditions.checkState( thisState == State.LOGIN, "Not expecting LOGIN" );
		
		if ( login.getProtocolVersion() > Vanilla.PROTOCOL_VERSION )
        {
            disconnect( bungee.getTranslation( "outdated_server" ) );
        } else if ( login.getProtocolVersion() < Vanilla.PROTOCOL_VERSION )
        {
            disconnect( bungee.getTranslation( "outdated_client" ) );
        }
		
		if ( this.onlineMode )
        {
            String encName = URLEncoder.encode( InitialHandler.this.getName(), "UTF-8" );
            String encodedHash = URLEncoder.encode( InitialHandler.this.serverId, "UTF-8" );
            String authURL = "http://session.minecraft.net/game/checkserver.jsp?user=" + encName + "&serverId=" + encodedHash;

            Callback<String> handler = new Callback<String>()
            {
                @Override
                public void done(String result, Throwable error)
                {
                    if ( error == null )
                    {
                        if ( "YES".equals( result ) )
                        {
                            finish();
                        } else
                        {
                            disconnect( "Not authenticated with Minecraft.net" );
                        }
                    } else
                    {
                        disconnect( bungee.getTranslation( "mojang_fail" ) );
                        bungee.getLogger().log( Level.SEVERE, "Error authenticating " + getName() + " with minecraft.net", error );
                    }
                }
            };

            HttpClient.get( authURL, ch.getHandle().eventLoop(), handler );
        } else
        {
            finish();
        }
    }

    @Override
    public void handle(Packet2Handshake handshake) throws Exception
    {
        Preconditions.checkState( thisState == State.HANDSHAKE, "Not expecting HANDSHAKE" );
        this.handshake = handshake;
        this.vHost = new InetSocketAddress( "localhost", 1234 );
        bungee.getLogger().log( Level.INFO, "{0} has connected", this );

        bungee.getPluginManager().callEvent( new PlayerHandshakeEvent( InitialHandler.this, handshake ) );

        if ( handshake.getUsername().length() > 16 )
        {
            disconnect( "Cannot have username longer than 16 characters" );
            return;
        }

        int limit = BungeeCord.getInstance().config.getPlayerLimit();
        if ( limit > 0 && bungee.getOnlineCount() > limit )
        {
            disconnect( bungee.getTranslation( "proxy_full" ) );
            return;
        }

        // If offline mode and they are already on, don't allow connect
        if ( !isOnlineMode() && bungee.getPlayer( handshake.getUsername() ) != null )
        {
            disconnect( bungee.getTranslation( "already_connected" ) );
            return;
        }
		
		if ( isOnlineMode() ) {
            this.serverId = Long.toHexString( this.rand.nextLong() );
			unsafe().sendPacket( new Packet2Handshake( this.serverId ) );
		} else {
			unsafe().sendPacket( new Packet2Handshake( "-" ) ); // TODO
		}
        thisState = State.LOGIN;
    }

    private void finish()
    {
        // Check for multiple connections
        ProxiedPlayer old = bungee.getPlayer( handshake.getUsername() );
        if ( old != null )
        {
            old.disconnect( bungee.getTranslation( "already_connected" ) );
        }

        // TODO: fire login event for plugin compatibility
        // bungee.getPluginManager().callEvent( new LoginEvent( InitialHandler.this, complete ) );
		
		UserConnection userCon = new UserConnection( bungee, ch, getName(), this );
        userCon.init();

        bungee.getPluginManager().callEvent( new PostLoginEvent( userCon ) );

        ch.getHandle().pipeline().get( HandlerBoss.class ).setHandler( new UpstreamBridge( bungee, userCon ) );

        ServerInfo server;
        if ( bungee.getReconnectHandler() != null )
        {
            server = bungee.getReconnectHandler().getServer( userCon );
        } else
        {
            server = AbstractReconnectHandler.getForcedHost( this );
        }
        userCon.connect( server, true );

        thisState = State.FINISHED;
    }

    @Override
    public synchronized void disconnect(String reason)
    {
        if ( !ch.isClosed() )
        {
            unsafe().sendPacket( new PacketFFKick( reason ) );
            ch.close();
        }
    }

    @Override
    public String getName()
    {
        return ( handshake == null ) ? null : handshake.getUsername();
    }

    @Override
    public InetSocketAddress getVirtualHost()
    {
        return vHost;
    }

    @Override
    public InetSocketAddress getAddress()
    {
        return (InetSocketAddress) ch.getHandle().remoteAddress();
    }

    @Override
    public Unsafe unsafe()
    {
        return unsafe;
    }

    public void setOnlineMode(boolean onlineMode)
    {
        Preconditions.checkState( thisState == State.HANDSHAKE, "Can only set online mode status whilst handshaking" );
        this.onlineMode = onlineMode;
    }

    @Override
    public String toString()
    {
        return "[" + ( ( getName() != null ) ? getName() : getAddress() ) + "] <-> InitialHandler";
    }
}
