package com.legymernok.backend.model.circuit;

public enum SimulationStatus {
    NEVER_RUN,
    COMPILING,
    COMPILE_ERROR,
    RUNNING,
    PAUSED,
    SUCCESS,
    FAILED
}
