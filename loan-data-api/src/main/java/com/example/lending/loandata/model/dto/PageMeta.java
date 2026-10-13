package com.example.lending.loandata.model.dto;

/** Pagination metadata included in paged responses. */
public record PageMeta(int number, int size, long totalElements, int totalPages) {}
