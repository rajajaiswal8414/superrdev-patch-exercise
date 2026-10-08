package com.internal.tasktracker.dto;

import com.internal.tasktracker.model.Task;

import java.util.List;

public class TaskSearchResponse {
    private List<Task> items;
    private int total;
    private int page;
    private int pageSize;

    public TaskSearchResponse(List<Task> items, int total, int page, int pageSize) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<Task> getItems() { return items; }
    public void setItems(List<Task> items) { this.items = items; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}
