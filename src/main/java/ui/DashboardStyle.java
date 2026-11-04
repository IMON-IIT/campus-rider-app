package ui;

import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

/** Shared Swing styling for the passenger and driver dashboards. */
final class DashboardStyle {
    static final Color PAGE = new Color(244, 247, 251);
    static final Color SURFACE = Color.WHITE;
    static final Color INK = new Color(28, 48, 74);
    static final Color MUTED = new Color(96, 111, 130);
    static final Color ACCENT = new Color(31, 119, 89);
    static final Color ACCENT_SOFT = new Color(229, 244, 237);
    static final Color DANGER = new Color(177, 55, 59);
    static final Color DANGER_SOFT = new Color(252, 235, 235);
    static final Color BORDER = new Color(220, 226, 233);
    static final Color SUBTLE = new Color(247, 249, 252);

    private DashboardStyle() {
    }

    static void styleButton(AbstractButton button, ButtonType type) {
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        switch (type) {
            case PRIMARY -> {
                button.setBackground(ACCENT);
                button.setForeground(Color.WHITE);
            }
            case DANGER -> {
                button.setBackground(DANGER_SOFT);
                button.setForeground(DANGER);
            }
            case SECONDARY -> {
                button.setBackground(SUBTLE);
                button.setForeground(INK);
            }
        }
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
        input.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        input.setBackground(SURFACE);
        input.setForeground(INK);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
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
