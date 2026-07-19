package edu.utem.ftmk.masakgramprompt.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "influencer")
public class Influencer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "influencer_id")
    private Integer influencerId;

    @Column(name = "name")
    private String name;

    @Column(name = "instagram_account")
    private String instagramAccount;

    @Column(name = "instagram_url")
    private String instagramUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}