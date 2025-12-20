package net.md_5.bungee;

import com.google.common.base.Preconditions;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.internal.PlatformDependent;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.logging.Level;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PermissionCheckEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.netty.PacketWrapper;
import net.md_5.bungee.netty.PipelineUtils;
import net.md_5.bungee.protocol.packet.*;
import net.md_5.bungee.util.CaseInsensitiveSet;

@RequiredArgsConstructor
public final class UserConnection implements ProxiedPlayer
{

    /*========================================================================*/
    @NonNull
    private final ProxyServer bungee;
    @NonNull
    private final ChannelWrapper ch;
    @Getter
    @NonNull
    private final String name;
    @Getter
    private final InitialHandler pendingConnection;
    /*========================================================================*/
    @Getter
    @Setter
    private ServerConnection handshakingServer;
    @Getter
    @Setter
    private ServerConnection server;
    @Getter
    private final Object switchMutex = new Object();
    @Getter
    private final Collection<ServerInfo> pendingConnects = new HashSet<>();
    /*========================================================================*/
    /*@Getter
    @Setter
    private int sentPingId;
    @Getter
    @Setter
    private long sentPingTime;
    @Getter
    @Setter
    private int ping = 100;*/
    @Getter
    @Setter
    private ServerInfo reconnectServer;
    /*========================================================================*/
    private final Collection<String> groups = new CaseInsensitiveSet();
    private final Collection<String> permissions = new CaseInsensitiveSet();
    /*========================================================================*/
    @Getter
    @Setter
    private int clientEntityId;
    @Getter
    @Setter
    private int serverEntityId;
    /*========================================================================*/
    @Getter
    private String displayName;
    /*========================================================================*/
    private final Unsafe unsafe = new Unsafe()
    {
        @Override
        public void sendPacket(DefinedPacket packet)
        {
            ch.write( packet );
        }
    };

    public void init()
    {
        this.displayName = name;
        /*try
        {
            this.tabList = getPendingConnection().getListener().getTabList().getDeclaredConstructor().newInstance();
        } catch ( ReflectiveOperationException ex )
        {
            throw new RuntimeException( ex );
        }*/
        //this.tabList.init( this );

        Collection<String> g = bungee.getConfigurationAdapter().getGroups( name );
        for ( String s : g )
        {
            addGroups( s );
        }
    }

    public void sendPacket(PacketWrapper packet)
    {
        ch.write( packet );
    }

    @Deprecated
    public boolean isActive()
    {
        return !ch.isClosed();
    }

    @Override
    public void setDisplayName(String name)
    {
        Preconditions.checkNotNull( name, "displayName" );
        Preconditions.checkArgument( name.length() <= 16, "Display name cannot be longer than 16 characters" );
        //getTabList().onDisconnect();
        displayName = name;
        //getTabList().onConnect();
    }

    @Override
    public void connect(ServerInfo target)
    {
        connect( target, false );
    }

    void sendDimensionSwitch(int targetDimension)
    {
        unsafe().sendPacket( new Packet9Respawn((byte) targetDimension == -1 ? 1 : -1));
        unsafe().sendPacket( new Packet9Respawn((byte) targetDimension));
    }

    public void connectNow(ServerInfo target)
    {
        sendDimensionSwitch(-1);
        connect( target );
    }

