package ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import java.awt.Color;

/** One-line message area: green for success, red for errors. */
public class MessageLabel extends JLabel {
    private static final Color SUCCESS = new Color(0, 110, 40);
    private static final Color ERROR = new Color(180, 0, 0);

    private boolean error;

    public MessageLabel() {
        super(" ");
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    }

    public void showSuccess(String text) {
        error = false;
        setForeground(SUCCESS);
        setText(text);
    }

    public void showError(String text) {
        error = true;
        setForeground(ERROR);
        setText("Error: " + text);
    }

    public boolean isError() {
        return error;
    }
}
