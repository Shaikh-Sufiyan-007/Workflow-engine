package com.workflow.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.dto.request.CreateDependencyDto;
import com.workflow.engine.dto.request.CreateTaskDto;
import com.workflow.engine.dto.request.CreateWorkflowRequest;
import com.workflow.engine.dto.request.LoginRequest;
import com.workflow.engine.enums.TaskType;
import com.workflow.engine.repository.UserRepository;
import com.workflow.engine.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    private String authToken;

    @BeforeEach
    void setUp() throws Exception {
        workflowRepository.deleteAll();
        userRepository.deleteAll();

        // Register user and get JWT
        LoginRequest regReq = new LoginRequest("admin", "password123");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        authToken = objectMapper.readTree(body).get("token").asText();
    }

    @Test
    @DisplayName("Create workflow with valid DAG succeeds")
    void testCreateWorkflowSuccess() throws Exception {
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();

        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .name("API Test Workflow")
                .description("Testing REST creation")
                .dagDefinition("{\"nodes\":[],\"edges\":[]}")
                .tasks(List.of(
                        CreateTaskDto.builder().id(taskA).name("Task A").taskType(TaskType.SHELL_COMMAND).build(),
                        CreateTaskDto.builder().id(taskB).name("Task B").taskType(TaskType.HTTP_CALL).build()
                ))
                .dependencies(List.of(
                        new CreateDependencyDto(taskB, taskA) // B depends on A
                ))
                .build();

        mockMvc.perform(post("/api/workflows")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("API Test Workflow")))
                .andExpect(jsonPath("$.tasks", hasSize(2)));
    }

    @Test
    @DisplayName("Create workflow with cyclic DAG is rejected with 400 Bad Request")
    void testCreateWorkflowCyclicRejected() throws Exception {
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();

        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .name("Cyclic Workflow")
                .dagDefinition("{}")
                .tasks(List.of(
                        CreateTaskDto.builder().id(taskA).name("Task A").taskType(TaskType.SHELL_COMMAND).build(),
                        CreateTaskDto.builder().id(taskB).name("Task B").taskType(TaskType.HTTP_CALL).build()
                ))
                .dependencies(List.of(
                        new CreateDependencyDto(taskB, taskA), // B depends on A
                        new CreateDependencyDto(taskA, taskB)  // A depends on B -> Cycle!
                ))
                .build();

        mockMvc.perform(post("/api/workflows")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Circular Dependency")));
    }
}
