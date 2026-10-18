package com.fitnessai.profile.service;

import com.fitnessai.profile.dto.ProfileDtos.ProfileResponse;
import com.fitnessai.profile.dto.ProfileDtos.ProfileStats;
import com.fitnessai.profile.dto.ProfileDtos.ProfileUpdateRequest;
import com.fitnessai.profile.model.Profile;
import com.fitnessai.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ProfileService {

    private final ProfileRepository profiles;

    public ProfileService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    public ProfileResponse get(long userId) {
        return profiles.findById(userId).map(ProfileResponse::from).orElseGet(() -> ProfileResponse.empty(userId));
    }

    @Transactional
    public ProfileResponse upsert(long userId, ProfileUpdateRequest req) {
        Profile profile = profiles.findById(userId).orElseGet(() -> new Profile(userId));
        req.applyTo(profile);
        return ProfileResponse.from(profiles.save(profile));
    }

    public List<ProfileResponse> all() {
        return profiles.findAll().stream().map(ProfileResponse::from).toList();
    }

    @Transactional
    public void deleteForUser(long userId) {
        profiles.deleteById(userId);
    }

    public ProfileStats stats() {
        Double avgAge = profiles.averageAge();
        return new ProfileStats(
                profiles.count(),
                avgAge == null ? null : Math.round(avgAge * 10.0) / 10.0,
                toMap(profiles.countByGoal()),
                toMap(profiles.countByLevel()),
                toMap(profiles.countByGender()));
    }

    private static Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> out = new TreeMap<>();
        rows.forEach(r -> out.put(((Enum<?>) r[0]).name().toLowerCase(Locale.ROOT), (Long) r[1]));
        return out;
    }
}
