package com.springapp1.data_api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.springapp1.data_api.security.InternalTokenFilter;
import org.springframework.context.annotation.Import;

@Import(InternalTokenFilter.class)
@WebMvcTest(
        controllers = TransformController.class,
        properties = "internal.token=test-secret"
)
class TransformControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void transformsTextWhenTokenIsValid() throws Exception {
        mockMvc.perform(post("/api/transform")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"hello"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("HELLO"));
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(post("/api/transform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"hello"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsRequestWithInvalidToken() throws Exception {
        mockMvc.perform(post("/api/transform")
                        .header("X-Internal-Token", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"hello"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsBlankText() throws Exception {
        mockMvc.perform(post("/api/transform")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedJsonWithoutToken() throws Exception {
        mockMvc.perform(post("/api/transform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken-json"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMalformedJsonWithValidToken() throws Exception {
        mockMvc.perform(post("/api/transform")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken-json"))
                .andExpect(status().isBadRequest());
    }
}