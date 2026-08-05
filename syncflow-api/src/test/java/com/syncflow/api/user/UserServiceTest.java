package com.syncflow.api.user;

import com.syncflow.api.user.entity.UserEntity;
import com.syncflow.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository repository;
    private UserService service;
    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder();
        service = new UserService(repository, encoder);
    }

    @Test
    void createEncodesPasswordAndDefaultsRoles() {
        when(repository.existsByUsername("alice")).thenReturn(false);
        when(repository.save(org.mockito.ArgumentMatchers.any(UserEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var u = service.create("alice", "pw", "alice@x.com", null);

        assertEquals("alice", u.getUsername());
        assertNotEquals("pw", u.getPasswordHash(), "password must be hashed");
        assertNotEquals("pw", encoder.encode("pw"));
        assertEquals(RoleConstants.USER, u.getRoles(), "blank roles should default to USER");
        assertTrue(u.isEnabled());
    }

    @Test
    void createKeepsProvidedRoles() {
        when(repository.existsByUsername("bob")).thenReturn(false);
        when(repository.save(org.mockito.ArgumentMatchers.any(UserEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        var u = service.create("bob", "pw", null, "ADMIN,USER");
        assertEquals("ADMIN,USER", u.getRoles());
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(repository.existsByUsername("dup")).thenReturn(true);
        assertThrows(UserService.UserConflictException.class, () -> service.create("dup", "pw", null, null));
        verify(repository).existsByUsername(anyString());
    }
}
