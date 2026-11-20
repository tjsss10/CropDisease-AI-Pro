package com.cropdisease.controller;

import com.cropdisease.model.User;
import com.cropdisease.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public record SignupRequest(String fullName, String email, String password, String phone,
                                String village, Double farmSizeAcres, String primaryCrop) { }

    public record LoginRequest(String email, String password) { }

    public record ProfileUpdate(String fullName, String phone, String village, Double farmSizeAcres,
                                String primaryCrop, Double latitude, Double longitude) { }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest r) {
        if (r.fullName() == null || r.fullName().trim().length() < 2) return error("Please enter your full name.");
        String email = r.email() == null ? "" : r.email().trim().toLowerCase(Locale.ROOT);
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) return error("Please enter a valid email address.");
        if (r.password() == null || r.password().length() < 6) return error("Password must be at least 6 characters.");
        if (users.existsByEmailIgnoreCase(email)) return error("An account with this email already exists. Please log in.");

        User u = new User();
        u.setFullName(r.fullName().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setPhone(blankToNull(r.phone()));
        u.setVillage(blankToNull(r.village()));
        u.setFarmSizeAcres(r.farmSizeAcres());
        u.setPrimaryCrop(blankToNull(r.primaryCrop()));
        return ResponseEntity.ok(users.save(u));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest r) {
        if (r.email() == null || r.password() == null) return error("Enter your email and password.");
        return users.findByEmailIgnoreCase(r.email().trim())
                .filter(u -> encoder.matches(r.password(), u.getPasswordHash()))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(401).body(Map.of("message", "Wrong email or password.")));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return users.findById(id).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("message", "User not found.")));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ProfileUpdate r) {
        User u = users.findById(id).orElse(null);
        if (u == null) return ResponseEntity.status(404).body(Map.of("message", "User not found."));
        if (r.fullName() != null && r.fullName().trim().length() >= 2) u.setFullName(r.fullName().trim());
        if (r.phone() != null) u.setPhone(blankToNull(r.phone()));
        if (r.village() != null) u.setVillage(blankToNull(r.village()));
        if (r.farmSizeAcres() != null) u.setFarmSizeAcres(r.farmSizeAcres() <= 0 ? null : r.farmSizeAcres());
        if (r.primaryCrop() != null) u.setPrimaryCrop(blankToNull(r.primaryCrop()));
        if (r.latitude() != null && r.longitude() != null
                && Math.abs(r.latitude()) <= 90 && Math.abs(r.longitude()) <= 180) {
            u.setLatitude(Math.round(r.latitude() * 1e4) / 1e4);
            u.setLongitude(Math.round(r.longitude() * 1e4) / 1e4);
        }
        return ResponseEntity.ok(users.save(u));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ResponseEntity<?> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
