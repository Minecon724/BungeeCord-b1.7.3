package net.md_5.bungee.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.BungeeServerInfo;
import net.md_5.bungee.ServerConnector;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.protocol.Vanilla;

public class PipelineUtils
{

    public static final AttributeKey<ListenerInfo> LISTENER = AttributeKey.valueOf( "ListerInfo" );
    public static final AttributeKey<UserConnection> USER = AttributeKey.valueOf( "User" );
    public static final AttributeKey<BungeeServerInfo> TARGET = AttributeKey.valueOf( "Target" );
    public static final ChannelInitializer<Channel> SERVER_CHILD = new ChannelInitializer<Channel>()
    {
        @Override
        protected void initChannel(Channel ch) throws Exception
        {
            if ( BungeeCord.getInstance().getConnectionThrottle().throttle( ( (InetSocketAddress) ch.remoteAddress() ).getAddress() ) )
            {
                ch.close();
                return;
            }

            BASE.initChannel( ch );
            ch.pipeline().get( HandlerBoss.class ).setHandler( new InitialHandler( ProxyServer.getInstance(), ch.attr( LISTENER ).get() ) );
        }
    };
    public static final ChannelInitializer<Channel> CLIENT = new ChannelInitializer<Channel>()
    {
        @Override
        protected void initChannel(Channel ch) throws Exception
        {
            BASE.initChannel( ch );
            ch.pipeline().get( HandlerBoss.class ).setHandler( new ServerConnector( ProxyServer.getInstance(), ch.attr( USER ).get(), ch.attr( TARGET ).get() ) );
        }
    };
    public static final Base BASE = new Base();
    private static final DefinedPacketEncoder packetEncoder = new DefinedPacketEncoder();
    public static String TIMEOUT_HANDLER = "timeout";
    public static String PACKET_DECODE_HANDLER = "packet-decoder";
    public static String PACKET_ENCODE_HANDLER = "packet-encoder";
    public static String BOSS_HANDLER = "inbound-boss";

    public final static class Base extends ChannelInitializer<Channel>
    {

        @Override
        public void initChannel(Channel ch) throws Exception
        {
            ch.config().setOption( ChannelOption.TCP_NODELAY, true );

            ch.pipeline().addLast( TIMEOUT_HANDLER, new ReadTimeoutHandler( BungeeCord.getInstance().config.getTimeout(), TimeUnit.MILLISECONDS ) );
            ch.pipeline().addLast( PACKET_DECODE_HANDLER, new PacketDecoder( Vanilla.getInstance() ) );
            ch.pipeline().addLast( PACKET_ENCODE_HANDLER, packetEncoder );
            ch.pipeline().addLast( BOSS_HANDLER, new HandlerBoss() );
        }
    };
}
