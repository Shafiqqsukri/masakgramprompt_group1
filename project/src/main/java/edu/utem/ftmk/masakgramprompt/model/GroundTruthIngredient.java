package edu.utem.ftmk.masakgramprompt.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ground_truth_ingredient")
public class GroundTruthIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gt_ingredient_id")
    private Integer gtIngredientId;

    @ManyToOne
    @JoinColumn(name = "gt_reel_id")
    private GroundTruthReel groundTruthReel;

    @Column(name = "name_original")
    private String nameOriginal;

    @Column(name = "language_mentioned")
    private String languageMentioned;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "quantity_expression")
    private String quantityExpression;

    @Column(name = "quantity_category")
    private String quantityCategory;

    @Column(name = "quantity_unit_culinary")
    private String quantityUnitCulinary;

    @Column(name = "quantity_value_culinary")
    private Float quantityValueCulinary;

    @Column(name = "estimated_weight_g")
    private Float estimatedWeightG;

    @Column(name = "calories")
    private Float calories;

    @Column(name = "total_fat_g")
    private Float totalFatG;

    @Column(name = "saturated_fat_g")
    private Float saturatedFatG;

    @Column(name = "cholesterol_mg")
    private Float cholesterolMg;

    @Column(name = "sodium_mg")
    private Float sodiumMg;

    @Column(name = "total_carbohydrate_g")
    private Float totalCarbohydrateG;

    @Column(name = "dietary_fiber_g")
    private Float dietaryFiberG;

    @Column(name = "total_sugars_g")
    private Float totalSugarsG;

    @Column(name = "protein_g")
    private Float proteinG;

    @Column(name = "vitamin_d_mcg")
    private Float vitaminDMcg;

    @Column(name = "calcium_mg")
    private Float calciumMg;

    @Column(name = "iron_mg")
    private Float ironMg;

    @Column(name = "potassium_mg")
    private Float potassiumMg;

    @Column(name = "annotation_layer")
    private String annotationLayer;

    @Column(name = "annotator_matric")
    private String annotatorMatric;

    @Column(name = "annotator_name")
    private String annotatorName;

    @Column(name = "annotated_at")
    private LocalDateTime annotatedAt;
}