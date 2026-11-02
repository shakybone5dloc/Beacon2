package dev.beacon.contracts.fit;

/** Whether fit checks are available, so the UI can explain how to switch them on. */
public record FitStatusResponse(boolean configured) {
}
