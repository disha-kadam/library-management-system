import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

/** Issue books and take them back. Both actions call stored procedures. */
public class IssuePanel extends JPanel implements MainFrame.Refreshable {

    private final JComboBox<String> bookBox = new JComboBox<>();
    private final JComboBox<String> memberBox = new JComboBox<>();
    private final JSpinner daysSpinner = new JSpinner(new SpinnerNumberModel(14, 1, 60, 1));
    private final JCheckBox overdueOnly = new JCheckBox("Show only overdue");
    private final JTable table = AppUtil.createTable();

    public IssuePanel() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        bookBox.setPrototypeDisplayValue("0000 - A fairly long book title goes here  [00 available]");
        memberBox.setPrototypeDisplayValue("0000 - A fairly long member name (Faculty)");

        // ---- top: issue form ----
        JPanel form = AppUtil.formPanel(
                new String[]{"Book", "Member", "Lend for (days)"},
                new JComponent[]{bookBox, memberBox, daysSpinner});

        JButton issueBtn = new JButton("Issue book");
        issueBtn.addActionListener(e -> issueBook());
        JPanel issueBtnWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        issueBtnWrap.add(issueBtn);
        JLabel rules = new JLabel("<html>Students can hold 3 books, faculty 5.<br>Late returns cost Rs 5 per day.</html>");
        rules.setForeground(Color.GRAY);
        issueBtnWrap.add(rules);

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setBorder(AppUtil.titled("Issue a book"));
        top.add(form, BorderLayout.CENTER);
        top.add(issueBtnWrap, BorderLayout.SOUTH);

        // ---- bottom: books currently out ----
        JButton returnBtn = new JButton("Return selected book");
        JButton refreshBtn = new JButton("Refresh");
        returnBtn.addActionListener(e -> returnBook());
        refreshBtn.addActionListener(e -> refresh());
        overdueOnly.addActionListener(e -> loadTable());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.add(returnBtn);
        toolbar.add(refreshBtn);
        toolbar.add(overdueOnly);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setBorder(AppUtil.titled("Books out right now  (overdue rows are shaded red)"));
        bottom.add(toolbar, BorderLayout.NORTH);
        bottom.add(new JScrollPane(table), BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(bottom, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        loadCombos();
        loadTable();
    }

    private void loadCombos() {
        bookBox.removeAllItems();
        memberBox.removeAllItems();
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT book_id, title, available_copies FROM books WHERE available_copies > 0 ORDER BY title")) {
                while (rs.next()) {
                    bookBox.addItem(rs.getInt(1) + " - " + rs.getString(2) + "  [" + rs.getInt(3) + " available]");
                }
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT member_id, name, member_type FROM members ORDER BY name")) {
                while (rs.next()) {
                    memberBox.addItem(rs.getInt(1) + " - " + rs.getString(2) + " (" + rs.getString(3) + ")");
                }
            }
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }

    private void loadTable() {
        // reads from the view v_current_issues
        String sql =
                "SELECT issue_id AS `Issue ID`, title AS Book, member_name AS Member, "
              + "       issue_date AS `Issued On`, due_date AS `Due Date`, "
              + "       days_overdue AS `Days Overdue`, fine_so_far AS `Fine So Far (Rs)` "
              + "  FROM v_current_issues "
              + (overdueOnly.isSelected() ? " WHERE days_overdue > 0 " : "")
              + " ORDER BY due_date";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            table.setModel(AppUtil.toTableModel(rs));
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }

    /** "12 - Some title [3 available]"  ->  12 */
    private static int idOf(Object comboItem) {
        String s = comboItem.toString();
        return Integer.parseInt(s.substring(0, s.indexOf(" - ")).trim());
    }

    private void issueBook() {
        Object book = bookBox.getSelectedItem();
        Object member = memberBox.getSelectedItem();
        if (book == null || member == null) {
            JOptionPane.showMessageDialog(this, "Choose a book and a member first.");
            return;
        }
        int days = ((Number) daysSpinner.getValue()).intValue();

        try (Connection con = DBConnection.getConnection();
             CallableStatement cs = con.prepareCall("{call issue_book(?, ?, ?)}")) {
            cs.setInt(1, idOf(book));
            cs.setInt(2, idOf(member));
            cs.setInt(3, days);
            cs.execute();
            JOptionPane.showMessageDialog(this,
                    "Book issued.\nDue back on " + LocalDate.now().plusDays(days) + ".");
            refresh();
        } catch (SQLException e) {
            AppUtil.showError(this, e);   // shows the rule message from the procedure
        }
    }

    private void returnBook() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a row in the table first.");
            return;
        }
        int m = table.convertRowIndexToModel(row);
        int issueId = ((Number) table.getModel().getValueAt(m, 0)).intValue();
        String title = AppUtil.str(table.getModel().getValueAt(m, 1));

        int ok = JOptionPane.showConfirmDialog(this,
                "Return \"" + title + "\"?", "Return book", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        try (Connection con = DBConnection.getConnection();
             CallableStatement cs = con.prepareCall("{call return_book(?, ?)}")) {
            cs.setInt(1, issueId);
            cs.registerOutParameter(2, Types.DECIMAL);
            cs.execute();
            BigDecimal fine = cs.getBigDecimal(2);
            if (fine != null && fine.signum() > 0) {
                JOptionPane.showMessageDialog(this,
                        "Book returned late.\nCollect a fine of Rs " + fine + ".",
                        "Fine due", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Book returned on time. No fine.");
            }
            refresh();
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }
}
