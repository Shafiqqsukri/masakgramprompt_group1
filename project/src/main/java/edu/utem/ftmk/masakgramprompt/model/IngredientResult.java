package edu.utem.ftmk.masakgramprompt.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "ingredient_result")
public class IngredientResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ingredient_id")
    private Integer ingredientId;

    @ManyToOne
    @JoinColumn(name = "result_id")
    private NutritionResult nutritionResult;

    @Column(name = "name_original")
    private String nameOriginal;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "quantity_value")
    private Float quantityValue;

    @Column(name = "unit_original")
    private String unitOriginal;

    @Column(name = "unit_en")
    private String unitEn;

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
}