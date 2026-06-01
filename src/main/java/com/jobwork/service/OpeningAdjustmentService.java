package com.jobwork.service;

import com.jobwork.domain.*;
import com.jobwork.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class OpeningAdjustmentService {

    @Autowired
    private OpeningAdjustmentRepository repo;

    public OpeningAdjustment save(OpeningAdjustment a) {
        return repo.save(a);
    }

    public List<OpeningAdjustment> getList(Long workerId,
                                           LocalDate from,
                                           LocalDate to) {
        return repo.findByWorkerIdAndDateBetween(workerId, from, to);
    }
}