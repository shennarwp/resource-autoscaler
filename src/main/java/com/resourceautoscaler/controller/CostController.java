package com.resourceautoscaler.controller;

import com.resourceautoscaler.model.CostAnalysis;
import com.resourceautoscaler.service.CostOptimizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for aggregate cost and savings analysis. */
@RestController
@RequestMapping("/api/v1/costs")
public class CostController {

    private final CostOptimizationService costService;

    /** Injects the service that computes aggregate cost analysis. */
    public CostController(CostOptimizationService costService) {
        this.costService = costService;
    }

    /** Returns the current cost estimate and projected optimization savings. */
    @GetMapping("/analysis")
    public ResponseEntity<CostAnalysis> getCostAnalysis() {
        return ResponseEntity.ok(costService.generateCostAnalysis());
    }
}
