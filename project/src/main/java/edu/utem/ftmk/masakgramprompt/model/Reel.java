package edu.utem.ftmk.masakgramprompt.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reel")
public class Reel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reel_id")
    private Integer reelId;

    @ManyToOne
    @JoinColumn(name = "influencer_id")
    private Influencer influencer;

    @Column(name = "reel_id_instagram")
    private String reelIdInstagram;

    @Column(name = "reel_url")
    private String reelUrl;

    @Column(name = "identified_by_matric")
    private String identifiedByMatric;

    @Column(name = "identified_by_name")
    private String identifiedByName;

    @Column(name = "identified_date")
    private LocalDate identifiedDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}