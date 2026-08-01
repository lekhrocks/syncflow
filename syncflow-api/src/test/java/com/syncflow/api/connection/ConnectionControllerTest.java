package com.syncflow.api.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncflow.api.connection.dto.CreateConnectionRequest;
import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.controller.ConnectionController;
import com.syncflow.core.connection.Connection;
import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.connection.Credentials;
import com.syncflow.core.connection.spi.ConnectorFactory;
import com.syncflow.api.config.versioning.VersionContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConnectionController.class)
@Import({ConnectionControllerTest.TestSecurityConfig.class, VersionContext.class})
class ConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConnectionService connectionService;

    @MockitoBean
    private ConnectorFactory connectorFactory;

    @Test
    void createConnection_returns201() throws Exception {
        var request = new CreateConnectionRequest("test-conn", ConnectionType.POSTGRESQL,
                "localhost", 5432, "mydb", "user", "pass", Map.of());

        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, "localhost", 5432, "mydb", Map.of());
        var creds = new Credentials("user", "pass");
        var connection = new Connection("test-conn", props, creds);

        when(connectionService.create(any(), any(), any())).thenReturn(connection);

        mockMvc.perform(post("/api/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("test-conn"));
    }

    @Test
    void listConnections_returns200() throws Exception {
        when(connectionService.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/connections"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteConnection_returns204() throws Exception {
        mockMvc.perform(delete("/api/connections/some-id"))
                .andExpect(status().isNoContent());
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
