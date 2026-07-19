package edu.utem.ftmk.masakgramprompt.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ground_truth_reel")
public class GroundTruthReel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gt_reel_id")
    private Integer gtReelId;

    @ManyToOne
    @JoinColumn(name = "transcript_id")
    private Transcript transcript;

    @Column(name = "annotator_matric")
    private String annotatorMatric;

    @Column(name = "annotator_name")
    private String annotatorName;

    @Column(name = "annotated_at")
    private LocalDateTime annotatedAt;
}