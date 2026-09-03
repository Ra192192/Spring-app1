package com.springapp1.data_api.controller;

import com.springapp1.data_api.dto.TransformRequest;
import com.springapp1.data_api.dto.TransformResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api")
public class TransformController {

    @PostMapping("/transform")
    public TransformResponse transform(
            @Valid @RequestBody TransformRequest request
    ) {
        String result = request.text().toUpperCase(Locale.ROOT);

        return new TransformResponse(result);
    }
}