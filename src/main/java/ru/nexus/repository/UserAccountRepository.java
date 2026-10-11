package ru.nexus.repository;

import ru.nexus.entity.UserAccount;

public interface UserAccountRepository
  extends
    org.springframework.data.jpa.repository.JpaRepository<UserAccount, Long>
{
  interface DiscoveryAccount {
    Long getId();
    String getGender();
    Boolean getDiscoveryHidden();
  }
  interface GalleryState {
    Long getId();
    Boolean getGalleryMigrated();
  }
  @org.springframework.data.jpa.repository.Query("select u.id as id, u.gender as gender, u.discoveryHidden as discoveryHidden from UserAccount u")
  java.util.List<DiscoveryAccount> findDiscoveryAccounts();
  @org.springframework.data.jpa.repository.Query("select u.id as id, u.galleryMigrated as galleryMigrated from UserAccount u where u.id in :ids")
  java.util.List<GalleryState> findGalleryStates(java.util.Collection<Long> ids);
  java.util.Optional<UserAccount> findByEmail(String email);
  java.util.Optional<UserAccount> findByLogin(String login);
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query("update UserAccount u set u.lastActiveAt=:now where u.id=:id and (u.lastActiveAt is null or u.lastActiveAt < :threshold)")
  int touchActivity(Long id, java.time.Instant now, java.time.Instant threshold);

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
