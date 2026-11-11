package ui;

import model.Location;
import model.PassengerAccount;
import model.Rickshaw;
import service.RideService;
import service.RideMatchingService;
import service.RouteService;
import dsa.RouteResult;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JSplitPane;
import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;

/** Passenger-facing route finder, request form, and rickshaw availability view. */
public class PassengerDashboard extends JFrame {
    private static final Color NAVY = DashboardStyle.INK;
    private static final Color MUTED = DashboardStyle.MUTED;
    private static final Color ACCENT = DashboardStyle.ACCENT;

    private final RideService rideService;
    private final RouteService routeService;
    private final PassengerAccount passengerAccount;
    private final JTextField passengerNameField = new JTextField("Demo Passenger", 18);
    private final JComboBox<Location> pickupBox;
    private final JComboBox<Location> destinationBox;
    private final JButton requestRideButton = createButton("Request Shared Ride", true);
    private final JButton cancelRideButton = createButton("Cancel Booking", false);
    private final JSpinner seatsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
    private final JTextArea routeArea = new JTextArea(5, 40);
    private final JTextArea rideStatusArea = new JTextArea(4, 40);
    private final JLabel requestStatus = new JLabel("Request status: Not submitted");
    private final JLabel bookingDetails = new JLabel("Driver and booking details will appear here.");
    private final JLabel selectedTripSeats = new JLabel("Select a rickshaw to view its seat availability.");
    private final JLabel selectedRouteEstimate = new JLabel();
    private final DefaultListModel<RideMatchingService.TripMatch> rickshawModel = new DefaultListModel<>();
    private final JList<RideMatchingService.TripMatch> rickshawList = new JList<>(rickshawModel);
    private final JLabel availableCount = new JLabel();
    private final MessageLabel message = new MessageLabel();
    private final Timer rideUpdateTimer;
    private final Runnable logoutAction;
    private final Runnable additionalSessionAction;
    private final CampusMapPanel campusMapPanel;
    private final GoogleMapsPanel googleMapsPanel;
    private final JTabbedPane mapTabs = new JTabbedPane();
    private Integer trackedRideId;
    private int displayedUpdateCount;
    private boolean requestInProgress;

    public PassengerDashboard(RideService rideService) {
        this(rideService, null, null, null);
    }

    public PassengerDashboard(RideService rideService, PassengerAccount passenger, Runnable logoutAction) {
        this(rideService, passenger, logoutAction, null);
    }

