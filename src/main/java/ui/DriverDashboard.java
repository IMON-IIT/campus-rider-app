package ui;

import model.Rickshaw;
import model.RideRequest;
import model.DriverAccount;
import service.RideService;
import service.RouteService;
import service.AccountService;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/** Driver-facing view of assigned requests and active rides. */
public class DriverDashboard extends JFrame {
    private static final Color NAVY = DashboardStyle.INK;
    private static final Color MUTED = DashboardStyle.MUTED;
    static final int VIEW_REFRESH_INTERVAL_MS = 250;

    private final RideService rideService;
    private final RouteService routeService;
    private final Rickshaw driver;
    private DriverAccount driverAccount;
    private final AccountService accountService;
    private final Runnable logoutAction;
    private final Runnable additionalSessionAction;
    private final JLabel driverTitle = new JLabel();
    private final JLabel driverDetails = new JLabel();
    private final DefaultListModel<RideRequest> requestsModel = new DefaultListModel<>();
    private final JList<RideRequest> requestList = new JList<>(requestsModel);
    private final JLabel requestNote = new JLabel();
    private final DefaultListModel<RideRequest> currentRideModel = new DefaultListModel<>();
    private final JList<RideRequest> currentRideList = new JList<>(currentRideModel);
    private final JLabel currentRideNote = new JLabel("No passenger bookings yet. Accepted requests appear here.");
    private final JLabel seatAvailabilityLabel = new JLabel();
    private final JComboBox<model.Location> currentLocationBox;
    private final JComboBox<model.Location> destinationBox;
    private final JSpinner totalSeatsSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 20, 1));
    private final JSpinner availableSeatsSpinner = new JSpinner(new SpinnerNumberModel(4, 0, 20, 1));
    private final CampusMapPanel campusMapPanel;
    private final GoogleMapsPanel googleMapsPanel;
    private final JTabbedPane mapTabs = new JTabbedPane();
    private final JToggleButton onlineToggle = new JToggleButton("Go Online");
    private final JLabel onlineStatus = new JLabel("Offline");
    private final JButton acceptButton = new JButton("Accept");
    private final JButton rejectButton = new JButton("Reject");
    private final JButton confirmPickupButton = new JButton("Confirm Pickup / Passenger Onboard");
    private final JButton completePassengerRideButton = new JButton("Complete Passenger Trip");
    private final JButton startTripButton = new JButton("Start Trip");
    private final JButton endTripButton = new JButton("End Trip");
    private final JButton updateSeatsButton = new JButton("Update Seats");
    private final MessageLabel message = new MessageLabel();
    private final Timer refreshTimer;
    private final Timer simulationTimer;
    private boolean simulationAdvanceInProgress;
    private boolean responseInProgress;
    private boolean tripWasActive;
    private boolean syncingSeatFields;
    private boolean totalSeatsEdited;
    private boolean availableSeatsEdited;
    private int persistedTotalSeats = 4;
    private int persistedAvailableSeats = 4;

    public DriverDashboard(RideService rideService, Rickshaw driver) {
        this(rideService, driver, null, null, null);
    }

    public DriverDashboard(RideService rideService, Rickshaw driver,
                           DriverAccount driverAccount, AccountService accountService, Runnable logoutAction) {
        this(rideService, driver, driverAccount, accountService, logoutAction, null);
    }

    public DriverDashboard(RideService rideService, Rickshaw driver,
                           DriverAccount driverAccount, AccountService accountService, Runnable logoutAction,
                           Runnable additionalSessionAction) {
        super("Campus Rider — Driver");
        this.rideService = Objects.requireNonNull(rideService, "Ride service is required.");
        this.routeService = this.rideService.getRouteService();
        this.driver = Objects.requireNonNull(driver, "A rickshaw profile is required for the driver dashboard.");
        this.driverAccount = driverAccount;
        this.accountService = accountService;
        this.logoutAction = logoutAction;
        this.additionalSessionAction = additionalSessionAction;
        if (driverAccount != null && !Objects.equals(driverAccount.getRickshawId(), driver.getId())) {
            throw new IllegalArgumentException("The driver account does not match the rickshaw profile.");
        }
        model.Location[] locations = routeService.getLocationsSorted().toArray(new model.Location[0]);
        currentLocationBox = new JComboBox<>(locations);
        destinationBox = new JComboBox<>(locations);
        DashboardStyle.styleInput(currentLocationBox);
        DashboardStyle.styleInput(destinationBox);
        DashboardStyle.styleInput(totalSeatsSpinner);
        DashboardStyle.styleInput(availableSeatsSpinner);
        DashboardStyle.styleList(requestList);
        DashboardStyle.styleList(currentRideList);
        campusMapPanel = new CampusMapPanel(routeService);
        googleMapsPanel = new GoogleMapsPanel(routeService);
        mapTabs.addTab("Google Maps", googleMapsPanel);
        mapTabs.addTab("Campus Graph (offline)", campusMapPanel);
        mapTabs.setSelectedIndex(GoogleMapsPanel.isConfigured() ? 0 : 1);
        if (driver != null) {
            selectLocation(currentLocationBox, driver.getLocationId());
        }
        String initialDestination = driver != null && driverAccount != null
                ? driverAccount.getDestinationId()
                : locations.length > 1 ? locations[1].getId() : "";
        selectLocation(destinationBox, initialDestination);
        if (driver != null) {
            totalSeatsSpinner.setValue(driver.getTotalSeats());
            availableSeatsSpinner.setValue(driver.getAvailableSeats());
            persistedTotalSeats = driver.getTotalSeats();
            persistedAvailableSeats = driver.getAvailableSeats();
        }
        totalSeatsSpinner.addChangeListener(event -> {
            if (!syncingSeatFields) {
                totalSeatsEdited = (Integer) totalSeatsSpinner.getValue() != persistedTotalSeats;
            }
        });
        availableSeatsSpinner.addChangeListener(event -> {
            if (!syncingSeatFields) {
                availableSeatsEdited = (Integer) availableSeatsSpinner.getValue() != persistedAvailableSeats;
            }
        });

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 700));

        JPanel content = new JPanel(new BorderLayout(16, 16));
        content.setBackground(DashboardStyle.PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(22, 26, 18, 26));
        content.add(buildHeader(), BorderLayout.NORTH);
        content.add(buildMainContent(), BorderLayout.CENTER);
        content.add(message, BorderLayout.SOUTH);
        setContentPane(content);

        requestList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        requestList.addListSelectionListener(event -> updateActionButtons());
        currentRideList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        currentRideList.addListSelectionListener(event -> updateActiveRideButtons());
        acceptButton.addActionListener(event -> respondToRequest(true));
        rejectButton.addActionListener(event -> respondToRequest(false));
        confirmPickupButton.addActionListener(event -> confirmPassengerPickup());
        completePassengerRideButton.addActionListener(event -> completePassengerRide());
        onlineToggle.addActionListener(event -> updateOnlineStatus());

        updateDriverDetails();
        refreshRideLists();
        refreshTimer = new Timer(VIEW_REFRESH_INTERVAL_MS, event -> refreshDriverView());
        simulationTimer = new Timer(RideService.TRIP_STEP_INTERVAL_MS, event -> advanceTrip());
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent event) {
                refreshTimer.start();
                simulationTimer.start();
            }

            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                refreshTimer.stop();
                simulationTimer.stop();
            }
        });
        setSize(1120, 790);
        setLocationRelativeTo(null);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);
        JPanel info = new JPanel(new BorderLayout(4, 4));
        info.setOpaque(false);
        driverTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23));
        driverTitle.setForeground(NAVY);
        driverDetails.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        driverDetails.setForeground(MUTED);
        info.add(driverTitle, BorderLayout.NORTH);
        info.add(driverDetails, BorderLayout.CENTER);

        JPanel status = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        status.setOpaque(false);
        onlineStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        DashboardStyle.styleBadge(onlineStatus, DashboardStyle.SUBTLE, MUTED);
        DashboardStyle.styleButton(onlineToggle, DashboardStyle.ButtonType.PRIMARY);
        status.add(onlineStatus);
        status.add(onlineToggle);

        JPanel utilityButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        utilityButtons.setOpaque(false);
        DashboardStyle.styleButton(updateSeatsButton, DashboardStyle.ButtonType.SECONDARY);
        updateSeatsButton.addActionListener(event -> updateSeats());
        utilityButtons.add(updateSeatsButton);
        if (additionalSessionAction != null) {
            JButton anotherSession = new JButton("Open Another Session");
            DashboardStyle.styleButton(anotherSession, DashboardStyle.ButtonType.SECONDARY);
            anotherSession.setToolTipText("Open another account while keeping this dashboard live.");
            anotherSession.addActionListener(event -> additionalSessionAction.run());
            utilityButtons.add(anotherSession);
        }
        if (driverAccount != null && accountService != null) {
            JButton updateDestination = new JButton("Update Destination");
            DashboardStyle.styleButton(updateDestination, DashboardStyle.ButtonType.SECONDARY);
            updateDestination.addActionListener(event -> changeDestination());
            utilityButtons.add(updateDestination);
        }
        if (logoutAction != null) {
            JButton logout = new JButton("Log Out");
            DashboardStyle.styleButton(logout, DashboardStyle.ButtonType.SECONDARY);
            logout.addActionListener(event -> {
                if (!rideService.getAssignedRequests(driver.getId()).isEmpty()
                        || !rideService.getActivePassengerRides(driver.getId()).isEmpty()) {
                    message.showError("Respond to requests and finish active passenger rides before logging out. "
                            + "Use Open Another Session to access another account.");
                    return;
                }
                refreshTimer.stop();
                dispose();
                logoutAction.run();
            });
            utilityButtons.add(logout);
        }

        JPanel controls = new JPanel(new BorderLayout(4, 2));
        controls.setOpaque(false);
        controls.add(status, BorderLayout.NORTH);
        controls.add(utilityButtons, BorderLayout.SOUTH);
        header.add(info, BorderLayout.CENTER);
        header.add(controls, BorderLayout.EAST);
        return header;
    }

    private JPanel buildMainContent() {
        JPanel sidebar = new JPanel(new BorderLayout(10, 10));
        sidebar.setOpaque(false);
        sidebar.add(buildTripControls(), BorderLayout.NORTH);
        JSplitPane bookings = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildRequestsSection(), buildActiveBookingSection());
        bookings.setResizeWeight(0.52);
        bookings.setContinuousLayout(true);
        bookings.setOneTouchExpandable(true);
        bookings.setBorder(null);
        sidebar.add(bookings, BorderLayout.CENTER);

        JSplitPane workspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                sidebar, buildCurrentRideSection());
        workspace.setResizeWeight(0.38);
        workspace.setContinuousLayout(true);
        workspace.setOneTouchExpandable(true);
        workspace.setDividerSize(10);
        workspace.setBorder(null);
        workspace.setPreferredSize(new Dimension(1180, 650));
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setOpaque(false);
        main.add(workspace, BorderLayout.CENTER);
        return main;
    }

    private JPanel buildTripControls() {
        JPanel section = createCard("Trip setup and seats");
        section.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 5, 3, 5);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        addTripField(section, c, 0, "Current location", currentLocationBox);
        addTripField(section, c, 1, "Destination", destinationBox);
        addTripField(section, c, 2, "Total seats", totalSeatsSpinner);
        addTripField(section, c, 3, "Available seats", availableSeatsSpinner);
        seatAvailabilityLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        DashboardStyle.styleBadge(seatAvailabilityLabel, DashboardStyle.ACCENT_SOFT, DashboardStyle.ACCENT);
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        section.add(seatAvailabilityLabel, c);
        startTripButton.addActionListener(event -> startTrip());
        endTripButton.addActionListener(event -> endTrip());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.add(startTripButton);
        actions.add(endTripButton);
        DashboardStyle.styleButton(startTripButton, DashboardStyle.ButtonType.PRIMARY);
        DashboardStyle.styleButton(endTripButton, DashboardStyle.ButtonType.DANGER);
        c.gridx = 1;
        c.gridy = 5;
        c.gridwidth = 2;
        section.add(actions, c);
        return section;
    }

    private void addTripField(JPanel panel, GridBagConstraints c, int row, String label, java.awt.Component field) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
    }

    private JPanel buildRequestsSection() {
        JPanel section = createCard("Requests assigned to this driver");
        section.setLayout(new BorderLayout(8, 8));
        requestList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        DashboardStyle.styleButton(acceptButton, DashboardStyle.ButtonType.PRIMARY);
        DashboardStyle.styleButton(rejectButton, DashboardStyle.ButtonType.DANGER);
        requestList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (value instanceof RideRequest ride) {
                    label.setText("<html><b>Ride #" + ride.getId() + " · "
                            + escapeHtml(ride.getPassengerName()) + "</b><br>"
                            + escapeHtml(routeService.getLocationName(ride.getPickupId()))
                            + " → " + escapeHtml(routeService.getLocationName(ride.getDestinationId()))
                            + "<br>Requested: " + ride.getRequestedSeats() + " seat(s) · Available: "
                            + escapeHtml(availableSeatsFor(ride)) + "</html>");
                }
                label.setOpaque(true);
                label.setBackground(isSelected ? new Color(255, 242, 204) : Color.WHITE);
                label.setForeground(NAVY);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(isSelected
                                ? new Color(224, 172, 49) : DashboardStyle.BORDER),
                        BorderFactory.createEmptyBorder(9, 10, 9, 10)));
                return label;
            }
        });
        requestList.setFixedCellHeight(74);
        JScrollPane requestScroll = new JScrollPane(requestList);
        DashboardStyle.styleScrollPane(requestScroll);
        section.add(requestScroll, BorderLayout.CENTER);

        requestNote.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        requestNote.setForeground(MUTED);
        section.add(requestNote, BorderLayout.NORTH);

        JButton refresh = new JButton("Refresh");
        DashboardStyle.styleButton(refresh, DashboardStyle.ButtonType.SECONDARY);
        refresh.addActionListener(event -> refreshRideLists());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(acceptButton);
        actions.add(rejectButton);
        actions.add(refresh);
        section.add(actions, BorderLayout.SOUTH);
        return section;
    }

    private JPanel buildActiveBookingSection() {
        JPanel section = createCard("Passenger bookings");
        section.setLayout(new BorderLayout(8, 8));
        currentRideList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        DashboardStyle.styleButton(confirmPickupButton, DashboardStyle.ButtonType.PRIMARY);
        DashboardStyle.styleButton(completePassengerRideButton, DashboardStyle.ButtonType.PRIMARY);
        currentRideNote.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        currentRideNote.setForeground(MUTED);
        currentRideList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, selected, focus);
                if (value instanceof RideRequest ride) {
                    label.setText("<html><b>Ride #" + ride.getId() + " · "
                            + escapeHtml(ride.getPassengerName()) + "</b><br>"
                            + escapeHtml(routeService.getLocationName(ride.getPickupId())) + " → "
                            + escapeHtml(routeService.getLocationName(ride.getDestinationId()))
                            + "<br>" + ride.getRequestedSeats() + " seat(s) · "
                            + ride.getStatus().name().replace('_', ' ') + "</html>");
                }
                label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
                return label;
            }
        });
        currentRideList.setFixedCellHeight(62);
        JScrollPane ridesScroll = new JScrollPane(currentRideList);
        DashboardStyle.styleScrollPane(ridesScroll);
        JPanel rideListArea = new JPanel(new BorderLayout(0, 6));
        rideListArea.setOpaque(false);
        rideListArea.add(currentRideNote, BorderLayout.NORTH);
        rideListArea.add(ridesScroll, BorderLayout.CENTER);
        confirmPickupButton.setEnabled(false);
        completePassengerRideButton.setEnabled(false);
        JPanel rideActions = new JPanel(new java.awt.GridLayout(1, 0, 6, 0));
        rideActions.setOpaque(false);
        rideActions.add(confirmPickupButton);
        rideActions.add(completePassengerRideButton);
        section.add(rideListArea, BorderLayout.CENTER);
        section.add(rideActions, BorderLayout.SOUTH);
        return section;
    }

    private JPanel buildCurrentRideSection() {
        JPanel section = createCard("Live trip map");
        section.setLayout(new BorderLayout(8, 8));
        JLabel mapNote = new JLabel("Schematic campus road graph · not GPS");
        mapNote.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        mapNote.setForeground(MUTED);
        section.add(mapNote, BorderLayout.NORTH);
        mapTabs.setMinimumSize(new Dimension(420, 320));
        mapTabs.setPreferredSize(new Dimension(760, 600));
        section.add(mapTabs, BorderLayout.CENTER);
        return section;
    }

    private String availableSeatsFor(RideRequest ride) {
        Rickshaw assigned = rideService.getRickshaw(ride.getRickshawId());
        return assigned == null ? "unknown" : assigned.getAvailableSeats() + "/" + assigned.getTotalSeats();
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JPanel createCard(String title) {
        JPanel card = new JPanel();
        card.setBackground(DashboardStyle.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                DashboardStyle.cardBorder(title),
                BorderFactory.createEmptyBorder(8, 10, 10, 10)));
        return card;
    }

    private void updateDriverDetails() {
        if (driver == null) {
            driverTitle.setText("No driver profile available");
            driverDetails.setText("No rickshaws are configured.");
            onlineToggle.setEnabled(false);
            DashboardStyle.styleBadge(onlineStatus, DashboardStyle.SUBTLE, MUTED);
            return;
        }
        boolean activeTrip = driver.hasActiveTrip();
        if (tripWasActive && !activeTrip) {
            selectLocation(currentLocationBox, driver.getLocationId());
        }
        tripWasActive = activeTrip;
        driverTitle.setText("Welcome, " + driver.getDriverName());
        String details = driver.getId() + "  |  " + routeService.getLocationName(driver.getLocationId());
        details += "  |  Seats: " + driver.getAvailableSeats() + "/" + driver.getTotalSeats();
        if (driver.hasActiveTrip()) {
            details += "  |  Active trip to " + routeService.getLocationName(driver.getDestinationId());
        }
        driverDetails.setText(details);
        updateSeatAvailabilityLabel();
        synchronizeUneditedSeatFields();
        boolean online = rideService.isDriverOnline(driver.getId());
        onlineToggle.setEnabled(true);
        onlineToggle.setSelected(online);
        onlineToggle.setText(online ? "Go Offline" : "Go Online");
        DashboardStyle.styleButton(onlineToggle, online
                ? DashboardStyle.ButtonType.DANGER : DashboardStyle.ButtonType.PRIMARY);
        onlineStatus.setText(online ? "Online for matching" : "Offline");
        DashboardStyle.styleBadge(onlineStatus, online
                ? DashboardStyle.ACCENT_SOFT : DashboardStyle.SUBTLE,
                online ? DashboardStyle.ACCENT : MUTED);
        updateTripControls();
    }

    private void synchronizeUneditedSeatFields() {
        syncingSeatFields = true;
        try {
            if (!totalSeatsEdited && persistedTotalSeats != driver.getTotalSeats()) {
                totalSeatsSpinner.setValue(driver.getTotalSeats());
                persistedTotalSeats = driver.getTotalSeats();
            }
            if (!availableSeatsEdited && persistedAvailableSeats != driver.getAvailableSeats()) {
                availableSeatsSpinner.setValue(driver.getAvailableSeats());
                persistedAvailableSeats = driver.getAvailableSeats();
            }
        } finally {
            syncingSeatFields = false;
        }
    }

    private void markSeatFieldsSaved() {
        persistedTotalSeats = driver.getTotalSeats();
        persistedAvailableSeats = driver.getAvailableSeats();
        totalSeatsEdited = false;
        availableSeatsEdited = false;
        syncingSeatFields = true;
        try {
            totalSeatsSpinner.setValue(persistedTotalSeats);
            availableSeatsSpinner.setValue(persistedAvailableSeats);
        } finally {
            syncingSeatFields = false;
        }
    }

    private void updateTripControls() {
        boolean hasDriver = driver != null;
        boolean activeTrip = hasDriver && driver.hasActiveTrip();
        currentLocationBox.setEnabled(hasDriver && !activeTrip);
        destinationBox.setEnabled(hasDriver && !activeTrip);
        startTripButton.setEnabled(hasDriver && !activeTrip);
        endTripButton.setEnabled(hasDriver && activeTrip);
        boolean hasAssignedOffer = hasDriver && !rideService.getAssignedRequests(driver.getId()).isEmpty();
        boolean hasActiveBooking = hasDriver && !rideService.getActivePassengerRides(driver.getId()).isEmpty();
        updateSeatsButton.setEnabled(hasDriver && !hasAssignedOffer && !hasActiveBooking);
    }

    private void changeDestination() {
        List<model.Location> locations = routeService.getLocationsSorted();
        model.Location current = locations.stream()
                .filter(location -> location.getId().equals(driverAccount.getDestinationId()))
                .findFirst().orElse(null);
        Object selected = javax.swing.JOptionPane.showInputDialog(this,
                "Choose your current destination:", "Update Destination",
                javax.swing.JOptionPane.PLAIN_MESSAGE, null, locations.toArray(), current);
        if (!(selected instanceof model.Location location)) {
            return;
        }
        try {
            driverAccount = accountService.updateDriverDestination(
                    driverAccount.getMobileNumber(), location.getId());
            updateDriverDetails();
            message.showSuccess("Destination updated to " + location.getName() + ".");
        } catch (IllegalArgumentException | java.io.IOException e) {
            message.showError(e.getMessage());
        }
    }

    private void updateOnlineStatus() {
        if (driver == null) {
            return;
        }
        boolean online = onlineToggle.isSelected();
        try {
            rideService.setDriverOnline(driver.getId(), online);
            onlineToggle.setText(online ? "Go Offline" : "Go Online");
            onlineStatus.setText(online ? "Online for matching" : "Offline");
            message.showSuccess(online
                    ? "You are online; offers arrive only during an active trip with available seats."
                    : "You will not receive new ride requests.");
        } catch (IllegalStateException e) {
            onlineToggle.setSelected(!online);
            message.showError(e.getMessage());
        }
    }

    private void respondToRequest(boolean accept) {
        RideRequest selected = requestList.getSelectedValue();
        if (selected == null || driver == null) {
            message.showError("Select an assigned request first.");
            return;
        }
        int rideId = selected.getId();
        String rickshawId = driver.getId();
        responseInProgress = true;
        acceptButton.setEnabled(false);
        rejectButton.setEnabled(false);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return accept ? rideService.acceptRideRequest(rideId, rickshawId)
                        : rideService.rejectRideRequest(rideId, rickshawId);
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    if (result.startsWith("No other suitable driver")) {
                        message.showError(result);
                    } else {
                        message.showSuccess(result);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    message.showError("The driver response was interrupted.");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    String detail = cause == null ? null : cause.getMessage();
                    message.showError(detail == null || detail.isBlank()
                            ? "Could not process the driver response."
                            : detail);
                } finally {
                    responseInProgress = false;
                    refreshRideLists();
                }
            }
        }.execute();
    }

    private void updateActionButtons() {
        boolean enabled = !responseInProgress && requestList.getSelectedValue() != null && driver != null;
        acceptButton.setEnabled(enabled);
        rejectButton.setEnabled(enabled);
        updateActiveRideButtons();
    }

    private void updateActiveRideButtons() {
        RideRequest selected = currentRideList.getSelectedValue();
        boolean enabled = !responseInProgress && selected != null && driver != null;
        confirmPickupButton.setEnabled(enabled
                && selected.getStatus() == model.RideStatus.ARRIVED_AT_PICKUP);
        completePassengerRideButton.setEnabled(enabled
                && selected.getStatus() == model.RideStatus.ARRIVED_AT_DESTINATION);
    }

    private void updateSeatAvailabilityLabel() {
        if (driver == null) {
            seatAvailabilityLabel.setText("No rickshaw seat information available.");
            DashboardStyle.styleBadge(seatAvailabilityLabel, DashboardStyle.SUBTLE, MUTED);
            return;
        }
        RideService.SeatAvailability seats = rideService.getSeatAvailability(driver.getId());
        seatAvailabilityLabel.setText("Rickshaw Capacity: " + seats.totalSeats()
                + "   Booked Seats: " + seats.bookedSeats()
                + "   Occupied Seats: " + seats.occupiedSeats()
                + "   Available Seats: " + seats.availableSeats()
                + "   Passenger Bookings: " + seats.passengerBookings());
        DashboardStyle.styleBadge(seatAvailabilityLabel,
                seats.availableSeats() == 0 ? DashboardStyle.DANGER_SOFT : DashboardStyle.ACCENT_SOFT,
                seats.availableSeats() == 0 ? DashboardStyle.DANGER : DashboardStyle.ACCENT);
    }

    private void refreshRideLists() {
        List<RideRequest> assigned = driver == null
                ? List.of() : rideService.getAssignedRequests(driver.getId());
        requestNote.setText(assigned.isEmpty()
                ? "No incoming requests for this rickshaw. Passenger requests appear here after submission."
                : assigned.size() + (assigned.size() == 1
                ? " incoming request — select it to Accept or Reject."
                : " incoming requests — select one to Accept or Reject."));
        requestNote.setForeground(assigned.isEmpty() ? MUTED : new Color(145, 94, 12));
        List<Integer> displayedIds = new java.util.ArrayList<>();
        for (int i = 0; i < requestsModel.size(); i++) {
            displayedIds.add(requestsModel.get(i).getId());
        }
        List<Integer> assignedIds = assigned.stream().map(RideRequest::getId).toList();
        if (!displayedIds.equals(assignedIds)) {
            Integer selectedRideId = requestList.getSelectedValue() == null
                    ? null : requestList.getSelectedValue().getId();
            requestsModel.clear();
            int selectedIndex = -1;
            for (int i = 0; i < assigned.size(); i++) {
                RideRequest ride = assigned.get(i);
                requestsModel.addElement(ride);
                if (selectedRideId != null && ride.getId() == selectedRideId) {
                    selectedIndex = i;
                }
            }
            if (selectedIndex >= 0) {
                requestList.setSelectedIndex(selectedIndex);
            } else {
                requestList.clearSelection();
            }
        }
        requestList.repaint();
        updateActionButtons();

        Integer selectedActiveId = currentRideList.getSelectedValue() == null
                ? null : currentRideList.getSelectedValue().getId();
        List<RideRequest> active = driver == null
                ? List.of() : rideService.getActivePassengerRides(driver.getId());
        currentRideNote.setText(active.isEmpty()
                ? "No passenger bookings yet. Accepted requests appear here."
                : active.size() + (active.size() == 1
                ? " active passenger booking" : " active passenger bookings"));
        List<Integer> displayedActiveIds = new java.util.ArrayList<>();
        for (int i = 0; i < currentRideModel.size(); i++) {
            displayedActiveIds.add(currentRideModel.get(i).getId());
        }
        List<Integer> activeIds = active.stream().map(RideRequest::getId).toList();
        if (!displayedActiveIds.equals(activeIds)) {
            currentRideModel.clear();
            int selectedIndex = -1;
            for (int i = 0; i < active.size(); i++) {
                RideRequest ride = active.get(i);
                currentRideModel.addElement(ride);
                if (selectedActiveId != null && selectedActiveId == ride.getId()) {
                    selectedIndex = i;
                }
            }
            if (selectedIndex >= 0) {
                currentRideList.setSelectedIndex(selectedIndex);
            } else {
                currentRideList.clearSelection();
            }
        }
        currentRideList.repaint();
        updateSeatAvailabilityLabel();
        updateTripControls();
        updateActiveRideButtons();
        refreshMap();
    }

    private void confirmPassengerPickup() {
        RideRequest selected = currentRideList.getSelectedValue();
        if (selected == null || driver == null
                || selected.getStatus() != model.RideStatus.ARRIVED_AT_PICKUP) {
            message.showError("Select a passenger waiting at pickup first.");
            return;
        }
        updateResponseButtons(true);
        int rideId = selected.getId();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                rideService.confirmPassengerPickup(rideId, driver.getId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    message.showSuccess("Passenger onboard. Trip is in progress.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    message.showError("Pickup confirmation was interrupted.");
                } catch (ExecutionException e) {
                    showActionError(e, "Could not confirm passenger pickup.");
                } finally {
                    updateResponseButtons(false);
                    refreshRideLists();
                }
            }
        }.execute();
    }

    private void completePassengerRide() {
        RideRequest selected = currentRideList.getSelectedValue();
        if (selected == null || driver == null
                || selected.getStatus() != model.RideStatus.ARRIVED_AT_DESTINATION) {
            message.showError("Select a passenger whose rickshaw has reached the destination.");
            return;
        }
        updateResponseButtons(true);
        int rideId = selected.getId();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return rideService.completePassengerTrip(rideId, driver.getId());
            }

            @Override
            protected void done() {
                try {
                    message.showSuccess(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    message.showError("Trip completion was interrupted.");
                } catch (ExecutionException e) {
                    showActionError(e, "Could not complete the passenger trip.");
                } finally {
                    updateResponseButtons(false);
                    refreshRideLists();
                }
            }
        }.execute();
    }

    private void updateResponseButtons(boolean inProgress) {
        responseInProgress = inProgress;
        updateActionButtons();
    }

    private void showActionError(ExecutionException exception, String fallback) {
        Throwable cause = exception.getCause();
        String detail = cause == null ? null : cause.getMessage();
        message.showError(detail == null || detail.isBlank() ? fallback : detail);
    }

    private void startTrip() {
        if (driver == null) {
            return;
        }
        model.Location current = (model.Location) currentLocationBox.getSelectedItem();
        model.Location destination = (model.Location) destinationBox.getSelectedItem();
        if (current == null || destination == null) {
            message.showError("Choose a current location and destination.");
            return;
        }
        try {
            rideService.startTrip(driver.getId(), current.getId(), destination.getId(),
                    (Integer) totalSeatsSpinner.getValue(), (Integer) availableSeatsSpinner.getValue());
            markSeatFieldsSaved();
            message.showSuccess("Trip started. You can receive route-compatible seat requests while online.");
            refreshRideLists();
            updateDriverDetails();
        } catch (IllegalArgumentException | IllegalStateException e) {
            message.showError(e.getMessage());
        }
    }

    private void endTrip() {
        if (driver == null) {
            return;
        }
        try {
            rideService.endTrip(driver.getId());
            message.showSuccess("Trip ended at " + routeService.getLocationName(driver.getLocationId()) + ".");
            refreshRideLists();
            updateDriverDetails();
        } catch (IllegalStateException e) {
            message.showError(e.getMessage());
        }
    }

    private void updateSeats() {
        if (driver == null) {
            return;
        }
        try {
            rideService.updateTripSeats(driver.getId(), (Integer) totalSeatsSpinner.getValue(),
                    (Integer) availableSeatsSpinner.getValue());
            markSeatFieldsSaved();
            updateDriverDetails();
            message.showSuccess("Seat availability updated.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            message.showError(e.getMessage());
        }
    }

    private void refreshDriverView() {
        refreshRideLists();
        updateDriverDetails();
    }

    private void advanceTrip() {
        if (driver == null || !driver.hasActiveTrip() || simulationAdvanceInProgress) {
            return;
        }

        simulationAdvanceInProgress = true;
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return rideService.advanceTripOneStep(driver.getId());
            }

            @Override
            protected void done() {
                simulationAdvanceInProgress = false;
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    message.showError("Trip simulation was interrupted.");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    message.showError(cause == null || cause.getMessage() == null
                            ? "Trip simulation could not advance." : cause.getMessage());
                }
                if (isDisplayable()) {
                    refreshRideLists();
                    updateDriverDetails();
                }
            }
        }.execute();
    }

    private void refreshMap() {
        if (driver == null) {
            return;
        }
        List<RideRequest> active = rideService.getActivePassengerRides(driver.getId());
        RideRequest focus = active.isEmpty() ? null : active.get(0);
        String status = focus == null ? driver.hasActiveTrip() ? "Trip in progress" : "No active trip"
                : switch (focus.getStatus()) {
                    case MATCHED -> "Rickshaw approaching pickup";
                    case ARRIVED_AT_PICKUP -> "Rickshaw at pickup · Confirm passenger onboard";
                    case IN_PROGRESS -> "Passenger onboard · Trip in progress";
                    case ARRIVED_AT_DESTINATION -> "At destination · Driver completion required";
                    default -> "Ride #" + focus.getId() + ": " + focus.getStatus().name().replace('_', ' ');
                };
        List<String> route = driver.hasActiveTrip() ? rideService.getTripRoute(driver.getId()) : List.of();
        String pickup = focus == null ? "" : focus.getPickupId();
        String destination = focus == null ? driver.getDestinationId() : focus.getDestinationId();
        campusMapPanel.setTripView(route, driver.getLocationId(), pickup, destination, status,
                rideService.getTripMovement(driver.getId()));
        googleMapsPanel.setTripView(route, driver.getLocationId(), pickup, destination);
    }

    private static void selectLocation(JComboBox<model.Location> combo, String locationId) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).getId().equals(locationId)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }
}
