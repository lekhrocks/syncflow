package com.syncflow.api.user;

import com.syncflow.api.user.entity.UserEntity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression: the auth user table is named {@code app_users}, not
 * {@code users},
 * so it does not collide with sample/integration tables that use {@code users}
 * (e.g. PgMongoSampleE2eTest). A collision caused "column full_name of relation
 * users does not exist" in that test.
 */
class UserEntityMappingTest {

    @Test
    void entityMapsToAppUsersTable() {
        var table = UserEntity.class.getAnnotation(Table.class);
        assertEquals("app_users", table.name(),
                "auth users table must be app_users to avoid colliding with sample 'users' tables");
    }
}
