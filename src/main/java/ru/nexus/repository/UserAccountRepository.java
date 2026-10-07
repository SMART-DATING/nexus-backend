package ru.nexus.repository;

import ru.nexus.entity.UserAccount;

public interface UserAccountRepository
  extends
    org.springframework.data.jpa.repository.JpaRepository<UserAccount, Long>
{
  java.util.Optional<UserAccount> findByEmail(String email);

  @org.springframework.data.jpa.repository.Lock(
    jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
  )
  @org.springframework.data.jpa.repository.Query(
    "select u from UserAccount u where u.id=:id"
  )
  java.util.Optional<UserAccount> lockById(
    @org.springframework.data.repository.query.Param("id") Long id
  );
}
