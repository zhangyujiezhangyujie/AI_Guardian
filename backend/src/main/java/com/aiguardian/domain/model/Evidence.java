package com.aiguardian.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evidence {
    private String evidenceId;
    private String documentId;
    private String documentTitle;
    private String section;
    private String content;
    private double score;
    private int rank;
}
