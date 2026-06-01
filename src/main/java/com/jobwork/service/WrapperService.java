package com.jobwork.service;

import com.jobwork.domain.Wrapper;
import com.jobwork.repository.WrapperRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WrapperService {

    private final WrapperRepository repo;

    public WrapperService(WrapperRepository repo) {
        this.repo = repo;
    }

    public List<Wrapper> findAll() {
        return repo.findAll();
    }

    public Wrapper save(Wrapper wrapper) {
        return repo.save(wrapper);
    }

    public void delete(Wrapper wrapper) {
        repo.delete(wrapper);
    }

   // public boolean existsByName(String name) {
      //  return repo.existsByName(name);
   // }
}