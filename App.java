import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

public class App {

    public static String startIP;
    public static int startPort;
    public static int endPort;
    public static int limit;
    public static long start;
    public static String timeStamp;
    public static volatile boolean stopped = false;
    public static String botName = "Scanner_1337"; // randomised each scan

    public static AtomicInteger scanned = new AtomicInteger(0);
    public static AtomicInteger found = new AtomicInteger(0);

    public static PrintWriter writer;
    private static final Object writerLock = new Object();
    private static final Object consoleLock = new Object();

    public static ExecutorService es;
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String WHITE = "\u001B[97m";

    public static String mcServerInfo(String inputJson) {
        String version = "";
        String players = "";

        Matcher m = Pattern.compile(":\\{\"name\":\"([^\"]+)\"").matcher(inputJson);
        if (m.find())
            version = m.group(1);

        String onlineStr = "";
        String maxStr = "";

        if (!inputJson.contains("\"sample\":[{")) {
            m = Pattern.compile(",\"online\":(\\d+)},\"ver").matcher(inputJson);
        } else {
            m = Pattern.compile(",\"online\":(\\d+),\"sample").matcher(inputJson);
        }
        if (m.find())
            onlineStr = m.group(1);

        m = Pattern.compile(":\\{\"max\":(\\d+),\"online").matcher(inputJson);
        if (m.find())
            maxStr = m.group(1);

        if (!onlineStr.isEmpty())
            players = onlineStr + "/" + maxStr;

        StringBuilder sb = new StringBuilder();
        if (!version.isEmpty())
            sb.append("Version: ").append(version);
        if (!players.isEmpty()) {
            if (sb.length() > 0)
                sb.append(" | ");
            sb.append("Players: ").append(players);
        }
        return sb.toString();
    }

    public static int parseProtocolVersion(String json) {
        Matcher m = Pattern.compile("\"protocol\":(\\d+)").matcher(json);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 47; // fallback
    }

    // Attempt a minecraft server list ping handshake on a single port.
    public static void mcHandShake(String inputIp, int inputPort) {
        if (stopped)
            return;

        String rawJson = null;
        try {
            rawJson = internalMCHandShake(inputIp, inputPort);
        } catch (IOException ignored) {
        }

        if (rawJson == null) {
            // if server is offline
            if (!GUI.onlyPrintOnlineServers) {
                synchronized (writerLock) {
                    if (writer != null)
                        writer.println("[OFFLINE] " + inputIp + ":" + inputPort);
                }
                printOffline(inputIp, inputPort);
            }
        } else {
            // if server is online
            String serverInfo = mcServerInfo(rawJson);
            int detectedProtocol = parseProtocolVersion(rawJson);

            String joinTag = "";
            if (GUI.attemptBotJoin) {
                BotJoiner.Result jr = BotJoiner.tryJoin(inputIp, inputPort, botName, detectedProtocol);
                switch (jr) {
                    case JOINED:
                        joinTag = " | \u001B[32mOFFLINE MODE\u001B[0m (bot joined!)";
                        break;
                    case ONLINE_MODE:
                        joinTag = " | \u001B[33mONLINE MODE\u001B[0m (auth required)";
                        break;
                    case REJECTED:
                        joinTag = " | \u001B[31mREJECTED\u001B[0m (whitelist/ban)";
                        break;
                    default:
                        joinTag = " | \u001B[2mjoin failed\u001B[0m";
                        break;
                }
            }

            String plainJoinTag = joinTag.replaceAll("\u001B\\[[\\d;]*m", "");
            String logEntry = "[ONLINE]  " + inputIp + ":" + inputPort
                    + (serverInfo.isEmpty() ? "" : "  |  " + serverInfo) + plainJoinTag;
            synchronized (writerLock) {
                if (writer != null)
                    writer.println(logEntry);
            }

            found.incrementAndGet();
            printOnline(inputIp, inputPort, serverInfo + joinTag);
        }

        int done = scanned.incrementAndGet();
        printProgress(done, limit, found.get());
        GUI.updateProgress(done, limit, found.get());
    }

