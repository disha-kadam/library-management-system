import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Vector;

/** Small helpers shared by all screens. */
public class AppUtil {

    public static final Color PRIMARY = new Color(28, 61, 90);
    public static final Color OVERDUE_ROW = new Color(255, 218, 214);

    /** Turns any ResultSet into a read-only table model. Column headings come from the SQL aliases. */
    public static DefaultTableModel toTableModel(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();

        Vector<String> names = new Vector<>();
        for (int i = 1; i <= cols; i++) names.add(md.getColumnLabel(i));

        Vector<Vector<Object>> rows = new Vector<>();
        while (rs.next()) {
            Vector<Object> row = new Vector<>();
            for (int i = 1; i <= cols; i++) row.add(rs.getObject(i));
            rows.add(row);
        }

        return new DefaultTableModel(rows, names) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int col) {
                // lets numbers and dates sort correctly when a heading is clicked
                for (int r = 0; r < getRowCount(); r++) {
                    Object v = getValueAt(r, col);
                    if (v != null) return v.getClass();
                }
                return Object.class;
            }
        };
    }

    /** A JTable that sorts on header click and highlights rows whose "Days Overdue" is above zero. */
    public static JTable createTable() {
        JTable table = new JTable() {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
                // renderers remember the last background colour, so clear it first
                if (renderer instanceof DefaultTableCellRenderer) {
                    ((DefaultTableCellRenderer) renderer).setBackground(null);
                }
                Component c = super.prepareRenderer(renderer, row, col);
                if (!isRowSelected(row) && getModel() instanceof AbstractTableModel) {
                    int overdueCol = ((AbstractTableModel) getModel()).findColumn("Days Overdue");
                    if (overdueCol >= 0) {
                        Object v = getModel().getValueAt(convertRowIndexToModel(row), overdueCol);
                        if (v instanceof Number && ((Number) v).intValue() > 0) {
                            c.setBackground(OVERDUE_ROW);
                        }
                    }
                }
                return c;
            }

            @Override
            public void setModel(javax.swing.table.TableModel model) {
                super.setModel(model);
                fitColumns(this);
            }

            @Override
            public boolean getScrollableTracksViewportWidth() {
                // fill the width when columns fit, otherwise scroll sideways
                return getParent() == null || getPreferredSize().width < getParent().getWidth();
            }
        };
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);
        return table;
    }

    /** Sizes each column to its heading and contents (checks the first 100 rows). */
    public static void fitColumns(JTable table) {
        if (table.getTableHeader() == null || table.getColumnCount() == 0) return;
        TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
        for (int col = 0; col < table.getColumnCount(); col++) {
            javax.swing.table.TableColumn column = table.getColumnModel().getColumn(col);
            int width = headerRenderer.getTableCellRendererComponent(
                    table, column.getHeaderValue(), false, false, -1, col).getPreferredSize().width;
            for (int row = 0; row < Math.min(table.getRowCount(), 100); row++) {
                Component c = table.prepareRenderer(table.getCellRenderer(row, col), row, col);
                width = Math.max(width, c.getPreferredSize().width);
            }
            column.setPreferredWidth(Math.min(width + 18, 340));
        }
    }

    /** Builds a two-column label/field form. */
    public static JPanel formPanel(String[] labels, JComponent[] fields) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        for (int i = 0; i < labels.length; i++) {
            g.gridy = i;

            g.gridx = 0;
            g.weightx = 0;
            g.fill = GridBagConstraints.NONE;
            g.anchor = GridBagConstraints.WEST;
            panel.add(new JLabel(labels[i]), g);

            g.gridx = 1;
            g.weightx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            panel.add(fields[i], g);
        }
        return panel;
    }

    public static Border titled(String title) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(6, 6, 6, 6));
    }

    public static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    /** Shows a database error in plain language, with a hint on how to fix it. */
    public static void showError(Component parent, SQLException e) {
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        String raw = e.getMessage() == null ? "" : e.getMessage();
        String msg;

        if ("45000".equals(state)) {
            msg = raw;   // message written by our stored procedure (SIGNAL)
        } else if (raw.contains("No suitable driver")) {
            msg = "MySQL JDBC driver not found.\n"
                + "Add mysql-connector-j-x.x.x.jar to the lib folder or to your IDE's project libraries.";
        } else if (state.startsWith("08")) {
            msg = "Cannot connect to MySQL.\nCheck that the MySQL server is running on localhost:3306.";
        } else {
            switch (e.getErrorCode()) {
                case 1045:
                    msg = "MySQL rejected the username or password.\nUpdate USER and PASSWORD in DBConnection.java.";
                    break;
                case 1049:
                    msg = "Database 'library_db' not found.\nRun database/library_db.sql in MySQL first.";
                    break;
                case 1062:
                    msg = "A record with this value already exists (ISBN and email must be unique).";
                    break;
                case 1451:
                    msg = "This record can't be deleted because it has issue history linked to it.";
                    break;
                case 1452:
                    msg = "The related book, member or category does not exist.";
                    break;
                case 3819:  // MySQL: check constraint violated
                case 4025:  // MariaDB: check constraint violated
                    msg = "This change breaks a database rule.\n"
                        + "Total copies can't be less than the copies currently issued.";
                    break;
                default:
                    msg = raw;
            }
        }
        JOptionPane.showMessageDialog(parent, msg, "Database error", JOptionPane.ERROR_MESSAGE);
    }
}
