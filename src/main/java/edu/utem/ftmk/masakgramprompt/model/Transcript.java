package edu.utem.ftmk.masakgramprompt.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "transcript")
public class Transcript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transcript_id")
    private Integer transcriptId;

    @Column(name = "reel_id")
    private Integer reelId;

    @Column(name = "audio_id")
    private Integer audioId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_created_at")
    private LocalDateTime fileCreatedAt;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "file_format")
    private String fileFormat;

    @Column(name = "audio_transcript_consistent")
    private Boolean audioTranscriptConsistent;

    @Column(name = "verified_by_matric")
    private String verifiedByMatric;

    @Column(name = "verified_by_name")
    private String verifiedByName;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
}