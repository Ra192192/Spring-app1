package com.springapp1.auth_api.service;

import com.springapp1.auth_api.dto.ProcessRequest;
import com.springapp1.auth_api.dto.ProcessResponse;
import com.springapp1.auth_api.entity.ProcessingLog;
import com.springapp1.auth_api.repository.ProcessingLogRepository;
import com.springapp1.auth_api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class ProcessService {

    private final RestClient dataApiClient;
    private final ProcessingLogRepository processingLogRepository;
    private final UserRepository userRepository;

    public ProcessService(
            @Qualifier("dataApiClient") RestClient dataApiClient,
            ProcessingLogRepository processingLogRepository,
            UserRepository userRepository
    ) {
        this.dataApiClient = dataApiClient;
        this.processingLogRepository = processingLogRepository;
        this.userRepository = userRepository;
    }

    public ProcessResponse process(UUID userId, ProcessRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User no longer exists"
            );
        }

        ProcessResponse response;

        try {
            response = dataApiClient.post()
                    .uri("/api/transform")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ProcessResponse.class);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Data service request failed"
            );
        }

        if (response == null || response.result() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Invalid response from data service"
            );
        }

        processingLogRepository.saveAndFlush(
                new ProcessingLog(
                        userId,
                        request.text(),
                        response.result()
                )
        );

        return response;
    }
}