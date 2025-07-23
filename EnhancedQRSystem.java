import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.security.NoSuchAlgorithmException;

public class EnhancedQRSystem {
    private JFrame frame;
    private JTextArea logArea, encryptedDataArea;
    private JTextField dataField;
    private JComboBox<String> securityLevelCombo;
    private JCheckBox passwordCheckBox;
    private JPasswordField passwordField;
    private JLabel qrDisplayLabel;
    private Map<String, QRCodeData> qrDatabase = new HashMap<>();
    private Map<String, java.util.List<String>> verificationLogs = new HashMap<>();
    private SecretKey secretKey;
    private Cipher cipher;
    private final Color PRIMARY_COLOR = new Color(0, 150, 136), SECONDARY_COLOR = new Color(255, 193, 7);
    private final Color ERROR_COLOR = new Color(244, 67, 54), SUCCESS_COLOR = new Color(76, 175, 80);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EnhancedQRSystem().initialize());
    }

    private void initialize() {
        try {
            secretKey = KeyGenerator.getInstance("AES").generateKey();
            cipher = Cipher.getInstance("AES");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Error initializing encryption: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        setupMainFrame();
        frame.add(createTabbedInterface());
        frame.setVisible(true);
    }

    private void setupMainFrame() {
        frame = new JFrame("Enhanced QR Verification System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 750);
        frame.setLocationRelativeTo(null);
        frame.setIconImage(createAppIcon());
    }

    private Image createAppIcon() {
        BufferedImage icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setColor(PRIMARY_COLOR);
        g.fillRect(0, 0, 64, 64);
        g.setColor(Color.WHITE);
        g.fillRect(12, 12, 40, 40);
        g.setColor(PRIMARY_COLOR);
        int[] squares = {16,16,8,8, 16,32,8,8, 32,16,8,8, 40,40,8,8};
        for (int i = 0; i < squares.length; i += 4) 
            g.fillRect(squares[i], squares[i+1], squares[i+2], squares[i+3]);
        g.dispose();
        return icon;
    }

    private JTabbedPane createTabbedInterface() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(PRIMARY_COLOR);
        tabbedPane.setForeground(Color.WHITE);
        tabbedPane.addTab("Generate QR", createGeneratePanel());
        tabbedPane.addTab("Verify QR", createVerifyPanel());
        tabbedPane.addTab("Verification Logs", createLogsPanel());
        return tabbedPane;
    }

    private JPanel createGeneratePanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(new Color(240, 240, 240));

        JPanel inputPanel = createCardPanel();
        inputPanel.setLayout(new GridLayout(0, 2, 10, 10));
        dataField = new JTextField();
        styleTextField(dataField);
        securityLevelCombo = new JComboBox<>(new String[]{"Low", "Medium", "High"});
        styleComboBox(securityLevelCombo);
        passwordCheckBox = new JCheckBox("Password Protect");
        passwordField = new JPasswordField();
        styleTextField(passwordField);
        passwordField.setEnabled(false);
        passwordCheckBox.addActionListener(e -> passwordField.setEnabled(passwordCheckBox.isSelected()));

        inputPanel.add(createFormLabel("Data to encode:")); inputPanel.add(dataField);
        inputPanel.add(createFormLabel("Security Level:")); inputPanel.add(securityLevelCombo);
        inputPanel.add(new JLabel()); inputPanel.add(passwordCheckBox);
        inputPanel.add(createFormLabel("Password:")); inputPanel.add(passwordField);

        qrDisplayLabel = new JLabel("", SwingConstants.CENTER);
        qrDisplayLabel.setPreferredSize(new Dimension(350, 350));
        qrDisplayLabel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        qrDisplayLabel.add(new JLabel("<html><center><font size='5' color='#666666'>QR Code will appear here</font><br><font size='3' color='#999999'>Enter data and click Generate</font></center></html>", SwingConstants.CENTER));

        encryptedDataArea = new JTextArea();
        encryptedDataArea.setEditable(false);
        encryptedDataArea.setLineWrap(true);
        encryptedDataArea.setBackground(new Color(250, 250, 250));

        JPanel displayPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        displayPanel.setOpaque(false);
        displayPanel.add(createCardPanelWithComponent(qrDisplayLabel));
        displayPanel.add(createCardPanelWithComponent(new JScrollPane(encryptedDataArea), "Encrypted Data"));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(createStyledButton("Generate QR Code", PRIMARY_COLOR, e -> generateQRCode()));
        buttonPanel.add(createStyledButton("Save QR Code", SECONDARY_COLOR, e -> saveQRCode()));

        panel.add(inputPanel, BorderLayout.NORTH);
        panel.add(displayPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createVerifyPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(new Color(240, 240, 240));

        JLabel verifyImageLabel = new JLabel("", SwingConstants.CENTER);
        verifyImageLabel.setPreferredSize(new Dimension(350, 350));
        verifyImageLabel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        verifyImageLabel.add(new JLabel("<html><center><font size='5' color='#666666'>Load QR Code to Verify</font><br><font size='3' color='#999999'>Click 'Load QR Image' button</font></center></html>", SwingConstants.CENTER));

        JTextArea decryptedDataArea = new JTextArea();
        decryptedDataArea.setEditable(false);
        decryptedDataArea.setLineWrap(true);
        decryptedDataArea.setBackground(new Color(250, 250, 250));

        JPanel displayPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        displayPanel.setOpaque(false);
        displayPanel.add(createCardPanelWithComponent(verifyImageLabel));
        displayPanel.add(createCardPanelWithComponent(new JScrollPane(decryptedDataArea), "Decrypted Data"));

        JLabel resultLabel = new JLabel(" ", SwingConstants.CENTER);
        resultLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        JPasswordField verifyPasswordField = new JPasswordField(20);
        styleTextField(verifyPasswordField);
        JPanel passwordPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        passwordPanel.setOpaque(false);
        passwordPanel.add(new JLabel("Password:"));
        passwordPanel.add(verifyPasswordField);
        passwordPanel.setVisible(false);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(createStyledButton("Load QR Image", PRIMARY_COLOR, e -> loadQRImage(verifyImageLabel, resultLabel, passwordPanel)));
        buttonPanel.add(createStyledButton("Verify QR Code", SUCCESS_COLOR, e -> verifyQRCode(verifyPasswordField.getPassword(), resultLabel, decryptedDataArea)));
        buttonPanel.add(createStyledButton("Decrypt Data", SECONDARY_COLOR, e -> decryptData(verifyPasswordField.getPassword(), decryptedDataArea)));

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setOpaque(false);
        southPanel.add(resultLabel, BorderLayout.NORTH);
        southPanel.add(passwordPanel, BorderLayout.CENTER);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);

        panel.add(displayPanel, BorderLayout.CENTER);
        panel.add(southPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(new Color(240, 240, 240));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JTextField searchField = new JTextField();
        styleTextField(searchField);
        JButton searchButton = createStyledButton("Search", PRIMARY_COLOR, e -> searchLogs(searchField.getText().trim()));
        searchButton.setPreferredSize(new Dimension(100, searchField.getPreferredSize().height));

        JPanel searchPanel = new JPanel(new BorderLayout(10, 10));
        searchPanel.setOpaque(false);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        JButton refreshButton = createStyledButton("Refresh Logs", SECONDARY_COLOR, e -> displayAllLogs());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(refreshButton);

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        displayAllLogs();
        return panel;
    }

    private JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)), BorderFactory.createEmptyBorder(15, 15, 15, 15)));
        return panel;
    }

    private JPanel createCardPanelWithComponent(Component comp, String title) {
        JPanel panel = createCardPanel();
        panel.setLayout(new BorderLayout());
        if (title != null) panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(comp, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCardPanelWithComponent(Component comp) {
        return createCardPanelWithComponent(comp, null);
    }

    private JLabel createFormLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        return label;
    }

    private void styleTextField(JTextField field) {
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)), BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        field.setBackground(Color.WHITE);
    }

    private void styleComboBox(JComboBox<String> combo) {
        combo.setBackground(Color.WHITE);
        combo.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)), BorderFactory.createEmptyBorder(5, 8, 5, 8)));
    }

    private JButton createStyledButton(String text, Color bgColor, ActionListener action) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        button.addActionListener(action);
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { button.setBackground(bgColor.darker()); }
            public void mouseExited(MouseEvent e) { button.setBackground(bgColor); }
        });
        return button;
    }

    private void generateQRCode() {
        String data = dataField.getText().trim();
        if (data.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please enter data to encode", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            String encryptedData = Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
            QRCodeData qrData = new QRCodeData();
            qrData.id = UUID.randomUUID().toString();
            qrData.originalData = data;
            qrData.encryptedData = encryptedData;
            qrData.securityLevel = (String)securityLevelCombo.getSelectedItem();
            qrData.createdAt = new Date();
            if (passwordCheckBox.isSelected() && passwordField.getPassword().length > 0) 
                qrData.passwordHash = new String(passwordField.getPassword());
            
            qrDatabase.put(qrData.id, qrData);
            verificationLogs.put(qrData.id, new ArrayList<>());
            
            qrDisplayLabel.removeAll();
            qrDisplayLabel.setIcon(new ImageIcon(generateSimulatedQRImage(qrData)));
            encryptedDataArea.setText("Encrypted Data:\n" + encryptedData);
            JOptionPane.showMessageDialog(frame, "<html><b>QR Code generated successfully!</b><br>ID: " + qrData.id + "</html>", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error encrypting data: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BufferedImage generateSimulatedQRImage(QRCodeData qrData) {
        int size = 300;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, size, size);
        g.setColor(Color.BLACK);
        g.fillRect(20, 20, 70, 70);
        g.fillRect(size-90, 20, 70, 70);
        g.fillRect(20, size-90, 70, 70);
        for (int i = 110; i < size-110; i += 10) {
            g.fillRect(90, i, 10, 10);
            g.fillRect(i, 90, 10, 10);
        }
        Random rand = new Random(qrData.id.hashCode());
        for (int y = 120; y < size-40; y += 20) 
            for (int x = 120; x < size-40; x += 20) 
                if (rand.nextBoolean()) g.fillRect(x, y, 15, 15);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.drawString("ID: " + qrData.id.substring(0, 8), 20, size-20);
        g.dispose();
        return image;
    }

    private void saveQRCode() {
        if (qrDisplayLabel.getIcon() == null) {
            JOptionPane.showMessageDialog(frame, "No QR code to save", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save QR Code Image");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) { return f.getName().toLowerCase().endsWith(".png") || f.isDirectory(); }
            public String getDescription() { return "PNG Images (*.png)"; }
        });
        if (fileChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".png")) 
                fileToSave = new File(fileToSave.getAbsolutePath() + ".png");
            try {
                ImageIcon icon = (ImageIcon) qrDisplayLabel.getIcon();
                BufferedImage bi = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics g = bi.createGraphics();
                icon.paintIcon(null, g, 0, 0);
                g.dispose();
                ImageIO.write(bi, "png", fileToSave);
                JOptionPane.showMessageDialog(frame, "QR code saved successfully to:\n" + fileToSave.getAbsolutePath(), "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Error saving QR code: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadQRImage(JLabel verifyImageLabel, JLabel resultLabel, JPanel passwordPanel) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) { return f.getName().toLowerCase().matches(".*\\.(png|jpg|jpeg)$") || f.isDirectory(); }
            public String getDescription() { return "Image Files (*.png, *.jpg, *.jpeg)"; }
        });
        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            try {
                ImageIcon icon = new ImageIcon(new ImageIcon(fileChooser.getSelectedFile().getPath()).getImage().getScaledInstance(300, 300, Image.SCALE_SMOOTH));
                verifyImageLabel.removeAll();
                verifyImageLabel.setIcon(icon);
                resultLabel.setText(" ");
                passwordPanel.setVisible(!qrDatabase.isEmpty() && qrDatabase.values().iterator().next().passwordHash != null);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "Error loading image: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void verifyQRCode(char[] password, JLabel resultLabel, JTextArea decryptedDataArea) {
        if (qrDatabase.isEmpty()) {
            resultLabel.setText("No QR codes in database to verify against");
            resultLabel.setForeground(ERROR_COLOR);
            return;
        }
        QRCodeData qrData = qrDatabase.values().iterator().next();
        if (qrData.passwordHash != null) {
            if (password == null || password.length == 0) {
                resultLabel.setText("Password required for verification");
                resultLabel.setForeground(Color.ORANGE);
                return;
            }
            if (!qrData.passwordHash.equals(new String(password))) {
                resultLabel.setText("Incorrect password");
                resultLabel.setForeground(ERROR_COLOR);
                return;
            }
        }
        verificationLogs.get(qrData.id).add(String.format("Verified at %s - Status: SUCCESS", new Date()));
        resultLabel.setText("<html><b>Verification SUCCESSFUL</b><br>ID: " + qrData.id + "<br>Security Level: " + qrData.securityLevel + "</html>");
        resultLabel.setForeground(SUCCESS_COLOR);
        displayAllLogs();
    }

    private void decryptData(char[] password, JTextArea decryptedDataArea) {
        if (qrDatabase.isEmpty()) {
            decryptedDataArea.setText("No QR codes in database to decrypt");
            return;
        }
        QRCodeData qrData = qrDatabase.values().iterator().next();
        try {
            if (qrData.passwordHash != null) {
                if (password == null || password.length == 0) {
                    decryptedDataArea.setText("Password required for decryption");
                    return;
                }
                if (!qrData.passwordHash.equals(new String(password))) {
                    decryptedDataArea.setText("Incorrect password - cannot decrypt");
                    return;
                }
            }
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            String decryptedData = new String(cipher.doFinal(Base64.getDecoder().decode(qrData.encryptedData)));
            decryptedDataArea.setText("Decrypted Data:\n" + decryptedData + "\n\nOriginal Data:\n" + qrData.originalData);
        } catch (Exception e) {
            decryptedDataArea.setText("Error decrypting data: " + e.getMessage());
        }
    }

    private void displayAllLogs() {
        StringBuilder sb = new StringBuilder("=== VERIFICATION LOGS ===\n\n");
        for (Map.Entry<String, java.util.List<String>> entry : verificationLogs.entrySet()) {
            QRCodeData qrData = qrDatabase.get(entry.getKey());
            if (qrData != null) {
                sb.append("QR ID: ").append(qrData.id).append("\nData: ").append(qrData.originalData)
                 .append("\nCreated: ").append(qrData.createdAt).append("\nSecurity Level: ").append(qrData.securityLevel)
                 .append("\n").append(entry.getValue().isEmpty() ? "No verifications yet\n" : "Verifications:\n");
                for (String log : entry.getValue()) sb.append("  - ").append(log).append("\n");
                sb.append("----------------------------------------\n");
            }
        }
        logArea.setText(sb.toString());
        logArea.setCaretPosition(0);
    }

    private void searchLogs(String query) {
        StringBuilder sb = new StringBuilder("=== SEARCH RESULTS ===\n\n");
        boolean found = false;
        for (QRCodeData qrData : qrDatabase.values()) {
            if (qrData.id.contains(query) || qrData.originalData.toLowerCase().contains(query.toLowerCase())) {
                found = true;
                sb.append("QR ID: ").append(qrData.id).append("\nData: ").append(qrData.originalData).append("\n");
                for (String log : verificationLogs.getOrDefault(qrData.id, Collections.emptyList())) 
                    sb.append("  - ").append(log).append("\n");
                sb.append("----------------------------------------\n");
            }
        }
        if (!found) sb.append("No matching records found for: ").append(query).append("\n");
        logArea.setText(sb.toString());
        logArea.setCaretPosition(0);
    }

    class QRCodeData {
        String id, originalData, encryptedData, securityLevel, passwordHash;
        Date createdAt;
    }
}