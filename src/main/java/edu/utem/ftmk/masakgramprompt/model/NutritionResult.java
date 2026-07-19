package edu.utem.ftmk.masakgramprompt.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "nutrition_result")
public class NutritionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Integer resultId;

    @OneToOne
    @JoinColumn(name = "experiment_id")
    private Experiment experiment;

    @Column(name = "recipe_name")
    private String recipeName;

    @Column(name = "servings_estimated")
    private Integer servingsEstimated;

    @Column(name = "serving_calories")
    private Float servingCalories;

    @Column(name = "serving_total_fat_g")
    private Float servingTotalFatG;

    @Column(name = "serving_saturated_fat_g")
    private Float servingSaturatedFatG;

    @Column(name = "serving_cholesterol_mg")
    private Float servingCholesterolMg;

    @Column(name = "serving_sodium_mg")
    private Float servingSodiumMg;

    @Column(name = "serving_carbohydrate_g")
    private Float servingCarbohydrateG;

    @Column(name = "serving_fiber_g")
    private Float servingFiberG;

    @Column(name = "serving_sugars_g")
    private Float servingSugarsG;

    @Column(name = "serving_protein_g")
    private Float servingProteinG;

    @Column(name = "serving_vitamin_d_mcg")
    private Float servingVitaminDMcg;

    @Column(name = "serving_calcium_mg")
    private Float servingCalciumMg;

    @Column(name = "serving_iron_mg")
    private Float servingIronMg;

    @Column(name = "serving_potassium_mg")
    private Float servingPotassiumMg;

    @Column(name = "total_calories")
    private Float totalCalories;

    @Column(name = "total_fat_g")
    private Float totalFatG;

    @Column(name = "total_saturated_fat_g")
    private Float totalSaturatedFatG;

    @Column(name = "total_cholesterol_mg")
    private Float totalCholesterolMg;

    @Column(name = "total_sodium_mg")
    private Float totalSodiumMg;

    @Column(name = "total_carbohydrate_g")
    private Float totalCarbohydrateG;

    @Column(name = "total_fiber_g")
    private Float totalFiberG;

    @Column(name = "total_sugars_g")
    private Float totalSugarsG;

    @Column(name = "total_protein_g")
    private Float totalProteinG;

    @Column(name = "total_vitamin_d_mcg")
    private Float totalVitaminDMcg;

    @Column(name = "total_calcium_mg")
    private Float totalCalciumMg;

    @Column(name = "total_iron_mg")
    private Float totalIronMg;

    @Column(name = "total_potassium_mg")
    private Float totalPotassiumMg;

    @Column(name = "raw_json_output", columnDefinition = "TEXT")
    private String rawJsonOutput;

    @Column(name = "json_valid")
    private Boolean jsonValid;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}