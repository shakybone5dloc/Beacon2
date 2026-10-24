package dev.beacon.contracts.documents;

/**
 * @param descriptionSaved true when the page's text became the application's job
 *                         description (only happens when it had none)
 */
public record CaptureResponse(
        DocumentResponse document,
        boolean descriptionSaved
) {
}
