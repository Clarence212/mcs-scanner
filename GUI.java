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
    private static JTextField rateField;
    private static JComboBox<String> rateUnitDropDown;
    private static JTextField botNameField;

    public static String scanSpeed = "Fast";
    public static boolean onlyPrintOnlineServers = true;
    public static boolean attemptBotJoin = false;
    public static long attemptRateMs = 1000;
    public static String customBotName = "";

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

        f.setSize(270, 238);
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
        botJoinCB.setBounds(2, 76, 135, 20);
        botJoinCB.setSelected(false);
        botJoinCB.setFocusable(false);
        botJoinCB.setOpaque(false);
        botJoinCB.setToolTipText("After finding an online server, try to log in as a bot (detects offline/online mode)");
        botJoinCB.addItemListener(e -> {
            attemptBotJoin = botJoinCB.isSelected();
            boolean on = botJoinCB.isSelected();
            rateField.setEnabled(on);
            rateField.setEditable(on);
            rateUnitDropDown.setEnabled(on);
            botNameField.setEnabled(on);
            botNameField.setEditable(on);
        });

        botNameField = new JTextField();
        botNameField.setBounds(138, 76, 124, 20);
        botNameField.setEnabled(false);
        botNameField.setEditable(false);
        botNameField.setForeground(Color.LIGHT_GRAY);
        botNameField.setToolTipText("Custom bot username (leave blank for random Bot_XXXX)");
        addPlaceholder(botNameField, "Bot_XXXX (random)");

        JLabel rateLabel = new JLabel("Rate:");
        rateLabel.setBounds(2, 100, 38, 20);
        rateField = new JTextField("1");
        rateField.setBounds(40, 100, 50, 20);
        rateField.setEditable(false);
        rateField.setEnabled(false);
        rateField.setToolTipText("How often to re-attempt joining a found server (enable Attempt Bot Join first)");
        rateUnitDropDown = new JComboBox<>(new String[]{"ms", "sec", "min"});
        rateUnitDropDown.setSelectedItem("sec");
        rateUnitDropDown.setBounds(93, 100, 68, 20);
        rateUnitDropDown.setFocusable(false);
        rateUnitDropDown.setEnabled(false);

        JLabel speedLabel = new JLabel("Speed:");
        speedLabel.setBounds(5, 124, 45, 18);
        speedDropDown = new JComboBox<>(new String[]{"Medium", "Fast", "Very Fast", "Dangerous"});
        speedDropDown.setSelectedItem("Fast");
        speedDropDown.setBounds(52, 124, 95, 18);
        speedDropDown.setFocusable(false);

        startButton = new JButton("Go!");
        startButton.setBounds(185, 118, 72, 28);
        startButton.setFocusable(false);
        startButton.addActionListener(this::onStartStop);

        scanProgress = new JProgressBar(0, 100);
        scanProgress.setBounds(5, 150, 252, 18);
        scanProgress.setString("Idle");
        scanProgress.setStringPainted(true);

        statsLabel = new JLabel("Ready.");
        statsLabel.setBounds(5, 172, 252, 16);
        statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statsLabel.setForeground(Color.DARK_GRAY);

        for (Component c : new Component[]{
                ipLabel, ipBox, endPortLabel, endPortBox,
                portLabel, portBox, onlineCB, botJoinCB, botNameField,
                rateLabel, rateField, rateUnitDropDown,
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
        if (attemptBotJoin) {
            String rateText = rateField.getText().trim();
            try {
                long rateVal = Long.parseLong(rateText);
                if (rateVal <= 0) throw new NumberFormatException();
                String unit = (String) rateUnitDropDown.getSelectedItem();
                if ("ms".equals(unit))       attemptRateMs = rateVal;
                else if ("sec".equals(unit)) attemptRateMs = rateVal * 1000L;
                else if ("min".equals(unit)) attemptRateMs = rateVal * 60_000L;
            } catch (NumberFormatException ignored) {
                rateField.setBackground(new Color(255, 180, 180));
                return;
            }
            String nameInput = botNameField.getText().trim();
            customBotName = (nameInput.isEmpty() || nameInput.equals("Bot_XXXX (random)")) ? "" : nameInput;
        } else {
            customBotName = "";
        }

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

                //Build a quoted command string so paths with spacesare handled correctly when passed through cmd /k
                
                String botArg = customBotName.isEmpty() ? "random" : customBotName;
                String scanArgs = finalIpText
                        + " " + finalPortVal
                        + " " + finalEndPortVal
                        + " " + finalScanSpeed
                        + " " + finalOnlyPrintOnline
                        + " " + finalAttemptBotJoin
                        + " " + attemptRateMs
                        + " " + botArg;

                ProcessBuilder pb;
                if (jarPath.endsWith(".jar")) {
                    String cmd = "\"" + javaExe + "\" -jar \"" + jarPath + "\" --scan " + scanArgs;
                    pb = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k", cmd);
                } else {
                    String cmd = "\"" + javaExe + "\" -cp \"" + jarPath + "\" GUI --scan " + scanArgs;
                    pb = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k", cmd);
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
        boolean botOn = enabled && botJoinCB.isSelected();
        rateField.setEnabled(botOn);
        rateField.setEditable(botOn);
        rateUnitDropDown.setEnabled(botOn);
        botNameField.setEnabled(botOn);
        botNameField.setEditable(botOn);
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
                if (args.length > 7) attemptRateMs = Long.parseLong(args[7]);
                if (args.length > 8) customBotName = args[8].equals("random") ? "" : args[8];

                App.startProcess();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        SwingUtilities.invokeLater(GUI::new);
    }
}
