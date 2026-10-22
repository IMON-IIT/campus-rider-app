package ui;

import service.RideService;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;

/** The application window with two tabs. */
public class MainWindow extends JFrame {

    public MainWindow(RideService rideService) {
        super("Smart Campus Ride-Sharing and Route Optimizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Route Finder", new RoutePanel(rideService.getRouteService()));
        tabs.addTab("Rides and Rickshaws", new RidePanel(rideService));
        add(tabs);

        setSize(1150, 780);
        setLocationRelativeTo(null);
    }
}
