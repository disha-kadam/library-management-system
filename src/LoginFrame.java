import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Admin login screen. Passwords are compared as SHA-256 hashes inside MySQL. */
public class LoginFrame extends JFrame {

    private final JTextField userField = new JTextField(16);
    private final JPasswordField passField = new JPasswordField(16);

    public LoginFrame() {
        super("Library Management System - Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel header = new JPanel(new GridLayout(2, 1, 0, 4));
        header.setBackground(AppUtil.PRIMARY);
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JLabel title = new JLabel("Library Management System", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel("Sign in as librarian", SwingConstants.CENTER);
        sub.setForeground(new Color(200, 215, 230));
        header.add(title);
        header.add(sub);

        JPanel form = AppUtil.formPanel(
                new String[]{"Username", "Password"},
                new JComponent[]{userField, passField});
        form.setBorder(BorderFactory.createEmptyBorder(18, 28, 6, 28));

        JButton loginBtn = new JButton("Sign in");
        loginBtn.addActionListener(e -> login());
        getRootPane().setDefaultButton(loginBtn);   // Enter key signs in

        JLabel hint = new JLabel("Demo account: admin / admin123", SwingConstants.CENTER);
        hint.setForeground(Color.GRAY);

        JPanel south = new JPanel(new BorderLayout(0, 10));
        south.setBorder(BorderFactory.createEmptyBorder(6, 34, 18, 34));
        south.add(loginBtn, BorderLayout.NORTH);
        south.add(hint, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void login() {
        String username = userField.getText().trim();
        String password = new String(passField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter both username and password.");
            return;
        }

        // PreparedStatement keeps this safe from SQL injection
        String sql = "SELECT username FROM admin WHERE username = ? AND password_hash = SHA2(?, 256)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("username");
                    dispose();
                    new MainFrame(name).setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(this, "Wrong username or password.",
                            "Sign in failed", JOptionPane.WARNING_MESSAGE);
                    passField.setText("");
                    passField.requestFocus();
                }
            }
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }
}
