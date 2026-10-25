package dev.beacon;

import dev.beacon.application.users.AccountService;
import dev.beacon.application.users.SignedInUser;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base for API tests: registers a fresh user before each test and sends every
 * request as that user, with a CSRF token. {@link #as} gives a second user.
 */
public abstract class SignedInApiTests {

    @Autowired WebApplicationContext context;
    @Autowired AccountService accounts;

    protected SignedInUser signedIn;
    protected MockMvcTester mvc;

    @BeforeEach
    void signIn() {
        signedIn = register();
        mvc = as(signedIn);
    }

    protected SignedInUser register() {
        return accounts.register("user-" + UUID.randomUUID() + "@example.com", "correct horse battery", null);
    }

    protected MockMvcTester as(SignedInUser user) {
        return MockMvcTester.create(MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user(user)).with(csrf()))
                .build());
    }
}
