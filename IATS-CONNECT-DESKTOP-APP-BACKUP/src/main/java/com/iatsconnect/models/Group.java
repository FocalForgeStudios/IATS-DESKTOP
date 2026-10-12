package com.iatsconnect.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Row of {@code public.groups}. Property names are mapped from snake_case by the shared ObjectMapper. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Group(String id, String name, String description, String course, String cohort, String parentGroupId, String createdBy, String visibility) {}
