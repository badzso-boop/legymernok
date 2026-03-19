package com.legymernok.backend.dto.circuit;

import lombok.Data;

import java.util.Map;

/**
 * Simulation results submitted by the frontend (CircuitJS1/Falstad) for analog verification.
 *
 * nodeVoltages: node label → measured voltage in Volts
 * branchCurrents: branch label → measured current in Amperes
 */
@Data
public class VerifyAnalogRequest {

    /** Node label → voltage in Volts. E.g. {"node1": 4.97} */
    private Map<String, Double> nodeVoltages;

    /** Branch/component label → current in Amperes. E.g. {"R1": 0.0049} */
    private Map<String, Double> branchCurrents;

    /** Component label → observed value (for COMPONENT_VALUE / LED_LIGHTS checks). */
    private Map<String, Double> componentValues;
}
