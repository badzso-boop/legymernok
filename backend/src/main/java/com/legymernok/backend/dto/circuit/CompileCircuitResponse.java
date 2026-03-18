package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.BoardType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompileCircuitResponse {

    private boolean success;

    /** Base64-encoded Intel HEX content. Null on error. */
    private String hexBase64;

    /** arduino-cli FQBN string used for compilation (e.g. "arduino:avr:uno"). */
    private String fqbn;

    /** Board type enum, convenient for the frontend to configure avr8js. */
    private BoardType boardType;

    /** Compilation time in milliseconds. */
    private Long compilationTimeMs;

    /** Full compiler output / error message. Null on success. */
    private String errorOutput;

    public static CompileCircuitResponse success(String hexBase64, String fqbn, BoardType boardType, long compilationTimeMs) {
        return CompileCircuitResponse.builder()
                .success(true)
                .hexBase64(hexBase64)
                .fqbn(fqbn)
                .boardType(boardType)
                .compilationTimeMs(compilationTimeMs)
                .build();
    }

    public static CompileCircuitResponse error(String errorOutput) {
        return CompileCircuitResponse.builder()
                .success(false)
                .errorOutput(errorOutput)
                .build();
    }
}
