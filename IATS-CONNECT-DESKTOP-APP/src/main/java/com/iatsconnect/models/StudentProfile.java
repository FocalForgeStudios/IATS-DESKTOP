package com.iatsconnect.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Row of {@code public.student_profiles}. Property names are mapped from snake_case by the shared ObjectMapper. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudentProfile(String id, String name, String course, String cohort, String studySchedule, String avatarUrl, String emoji) {}
