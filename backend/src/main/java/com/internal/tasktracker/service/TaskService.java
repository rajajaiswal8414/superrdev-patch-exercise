package com.internal.tasktracker.service;

import com.internal.tasktracker.dto.TaskSearchResponse;
import com.internal.tasktracker.model.Task;
import com.internal.tasktracker.model.TaskStatus;
import com.internal.tasktracker.repository.TaskRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public TaskSearchResponse searchTasks(String q, String status, String priority, int page, int pageSize, String sortBy, String sortDir) {
        // Normalize query input
        String query = q == null ? "" : q.trim();
        String searchTerm = "%" + query.toLowerCase() + "%";

        // Parse status filter safely
        String normalizedStatus = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                normalizedStatus = TaskStatus.valueOf(status.trim().toUpperCase()).name();
            } catch (IllegalArgumentException e) {
                normalizedStatus = null;
            }
        }

        // Normalize priority filter
        String normalizedPriority = (priority != null && !priority.trim().isEmpty())
                ? priority.trim().toUpperCase()
                : null;

        // Determine sort order (default to ascending ID)
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String field = "createdAt".equalsIgnoreCase(sortBy) ? "createdAt" : "id";
        Sort sort = Sort.by(direction, field);

        System.out.println("[TaskService] q=\"" + query + "\" status=" + normalizedStatus
                + " priority=" + normalizedPriority
                + " page=" + page + " pageSize=" + pageSize + " sort=" + field + ":" + direction);

        List<Task> allResults = taskRepository.searchTasks(searchTerm, normalizedStatus, normalizedPriority, sort);

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, allResults.size());
        List<Task> pageResults = (start < allResults.size())
                ? allResults.subList(start, end)
                : Collections.emptyList();

        return new TaskSearchResponse(pageResults, allResults.size(), page, pageSize);
    }
}
