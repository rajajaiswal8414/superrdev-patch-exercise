package com.internal.tasktracker.repository;

import com.internal.tasktracker.model.Task;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("SELECT t FROM Task t WHERE t.archived = false "
         + "AND (LOWER(t.title) LIKE :term OR LOWER(t.description) LIKE :term) "
         + "AND (COALESCE(:status, '') = '' OR t.status = :status) "
         + "AND (COALESCE(:priority, '') = '' OR UPPER(t.priority) = :priority)")
    List<Task> searchTasks(
            @Param("term") String term,
            @Param("status") String status,
            @Param("priority") String priority,
            Sort sort);
}
