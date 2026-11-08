package dev.beacon.api.home;

import dev.beacon.application.home.HomeService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.home.HomeTodosResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The parts of Home no other endpoint gives. */
@Tag(name = "Home")
@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService home;

    public HomeController(HomeService home) {
        this.home = home;
    }

    @Operation(operationId = "getHomeTodos")
    @GetMapping("/todos")
    public HomeTodosResponse todos(@AuthenticationPrincipal SignedInUser user) {
        return home.todos(user.id());
    }
}
