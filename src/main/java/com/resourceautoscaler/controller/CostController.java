package com.resourceautoscaler.controller;

import com.resourceautoscaler.model.CostAnalysis;
import com.resourceautoscaler.service.CostOptimizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/costs")
public class CostController {

    private final CostOptimizationService costService;

    public CostController(CostOptimizationService costService) {
        this.costService = costService;
    }

    @GetMapping("/analysis")
    public ResponseEntity<CostAnalysis> getCostAnalysis() {
        return ResponseEntity.ok(costService.generateCostAnalysis());
    }
}
