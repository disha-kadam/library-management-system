import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.sql.*;

/** Add, update, delete and search library members. */
public class MembersPanel extends JPanel implements MainFrame.Refreshable {

    private final JTextField idField = new JTextField();
    private final JTextField nameField = new JTextField(16);
    private final JTextField emailField = new JTextField(16);
    private final JTextField phoneField = new JTextField(16);
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"Student", "Faculty"});
    private final JTextField searchField = new JTextField(22);
    private final JTable table = AppUtil.createTable();

    public MembersPanel() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        idField.setEditable(false);

        JPanel form = AppUtil.formPanel(
                new String[]{"Member ID", "Name *", "Email *", "Phone *", "Type"},
                new JComponent[]{idField, nameField, emailField, phoneField, typeBox});

        JButton addBtn = new JButton("Add member");
        JButton updateBtn = new JButton("Save changes");
        JButton deleteBtn = new JButton("Delete member");
        JButton clearBtn = new JButton("Clear form");
        addBtn.addActionListener(e -> addMember());
        updateBtn.addActionListener(e -> updateMember());
        deleteBtn.addActionListener(e -> deleteMember());
        clearBtn.addActionListener(e -> clearForm());

        JPanel buttons = new JPanel(new GridLayout(2, 2, 8, 8));
        buttons.add(addBtn);
        buttons.add(updateBtn);
        buttons.add(deleteBtn);
        buttons.add(clearBtn);

        JPanel buttonWrap = new JPanel(new BorderLayout());
        buttonWrap.add(buttons, BorderLayout.NORTH);

        JPanel left = new JPanel(new BorderLayout(8, 12));
        left.setBorder(AppUtil.titled("Member details"));
        left.add(form, BorderLayout.NORTH);
        left.add(buttonWrap, BorderLayout.CENTER);
        

        JButton searchBtn = new JButton("Search");
        JButton allBtn = new JButton("Show all");
        searchBtn.addActionListener(e -> loadTable());
        searchField.addActionListener(e -> loadTable());
        allBtn.addActionListener(e -> {
            searchField.setText("");
            loadTable();
        });

        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchBar.add(new JLabel("Name, email or phone:"));
        searchBar.add(searchField);
        searchBar.add(searchBtn);
        searchBar.add(allBtn);

        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setBorder(AppUtil.titled("Members  (click a row to edit it)"));
        right.add(searchBar, BorderLayout.NORTH);
        right.add(new JScrollPane(table), BorderLayout.CENTER);

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) fillFormFromSelection();
        });
    }

    @Override
    public void refresh() {
        loadTable();
    }

    private void loadTable() {
        // correlated subquery counts how many books each member holds right now
        String sql =
                "SELECT m.member_id AS ID, m.name AS Name, m.email AS Email, m.phone AS Phone, "
              + "       m.member_type AS Type, m.join_date AS `Joined On`, "
              + "       (SELECT COUNT(*) FROM issues i "
              + "         WHERE i.member_id = m.member_id AND i.return_date IS NULL) AS `Books Held` "
              + "  FROM members m "
              + " WHERE m.name LIKE ? OR m.email LIKE ? OR m.phone LIKE ? "
              + " ORDER BY m.member_id";
        String key = "%" + searchField.getText().trim() + "%";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, key);
            ps.setString(3, key);
            try (ResultSet rs = ps.executeQuery()) {
                table.setModel(AppUtil.toTableModel(rs));
            }
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }

    private void fillFormFromSelection() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        int m = table.convertRowIndexToModel(row);
        TableModel tm = table.getModel();
        idField.setText(AppUtil.str(tm.getValueAt(m, 0)));
        nameField.setText(AppUtil.str(tm.getValueAt(m, 1)));
        emailField.setText(AppUtil.str(tm.getValueAt(m, 2)));
        phoneField.setText(AppUtil.str(tm.getValueAt(m, 3)));
        typeBox.setSelectedItem(tm.getValueAt(m, 4));
    }

    private boolean formIsValid() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        if (name.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name, email and phone are required.");
            return false;
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            JOptionPane.showMessageDialog(this, "Enter a valid email, like name@example.com.");
            return false;
        }
        if (!phone.matches("\\d{10}")) {
            JOptionPane.showMessageDialog(this, "Phone must be exactly 10 digits.");
            return false;
        }
        return true;
    }

    private void addMember() {
        if (!formIsValid()) return;
        String sql = "INSERT INTO members (name, email, phone, member_type, join_date) "
                   + "VALUES (?, ?, ?, ?, CURDATE())";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nameField.getText().trim());
            ps.setString(2, emailField.getText().trim());
            ps.setString(3, phoneField.getText().trim());
            ps.setString(4, (String) typeBox.getSelectedItem());
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Member added.");
            clearForm();
            loadTable();
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }

    private void updateMember() {
        if (idField.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select a member in the table first.");
            return;
        }
        if (!formIsValid()) return;
        String sql = "UPDATE members SET name = ?, email = ?, phone = ?, member_type = ? WHERE member_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nameField.getText().trim());
            ps.setString(2, emailField.getText().trim());
            ps.setString(3, phoneField.getText().trim());
            ps.setString(4, (String) typeBox.getSelectedItem());
            ps.setInt(5, Integer.parseInt(idField.getText()));
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Changes saved.");
            clearForm();
            loadTable();
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }

    private void deleteMember() {
        if (idField.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select a member in the table first.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this,
                "Delete member \"" + nameField.getText() + "\"?", "Delete member", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM members WHERE member_id = ?")) {
            ps.setInt(1, Integer.parseInt(idField.getText()));
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Member deleted.");
            clearForm();
            loadTable();
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }

    private void clearForm() {
        idField.setText("");
        nameField.setText("");
        emailField.setText("");
        phoneField.setText("");
        typeBox.setSelectedIndex(0);
        table.clearSelection();
    }
}