    // originally adapted from https://stackoverflow.com/q/30768091
    public static String internalMCHandShake(String inputIp, int inputPort) throws IOException {
        Socket socket = new Socket();
        socket.setSoTimeout(5000);
        socket.connect(new InetSocketAddress(inputIp, inputPort), 3000);

        try {
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            DataInputStream input = new DataInputStream(socket.getInputStream());

            byte[] handshake = createHandshakeMessage(inputIp, inputPort);
            writeVarInt(output, handshake.length);
            output.write(handshake);

            output.writeByte(0x01);
            output.writeByte(0x00);
            output.flush();

            readVarInt(input);
            int packetId = readVarInt(input);
            if (packetId != 0x00)
                throw new IOException("Expected status response (0x00), got: " + packetId);

            int jsonLen = readVarInt(input);
            if (jsonLen < 0)
                throw new IOException("Negative JSON length: " + jsonLen);

            String json = "";
            if (jsonLen > 0) {
                byte[] jsonBytes = new byte[jsonLen];
                input.readFully(jsonBytes);
                json = new String(jsonBytes, StandardCharsets.UTF_8);
            }

            // best effort ping/pong, some servers skip it never let it hide an online
            // server
            try {
                output.writeByte(0x09);
                output.writeByte(0x01);
                output.writeLong(System.currentTimeMillis());
                output.flush();
            } catch (IOException ignored) {
            }

            return json;
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static void startProcess() throws InterruptedException, FileNotFoundException, UnsupportedEncodingException {
        // reset state
        stopped = false;
        scanned.set(0);
        found.set(0);
        start = System.nanoTime();
        timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm").format(new Date());
        if (endPort < startPort)
            throw new IllegalArgumentException("End port must be >= start port.");
        limit = endPort - startPort + 1;
        botName = "Bot_" + (1000 + (int) (Math.random() * 8999));

        synchronized (writerLock) {
            writer = new PrintWriter("Output Log " + timeStamp + ".log", "UTF-8");
            writer.println("# Minecraft Server Port Scanner");
            writer.println(
                    "# Target : " + startIP + " | Port Range: " + startPort + "-" + endPort + " (" + limit + " ports)");
            writer.println("# Speed  : " + GUI.scanSpeed + " | Online only: " + GUI.onlyPrintOnlineServers);
            writer.println("# Scanned: " + timeStamp);
            writer.println("# -----------------------------------------------");
        }
        printHeader();

        int threads;
        switch (GUI.scanSpeed) {
            case "Dangerous":
                threads = 500;
                break;
            case "Very Fast":
                threads = 300;
                break;
            case "Fast":
                threads = 150;
                break;
            default:
                threads = 50;
                break;
        }
        es = Executors.newFixedThreadPool(threads);

        System.out.printf("Scanning %d ports on %s:%d at '%s' speed (%d threads)%n",
                limit, startIP, startPort, GUI.scanSpeed, threads);

        CountDownLatch latch = new CountDownLatch(limit);

        for (int i = 0; i < limit; i++) {
            if (stopped) {
                for (int j = i; j < limit; j++)
                    latch.countDown();
                break;
            }
            final int port = startPort + i;
            es.submit(() -> {
                try {
                    mcHandShake(startIP, port);
                } finally {
                    latch.countDown();
                }
            });

            if (i > 0 && i % 100 == 0 && !stopped) {
                try {
                    switch (GUI.scanSpeed) {
                        case "Medium":
                            Thread.sleep(500);
                            break;
                        case "Fast":
                            Thread.sleep(125);
                            break;
                        case "Very Fast":
                            Thread.sleep(50);
                            break;
                        case "Dangerous":
                            Thread.sleep(10);
                            break;
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        latch.await(); // blocks background thread until all tasks r finished
        es.shutdown();

        long elapsed = (System.nanoTime() - start) / 1_000_000;

        synchronized (writerLock) {
            writer.println("# -----------------------------------------------");
            writer.printf("# Finished in %dms | Found: %d online servers%n", elapsed, found.get());
            writer.close();
        }
        printSummary(elapsed, found.get(), timeStamp);

        GUI.onScanComplete(found.get(), timeStamp);
    }

    /** Prints a startup banner. */
    private static void printHeader() {
        synchronized (consoleLock) {
            System.out.println();
            System.out.println(CYAN + BOLD + "╔══════════════════════════════════════════════╗" + RESET);
            System.out.println(CYAN + BOLD + "║       Minecraft Server Port Scanner          ║" + RESET);
            System.out.println(CYAN + BOLD + "╚══════════════════════════════════════════════╝" + RESET);
            System.out.println(DIM + "  Target     : " + RESET + WHITE + startIP + RESET);
            System.out.println(DIM + "  Port Range : " + RESET + WHITE + startPort + " – " + endPort + RESET +
                    DIM + "  (" + RESET + WHITE + limit + " ports" + RESET + DIM + ")" + RESET);
            System.out.println(DIM + "  Speed  : " + RESET + WHITE + GUI.scanSpeed + RESET +
                    DIM + "  Online-only: " + RESET + WHITE + GUI.onlyPrintOnlineServers + RESET);
            System.out.println(CYAN + "  ──────────────────────────────────────────────" + RESET);
            System.out.println();
        }
    }

    private static void printOnline(String ip, int port, String info) {
        synchronized (consoleLock) {
            String tag = GREEN + BOLD + "[ONLINE]" + RESET;
            String addr = WHITE + BOLD + ip + ":" + port + RESET;
            String meta = info.isEmpty() ? "" : "  " + DIM + info + RESET;
            System.out.println("\r" + tag + "  " + addr + meta);
        }
    }

    private static void printOffline(String ip, int port) {
        synchronized (consoleLock) {
            System.out.println("\r" + DIM + RED + "[OFFLINE]" + RESET +
                    DIM + "  " + ip + ":" + port + RESET);
        }
    }

    private static void printProgress(int done, int total, int foundCount) {
        double pct = total == 0 ? 0 : (done / (double) total) * 100.0;
        int barLen = 28;
        int filled = (int) (pct / 100.0 * barLen);

        StringBuilder bar = new StringBuilder(CYAN + "[" + RESET);
        for (int i = 0; i < barLen; i++) {
            if (i < filled)
                bar.append(GREEN + "=" + RESET);
            else if (i == filled)
                bar.append(CYAN + ">" + RESET);
            else
                bar.append(DIM + " " + RESET);
        }
        bar.append(CYAN + "]" + RESET);

        synchronized (consoleLock) {
            System.out.printf("\r%s %s%.1f%%%s  Scanned: %d/%d  |  %sFound: %d%s   ",
                    bar,
                    YELLOW, pct, RESET,
                    done, total,
                    GREEN, foundCount, RESET);
        }
    }

    private static void printSummary(long elapsedMs, int foundCount, String logTimestamp) {
        synchronized (consoleLock) {
            System.out.println();
            System.out.println();
            System.out.println(CYAN + BOLD + "╔══════════════════════════════════════════════╗" + RESET);
            System.out.println(CYAN + BOLD + "║                Scan Complete                 ║" + RESET);
            System.out.println(CYAN + BOLD + "╚══════════════════════════════════════════════╝" + RESET);
            System.out.printf("  " + GREEN + BOLD + "Servers found : %d%n" + RESET, foundCount);
            System.out.printf("  " + WHITE + "Time elapsed  : %,d ms%n" + RESET, elapsedMs);
            System.out.printf("  " + DIM + "Log saved     : Output Log %s.log%n" + RESET, logTimestamp);
            System.out.println();
        }
    }

    public static void stopProcess() {
        stopped = true;
        if (es != null)
            es.shutdownNow();
        synchronized (writerLock) {
            if (writer != null) {
                writer.println("# Scan stopped by user.");
                writer.close();
            }
        }
    }

    public static byte[] createHandshakeMessage(String host, int port) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream handshake = new DataOutputStream(buffer);
        handshake.writeByte(0x00);
        writeVarInt(handshake, 4);
        writeString(handshake, host, StandardCharsets.UTF_8);
        handshake.writeShort(port);
        writeVarInt(handshake, 1);
        return buffer.toByteArray();
    }

    public static void writeString(DataOutputStream out, String string, Charset charset) throws IOException {
        byte[] bytes = string.getBytes(charset);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    public static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & 0xFFFFFF80) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
    }

    public static int readVarInt(DataInputStream in) throws IOException {
        int i = 0, j = 0;
        while (true) {
            int k = in.readByte();
            i |= (k & 0x7F) << j++ * 7;
            if (j > 5)
                throw new RuntimeException("VarInt too big");
            if ((k & 0x80) != 128)
                break;
        }
        return i;
    }
}
