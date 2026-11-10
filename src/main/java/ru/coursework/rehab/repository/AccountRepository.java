package ru.coursework.rehab.repository;
import ru.coursework.rehab.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AccountRepository extends JpaRepository<Account,Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Account a where a.id = :id")
    java.util.Optional<Account> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    java.util.Optional<Account> findByUsername(String username); boolean existsByUsername(String username); java.util.List<Account> findByRole(Account.Role role); }
