package ru.coursework.rehab.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "alert_settings")
public class AlertSettings {
    @Id public Long id = 1L;
    public int daysPerWindow = 3;
    public double deterioration = 2;
    public double stableBand = 1;
    public double loadGrowth = 5;
    @Version public long version;
}
