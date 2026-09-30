import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Ready-made reports. The SQL for each report is shown on screen,
 * which is handy for explaining joins, grouping and subqueries in the viva.
 */
public class ReportsPanel extends JPanel implements MainFrame.Refreshable {

    private static final String[] NAMES = {
            "Overdue books with fine so far",
            "Complete issue history",
            "Most borrowed books",
            "Books and copies per category",
            "Members who paid fines",
            "Books never issued",
            "Members who never borrowed"
    };

    private static final String[] QUERIES = {
            // 1. view + filter
            "SELECT issue_id AS `Issue ID`, title AS Book, member_name AS Member,\n"
          + "       due_date AS `Due Date`, days_overdue AS `Days Overdue`,\n"
          + "       fine_so_far AS `Fine So Far (Rs)`\n"
          + "  FROM v_current_issues\n"
          + " WHERE days_overdue > 0\n"
          + " ORDER BY days_overdue DESC",

            // 2. three-table join + CASE
            "SELECT i.issue_id AS ID, b.title AS Book, m.name AS Member,\n"
          + "       i.issue_date AS Issued, i.due_date AS Due, i.return_date AS Returned,\n"
          + "       i.fine AS `Fine (Rs)`,\n"
          + "       CASE WHEN i.return_date IS NULL THEN 'Out' ELSE 'Returned' END AS Status\n"
          + "  FROM issues i\n"
          + "  JOIN books b   ON b.book_id = i.book_id\n"
          + "  JOIN members m ON m.member_id = i.member_id\n"
          + " ORDER BY i.issue_date DESC",

            // 3. GROUP BY + aggregate + LIMIT
            "SELECT b.title AS Book, b.author AS Author, COUNT(i.issue_id) AS `Times Issued`\n"
          + "  FROM books b\n"
          + "  JOIN issues i ON i.book_id = b.book_id\n"
          + " GROUP BY b.book_id, b.title, b.author\n"
          + " ORDER BY `Times Issued` DESC\n"
          + " LIMIT 5",

            // 4. LEFT JOIN + GROUP BY
            "SELECT c.name AS Category, COUNT(b.book_id) AS Titles,\n"
          + "       IFNULL(SUM(b.total_copies), 0) AS `Total Copies`,\n"
          + "       IFNULL(SUM(b.available_copies), 0) AS Available\n"
          + "  FROM categories c\n"
          + "  LEFT JOIN books b ON b.category_id = c.category_id\n"
          + " GROUP BY c.category_id, c.name\n"
          + " ORDER BY Titles DESC",

            // 5. GROUP BY + HAVING
            "SELECT m.name AS Member, m.member_type AS Type,\n"
          + "       SUM(CASE WHEN i.fine > 0 THEN 1 ELSE 0 END) AS `Late Returns`,\n"
          + "       SUM(i.fine) AS `Total Fine (Rs)`\n"
          + "  FROM members m\n"
          + "  JOIN issues i ON i.member_id = m.member_id\n"
          + " GROUP BY m.member_id, m.name, m.member_type\n"
          + "HAVING SUM(i.fine) > 0\n"
          + " ORDER BY `Total Fine (Rs)` DESC",

            // 6. NOT EXISTS subquery
            "SELECT b.book_id AS ID, b.title AS Book, b.author AS Author\n"
          + "  FROM books b\n"
          + " WHERE NOT EXISTS (SELECT 1 FROM issues i WHERE i.book_id = b.book_id)",

            // 7. LEFT JOIN ... IS NULL (anti-join)
            "SELECT m.member_id AS ID, m.name AS Member, m.email AS Email\n"
          + "  FROM members m\n"
          + "  LEFT JOIN issues i ON i.member_id = m.member_id\n"
          + " WHERE i.issue_id IS NULL"
    };

    private final JComboBox<String> reportBox = new JComboBox<>(NAMES);
    private final JTextArea sqlArea = new JTextArea(8, 60);
    private final JTable table = AppUtil.createTable();

    public ReportsPanel() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JButton runBtn = new JButton("Run report");
        runBtn.addActionListener(e -> runReport());
        reportBox.addActionListener(e -> runReport());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.add(new JLabel("Report:"));
        top.add(reportBox);
        top.add(runBtn);

        sqlArea.setEditable(false);
        sqlArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane sqlScroll = new JScrollPane(sqlArea);
        sqlScroll.setBorder(AppUtil.titled("SQL used for this report"));

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(AppUtil.titled("Result"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sqlScroll, tableScroll);
        split.setResizeWeight(0.3);
        split.setBorder(null);

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        runReport();
    }

    private void runReport() {
        String sql = QUERIES[reportBox.getSelectedIndex()];
        sqlArea.setText(sql);
        sqlArea.setCaretPosition(0);
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            table.setModel(AppUtil.toTableModel(rs));
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }
}
