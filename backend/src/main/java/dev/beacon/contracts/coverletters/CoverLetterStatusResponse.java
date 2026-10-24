package dev.beacon.contracts.coverletters;

/** Whether drafting is available, so the UI can explain how to switch it on. */
public record CoverLetterStatusResponse(boolean configured) {
}
