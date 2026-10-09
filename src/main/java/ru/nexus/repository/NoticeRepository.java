package ru.nexus.repository;

import ru.nexus.entity.Notice;

public interface NoticeRepository
  extends org.springframework.data.jpa.repository.JpaRepository<Notice, Long>
{
  java.util.List<Notice> findTop100ByUserIdOrderByIdDesc(Long userId);
}
