package dev.beacon.contracts.home;

import java.util.List;

/**
 * Small to-dos for Home, newest first and a few of each.
 *
 * @param missingSentResume applications sent out with no record of which resume went with them
 * @param openSuggestions   applications not yet applied to that have resume suggestions still open
 */
public record HomeTodosResponse(List<HomeTodoResponse> missingSentResume, List<HomeTodoResponse> openSuggestions) {}
