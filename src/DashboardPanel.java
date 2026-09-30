import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Summary numbers plus the most recent issues. */
public class DashboardPanel extends JPanel implements MainFrame.Refreshable {

    private static final String[] TITLES = {
            "Book titles", "Total copies", "Members",
            "Books out now", "Overdue books", "Fines collected (Rs)"
    };
    private static final Color[] COLORS = {
            new Color(28, 61, 90), new Color(44, 98, 140), new Color(46, 125, 110),
            new Color(191, 132, 30), new Color(178, 58, 58), new Color(96, 72, 140)
    };

    private final JLabel[] values = new JLabel[TITLES.length];
    private final JTable recentTable = AppUtil.createTable();

    public DashboardPanel() {
        setLayout(new BorderLayout(16, 16));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel grid = new JPanel(new GridLayout(2, 3, 16, 16));
        for (int i = 0; i < TITLES.length; i++) {
            JPanel card = new JPanel(new BorderLayout(0, 6));
            card.setBackground(COLORS[i]);
            card.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

            JLabel t = new JLabel(TITLES[i]);
            t.setForeground(new Color(235, 240, 245));

            values[i] = new JLabel("-");
            values[i].setFont(values[i].getFont().deriveFont(Font.BOLD, 34f));
            values[i].setForeground(Color.WHITE);

            card.add(t, BorderLayout.NORTH);
            card.add(values[i], BorderLayout.CENTER);
            grid.add(card);
        }

        JPanel recent = new JPanel(new BorderLayout());
        recent.setBorder(AppUtil.titled("Recently issued books"));
        recent.add(new JScrollPane(recentTable), BorderLayout.CENTER);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refresh());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottom.add(refreshBtn);

        add(grid, BorderLayout.NORTH);
        add(recent, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        String statsSql =
                "SELECT (SELECT COUNT(*) FROM books), "
              + "       (SELECT IFNULL(SUM(total_copies), 0) FROM books), "
              + "       (SELECT COUNT(*) FROM members), "
              + "       (SELECT COUNT(*) FROM issues WHERE return_date IS NULL), "
              + "       (SELECT COUNT(*) FROM issues WHERE return_date IS NULL AND due_date < CURDATE()), "
              + "       (SELECT IFNULL(SUM(fine), 0) FROM issues WHERE return_date IS NOT NULL)";

        String recentSql =
                "SELECT i.issue_id AS `Issue ID`, b.title AS Book, m.name AS Member, "
              + "       i.issue_date AS `Issued On`, i.due_date AS `Due Date`, "
              + "       GREATEST(DATEDIFF(CURDATE(), i.due_date), 0) AS `Days Overdue` "
              + "  FROM issues i "
              + "  JOIN books b   ON b.book_id = i.book_id "
              + "  JOIN members m ON m.member_id = i.member_id "
              + " WHERE i.return_date IS NULL "
              + " ORDER BY i.issue_date DESC, i.issue_id DESC LIMIT 8";

        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery(statsSql)) {
                if (rs.next()) {
                    for (int i = 0; i < values.length; i++) values[i].setText(rs.getString(i + 1));
                }
            }
            try (ResultSet rs = st.executeQuery(recentSql)) {
                recentTable.setModel(AppUtil.toTableModel(rs));
            }
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }
}
