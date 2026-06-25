package com.canet.fileproc.controller;

import com.canet.fileproc.model.SourceType;

public record AgeOffRequest(SourceType sourceType, int retentionDays) {}
