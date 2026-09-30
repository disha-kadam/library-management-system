import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.sql.*;

/** Add, update, delete and search books. */
public class BooksPanel extends JPanel implements MainFrame.Refreshable {

    private final JTextField idField = new JTextField();
    private final JTextField isbnField = new JTextField(16);
    private final JTextField titleField = new JTextField(16);
    private final JTextField authorField = new JTextField(16);
    private final JComboBox<String> categoryBox = new JComboBox<>();
    private final JTextField publisherField = new JTextField(16);
    private final JSpinner copiesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JTextField searchField = new JTextField(22);
    private final JTable table = AppUtil.createTable();

    public BooksPanel() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        idField.setEditable(false);

        // ---- left: form ----
        JPanel form = AppUtil.formPanel(
                new String[]{"Book ID", "ISBN *", "Title *", "Author *", "Category", "Publisher", "Total copies"},
                new JComponent[]{idField, isbnField, titleField, authorField, categoryBox, publisherField, copiesSpinner});

        JButton addBtn = new JButton("Add book");
        JButton updateBtn = new JButton("Save changes");
        JButton deleteBtn = new JButton("Delete book");
        JButton clearBtn = new JButton("Clear form");
        addBtn.addActionListener(e -> addBook());
        updateBtn.addActionListener(e -> updateBook());
        deleteBtn.addActionListener(e -> deleteBook());
        clearBtn.addActionListener(e -> clearForm());

        JPanel buttons = new JPanel(new GridLayout(2, 2, 8, 8));
        buttons.add(addBtn);
        buttons.add(updateBtn);
        buttons.add(deleteBtn);
        buttons.add(clearBtn);

        JPanel buttonWrap = new JPanel(new BorderLayout());
        buttonWrap.add(buttons, BorderLayout.NORTH);

        JPanel left = new JPanel(new BorderLayout(8, 12));
        left.setBorder(AppUtil.titled("Book details"));
        left.add(form, BorderLayout.NORTH);
        left.add(buttonWrap, BorderLayout.CENTER);
        

        // ---- right: search + table ----
        JButton searchBtn = new JButton("Search");
        JButton allBtn = new JButton("Show all");
        searchBtn.addActionListener(e -> loadTable());
        searchField.addActionListener(e -> loadTable());
        allBtn.addActionListener(e -> {
            searchField.setText("");
            loadTable();
        });

        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchBar.add(new JLabel("Title, author or ISBN:"));
        searchBar.add(searchField);
        searchBar.add(searchBtn);
        searchBar.add(allBtn);

        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setBorder(AppUtil.titled("Books  (click a row to edit it)"));
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
        loadCategories();
        loadTable();
    }

    private void loadCategories() {
        categoryBox.removeAllItems();
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM categories ORDER BY name")) {
            while (rs.next()) categoryBox.addItem(rs.getString(1));
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }

    private void loadTable() {
        String sql =
                "SELECT b.book_id AS ID, b.isbn AS ISBN, b.title AS Title, b.author AS Author, "
              + "       c.name AS Category, b.publisher AS Publisher, "
              + "       b.total_copies AS Total, b.available_copies AS Available "
              + "  FROM books b LEFT JOIN categories c ON b.category_id = c.category_id "
              + " WHERE b.title LIKE ? OR b.author LIKE ? OR b.isbn LIKE ? "
              + " ORDER BY b.book_id";
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
        isbnField.setText(AppUtil.str(tm.getValueAt(m, 1)));
        titleField.setText(AppUtil.str(tm.getValueAt(m, 2)));
        authorField.setText(AppUtil.str(tm.getValueAt(m, 3)));
        categoryBox.setSelectedItem(tm.getValueAt(m, 4));
        publisherField.setText(AppUtil.str(tm.getValueAt(m, 5)));
        copiesSpinner.setValue(((Number) tm.getValueAt(m, 6)).intValue());
    }

    private boolean formIsValid() {
        if (isbnField.getText().trim().isEmpty()
                || titleField.getText().trim().isEmpty()
                || authorField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "ISBN, title and author are required.");
            return false;
        }
        return true;
    }

    private int copies() {
        return ((Number) copiesSpinner.getValue()).intValue();
    }

    private void addBook() {
        if (!formIsValid()) return;
        String sql = "INSERT INTO books (isbn, title, author, category_id, publisher, total_copies, available_copies) "
                   + "VALUES (?, ?, ?, (SELECT category_id FROM categories WHERE name = ?), ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, isbnField.getText().trim());
            ps.setString(2, titleField.getText().trim());
            ps.setString(3, authorField.getText().trim());
            ps.setString(4, (String) categoryBox.getSelectedItem());
            ps.setString(5, publisherField.getText().trim());
            ps.setInt(6, copies());
            ps.setInt(7, copies());   // a new book starts with all copies available
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Book added.");
            clearForm();
            loadTable();
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }

    private void updateBook() {
        if (idField.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select a book in the table first.");
            return;
        }
        if (!formIsValid()) return;
        // available_copies is set BEFORE total_copies because MySQL applies SET left to right,
        // so it still sees the old total and shifts availability by the difference.
        String sql = "UPDATE books SET isbn = ?, title = ?, author = ?, "
                   + "category_id = (SELECT category_id FROM categories WHERE name = ?), publisher = ?, "
                   + "available_copies = available_copies + (? - total_copies), total_copies = ? "
                   + "WHERE book_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, isbnField.getText().trim());
            ps.setString(2, titleField.getText().trim());
            ps.setString(3, authorField.getText().trim());
            ps.setString(4, (String) categoryBox.getSelectedItem());
            ps.setString(5, publisherField.getText().trim());
            ps.setInt(6, copies());
            ps.setInt(7, copies());
            ps.setInt(8, Integer.parseInt(idField.getText()));
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Changes saved.");
            clearForm();
            loadTable();
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }

    private void deleteBook() {
        if (idField.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select a book in the table first.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this,
                "Delete \"" + titleField.getText() + "\"?", "Delete book", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM books WHERE book_id = ?")) {
            ps.setInt(1, Integer.parseInt(idField.getText()));
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Book deleted.");
            clearForm();
            loadTable();
        } catch (SQLException e) {
            AppUtil.showError(this, e);
        }
    }

    private void clearForm() {
        idField.setText("");
        isbnField.setText("");
        titleField.setText("");
        authorField.setText("");
        publisherField.setText("");
        copiesSpinner.setValue(1);
        if (categoryBox.getItemCount() > 0) categoryBox.setSelectedIndex(0);
        table.clearSelection();
    }
}
