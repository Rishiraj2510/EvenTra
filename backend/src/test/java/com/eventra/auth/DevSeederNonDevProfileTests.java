package com.eventra.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that DevAccountSeeder bean does NOT exist and no seed rows are created
 * when the "dev" profile is inactive (default profile).
 */
@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!"
})
class DevSeederNonDevProfileTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private UserRepository userRepository;

    @Test
    void withoutDevProfileSeederBeanDoesNotExistAndCreatesNothing() {
        // Verify bean does not exist in application context
        assertThrows(NoSuchBeanDefinitionException.class, () ->
                applicationContext.getBean(DevAccountSeeder.class),
                "DevAccountSeeder bean must not exist outside dev profile");

        // Verify no seed accounts were created
        assertTrue(userRepository.findByEmailIgnoreCase("organiser@eventra.local").isEmpty());
        assertTrue(userRepository.findByEmailIgnoreCase("volunteer@eventra.local").isEmpty());
    }
}

