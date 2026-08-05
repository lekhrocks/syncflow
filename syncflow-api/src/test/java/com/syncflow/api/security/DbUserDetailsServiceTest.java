package com.syncflow.api.security;

import com.syncflow.api.user.entity.UserEntity;
import com.syncflow.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DbUserDetailsServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final DbUserDetailsService service = new DbUserDetailsService(repository);

    private UserEntity user(String username, String roles, boolean enabled) {
        var u = new UserEntity();
        u.setUsername(username);
        u.setPasswordHash("$2a$10$kcqbSa6/YwMoZge2NPc5b.ASDIr7vXvAjZ6Amvfdl.A6z.azwH1Au");
        u.setRoles(roles);
        u.setEnabled(enabled);
        return u;
    }

    @Test
    void mapsCsvRolesToAuthorities() {
        when(repository.findByUsername("alice")).thenReturn(Optional.of(user("alice", "ADMIN,USER", true)));
        var details = service.loadUserByUsername("alice");
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertEquals(2, details.getAuthorities().size());
    }

    @Test
    void blankRolesYieldUserAuthority() {
        when(repository.findByUsername("bob")).thenReturn(Optional.of(user("bob", " ", true)));
        var details = service.loadUserByUsername("bob");
        assertTrue(details.getAuthorities().isEmpty());
    }

    @Test
    void disabledUserIsRejected() {
        when(repository.findByUsername("locked")).thenReturn(Optional.of(user("locked", "USER", false)));
        var details = service.loadUserByUsername("locked");
        assertFalse(details.isEnabled());
    }

    @Test
    void unknownUserThrows() {
        when(repository.findByUsername("nope")).thenReturn(Optional.empty());
        assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> service.loadUserByUsername("nope"));
    }
}
