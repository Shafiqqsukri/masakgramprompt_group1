package edu.utem.ftmk.masakgramprompt.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "prompt_technique")
public class PromptTechnique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "technique_id")
    private Integer techniqueId;

    @Column(name = "technique_name")
    private String techniqueName;

    @Column(name = "system_prompt_file")
    private String systemPromptFile;

    @Column(name = "user_prompt_file")
    private String userPromptFile;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}