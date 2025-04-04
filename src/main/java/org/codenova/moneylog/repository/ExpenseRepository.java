package org.codenova.moneylog.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.codenova.moneylog.entity.Expense;
import org.codenova.moneylog.vo.ExpenseJoinCategory;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ExpenseRepository {
    int save(Expense expense);

    List<Expense> findByUserId(@Param("userId") int userId);

    List<Expense> findByUserIdAndDuration(@Param("userId") int userId,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);

    List<ExpenseJoinCategory> expenseJoinCategory();

    int getWeekExpenseSum(@Param("userId") int userId,
                          @Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate);

}




