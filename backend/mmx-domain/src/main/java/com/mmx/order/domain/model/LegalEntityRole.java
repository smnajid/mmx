package com.mmx.order.domain.model;

/** Mutually exclusive role of a LegalEntity within an Organisation. */
public sealed interface LegalEntityRole permits TradingHubRole, TradingClientRole {}
