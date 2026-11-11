package ui;

import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.event.ChangeListener;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

/** Shared Swing styling for the passenger and driver dashboards. */
final class DashboardStyle {
    static final Color PAGE = new Color(246, 246, 246);
    static final Color SURFACE = Color.WHITE;
    static final Color INK = new Color(20, 20, 20);
    static final Color MUTED = new Color(112, 112, 112);
    static final Color ACCENT = new Color(20, 20, 20);
    static final Color ACCENT_SOFT = new Color(231, 244, 235);
    static final Color DANGER = new Color(176, 44, 44);
    static final Color DANGER_SOFT = new Color(252, 237, 237);
    static final Color BORDER = new Color(225, 225, 225);
    static final Color SUBTLE = new Color(246, 246, 246);

    private DashboardStyle() {
    }

    static void styleButton(AbstractButton button, ButtonType type) {
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setRolloverEnabled(true);
        button.putClientProperty("campusride.buttonType", type);
        button.setBackground(buttonBackground(type, false, false));
        button.setForeground(type == ButtonType.PRIMARY ? Color.WHITE
                : type == ButtonType.DANGER ? DANGER : INK);
        if (!Boolean.TRUE.equals(button.getClientProperty("campusride.hoverListenerInstalled"))) {
            ChangeListener hoverListener = event -> {
                ButtonType currentType = (ButtonType) button.getClientProperty("campusride.buttonType");
                boolean active = button.isEnabled() && (button.getModel().isRollover()
                        || button.getModel().isPressed());
                button.setBackground(buttonBackground(currentType, active, button.isSelected()));
            };
            button.getModel().addChangeListener(hoverListener);
            button.putClientProperty("campusride.hoverListenerInstalled", Boolean.TRUE);
        }
    }

    private static Color buttonBackground(ButtonType type, boolean hovered, boolean selected) {
        return switch (type) {
            case PRIMARY -> hovered ? new Color(56, 56, 56) : ACCENT;
            case DANGER -> hovered ? new Color(247, 220, 220) : DANGER_SOFT;
            case SECONDARY -> hovered || selected ? new Color(232, 232, 232) : SUBTLE;
        };
    }

    static TitledBorder cardBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER, 1), title);
        border.setTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        border.setTitleColor(INK);
        border.setTitlePosition(TitledBorder.TOP);
        border.setTitleJustification(TitledBorder.LEADING);
        return border;
    }

    static void styleInput(JComponent input) {
        input.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        input.setBackground(SURFACE);
        input.setForeground(INK);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    }

    static void styleList(JList<?> list) {
        list.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        list.setBackground(SURFACE);
        list.setForeground(INK);
        list.setSelectionBackground(ACCENT_SOFT);
        list.setSelectionForeground(INK);
        list.setFixedCellHeight(-1);
    }

    static void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        scrollPane.getViewport().setBackground(SURFACE);
    }

    static void styleTextArea(JTextArea textArea) {
        textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        textArea.setForeground(INK);
        textArea.setBackground(SUBTLE);
        textArea.setBorder(BorderFactory.createEmptyBorder(8, 9, 8, 9));
    }

    static void styleBadge(JLabel label, Color background, Color foreground) {
        label.setOpaque(true);
        label.setBackground(background);
        label.setForeground(foreground);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        label.setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
    }

    enum ButtonType {
        PRIMARY,
        SECONDARY,
        DANGER
    }
}
