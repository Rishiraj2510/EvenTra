package com.eventra.settings;

import com.eventra.common.EventValidationException;
import com.eventra.settings.dto.SettingsRequest;
import com.eventra.settings.dto.SettingsResponse;
import com.eventra.settings.dto.WeightsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private final SettingsRepository settingsRepository;

    public SettingsService(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public SettingsResponse getSettings() {
        Settings settings = settingsRepository.findById(true)
                .orElseThrow(() -> new SettingsNotFoundException("Settings configuration row not found"));
        return toResponse(settings);
    }

    @Transactional
    public SettingsResponse updateSettings(SettingsRequest req) {
        validateWeights(req.getWeights());

        Settings settings = settingsRepository.findById(true)
                .orElseThrow(() -> new SettingsNotFoundException("Settings configuration row not found"));

        WeightsDto w = req.getWeights();
        settings.setWeightSkill(w.getSkill());
        settings.setWeightExperience(w.getExperience());
        settings.setWeightPerformance(w.getPerformance());
        settings.setWeightLoad(w.getLoad());
        settings.setWeightInterest(w.getInterest());
        settings.setFirstTimerBonus(req.getFirstTimerBonus());
        settings.setDefaultMaxHours(req.getDefaultMaxHours());

        Settings saved = settingsRepository.save(settings);
        return toResponse(saved);
    }

    private void validateWeights(WeightsDto w) {
        if (w == null || w.getSkill() == null || w.getExperience() == null ||
                w.getPerformance() == null || w.getLoad() == null || w.getInterest() == null) {
            throw new EventValidationException("All weights are required");
        }
        int sum = w.getSkill() + w.getExperience() + w.getPerformance() + w.getLoad() + w.getInterest();
        if (sum != 100) {
            throw new EventValidationException("Sum of weights must be exactly 100, but got: " + sum);
        }
    }

    private SettingsResponse toResponse(Settings settings) {
        WeightsDto weights = new WeightsDto(
                settings.getWeightSkill(),
                settings.getWeightExperience(),
                settings.getWeightPerformance(),
                settings.getWeightLoad(),
                settings.getWeightInterest()
        );
        return new SettingsResponse(
                weights,
                settings.getFirstTimerBonus(),
                settings.getDefaultMaxHours()
        );
    }
}

