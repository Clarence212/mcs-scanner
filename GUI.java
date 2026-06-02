import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

public class GUI {

    public static JFrame f = new JFrame("MC Scanner");
    public static JPanel thePanel = new JPanel();
    public static JProgressBar scanProgress;
    public static JLabel statsLabel;
    public static JLabel exitLabel = new JLabel();
    public static JPanel completionPanel = new JPanel();
    private static JButton scanAgainButton;
    private static Process activeProcess;

    public static JTextField ipBox;
    public static JTextField portBox;
    public static JTextField endPortBox;
    public static JCheckBox onlineCB;
    public static JCheckBox botJoinCB;
    private static JButton startButton;
    private static JComboBox<String> speedDropDown;

    public static String scanSpeed = "Fast";
    public static boolean onlyPrintOnlineServers = true;
    public static boolean attemptBotJoin = false;

    public static void updateProgress(int done, int total, int foundCount) {
        if (scanProgress != null && statsLabel != null) {
            SwingUtilities.invokeLater(() -> {
                int pct = total == 0 ? 0 : (int) ((done / (double) total) * 100);
                scanProgress.setValue(pct);
                scanProgress.setString(pct + "%");
                statsLabel.setText("Scanned: " + done + " / " + total + "  |  Found: " + foundCount);
            });
        }
    }

    public static void onScanComplete(int foundCount, String timestamp) {
        if (scanProgress != null && statsLabel != null) {
            SwingUtilities.invokeLater(() -> {
                scanProgress.setValue(100);
                scanProgress.setString("Complete!");
                statsLabel.setText("Done! Found " + foundCount + " servers.");

                thePanel.setVisible(false);
                exitLabel.setText("<html><body style='padding:8px;'>"
                        + "<b>Scan Complete!</b><br>"
                        + "Found <b>" + foundCount + "</b> server(s).<br>"
                        + "Log saved: <i>Output Log " + timestamp + ".log</i>"
                        + "</body></html>");
                f.add(completionPanel, BorderLayout.CENTER);
                f.revalidate();
                f.repaint();
            });
        }
    }

