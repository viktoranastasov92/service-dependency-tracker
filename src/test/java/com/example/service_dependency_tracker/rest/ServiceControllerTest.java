package com.example.service_dependency_tracker.rest;

import com.example.service_dependency_tracker.domain.ServiceNode;
import com.example.service_dependency_tracker.exception.DuplicateServiceException;
import com.example.service_dependency_tracker.exception.ServiceNotFoundException;
import com.example.service_dependency_tracker.service.ServiceManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ServiceControllerTest {

    @Mock
    private ServiceManagementService serviceManagementService;

    @InjectMocks
    private ServiceController serviceController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(serviceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ---------------------------------------------------------------------------
    // POST /services
    // ---------------------------------------------------------------------------

    @Nested
    class RegisterService {

        @Test
        void shouldReturn201WithServiceDTOWhenServiceRegisteredSuccessfully() throws Exception {
            when(serviceManagementService.registerService("payment-service", "Handles payments"))
                    .thenReturn(serviceNode("payment-service", "Handles payments"));

            mockMvc.perform(post("/services")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"payment-service","description":"Handles payments"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("payment-service"))
                    .andExpect(jsonPath("$.description").value("Handles payments"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }

        @Test
        void shouldReturn201WhenDescriptionIsAbsent() throws Exception {
            when(serviceManagementService.registerService("payment-service", null))
                    .thenReturn(serviceNode("payment-service", null));

            mockMvc.perform(post("/services")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"payment-service"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("payment-service"));
        }

        @Test
        void shouldReturn400WhenNameIsAbsent() throws Exception {
            mockMvc.perform(post("/services")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description":"No name provided"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"));
        }

        @Test
        void shouldReturn400WhenNameContainsUppercaseLetters() throws Exception {
            mockMvc.perform(post("/services")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"PaymentService"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn400WhenNameContainsUnderscores() throws Exception {
            mockMvc.perform(post("/services")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"payment_service"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn409WhenServiceNameAlreadyRegistered() throws Exception {
            when(serviceManagementService.registerService(eq("payment-service"), any()))
                    .thenThrow(new DuplicateServiceException("payment-service"));

            mockMvc.perform(post("/services")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"payment-service"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(containsString("payment-service")));
        }
    }

    // ---------------------------------------------------------------------------
    // GET /services
    // ---------------------------------------------------------------------------

    @Nested
    class ListServices {

        @Test
        void shouldReturn200WithAllRegisteredServices() throws Exception {
            when(serviceManagementService.listServices()).thenReturn(List.of(
                    serviceNode("payment-service", null),
                    serviceNode("user-auth-service", null)));

            mockMvc.perform(get("/services"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].name",
                            containsInAnyOrder("payment-service", "user-auth-service")));
        }

        @Test
        void shouldReturn200WithEmptyArrayWhenNoServicesRegistered() throws Exception {
            when(serviceManagementService.listServices()).thenReturn(List.of());

            mockMvc.perform(get("/services"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // ---------------------------------------------------------------------------
    // GET /services/{name}
    // ---------------------------------------------------------------------------

    @Nested
    class GetService {

        @Test
        void shouldReturn200WithServiceDTOWhenFound() throws Exception {
            when(serviceManagementService.getService("payment-service"))
                    .thenReturn(serviceNode("payment-service", "Handles payments"));

            mockMvc.perform(get("/services/payment-service"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("payment-service"))
                    .andExpect(jsonPath("$.description").value("Handles payments"));
        }

        @Test
        void shouldReturn404WithApiErrorWhenServiceNotFound() throws Exception {
            when(serviceManagementService.getService("unknown-service"))
                    .thenThrow(new ServiceNotFoundException("unknown-service"));

            mockMvc.perform(get("/services/unknown-service"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value(containsString("unknown-service")))
                    .andExpect(jsonPath("$.path").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }
    }

    // ---------------------------------------------------------------------------
    // DELETE /services/{name}
    // ---------------------------------------------------------------------------

    @Nested
    class DeleteService {

        @Test
        void shouldReturn204WhenServiceDeletedSuccessfully() throws Exception {
            doNothing().when(serviceManagementService).deleteService("payment-service");

            mockMvc.perform(delete("/services/payment-service"))
                    .andExpect(status().isNoContent());
        }

        @Test
        void shouldReturn404WhenServiceToDeleteDoesNotExist() throws Exception {
            doThrow(new ServiceNotFoundException("unknown-service"))
                    .when(serviceManagementService).deleteService("unknown-service");

            mockMvc.perform(delete("/services/unknown-service"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    // ---------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------

    private ServiceNode serviceNode(String name, String description) {
        return new ServiceNode(name, description);
    }
}
