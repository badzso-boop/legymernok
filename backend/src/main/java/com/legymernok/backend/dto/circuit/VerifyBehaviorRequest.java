package com.legymernok.backend.dto.circuit;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Simulation log submitted by the frontend (avr8js) for behavior verification.
 *
 * GPIO: map of pin number (as string) → final observed state ("HIGH" or "LOW")
 * Serial: list of lines printed via Serial.println() during simulation
 * PWM: map of pin number (as string) → average duty cycle percentage (0–100)
 */
@Data
public class VerifyBehaviorRequest {

    /** Pin number → observed state ("HIGH" / "LOW"). E.g. {"13": "HIGH"} */
    private Map<String, String> gpioPinStates;

    /** Lines printed to serial output during the simulation. */
    private List<String> serialOutputLines;

    /** Pin number → duty cycle percentage (0–100). E.g. {"9": "50"} */
    private Map<String, Integer> pwmDutyCycles;
}
