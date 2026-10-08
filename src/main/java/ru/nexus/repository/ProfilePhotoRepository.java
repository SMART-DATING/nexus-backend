package ru.nexus.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.nexus.entity.ProfilePhoto;

public interface ProfilePhotoRepository
  extends JpaRepository<ProfilePhoto, Long>
{
  List<ProfilePhoto> findByUserIdOrderByPositionAscIdAsc(Long userId);
  long countByUserId(Long userId);
}
