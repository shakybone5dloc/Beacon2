package dev.beacon.application.postings;

import dev.beacon.domain.companies.CareersPlatform;
import org.springframework.stereotype.Component;

import java.util.List;

/** Finds the client for a careers-board platform. One place, so tests can swap the clients out. */
@Component
public class CareersBoardClients {

    private final List<CareersBoardClient> clients;

    public CareersBoardClients(List<CareersBoardClient> clients) {
        this.clients = List.copyOf(clients);
    }

    public CareersBoardClient forPlatform(CareersPlatform platform) {
        return clients.stream()
                .filter(c -> c.platform() == platform)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No careers-board client for " + platform));
    }
}
