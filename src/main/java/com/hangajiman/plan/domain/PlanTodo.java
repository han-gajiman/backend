package com.hangajiman.plan.domain;

import java.time.LocalDate;

import com.hangajiman.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 소원 캘린더의 날짜별 할 일 (ERD plan_todos, V12). 지난 날짜는 바꿀 수 없습니다(서비스에서 확인).
 */
@Entity
@Table(name = "plan_todos")
public class PlanTodo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "todo_date", nullable = false)
    private LocalDate todoDate;

    @Column(nullable = false, length = 40)
    private String content;

    @Column(nullable = false)
    private boolean done;

    protected PlanTodo() {
    }

    public PlanTodo(Long userId, LocalDate todoDate, String content) {
        this.userId = userId;
        this.todoDate = todoDate;
        this.content = content;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getTodoDate() {
        return todoDate;
    }

    public String getContent() {
        return content;
    }

    public boolean isDone() {
        return done;
    }
}
