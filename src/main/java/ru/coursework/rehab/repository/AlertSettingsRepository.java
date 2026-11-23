package ru.coursework.rehab.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.coursework.rehab.domain.AlertSettings;

public interface AlertSettingsRepository extends JpaRepository<AlertSettings, Long> {}
