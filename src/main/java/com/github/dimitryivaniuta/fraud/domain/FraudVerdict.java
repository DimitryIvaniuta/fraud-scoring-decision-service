package com.github.dimitryivaniuta.fraud.domain;

/**
 * Final business decision produced by the fraud scoring engine.
 */
public enum FraudVerdict {
    /** Transaction can continue automatically. */
    APPROVE,

    /** Transaction must be routed to manual review. */
    REVIEW,

    /** Transaction should be rejected automatically. */
    DECLINE
}
