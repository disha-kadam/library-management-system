import javax.swing.*;
import java.awt.*;

/** Main window: a header bar and one tab per module. */
public class MainFrame extends JFrame {

    /** Every tab implements this so it reloads fresh data whenever it is opened. */
    public interface Refreshable {
        void refresh();
    }

    public MainFrame(String username) {
        super("Library Management System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 720);
        setMinimumSize(new Dimension(1000, 600));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);   // open full screen

        // ---- header ----
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppUtil.PRIMARY);
        header.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));

        JLabel title = new JLabel("Library Management System");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(Color.WHITE);

        JLabel user = new JLabel("Signed in as " + username);
        user.setForeground(new Color(200, 215, 230));
        JButton logout = new JButton("Sign out");
        logout.addActionListener(e -> {
            dispose();
            new LoginFrame().setVisible(true);
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(user);
        right.add(logout);

        header.add(title, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        // ---- tabs ----
        DashboardPanel dashboard = new DashboardPanel();
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Dashboard", dashboard);
        tabs.addTab("Books", new BooksPanel());
        tabs.addTab("Members", new MembersPanel());
        tabs.addTab("Issue / Return", new IssuePanel());
        tabs.addTab("Reports", new ReportsPanel());
        tabs.addChangeListener(e -> {
            Component c = tabs.getSelectedComponent();
            if (c instanceof Refreshable) ((Refreshable) c).refresh();
        });

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        dashboard.refresh();
    }
}
