import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.InflaterInputStream;

/**
 * Performs a full Minecraft login + play-state entry so the bot
 * actually appears in the server's terminal log.
 *
 * Full flow (protocol 47 / 1.8.9):
 *
 * [Login state]
 * C->S Handshake (next_state = 2)
 * C->S Login Start
 * S->C 0x03 Set Compression (optional – handle it)
 * S->C 0x02 Login Success (offline mode)
 * 0x01 Encryption Req (online mode – abort)
 * 0x00 Disconnect (rejected)
 *
 * [Play state]
 * S->C 0x01 Join Game ← server logs "X joined the game" here
 * S->C 0x00 Keep Alive ← must echo back or server kicks us
 * ...
 * Bot closes socket → server logs "X left the game"
 */
public class BotJoiner {

    public enum Result {
        JOINED,
        ONLINE_MODE,
        REJECTED,
        FAILED
    }

    private static final int MAX_PLAY_PACKETS = 30; //give up after this many play packets

    public static Result tryJoin(String ip, int port, String botName, int protocolVersion) {
        Socket socket = new Socket();
        try {
            socket.setSoTimeout(8000);
            socket.connect(new InetSocketAddress(ip, port), 3000);

            DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(socket.getInputStream());

            sendHandshake(out, ip, port, protocolVersion);
            sendLoginStart(out, botName, protocolVersion);
            out.flush();

            int compressionThreshold = -1;

            loginLoop: for (int i = 0; i < 15; i++) {
                Packet p = readPacket(in, compressionThreshold);
                switch (p.id) {
                    case 0x02: //login Success
                        break loginLoop;
                    case 0x01:
                        return Result.ONLINE_MODE;
                    case 0x00://disconnect
                        return Result.REJECTED;
                    case 0x03:
                        compressionThreshold = readVarInt(p.data);
                        break;
                    case 0x05:
                        if (protocolVersion >= 766) {
                            String key = readString(p.data);
                            sendLoginCookieResponse(out, key, compressionThreshold);
                            out.flush();
                        }
                        break;
                    default:
                        break;
                }
            }

            if (protocolVersion >= 764) {
                sendLoginAcknowledged(out, compressionThreshold);
                out.flush();

                sendClientInformation(out, protocolVersion, compressionThreshold);
                out.flush();

                configLoop: for (int i = 0; i < 50; i++) {
                    Packet p = readPacket(in, compressionThreshold);

                    if (protocolVersion >= 766) {
                        switch (p.id) {
                            case 0x00:
                                String key = readString(p.data);
                                sendConfigCookieResponse(out, key, compressionThreshold);
                                out.flush();
                                break;
                            case 0x02:
                                return Result.REJECTED;
                            case 0x03:
                                sendConfigFinish(out, protocolVersion, compressionThreshold);
                                out.flush();
                                break configLoop;
                            case 0x04:
                                long kaId = p.data.readLong();
                                sendConfigKeepAlive(out, kaId, protocolVersion, compressionThreshold);
                                out.flush();
                                break;
                            case 0x05:
                                int pingId = p.data.readInt();
                                sendConfigPong(out, pingId, protocolVersion, compressionThreshold);
                                out.flush();
                                break;
                            case 0x0E:
                                int numPacks = readVarInt(p.data);
                                sendServerboundKnownPacks(out, p.data, numPacks, compressionThreshold);
                                out.flush();
                                break;
                        }
                    } else {
                        switch (p.id) {
                            case 0x00:
                                break;
                            case 0x01:
                                return Result.REJECTED;
                            case 0x02:
                                sendConfigFinish(out, protocolVersion, compressionThreshold);
                                out.flush();
                                break configLoop;
                            case 0x03:
                                long kaId = p.data.readLong();
                                sendConfigKeepAlive(out, kaId, protocolVersion, compressionThreshold);
                                out.flush();
                                break;
                            case 0x04:
                                int pingId = p.data.readInt();
                                sendConfigPong(out, pingId, protocolVersion, compressionThreshold);
                                out.flush();
                                break;
                        }
                    }
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
                return Result.JOINED;
            }

            for (int i = 0; i < MAX_PLAY_PACKETS; i++) {
                Packet p = readPacket(in, compressionThreshold);

                if (p.id == 0x01) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                    }
                    return Result.JOINED;
                }

                if (p.id == 0x00) {
                    int keepAliveId = readVarInt(p.data);
                    sendKeepAlive(out, keepAliveId, compressionThreshold);
                    out.flush();
                }
            }

            return Result.JOINED;

        } catch (IOException e) {
            return Result.FAILED;
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static Packet readPacket(DataInputStream in, int compressionThreshold)
            throws IOException {

        int packetLength = readVarInt(in);
        byte[] raw = new byte[packetLength];
        in.readFully(raw);
        DataInputStream buf = new DataInputStream(new ByteArrayInputStream(raw));

        if (compressionThreshold >= 0) {

            int dataLength = readVarInt(buf);
            if (dataLength == 0) {

                int id = readVarInt(buf);
                return new Packet(id, buf);
            } else {

                byte[] compressed = new byte[buf.available()];
                buf.readFully(compressed);
                DataInputStream decompressed = new DataInputStream(
                        new InflaterInputStream(new ByteArrayInputStream(compressed)));
                int id = readVarInt(decompressed);
                return new Packet(id, decompressed);
            }
        } else {
            int id = readVarInt(buf);
            return new Packet(id, buf);
        }
    }

    private static void sendHandshake(DataOutputStream out, String host, int port, int protocolVersion)
            throws IOException {
        byte[] bytes = buildPacket(dos -> {
            dos.writeByte(0x00);
            writeVarInt(dos, protocolVersion);
            writeString(dos, host);
            dos.writeShort(port);
            writeVarInt(dos, 2);
        });
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void sendLoginStart(DataOutputStream out, String name, int protocolVersion)
            throws IOException {
        byte[] bytes = buildPacket(dos -> {
            dos.writeByte(0x00);
            writeString(dos, name);
            //we send a zeroed UUID which is accepted by offline mode servers
            if (protocolVersion >= 759) {
                dos.writeLong(0L);
                dos.writeLong(0L);
            }
        });
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void sendKeepAlive(DataOutputStream out, int keepAliveId, int compressionThreshold)
            throws IOException {
        byte[] bytes = buildPacket(dos -> {
            dos.writeByte(0x00);
            writeVarInt(dos, keepAliveId);
        });
        sendPacket(out, bytes, compressionThreshold);
    }

    private static void sendLoginCookieResponse(DataOutputStream out, String key, int compressionThreshold)
            throws IOException {
        byte[] bytes = buildPacket(dos -> {
            dos.writeByte(0x04);
            writeString(dos, key);
            dos.writeBoolean(false); //no payload
        });
        sendPacket(out, bytes, compressionThreshold);
    }

    private static void sendConfigCookieResponse(DataOutputStream out, String key, int compressionThreshold)
            throws IOException {
        byte[] bytes = buildPacket(dos -> {
            dos.writeByte(0x01);
            writeString(dos, key);
            dos.writeBoolean(false);
        });
        sendPacket(out, bytes, compressionThreshold);
    }

    private static void sendConfigKeepAlive(DataOutputStream out, long kaId, int protocolVersion,
            int compressionThreshold) throws IOException {
        byte[] bytes = buildPacket(dos -> {
            int id = (protocolVersion >= 766) ? 0x04 : 0x03;
            dos.writeByte(id);
            dos.writeLong(kaId);
        });
        sendPacket(out, bytes, compressionThreshold);
    }

    private static void sendConfigPong(DataOutputStream out, int pingId, int protocolVersion, int compressionThreshold)
            throws IOException {
        byte[] bytes = buildPacket(dos -> {
            int id = (protocolVersion >= 766) ? 0x05 : 0x04;
            dos.writeByte(id);
            dos.writeInt(pingId);
        });
        sendPacket(out, bytes, compressionThreshold);
    }

    private static void sendConfigFinish(DataOutputStream out, int protocolVersion, int compressionThreshold)
            throws IOException {
        byte[] bytes = buildPacket(dos -> {
            if (protocolVersion >= 766) {
                dos.writeByte(0x03);
            } else {
                dos.writeByte(0x02);
            }
        });
        sendPacket(out, bytes, compressionThreshold);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void sendLoginAcknowledged(DataOutputStream out, int compressionThreshold) throws IOException {
        byte[] bytes = buildPacket(dos -> {
            dos.writeByte(0x03);
        });
        sendPacket(out, bytes, compressionThreshold);
    }

    private static void sendClientInformation(DataOutputStream out, int protocolVersion, int compressionThreshold)
            throws IOException {
        byte[] bytes = buildPacket(dos -> {
            dos.writeByte(0x00);
            writeString(dos, "en_us");
            dos.writeByte(10);
            writeVarInt(dos, 0);
            dos.writeBoolean(true);
            dos.writeByte(127);
            writeVarInt(dos, 1);
            dos.writeBoolean(false);
            dos.writeBoolean(true);
            if (protocolVersion >= 768) {
                writeVarInt(dos, 0);
            }
        });
        sendPacket(out, bytes, compressionThreshold);
    }

    private static void sendServerboundKnownPacks(DataOutputStream out, DataInputStream in, int numPacks,
            int compressionThreshold) throws IOException {
        byte[] bytes = buildPacket(dos -> {
            dos.writeByte(0x07);
            writeVarInt(dos, numPacks);
            for (int i = 0; i < numPacks; i++) {
                String namespace = readString(in);
                String id = readString(in);
                String version = readString(in);
                writeString(dos, namespace);
                writeString(dos, id);
                writeString(dos, version);
            }
        });
        sendPacket(out, bytes, compressionThreshold);
    }

    private static void sendPacket(DataOutputStream out, byte[] payload, int compressionThreshold) throws IOException {
        if (compressionThreshold >= 0) {
            writeVarInt(out, 1 + payload.length);
            writeVarInt(out, 0);
            out.write(payload);
        } else {
            writeVarInt(out, payload.length);
            out.write(payload);
        }
    }

    @FunctionalInterface
    interface PacketWriter {
        void write(DataOutputStream dos) throws IOException;
    }

    private static byte[] buildPacket(PacketWriter writer) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(buf);
        writer.write(dos);
        return buf.toByteArray();
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & 0xFFFFFF80) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
    }

    static int readVarInt(DataInputStream in) throws IOException {
        int value = 0, shift = 0;
        while (true) {
            byte b = in.readByte();
            value |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35)
                throw new IOException("VarInt too large");
            if ((b & 0x80) == 0)
                return value;
        }
    }

    private static class Packet {
        final int id;
        final DataInputStream data;

        Packet(int id, DataInputStream data) {
            this.id = id;
            this.data = data;
        }
    }

    private static class ByteArrayInputStream extends java.io.ByteArrayInputStream {
        ByteArrayInputStream(byte[] buf) {
            super(buf);
        }
    }
}