    public PassengerDashboard(RideService rideService, PassengerAccount passenger, Runnable logoutAction,
                              Runnable additionalSessionAction) {
        super("Campus Rider — Passenger");
        this.rideService = rideService;
        this.routeService = rideService.getRouteService();
        this.passengerAccount = passenger;
        this.logoutAction = logoutAction;
        this.additionalSessionAction = additionalSessionAction;
        this.campusMapPanel = new CampusMapPanel(routeService);
        this.googleMapsPanel = new GoogleMapsPanel(routeService);
        mapTabs.addTab("Google Maps", googleMapsPanel);
        mapTabs.addTab("Campus Graph (offline)", campusMapPanel);
        mapTabs.setSelectedIndex(GoogleMapsPanel.isConfigured() ? 0 : 1);
        if (passenger != null) {
            passengerNameField.setText(passenger.getFullName());
            passengerNameField.setEditable(false);
        }
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1080, 740));

        List<Location> locations = routeService.getLocationsSorted();
        Location[] choices = locations.toArray(new Location[0]);
        pickupBox = new JComboBox<>(choices);
        destinationBox = new JComboBox<>(choices);
        DashboardStyle.styleInput(passengerNameField);
        DashboardStyle.styleInput(pickupBox);
        DashboardStyle.styleInput(destinationBox);
        DashboardStyle.styleInput(seatsSpinner);
        DashboardStyle.styleList(rickshawList);
        if (choices.length > 1) {
            destinationBox.setSelectedIndex(1);
        }
        pickupBox.addActionListener(event -> clearTripOptions());
        destinationBox.addActionListener(event -> clearTripOptions());
        seatsSpinner.addChangeListener(event -> clearTripOptions());

        JPanel content = new JPanel(new BorderLayout(16, 16));
        content.setBackground(DashboardStyle.PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(22, 26, 18, 26));
        content.add(buildHeader(), BorderLayout.NORTH);
        content.add(buildMainContent(), BorderLayout.CENTER);
        content.add(message, BorderLayout.SOUTH);
        setContentPane(content);

        message.showSuccess("Choose your stops, select a rickshaw, then send your ride request.");
        refreshAvailableRickshaws(false);
        if (passenger != null) {
            model.RideRequest latestRide = rideService.getLatestRideForPassenger(passenger.getMobileNumber());
            if (latestRide != null) {
                trackedRideId = latestRide.getId();
                updateRequestStatus(latestRide);
            }
        }
        updateRequestButtonState();
        rideUpdateTimer = new Timer(250, event -> refreshTrackedRide());
        rideUpdateTimer.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                rideUpdateTimer.stop();
            }
        });
        pack();
        setSize(Math.max(getWidth(), 1240), Math.max(getHeight(), 820));
        setLocationRelativeTo(null);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setOpaque(false);
        String passengerName = passengerAccount == null ? "Passenger" : passengerAccount.getFullName();
        JLabel title = new JLabel("Welcome, " + passengerName);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
        title.setForeground(NAVY);
        JLabel subtitle = new JLabel("Plan a shared ride across Jahangirnagar University.");
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        subtitle.setForeground(MUTED);
        JPanel text = new JPanel(new GridBagLayout());
        text.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        text.add(title, c);
        c.gridy = 1;
        text.add(subtitle, c);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        actions.setOpaque(false);
        if (additionalSessionAction != null) {
            JButton anotherSession = new JButton("Open Another Session");
            DashboardStyle.styleButton(anotherSession, DashboardStyle.ButtonType.SECONDARY);
            anotherSession.setToolTipText("Open another account while keeping this dashboard live.");
            anotherSession.addActionListener(event -> additionalSessionAction.run());
            actions.add(anotherSession);
        }
        if (logoutAction != null) {
            JButton logout = new JButton("Log Out");
            DashboardStyle.styleButton(logout, DashboardStyle.ButtonType.SECONDARY);
            logout.addActionListener(event -> {
                rideUpdateTimer.stop();
                dispose();
                logoutAction.run();
            });
            actions.add(logout);
        }
        header.add(text, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel buildMainContent() {
        JPanel main = new JPanel(new BorderLayout(12, 12));
        main.setOpaque(false);
        JPanel mapCard = createCard("Campus map");
        mapCard.setLayout(new BorderLayout(8, 8));
        JLabel mapNote = new JLabel("Schematic campus route simulation · vehicle positions are not GPS");
        mapNote.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        mapNote.setForeground(MUTED);
        mapCard.add(mapNote, BorderLayout.NORTH);
        mapTabs.setMinimumSize(new Dimension(500, 360));
        mapTabs.setPreferredSize(new Dimension(700, 500));
        mapCard.add(mapTabs, BorderLayout.CENTER);

        JPanel bookingPanel = new JPanel();
        bookingPanel.setOpaque(false);
        bookingPanel.setLayout(new javax.swing.BoxLayout(bookingPanel, javax.swing.BoxLayout.Y_AXIS));
        bookingPanel.add(buildTripCard());
        bookingPanel.add(javax.swing.Box.createVerticalStrut(12));
        bookingPanel.add(buildAvailabilityCard());
        JScrollPane bookingScroll = new JScrollPane(bookingPanel);
        bookingScroll.setBorder(null);
        bookingScroll.getViewport().setOpaque(false);
        bookingScroll.setOpaque(false);
        bookingScroll.getVerticalScrollBar().setUnitIncrement(18);
        bookingScroll.setPreferredSize(new Dimension(400, 500));

        JSplitPane workspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapCard, bookingScroll);
        workspace.setResizeWeight(0.65);
        workspace.setContinuousLayout(true);
        workspace.setOneTouchExpandable(true);
        workspace.setDividerSize(10);
        workspace.setBorder(null);
        main.add(workspace, BorderLayout.CENTER);
        main.add(buildStatusCard(), BorderLayout.SOUTH);
        return main;
    }

    private JPanel buildTripCard() {
        JPanel card = createCard("Plan your ride");
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 8, 6, 8);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        addFormRow(card, c, 0, "Passenger name", passengerNameField);
        addFormRow(card, c, 1, "Pickup", pickupBox);
        addFormRow(card, c, 2, "Destination", destinationBox);
        addFormRow(card, c, 3, "Seats", seatsSpinner);

        JButton findRoute = createButton("Find Shortest Route", false);
        findRoute.addActionListener(event -> findRoute());
        requestRideButton.addActionListener(event -> requestRide());
        requestRideButton.setEnabled(false);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actions.setOpaque(false);
        actions.add(findRoute);
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        card.add(actions, c);

        routeArea.setEditable(false);
        routeArea.setLineWrap(true);
        routeArea.setWrapStyleWord(true);
        DashboardStyle.styleTextArea(routeArea);
        routeArea.setText("Route details will appear here after you select your stops.");
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 2;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        JScrollPane routeScroll = new JScrollPane(routeArea);
        DashboardStyle.styleScrollPane(routeScroll);
        card.add(routeScroll, c);
        return card;
    }

    private JPanel buildStatusCard() {
        JPanel card = createCard("Ride request updates");
        card.setLayout(new BorderLayout(8, 8));
        rideStatusArea.setEditable(false);
        rideStatusArea.setLineWrap(true);
        rideStatusArea.setWrapStyleWord(true);
        DashboardStyle.styleTextArea(rideStatusArea);
        rideStatusArea.setText("Submit a ride request to see driver search and response updates.");
        requestStatus.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        requestStatus.setForeground(NAVY);
        DashboardStyle.styleBadge(requestStatus, DashboardStyle.SUBTLE, NAVY);
        bookingDetails.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        bookingDetails.setForeground(MUTED);
        JPanel updates = new JPanel(new BorderLayout(0, 5));
        updates.setOpaque(false);
        JPanel statusHeader = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        statusHeader.setOpaque(false);
        statusHeader.add(requestStatus);
        statusHeader.add(bookingDetails);
        updates.add(statusHeader, BorderLayout.NORTH);
        JScrollPane updatesScroll = new JScrollPane(rideStatusArea);
        DashboardStyle.styleScrollPane(updatesScroll);
        updatesScroll.setPreferredSize(new Dimension(500, 64));
        updatesScroll.setMinimumSize(new Dimension(250, 54));
        updates.add(updatesScroll, BorderLayout.CENTER);
        card.add(updates, BorderLayout.NORTH);
        cancelRideButton.setEnabled(false);
        DashboardStyle.styleButton(cancelRideButton, DashboardStyle.ButtonType.DANGER);
        cancelRideButton.addActionListener(event -> cancelTrackedRide());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancelRideButton);
        card.add(actions, BorderLayout.SOUTH);
        mapTabs.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        mapTabs.setForeground(NAVY);
        card.setPreferredSize(new Dimension(560, 145));
        return card;
    }

    private void addFormRow(JPanel panel, GridBagConstraints c, int row, String label, java.awt.Component field) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(fieldLabel, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, c);
    }

    private JPanel buildAvailabilityCard() {
        JPanel card = createCard("Available rickshaws");
        card.setLayout(new BorderLayout(8, 8));

        JPanel summary = new JPanel(new BorderLayout());
        summary.setOpaque(false);
        availableCount.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        availableCount.setForeground(NAVY);
        summary.add(availableCount, BorderLayout.WEST);
        selectedRouteEstimate.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        selectedRouteEstimate.setForeground(ACCENT);
        summary.add(selectedRouteEstimate, BorderLayout.CENTER);
        selectedTripSeats.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        selectedTripSeats.setForeground(MUTED);
        JPanel summaryDetails = new JPanel(new BorderLayout(0, 3));
        summaryDetails.setOpaque(false);
        summaryDetails.add(selectedRouteEstimate, BorderLayout.NORTH);
        summaryDetails.add(selectedTripSeats, BorderLayout.SOUTH);
        summary.add(summaryDetails, BorderLayout.SOUTH);
        rickshawList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        rickshawList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        rickshawList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, selected, focus);
                if (value instanceof RideMatchingService.TripMatch match) {
                    Rickshaw rickshaw = match.getRickshaw();
                    RideService.SeatAvailability seats = rideService.getSeatAvailability(rickshaw.getId());
                    label.setText("<html><b>Shared rickshaw · "
                            + escapeHtml(rickshaw.getDriverName()) + "</b><br>"
                            + escapeHtml(routeService.getLocationName(rickshaw.getLocationId()))
                            + " → " + escapeHtml(routeService.getLocationName(rickshaw.getDestinationId()))
                            + "<br>Rickshaw " + escapeHtml(rickshaw.getId()) + " · "
                            + seats.availableSeats() + " seats available · "
                            + seats.totalSeats() + " total"
                            + " · Pickup " + String.format(Locale.ROOT, "%.0f m",
                            match.getDistanceToPickupMeters()) + " · Detour "
                            + String.format(Locale.ROOT, "%.0f m", match.getExtraDistanceMeters())
                            + "<br><b><font color='#1f7759'>" + estimateFareForDisplay()
                            + "</font></b></html>");
                }
                label.setOpaque(true);
                label.setBackground(selected ? DashboardStyle.ACCENT_SOFT : Color.WHITE);
                label.setForeground(NAVY);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(selected ? DashboardStyle.ACCENT : DashboardStyle.BORDER),
                        BorderFactory.createEmptyBorder(10, 11, 10, 11)));
                return label;
            }
        });
        rickshawList.setFixedCellHeight(90);
        rickshawList.addListSelectionListener(event -> {
            RideMatchingService.TripMatch selected = rickshawList.getSelectedValue();
            updateRequestButtonState();
            if (selected != null) {
                Rickshaw rickshaw = selected.getRickshaw();
                Location pickup = (Location) pickupBox.getSelectedItem();
                Location destination = (Location) destinationBox.getSelectedItem();
                updateMapViews(rideService.getTripRoute(rickshaw.getId()), rickshaw.getLocationId(),
                        pickup == null ? "" : pickup.getId(), destination == null ? "" : destination.getId(),
                        "Available trip", rickshaw.getId());
                updateSelectedTripSeatSummary(rickshaw.getId());
            }
        });
        JScrollPane list = new JScrollPane(rickshawList);
        DashboardStyle.styleScrollPane(list);
        list.setPreferredSize(new Dimension(380, 240));

        JLabel seatsNote = new JLabel("<html>Only online active trips with a compatible route and enough seats appear. "
                + "Estimated fare is a demonstration only, based on the campus graph at ৳10/km; no payment is collected.</html>");
        seatsNote.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        seatsNote.setForeground(MUTED);

        JButton searchTrips = createButton("Search Available Trips", false);
        searchTrips.addActionListener(event -> refreshAvailableRickshaws(true));
        JPanel actions = new JPanel(new java.awt.GridLayout(0, 1, 0, 5));
        actions.setOpaque(false);
        actions.add(searchTrips);
        actions.add(requestRideButton);
        JPanel footer = new JPanel(new BorderLayout(0, 6));
        footer.setOpaque(false);
        footer.add(seatsNote, BorderLayout.NORTH);
        footer.add(actions, BorderLayout.CENTER);

        JPanel selectionHeader = new JPanel(new BorderLayout(0, 8));
        selectionHeader.setOpaque(false);
        JLabel rideType = new JLabel("Shared Rickshaw");
        rideType.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        rideType.setForeground(NAVY);
        selectionHeader.add(summary, BorderLayout.NORTH);
        selectionHeader.add(rideType, BorderLayout.SOUTH);
        card.add(selectionHeader, BorderLayout.NORTH);
        card.add(list, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createCard(String title) {
        JPanel card = new JPanel();
        card.setBackground(DashboardStyle.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                DashboardStyle.cardBorder(title),
                BorderFactory.createEmptyBorder(10, 12, 12, 12)));
        return card;
    }

    private JButton createButton(String text, boolean primary) {
        JButton button = new JButton(text);
        DashboardStyle.styleButton(button, primary
                ? DashboardStyle.ButtonType.PRIMARY : DashboardStyle.ButtonType.SECONDARY);
        return button;
    }

    private void findRoute() {
        Location pickup = (Location) pickupBox.getSelectedItem();
        Location destination = (Location) destinationBox.getSelectedItem();
        if (pickup == null || destination == null) {
            message.showError("Choose both a pickup and a destination.");
            return;
        }
        RouteResult route = routeService.findRoute(pickup.getId(), destination.getId());
        routeArea.setText(routeService.describeRoute(route));
        routeArea.setCaretPosition(0);
        if (route.isFound()) {
            message.showSuccess(String.format(Locale.ROOT, "Route found: %.1f m.",
                    route.getTotalDistanceMeters()));
        } else {
            message.showError(route.getMessage());
        }
    }

    private void requestRide() {
        Location pickup = (Location) pickupBox.getSelectedItem();
        Location destination = (Location) destinationBox.getSelectedItem();
        String passengerName = passengerNameField.getText();
        RideMatchingService.TripMatch selected = rickshawList.getSelectedValue();
        int requestedSeats = (Integer) seatsSpinner.getValue();
        if (selected == null) {
            message.showError("Search and select a suitable active rickshaw first.");
            return;
        }
        String selectedRickshawId = selected.getRickshaw().getId();
        requestInProgress = true;
        updateRequestButtonState();
        message.setForeground(MUTED);
        message.setText("Submitting request and searching for a driver...");
        requestStatus.setText("Request status: Submitting...");

        new SwingWorker<model.RideRequest, Void>() {
            @Override
            protected model.RideRequest doInBackground() {
                return rideService.bookSharedRide(passengerName,
                        passengerAccount == null ? "" : passengerAccount.getMobileNumber(),
                        pickup == null ? null : pickup.getId(), destination == null ? null : destination.getId(),
                        requestedSeats, selectedRickshawId);
            }

            @Override
            protected void done() {
                try {
                    model.RideRequest ride = get();
                    trackedRideId = ride.getId();
                    displayedUpdateCount = 0;
                    rideStatusArea.setText("");
                    rickshawModel.clear();
                    requestRideButton.setEnabled(false);
                    refreshTrackedRide();
                    refreshAvailableRickshaws(false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    requestStatus.setText("Request status: Submission interrupted");
                    message.showError("Ride request was interrupted before a result was received.");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    String error = cause == null ? null : cause.getMessage();
                    requestStatus.setText("Request status: Failed");
                    message.showError(error == null || error.isBlank()
                            ? "Ride request failed (" + (cause == null ? "unknown error"
                            : cause.getClass().getSimpleName()) + ")."
                            : "Ride request failed: " + error);
                } finally {
                    requestInProgress = false;
                    updateRequestButtonState();
                }
            }
        }.execute();
    }

    private void refreshTrackedRide() {
        if (trackedRideId == null) {
            refreshAvailableRickshaws(false);
            return;
        }
        campusMapPanel.setAvailableVehicleLocationIds(List.of());
        List<String> updates = rideService.getRideUpdates(trackedRideId);
        while (displayedUpdateCount < updates.size()) {
            rideStatusArea.append(updates.get(displayedUpdateCount++) + "\n");
        }
        rideStatusArea.setCaretPosition(rideStatusArea.getDocument().getLength());

        model.RideRequest ride = rideService.getRide(trackedRideId);
        if (ride == null) {
            requestStatus.setText("Request status: Unavailable");
            bookingDetails.setText("Booking details are unavailable.");
            message.showError("The tracked ride request is no longer available.");
            updateRequestButtonState();
            return;
        }
        updateRequestStatus(ride);
        Rickshaw assigned = ride.getRickshawId().isBlank()
                ? null : rideService.getRickshaw(ride.getRickshawId());
        if (assigned == null) {
            bookingDetails.setText("Driver and rickshaw will appear when assigned.");
        } else {
            RideService.SeatAvailability seats = rideService.getSeatAvailability(assigned.getId());
            bookingDetails.setText("Driver: " + assigned.getDriverName() + " · Rickshaw: "
                    + assigned.getId() + " · Your seats: " + ride.getRequestedSeats()
                    + " · Capacity " + seats.totalSeats() + " · Booked " + seats.bookedSeats()
                    + " · Onboard " + seats.occupiedSeats() + " · Available " + seats.availableSeats()
                    + " · Passenger bookings: " + seats.passengerBookings());
        }
        if (ride.getStatus() == model.RideStatus.NO_DRIVER_AVAILABLE) {
            message.showError("No Rickshaw Available for this route.");
        } else if (ride.getStatus() == model.RideStatus.MATCHED) {
            message.showSuccess("Accepted. Driver Approaching your pickup.");
        } else if (ride.getStatus() == model.RideStatus.ARRIVED_AT_PICKUP) {
            message.showSuccess("Rickshaw arrived. The driver will confirm pickup.");
        } else if (ride.getStatus() == model.RideStatus.OFFERED) {
            if (!ride.getRejectedRickshawIds().isEmpty()) {
                message.showSuccess(assigned == null
                        ? "Driver Rejected. Searching for Another Driver."
                        : "Driver Rejected. Searching for Another Driver; waiting for "
                        + assigned.getDriverName() + " (" + assigned.getId() + ").");
            } else {
                message.showSuccess(assigned == null
                        ? "Request Sent. Waiting for Driver."
                        : "Request Sent to " + assigned.getDriverName() + " (" + assigned.getId()
                        + "). Waiting for Driver.");
            }
        } else if (ride.getStatus() == model.RideStatus.IN_PROGRESS) {
            message.showSuccess("Passenger Onboard. Your trip is in progress.");
        } else if (ride.getStatus() == model.RideStatus.ARRIVED_AT_DESTINATION) {
            message.showSuccess("At destination. Waiting for the driver to complete the trip.");
        } else if (ride.getStatus() == model.RideStatus.COMPLETED) {
            message.showSuccess("Trip Completed.");
        } else if (ride.getStatus() == model.RideStatus.CANCELLED) {
            message.showError("The ride was cancelled.");
        }
        if (assigned != null) {
            updateSelectedTripSeatSummary(assigned.getId());
            updateMapViews(rideService.getTripRoute(assigned.getId()), assigned.getLocationId(),
                    ride.getPickupId(), ride.getDestinationId(),
                    mapStatus(ride.getStatus()), assigned.getId());
        } else {
            updateMapViews(List.of(), "", ride.getPickupId(), ride.getDestinationId(),
                    "Waiting for driver assignment", "");
        }
        cancelRideButton.setEnabled(ride.getStatus() == model.RideStatus.OFFERED
                || ride.getStatus() == model.RideStatus.MATCHED
                || ride.getStatus() == model.RideStatus.ARRIVED_AT_PICKUP);
        updateRequestButtonState();
    }

    private void refreshAvailableRickshaws(boolean userInitiated) {
        requestRideButton.setEnabled(false);
        Location pickup = (Location) pickupBox.getSelectedItem();
        Location destination = (Location) destinationBox.getSelectedItem();
        updateSelectedRouteEstimate(pickup, destination);
        if (pickup == null || destination == null || pickup.getId().equals(destination.getId())) {
            rickshawModel.clear();
            availableCount.setText("Choose different pickup and destination stops.");
            if (userInitiated && trackedRideId == null) {
                message.showError("Choose different pickup and destination stops.");
            }
            return;
        }
        List<RideMatchingService.TripMatch> matches = rideService.findAvailableTrips(
                pickup.getId(), destination.getId(), (Integer) seatsSpinner.getValue());
        String selectedRickshawId = rickshawList.getSelectedValue() == null
                ? null : rickshawList.getSelectedValue().getRickshaw().getId();
        boolean sameOrder = matches.size() == rickshawModel.size();
        if (sameOrder) {
            for (int i = 0; i < matches.size(); i++) {
                if (!matches.get(i).getRickshaw().getId()
                        .equals(rickshawModel.get(i).getRickshaw().getId())) {
                    sameOrder = false;
                    break;
                }
            }
        }
        if (sameOrder) {
            for (int i = 0; i < matches.size(); i++) {
                rickshawModel.set(i, matches.get(i));
            }
            rickshawList.repaint();
        } else {
            rickshawModel.clear();
            int restoredSelection = -1;
            for (int i = 0; i < matches.size(); i++) {
                RideMatchingService.TripMatch match = matches.get(i);
                rickshawModel.addElement(match);
                if (match.getRickshaw().getId().equals(selectedRickshawId)) {
                    restoredSelection = i;
                }
            }
            if (restoredSelection >= 0) {
                rickshawList.setSelectedIndex(restoredSelection);
            }
        }
        availableCount.setText(matches.size() + " compatible active trip(s) found");
        campusMapPanel.setAvailableVehicleLocationIds(matches.stream()
                .map(match -> match.getRickshaw().getLocationId()).toList());
        if (matches.isEmpty()) {
            selectedTripSeats.setText("No compatible active trip selected.");
            if (userInitiated && trackedRideId == null) {
                message.showError("No online active trip has a compatible route and enough available seats.");
            }
            updateMapViews(List.of(), "", pickup.getId(), destination.getId(),
                    "No compatible active trip", "");
        } else {
            if (userInitiated && trackedRideId == null) {
                message.showSuccess("Select a rickshaw, then click Send Ride Request.");
            }
            if (rickshawList.getSelectedIndex() < 0) {
                rickshawList.setSelectedIndex(0);
            }
            RideMatchingService.TripMatch selected = rickshawList.getSelectedValue();
            if (selected != null) {
                Rickshaw rickshaw = selected.getRickshaw();
                updateMapViews(rideService.getTripRoute(rickshaw.getId()), rickshaw.getLocationId(),
                        pickup.getId(), destination.getId(), "Available trip", rickshaw.getId());
                updateSelectedTripSeatSummary(rickshaw.getId());
            }
        }
        updateRequestButtonState();
    }

    private void updateSelectedTripSeatSummary(String rickshawId) {
        RideService.SeatAvailability seats = rideService.getSeatAvailability(rickshawId);
        selectedTripSeats.setText("Capacity " + seats.totalSeats() + " · Booked "
                + seats.bookedSeats() + " · Onboard " + seats.occupiedSeats() + " · Available "
                + seats.availableSeats() + " · " + seats.passengerBookings() + " passenger booking(s)");
    }

    private void clearTripOptions() {
        rickshawModel.clear();
        campusMapPanel.setAvailableVehicleLocationIds(List.of());
        requestRideButton.setEnabled(false);
        if (trackedRideId == null) {
            message.showSuccess("Choose your stops and search for available rickshaws.");
        }
        Location pickup = (Location) pickupBox.getSelectedItem();
        Location destination = (Location) destinationBox.getSelectedItem();
        updateSelectedRouteEstimate(pickup, destination);
        updateMapViews(List.of(), "", pickup == null ? "" : pickup.getId(),
                destination == null ? "" : destination.getId(),
                "Choose a trip to view its route", "");
    }

    private void updateSelectedRouteEstimate(Location pickup, Location destination) {
        if (pickup == null || destination == null || pickup.getId().equals(destination.getId())) {
            selectedRouteEstimate.setText("Estimated fare unavailable");
            return;
        }
        RouteResult route = routeService.findRoute(pickup.getId(), destination.getId());
        OptionalLong fare = service.EstimatedFareCalculator.estimateFare(route);
        if (!fare.isPresent()) {
            selectedRouteEstimate.setText("Estimated fare unavailable");
            return;
        }
        double distanceKilometers = route.getTotalDistanceMeters() / 1000;
        selectedRouteEstimate.setText(String.format(Locale.ROOT,
                "Route %.2f km · Estimated fare ৳%d", distanceKilometers, fare.getAsLong()));
    }

    private String estimateFareForDisplay() {
        Location pickup = (Location) pickupBox.getSelectedItem();
        Location destination = (Location) destinationBox.getSelectedItem();
        if (pickup == null || destination == null || pickup.getId().equals(destination.getId())) {
            return "Fare unavailable";
        }
        OptionalLong fare = service.EstimatedFareCalculator.estimateFare(
                routeService.findRoute(pickup.getId(), destination.getId()));
        return fare.isPresent() ? "Estimated fare ৳" + fare.getAsLong() : "Fare unavailable";
    }

    private void updateMapViews(List<String> route, String currentLocationId,
                                String pickupId, String destinationId, String status, String rickshawId) {
        campusMapPanel.setTripView(route, currentLocationId, pickupId, destinationId, status,
                rickshawId.isBlank() ? null : rideService.getTripMovement(rickshawId));
        googleMapsPanel.setTripView(route, currentLocationId, pickupId, destinationId);
    }

    private void cancelTrackedRide() {
        if (trackedRideId == null || !cancelRideButton.isEnabled()) {
            return;
        }
        int rideId = trackedRideId;
        cancelRideButton.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return rideService.cancelRide(rideId);
            }

            @Override
            protected void done() {
                try {
                    message.showSuccess(get());
                    refreshTrackedRide();
                    refreshAvailableRickshaws(false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    message.showError("Ride cancellation was interrupted.");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    String detail = cause == null ? null : cause.getMessage();
                    message.showError(detail == null || detail.isBlank()
                            ? "Could not cancel this booking." : detail);
                }
            }
        }.execute();
    }

    private void updateRequestButtonState() {
        model.RideRequest trackedRide = trackedRideId == null ? null : rideService.getRide(trackedRideId);
        boolean hasActiveRequest = trackedRide != null && trackedRide.getStatus().isActive();
        requestRideButton.setEnabled(canSubmitRide(requestInProgress, hasActiveRequest,
                rickshawList.getSelectedValue() != null));
    }

    private void updateRequestStatus(model.RideRequest ride) {
        requestStatus.setText("Request status: " + requestStatusText(ride));
        Color background = DashboardStyle.SUBTLE;
        Color foreground = NAVY;
        switch (ride.getStatus()) {
            case MATCHED, ARRIVED_AT_PICKUP, IN_PROGRESS -> {
                background = DashboardStyle.ACCENT_SOFT;
                foreground = ACCENT;
            }
            case NO_DRIVER_AVAILABLE, CANCELLED -> {
                background = DashboardStyle.DANGER_SOFT;
                foreground = DashboardStyle.DANGER;
            }
            default -> {
            }
        }
        DashboardStyle.styleBadge(requestStatus, background, foreground);
    }

    static boolean canSubmitRide(boolean requestInProgress, boolean hasActiveRequest, boolean hasSelection) {
        return !requestInProgress && !hasActiveRequest && hasSelection;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String mapStatus(model.RideStatus status) {
        return switch (status) {
            case MATCHED -> "Rickshaw approaching";
            case ARRIVED_AT_PICKUP -> "Rickshaw at pickup · Driver confirming";
            case IN_PROGRESS -> "Passenger onboard · Trip in progress";
            case ARRIVED_AT_DESTINATION -> "At destination · Awaiting driver";
            case COMPLETED -> "Trip completed";
            case CANCELLED -> "Booking cancelled";
            case NO_DRIVER_AVAILABLE -> "No driver available";
            default -> "Waiting for driver";
        };
    }

    static String requestStatusText(model.RideRequest ride) {
        String status = switch (ride.getStatus()) {
            case PENDING -> "Pending — searching for a driver";
            case OFFERED -> ride.getRejectedRickshawIds().isEmpty()
                    ? "Pending — waiting for driver response"
                    : "Rejected — searching for another driver";
            case MATCHED -> "Request Accepted — rickshaw approaching";
            case ARRIVED_AT_PICKUP -> "Request Accepted — driver at pickup";
            case IN_PROGRESS -> "Passenger Onboard — Trip in Progress";
            case ARRIVED_AT_DESTINATION -> "At destination — awaiting driver confirmation";
            case NO_DRIVER_AVAILABLE -> "No Driver Available";
            case COMPLETED -> "Trip Completed";
            case CANCELLED -> "Cancelled";
        };
        return status;
    }
}
