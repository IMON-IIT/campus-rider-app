package ui;

import dsa.RouteResult;
import model.Location;
import service.RouteService;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

/** Tab 1: Trie location search and Dijkstra shortest route. */
public class RoutePanel extends JPanel {
    private final RouteService routeService;

    // Package-private so the UI tests can drive the panel.
    final JTextField searchField = new JTextField(20);
    final DefaultListModel<Location> matchesModel = new DefaultListModel<>();
    final JList<Location> matchesList = new JList<>(matchesModel);
    final JLabel matchCountLabel = new JLabel("Type to search");
    final JButton useAsStartButton = new JButton("Use as Start");
    final JButton useAsDestinationButton = new JButton("Use as Destination");
    final JComboBox<Location> fromBox;
    final JComboBox<Location> toBox;
    final JButton findRouteButton = new JButton("Find Shortest Route");
    final JTextArea resultArea = new JTextArea(16, 50);
    final MessageLabel message = new MessageLabel();

    public RoutePanel(RouteService routeService) {
        super(new BorderLayout(10, 10));
        this.routeService = routeService;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Location[] locations = routeService.getLocationsSorted().toArray(new Location[0]);
        fromBox = new JComboBox<>(locations);
        toBox = new JComboBox<>(locations);
        if (locations.length > 1) {
            toBox.setSelectedIndex(1);
        }

        add(buildSearchPanel(), BorderLayout.WEST);
        add(buildRoutePanel(), BorderLayout.CENTER);
        add(message, BorderLayout.SOUTH);

        // Search again on every key press.
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateMatches();
            }

            public void removeUpdate(DocumentEvent e) {
                updateMatches();
            }

            public void changedUpdate(DocumentEvent e) {
                updateMatches();
            }
        });
        useAsStartButton.addActionListener(e -> useSelectedMatch(fromBox, "Start"));
        useAsDestinationButton.addActionListener(e -> useSelectedMatch(toBox, "Destination"));
        findRouteButton.addActionListener(e -> findRoute());
    }

    private JPanel buildSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Search Location (Trie)"));
        panel.setPreferredSize(new Dimension(340, 400));

        JPanel top = new JPanel(new BorderLayout(5, 5));
        top.add(searchField, BorderLayout.NORTH);
        top.add(matchCountLabel, BorderLayout.SOUTH);

        matchesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 5, 5));
        buttons.add(useAsStartButton);
        buttons.add(useAsDestinationButton);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(matchesList), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildRoutePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Shortest Route (Dijkstra)"));

        JPanel choose = new JPanel(new FlowLayout(FlowLayout.LEFT));
        choose.add(new JLabel("Start:"));
        choose.add(fromBox);
        choose.add(new JLabel("Destination:"));
        choose.add(toBox);
        choose.add(findRouteButton);

        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        panel.add(choose, BorderLayout.NORTH);
        panel.add(new JScrollPane(resultArea), BorderLayout.CENTER);
        return panel;
    }

    void updateMatches() {
        String text = searchField.getText();
        List<Location> matches = routeService.searchLocations(text);
        matchesModel.clear();
        for (Location location : matches) {
            matchesModel.addElement(location);
        }
        if (text.isBlank()) {
            matchCountLabel.setText("Type to search");
        } else {
            matchCountLabel.setText(matches.size() + " match(es)");
        }
        if (!matches.isEmpty()) {
            matchesList.setSelectedIndex(0);
        }
    }

    private void useSelectedMatch(JComboBox<Location> box, String label) {
        Location selected = matchesList.getSelectedValue();
        if (selected == null) {
            message.showError("Search for a location and select it in the list first.");
            return;
        }
        box.setSelectedItem(selected);
        message.showSuccess(label + " set to " + selected.getName() + ".");
    }

    void findRoute() {
        Location from = (Location) fromBox.getSelectedItem();
        Location to = (Location) toBox.getSelectedItem();
        if (from == null || to == null) {
            message.showError("Please choose a start and a destination.");
            return;
        }
        RouteResult route = routeService.findRoute(from.getId(), to.getId());
        resultArea.setText(routeService.describeRoute(route));
        resultArea.setCaretPosition(0);
        if (route.isFound()) {
            message.showSuccess(String.format("Shortest route from %s to %s: %.1f m.",
                    from.getName(), to.getName(), route.getTotalDistanceMeters()));
        } else {
            message.showError(route.getMessage());
        }
    }
}