    GUI() {
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setLayout(new BorderLayout());

        buildPanel();
        f.add(thePanel, BorderLayout.CENTER);

        exitLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        exitLabel.setVisible(true);

        completionPanel.setLayout(new BorderLayout());
        completionPanel.add(exitLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        scanAgainButton = new JButton("Scan Again");
        scanAgainButton.setFocusable(false);
        scanAgainButton.addActionListener(e -> {
            f.remove(completionPanel);
            thePanel.setVisible(true);
            resetUI("Ready.");
            f.revalidate();
            f.repaint();
        });
        buttonPanel.add(scanAgainButton);
        completionPanel.add(buttonPanel, BorderLayout.SOUTH);

        f.setSize(270, 205);
        f.setResizable(false);
        f.setLocationRelativeTo(null);

        Image icon = Toolkit.getDefaultToolkit().getImage("src\\icon.png");
        f.setIconImage(icon);
        f.setVisible(true);
    }

    private void buildPanel() {
        thePanel.setLayout(null);

        JLabel ipLabel = new JLabel("IP:");
        ipLabel.setBounds(5, 7, 22, 20);
        ipBox = new JTextField("Enter Server IP");
        ipBox.setBounds(28, 7, 135, 20);
        ipBox.setForeground(Color.LIGHT_GRAY);
        ipBox.setToolTipText("IP address to scan");
        addPlaceholder(ipBox, "Enter Server IP");

        JLabel endPortLabel = new JLabel("End Port:");
        endPortLabel.setBounds(170, 7, 58, 20);
        endPortBox = new JTextField("End Port");
        endPortBox.setBounds(170, 27, 90, 20);
        endPortBox.setForeground(Color.LIGHT_GRAY);
        endPortBox.setToolTipText("Last port to scan (must be >= Start Port, max 65535)");
        addPlaceholder(endPortBox, "End Port");

        JLabel portLabel = new JLabel("Start Port:");
        portLabel.setBounds(5, 32, 65, 20);
        portBox = new JTextField("Enter Start Port");
        portBox.setBounds(72, 32, 95, 20);
        portBox.setForeground(Color.LIGHT_GRAY);
        portBox.setToolTipText("Port number to begin scanning from (0-65535)");
        addPlaceholder(portBox, "Enter Start Port");

        onlineCB = new JCheckBox("Only print online servers");
        onlineCB.setBounds(2, 56, 195, 20);
        onlineCB.setSelected(true);
        onlineCB.setFocusable(false);
        onlineCB.setOpaque(false);
        onlineCB.addItemListener(e -> onlyPrintOnlineServers = onlineCB.isSelected());

        botJoinCB = new JCheckBox("Attempt Bot Join");
        botJoinCB.setBounds(2, 76, 175, 20);
        botJoinCB.setSelected(false);
        botJoinCB.setFocusable(false);
        botJoinCB.setOpaque(false);
        botJoinCB
                .setToolTipText("After finding an online server, try to log in as a bot (detects offline/online mode)");
        botJoinCB.addItemListener(e -> attemptBotJoin = botJoinCB.isSelected());

        JLabel speedLabel = new JLabel("Speed:");
        speedLabel.setBounds(5, 100, 45, 18);
        speedDropDown = new JComboBox<>(new String[] { "Medium", "Fast", "Very Fast", "Dangerous" });
        speedDropDown.setSelectedItem("Fast");
        speedDropDown.setBounds(52, 100, 95, 18);
        speedDropDown.setFocusable(false);

        startButton = new JButton("Go!");
        startButton.setBounds(185, 94, 72, 28);
        startButton.setFocusable(false);
        startButton.addActionListener(this::onStartStop);

        scanProgress = new JProgressBar(0, 100);
        scanProgress.setBounds(5, 124, 252, 18);
        scanProgress.setString("Idle");
        scanProgress.setStringPainted(true);

        statsLabel = new JLabel("Ready.");
        statsLabel.setBounds(5, 145, 252, 16);
        statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statsLabel.setForeground(Color.DARK_GRAY);

        for (Component c : new Component[] {
                ipLabel, ipBox, endPortLabel, endPortBox,
                portLabel, portBox, onlineCB, botJoinCB,
                speedLabel, speedDropDown, startButton,
                scanProgress, statsLabel
        })
            thePanel.add(c);
    }

    private void onStartStop(ActionEvent e) {
        if (startButton.getText().equals("Stop")) {
            if (activeProcess != null) {
                activeProcess.destroyForcibly();
            }
            resetUI("Stopped.");
            return;
        }

        boolean ok = true;
        ipBox.setBackground(Color.WHITE);
        portBox.setBackground(Color.WHITE);
        endPortBox.setBackground(Color.WHITE);

        String ipText = ipBox.getText().trim();
        String portText = portBox.getText().trim();
        String endPortText = endPortBox.getText().trim();

        if (ipText.isEmpty() || ipText.equals("Enter Server IP") || !ipText.contains(".")) {
            ipBox.setBackground(new Color(255, 180, 180));
            ok = false;
        }
        int portVal = -1;
        try {
            portVal = Integer.parseInt(portText);
            if (portVal < 0 || portVal > 65535)
                throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            portBox.setBackground(new Color(255, 180, 180));
            ok = false;
        }
        int endPortVal = -1;
        try {
            endPortVal = Integer.parseInt(endPortText);
            if (endPortVal < 0 || endPortVal > 65535)
                throw new NumberFormatException();
            if (portVal >= 0 && endPortVal < portVal)
                throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            endPortBox.setBackground(new Color(255, 180, 180));
            ok = false;
        }

        if (!ok)
            return;

        onlyPrintOnlineServers = onlineCB.isSelected();
        scanSpeed = (String) speedDropDown.getSelectedItem();
        attemptBotJoin = botJoinCB.isSelected();

        final String finalIpText = ipText;
        final int finalPortVal = portVal;
        final int finalEndPortVal = endPortVal;
        final String finalScanSpeed = scanSpeed;
        final boolean finalOnlyPrintOnline = onlyPrintOnlineServers;
        final boolean finalAttemptBotJoin = attemptBotJoin;

        setControlsEnabled(false);
        startButton.setText("Stop");
        scanProgress.setIndeterminate(true);
        scanProgress.setString("Scanning...");
        statsLabel.setText("Scan running in terminal window...");

        Thread launchThread = new Thread(() -> {
            try {
                String javaExe = System.getProperty("java.home")
                        + java.io.File.separator
                        + "bin"
                        + java.io.File.separator
                        + "java.exe";

                String jarPath = new java.io.File(
                        GUI.class.getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI()
                ).getAbsolutePath();

                ProcessBuilder pb;
                if (jarPath.endsWith(".jar")) {
                    pb = new ProcessBuilder(
                            "cmd",
                            "/c",
                            "start",
                            "cmd",
                            "/k",
                            javaExe, "-jar", jarPath, "--scan",
                            finalIpText, String.valueOf(finalPortVal), String.valueOf(finalEndPortVal),
                            finalScanSpeed, String.valueOf(finalOnlyPrintOnline), String.valueOf(finalAttemptBotJoin)
                    );
                } else {
                    pb = new ProcessBuilder(
                            "cmd",
                            "/c",
                            "start",
                            "cmd",
                            "/k",
                            javaExe, "-cp", jarPath, "GUI", "--scan",
                            finalIpText, String.valueOf(finalPortVal), String.valueOf(finalEndPortVal),
                            finalScanSpeed, String.valueOf(finalOnlyPrintOnline), String.valueOf(finalAttemptBotJoin)
                    );
                }

                activeProcess = pb.start();
                activeProcess.waitFor();

                SwingUtilities.invokeLater(() -> {
                    resetUI("Scan complete.");
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    resetUI("Error: " + ex.getMessage());
                });
            }
        });
        launchThread.setDaemon(true);
        launchThread.start();
    }

    private static void resetUI(String statusMsg) {
        SwingUtilities.invokeLater(() -> {
            startButton.setText("Go!");
            setControlsEnabled(true);
            scanProgress.setIndeterminate(false);
            scanProgress.setString("Idle");
            scanProgress.setValue(0);
            statsLabel.setText(statusMsg);
        });
    }

    private static void setControlsEnabled(boolean enabled) {
        ipBox.setEnabled(enabled);
        portBox.setEnabled(enabled);
        endPortBox.setEnabled(enabled);
        onlineCB.setEnabled(enabled);
        botJoinCB.setEnabled(enabled);
        speedDropDown.setEnabled(enabled);
    }

    private static void addPlaceholder(JTextField field, String placeholder) {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
                field.setBackground(Color.WHITE);
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setText(placeholder);
                    field.setForeground(Color.LIGHT_GRAY);
                }
            }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (field.getBackground().equals(new Color(255, 180, 180)))
                    field.setBackground(Color.WHITE);
            }
        });
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--scan".equals(args[0])) {
            try {
                App.startIP = args[1];
                App.startPort = Integer.parseInt(args[2]);
                App.endPort = Integer.parseInt(args[3]);
                scanSpeed = args[4];
                onlyPrintOnlineServers = Boolean.parseBoolean(args[5]);
                attemptBotJoin = Boolean.parseBoolean(args[6]);

                App.startProcess();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        SwingUtilities.invokeLater(GUI::new);
    }
}
