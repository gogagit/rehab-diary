package ru.coursework.rehab.service;

import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coursework.rehab.domain.Account;
import ru.coursework.rehab.domain.TrustedDevice;
import ru.coursework.rehab.repository.AccountRepository;
import ru.coursework.rehab.repository.PatientRepository;
import ru.coursework.rehab.repository.TrustedDeviceRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class TrustedDeviceService implements CommandLineRunner {
    public static final Duration DEVICE_LIFETIME = Duration.ofDays(30);
    private static final Duration INVITE_LIFETIME = Duration.ofMinutes(15);
    private final SecureRandom random = new SecureRandom();
    private final AccountRepository accounts;
    private final PatientRepository patients;
    private final TrustedDeviceRepository devices;
    private final Clock clock;
    private final EntityManager entityManager;

    public TrustedDeviceService(AccountRepository accounts, PatientRepository patients, Clock clock,
                                EntityManager entityManager, TrustedDeviceRepository devices) {
        this.accounts = accounts;
        this.patients = patients;
        this.clock = clock;
        this.devices = devices;
        this.entityManager = entityManager;
    }

    @Transactional
    public String issue(Long patientId, Authentication authentication) {
        Account account = managedPatient(patientId, authentication);
        String invite = token();
        account.deviceInviteHash = hash(invite);
        account.deviceInviteExpiresAt = clock.instant().plus(INVITE_LIFETIME);
        return invite;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Instant now = clock.instant();
        for (Account candidate : accounts.findByDeviceHashIsNotNull()) {
            Account account = accounts.findLockedById(candidate.id).orElseThrow();
            entityManager.refresh(account);
            if (account.deviceHash != null && account.deletedAt == null && account.role == Account.Role.PATIENT &&
                account.deviceExpiresAt != null && account.deviceExpiresAt.isAfter(now)) {
                saveDevice(account, account.deviceHash, "Ранее привязанный браузер", null, account.deviceExpiresAt);
            }
            account.deviceHash = null;
            account.deviceExpiresAt = null;
        }
    }

    @Transactional
    public DeviceList list(Long patientId, Authentication authentication) {
        Account account = managedPatient(patientId, authentication);
        Instant now = clock.instant();
        Instant expires = account.deviceInviteExpiresAt;
        return new DeviceList(devices.findByAccountIdAndExpiresAtAfterOrderByIdDesc(account.id, now)
            .stream().map(d -> new DeviceView(d.id, d.name, d.linkedAt, d.expiresAt)).toList(),
            expires != null && expires.isAfter(now) ? expires : null);
    }

    @Transactional
    public void revoke(Long patientId, Long deviceId, Authentication authentication) {
        Account account = managedPatient(patientId, authentication);
        if (devices.revoke(deviceId, account.id) == 0) throw BusinessException.missing();
    }

    @Transactional
    public void cancelInvite(Long patientId, Authentication authentication) {
        clearInvite(managedPatient(patientId, authentication));
    }

    @Transactional
    public void forget(String token) {
        if (isToken(token)) devices.revokeByHash(hash(token));
    }

    @Transactional
    public String pair(String invite, String name, String previousDevice) {
        if (!isToken(invite)) throw invalidInvite();
        String inviteHash = hash(invite);
        Long id = accounts.findInviteAccountId(inviteHash).orElseThrow(TrustedDeviceService::invalidInvite);
        Account account = accounts.findLockedById(id).orElseThrow(TrustedDeviceService::invalidInvite);
        if (account.deletedAt != null || account.role != Account.Role.PATIENT ||
            !inviteHash.equals(account.deviceInviteHash) || account.deviceInviteExpiresAt == null ||
            !account.deviceInviteExpiresAt.isAfter(clock.instant())) throw invalidInvite();
        String device = token();
        Instant now = clock.instant();
        saveDevice(account, hash(device), name, now, now.plus(DEVICE_LIFETIME));
        clearInvite(account);
        forget(previousDevice);
        return device;
    }

    public Optional<String> authenticate(String device) {
        return isToken(device) ? devices.findActiveLogin(hash(device), clock.instant()) : Optional.empty();
    }

    private boolean isToken(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{43}");
    }

    private void saveDevice(Account account, String hash, String name, Instant linkedAt, Instant expiresAt) {
        TrustedDevice device = new TrustedDevice();
        device.account = account;
        device.tokenHash = hash;
        device.name = name;
        device.linkedAt = linkedAt;
        device.expiresAt = expiresAt;
        devices.save(device);
    }

    public record DeviceView(Long id, String name, Instant linkedAt, Instant expiresAt) {}
    public record DeviceList(List<DeviceView> devices, Instant inviteExpiresAt) {}

    private Account managedPatient(Long patientId, Authentication authentication) {
        accounts.findByUsername(authentication.getName())
            .filter(account -> account.role == Account.Role.ADMIN && account.deletedAt == null)
            .orElseThrow(() -> new AccessDeniedException("Управлять устройствами может только администратор"));
        var patient = patients.findLockedById(patientId).orElseThrow(BusinessException::missing);
        Account account = accounts.findLockedById(patient.account.id).orElseThrow(BusinessException::missing);
        entityManager.refresh(account);
        if (account.deletedAt != null) throw BusinessException.missing();
        return account;
    }

    private void clearInvite(Account account) {
        account.deviceInviteHash = null;
        account.deviceInviteExpiresAt = null;
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BusinessException invalidInvite() {
        return BusinessException.invalid("Ссылка недействительна или уже использована. Попросите администратора выдать новую.");
    }
}