    public void connect(ServerInfo info, final boolean retry)
    {
        Preconditions.checkNotNull( info, "info" );

        ServerConnectEvent event = new ServerConnectEvent( this, info );
        if ( bungee.getPluginManager().callEvent( event ).isCancelled() )
        {
            return;
        }

        final BungeeServerInfo target = (BungeeServerInfo) event.getTarget(); // Update in case the event changed target

        if ( getServer() != null && Objects.equals( getServer().getInfo(), target ) )
        {
            sendMessage( bungee.getTranslation( "already_connected" ) );
            return;
        }
        if ( pendingConnects.contains( target ) )
        {
            sendMessage( bungee.getTranslation( "already_connecting" ) );
            return;
        }

        pendingConnects.add( target );

        ChannelInitializer<Channel> initializer = new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                PipelineUtils.BASE.initChannel(ch);
                ch.pipeline().get(HandlerBoss.class).setHandler(new ServerConnector(bungee, UserConnection.this, target));
            }
        };
        ChannelFutureListener listener = future -> {
            if ( !future.isSuccess() )
            {
                future.channel().close();
                pendingConnects.remove( target );

                ServerInfo def = ProxyServer.getInstance().getServers().get( getPendingConnection().getListener().getFallbackServer() );
                if ( def != null && retry && target != def && ( getServer() == null || def != getServer().getInfo() ) )
                {
                    sendMessage( bungee.getTranslation( "fallback_lobby" ) );
                    connect( def, false );
                } else
                {
                    bungee.getLogger().log( Level.INFO, "Could not connect %s to %s: ".formatted(displayName, target.getName()), future.cause() );

                    if ( server == null )
                    {
                        disconnect( bungee.getTranslation( "fallback_kick" ) );
                    } else
                    {
                        sendMessage( bungee.getTranslation( "fallback_kick" ) );
                    }
                }
            }
        };
        Bootstrap b = new Bootstrap()
                .channel( NioSocketChannel.class )
                .group( BungeeCord.getInstance().eventLoops )
                .handler( initializer )
                .option( ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000 ) // TODO: Configurable
                .remoteAddress( target.getAddress() );
        // Windows is bugged, multi homed users will just have to live with random connecting IPs
        if ( getPendingConnection().getListener().isSetLocalAddress() && !PlatformDependent.isWindows() )
        {
            b.localAddress( getPendingConnection().getListener().getHost().getHostString(), 0 );
        }
        b.connect().addListener( listener );
    }

    @Override
    public synchronized void disconnect(String reason)
    {
        if ( ch.getHandle().isActive() )
        {
            bungee.getLogger().log( Level.INFO, "[" + getName() + "] disconnected with: " + reason );
            unsafe().sendPacket( new PacketFFKick( reason.substring(0, Math.min( 100, reason.length() ) ) ) );
            ch.close();
            if ( server != null )
            {
                server.disconnect( "Quitting" );
            }
        }
    }

    @Override
    public void chat(String message)
    {
        Preconditions.checkState( server != null, "Not connected to server" );
        server.getCh().write( new Packet3Chat( message ) );
    }

    @Override
    public void sendMessage(String message)
    {
        unsafe().sendPacket( new Packet3Chat( message ) );
    }

    @Override
    public void sendMessages(String... messages)
    {
        for ( String message : messages )
        {
            sendMessage( message );
        }
    }

    @Override
    public void sendData(byte[] data)
    {
        unsafe().sendPacket( new PacketF9BungeeMessage( data ) );
    }

    @Override
    public InetSocketAddress getAddress()
    {
        return (InetSocketAddress) ch.getHandle().remoteAddress();
    }

    @Override
    public Collection<String> getGroups()
    {
        return Collections.unmodifiableCollection( groups );
    }

    @Override
    public void addGroups(String... groups)
    {
        for ( String group : groups )
        {
            this.groups.add( group );
            for ( String permission : bungee.getConfigurationAdapter().getPermissions( group ) )
            {
                setPermission( permission, true );
            }
        }
    }

    @Override
    public void removeGroups(String... groups)
    {
        for ( String group : groups )
        {
            this.groups.remove( group );
            for ( String permission : bungee.getConfigurationAdapter().getPermissions( group ) )
            {
                setPermission( permission, false );
            }
        }
    }

    @Override
    public boolean hasPermission(String permission)
    {
        return bungee.getPluginManager().callEvent( new PermissionCheckEvent( this, permission, permissions.contains( permission ) ) ).hasPermission();
    }

    @Override
    public void setPermission(String permission, boolean value)
    {
        if ( value )
        {
            permissions.add( permission );
        } else
        {
            permissions.remove( permission );
        }
    }

    @Override
    public String toString()
    {
        return name;
    }

    @Override
    public Unsafe unsafe()
    {
        return unsafe;
    }
}
