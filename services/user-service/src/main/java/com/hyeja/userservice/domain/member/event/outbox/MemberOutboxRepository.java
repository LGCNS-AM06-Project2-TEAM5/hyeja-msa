package com.hyeja.userservice.domain.member.event.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberOutboxRepository extends JpaRepository<MemberOutbox, Long> {

    List<MemberOutbox> findTop100ByPublishedAtIsNullOrderByIdAsc();
}
