package com.jobwork.service;

import com.jobwork.domain.YarnType;
import com.jobwork.repository.YarnTypeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class YarnTypeService {

  private final YarnTypeRepository repo;

  // 🔹 Get all Yarn Types (for ComboBox)
  @Transactional(readOnly = true)
  public List<YarnType> findAll() {
    return repo.findAll();
  }

  // 🔹 Find by name (IMPORTANT for your edit dialog)
  @Transactional(readOnly = true)
  public YarnType findByName(String name) {
    return repo.findByNameIgnoreCase(name).orElse(null);
  }
}
