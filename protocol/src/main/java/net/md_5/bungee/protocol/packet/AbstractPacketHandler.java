package net.md_5.bungee.protocol.packet;

public abstract class AbstractPacketHandler
{
    public void handle(Packet1Login login) throws Exception
    {
    }

    public void handle(Packet2Handshake handshake) throws Exception
    {
    }

    public void handle(Packet3Chat chat) throws Exception
    {
    }

    public void handle(Packet9Respawn respawn) throws Exception
    {
    }

    public void handle(PacketF9BungeeMessage bungeeMessage) throws Exception
    {
    }

    public void handle(PacketFFKick kick) throws Exception
    {
    }
}
