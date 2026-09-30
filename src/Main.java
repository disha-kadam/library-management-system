import javax.swing.*;
import java.awt.Font;

/** Start the application from here. */
public class Main {

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    UIManager.getLookAndFeelDefaults().put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                    break;
                }
            }
        } catch (Exception ignored) {
            // falls back to the default look and feel
        }
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
