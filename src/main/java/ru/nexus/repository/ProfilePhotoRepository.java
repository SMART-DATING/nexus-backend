package ru.nexus.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.nexus.entity.ProfilePhoto;

public interface ProfilePhotoRepository
  extends JpaRepository<ProfilePhoto, Long>
{
  interface Metadata {
    Long getId();
    Long getUserId();
    int getPosition();
    String getVersion();
  }
  @org.springframework.data.jpa.repository.Query("select p.id as id, p.userId as userId, p.position as position, p.version as version from ProfilePhoto p where p.userId in :ids order by p.userId, p.position, p.id")
  List<Metadata> findMetadata(java.util.Collection<Long> ids);
  List<ProfilePhoto> findByUserIdOrderByPositionAscIdAsc(Long userId);
  long countByUserId(Long userId);
}
