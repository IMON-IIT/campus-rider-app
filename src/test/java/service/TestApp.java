package service;

import storage.CsvStorage;

import java.io.IOException;
import java.nio.file.Path;

/** Starts the services the same way Main does, using a test data folder. */
public class TestApp {

    public static RideService start(Path dataDir) throws IOException {
        CsvStorage storage = new CsvStorage(dataDir);
        RouteService routeService = new RouteService(storage.loadLocations(), storage.loadRoads(), storage.getWarnings());
        return new RideService(storage, routeService);
    }
}
